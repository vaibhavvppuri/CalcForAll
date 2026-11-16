package com.vaibhav.calc.core.num

import ch.obermuhlner.math.big.BigDecimalMath
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * An exact-first real number. **`Double` appears nowhere in this hierarchy** —
 * not in storage, not in computation (Constitution, Article 3). `Double` exists
 * only at the display boundary, produced by callers from [toBigDecimal].
 *
 * Four shapes, each preserving as much exactness as it can:
 *  - [Rational] — an exact `p/q` (always reduced). Decimal *and* fraction
 *    literals become this, which is why `0.1 + 0.2` is exactly `0.3`.
 *  - [Surd] — an exact `coeff · √radicand` (radicand square-free ≥ 2), so
 *    `√8` survives as `2√2` and `√2 · √2` collapses back to `2`.
 *  - [SymConst] — an exact rational multiple of π or e, so `π` stays `π`.
 *  - [Dec] — an explicit finite-precision `BigDecimal`, used only when a result
 *    genuinely has no exact closed form here (e.g. `sin(1)`, `1 + √2`). Its
 *    precision and rounding are always explicit settings, never accidents.
 *
 * Operations are *exact-first*: they stay in the closed exact set whenever they
 * can and fall back to [Dec] (still `BigDecimal`, never `Double`) only when they
 * must. This is deliberately NOT a CAS (Article 2) — there is no symbolic sum of
 * unlike terms, no variable algebra; just enough exact structure to satisfy the
 * M1.1 acceptance tests and behave like a real calculator.
 */
sealed interface Num : Comparable<Num> {

    /** The display / fallback boundary: render this number as a [BigDecimal]. */
    fun toBigDecimal(mc: MathContext): BigDecimal

    val isZero: Boolean

    companion object {
        /**
         * Internal working precision for irrational fallbacks. Generous so that
         * display rounding (far coarser) is never the limiting factor.
         */
        val MC: MathContext = MathContext(50, RoundingMode.HALF_EVEN)

        val ZERO: Rational = Rational.of(BigInteger.ZERO)
        val ONE: Rational = Rational.of(BigInteger.ONE)
        val TWO: Rational = Rational.of(BigInteger.valueOf(2))
        val TEN: Rational = Rational.of(BigInteger.TEN)
        val HALF: Rational = Rational.of(BigInteger.ONE, BigInteger.valueOf(2))

        fun of(i: Long): Rational = Rational.of(BigInteger.valueOf(i))
        fun of(i: Int): Rational = Rational.of(BigInteger.valueOf(i.toLong()))
        fun of(i: BigInteger): Rational = Rational.of(i)
        fun of(n: BigInteger, d: BigInteger): Rational = Rational.of(n, d)
        fun of(n: Long, d: Long): Rational = Rational.of(BigInteger.valueOf(n), BigInteger.valueOf(d))

        /**
         * Exact conversion of a decimal / scientific literal to a [Rational].
         * `"0.1"` → `1/10`, `"1.5e-3"` → `3/2000`. No precision is ever lost.
         */
        fun ofDecimal(bd: BigDecimal): Rational {
            NumericLimits.literal(bd)
            if (bd.signum() == 0) return ZERO
            val unscaled = bd.unscaledValue()
            val scale = bd.scale()
            return if (scale >= 0) {
                Rational.of(unscaled, BigInteger.TEN.pow(scale))
            } else {
                Rational.of(NumericLimits.multiply(unscaled, BigInteger.TEN.pow(-scale)), BigInteger.ONE)
            }
        }

        fun ofDecimal(s: String): Rational {
            NumericLimits.literalText(s)
            return ofDecimal(BigDecimal(s))
        }

        fun pi(mc: MathContext): BigDecimal { NumericLimits.precision(mc); return BigDecimalMath.pi(mc) }
        fun e(mc: MathContext): BigDecimal { NumericLimits.precision(mc); return BigDecimalMath.e(mc) }
    }
}

