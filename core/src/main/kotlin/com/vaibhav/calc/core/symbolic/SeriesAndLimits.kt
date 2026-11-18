package com.vaibhav.calc.core.symbolic

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.graph.PlottableFunction
import com.vaibhav.calc.core.graph.Sample
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.name.Functions
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.bounds
import com.vaibhav.calc.core.num.establishedSign
import com.vaibhav.calc.core.num.requireKnownPoint
import com.vaibhav.calc.core.num.toBigIntegerExactOrNull
import com.vaibhav.calc.core.solve.Extraction
import com.vaibhav.calc.core.solve.Poly
import com.vaibhav.calc.core.solve.structureExtractor
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.Rational
import com.vaibhav.calc.core.num.abs
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.signum
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.num.unaryMinus
import java.math.BigInteger
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Taylor polynomials (CalcForAll 1.2): `Σ f⁽ᵏ⁾(a)/k! · (x−a)ᵏ`, built from the
 * engine's own symbolic derivative and exact evaluation, so `sin x` about 0 is
 * `x - x^3/6 + x^5/120` with exact rational coefficients. Calculus uses radians.
 */
object Series {
    const val MAX_ORDER = 10

    /** [exact] is false when any coefficient had to be rounded for display. */
    data class Taylor(val expression: String, val coefficients: List<Num>, val exact: Boolean)

    fun taylor(input: String, variable: String, center: Num, order: Int, settings: Settings): CalcResult<Taylor> {
        if (order !in 0..MAX_ORDER) return CalcResult.Err(EvalError.Domain("the order must be from 0 to $MAX_ORDER", null))
        val name = variable.lowercase()
        if (!ReservedNames.isUnknown(name)) return CalcResult.Err(EvalError.Domain("use x, y or z as the variable", null))
        val radians = settings.copy(angle = AngleMode.RAD)
        var current = input
        var factorial: Num = Num.ONE
        val coefficients = ArrayList<Num>()
        for (k in 0..order) {
            if (k > 0) {
                current = when (val d = Symbolic.differentiate(current, name)) {
                    is CalcResult.Err -> return d
                    is CalcResult.Ok -> d.value.expression
                }
                factorial = factorial * Num.of(k)
            }
            val f = when (val p = PlottableFunction.of(current, radians, Environment(), name)) {
                is CalcResult.Err -> return p
                is CalcResult.Ok -> p.value
            }
            coefficients += when (val s = f.at(center)) {
                is Sample.Defined -> s.y / factorial
                is Sample.Undefined -> return CalcResult.Err(
                    EvalError.Domain("the function or one of its derivatives is undefined at the centre", null),
                )
                is Sample.Unevaluated -> return CalcResult.Err(s.error)
            }
        }
        val exact = coefficients.none { it is Dec }
        return CalcResult.Ok(Taylor(render(coefficients, name, center, settings), coefficients, exact))
    }

    private fun render(coefficients: List<Num>, name: String, center: Num, settings: Settings): String {
        val base = when {
            center.isZero -> name
            center.signum() < 0 -> "($name+${Formatter.input(-center)})"
            else -> "($name-${Formatter.input(center)})"
        }
        fun plain(n: Num): String = when (n) {
            is Rational -> Formatter.input(n)
            is Dec -> "(${Formatter.input(Dec(n.value.round(settings.displayMc)))})"
            else -> "(${Formatter.input(n)})"
        }
        val out = StringBuilder()
        for ((k, raw) in coefficients.withIndex()) {
            if (raw.isZero) continue
            val negative = raw.signum() < 0
            val magnitude = if (negative) -raw else raw
            val power = when (k) {
                0 -> ""
                1 -> base
                else -> "$base^$k"
            }
            val term = when {
                k == 0 -> plain(magnitude)
                magnitude is Rational -> {
                    val head = if (magnitude.num == BigInteger.ONE) power else "${magnitude.num}*$power"
                    if (magnitude.den == BigInteger.ONE) head else "$head/${magnitude.den}"
                }
                else -> "${plain(magnitude)}*$power"
            }
            out.append(if (out.isEmpty()) (if (negative) "-$term" else term) else (if (negative) " - $term" else " + $term"))
        }
        return if (out.isEmpty()) "0" else out.toString()
    }
}

