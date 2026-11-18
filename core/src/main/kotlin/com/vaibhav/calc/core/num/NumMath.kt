package com.vaibhav.calc.core.num

import ch.obermuhlner.math.big.BigDecimalMath
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * The mathematical function library over [Num]. Every function is exact-first:
 * it returns an exact [Rational] / [Surd] / [SymConst] when it can, and only
 * falls back to [Dec] (BigDecimal — never Double) when the true result is
 * genuinely irrational. Transcendental kernels come from big-math, so `Double`
 * is never touched.
 *
 * Angle handling lives one layer up (the evaluator applies the deg/rad/grad
 * mode); everything here is in radians.
 */
object NumMath {

    // Guard against pathological blow-ups (Article: overflow is a typed error).
    private fun guard(mc: MathContext) = NumericLimits.guard(mc)

    // -----------------------------------------------------------------------
    // Roots and powers.
    // -----------------------------------------------------------------------

    /** √x, exact when possible: `√8 → 2√2`, `√9 → 3`, `√(4/9) → 2/3`. */
    fun sqrt(x: Num, mc: MathContext): Num = when (x) {
        is Rational -> {
            when {
                x.isZero -> Num.ZERO
                x.signum() < 0 -> throw ArithFault.Domain("square root of a negative number", "sqrt")
                else -> {
                    // Reduce each operand first: a perfect-square fraction need
                    // not allocate its much larger n*d product.
                    val (outsideN, insideN) = extractSquareFactor(x.num)
                    val (outsideD, insideD) = extractSquareFactor(x.den)
                    val inside = NumericLimits.multiply(insideN, insideD)
                    val coeff = Rational.of(outsideN, NumericLimits.multiply(outsideD, insideD))
                    if (inside == BigInteger.ONE) coeff else Surd.of(coeff, inside)
                }
            }
        }
        else -> {
            if (x.signum() < 0) throw ArithFault.Domain("square root of a negative number", "sqrt")
            enclosed(x, mc, squareRoot(x.toBigDecimal(guard(mc)), guard(mc)).round(mc), CertifiedMath::sqrt)
        }
    }

    /** ∛x. Exact for perfect cubes (`∛8 → 2`), decimal otherwise. */
    fun cbrt(x: Num, mc: MathContext): Num = root(x, BigInteger.valueOf(3), mc)

    /** The n-th root of x = x^(1/n). */
    fun root(x: Num, n: BigInteger, mc: MathContext): Num {
        NumericLimits.precision(mc)
        NumericLimits.integer(n)
        if (n.signum() <= 0) throw ArithFault.Domain("root index must be positive", "root")
        if (n == BigInteger.ONE) return x
        if (n == BigInteger.valueOf(2)) return sqrt(x, mc)
        if (x.isZero) return Num.ZERO
        val negative = x.signum() < 0
        if (negative && n.mod(BigInteger.valueOf(2)).signum() == 0) {
            throw ArithFault.Domain("even root of a negative number", "root")
        }
        // Try an exact rational root first.
        exactRoot(x, n)?.let { return it }
        val g = guard(mc)
        // The interval-aware abs() may choose a new midpoint when the interval crosses zero.
        // Compute the nominal root from the original nominal value; enclose endpoints below.
        val radicand = NumericLimits.transcendental(x.toBigDecimal(g).abs())
        // exp(log(x)/n) avoids a root-kernel integer narrowing and works for
        // indices beyond Int.MAX_VALUE without work proportional to the index.
        val approx = if (radicand.signum() == 0) BigDecimal.ZERO else {
            val logarithm = BigDecimalMath.log(radicand, g).divide(BigDecimal(n), g)
            BigDecimalMath.exp(NumericLimits.exponential(logarithm), g).round(mc)
        }
        val value = if (negative) approx.negate() else approx
        return enclosed(x, mc, value) { bounds, precision ->
            fun endpoint(point: BigDecimal): DecimalBounds {
                if (point.signum() == 0) return DecimalBounds.point(BigDecimal.ZERO)
                if (point.signum() < 0 && !n.testBit(0)) throw ArithFault.Unsupported("the root argument's uncertainty crosses the real domain")
                val log = CertifiedMath.ln(DecimalBounds.point(point.abs()), precision)
                val power = log.divide(DecimalBounds.point(BigDecimal(n)), precision)
                val result = CertifiedMath.exp(power, precision)
                return if (point.signum() < 0) result.negate() else result
            }
            DecimalBounds(endpoint(bounds.lower).lower, endpoint(bounds.upper).upper)
        }
    }

    /** x^y, exact-first. Integer, half-integer and unit-fraction exponents stay exact when they can. */
    fun pow(base: Num, exp: Num, mc: MathContext): Num {
        exp.toBigIntegerExactOrNull()?.let { return intPow(base, it) }

        if (exp is Rational) {
            // Take roots before powers: a moderate p/q may have a huge p, and
            // forming base^p first can overflow even when base^(p/q) is near 1.
            // Half-integer powers retain their exact rational/surd shapes.
            if (exp.den == BigInteger.valueOf(2)) return intPow(sqrt(base, mc), exp.num)
            // base^(1/q) = q-th root
            if (exp.num == BigInteger.ONE) return root(base, exp.den, mc)
            exactRoot(base, exp.den)?.let { return intPow(it, exp.num) }
            if (base.signum() < 0) {
                if (!exp.den.testBit(0)) throw ArithFault.Domain("even root of a negative number", "pow")
                val magnitude = decPow(base.abs(), exp, mc)
                return if (exp.num.testBit(0)) -magnitude else magnitude
            }
            return decPow(base, exp, mc)
        }
        return decPow(base, exp, mc)
    }

    private fun intPow(base: Num, k: BigInteger): Num {
        if (k.signum() == 0) return Num.ONE // 0^0 defined as 1 (documented in DECISIONS.md)
        NumericLimits.powerExponent(k)
        if (k.signum() < 0) {
            if (base.isZero) throw ArithFault.DivideByZero("zero raised to a negative power")
            if (base is SymConst && k.negate() > SMALL_POWER) return boundedIntegerPower(base, k, powerOfConstant(base, k.negate(), inverse = true))
            return intPow(base, k.negate()).reciprocal()
        }
        // A multiple of π or e leaves the exact tower at its first product, and a chain of rounded
        // products loses a digit for every power of ten in the exponent. Up to the sixty-fourth
        // power that is under the two digits in fifty no reader of an estimate relies on, and the
        // chain stays the products a person would write: e^2 = e·e, e^(2+3) = e^2·e^3 and
        // e^64 = e^32·e^32 hold digit for digit, as they did. Beyond it the power is taken from a
        // wider expansion, and an exponent law across that line is no longer digit for digit.
        if (base is SymConst && k > SMALL_POWER) return boundedIntegerPower(base, k, powerOfConstant(base, k))
        // Exponentiation by squaring over the exact Num arithmetic.
        var result: Num = Num.ONE
        var b = base
        var e = k
        while (e.signum() > 0) {
            if (e.testBit(0)) result *= b
            e = e.shiftRight(1)
            if (e.signum() > 0) b *= b
        }
        return result
    }

    private val SMALL_POWER = BigInteger.valueOf(64)