// ---------------------------------------------------------------------------
// Rational — the workhorse. Always reduced, denominator > 0.
// ---------------------------------------------------------------------------
class Rational private constructor(
    val num: BigInteger,
    val den: BigInteger,
) : Num {

    override val isZero: Boolean get() = num.signum() == 0
    val isInteger: Boolean get() = den == BigInteger.ONE

    override fun toBigDecimal(mc: MathContext): BigDecimal {
        NumericLimits.precision(mc)
        return BigDecimal(num).divide(BigDecimal(den), mc)
    }

    /** Exact `BigDecimal` when the fraction terminates in base 10, else null. */
    fun toExactBigDecimalOrNull(): BigDecimal? {
        // n/d terminates iff d (after reduction) has only 2s and 5s as factors.
        val five = BigInteger.valueOf(5)
        val twos = den.lowestSetBit
        var d = den.shiftRight(twos)
        var fives = 0
        while (d.mod(five).signum() == 0) { d /= five; fives++ }
        if (d != BigInteger.ONE) return null
        // A base-10-terminating fraction has exactly max(twos, fives) decimal
        // places, so this scale makes the division provably exact (no rounding).
        val scale = maxOf(twos, fives)
        return BigDecimal(num).divide(BigDecimal(den), scale, RoundingMode.UNNECESSARY)
    }

    fun toBigIntegerExactOrNull(): BigInteger? = if (isInteger) num else null

    override fun compareTo(other: Num): Int = when (other) {
        is Rational -> {
            if (this == other) 0
            else if (num.signum() != other.num.signum()) num.signum().compareTo(other.num.signum())
            else {
                val common = den.gcd(other.den)
                NumericLimits.multiply(num, other.den / common, NumericLimits.MAX_SCRATCH_BITS)
                    .compareTo(NumericLimits.multiply(other.num, den / common, NumericLimits.MAX_SCRATCH_BITS))
            }
        }
        else -> compareGeneric(this, other)
    }

    override fun equals(other: Any?): Boolean =
        other is Rational && num == other.num && den == other.den

    override fun hashCode(): Int = 31 * num.hashCode() + den.hashCode()

    override fun toString(): String = if (isInteger) num.toString() else "$num/$den"

    companion object {
        fun of(n: BigInteger, d: BigInteger = BigInteger.ONE): Rational {
            if (d.signum() == 0) throw ArithFault.DivideByZero("rational with zero denominator")
            NumericLimits.integer(n, NumericLimits.MAX_SCRATCH_BITS)
            NumericLimits.integer(d, NumericLimits.MAX_SCRATCH_BITS)
            var nn = n
            var dd = d
            if (dd.signum() < 0) {
                nn = nn.negate(); dd = dd.negate()
            }
            if (nn.signum() == 0) return Rational(BigInteger.ZERO, BigInteger.ONE)
            val g = nn.gcd(dd)
            return Rational(NumericLimits.integer(nn / g), NumericLimits.integer(dd / g))
        }
    }
}

// ---------------------------------------------------------------------------
// Surd — coeff · √radicand, radicand square-free ≥ 2, coeff ≠ 0.
// ---------------------------------------------------------------------------
class Surd private constructor(
    val coeff: Rational,
    val radicand: BigInteger,
) : Num {

    override val isZero: Boolean get() = false // coeff ≠ 0 and √radicand > 0

    override fun toBigDecimal(mc: MathContext): BigDecimal {
        val guard = NumericLimits.guard(mc)
        val root = BigDecimalMath.sqrt(BigDecimal(radicand), guard)
        return coeff.toBigDecimal(guard).multiply(root, guard).round(mc)
    }

    override fun compareTo(other: Num): Int =
        if (other is Surd && other.radicand == radicand) coeff.compareTo(other.coeff)
        else compareGeneric(this, other)

    override fun equals(other: Any?): Boolean =
        other is Surd && coeff == other.coeff && radicand == other.radicand

    override fun hashCode(): Int = 31 * coeff.hashCode() + radicand.hashCode()

    override fun toString(): String = "($coeff)√$radicand"

    companion object {
        /**
         * Build `coeff · √radicand`, pulling out perfect-square factors and
         * collapsing to a [Rational] when the radicand becomes 1. This is what
         * turns `√8` into `2√2` and `√9` into `3`.
         */
        fun of(coeff: Rational, radicand: BigInteger): Num {
            if (coeff.isZero || radicand.signum() == 0) return Num.ZERO
            if (radicand.signum() < 0) throw ArithFault.Domain("square root of a negative number", "sqrt")
            if (radicand == BigInteger.ONE) return coeff
            NumericLimits.integer(radicand)
            val (outside, inside) = extractSquareFactor(radicand)
            val newCoeff = rMul(coeff, Num.of(outside))
            return if (inside == BigInteger.ONE) newCoeff else Surd(newCoeff, inside)
        }
    }
}