/**
 * Numerical limits (CalcForAll 1.2). This is an ESTIMATE from sampled values, never a
 * proof: the function is evaluated ever closer to the point from each side (or at ever
 * larger arguments), and a side counts only if its values settle. An oscillating
 * function such as `sin(1/x)` is reported as not settling rather than given a number.
 */
object Limits {
    sealed interface Side {
        /** The samples settle on [value]; [digits] significant digits agree between the last two. */
        data class Settles(val value: Num, val digits: Int) : Side
        data class Unbounded(val positive: Boolean) : Side

        /** The values were computed and do not settle (an oscillation, or convergence too slow to call). */
        object Unsettled : Side

        /** The function had no value at any sample on this side of the point (outside its domain). */
        object Undefined : Side

        /**
         * No value was obtained close to the point, although there were values further out:
         * an argument or size limit was reached, precision ran out, or the domain ends before
         * the point. This says nothing about the limit itself, and must never be reported as
         * "does not settle" or "has no value" — either would be a claim about the mathematics.
         */
        object Unevaluated : Side
    }

    /** For a limit at ±∞ only [right] is meaningful and [left] repeats it. */
    data class Estimate(val left: Side, val right: Side) {
        /** The two-sided value when both sides settle on the same number. */
        val twoSided: Side.Settles?
            get() {
                val l = left as? Side.Settles ?: return null
                val r = right as? Side.Settles ?: return null
                val digits = minOf(l.digits, r.digits)
                val mc = MathContext(digits, RoundingMode.HALF_EVEN)
                val leftValue = l.value.toBigDecimal(MathContext(PRECISION)).round(mc)
                val rightValue = r.value.toBigDecimal(MathContext(PRECISION)).round(mc)
                // Compare at the significant precision actually reported by both estimates.
                // A unit-sized absolute tolerance would erase small but real jumps.
                if (leftValue.compareTo(rightValue) != 0) return null
                return Side.Settles(Dec(leftValue.stripTrailingZeros()), digits)
            }
    }

    private val TOLERANCE = BigDecimal("0.000001")
    private const val MAX_DIGITS = 10
    private const val PRECISION = 50

    /** Limit of [input] as [variable] → [point], from both sides. Trigonometry in radians. */
    fun at(input: String, variable: String, point: Num, settings: Settings): CalcResult<Estimate> =
        guarded { atInternal(input, variable, point, settings) }

    private fun atInternal(input: String, variable: String, point: Num, settings: Settings): CalcResult<Estimate> {
        point.requireKnownPoint("limit point")
        val f = when (val p = function(input, variable, settings)) {
            is CalcResult.Err -> return p
            is CalcResult.Ok -> p.value
        }
        // Offsets of 3/(7·10ᵏ), not 1/10ᵏ: with a round offset, `1/x` would be a whole
        // number and `(1+x)^(1/x)` would ask for an exact power with a huge integer
        // exponent, which the number tower rightly refuses.
        val supportedZero = continuousZero(f, point)
        fun side(direction: Int): Side = classify((2..12).map { k ->
            f.at(point + Num.of(BigInteger.valueOf(3L * direction), BigInteger.TEN.pow(k).multiply(BigInteger.valueOf(7))))
        }, supportedZero)
        return CalcResult.Ok(Estimate(side(-1), side(1)))
    }

    /** Limit as the variable grows without bound in the [positive] or negative direction. */
    fun atInfinity(input: String, variable: String, positive: Boolean, settings: Settings): CalcResult<Estimate> =
        guarded { atInfinityInternal(input, variable, positive, settings) }