    private fun boundedIntegerPower(base: Num, exponent: BigInteger, value: BigDecimal): Num {
        var factor = base.bounds() ?: return Dec(value)
        var result = DecimalBounds.point(BigDecimal.ONE)
        var power = exponent.abs()
        while (power.signum() > 0) {
            if (power.testBit(0)) result = result.multiply(factor, 66)
            power = power.shiftRight(1)
            if (power.signum() > 0) factor = factor.multiply(factor, 66)
        }
        if (exponent.signum() < 0) result = DecimalBounds.point(BigDecimal.ONE).divide(result, 66)
        return result.around(value)
    }

    /**
     * (c·π)^k or (c·e)^k for a larger k, to the tower's digits. The base is exact, so it is expanded
     * far enough for the power to keep them all. Squared and multiplied at fifty digits, e^108
     * came out wrong in its last two, which at that size is 0.17 of a unit: frac(e^108) read
     * 0.398 for 0.230, while exp(108) had it right (thirteenth review).
     */
    private fun powerOfConstant(base: SymConst, k: BigInteger, inverse: Boolean = false): BigDecimal {
        val target = Tower.mc
        val wide = MathContext(target.precision + digits(k) + CANCELLATION_GUARD, RoundingMode.HALF_EVEN)
        var result = BigDecimal.ONE
        var b = base.toBigDecimal(wide)
        var e = k
        while (e.signum() > 0) {
            if (e.testBit(0)) {
                NumericLimits.decimalProduct(result, b)
                result = result.multiply(b, wide)
            }
            e = e.shiftRight(1)
            if (e.signum() > 0) {
                NumericLimits.decimalProduct(b, b)
                b = b.multiply(b, wide)
            }
        }
        // a negative power is one over it, taken before the digits are cut to the tower's
        return (if (inverse) BigDecimal.ONE.divide(result, wide) else result).round(target)
    }

    private fun decPow(base: Num, exp: Num, mc: MathContext): Num {
        val g = guard(mc)
        if (base.isZero) {
            if (exp is Dec && (exp.bounds()?.lower?.signum() ?: 0) <= 0)
                throw ArithFault.Unsupported("zero raised to an uncertain exponent is not determined")
            return if (exp.signum() > 0) Num.ZERO
            else throw ArithFault.DivideByZero("zero raised to a non-positive power")
        }
        if (base.signum() < 0) {
            throw ArithFault.Domain("negative base with a non-integer exponent (complex result)", "pow")
        }
        val b = NumericLimits.transcendental(base.toBigDecimal(g))
        val e = NumericLimits.transcendental(exp.toBigDecimal(g))
        // A small exponent can still explode for a huge base, and vice versa.
        // Bound their actual log-product before invoking exp.
        val power = BigDecimalMath.log(b, g).multiply(e, g)
        val value = BigDecimalMath.exp(NumericLimits.exponential(power), g).round(mc)
        val precision = enclosurePrecision(mc)
        val baseBounds = base.bounds(precision) ?: return Dec(value)
        val exponentBounds = exp.bounds(precision) ?: return Dec(value)
        return CertifiedMath.exp(CertifiedMath.ln(baseBounds, precision).multiply(exponentBounds, precision), precision).around(value)
    }

    /** Exact rational n-th root of x, or null if there isn't one. */
    private fun exactRoot(x: Num, n: BigInteger): Num? {
        if (x !is Rational) return null
        if (x.isZero) return Num.ZERO
        val negative = x.num.signum() < 0
        if (negative && n.mod(BigInteger.valueOf(2)).signum() == 0) return null
        val rootNum = bigIntRoot(x.num.abs(), n) ?: return null
        val rootDen = bigIntRoot(x.den, n) ?: return null
        val signed = if (negative) rootNum.negate() else rootNum
        return Rational.of(signed, rootDen)
    }

    /** Exact integer n-th root of m (m ≥ 0) if one exists, else null (binary search). */
    private fun bigIntRoot(m: BigInteger, n: BigInteger): BigInteger? {
        if (m.signum() == 0) return BigInteger.ZERO
        if (m == BigInteger.ONE) return BigInteger.ONE
        // For m ≥ 2, an integer n-th root r ≥ 2 requires 2^n ≤ m, i.e.
        // n ≤ bitLength(m). Past that no exact integer root exists — bail so the
        // caller uses the decimal path. This also keeps n well inside Int range
        // (the narrowing below is then always safe) and prevents a pathological
        // mid.pow(hugeN) from hanging or exhausting memory.
        if (n.bitLength() > 31 || n > BigInteger.valueOf(m.bitLength().toLong())) return null
        val nInt = n.toInt()
        // Integer Newton iteration from above; binary search takes tens of
        // thousands of large multiplications for a large cube-root operand.
        var guess = BigInteger.ONE.shiftLeft((m.bitLength() + nInt - 1) / nInt)
        val nMinusOne = n - BigInteger.ONE
        while (true) {
            // This temporary is bounded by at most twice m's bit length.
            val denominator = guess.pow(nInt - 1)
            val next = (nMinusOne * guess + m / denominator) / n
            if (next >= guess) return if (guess.pow(nInt) == m) guess else null
            guess = next
        }
    }

    // -----------------------------------------------------------------------
    // Exponential and logarithms.
    // -----------------------------------------------------------------------

    fun exp(x: Num, mc: MathContext): Num {
        if (x.isZero) return Num.ONE
        val g = guard(mc)
        return enclosed(x, mc, BigDecimalMath.exp(NumericLimits.exponential(x.toBigDecimal(g)), g).round(mc), CertifiedMath::exp)
    }

    fun ln(x: Num, mc: MathContext): Num {
        if (x.signum() <= 0) throw ArithFault.Domain("logarithm of a non-positive number", "ln")
        if (x is Rational && x == Num.ONE) return Num.ZERO
        if (x is SymConst && x.konst == Konst.E && x.coeff == Num.ONE) return Num.ONE // ln(e) = 1
        val g = guard(mc)
        return enclosed(x, mc, BigDecimalMath.log(NumericLimits.transcendental(x.toBigDecimal(g)), g).round(mc), CertifiedMath::ln)
    }

    fun log10(x: Num, mc: MathContext): Num {
        if (x.signum() <= 0) throw ArithFault.Domain("logarithm of a non-positive number", "log")
        integerLogExact(x, BigInteger.TEN)?.let { return it }
        val g = guard(mc)
        return enclosed(x, mc, BigDecimalMath.log10(NumericLimits.transcendental(x.toBigDecimal(g)), g).round(mc)) { bounds, precision ->
            CertifiedMath.ln(bounds, precision).divide(CertifiedMath.ln(DecimalBounds.point(BigDecimal.TEN), precision), precision)
        }
    }

    fun log2(x: Num, mc: MathContext): Num {
        if (x.signum() <= 0) throw ArithFault.Domain("logarithm of a non-positive number", "log2")
        integerLogExact(x, BigInteger.valueOf(2))?.let { return it }
        val g = guard(mc)
        return enclosed(x, mc, BigDecimalMath.log2(NumericLimits.transcendental(x.toBigDecimal(g)), g).round(mc)) { bounds, precision ->
            CertifiedMath.ln(bounds, precision).divide(CertifiedMath.ln(DecimalBounds.point(BigDecimal(2)), precision), precision)
        }
    }