enum class Konst(val symbol: String) { PI("π"), E("e") }

// ---------------------------------------------------------------------------
// SymConst — coeff · π (or e). Keeps π symbolic through scaling/adding.
// ---------------------------------------------------------------------------
class SymConst private constructor(
    val coeff: Rational,
    val konst: Konst,
) : Num {

    override val isZero: Boolean get() = false // coeff ≠ 0, constants are irrational > 0

    override fun toBigDecimal(mc: MathContext): BigDecimal {
        val guard = NumericLimits.guard(mc)
        val base = when (konst) {
            Konst.PI -> BigDecimalMath.pi(guard)
            Konst.E -> BigDecimalMath.e(guard)
        }
        return coeff.toBigDecimal(guard).multiply(base, guard).round(mc)
    }

    override fun compareTo(other: Num): Int =
        if (other is SymConst && other.konst == konst) coeff.compareTo(other.coeff)
        else compareGeneric(this, other)

    override fun equals(other: Any?): Boolean =
        other is SymConst && coeff == other.coeff && konst == other.konst

    override fun hashCode(): Int = 31 * coeff.hashCode() + konst.hashCode()

    override fun toString(): String = "($coeff)${konst.symbol}"

    companion object {
        fun of(coeff: Rational, konst: Konst): Num =
            if (coeff.isZero) Num.ZERO else SymConst(coeff, konst)
    }
}

// ---------------------------------------------------------------------------
// Dec — an explicit finite-precision decimal (still BigDecimal, never Double).
// ---------------------------------------------------------------------------
class Dec(val value: BigDecimal) : Num {

    init { NumericLimits.decimal(value) }

    override val isZero: Boolean get() = value.signum() == 0

    override fun toBigDecimal(mc: MathContext): BigDecimal {
        NumericLimits.precision(mc)
        return value.round(mc)
    }

    override fun compareTo(other: Num): Int = when (other) {
        is Dec -> value.compareTo(other.value)
        else -> compareGeneric(this, other)
    }

    override fun equals(other: Any?): Boolean = other is Dec && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    override fun toString(): String = value.toPlainString()
}

// ===========================================================================
// Arithmetic — exact-first, with a BigDecimal (never Double) fallback.
// ===========================================================================

private fun rAdd(a: Rational, b: Rational): Rational {
    // Form the least-common denominator, then cancel the shared part before
    // constructing it. Scratch numerators are bounded by two operand budgets.
    val g = a.den.gcd(b.den)
    val ad = a.den / g
    val bd = b.den / g
    val numerator = NumericLimits.add(
        NumericLimits.multiply(a.num, bd, NumericLimits.MAX_SCRATCH_BITS),
        NumericLimits.multiply(b.num, ad, NumericLimits.MAX_SCRATCH_BITS),
        NumericLimits.MAX_SCRATCH_BITS,
    )
    val cancel = numerator.gcd(g)
    return Rational.of(numerator / cancel, NumericLimits.multiply(ad, b.den / cancel))
}

private fun rMul(a: Rational, b: Rational): Rational {
    val left = a.num.gcd(b.den)
    val right = b.num.gcd(a.den)
    return Rational.of(
        NumericLimits.multiply(a.num / left, b.num / right),
        NumericLimits.multiply(a.den / right, b.den / left),
    )
}

