package com.vaibhav.calc.core.symbolic

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.num.*
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Conservative domain screening, separate from quadrature's estimated error. */
internal class ContinuousRange(
    private val settings: Settings,
    private val variable: String,
    private val lower: BigDecimal,
    private val upper: BigDecimal,
    private val checkpoint: () -> Unit,
) {
    private val mc = settings.computeMc
    private val evaluator = Evaluator(settings, Environment())
    private val zero = BigDecimal.ZERO
    private val one = BigDecimal.ONE
    private val pi by lazy { Num.pi(mc) }
    private val halfPi by lazy { pi.divide(BigDecimal(2), mc) }
    private data class Interval(val lo: BigDecimal, val hi: BigDecimal) {
        val crossesZero get() = lo.signum() <= 0 && hi.signum() >= 0
    }

    private var largestMagnitude = BigDecimal.ZERO
    fun check(e: Expr): BigDecimal { interval(e); return largestMagnitude }

    private fun record(r: Interval): Interval {
        largestMagnitude = maxOf(largestMagnitude, r.lo.abs(), r.hi.abs())
        return r
    }

    private fun evaluate(e: Expr): BigDecimal = when (val result = evaluator.eval(e)) {
        is CalcResult.Err -> throw SymbolicFault(result.error)
        is CalcResult.Ok -> (result.value as? RealValue)?.num?.toBigDecimal(mc)
            ?: domain("numerical integration needs real scalar values", e.span)
    }

    private fun fn(name: String, x: BigDecimal): BigDecimal = evaluate(call(name, number(Dec(x))))
    private fun endpoints(name: String, r: Interval): Interval {
        val a = fn(name, r.lo)
        val b = fn(name, r.hi)
        return Interval(minOf(a, b), maxOf(a, b))
    }

    private fun interval(e: Expr): Interval {
        checkpoint()
        if (!Expressions.dependsOn(e, variable)) {
            val constant = evaluate(e)
            if (constant.abs() > BigDecimal("1e100")) unsupported("expression range exceeds the numerical integration budget", e.span)
            return record(Interval(constant, constant))
        }
        val r = when (e) {
            is NumberLit -> e.value.toBigDecimal(mc).let { Interval(it, it) }
            is NameRef -> if (e.name == variable) Interval(lower, upper) else evaluate(e).let { Interval(it, it) }
            is Unary -> interval(e.operand).let { if (e.op == UnaryOp.NEG) Interval(it.hi.negate(), it.lo.negate()) else it }
            is Binary -> {
                val a = interval(e.left)
                val b = interval(e.right)
                when (e.op) {
                    BinaryOp.ADD -> Interval(checkedSum(a.lo, b.lo), checkedSum(a.hi, b.hi))
                    BinaryOp.SUB -> Interval(checkedSum(a.lo, b.hi.negate()), checkedSum(a.hi, b.lo.negate()))
                    BinaryOp.MUL -> products(a, b)
                    BinaryOp.DIV -> {
                        if (b.crossesZero) unsupported("cannot establish a continuous integral: denominator may be zero on this interval; split the bounds", e.right.span)
                        if (minOf(b.lo.abs(), b.hi.abs()) < maxOf(b.lo.abs(), b.hi.abs()).multiply(BigDecimal("1e-12"), mc)) {
                            unsupported("denominator varies too sharply for a reliable numerical estimate; split the interval", e.right.span)
                        }
                        products(a, Interval(one.divide(b.hi, mc), one.divide(b.lo, mc)))
                    }
                    BinaryOp.POW -> power(e, a, b)
                }
            }
            is Call -> {
                if (e.args.size != 1) unsupported("numerical integration currently supports unary continuous functions", e.span)
                val a = interval(e.args.single())
                when (e.name) {
                    "sin", "cos" -> trig(e.name, a)
                    "tan" -> {
                        val leftCell = a.lo.add(halfPi, mc).divide(pi, 0, RoundingMode.FLOOR)
                        val rightCell = a.hi.add(halfPi, mc).divide(pi, 0, RoundingMode.FLOOR)
                        val closestPole = leftCell.multiply(pi, mc).subtract(halfPi, mc)
                        if (leftCell != rightCell || a.lo.subtract(closestPole, mc).abs() < BigDecimal("1e-24")) {
                            unsupported("tan may cross a pole on these bounds; split the interval", e.span)
                        }
                        endpoints(e.name, a)
                    }
                    "ln", "log10", "log2" -> {
                        if (a.lo.signum() <= 0) unsupported("logarithm argument is not established positive throughout the interval", e.span)
                        endpoints(e.name, a)
                    }
                    "sqrt" -> {
                        if (a.lo.signum() < 0) unsupported("square-root argument is not established non-negative throughout the interval", e.span)
                        endpoints(e.name, a)
                    }
                    "asin", "acos" -> {
                        if (a.lo < one.negate() || a.hi > one) unsupported("inverse sine/cosine argument leaves [-1, 1]", e.span)
                        endpoints(e.name, a)
                    }
                    "acosh" -> {
                        if (a.lo < one) unsupported("acosh argument may be below 1", e.span)
                        endpoints(e.name, a)
                    }
                    "atanh" -> {
                        if (a.lo <= one.negate() || a.hi >= one) unsupported("atanh argument must stay strictly between -1 and 1", e.span)
                        endpoints(e.name, a)
                    }
                    "abs" -> if (a.crossesZero) Interval(zero, maxOf(a.lo.abs(), a.hi.abs())) else endpoints(e.name, a)
                    "cosh" -> {
                        if (maxOf(a.lo.abs(), a.hi.abs()) > BigDecimal(230)) unsupported("hyperbolic function argument exceeds the integration budget", e.span)
                        val end = endpoints(e.name, a)
                        if (a.crossesZero) Interval(one, end.hi) else end
                    }
                    "exp" -> {
                        if (a.hi > BigDecimal(230) || a.lo < BigDecimal(-10000)) unsupported("exponential argument exceeds the integration budget", e.span)
                        endpoints(e.name, a)
                    }
                    "sinh", "tanh" -> {
                        if (maxOf(a.lo.abs(), a.hi.abs()) > BigDecimal(230)) unsupported("hyperbolic function argument exceeds the integration budget", e.span)
                        endpoints(e.name, a)
                    }
                    "cbrt", "atan", "asinh" -> endpoints(e.name, a)
                    else -> unsupported("numerical integration cannot establish continuity for '${e.name}'", e.span)
                }
            }
            else -> unsupported("use a real scalar expression for numerical integration", e.span)
        }
        if (maxOf(r.lo.abs(), r.hi.abs()) > BigDecimal("1e100")) unsupported("expression range exceeds the numerical integration budget", e.span)
        return record(r)
    }

    private fun products(a: Interval, b: Interval): Interval {
        val values = listOf(a.lo.multiply(b.lo, mc), a.lo.multiply(b.hi, mc), a.hi.multiply(b.lo, mc), a.hi.multiply(b.hi, mc))
        return Interval(values.minOrNull()!!, values.maxOrNull()!!)
    }

    private fun checkedSum(a: BigDecimal, b: BigDecimal): BigDecimal {
        val exact = a.add(b)
        val rounded = exact.round(mc)
        if (exact.subtract(rounded).abs() > BigDecimal("1e-24")) {
            unsupported("expression loses too much precision in an intermediate sum; simplify or rescale it before integration")
        }
        return rounded
    }

    private fun power(e: Binary, a: Interval, b: Interval): Interval {
        if (b.lo.compareTo(b.hi) != 0) {
            if (a.lo.signum() <= 0) unsupported("a variable power needs a strictly positive base throughout the interval", e.span)
            val logs = endpoints("ln", a)
            val exponents = products(logs, b)
            if (exponents.hi > BigDecimal(230) || exponents.lo < BigDecimal(-10000)) unsupported("power exceeds the integration budget", e.span)
            return endpoints("exp", exponents)
        }
        val exactExponent = (Expressions.simplifyLocal(e.right) as? NumberLit)?.value
        val exponent = exactExponent ?: Dec(b.lo)
        if (b.lo.abs() > BigDecimal(128)) unsupported("numeric integration powers are limited to magnitude 128", e.span)
        if (b.lo.signum() < 0 && a.crossesZero) unsupported("negative power may have a pole inside the interval", e.span)
        val rational = exponent as? Rational
        val integer = exponent.toBigIntegerExactOrNull()
        if (a.lo.signum() < 0 && integer == null && (rational == null || !rational.den.testBit(0))) {
            unsupported("power may leave the real domain on this interval", e.span)
        }
        fun value(x: BigDecimal) = NumMath.pow(Dec(x), exponent, mc).toBigDecimal(mc)
        val first = value(a.lo)
        val last = value(a.hi)
        val evenNumerator = if (integer != null) !integer.testBit(0) else rational?.num?.testBit(0) == false
        val low = if (a.crossesZero && b.lo.signum() > 0 && evenNumerator) zero else minOf(first, last)
        return Interval(low, maxOf(first, last))
    }

    private fun trig(name: String, r: Interval): Interval {
        if (maxOf(r.lo.abs(), r.hi.abs()) > BigDecimal("1e8")) unsupported("trigonometric arguments exceed the numerical integration budget")
        if (r.hi.subtract(r.lo, mc) >= pi.multiply(BigDecimal(2), mc)) return Interval(one.negate(), one)
        var bounds = endpoints(name, r)
        val phase = if (name == "sin") halfPi else zero
        val first = r.lo.subtract(phase, mc).divide(pi, 0, RoundingMode.CEILING).toBigIntegerExact()
        val last = r.hi.subtract(phase, mc).divide(pi, 0, RoundingMode.FLOOR).toBigIntegerExact()
        var k = first
        while (k <= last) {
            val value = if (k.testBit(0)) one.negate() else one
            bounds = Interval(minOf(bounds.lo, value), maxOf(bounds.hi, value))
            k += BigInteger.ONE
        }
        return bounds
    }
}
