package com.vaibhav.calc.core.symbolic

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.num.*
import java.math.BigInteger

/** Exact multivariate polynomials only. Extraction never cancels a variable denominator. */
internal class RationalPolynomial private constructor(val terms: Map<Monomial, Rational>) {
    data class Monomial(val powers: Map<String, Int>) {
        val degree: Int get() = powers.values.sum()
        fun multiply(o: Monomial): Monomial {
            val out = powers.toMutableMap()
            o.powers.forEach { (name, power) -> out[name] = (out[name] ?: 0) + power }
            if (out.values.sum() > MAX_DEGREE) unsupported("polynomial degree exceeds $MAX_DEGREE")
            return Monomial(out.toSortedMap())
        }
    }

    operator fun plus(o: RationalPolynomial): RationalPolynomial {
        val out = terms.toMutableMap()
        for ((m, c) in o.terms) out[m] = ((out[m] ?: Num.ZERO) + c) as Rational
        return of(out)
    }

    fun negative(): RationalPolynomial = of(terms.mapValues { (-it.value) as Rational })
    operator fun minus(o: RationalPolynomial): RationalPolynomial = this + o.negative()

    operator fun times(o: RationalPolynomial): RationalPolynomial {
        if (terms.size.toLong() * o.terms.size > MAX_TERMS * MAX_TERMS) unsupported("polynomial expansion is too large")
        val out = linkedMapOf<Monomial, Rational>()
        for ((m, c) in terms) for ((n, d) in o.terms) {
            val product = m.multiply(n)
            out[product] = ((out[product] ?: Num.ZERO) + c * d) as Rational
            if (out.size > MAX_TERMS) unsupported("polynomial expansion exceeds $MAX_TERMS terms")
        }
        return of(out)
    }

    fun scale(n: Rational): RationalPolynomial = of(terms.mapValues { (it.value * n) as Rational })

    fun power(n: Int): RationalPolynomial {
        if (n !in 0..MAX_DEGREE) unsupported("polynomial powers must be from 0 to $MAX_DEGREE")
        var base = this
        var out = constant(Num.ONE)
        var exponent = n
        while (exponent > 0) {
            if (exponent and 1 == 1) out *= base
            exponent = exponent shr 1
            if (exponent > 0) base *= base
        }
        return out
    }

    fun derivative(variable: String): RationalPolynomial {
        val out = linkedMapOf<Monomial, Rational>()
        for ((m, c) in terms) {
            val power = m.powers[variable] ?: 0
            if (power == 0) continue
            val powers = m.powers.toMutableMap()
            if (power == 1) powers.remove(variable) else powers[variable] = power - 1
            out[Monomial(powers.toSortedMap())] = (c * Num.of(power)) as Rational
        }
        return of(out)
    }

    fun antiderivative(variable: String): RationalPolynomial {
        val out = linkedMapOf<Monomial, Rational>()
        for ((m, c) in terms) {
            val power = (m.powers[variable] ?: 0) + 1
            if (m.degree + 1 > MAX_DEGREE) unsupported("antiderivative exceeds polynomial degree limit")
            val powers = m.powers + (variable to power)
            out[Monomial(powers.toSortedMap())] = (c / Num.of(power)) as Rational
        }
        return of(out)
    }

    fun degree(variable: String): Int = terms.keys.maxOfOrNull { it.powers[variable] ?: 0 } ?: -1

    /** A polynomial in this one variable with rational coefficients, or null. */
    fun coefficients(variable: String): Map<Int, Rational>? {
        if (terms.keys.any { m -> m.powers.keys.any { it != variable } }) return null
        return terms.mapKeys { (m, _) -> m.powers[variable] ?: 0 }
    }

    fun toExpr(): Expr {
        if (terms.isEmpty()) return integer(0)
        val ordered = terms.entries.sortedWith(compareByDescending<Map.Entry<Monomial, Rational>> { it.key.degree }
            .thenByDescending { it.key.powers["x"] ?: 0 }
            .thenByDescending { it.key.powers["y"] ?: 0 }
            .thenByDescending { it.key.powers["z"] ?: 0 }
            .thenBy { it.key.powers.toString() })
        var result: Expr? = null
        for ((m, c) in ordered) {
            var term: Expr? = null
            for ((name, power) in m.powers.toSortedMap()) {
                val factor = if (power == 1) variable(name) else pow(variable(name), integer(power))
                term = term?.let { mul(it, factor) } ?: factor
            }
            val magnitude = c.abs() as Rational
            val value = if (term == null) number(magnitude) else if (magnitude == Num.ONE) term else mul(number(magnitude), term)
            result = if (result == null) {
                if (c.signum() < 0) neg(value) else value
            } else if (c.signum() < 0) sub(result, value) else add(result, value)
        }
        return result!!
    }

    companion object {
        const val MAX_DEGREE = 32
        const val MAX_TERMS = 256
        private val ONE = Monomial(emptyMap())

        private fun of(terms: Map<Monomial, Rational>): RationalPolynomial {
            val clean = terms.filterValues { !it.isZero }
            if (clean.size > MAX_TERMS) unsupported("polynomial expansion exceeds $MAX_TERMS terms")
            if (clean.values.any { it.num.bitLength() > 8192 || it.den.bitLength() > 8192 }) unsupported("polynomial coefficient exceeds the arithmetic budget")
            return RationalPolynomial(clean)
        }

        fun constant(n: Rational): RationalPolynomial = of(mapOf(ONE to n))

        fun from(e: Expr): RationalPolynomial? = when (e) {
            is NumberLit -> (e.value as? Rational)?.let(::constant)
            is NameRef -> if (ReservedNames.isUnknown(e.name) || e.name in ReservedNames.REGISTERS) {
                of(mapOf(Monomial(mapOf(e.name to 1)) to Num.ONE))
            } else null
            is Unary -> from(e.operand)?.let { if (e.op == UnaryOp.NEG) it.negative() else it }
            is Binary -> {
                val a = from(e.left)
                val b = from(e.right)
                when (e.op) {
                    BinaryOp.ADD -> if (a != null && b != null) a + b else null
                    BinaryOp.SUB -> if (a != null && b != null) a - b else null
                    BinaryOp.MUL -> if (a != null && b != null) a * b else null
                    BinaryOp.DIV -> e.right.rational()?.takeIf { !it.isZero }?.let { d -> a?.scale((Num.ONE / d) as Rational) }
                    BinaryOp.POW -> e.right.rational()?.toBigIntegerExactOrNull()?.takeIf {
                        it.signum() >= 0 && it <= BigInteger.valueOf(MAX_DEGREE.toLong())
                    }?.let { a?.power(it.toInt()) }
                }
            }
            else -> null
        }
    }
}
