package com.vaibhav.calc.core.symbolic

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.num.*
import java.math.BigInteger

/** A parseable expression, plus the qualifications that must travel with it. */
data class SymbolicAnswer(
    val expression: String,
    val operation: String,
    val variable: String,
    val steps: List<String>,
    val conditions: List<String>,
    val angleMode: String = "RAD",
    val constantOfIntegration: Boolean = false,
    /** A conservative signal that reuse must retain the source/result domain. */
    val domainRestricted: Boolean = false,
) {
    val display: String get() = expression + if (constantOfIntegration) " + C" else ""
}

/**
 * Bounded, real symbolic algebra over the existing parser and number tower.
 * Calculus is always in radians. Results are valid on the stated domain; this is
 * not a general-purpose CAS and unsupported forms return a typed error.
 */
object Symbolic {
    fun simplify(input: String, variable: String = "x"): CalcResult<SymbolicAnswer> = symbolicResult {
        val source = Expressions.parse(input, variable)
        val simplified = canonical(source)
        answer(source, simplified, "simplify", variable.lowercase(),
            listOf("Combine exact rational coefficients and like polynomial terms.",
                "Preserve variable denominators and other domain restrictions."))
    }

    fun expand(input: String, variable: String = "x"): CalcResult<SymbolicAnswer> = symbolicResult {
        val source = Expressions.parse(input, variable)
        val polynomial = RationalPolynomial.from(Expressions.simplifyLocal(source))
            ?: unsupported("expansion supports real polynomials with rational coefficients and non-negative integer powers up to ${RationalPolynomial.MAX_DEGREE}")
        answer(source, polynomial.toExpr(), "expand", variable.lowercase(),
            listOf("Distribute products and non-negative integer powers.", "Collect like terms using exact rational coefficients."))
    }

    fun differentiate(input: String, variable: String = "x"): CalcResult<SymbolicAnswer> = symbolicResult {
        val name = variable.lowercase()
        val source = Expressions.parse(input, name)
        val result = canonical(derivative(source, name))
        answer(source, result, "differentiate", name,
            listOf("Differentiate with respect to $name; other symbols are constant.",
                "Apply power, sum, product, quotient and chain rules in radians.",
                "Simplify exact polynomial coefficients without removing domain holes."), derivative = true)
    }

    fun integrate(input: String, variable: String = "x"): CalcResult<SymbolicAnswer> = symbolicResult {
        val name = variable.lowercase()
        val source = Expressions.parse(input, name)
        val result = canonical(antiderivative(canonical(source), name))
        answer(source, result, "integrate", name,
            listOf("Find a real antiderivative with respect to $name, in radians.",
                "Use exact power rules, linear substitutions and finite integration by parts.",
                "The constant C may differ on disconnected components of the domain."), integral = true)
    }

    /** Exact factorisation for univariate rational polynomials with rational roots. */
    fun factor(input: String, variable: String = "x"): CalcResult<SymbolicAnswer> = symbolicResult {
        val name = variable.lowercase()
        val source = Expressions.parse(input, name)
        val polynomial = RationalPolynomial.from(Expressions.simplifyLocal(source))
            ?: unsupported("factorisation requires a polynomial with rational coefficients")
        val coefficients = polynomial.coefficients(name)
            ?: unsupported("factorisation currently supports one variable at a time")
        val result = factorRational(coefficients, name)
        answer(source, result, "factor", name,
            listOf("Extract rational roots by the rational-root theorem.",
                "Divide exactly by each confirmed factor; retain any unfactored remainder."))
    }

    private fun answer(
        source: Expr, result: Expr, operation: String, name: String,
        steps: List<String>, derivative: Boolean = false, integral: Boolean = false,
    ): SymbolicAnswer {
        Expressions.checkSize(result, result = true)
        // A rule may introduce a closed expression, for example ln(a) in
        // d(a^x). Validate it even when a was written as sin(-pi/2), not a literal.
        ClosedExpressions.validate(result)
        val restrictions = (Expressions.conditions(source, derivative) + Expressions.conditions(result)).toSet()
        val conditions = linkedSetOf<String>()
        conditions += "All symbols are real; trigonometric arguments are in radians."
        conditions += restrictions
        if (derivative) conditions += "The derivative applies where the original expression is defined and differentiable."
        return SymbolicAnswer(Expressions.render(result), operation, name, steps, conditions.toList(),
            constantOfIntegration = integral,
            domainRestricted = restrictions.isNotEmpty() || !Expressions.isTotal(source) || !Expressions.isTotal(result))
    }

