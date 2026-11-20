package com.vaibhav.calc.core.num

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** Bounded elementary kernels: directed arithmetic plus explicit Taylor/geometric remainders. */
internal object CertifiedMath {
    private val ZERO = BigDecimal.ZERO
    private val ONE = BigDecimal.ONE
    private val TWO = BigDecimal(2)
    private val THREE = BigDecimal(3)
    private val HALF = BigDecimal("0.5")
    private fun point(x: BigDecimal) = DecimalBounds.point(x)
    private fun work(precision: Int) = minOf(precision + 20, NumericLimits.MAX_WORKING_PRECISION)
    private fun maxAbs(x: DecimalBounds) = maxOf(x.lower.abs(), x.upper.abs())
    private fun symmetric(radius: BigDecimal) = DecimalBounds(radius.negate(), radius)

    fun sqrt(x: DecimalBounds, precision: Int): DecimalBounds {
        if (x.lower.signum() < 0) throw ArithFault.Unsupported("the argument's uncertainty crosses the square-root domain")
        return DecimalBounds(DecimalEnclosures.sqrt(x.lower, precision).lower,
            DecimalEnclosures.sqrt(x.upper, precision).upper)
    }

    fun exp(x: DecimalBounds, precision: Int): DecimalBounds = DecimalBounds(
        expPoint(x.lower, precision).lower, expPoint(x.upper, precision).upper)

    private fun expPoint(value: BigDecimal, precision: Int): DecimalBounds {
        NumericLimits.exponential(value)
        if (value.signum() == 0) return point(ONE)
        val p = work(precision)
        var argument = value.abs()
        var squares = 0
        while (argument > HALF) { argument = argument.divide(TWO); squares++ }
        val y = point(argument)
        var term = point(ONE)
        var sum = term
        val target = ONE.scaleByPowerOfTen(-(p - 5))
        for (k in 1..(p * 2 + 64)) {
            term = term.multiply(y, p).divide(point(BigDecimal(k)), p)
            sum = sum.add(term, p)
            // All terms are positive. Every later ratio is <=1/2, hence tail <=2*next.
            val next = term.multiply(y, p).divide(point(BigDecimal(k + 1)), p)
            val tail = next.upper.multiply(TWO)
            if (tail <= target) {
                var result = DecimalBounds(sum.lower, sum.upper.add(tail))
                repeat(squares) { result = result.multiply(result, p) }
                if (value.signum() < 0) result = point(ONE).divide(result, p)
                return result.round(precision)
            }
        }
        throw ArithFault.Unsupported("exponential enclosure exceeds its work budget")
    }

    fun ln(x: DecimalBounds, precision: Int): DecimalBounds {
        if (x.lower.signum() <= 0) throw ArithFault.Unsupported("the argument's uncertainty crosses the logarithm domain")
        return DecimalBounds(lnPoint(x.lower, precision).lower, lnPoint(x.upper, precision).upper)
    }

    private val logarithmOfTen = LinkedHashMap<Int, DecimalBounds>()
    @Synchronized private fun lnTen(precision: Int): DecimalBounds {
        logarithmOfTen[precision]?.let { return it }
        val result = lnMantissa(BigDecimal.TEN, precision)
        if (logarithmOfTen.size >= 16) logarithmOfTen.remove(logarithmOfTen.keys.first())
        logarithmOfTen[precision] = result
        return result
    }

    private fun lnPoint(value: BigDecimal, precision: Int): DecimalBounds {
        if (value.compareTo(ONE) == 0) return point(ZERO)
        val p = work(precision)
        val exponent = NumericLimits.adjustedExponent(value).toInt()
        val mantissa = value.scaleByPowerOfTen(-exponent)
        val result = lnMantissa(mantissa, p).add(lnTen(p).multiply(point(BigDecimal(exponent)), p), p)
        return result.round(precision)
    }

    /** Four square roots put the mantissa in [1, 10^(1/16)], where the atanh series converges rapidly. */
    private fun lnMantissa(value: BigDecimal, precision: Int): DecimalBounds {
        if (value.compareTo(ONE) == 0) return point(ZERO)
        var root = point(value)
        repeat(4) { root = sqrt(root, precision) }
        val z = root.subtract(point(ONE), precision).divide(root.add(point(ONE), precision), precision)
        val squared = z.multiply(z, precision)
        val magnitudeSquared = maxAbs(squared)
        if (magnitudeSquared >= ONE) throw ArithFault.Unsupported("logarithm range reduction did not converge")
        var power = z
        var sum = point(ZERO)
        val target = ONE.scaleByPowerOfTen(-(precision - 5))
        for (k in 0..(precision * 2 + 64)) {
            sum = sum.add(power.divide(point(BigDecimal(2 * k + 1)), precision), precision)
            power = power.multiply(squared, precision)
            // ln(t)=2*sum(z^(2k+1)/(2k+1)); bound the omitted positive-magnitude geometric tail.
            val tail = maxAbs(power).divide(BigDecimal(2 * k + 3), DecimalBounds.up(precision))
                .divide(ONE.subtract(magnitudeSquared), DecimalBounds.up(precision))
            if (tail <= target) return sum.add(symmetric(tail), precision).multiply(point(BigDecimal(32)), precision)
        }
        throw ArithFault.Unsupported("logarithm enclosure exceeds its work budget")
    }