private fun rDiv(a: Rational, b: Rational): Rational {
    if (b.isZero) throw ArithFault.DivideByZero()
    val numerator = a.num.gcd(b.num)
    val denominator = a.den.gcd(b.den)
    return Rational.of(
        NumericLimits.multiply(a.num / numerator, b.den / denominator),
        NumericLimits.multiply(a.den / denominator, b.num / numerator),
    )
}

private fun decOf(n: Num): BigDecimal = n.toBigDecimal(Num.MC)
private fun decAdd(a: Num, b: Num): Num {
    val left = decOf(a)
    val right = decOf(b)
    val sum = left.add(right, Num.MC)
    if (!a.isZero && !b.isZero && (sum.compareTo(left) == 0 || sum.compareTo(right) == 0)) {
        throw ArithFault.Unsupported("addition needs more than ${Num.MC.precision} digits to preserve both operands; rearrange or rescale the expression")
    }
    return Dec(sum)
}
private fun decMul(a: Num, b: Num): Num {
    val left = decOf(a)
    val right = decOf(b)
    NumericLimits.decimalProduct(left, right)
    return Dec(left.multiply(right, Num.MC))
}

operator fun Num.unaryMinus(): Num = when (this) {
    is Rational -> Rational.of(num.negate(), den)
    is Surd -> Surd.of(Rational.of(coeff.num.negate(), coeff.den), radicand)
    is SymConst -> SymConst.of(Rational.of(coeff.num.negate(), coeff.den), konst)
    is Dec -> Dec(value.negate())
}

operator fun Num.plus(o: Num): Num = when {
    // An estimated zero must not turn the other operand into an exact result.
    // Keep an existing Dec unchanged when the other operand is zero.
    this is Dec || o is Dec -> when {
        this.isZero && o is Dec -> o
        o.isZero && this is Dec -> this
        else -> decAdd(this, o)
    }
    this is Rational && o is Rational -> rAdd(this, o)
    this is Surd && o is Surd && radicand == o.radicand -> Surd.of(rAdd(coeff, o.coeff), radicand)
    this is SymConst && o is SymConst && konst == o.konst -> SymConst.of(rAdd(coeff, o.coeff), konst)
    this.isZero -> o          // 0 + x = x, preserving x's exact shape
    o.isZero -> this          // x + 0 = x
    else -> decAdd(this, o)
}

operator fun Num.minus(o: Num): Num = this + (-o)

operator fun Num.times(o: Num): Num = when {
    // A known exact zero is still an exact annihilator for a finite estimate.
    (this.isZero && this !is Dec) || (o.isZero && o !is Dec) -> Num.ZERO
    this is Dec || o is Dec -> if (this.isZero || o.isZero) Dec(BigDecimal.ZERO) else decMul(this, o)
    this.isZero || o.isZero -> Num.ZERO
    this is Rational && o is Rational -> rMul(this, o)
    this is Rational && o is Surd -> Surd.of(rMul(this, o.coeff), o.radicand)
    this is Surd && o is Rational -> Surd.of(rMul(coeff, o), radicand)
    this is Surd && o is Surd -> {
        // sqrt(a) sqrt(b) = gcd(a,b) sqrt((a/g)(b/g)); avoid a huge
        // intermediate radicand when the factors cancel to a small answer.
        val common = radicand.gcd(o.radicand)
        Surd.of(rMul(rMul(coeff, o.coeff), Num.of(common)),
            NumericLimits.multiply(radicand / common, o.radicand / common))
    }
    this is Rational && o is SymConst -> SymConst.of(rMul(this, o.coeff), o.konst)
    this is SymConst && o is Rational -> SymConst.of(rMul(coeff, o), konst)
    else -> decMul(this, o)
}