    internal fun canonical(e: Expr): Expr {
        val local = Expressions.simplifyLocal(e)
        val polynomial = RationalPolynomial.from(local)
        return polynomial?.toExpr() ?: local
    }

    internal fun derivative(e: Expr, name: String): Expr {
        if (!Expressions.dependsOn(e, name)) return integer(0)
        RationalPolynomial.from(e)?.let { return it.derivative(name).toExpr() }
        return when (e) {
            is NumberLit -> integer(0)
            is NameRef -> integer(if (e.name == name) 1 else 0)
            is Unary -> if (e.op == UnaryOp.NEG) neg(derivative(e.operand, name)) else derivative(e.operand, name)
            is Binary -> {
                val u = e.left
                val v = e.right
                val du = derivative(u, name)
                val dv = derivative(v, name)
                when (e.op) {
                    BinaryOp.ADD -> add(du, dv)
                    BinaryOp.SUB -> sub(du, dv)
                    BinaryOp.MUL -> add(mul(du, v), mul(u, dv))
                    BinaryOp.DIV -> div(sub(mul(du, v), mul(u, dv)), pow(v, integer(2)))
                    BinaryOp.POW -> {
                        if (!Expressions.dependsOn(v, name)) {
                            // The zero exponent needs its own rule: 0*x^-1 is not
                            // defined at zero even though d(x^0)/dx is zero there.
                            if (v.isNumber(0)) integer(0)
                            else mul(mul(v, pow(u, canonical(sub(v, integer(1))))), du)
                        } else if (!Expressions.dependsOn(u, name)) {
                            if (u.rational()?.signum()?.let { it <= 0 } == true) {
                                domain("a variable real exponent needs a positive constant base", e.span)
                            }
                            mul(mul(e, call("ln", u)), dv)
                        } else {
                            mul(e, add(mul(dv, call("ln", u)), div(mul(v, du), u)))
                        }
                    }
                }
            }
            is Call -> {
                if (e.args.size != 1) unsupported("differentiation does not support '${e.name}' with ${e.args.size} arguments", e.span)
                val u = e.args.single()
                val du = derivative(u, name)
                val outer = when (e.name) {
                    "sin" -> call("cos", u)
                    "cos" -> neg(call("sin", u))
                    "tan" -> div(integer(1), pow(call("cos", u), integer(2)))
                    "exp" -> e
                    "ln" -> div(integer(1), u)
                    "log10" -> div(integer(1), mul(u, call("ln", integer(10))))
                    "log2" -> div(integer(1), mul(u, call("ln", integer(2))))
                    "sqrt" -> div(integer(1), mul(integer(2), call("sqrt", u)))
                    "cbrt" -> div(integer(1), mul(integer(3), pow(call("cbrt", u), integer(2))))
                    "sinh" -> call("cosh", u)
                    "cosh" -> call("sinh", u)
                    "tanh" -> div(integer(1), pow(call("cosh", u), integer(2)))
                    "asin" -> div(integer(1), call("sqrt", sub(integer(1), pow(u, integer(2)))))
                    "acos" -> neg(div(integer(1), call("sqrt", sub(integer(1), pow(u, integer(2))))))
                    "atan" -> div(integer(1), add(integer(1), pow(u, integer(2))))
                    "asinh" -> div(integer(1), call("sqrt", add(pow(u, integer(2)), integer(1))))
                    "acosh" -> div(integer(1), mul(call("sqrt", sub(u, integer(1))), call("sqrt", add(u, integer(1)))))
                    "atanh" -> div(integer(1), sub(integer(1), pow(u, integer(2))))
                    "abs" -> div(u, call("abs", u))
                    else -> unsupported("no symbolic derivative rule for '${e.name}'", e.span)
                }
                mul(outer, du)
            }
            else -> unsupported("this expression is not supported by real symbolic differentiation", e.span)
        }
    }

