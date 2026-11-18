package com.vaibhav.calc.core.num

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Closed real interval. Endpoints are rounded outwards, never inferred from agreement of probes. */
internal data class DecimalBounds(val lower: BigDecimal, val upper: BigDecimal) {
    init { require(lower <= upper) }
    val containsZero get() = lower.signum() <= 0 && upper.signum() >= 0
    fun negate() = DecimalBounds(upper.negate(), lower.negate())
    fun round(precision: Int) = DecimalBounds(lower.round(down(precision)), upper.round(up(precision)))
    fun add(b: DecimalBounds, precision: Int) = DecimalBounds(
        lower.add(b.lower, down(precision)), upper.add(b.upper, up(precision)))
    fun subtract(b: DecimalBounds, precision: Int) = add(b.negate(), precision)
    fun multiply(b: DecimalBounds, precision: Int): DecimalBounds {
        // Keep every corner's resource preflight, including corners not selected below.
        NumericLimits.decimalProduct(lower, b.lower)
        NumericLimits.decimalProduct(lower, b.upper)
        NumericLimits.decimalProduct(upper, b.lower)
        NumericLimits.decimalProduct(upper, b.upper)
        val floor = down(precision)
        val ceiling = up(precision)
        // Monotonicity selects the extrema without computing and rounding every corner
        // twice. Only two intervals crossing zero need two candidates per endpoint.
        return when {
            lower.signum() >= 0 -> when {
                b.lower.signum() >= 0 -> DecimalBounds(lower.multiply(b.lower, floor), upper.multiply(b.upper, ceiling))
                b.upper.signum() <= 0 -> DecimalBounds(upper.multiply(b.lower, floor), lower.multiply(b.upper, ceiling))
                else -> DecimalBounds(upper.multiply(b.lower, floor), upper.multiply(b.upper, ceiling))
            }
            upper.signum() <= 0 -> when {
                b.lower.signum() >= 0 -> DecimalBounds(lower.multiply(b.upper, floor), upper.multiply(b.lower, ceiling))
                b.upper.signum() <= 0 -> DecimalBounds(upper.multiply(b.upper, floor), lower.multiply(b.lower, ceiling))
                else -> DecimalBounds(lower.multiply(b.upper, floor), lower.multiply(b.lower, ceiling))
            }
            b.lower.signum() >= 0 -> DecimalBounds(lower.multiply(b.upper, floor), upper.multiply(b.upper, ceiling))
            b.upper.signum() <= 0 -> DecimalBounds(upper.multiply(b.lower, floor), lower.multiply(b.lower, ceiling))
            else -> DecimalBounds(
                minOf(lower.multiply(b.upper, floor), upper.multiply(b.lower, floor)),
                maxOf(lower.multiply(b.lower, ceiling), upper.multiply(b.upper, ceiling)),
            )
        }
    }
    fun divide(b: DecimalBounds, precision: Int): DecimalBounds {
        if (b.containsZero) throw ArithFault.Unsupported("the divisor's uncertainty includes zero")
        if (b.lower.compareTo(b.upper) == 0) {
            // Dividing by a known point is monotone (reversed for a negative point).
            // Taylor recurrences mostly divide by integers: avoid forming a rounded
            // reciprocal and then multiplying all four endpoint pairs each time.
            val divisor = b.lower
            val low = if (divisor.signum() > 0) lower else upper
            val high = if (divisor.signum() > 0) upper else lower
            return DecimalBounds(
                NumericLimits.decimal(low.divide(divisor, down(precision))),
                NumericLimits.decimal(high.divide(divisor, up(precision))),
            )
        }
        return multiply(DecimalBounds(BigDecimal.ONE.divide(b.upper, down(precision)),
            BigDecimal.ONE.divide(b.lower, up(precision))), precision)
    }
    fun around(value: BigDecimal): Dec = Dec(value,
        maxOf(value.subtract(lower).abs(), upper.subtract(value).abs()))

    companion object {
        fun point(value: BigDecimal) = DecimalBounds(value, value)
        fun down(precision: Int) = MathContext(precision, RoundingMode.FLOOR)
        fun up(precision: Int) = MathContext(precision, RoundingMode.CEILING)
    }
}

/** Exact values can be enclosed without assigning guessed accuracy to a legacy estimate. */
internal fun Num.bounds(precision: Int = 66): DecimalBounds? {
    NumericLimits.precision(MathContext(precision))
    return when (this) {
        is Dec -> absoluteError?.let { DecimalBounds(value.subtract(it), value.add(it)).round(precision) }
        is Rational -> DecimalBounds(BigDecimal(num).divide(BigDecimal(den), DecimalBounds.down(precision)),
            BigDecimal(num).divide(BigDecimal(den), DecimalBounds.up(precision)))
        is Surd -> coeff.bounds(precision)!!.multiply(DecimalEnclosures.sqrt(BigDecimal(radicand), precision), precision)
        is SymConst -> coeff.bounds(precision)!!.multiply(DecimalEnclosures.constant(konst, precision), precision)
    }
}