    fun logBase(base: Num, x: Num, mc: MathContext): Num {
        if (base.signum() <= 0 || base == Num.ONE) throw ArithFault.Domain("invalid logarithm base", "log")
        if (x.signum() <= 0) throw ArithFault.Domain("logarithm of a non-positive number", "log")
        if (base is Rational && base.isInteger && base.num > BigInteger.ONE)
            integerLogExact(x, base.num)?.let { return it }
        // log_b(x) = ln x / ln b, computed in decimal.
        val g = guard(mc)
        val lnx = BigDecimalMath.log(NumericLimits.transcendental(x.toBigDecimal(g)), g)
        val lnb = BigDecimalMath.log(NumericLimits.transcendental(base.toBigDecimal(g)), g)
        val value = lnx.divide(lnb, g).round(mc)
        val precision = enclosurePrecision(mc)
        val xBounds = x.bounds(precision) ?: return Dec(value)
        val baseBounds = base.bounds(precision) ?: return Dec(value)
        return CertifiedMath.ln(xBounds, precision).divide(CertifiedMath.ln(baseBounds, precision), precision).around(value)
    }

    /** Exact `log_base(x)` when x is an integer power of base (e.g. log10(1000) = 3). */
    private fun integerLogExact(x: Num, base: BigInteger): Num? {
        if (x is Rational && x.num == BigInteger.ONE && x.den > BigInteger.ONE)
            return integerLogExact(Num.of(x.den), base)?.let { -it }
        val i = x.toBigIntegerExactOrNull() ?: return null
        if (i.signum() <= 0) return null
        var v = i
        var k = 0L
        while (v != BigInteger.ONE) {
            val (q, r) = v.divideAndRemainder(base)
            if (r.signum() != 0) return null
            v = q
            k++
        }
        return Num.of(k)
    }

    // -----------------------------------------------------------------------
    // Trigonometry (radians in / out) — the evaluator applies the angle mode.
    // -----------------------------------------------------------------------

    // The standard angles have exact values; the kernel can only approximate them,
    // and an approximation compared against an exact surd is unequal — which is how
    // `sin(45) = √2/2` came to answer FALSE. ExactTrig answers those angles and
    // returns null everywhere else, so there is still exactly one sin. The table is
    // pinned to the kernel by property test (ExactTrigSpec) — that is what makes a
    // hand-written table legitimate rather than a second implementation (Article 12).
    fun sin(x: Num, mc: MathContext): Num =
        ExactTrig.sin(x) ?: pointTrig(x, mc, CertifiedMath::sin)
            ?: decFn(x, mc, CertifiedMath::sin) { v, m -> BigDecimalMath.sin(NumericLimits.trigonometric(v), m) }

    fun cos(x: Num, mc: MathContext): Num =
        ExactTrig.cos(x) ?: pointTrig(x, mc, CertifiedMath::cos)
            ?: decFn(x, mc, CertifiedMath::cos) { v, m -> BigDecimalMath.cos(NumericLimits.trigonometric(v), m) }

    /** Exact/known-point inputs need one certified kernel, not a second nominal-only kernel. */
    private fun pointTrig(x: Num, mc: MathContext, calculate: (DecimalBounds, Int) -> DecimalBounds): Num? {
        if (x is Dec && x.absoluteError?.signum() != 0) return null
        val g = guard(mc)
        NumericLimits.trigonometric(x.toBigDecimal(g)) // preserve the original input/precision guards
        val precision = enclosurePrecision(mc)
        val interval = calculate(x.bounds(precision)!!, precision)
        val nominal = interval.lower.add(interval.upper).divide(BigDecimal(2)).round(mc)
        return interval.around(nominal)
    }

    fun tan(x: Num, mc: MathContext): Num =
        ExactTrig.tan(x) ?: decFn(x, mc, CertifiedMath::tan) { v, m -> BigDecimalMath.tan(NumericLimits.trigonometric(v), m) }

    // The inverse direction needs the fast path for the same reason the forward one
    // does: `asin(1/2) = 30` answered FALSE until the table was inverted. The domain
    // check runs FIRST, so asin(5) is still a Domain fault rather than a table miss.
    fun asin(x: Num, mc: MathContext): Num {
        requireUnitInterval(x, "asin")
        return ExactTrig.asin(x) ?: decFn(x, mc, CertifiedMath::asin) { v, m -> BigDecimalMath.asin(v, m) }
    }

    fun acos(x: Num, mc: MathContext): Num {
        requireUnitInterval(x, "acos")
        ExactTrig.acos(x)?.let { return it }
        return decFn(x, mc, CertifiedMath::acos) { v, m -> BigDecimalMath.acos(v, m) }
    }

    fun atan(x: Num, mc: MathContext): Num =
        ExactTrig.atan(x) ?: decFn(x, mc, CertifiedMath::atan) { v, m -> BigDecimalMath.atan(v, m) }

    fun atan2(y: Num, x: Num, mc: MathContext): Num {
        if (y.isZero && x.isZero) throw ArithFault.Domain("atan2(0, 0) is undefined", "atan2")
        val xs = try { x.establishedSign("atan2 quadrant") } catch (_: ArithFault.Unsupported) { null }
        val ys = try { y.establishedSign("atan2 quadrant") } catch (_: ArithFault.Unsupported) { null }
        val pi = SymConst.of(Num.ONE, Konst.PI)
        if (x.isZero && ys != null && ys != 0) return if (ys > 0) pi / Num.TWO else -pi / Num.TWO
        if (y.isZero && xs != null && xs != 0) return if (xs > 0) Num.ZERO else pi
        if (xs != null && ys != null && xs != 0) ExactTrig.atan(y / x)?.let {
            return if (xs > 0) it else if (ys >= 0) it + pi else it - pi
        }
        val precision = enclosurePrecision(mc)
        val yBounds = y.bounds(precision)
        val xBounds = x.bounds(precision)
        // Establish the rectangle's domain before a nominal (0,0) reaches the backend.
        val enclosure = if (yBounds != null && xBounds != null) CertifiedMath.atan2(yBounds, xBounds, precision) else null
        if (y.toBigDecimal(mc).signum() == 0 && x.toBigDecimal(mc).signum() == 0)
            throw ArithFault.Unsupported("atan2's nominal origin has no established angle")
        val g = guard(mc)
        val value = BigDecimalMath.atan2(NumericLimits.transcendental(y.toBigDecimal(g)), NumericLimits.transcendental(x.toBigDecimal(g)), g).round(mc)
        return enclosure?.around(value) ?: Dec(value)
    }

    fun sinh(x: Num, mc: MathContext): Num = decFn(x, mc, CertifiedMath::sinh) { v, m -> BigDecimalMath.sinh(NumericLimits.exponential(v), m) }
    fun cosh(x: Num, mc: MathContext): Num = decFn(x, mc, CertifiedMath::cosh) { v, m -> BigDecimalMath.cosh(NumericLimits.exponential(v), m) }
    fun tanh(x: Num, mc: MathContext): Num = decFn(x, mc, CertifiedMath::tanh) { v, m -> BigDecimalMath.tanh(NumericLimits.exponential(v), m) }
    fun asinh(x: Num, mc: MathContext): Num = decFn(x, mc, CertifiedMath::asinh) { v, m -> BigDecimalMath.asinh(v, m) }