/** Multiplicative inverse, staying exact where the closed set allows. */
fun Num.reciprocal(): Num = when (this) {
    is Rational -> {
        if (isZero) throw ArithFault.DivideByZero()
        Rational.of(den, num)
    }
    // 1 / (c·√r) = (1 / (c·r)) · √r  — stays a surd.
    is Surd -> Surd.of(rDiv(Num.ONE, rMul(coeff, Num.of(radicand))), radicand)
    // 1 / (c·π) has no closed form here → decimal fallback.
    is SymConst -> Dec(BigDecimal.ONE.divide(toBigDecimal(Num.MC), Num.MC))
    is Dec -> {
        if (isZero) throw ArithFault.DivideByZero()
        Dec(BigDecimal.ONE.divide(value, Num.MC))
    }
}

operator fun Num.div(o: Num): Num {
    if (o.isZero) throw ArithFault.DivideByZero()
    return when {
        // Exact zero over any known nonzero finite divisor remains exact.
        this.isZero && this !is Dec -> Num.ZERO
        this is Rational && o is Rational -> rDiv(this, o)
        // (c1·π) / (c2·π) → the constant cancels, back to an exact rational.
        this is SymConst && o is SymConst && konst == o.konst -> rDiv(coeff, o.coeff)
        else -> this * o.reciprocal()
    }
}

// ===========================================================================
// Shared helpers.
// ===========================================================================

private fun compareGeneric(a: Num, b: Num): Int {
    // Compare by decimalisation, widening precision if the two look equal, so a
    // pair of distinct-but-very-close reals isn't falsely reported equal. If they
    // still agree past ~320 significant digits, treat them as equal (best effort).
    var precision = 40
    repeat(4) {
        val mc = MathContext(precision, RoundingMode.HALF_EVEN)
        val c = a.toBigDecimal(mc).compareTo(b.toBigDecimal(mc))
        if (c != 0) return c
        precision *= 2
    }
    return 0
}

fun Num.signum(): Int = when (this) {
    is Rational -> num.signum()
    is Surd -> coeff.num.signum()      // √radicand > 0, so sign follows coeff
    is SymConst -> coeff.num.signum()  // π, e > 0, so sign follows coeff
    is Dec -> value.signum()
}

fun Num.abs(): Num = if (signum() < 0) -this else this

fun Num.toBigIntegerExactOrNull(): BigInteger? = when (this) {
    is Rational -> if (isInteger) num else null
    is Dec -> try {
        value.toBigIntegerExact()
    } catch (_: ArithmeticException) {
        null
    }
    else -> null
}

fun Num.isIntegerValue(): Boolean = toBigIntegerExactOrNull() != null

/**
 * `n = outside² · inside`, with `inside` square-free as far as trial division
 * reaches (capped so pathological radicands can't stall the engine — a larger
 * square factor just stays un-extracted, which is still correct, only less tidy).
 */
internal fun extractSquareFactor(n: BigInteger): Pair<BigInteger, BigInteger> {
    NumericLimits.integer(n)
    // Fast path: a perfect square of any size collapses fully (√(k²) → k),
    // which trial division alone would miss when k is a large prime.
    val whole = integerSquareRoot(n)
    if (whole * whole == n) return whole to BigInteger.ONE

    var inside = n
    var outside = BigInteger.ONE
    val cap = BigInteger.valueOf(NumericLimits.MAX_TRIAL_DIVISOR.toLong())
    var f = BigInteger.valueOf(2)
    while (f * f <= inside && f <= cap) {
        val f2 = f * f
        while (inside.mod(f2).signum() == 0) {
            inside /= f2
            outside *= f
        }
        f = if (f == BigInteger.valueOf(2)) BigInteger.valueOf(3) else f + BigInteger.valueOf(2)
    }
    return outside to inside
}

/** Integer Newton square root using APIs present on Android 6 (API 23). */
internal fun integerSquareRoot(n: BigInteger): BigInteger {
    if (n.signum() < 0) throw ArithFault.Domain("square root of a negative integer", "sqrt")
    NumericLimits.integer(n)
    if (n <= BigInteger.ONE) return n
    var guess = BigInteger.ONE.shiftLeft((n.bitLength() + 1) / 2)
    while (true) {
        val next = (guess + n / guess).shiftRight(1)
        if (next >= guess) return guess
        guess = next
    }
}