/** A branch may use this sign only when it is shared by every possible value. */
internal fun Num.establishedSign(context: String): Int {
    if (this !is Dec) return signum()
    val interval = bounds() ?: throw ArithFault.Unsupported("$context needs a known error bound")
    return when {
        interval.lower.signum() > 0 -> 1
        interval.upper.signum() < 0 -> -1
        interval.lower.signum() == 0 && interval.upper.signum() == 0 -> 0
        else -> throw ArithFault.Unsupported("$context cannot be decided because the uncertainty includes zero")
    }
}

/** For tools whose statistical/domain semantics do not yet propagate uncertain observations. */
internal fun Num.requireKnownPoint(context: String) {
    if (this is Dec && absoluteError?.signum() != 0) {
        throw ArithFault.Unsupported("$context requires known point values; uncertain observations are not supported")
    }
}

/**
 * Enclosures use directed decimal arithmetic and explicit mathematical remainder bounds.
 * The existing sqrt kernel supplies only a candidate: squaring both endpoints verifies it.
 */
internal object DecimalEnclosures {
    private val constants = LinkedHashMap<Pair<Konst, Int>, DecimalBounds>()

    fun sqrt(value: BigDecimal, precision: Int): DecimalBounds {
        if (value.signum() < 0) throw ArithFault.Domain("square root of a negative number", "sqrt")
        if (value.signum() == 0) return DecimalBounds.point(BigDecimal.ZERO)
        val candidate = squareRoot(value, MathContext(minOf(precision + 8, NumericLimits.MAX_WORKING_PRECISION)))
        var radius = BigDecimal.ONE.scaleByPowerOfTen((NumericLimits.adjustedExponent(candidate) - precision + 1).toInt())
        repeat(16) {
            val lower = candidate.subtract(radius).max(BigDecimal.ZERO)
            val upper = candidate.add(radius)
            if (lower.multiply(lower) <= value && upper.multiply(upper) >= value)
                return DecimalBounds(lower, upper).round(precision)
            radius = radius.multiply(BigDecimal.TEN)
        }
        throw ArithFault.Unsupported("could not establish a square-root error bound within the working budget")
    }

    @Synchronized
    fun constant(kind: Konst, precision: Int): DecimalBounds {
        val key = kind to precision
        constants[key]?.let { return it }
        val working = minOf(precision + 16, NumericLimits.MAX_WORKING_PRECISION)
        val result = when (kind) {
            Konst.PI -> atanReciprocal(5, working).multiply(DecimalBounds.point(BigDecimal(16)), working)
                .subtract(atanReciprocal(239, working).multiply(DecimalBounds.point(BigDecimal(4)), working), working)
            Konst.E -> expOne(working)
        }.round(precision)
        if (constants.size >= 16) constants.remove(constants.keys.first())
        constants[key] = result
        return result
    }

    /** Machin's identity: pi = 16 atan(1/5) - 4 atan(1/239); each series is alternating. */
    private fun atanReciprocal(divisor: Int, precision: Int): DecimalBounds {
        val one = DecimalBounds.point(BigDecimal.ONE)
        val z = one.divide(DecimalBounds.point(BigDecimal(divisor)), precision)
        val zSquared = z.multiply(z, precision)
        var power = z
        var sum = DecimalBounds.point(BigDecimal.ZERO)
        val target = BigDecimal.ONE.scaleByPowerOfTen(-(precision - 4))
        for (index in 0..(precision * 2 + 64)) {
            val term = power.divide(DecimalBounds.point(BigDecimal(2 * index + 1)), precision)
            sum = if (index and 1 == 0) sum.add(term, precision) else sum.subtract(term, precision)
            power = power.multiply(zSquared, precision)
            val next = power.upper.divide(BigDecimal(2 * index + 3), DecimalBounds.up(precision))
            if (next <= target) return DecimalBounds(sum.lower.subtract(next), sum.upper.add(next))
        }
        throw ArithFault.Unsupported("constant expansion exceeds its work budget")
    }

    /** Positive e-series; after term k, its tail is at most twice the next term. */
    private fun expOne(precision: Int): DecimalBounds {
        var term = DecimalBounds.point(BigDecimal.ONE)
        var sum = term
        val target = BigDecimal.ONE.scaleByPowerOfTen(-(precision - 4))
        for (index in 1..(precision * 2 + 64)) {
            term = term.divide(DecimalBounds.point(BigDecimal(index)), precision)
            sum = sum.add(term, precision)
            val next = term.upper.divide(BigDecimal(index + 1), DecimalBounds.up(precision))
            val remainder = next.multiply(BigDecimal(2))
            if (remainder <= target) return DecimalBounds(sum.lower, sum.upper.add(remainder))
        }
        throw ArithFault.Unsupported("constant expansion exceeds its work budget")
    }
}