    fun acosh(x: Num, mc: MathContext): Num {
        if (x < Num.ONE) throw ArithFault.Domain("acosh is defined only for x ≥ 1", "acosh")
        return decFn(x, mc, CertifiedMath::acosh) { v, m -> BigDecimalMath.acosh(v, m) }
    }

    fun atanh(x: Num, mc: MathContext): Num {
        if (x <= -Num.ONE || x >= Num.ONE) throw ArithFault.Domain("atanh is defined only for |x| < 1", "atanh")
        return decFn(x, mc, CertifiedMath::atanh) { v, m -> BigDecimalMath.atanh(v, m) }
    }

    private fun decFn(x: Num, mc: MathContext, enclosure: ((DecimalBounds, Int) -> DecimalBounds)? = null,
        f: (BigDecimal, MathContext) -> BigDecimal): Num {
        val g = guard(mc)
        val value = f(NumericLimits.transcendental(x.toBigDecimal(g)), g).round(mc)
        return if (enclosure == null) Dec(value) else enclosed(x, mc, value, enclosure)
    }

    private fun enclosurePrecision(mc: MathContext) = minOf(maxOf(66, mc.precision + 16), NumericLimits.MAX_EXPANSION_PRECISION)

    private fun enclosed(x: Num, mc: MathContext, value: BigDecimal, calculate: (DecimalBounds, Int) -> DecimalBounds): Num {
        val precision = enclosurePrecision(mc)
        val argument = x.bounds(precision) ?: return Dec(value)
        return calculate(argument, precision).around(value)
    }

    private fun requireUnitInterval(x: Num, fn: String) {
        if (x < -Num.ONE || x > Num.ONE) throw ArithFault.Domain("$fn is defined only for -1 ≤ x ≤ 1", fn)
    }

    // -----------------------------------------------------------------------
    // Combinatorics — exact BigInteger.
    // -----------------------------------------------------------------------

    fun factorial(n: Num): Num {
        val i = nonNegativeInteger(n, "factorial")
        if (i > BigInteger.valueOf(NumericLimits.MAX_FACTORIAL_ARGUMENT.toLong())) throw ArithFault.Overflow("factorial argument exceeds ${NumericLimits.MAX_FACTORIAL_ARGUMENT}")
        var acc = BigInteger.ONE
        var k = BigInteger.valueOf(2)
        while (k <= i) {
            acc = NumericLimits.multiply(acc, k)
            k += BigInteger.ONE
        }
        return Num.of(acc)
    }

    /** Permutations nPr = n! / (n-r)!, computed as a falling product (exact). */
    fun nPr(n: Num, r: Num): Num {
        val nn = nonNegativeInteger(n, "nPr")
        val rr = nonNegativeInteger(r, "nPr")
        if (rr > nn) throw ArithFault.Domain("nPr requires r ≤ n", "nPr")
        if (rr > BigInteger.valueOf(NumericLimits.MAX_COMBINATORIC_TERMS.toLong())) throw ArithFault.Overflow("nPr term budget exceeded")
        var acc = BigInteger.ONE
        var k = nn
        val stop = nn - rr
        while (k > stop) {
            acc = NumericLimits.multiply(acc, k)
            k -= BigInteger.ONE
        }
        return Num.of(acc)
    }

    /** Combinations nCr = n! / (r!(n-r)!), via a multiplicative formula (exact). */
    fun nCr(n: Num, r: Num): Num {
        val nn = nonNegativeInteger(n, "nCr")
        var rr = nonNegativeInteger(r, "nCr")
        if (rr > nn) throw ArithFault.Domain("nCr requires r ≤ n", "nCr")
        if (rr > nn - rr) rr = nn - rr // symmetry: fewer multiplications
        if (rr > BigInteger.valueOf(NumericLimits.MAX_COMBINATORIC_TERMS.toLong())) throw ArithFault.Overflow("nCr term budget exceeded")
        var acc = BigInteger.ONE
        var i = BigInteger.ONE
        while (i <= rr) {
            val factor = nn - rr + i
            val cancel = factor.gcd(i)
            // The remaining denominator divides the current binomial exactly.
            acc = NumericLimits.multiply(acc / (i / cancel), factor / cancel)
            i += BigInteger.ONE
        }
        return Num.of(acc)
    }

    // -----------------------------------------------------------------------
    // Rounding family — int, frac, floor, ceil, round, mod, min, max.
    // -----------------------------------------------------------------------

    /** Truncate toward zero. */
    fun truncate(x: Num): Num = Num.of(integerPart(x))

    /**
     * Fractional part: x − truncate(x), carrying the sign of x.
     *
     * Never taken by the tower's own subtraction once the value is not a plain fraction: that
     * rounds the value to fifty digits first, and `frac(π·10^50)` came out as 0 (ninth review).
     */
    fun frac(x: Num): Num = when (x) {
        is Rational -> x - truncate(x)
        // Every digit the decimal holds counts.
        is Dec -> if (x.absoluteError?.signum() == 0) Dec(x.value.remainder(BigDecimal.ONE), BigDecimal.ZERO)
            else x - Num.of(integerDecision(x, RoundingMode.DOWN))
        else -> {
            val whole = integerPart(x)
            if (whole.signum() == 0) x // below one in size: its own fractional part, still exact
            else {
                val value = inside(
                    cancellingDifference(digits(whole), "the value is too large for its fractional part to be taken",
                        { x.toBigDecimal(it) }, { BigDecimal(whole) }),
                    BigDecimal.ONE, "the fractional part is too close to 1 to be written in ${Num.MC.precision} digits")
                // The integer part was proved above. Subtract it from a directed enclosure,
                // retaining enough leading digits for cancellation within the same work budget.
                val precision = minOf(NumericLimits.MAX_EXPANSION_PRECISION, digits(whole) + 66)
                x.bounds(precision)!!.subtract(DecimalBounds.point(BigDecimal(whole)), precision).around(value)
            }
        }
    }

    fun floor(x: Num): Num = Num.of(floorInt(x))

    fun ceil(x: Num): Num = -floor(-x)

    /** Round to the nearest integer, ties away from zero (`2.5 → 3`, `−2.5 → −3`). */
    fun round(x: Num): Num {
        if (x is Dec) return Num.of(integerDecision(x, RoundingMode.HALF_UP))
        val sign = x.signum()
        if (sign == 0) return Num.ZERO
        val nearest = when (val size = x.abs()) {
            is Rational -> floorInt(size + Num.HALF) // floor(|x| + 1/2), exact
            // Adding the half in the tower would round a long decimal to fifty digits first.
            is Dec -> integerDecision(size, RoundingMode.HALF_UP)
            // floor(|x| + 1/2) = (floor(2|x|) + 1) div 2, and 2|x| is still exact, so its whole
            // part is settled the way floor's is. Fifty digits put round(√(10^50+10^25)) one too high.
            else -> (floorInt(size * Num.TWO) + BigInteger.ONE).shiftRight(1)
        }
        return Num.of(if (sign < 0) nearest.negate() else nearest)
    }