    private fun atInfinityInternal(input: String, variable: String, positive: Boolean, settings: Settings): CalcResult<Estimate> {
        val f = when (val p = function(input, variable, settings)) {
            is CalcResult.Err -> return p
            is CalcResult.Ok -> p.value
        }
        // 10ᵏ + 1/3 for the same reason: never a whole-number argument.
        val supportedZero = rationalZeroAtInfinity(f, settings.copy(angle = AngleMode.RAD))
        val side = classify((1..11).map { k ->
            val x = Num.of(BigInteger.TEN.pow(k)) + Num.of(1, 3)
            f.at(if (positive) x else -x)
        }, supportedZero)
        return CalcResult.Ok(Estimate(side, side))
    }

    private fun function(input: String, variable: String, settings: Settings): CalcResult<PlottableFunction> {
        val name = variable.lowercase()
        if (!ReservedNames.isUnknown(name)) return CalcResult.Err(EvalError.Domain("use x, y or z as the variable", null))
        val precise = settings.copy(angle = AngleMode.RAD, computePrecision = maxOf(settings.computePrecision, PRECISION))
        val parsed = PlottableFunction.of(input, precise, Environment(), name)
        if (parsed is CalcResult.Ok) {
            val pending = ArrayDeque<Pair<Expr, Int>>()
            pending.addLast(parsed.value.expr to 1)
            var count = 0
            while (pending.isNotEmpty()) {
                val (e, depth) = pending.removeLast()
                if (++count > 512 || depth > 64)
                    return CalcResult.Err(EvalError.Unsupported("limit expression exceeds 512 nodes or depth 64", null))
                children(e).forEach { pending.addLast(it to depth + 1) }
            }
        }
        return parsed
    }

    private fun classify(samples: List<Sample>, supportedZero: Boolean): Side {
        // A side with no value at ANY sample is outside the function's domain. A side that has
        // values further out but none close in is different: that is where arguments overflow
        // or a subtraction such as 1 − cos(x³) loses every digit and a division by zero follows.
        // It may also be a true edge of the domain; from samples alone the two cannot be told
        // apart, so it is reported only as "no value obtained close to the point".
        if (samples.all { it is Sample.Undefined }) return Side.Undefined
        if (samples.takeLast(5).any { it !is Sample.Defined }) return Side.Unevaluated
        val values = samples.takeLastWhile { it is Sample.Defined }.map { (it as Sample.Defined).y }
        if (values.size < 5) return Side.Unsettled
        val intervals = values.map { it.bounds() ?: return Side.Unevaluated }
        if (supportedZero) return Side.Settles(Dec(BigDecimal.ZERO), MAX_DIGITS)
        val last = values.last().toBigDecimal(MathContext(PRECISION))
        val gaps = intervals.zipWithNext { a, b ->
            maxOf(b.upper.subtract(a.lower).abs(), b.lower.subtract(a.upper).abs())
        }
        val recent = gaps.takeLast(3)
        val scale = maxOf(intervals.last().lower.abs(), intervals.last().upper.abs())
        val shrinking = recent[0] >= recent[1] && recent[1] >= recent[2]
        if (shrinking && recent[2] <= scale.multiply(TOLERANCE)) {
            val digits = agreeingDigits(recent[2], scale)
            return Side.Settles(round(Num.ofDecimal(last), digits), digits)
        }
        val tail = intervals.takeLast(5)
        fun lowerMagnitude(a: com.vaibhav.calc.core.num.DecimalBounds) =
            if (a.containsZero) BigDecimal.ZERO else minOf(a.lower.abs(), a.upper.abs())
        val growing = tail.zipWithNext { a, b -> lowerMagnitude(b) > maxOf(a.lower.abs(), a.upper.abs()) }.all { it }
        val positive = tail.all { it.lower.signum() > 0 }
        val negative = tail.all { it.upper.signum() < 0 }
        if (growing && (positive || negative) && lowerMagnitude(tail.last()) > BigDecimal(1_000_000)) return Side.Unbounded(positive)
        return Side.Unsettled
    }

    private fun agreeingDigits(gap: BigDecimal, scale: BigDecimal): Int {
        if (gap.signum() == 0) return MAX_DIGITS
        var digits = 0
        var bound = scale
        while (digits < MAX_DIGITS && gap <= bound.movePointLeft(1)) {
            bound = bound.movePointLeft(1)
            digits++
        }
        return maxOf(digits, 1)
    }