    private fun antiderivative(e: Expr, name: String): Expr {
        if (!Expressions.dependsOn(e, name)) return mul(e, variable(name))
        RationalPolynomial.from(e)?.let { return it.antiderivative(name).toExpr() }
        return when (e) {
            is Unary -> if (e.op == UnaryOp.NEG) neg(antiderivative(e.operand, name)) else antiderivative(e.operand, name)
            is Binary -> when (e.op) {
                BinaryOp.ADD -> add(antiderivative(e.left, name), antiderivative(e.right, name))
                BinaryOp.SUB -> sub(antiderivative(e.left, name), antiderivative(e.right, name))
                BinaryOp.MUL -> when {
                    !Expressions.dependsOn(e.left, name) -> mul(e.left, antiderivative(e.right, name))
                    !Expressions.dependsOn(e.right, name) -> mul(e.right, antiderivative(e.left, name))
                    else -> integrationByParts(e.left, e.right, name)
                        ?: integrationByParts(e.right, e.left, name)
                        ?: unsupported("no supported antiderivative for this product; try a definite numerical integral", e.span)
                }
                BinaryOp.DIV -> when {
                    !Expressions.dependsOn(e.right, name) -> div(antiderivative(e.left, name), e.right)
                    !Expressions.dependsOn(e.left, name) -> {
                        val slope = affineSlope(e.right, name)
                        if (slope != null) div(mul(e.left, call("ln", call("abs", e.right))), number(slope))
                        else {
                            val denominator = RationalPolynomial.from(e.right)?.coefficients(name)
                            if (denominator != null && denominator.keys.all { it == 0 || it == 2 } &&
                                denominator[0] == Num.ONE && denominator[2] == Num.ONE) {
                                mul(e.left, call("atan", variable(name)))
                            } else if (e.right is Binary && e.right.op == BinaryOp.POW) {
                                mul(e.left, integratePower(e.right.left, canonical(neg(e.right.right)), name))
                            } else unsupported("no supported antiderivative for this quotient; try a definite numerical integral", e.span)
                        }
                    }
                    else -> unsupported("no supported antiderivative for this quotient; try a definite numerical integral", e.span)
                }
                BinaryOp.POW -> integratePower(e.left, e.right, name)
            }
            is Call -> {
                if (e.args.size != 1) unsupported("no antiderivative rule for '${e.name}'", e.span)
                val u = e.args.single()
                val slope = affineSlope(u, name)
                    ?: unsupported("'${e.name}' integration requires a linear argument with a non-zero rational slope", e.span)
                val primitive = when (e.name) {
                    "sin" -> neg(call("cos", u))
                    "cos" -> call("sin", u)
                    "exp" -> e
                    "sinh" -> call("cosh", u)
                    "cosh" -> call("sinh", u)
                    "tan" -> neg(call("ln", call("abs", call("cos", u))))
                    "ln" -> sub(mul(u, call("ln", u)), u)
                    "sqrt" -> return integratePower(u, number(Num.HALF), name)
                    "cbrt" -> return integratePower(u, number(Num.of(1, 3)), name)
                    "abs" -> div(mul(u, call("abs", u)), integer(2))
                    else -> unsupported("no symbolic antiderivative rule for '${e.name}'; try a definite numerical integral", e.span)
                }
                div(primitive, number(slope))
            }
            else -> unsupported("no symbolic antiderivative rule for this expression", e.span)
        }
    }

    private fun integratePower(base: Expr, exponent: Expr, name: String): Expr {
        if (!Expressions.dependsOn(base, name)) {
            val slope = affineSlope(exponent, name)
                ?: unsupported("an exponential integral requires a linear exponent")
            val n = base.rational()
            if (n != null && n.signum() <= 0) domain("a real exponential antiderivative needs a positive base")
            if (base.isNumber(1)) return variable(name)
            return div(pow(base, exponent), mul(number(slope), call("ln", base)))
        }
        val power = exponent.rational() ?: unsupported("the power rule requires a constant rational exponent")
        val slope = affineSlope(base, name) ?: unsupported("the power rule requires a linear base with a non-zero rational slope")
        if (power == Num.of(-1)) return div(call("ln", call("abs", base)), number(slope))
        val next = power + Num.ONE
        return div(pow(base, number(next)), number(slope * next))
    }

    private fun affineSlope(e: Expr, name: String): Rational? {
        val p = RationalPolynomial.from(canonical(e)) ?: return null
        val coeffs = p.coefficients(name) ?: return null
        if (coeffs.keys.any { it > 1 }) return null
        return coeffs[1]?.takeIf { !it.isZero }
    }