    /**
     * Floored modulo: the result carries the sign of the divisor.
     *
     * `a − b·⌊a/b⌋` is only taken in the tower when the quotient is an exact fraction. Otherwise
     * the subtraction cancels as many leading digits as the quotient has, and at fifty digits
     * `mod(10^60, π)` came out as 10000000000 (ninth review).
     */
    fun mod(a: Num, b: Num): Num = modulo(a, b, null)

    private fun modulo(a: Num, b: Num, doubt: BigDecimal?, confirmed: Boolean = false): Num {
        if (a is Dec || b is Dec) {
            if (isDecimal(a) && isDecimal(b) &&
                (a !is Dec || a.absoluteError?.signum() == 0) && (b !is Dec || b.absoluteError?.signum() == 0))
                return decimalMod(a, b, null, false)
            if ((a !is Dec || a.absoluteError?.signum() == 0) && (b !is Dec || b.absoluteError?.signum() == 0))
                return modulo(if (a is Dec) heldValue(a) else a, if (b is Dec) heldValue(b) else b, null)
            val left = a.bounds() ?: throw ArithFault.Unsupported(UNKNOWN_UNCERTAINTY)
            val right = b.bounds() ?: throw ArithFault.Unsupported(UNKNOWN_UNCERTAINTY)
            val quotient = left.divide(right, 66)
            val lower = quotient.lower.setScale(0, RoundingMode.FLOOR).toBigIntegerExact()
            val upper = quotient.upper.setScale(0, RoundingMode.FLOOR).toBigIntegerExact()
            if (lower != upper) throw ArithFault.Unsupported("the quotient's uncertainty crosses an integer; the remainder is not determined")
            return a - b * Num.of(lower)
        }
        if (b.isZero) throw ArithFault.DivideByZero("mod by zero")
        if (a.isZero) return a
        // An exact quotient q leaves the exact remainder b·(q − ⌊q⌋): 3π mod 2π is π.
        exactQuotient(a, b)?.let { q -> return b * (q - floor(q)) }
        val estimated = if (a is Dec || b is Dec) doubt else null
        return if (isDecimal(a) && isDecimal(b)) decimalMod(a, b, estimated, confirmed) else irrationalMod(a, b, estimated, confirmed)
    }

    // -----------------------------------------------------------------------
    // The same family as the calculator offers it.
    //
    // An estimate (a decimal approximation) carries no error bound. Read digit for digit, one
    // that should be a whole number falls on either side of it by the luck of its last digit:
    // floor(8·(1+√2)/(1+√2)) read 7, mod(8·(1+√2), 1+√2) read the divisor, and 1.2 answered
    // floor(ln(125)/ln(5)) = 2 (tenth review). So the evaluator hands these functions the
    // argument as the engine's fifty digits give it, with a measure of how far it can be off
    // ([doubt], below). An estimate that lies within that of a whole number, without being
    // exactly it, is read as that whole number, and the answer is itself an estimate (shown
    // with ≈). One whose whole part is beyond what its digits can reach is refused. Exact values
    // are untouched. The one-argument forms above stay as they are for the engine's own use,
    // where a decimal is taken at the digits it holds.
    //
    // Two earlier remedies did not hold. Refusing every estimate near a whole number turned
    // away ordinary questions (eleventh review). A fixed allowance of two digits in fifty is
    // smaller than the error of an ill-conditioned step, and floor(tan(atan(25))) read 24
    // (twelfth review).
    // -----------------------------------------------------------------------

    // [confirmed]: the first coarser computations gave the very same value (see [readsAs]).
    fun truncate(x: Num, doubt: BigDecimal, confirmed: Boolean = false): Num =
        truncate(x)

    fun floor(x: Num, doubt: BigDecimal, confirmed: Boolean = false): Num =
        floor(x)

    fun ceil(x: Num, doubt: BigDecimal, confirmed: Boolean = false): Num =
        ceil(x)

    fun frac(x: Num, doubt: BigDecimal, confirmed: Boolean = false): Num =
        frac(x)

    fun mod(a: Num, b: Num, quotientDoubt: BigDecimal, confirmed: Boolean = false): Num = modulo(a, b, quotientDoubt, confirmed)

    fun round(x: Num, doubt: BigDecimal, confirmed: Boolean = false): Num {
        return round(x)
    }

    /**
     * How far an estimate computed at the engine's fifty digits can be off, measured and not
     * assumed: by how far it moved from the same quantity computed at forty-five and at forty
     * digits. Rounding shrinks as the precision rises, by a factor of 10⁵ and 10¹⁰ over those
     * steps; a true distance from a whole number does not move at all. A hundredfold is allowed
     * on each. Two lower precisions are needed because an estimate can be exactly whole at one
     * of them by chance. The coarser values come from a pass in which the tower's own sums and
     * products are coarser too (see [Tower]), so the digits a cancellation took are measured
     * like any others. A value that does not move (an exact one, or an estimate kept in Ans or a
     * register, which has only the digits it was stored with) has no measured doubt, and is left
     * with the two digits in fifty that rounded arithmetic disturbs.
     */
    fun doubt(fine: Num, at45: Num, at40: Num): BigDecimal =
        if (fine !is Dec) BigDecimal.ZERO else movement(fine.value, at45.toBigDecimal(Num.MC), at40.toBigDecimal(Num.MC))

    /** The same for the quotient a/b, which is what a remainder hinges on. */
    fun quotientDoubt(a: Num, b: Num, a45: Num, b45: Num, a40: Num, b40: Num): BigDecimal {
        if (a !is Dec && b !is Dec) return BigDecimal.ZERO
        fun quotient(x: Num, y: Num): BigDecimal? =
            if (y.isZero) null else x.toBigDecimal(Num.MC).divide(y.toBigDecimal(Num.MC), Num.MC)
        val fine = quotient(a, b) ?: return BigDecimal.ZERO
        // A divisor that is zero when it is worked out more coarsely leaves nothing to measure the
        // quotient against: mod(7.5, 10^49 + √2 − 10^49) divided by 1 and answered 0.5.
        val at45 = quotient(a45, b45) ?: return BigDecimal.ONE
        val at40 = quotient(a40, b40) ?: return BigDecimal.ONE
        return movement(fine, at45, at40)
    }

