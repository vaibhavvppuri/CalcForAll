package com.vaibhav.calc.core.graph

import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.NumMath
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.signum
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.num.toBigIntegerExactOrNull
import java.math.MathContext
import java.math.RoundingMode

/**
 * Axis tick generation (spec 10.7). Steps are "nice" numbers — 1, 2 or 5 times a
 * power of ten — and **exact**, so ticks land on the same values at every frame of
 * a zoom or a grid→axes transition rather than shimmering as they round.
 */
object Axes {

    data class Ticks(val step: Num, val values: List<Num>)

    /** A guard, not a truncation: too many ticks is a failure, never a quiet clip (Article 11). */
    private const val MAX_TICKS = 10_000

    /** The nice step for a range: 1/2/5 × 10ⁿ, exact. */
    fun niceStep(range: Num, targetCount: Int, mc: MathContext): Num {
        require(range.signum() > 0) { "range must be positive (got $range)" }
        require(targetCount >= 1) { "targetCount must be ≥ 1" }

        val raw = range / Num.of(targetCount.toLong())
        // Tick placement is display geometry over nominal viewport coordinates. It must
        // not ask a mathematical rounding function to certify an uncertain logarithm.
        val point = raw.toBigDecimal(mc)
        val exponent = java.math.BigInteger.valueOf(point.precision().toLong() - point.scale() - 1L)
        val magnitude = NumMath.pow(Num.TEN, Num.of(exponent), mc)
        val normalized = raw / magnitude // in [1, 10)
        val multiplier = when {
            normalized <= Num.ONE -> Num.ONE
            normalized <= Num.TWO -> Num.TWO
            normalized <= Num.of(5) -> Num.of(5)
            else -> Num.TEN
        }
        return multiplier * magnitude
    }

    /** Ticks covering [min, max], aligned to multiples of the nice step. */
    fun ticks(min: Num, max: Num, targetCount: Int = 10, mc: MathContext = Num.MC): Ticks {
        require(min < max) { "need min < max (got $min, $max)" }
        val step = niceStep(max - min, targetCount, mc)
        val first = Num.of((min / step).toBigDecimal(mc).setScale(0, RoundingMode.CEILING).toBigIntegerExact()) * step

        val values = ArrayList<Num>()
        var v = first
        while (v <= max) {
            values += v
            v += step
            if (values.size > MAX_TICKS) {
                throw ArithFault.Overflow("tick count exceeded $MAX_TICKS — refusing to truncate silently")
            }
        }
        return Ticks(step, values)
    }

    /** Ticks for both axes of a viewport. */
    fun forViewport(v: Viewport, targetCount: Int = 10, mc: MathContext = Num.MC): Pair<Ticks, Ticks> =
        ticks(v.xMin, v.xMax, targetCount, mc) to ticks(v.yMin, v.yMax, targetCount, mc)
}