    /** Polynomial times exp/sin/cos: terminate integration by parts at degree zero. */
    private fun integrationByParts(polynomial: Expr, function: Expr, name: String): Expr? {
        val p = RationalPolynomial.from(polynomial) ?: return null
        val coefficients = p.coefficients(name) ?: return null
        if ((coefficients.keys.maxOrNull() ?: 0) > 8) unsupported("integration by parts is limited to polynomial degree 8")
        val fn = function as? Call ?: return null
        if (fn.args.size != 1 || fn.name !in setOf("exp", "sin", "cos")) return null
        val slope = affineSlope(fn.args[0], name) ?: return null
        var current = p
        var currentFunction = fn.name
        var primitiveSign = 1
        var coefficient: Num = Num.ONE
        var sum: Expr = integer(0)
        for (i in 0..(coefficients.keys.maxOrNull() ?: 0)) {
            currentFunction = when (currentFunction) {
                "sin" -> { primitiveSign = -primitiveSign; "cos" }
                "cos" -> "sin"
                else -> "exp"
            }
            coefficient = coefficient / slope
            val term = mul(mul(number(coefficient), current.toExpr()), call(currentFunction, fn.args[0]))
            val sign = (if (i % 2 == 0) 1 else -1) * primitiveSign
            sum = if (sign > 0) add(sum, term) else sub(sum, term)
            current = current.derivative(name)
        }
        return sum
    }

    private fun factorRational(coefficients: Map<Int, Rational>, name: String): Expr {
        if (coefficients.isEmpty()) return integer(0)
        var coefficientsNow = coefficients
        val factors = mutableListOf<Expr>()
        var steps = 0
        while ((coefficientsNow.keys.maxOrNull() ?: 0) > 0) {
            val degree = coefficientsNow.keys.maxOrNull()!!
            if (degree > 12) unsupported("rational factorisation is limited to degree 12")
            if (coefficientsNow[0]?.isZero != false) {
                factors += variable(name)
                coefficientsNow = coefficientsNow.filterKeys { it > 0 }.mapKeys { it.key - 1 }
                continue
            }
            var denominator = BigInteger.ONE
            for (c in coefficientsNow.values) denominator = denominator / denominator.gcd(c.den) * c.den
            val ints = coefficientsNow.mapValues { (_, c) -> c.num * (denominator / c.den) }
            val content = ints.values.fold(BigInteger.ZERO) { acc, n -> acc.gcd(n.abs()) }.coerceAtLeast(BigInteger.ONE)
            val constant = ints.getValue(0).divide(content).abs()
            val leading = ints.getValue(degree).divide(content).abs()
            if (constant > BigInteger.valueOf(1_000_000) || leading > BigInteger.valueOf(1_000_000)) break
            val numerators = divisors(constant.toInt())
            val denominators = divisors(leading.toInt())
            if (numerators.size.toLong() * denominators.size * 2 + steps > 4096) break
            val roots = numerators.flatMap { p -> denominators.flatMap { q ->
                listOf(Num.of(p.toLong(), q.toLong()), Num.of(-p.toLong(), q.toLong()))
            }}.distinct()
            if (roots.size + steps > 4096) break
            val root = roots.firstOrNull { x ->
                steps++
                var value: Num = Num.ZERO
                for (power in degree downTo 0) value = value * x + (coefficientsNow[power] ?: Num.ZERO)
                value.isZero
            } ?: break
            factors += sub(variable(name), number(root))
            val quotient = linkedMapOf<Int, Rational>()
            var value: Num = coefficientsNow.getValue(degree)
            quotient[degree - 1] = value as Rational
            for (power in degree - 1 downTo 1) {
                value = (coefficientsNow[power] ?: Num.ZERO) + root * value
                quotient[power - 1] = value as Rational
            }
            coefficientsNow = quotient.filterValues { !it.isZero }
        }
        var remainder: Expr = integer(0)
        for ((power, coefficient) in coefficientsNow.toSortedMap(reverseOrder())) {
            val term = if (power == 0) number(coefficient) else mul(number(coefficient), if (power == 1) variable(name) else pow(variable(name), integer(power)))
            remainder = add(remainder, term)
        }
        var result = canonical(remainder)
        for (factor in factors) result = mul(result, Expressions.simplifyLocal(factor))
        // Local simplification only: full canonicalisation would expand the factors again.
        return Expressions.simplifyLocal(result)
    }

    private fun divisors(n: Int): List<Int> {
        val result = sortedSetOf<Int>()
        var d = 1
        while (d.toLong() * d <= n) {
            if (n % d == 0) { result += d; result += n / d }
            d++
        }
        return result.toList()
    }
}