    /**
     * How far the finest of three values can be off, from the two steps down to the coarser
     * ones. [digits] is the precision of the first coarser value, forty-five for a rounding
     * function.
     *
     * Rounding shrinks by 10⁵ for every five digits, as a rule, and the steps are scaled down
     * by that with a hundredfold to spare. The rule has exceptions, and the two steps can tell
     * (fourteenth review):
     *  - Seen through a square root or an inverse sine at the very edge of its domain, rounding
     *    shrinks by 10^2.5. Where the second step is at least a hundred times the first, the
     *    rate the two show is used: what is left at the finest precision is to the first step
     *    as the first step is to the second.
     *  - Where it is not, the steps show little or no rate, and nothing says the value has
     *    stopped moving: it is trusted only as far as the three agree.
     *    asin(sin(20)² + cos(20)²) is 90 to forty-three digits at forty-five, and 8·10⁻²⁴ short
     *    of it at fifty; 10^49 + √2 − 10^49 is 1 at fifty and 0 at forty-five.
     *
     * A step no larger than two units of the coarser precision is no step at all. It is the
     * coarser value having no room for the finer one's digits: tanh(53) is 1 at forty-five
     * digits and at forty, and truly short of 1 at fifty; 10^44 + √2 has no room for its
     * fraction at forty-five; 2^140, held as an estimate, has forty-three digits and is rounded
     * at forty. What such a value can be off by is within the two digits in fifty that every
     * estimate is allowed. (Two units and no more: tan(atan(23)) is two units over 23 at
     * forty-five digits and five under at forty, and that is rounding which must be seen.)
     */
    private fun movement(fine: BigDecimal, at45: BigDecimal, at40: BigDecimal, digits: Int = 45): BigDecimal {
        val five = step(fine, at45, digits)
        val ten = step(fine, at40, digits - 5)
        val scaled = ten.scaleByPowerOfTen(-8).max(five.scaleByPowerOfTen(-3))
        if (five.signum() == 0) return scaled
        // As far as the three agree; less, where the second step is so much larger that a rate
        // shows. The rate is read from the steps as they are: 39!/e is 24 out at forty-five digits
        // and a quarter of a million out at forty, where its last digit is the millions.
        val first = fine.subtract(at45).abs()
        val second = fine.subtract(at40).abs()
        val agreed = first.add(second)
        val shown = if (second > first) first.multiply(first).divide(second, Num.MC).scaleByPowerOfTen(2).min(agreed) else agreed
        return scaled.max(shown)
    }

    /** How far [coarser], worked out to [digits], is from [fine]; nothing, when that is within two units of its last digit. */
    private fun step(fine: BigDecimal, coarser: BigDecimal, digits: Int): BigDecimal {
        val moved = fine.subtract(coarser).abs()
        if (fine.signum() == 0) return moved
        val unit = BigDecimal.ONE.scaleByPowerOfTen((NumericLimits.adjustedExponent(fine) - digits + 1).toInt())
        return if (moved > unit.multiply(TWO_UNITS)) moved else BigDecimal.ZERO
    }

    /**
     * The same measure for a quantity worked out at a session's own precision: how far it moved
     * when everything in it was worked out with five and with ten fewer digits, [digits] being
     * the precision of the first of those. Either pass may be missing (it failed, or the session
     * has too few digits to take ten from); with neither there is no measure, and null says so.
     */
    fun movement(fine: Num, fiveFewer: Num?, tenFewer: Num?, digits: Int): BigDecimal? {
        val value = fine.toBigDecimal(Num.MC)
        val five = fiveFewer?.toBigDecimal(Num.MC)
        val ten = tenFewer?.toBigDecimal(Num.MC)
        return when {
            five != null && ten != null -> movement(value, five, ten, digits)
            five != null -> value.subtract(five).abs().scaleByPowerOfTen(-3)
            ten != null -> value.subtract(ten).abs().scaleByPowerOfTen(-8)
            else -> null
        }
    }

    /**
     * The measure for a session with too few digits to take five from: how far the quantity is
     * from the same one worked out with five MORE digits, a hundredfold allowed as before. The
     * finer value is the truth as far as this session's digits go, so the distance is the error
     * itself and needs no scaling. It says nothing about the tower's own rounding, which no
     * session can compute more finely.
     */
    fun distance(value: Num, fiveMore: Num): BigDecimal =
        value.toBigDecimal(Num.MC).subtract(fiveMore.toBigDecimal(Num.MC)).abs().scaleByPowerOfTen(2)

    private fun estimate(whole: BigInteger): Num = Dec(BigDecimal(whole))

    /** The whole number an estimate reads as, or null when it is exact, exactly whole, or clear of every whole number. */
    /**
     * [nearLine] for twice a value that is to be rounded. Next to an even number the value
     * itself is next to a whole number and rounds to it from either side, so nothing hinges on
     * which side it is: only a doubt of a quarter of a unit or more is refused there. 39!/e is
     * 0.024 short of a whole number with a doubt of 0.03, and its rounding is not in doubt.
     */
    private fun nextToAHalf(twice: BigDecimal, doubt: BigDecimal, confirmed: Boolean): BigInteger? {
        val nearest = twice.setScale(0, RoundingMode.HALF_EVEN).toBigInteger()
        if (nearest.testBit(0)) return nearLine(twice, doubt, confirmed, ESTIMATE_TOO_LARGE, ESTIMATE_UNSETTLED)
        if (twice.abs().scaleByPowerOfTen(-NumericLimits.TRUSTED_DIGITS) >= HALF_UNIT) throw ArithFault.Unsupported(ESTIMATE_TOO_LARGE)
        if (doubt >= HALF_UNIT) throw ArithFault.Unsupported(ESTIMATE_UNSETTLED)
        return null
    }

    private fun readsAsWhole(x: Num, doubt: BigDecimal, confirmed: Boolean): BigInteger? =
        if (x is Dec) nearLine(x.value, doubt, confirmed, ESTIMATE_TOO_LARGE, ESTIMATE_UNSETTLED) else null

    /**
     * The whole number [value] lies within its allowance of, without being exactly it; null when
     * it is exactly whole (taken as held) or stands clear. The allowance is the larger of the
     * measured [doubt] and the two digits in fifty that rounded arithmetic disturbs. Below one
     * half in size the nearest whole number is 0 and the gap is the value itself, never within
     * its own allowance, so nothing is read as 0.
     *
     * Reading a value as whole is sound only while the allowance is tiny beside a unit: then a
     * value that merely happens to lie within it is one in a billion. With a wider allowance a
     * value inside it may as well be a true fraction, and one whose allowance reaches half a
     * unit has no whole part at all. Both are refused.
     */
    private fun nearLine(value: BigDecimal, doubt: BigDecimal, confirmed: Boolean, tooLarge: String, unsettled: String): BigInteger? {
        if (value.signum() == 0) return null
        val rounding = value.abs().scaleByPowerOfTen(-NumericLimits.TRUSTED_DIGITS)
        if (rounding >= HALF_UNIT) throw ArithFault.Unsupported(tooLarge)
        if (doubt >= HALF_UNIT) throw ArithFault.Unsupported(unsettled)
        val nearest = value.setScale(0, RoundingMode.HALF_EVEN)
        val gap = value.subtract(nearest).abs()
        // Exactly whole as held. That is taken as it stands, unless it was measured to be unsettled:
        // 10^44 + √1.0000002 − 10^44 holds 1.00000, the digits that would tell it from 1 swallowed.
        if (gap.signum() == 0) {
            if (doubt > SETTLED) throw ArithFault.Unsupported(unsettled)
            return null
        }
        return if (readsAs(gap, rounding, doubt, confirmed, unsettled)) nearest.toBigIntegerExact() else null
    }