    private fun round(value: Num, digits: Int): Num {
        val rounded = value.toBigDecimal(MathContext(PRECISION)).round(MathContext(digits, RoundingMode.HALF_EVEN))
        return Dec(if (rounded.signum() == 0) java.math.BigDecimal.ZERO else rounded.stripTrailingZeros())
    }

    private fun children(e: Expr): List<Expr> = when (e) {
        is Unary -> listOf(e.operand)
        is Binary -> listOf(e.left, e.right)
        is Postfix -> listOf(e.operand)
        is Call -> e.args
        is UnitApply -> listOf(e.value)
        is NumberLit, is NameRef -> emptyList()
    }

    /** Zero is a supported structural conclusion here, never a synonym for a small sample. */
    private fun continuousZero(f: PlottableFunction, point: Num): Boolean {
        if ((f.at(point) as? Sample.Defined)?.y?.isZero != true) return false
        fun closed(e: Expr): Num? =
            ((f.evaluateClosed(e) as? CalcResult.Ok)?.value as? RealValue)?.num
        fun continuous(e: Expr): Boolean = when (e) {
            is NumberLit, is NameRef -> true
            is Unary -> continuous(e.operand)
            is Postfix -> e.op == PostfixOp.PERCENT && continuous(e.operand)
            is UnitApply -> false
            is Call -> (Functions.get(e.name)?.name ?: e.name.lowercase()) in setOf(
                "sin", "cos", "atan", "abs", "cbrt", "exp", "sinh", "cosh", "tanh", "asinh", "hypot", "min", "max",
            ) && e.args.all(::continuous)
            is Binary -> when (e.op) {
                BinaryOp.ADD, BinaryOp.SUB, BinaryOp.MUL -> continuous(e.left) && continuous(e.right)
                BinaryOp.DIV -> !Expressions.dependsOn(e.right, f.unknown) &&
                    closed(e.right)?.establishedSign("limit denominator")?.let { it != 0 } == true && continuous(e.left)
                BinaryOp.POW -> !Expressions.dependsOn(e.right, f.unknown) &&
                    closed(e.right)?.toBigIntegerExactOrNull()?.let { it.signum() >= 0 } == true && continuous(e.left)
            }
        }
        return try { continuous(f.expr) } catch (_: ArithFault) { false }
    }

    /** A proper rational function with exact coefficients tends to zero in both directions. */
    private fun rationalZeroAtInfinity(f: PlottableFunction, settings: Settings): Boolean {
        val division = f.expr as? Binary ?: return false
        if (division.op != BinaryOp.DIV) return false
        return try {
            val extractor = structureExtractor(settings, Environment())
            val numerator = (extractor.poly(division.left, f.unknown) as? Extraction.Extracted<Poly>)?.value ?: return false
            val denominator = (extractor.poly(division.right, f.unknown) as? Extraction.Extracted<Poly>)?.value ?: return false
            numerator.degree < denominator.degree && denominator.degree >= 0 &&
                numerator.coeffs.all { it is Rational } && denominator.coeffs.all { it is Rational }
        } catch (_: ArithFault) { false }
    }

    private inline fun guarded(block: () -> CalcResult<Estimate>): CalcResult<Estimate> = try { block() }
    catch (fault: ArithFault) {
        CalcResult.Err(when (fault) {
            is ArithFault.DivideByZero -> EvalError.DivideByZero(null, fault.message ?: "division by zero")
            is ArithFault.Domain -> EvalError.Domain(fault.message ?: "invalid limit domain", null)
            is ArithFault.Overflow -> EvalError.Overflow(fault.message ?: "limit calculation exceeded its numeric budget", null)
            is ArithFault.Underflow -> EvalError.Underflow(fault.message ?: "limit calculation exceeded its numeric budget", null)
            is ArithFault.Unsupported -> EvalError.Unsupported(fault.message ?: "unsupported limit estimate", null)
        })
    }
}
