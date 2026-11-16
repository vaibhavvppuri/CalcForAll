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
            Dec(BigDecimalMath.sqrt(x.toBigDecimal(guard(mc)), guard(mc)).round(mc))
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
        val negative = x.signum() < 0
        if (negative && n.mod(BigInteger.valueOf(2)).signum() == 0) {
            throw ArithFault.Domain("even root of a negative number", "root")
        }
        // Try an exact rational root first.
        exactRoot(x, n)?.let { return it }
        val mag = x.abs()
        val g = guard(mc)
        val radicand = NumericLimits.transcendental(mag.toBigDecimal(g))
        // exp(log(x)/n) avoids a root-kernel integer narrowing and works for
        // indices beyond Int.MAX_VALUE without work proportional to the index.
        val logarithm = BigDecimalMath.log(radicand, g).divide(BigDecimal(n), g)
        val approx = BigDecimalMath.exp(NumericLimits.exponential(logarithm), g).round(mc)
        return Dec(if (negative) approx.negate() else approx)
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
            return intPow(base, k.negate()).reciprocal()
        }
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

    private fun decPow(base: Num, exp: Num, mc: MathContext): Num {
        val g = guard(mc)
        if (base.isZero) {
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
        return Dec(BigDecimalMath.exp(NumericLimits.exponential(power), g).round(mc))
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
        return Dec(BigDecimalMath.exp(NumericLimits.exponential(x.toBigDecimal(g)), g).round(mc))
    }

    fun ln(x: Num, mc: MathContext): Num {
        if (x.signum() <= 0) throw ArithFault.Domain("logarithm of a non-positive number", "ln")
        if (x is Rational && x == Num.ONE) return Num.ZERO
        if (x is SymConst && x.konst == Konst.E && x.coeff == Num.ONE) return Num.ONE // ln(e) = 1
        val g = guard(mc)
        return Dec(BigDecimalMath.log(NumericLimits.transcendental(x.toBigDecimal(g)), g).round(mc))
    }

    fun log10(x: Num, mc: MathContext): Num {
        if (x.signum() <= 0) throw ArithFault.Domain("logarithm of a non-positive number", "log")
        integerLogExact(x, BigInteger.TEN)?.let { return it }
        val g = guard(mc)
        return Dec(BigDecimalMath.log10(NumericLimits.transcendental(x.toBigDecimal(g)), g).round(mc))
    }

    fun log2(x: Num, mc: MathContext): Num {
        if (x.signum() <= 0) throw ArithFault.Domain("logarithm of a non-positive number", "log2")
        integerLogExact(x, BigInteger.valueOf(2))?.let { return it }
        val g = guard(mc)
        return Dec(BigDecimalMath.log2(NumericLimits.transcendental(x.toBigDecimal(g)), g).round(mc))
    }

    fun logBase(base: Num, x: Num, mc: MathContext): Num {
        if (base.signum() <= 0 || base == Num.ONE) throw ArithFault.Domain("invalid logarithm base", "log")
        if (x.signum() <= 0) throw ArithFault.Domain("logarithm of a non-positive number", "log")
        // log_b(x) = ln x / ln b, computed in decimal.
        val g = guard(mc)
        val lnx = BigDecimalMath.log(NumericLimits.transcendental(x.toBigDecimal(g)), g)
        val lnb = BigDecimalMath.log(NumericLimits.transcendental(base.toBigDecimal(g)), g)
        return Dec(lnx.divide(lnb, g).round(mc))
    }

    /** Exact `log_base(x)` when x is an integer power of base (e.g. log10(1000) = 3). */
    private fun integerLogExact(x: Num, base: BigInteger): Num? {
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
        ExactTrig.sin(x) ?: decFn(x, mc) { v, m -> BigDecimalMath.sin(NumericLimits.trigonometric(v), m) }

    fun cos(x: Num, mc: MathContext): Num =
        ExactTrig.cos(x) ?: decFn(x, mc) { v, m -> BigDecimalMath.cos(NumericLimits.trigonometric(v), m) }

    fun tan(x: Num, mc: MathContext): Num =
        ExactTrig.tan(x) ?: decFn(x, mc) { v, m -> BigDecimalMath.tan(NumericLimits.trigonometric(v), m) }

    // The inverse direction needs the fast path for the same reason the forward one
    // does: `asin(1/2) = 30` answered FALSE until the table was inverted. The domain
    // check runs FIRST, so asin(5) is still a Domain fault rather than a table miss.
    fun asin(x: Num, mc: MathContext): Num {
        requireUnitInterval(x, "asin")
        return ExactTrig.asin(x) ?: decFn(x, mc) { v, m -> BigDecimalMath.asin(v, m) }
    }

    fun acos(x: Num, mc: MathContext): Num {
        requireUnitInterval(x, "acos")
        ExactTrig.acos(x)?.let { return it }
        return decFn(x, mc) { v, m -> BigDecimalMath.acos(v, m) }
    }

    fun atan(x: Num, mc: MathContext): Num =
        ExactTrig.atan(x) ?: decFn(x, mc) { v, m -> BigDecimalMath.atan(v, m) }

    fun atan2(y: Num, x: Num, mc: MathContext): Num {
        if (y.isZero && x.isZero) throw ArithFault.Domain("atan2(0, 0) is undefined", "atan2")
        val g = guard(mc)
        return Dec(BigDecimalMath.atan2(NumericLimits.transcendental(y.toBigDecimal(g)), NumericLimits.transcendental(x.toBigDecimal(g)), g).round(mc))
    }

    fun sinh(x: Num, mc: MathContext): Num = decFn(x, mc) { v, m -> BigDecimalMath.sinh(NumericLimits.exponential(v), m) }
    fun cosh(x: Num, mc: MathContext): Num = decFn(x, mc) { v, m -> BigDecimalMath.cosh(NumericLimits.exponential(v), m) }
    fun tanh(x: Num, mc: MathContext): Num = decFn(x, mc) { v, m -> BigDecimalMath.tanh(NumericLimits.exponential(v), m) }
    fun asinh(x: Num, mc: MathContext): Num = decFn(x, mc) { v, m -> BigDecimalMath.asinh(v, m) }

    fun acosh(x: Num, mc: MathContext): Num {
        if (x < Num.ONE) throw ArithFault.Domain("acosh is defined only for x ≥ 1", "acosh")
        return decFn(x, mc) { v, m -> BigDecimalMath.acosh(v, m) }
    }

    fun atanh(x: Num, mc: MathContext): Num {
        if (x <= -Num.ONE || x >= Num.ONE) throw ArithFault.Domain("atanh is defined only for |x| < 1", "atanh")
        return decFn(x, mc) { v, m -> BigDecimalMath.atanh(v, m) }
    }

    private inline fun decFn(x: Num, mc: MathContext, f: (BigDecimal, MathContext) -> BigDecimal): Num {
        val g = guard(mc)
        return Dec(f(NumericLimits.transcendental(x.toBigDecimal(g)), g).round(mc))
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

    /** Fractional part: x − truncate(x). */
    fun frac(x: Num): Num = x - truncate(x)

    fun floor(x: Num): Num = Num.of(floorInt(x))

    fun ceil(x: Num): Num = -floor(-x)

    /** Round to the nearest integer, ties away from zero (`2.5 → 3`, `−2.5 → −3`). */
    fun round(x: Num): Num {
        val sign = x.signum()
        if (sign == 0) return Num.ZERO
        val f = floorInt(x.abs() + Num.HALF) // floor(|x| + 1/2)
        return Num.of(if (sign < 0) f.negate() else f)
    }

    /** Floored modulo: result carries the sign of the divisor. */
    fun mod(a: Num, b: Num): Num {
        if (b.isZero) throw ArithFault.DivideByZero("mod by zero")
        return a - b * floor(a / b)
    }

    fun min(a: Num, b: Num): Num = if (a <= b) a else b
    fun max(a: Num, b: Num): Num = if (a >= b) a else b

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
        is Dec -> x.value.toBigInteger() // toward zero
        else -> x.toBigDecimal(Num.MC).toBigInteger()
    }

    private fun floorInt(x: Num): BigInteger = when (x) {
        is Rational -> {
            val (q, r) = x.num.divideAndRemainder(x.den) // den > 0
            if (r.signum() < 0) q - BigInteger.ONE else q
        }
        is Dec -> x.value.setScale(0, RoundingMode.FLOOR).toBigIntegerExact()
        else -> x.toBigDecimal(Num.MC).setScale(0, RoundingMode.FLOOR).toBigIntegerExact()
    }

    private fun nonNegativeInteger(x: Num, fn: String): BigInteger {
        val i = x.toBigIntegerExactOrNull() ?: throw ArithFault.Domain("$fn requires an integer", fn)
        if (i.signum() < 0) throw ArithFault.Domain("$fn requires a non-negative integer", fn)
        return i
    }

    private fun integerOnly(x: Num, fn: String): BigInteger =
        x.toBigIntegerExactOrNull() ?: throw ArithFault.Domain("$fn requires integers", fn)
}