    /**
     * Whether a value that lies [gap] from a whole number, and not on it, is read as that
     * number. It is when the gap is within the allowance: the two digits in fifty ([rounding])
     * or the measured [doubt], whichever is larger. Two things stand in the way.
     *
     * A value the first coarser computations reproduce exactly ([confirmed]) is not explained
     * by rounding: rounding would have come out differently at forty-five digits. Its own digits
     * stand, beyond the two in fifty. 10^38 + 0.74 − 10^38 + 0.2599999999 is 0.9999999999 at
     * fifty digits and at forty-five; only the forty-digit sum, which has no room for 0.74,
     * differs, and on the strength of that one the value was read as 1 (fourteenth review).
     *
     * And an allowance too wide to mean anything is refused: see [SETTLED] and [BASE_SETTLED].
     */
    private fun readsAs(gap: BigDecimal, rounding: BigDecimal, doubt: BigDecimal, confirmed: Boolean, unsettled: String): Boolean {
        if (gap > rounding.max(doubt)) return false
        if (confirmed && gap > rounding) return false
        if (doubt > SETTLED || rounding > BASE_SETTLED) throw ArithFault.Unsupported(unsettled)
        return true
    }

    /**
     * The widest measured doubt within which a value is read as the whole number beside it.
     * Inside a wider one it could as well be a true fraction, and is refused. This was a
     * billionth of a unit until the fourteenth review: once the tower's own lost digits were
     * measured, that read a value 10⁻¹⁰ short of 1 as 1. A number that close to a whole one is
     * easily written down; one within 10⁻²⁰ is not.
     */
    private val SETTLED = BigDecimal("1E-20")

    /** The same limit for the two digits in fifty alone, where nothing was measured: a billionth of a unit, as it was. */
    private val BASE_SETTLED = BigDecimal("1E-9")

    private val HALF_UNIT = BigDecimal("0.5")
    private val TWO_UNITS = BigDecimal(2)
    private const val ESTIMATE_TOO_LARGE =
        "this is an estimate, and it is too large for the digits it can be trusted to reach the units"
    internal const val ESTIMATE_UNSETTLED =
        "this is an estimate whose whole part is not settled: it changes when it is computed more closely"
    private const val QUOTIENT_TOO_LARGE =
        "the quotient involves an estimate and is too large for the digits it can be trusted to reach its units"
    private const val QUOTIENT_UNSETTLED =
        "the quotient involves an estimate and its whole part is not settled: it changes when it is computed more closely"

    private fun isDecimal(x: Num): Boolean = x is Rational || x is Dec

    private fun exactQuotient(a: Num, b: Num): Rational? {
        val mayCancel = (a is Rational && b is Rational) ||
            (a is SymConst && b is SymConst && a.konst == b.konst) ||
            (a is Surd && b is Surd) // √8 over √2 is 2; √3 over √2 stays a surd
        return if (mayCancel) (a / b) as? Rational else null
    }

    /** A finite decimal is taken at the digits it holds, so the remainder is found exactly, then rounded once. */
    private fun decimalMod(a: Num, b: Num, doubt: BigDecimal?, confirmed: Boolean): Num {
        val dividend = heldValue(a)
        val divisor = heldValue(b)
        val ratio = (dividend / divisor) as Rational // a fraction over a fraction
        if (doubt != null) {
            val rounding = ratio.toBigDecimal(Num.MC).abs().scaleByPowerOfTen(-NumericLimits.TRUSTED_DIGITS)
            if (rounding >= HALF_UNIT) throw ArithFault.Unsupported(QUOTIENT_TOO_LARGE)
            if (doubt >= HALF_UNIT) throw ArithFault.Unsupported(QUOTIENT_UNSETTLED)
            // A quotient that reads as a whole number is one: nothing is left over.
            if (!ratio.isInteger) {
                val gap = (ratio - Num.of(floorInt(ratio + Num.HALF))).abs().toBigDecimal(Num.MC)
                if (readsAs(gap, rounding, doubt, confirmed, QUOTIENT_UNSETTLED)) return Dec(BigDecimal.ZERO)
            } else if (doubt > SETTLED) {
                // exactly whole as held, but measured to be unsettled: mod(10^44 + √0.9999998 − 10^44, 1)
                throw ArithFault.Unsupported(QUOTIENT_UNSETTLED)
            }
        }
        val quotient = floorInt(ratio)
        if (quotient.signum() == 0) return a
        val exact = dividend - divisor * Num.of(quotient)
        val point = (exact as Rational).toExactBigDecimalOrNull()
        if (point != null) return Dec(point, BigDecimal.ZERO)
        val remainder = exact.toBigDecimal(Num.MC)
        return exact.bounds()!!.around(inside(remainder, divisor.toBigDecimal(Num.MC), REMAINDER_AT_THE_DIVISOR))
    }

    private fun heldValue(x: Num): Rational = when (x) {
        is Rational -> x
        is Dec -> x.value.let {
            if (it.scale() >= 0) Num.of(it.unscaledValue(), BigInteger.TEN.pow(it.scale()))
            else Num.of(NumericLimits.multiply(it.unscaledValue(), BigInteger.TEN.pow(-it.scale())))
        }
        else -> throw IllegalArgumentException("not a decimal value")
    }

    /**
     * The quotient is irrational here (a surd or a multiple of π or e against something it does
     * not cancel with), so it is never a whole number: its floor is settled from as many digits
     * as it needs, and the remainder is taken from expansions wide enough to survive the
     * cancellation.
     */
    private fun irrationalMod(a: Num, b: Num, doubt: BigDecimal?, confirmed: Boolean): Num {
        if (doubt != null) {
            val ratio = a.toBigDecimal(Num.MC).divide(b.toBigDecimal(Num.MC), Num.MC)
            val rounding = ratio.abs().scaleByPowerOfTen(-NumericLimits.TRUSTED_DIGITS)
            if (rounding >= HALF_UNIT) throw ArithFault.Unsupported(QUOTIENT_TOO_LARGE)
            if (doubt >= HALF_UNIT) throw ArithFault.Unsupported(QUOTIENT_UNSETTLED)
            // Below one half in size the nearest whole number is 0 and the gap is the quotient
            // itself, which is never within its own allowance. Otherwise a quotient that reads as
            // a whole number is one, and nothing is left over.
            if (ratio.abs() >= HALF_UNIT) {
                val gap = ratio.subtract(ratio.setScale(0, RoundingMode.HALF_EVEN)).abs()
                val whole = if (gap.signum() != 0) readsAs(gap, rounding, doubt, confirmed, QUOTIENT_UNSETTLED) else {
                    if (doubt > SETTLED || rounding > BASE_SETTLED) throw ArithFault.Unsupported(QUOTIENT_UNSETTLED)
                    true
                }
                if (whole) return Dec(BigDecimal.ZERO)
            }
        }
        val quotient = settled("the quotient is too large, or too close to a whole number, for the remainder to be taken") {
            a.toBigDecimal(it).divide(b.toBigDecimal(it), it)
        }.setScale(0, RoundingMode.FLOOR).toBigIntegerExact()
        if (quotient.signum() == 0) return a // 0 < a/b < 1: a is its own remainder, kept as it is
        val times = BigDecimal(quotient)
        val remainder = cancellingDifference(digits(quotient), "the quotient is too large for the remainder to be taken",
            { a.toBigDecimal(it) }, { b.toBigDecimal(it).multiply(times) })
        return Dec(inside(remainder, b.toBigDecimal(Num.MC), REMAINDER_AT_THE_DIVISOR))
    }

    private val REMAINDER_AT_THE_DIVISOR = "the remainder is too close to the divisor to be written in ${Num.MC.precision} digits"