    fun sin(x: DecimalBounds, precision: Int): DecimalBounds = trig(x, precision, false)
    fun cos(x: DecimalBounds, precision: Int): DecimalBounds = trig(x, precision, true)
    fun tan(x: DecimalBounds, precision: Int): DecimalBounds = sin(x, precision).divide(cos(x, precision), precision)

    private fun trig(x: DecimalBounds, precision: Int, cosine: Boolean): DecimalBounds {
        val p = work(precision)
        val center = x.lower.add(x.upper).divide(TWO, MathContext(p))
        NumericLimits.trigonometric(center)
        val inputRadius = maxOf(center.subtract(x.lower).abs(), x.upper.subtract(center).abs())
        if (inputRadius >= TWO) return DecimalBounds(ONE.negate(), ONE)
        val reducedCenter: BigDecimal
        val reductionRadius: BigDecimal
        if (center.abs() <= THREE) {
            // 3 < pi: this center is already inside the Taylor kernel's range.
            // Keep the complete input radius, but avoid constructing and subtracting
            // a zero multiple of an enclosed 2*pi for every small-angle sample.
            reducedCenter = center
            reductionRadius = ZERO
        } else {
            val twoPi = DecimalEnclosures.constant(Konst.PI, p).multiply(point(TWO), p)
            val piMid = twoPi.lower.add(twoPi.upper).divide(TWO, MathContext(p))
            val turns = center.divide(piMid, 0, java.math.RoundingMode.HALF_EVEN)
            val reduced = point(center).subtract(twoPi.multiply(point(turns), p), p)
            reducedCenter = reduced.lower.add(reduced.upper).divide(TWO, MathContext(p))
            reductionRadius = maxOf(reducedCenter.subtract(reduced.lower).abs(), reduced.upper.subtract(reducedCenter).abs())
        }
        val y = point(reducedCenter)
        val square = y.multiply(y, p)
        var term = if (cosine) point(ONE) else y
        var sum = term
        var preparedTerm: DecimalBounds? = null
        val target = ONE.scaleByPowerOfTen(-(p - 5))
        for (k in 1..(p * 2 + 64)) {
            val first = if (cosine) 2 * k - 1 else 2 * k
            term = preparedTerm ?: term.multiply(square, p).divide(point(BigDecimal(first * (first + 1))), p).negate()
            sum = sum.add(term, p)
            val nextFirst = first + 2
            val next = term.multiply(square, p).divide(point(BigDecimal(nextFirst * (nextFirst + 1))), p)
            // After the first few terms on |x|<=pi the alternating terms decrease, so remainder <=next term.
            val tail = maxAbs(next)
            if (k >= 3 && tail <= target) {
                val radius = tail.add(inputRadius).add(reductionRadius) // |sin'|,|cos'|<=1 globally.
                val enclosed = sum.add(symmetric(radius), p)
                return DecimalBounds(enclosed.lower.max(ONE.negate()), enclosed.upper.min(ONE)).round(precision)
            }
            // The remainder calculation already produced the next recurrence term.
            // Its outward-rounded endpoints are exactly what the next iteration needs.
            preparedTerm = next.negate()
        }
        throw ArithFault.Unsupported("trigonometric enclosure exceeds its work budget")
    }

    fun sinh(x: DecimalBounds, precision: Int): DecimalBounds =
        exp(x, precision).subtract(exp(x.negate(), precision), precision).divide(point(TWO), precision)
    fun cosh(x: DecimalBounds, precision: Int): DecimalBounds =
        exp(x, precision).add(exp(x.negate(), precision), precision).divide(point(TWO), precision)
    fun tanh(x: DecimalBounds, precision: Int): DecimalBounds =
        sinh(x, precision).divide(cosh(x, precision), precision)

    fun atan(x: DecimalBounds, precision: Int): DecimalBounds = DecimalBounds(
        atanPoint(x.lower, precision).lower, atanPoint(x.upper, precision).upper)

    fun atan2(y: DecimalBounds, x: DecimalBounds, precision: Int): DecimalBounds {
        val p = work(precision)
        val pi = DecimalEnclosures.constant(Konst.PI, p)
        val halfPi = pi.divide(point(TWO), p)
        val result = when {
            x.lower.signum() > 0 -> atan(y.divide(x, p), p)
            x.upper.signum() < 0 && y.lower.signum() >= 0 -> atan(y.divide(x, p), p).add(pi, p)
            x.upper.signum() < 0 && y.upper.signum() < 0 -> atan(y.divide(x, p), p).subtract(pi, p)
            y.lower.signum() > 0 -> halfPi.subtract(atan(x.divide(y, p), p), p)
            y.upper.signum() < 0 -> halfPi.negate().subtract(atan(x.divide(y, p), p), p)
            else -> throw ArithFault.Unsupported("atan2 uncertainty crosses the origin or its principal-angle branch cut")
        }
        return result.round(precision)
    }

    private fun atanPoint(value: BigDecimal, precision: Int): DecimalBounds {
        if (value.signum() == 0) return point(ZERO)
        if (value.signum() < 0) return atanPoint(value.negate(), precision).negate()
        val p = work(precision)
        if (value > ONE) {
            val reciprocal = point(ONE).divide(point(value), p)
            return DecimalEnclosures.constant(Konst.PI, p).divide(point(TWO), p)
                .subtract(atan(reciprocal, p), p).round(precision)
        }
        // atan(t) = 2 atan(t/(1+sqrt(1+t^2))). Two reductions put t below 0.2.
        var t = point(value)
        repeat(2) {
            t = t.divide(point(ONE).add(sqrt(point(ONE).add(t.multiply(t, p), p), p), p), p)
        }
        val squared = t.multiply(t, p)
        var power = t
        var sum = point(ZERO)
        val target = ONE.scaleByPowerOfTen(-(p - 5))
        for (k in 0..(p * 2 + 64)) {
            val term = power.divide(point(BigDecimal(2 * k + 1)), p)
            sum = if (k and 1 == 0) sum.add(term, p) else sum.subtract(term, p)
            power = power.multiply(squared, p)
            // The positive alternating terms decrease; the omitted remainder is <=next term.
            val tail = power.upper.divide(BigDecimal(2 * k + 3), DecimalBounds.up(p))
            if (tail <= target) return sum.add(symmetric(tail), p)
                .multiply(point(BigDecimal(4)), p).round(precision)
        }
        throw ArithFault.Unsupported("arctangent enclosure exceeds its work budget")
    }

    fun asin(x: DecimalBounds, precision: Int): DecimalBounds {
        if (x.lower < ONE.negate() || x.upper > ONE)
            throw ArithFault.Unsupported("the argument's uncertainty crosses the arcsine domain")
        return DecimalBounds(asinPoint(x.lower, precision).lower, asinPoint(x.upper, precision).upper)
    }

    private fun asinPoint(value: BigDecimal, precision: Int): DecimalBounds {
        val p = work(precision)
        val x = point(value)
        // asin(x)=2 atan(x/(1+sqrt(1-x^2))); the denominator remains >=1 at the endpoints.
        val root = sqrt(point(ONE).subtract(x.multiply(x, p), p), p)
        return atan(x.divide(point(ONE).add(root, p), p), p).multiply(point(TWO), p).round(precision)
    }

    fun acos(x: DecimalBounds, precision: Int): DecimalBounds {
        val p = work(precision)
        return DecimalEnclosures.constant(Konst.PI, p).divide(point(TWO), p)
            .subtract(asin(x, p), p).round(precision)
    }

    fun asinh(x: DecimalBounds, precision: Int): DecimalBounds = DecimalBounds(
        asinhPoint(x.lower, precision).lower, asinhPoint(x.upper, precision).upper)

    private fun asinhPoint(value: BigDecimal, precision: Int): DecimalBounds {
        if (value.signum() == 0) return point(ZERO)
        if (value.signum() < 0) return asinhPoint(value.negate(), precision).negate()
        val p = work(precision)
        val x = point(value)
        return ln(x.add(sqrt(x.multiply(x, p).add(point(ONE), p), p), p), p).round(precision)
    }

    fun acosh(x: DecimalBounds, precision: Int): DecimalBounds {
        if (x.lower < ONE) throw ArithFault.Unsupported("the argument's uncertainty crosses the inverse hyperbolic cosine domain")
        fun endpoint(value: BigDecimal): DecimalBounds {
            val p = work(precision)
            val a = point(value)
            val root = sqrt(a.subtract(point(ONE), p).multiply(a.add(point(ONE), p), p), p)
            return ln(a.add(root, p), p).round(precision)
        }
        return DecimalBounds(endpoint(x.lower).lower, endpoint(x.upper).upper)
    }

    fun atanh(x: DecimalBounds, precision: Int): DecimalBounds {
        if (x.lower <= ONE.negate() || x.upper >= ONE)
            throw ArithFault.Unsupported("the argument's uncertainty crosses the inverse hyperbolic tangent domain")
        val p = work(precision)
        val ratio = point(ONE).add(x, p).divide(point(ONE).subtract(x, p), p)
        return ln(ratio, p).divide(point(TWO), p).round(precision)
    }
}