    fun min(a: Num, b: Num): Num = extremum(a, b, false)
    fun max(a: Num, b: Num): Num = extremum(a, b, true)

    private fun extremum(a: Num, b: Num, maximum: Boolean): Num {
        if (a !is Dec && b !is Dec) return if ((a >= b) == maximum) a else b
        fun choose(x: BigDecimal, y: BigDecimal) = if (maximum) maxOf(x, y) else minOf(x, y)
        val value = choose(a.toBigDecimal(Num.MC), b.toBigDecimal(Num.MC))
        val left = a.bounds() ?: return Dec(value)
        val right = b.bounds() ?: return Dec(value)
        return DecimalBounds(choose(left.lower, right.lower), choose(left.upper, right.upper)).around(value)
    }

    fun sign(x: Num): Num {
        if (x !is Dec) return Num.of(x.signum().toLong())
        val interval = x.bounds() ?: throw ArithFault.Unsupported("this estimate has no established error bound; its sign is not determined")
        return when {
            interval.lower.signum() > 0 -> Num.ONE
            interval.upper.signum() < 0 -> -Num.ONE
            interval.lower.signum() == 0 && interval.upper.signum() == 0 -> Num.ZERO
            else -> throw ArithFault.Unsupported("the estimate's uncertainty includes zero; its sign is not determined")
        }
    }

    fun gcd(a: Num, b: Num): Num {
        val ai = integerOnly(a, "gcd")
        val bi = integerOnly(b, "gcd")
        return Num.of(ai.gcd(bi))
    }

    fun lcm(a: Num, b: Num): Num {
        val ai = integerOnly(a, "lcm")
        val bi = integerOnly(b, "lcm")
        if (ai.signum() == 0 || bi.signum() == 0) return Num.ZERO
        return Num.of(NumericLimits.multiply(ai / ai.gcd(bi), bi).abs())
    }

    /** √(x² + y²), exact when it can be (`hypot(3,4) → 5`). */
    fun hypot(x: Num, y: Num, mc: MathContext): Num = sqrt(x * x + y * y, mc)

    // -----------------------------------------------------------------------
    // Small shared guards.
    // -----------------------------------------------------------------------

    private fun integerPart(x: Num): BigInteger = when (x) {
        is Rational -> x.num / x.den // BigInteger division truncates toward zero
        is Dec -> integerDecision(x, RoundingMode.DOWN)
        else -> settled(x).toBigInteger()
    }

    private fun floorInt(x: Num): BigInteger = when (x) {
        is Rational -> {
            val (q, r) = x.num.divideAndRemainder(x.den) // den > 0
            if (r.signum() < 0) q - BigInteger.ONE else q
        }
        is Dec -> integerDecision(x, RoundingMode.FLOOR)
        else -> settled(x).setScale(0, RoundingMode.FLOOR).toBigIntegerExact()
    }

    /**
     * An exact irrational — a surd, or a multiple of π or e — as a decimal whose whole part is
     * settled. The tower's fifty digits cannot settle it once the value has about fifty integer
     * digits, or lies closer to a whole number than fifty digits show: `ceil(π·10^50)` read off
     * them was one too small, and `ceil(√(10^50+1))` was 10²⁵ where it must be 10²⁵+1. Such a
     * value is never itself a whole number, so more digits always separate it from one; past
     * the working-precision budget the answer is a refusal. For ordinary values the first,
     * fifty-digit expansion is what is returned, exactly as before.
     */
    private fun settled(x: Num): BigDecimal =
        settled("the value is too large, or too close to a whole number, for its whole part to be taken") { x.toBigDecimal(it) }

    /** The same for any quantity known never to be a whole number, given as its expansion to a precision. */
    private fun settled(refusal: String, expansion: (MathContext) -> BigDecimal): BigDecimal {
        var precision = Num.MC.precision
        while (true) {
            val value = expansion(MathContext(precision, RoundingMode.HALF_EVEN))
            val wholeDigits = maxOf(0, value.precision() - value.scale())
            // More whole digits than the widest expansion can get past: nothing wider settles it,
            // so the refusal comes after this one expansion and not after five more.
            if (wholeDigits + SETTLE_GUARD > NumericLimits.MAX_EXPANSION_PRECISION) throw ArithFault.Unsupported(refusal)
            val fractionDigits = precision - wholeDigits
            val nearest = value.setScale(0, RoundingMode.HALF_EVEN)
            val gap = value.subtract(nearest).abs()
            if (gap.signum() != 0) {
                // Next to zero the sign alone decides. Next to any other whole number the gap
                // has to stand clear of the last digits kept.
                if (nearest.signum() == 0) return value
                if (fractionDigits >= SETTLE_GUARD && gap.precision() - gap.scale() - 1 >= -(fractionDigits - SETTLE_GUARD)) return value
            }
            // The widest expansion, not the working budget: an irrational asked for the whole
            // budget has no room for its own guard digits and answers with a precision fault.
            if (precision >= NumericLimits.MAX_EXPANSION_PRECISION) throw ArithFault.Unsupported(refusal)
            // Straight past the whole digits: a precision that does not reach beyond them cannot settle.
            precision = minOf(maxOf(precision * 2, wholeDigits + SETTLE_GUARD + 1), NumericLimits.MAX_EXPANSION_PRECISION)
        }
    }

    /** A part that must stay inside its range: fifty digits can round it onto the edge. */
    private fun inside(part: BigDecimal, edge: BigDecimal, refusal: String): BigDecimal {
        if (part.abs().compareTo(edge.abs()) >= 0) throw ArithFault.Unsupported(refusal)
        return part
    }

    private fun digits(n: BigInteger): Int = if (n.signum() == 0) 0 else BigDecimal(n.abs()).precision()

    private const val SETTLE_GUARD = 10

    private fun nonNegativeInteger(x: Num, fn: String): BigInteger {
        val i = x.toBigIntegerExactOrNull() ?: throw ArithFault.Domain("$fn requires an integer", fn)
        if (i.signum() < 0) throw ArithFault.Domain("$fn requires a non-negative integer", fn)
        return i
    }

    private fun integerOnly(x: Num, fn: String): BigInteger =
        knownInteger(x, fn)

    private fun knownInteger(x: Num, fn: String): BigInteger {
        if (x is Dec && x.absoluteError?.signum() != 0) throw ArithFault.Unsupported("$fn requires a known integer, not an uncertain estimate")
        return x.toBigIntegerExactOrNull() ?: throw ArithFault.Domain("$fn requires integers", fn)
    }

    private fun integerDecision(x: Dec, rounding: RoundingMode): BigInteger {
        if (x.absoluteError?.signum() == 0) return x.value.setScale(0, rounding).toBigIntegerExact()
        val interval = x.bounds() ?: throw ArithFault.Unsupported(UNKNOWN_UNCERTAINTY)
        val lower = interval.lower.setScale(0, rounding).toBigIntegerExact()
        val upper = interval.upper.setScale(0, rounding).toBigIntegerExact()
        if (lower != upper) throw ArithFault.Unsupported("the estimate's uncertainty crosses a rounding boundary; the whole number is not determined")
        return lower
    }

    private const val UNKNOWN_UNCERTAINTY = "this estimate has no established error bound; its whole part cannot be determined"
}
