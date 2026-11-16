package com.vaibhav.calc.core.solve

import com.vaibhav.calc.core.ast.Binary
import com.vaibhav.calc.core.ast.BinaryOp
import com.vaibhav.calc.core.ast.Call
import com.vaibhav.calc.core.ast.Expr
import com.vaibhav.calc.core.ast.NameRef
import com.vaibhav.calc.core.ast.NumberLit
import com.vaibhav.calc.core.ast.Postfix
import com.vaibhav.calc.core.ast.PostfixOp
import com.vaibhav.calc.core.ast.Unary
import com.vaibhav.calc.core.ast.UnaryOp
import com.vaibhav.calc.core.ast.UnitApply
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.num.toBigIntegerExactOrNull
import com.vaibhav.calc.core.num.unaryMinus

/**
 * The outcome of trying to read structure out of an AST.
 *
 * The three cases are **structurally distinct on purpose** (Article 11): a caller
 * must be able to tell "this genuinely isn't polynomial, fall back to numeric
 * methods" from "we failed to evaluate something, stop and report". A single
 * nullable answer conflates them, and the two demand opposite responses — which is
 * how a plausible wrong root gets drawn on a graph.
 */
sealed interface Extraction<out T> {
    /** Structure was read successfully. */
    data class Extracted<T>(val value: T) : Extraction<T>

    /** Well-formed, but genuinely not polynomial/linear in the unknown. A legitimate ANSWER. */
    data class NotPolynomial(val reason: String) : Extraction<Nothing>

    /** We could not evaluate part of it. A FAILURE — never to be mistaken for the above. */
    data class Failed(val error: EvalError) : Extraction<Nothing>
}

inline fun <T, R> Extraction<T>.map(f: (T) -> R): Extraction<R> = when (this) {
    is Extraction.Extracted -> Extraction.Extracted(f(value))
    is Extraction.NotPolynomial -> this
    is Extraction.Failed -> this
}

inline fun <T, R> Extraction<T>.flatMap(f: (T) -> Extraction<R>): Extraction<R> = when (this) {
    is Extraction.Extracted -> f(value)
    is Extraction.NotPolynomial -> this
    is Extraction.Failed -> this
}

/**
 * A univariate polynomial with exact rational coefficients — `coeffs[i]` is the
 * coefficient of `xⁱ`. This exists so an inline equation can reach the existing
 * typed solvers: the AST is evaluated in a **polynomial algebra** instead of the
 * number algebra, which extracts (a, b, c) with no symbolic rewriting.
 *
 * This is deliberately NOT a CAS (Article 2): no simplification rules, no
 * factoring, no identities — only +, −, × and integer powers over coefficients.
 */
class Poly(coefficients: List<Num>) {

    /** Trailing zero coefficients trimmed, so degree is meaningful. */
    val coeffs: List<Num> = coefficients.dropLastWhile { it.isZero }

    /** −1 for the identically-zero polynomial. */
    val degree: Int get() = coeffs.size - 1

    val isZero: Boolean get() = coeffs.isEmpty()

    fun coeff(i: Int): Num = if (i < coeffs.size) coeffs[i] else Num.ZERO

    /** Evaluate at [x] (Horner). Used to VERIFY roots by substitution. */
    fun at(x: Num): Num {
        var acc: Num = Num.ZERO
        for (i in coeffs.indices.reversed()) acc = acc * x + coeffs[i]
        return acc
    }

    operator fun plus(o: Poly): Poly =
        Poly((0 until maxOf(coeffs.size, o.coeffs.size)).map { coeff(it) + o.coeff(it) })

    operator fun minus(o: Poly): Poly =
        Poly((0 until maxOf(coeffs.size, o.coeffs.size)).map { coeff(it) - o.coeff(it) })

    operator fun times(o: Poly): Poly {
        if (isZero || o.isZero) return Poly(emptyList())
        // Bound the RESULTING degree, not just the exponent: nested powers like
        // ((x^64)^64)^64 each pass an exponent check yet compound to a degree in
        // the millions, which allocates until the heap dies.
        requireDegree(degree.toLong() + o.degree.toLong())
        val out = MutableList<Num>(coeffs.size + o.coeffs.size - 1) { Num.ZERO }
        for (i in coeffs.indices) for (j in o.coeffs.indices) {
            out[i + j] = out[i + j] + coeffs[i] * o.coeffs[j]
        }
        return Poly(out)
    }

    operator fun unaryMinus(): Poly = Poly(coeffs.map { -it })

    fun pow(n: Int): Poly {
        require(n >= 0)
        if (degree > 0) requireDegree(degree.toLong() * n)
        var result = constant(Num.ONE)
        repeat(n) { result *= this }
        return result
    }

    /** Divide by a scalar (the only division a polynomial algebra permits here). */
    fun divideByScalar(k: Num): Poly = Poly(coeffs.map { it / k })

    companion object {
        /** The solvers only handle degree ≤ 2; this is generous and keeps memory bounded. */
        const val MAX_RESULT_DEGREE = 64

        private fun requireDegree(d: Long) {
            if (d > MAX_RESULT_DEGREE) {
                throw ArithFault.Overflow("polynomial degree $d exceeds the supported bound ($MAX_RESULT_DEGREE)")
            }
        }

        fun constant(n: Num): Poly = Poly(listOf(n))
        val X: Poly = Poly(listOf(Num.ZERO, Num.ONE))
    }
}

/** A linear form over several unknowns: `Σ cᵢ·uᵢ + k`. Used for systems. */
data class LinearForm(val coefficients: Map<String, Num>, val constant: Num) {

    operator fun plus(o: LinearForm): LinearForm {
        val keys = coefficients.keys + o.coefficients.keys
        return LinearForm(keys.associateWith { coeff(it) + o.coeff(it) }, constant + o.constant)
    }

    operator fun minus(o: LinearForm): LinearForm {
        val keys = coefficients.keys + o.coefficients.keys
        return LinearForm(keys.associateWith { coeff(it) - o.coeff(it) }, constant - o.constant)
    }

    operator fun unaryMinus(): LinearForm =
        LinearForm(coefficients.mapValues { -it.value }, -constant)

    fun coeff(u: String): Num = coefficients[u] ?: Num.ZERO

    val isConstant: Boolean get() = coefficients.values.all { it.isZero }

    fun scale(k: Num): LinearForm = LinearForm(coefficients.mapValues { it.value * k }, constant * k)

    companion object {
        fun constant(n: Num): LinearForm = LinearForm(emptyMap(), n)
        fun unknown(name: String): LinearForm = LinearForm(mapOf(name to Num.ONE), Num.ZERO)
    }
}

/**
 * Extracts polynomial / linear structure from an AST. A subtree with no unknown is
 * handed to [evalConstant] and folded to a number; a subtree using an unknown
 * non-polynomially yields [Extraction.NotPolynomial]; a subtree we could not
 * evaluate yields [Extraction.Failed] — three distinct outcomes, never merged.
 */
class StructureExtractor(
    private val unknowns: Set<String>,
    private val evalConstant: (Expr) -> Extraction<Num>,
) {

    fun unknownsIn(e: Expr): Set<String> {
        val found = LinkedHashSet<String>()
        collect(e, found)
        return found
    }

    private fun collect(e: Expr, into: MutableSet<String>) {
        when (e) {
            is NameRef -> if (ReservedNames.isUnknown(e.name)) into += ReservedNames.canonicalUnknown(e.name)
            is Unary -> collect(e.operand, into)
            is Binary -> { collect(e.left, into); collect(e.right, into) }
            is Postfix -> collect(e.operand, into)
            is Call -> e.args.forEach { collect(it, into) }
            is UnitApply -> collect(e.value, into)
            is NumberLit -> {}
        }
    }

    private fun hasUnknown(e: Expr): Boolean = unknownsIn(e).isNotEmpty()

    /** Univariate polynomial in [variable]. */
    fun poly(e: Expr, variable: String): Extraction<Poly> {
        if (!hasUnknown(e)) return evalConstant(e).map { Poly.constant(it) }
        return when (e) {
            is NameRef ->
                if (ReservedNames.canonicalUnknown(e.name) == variable) Extraction.Extracted(Poly.X)
                else Extraction.NotPolynomial("'${e.name}' is a second unknown")
            is Unary -> poly(e.operand, variable).map { if (e.op == UnaryOp.NEG) -it else it }
            is Binary -> polyBinary(e, variable)
            else -> Extraction.NotPolynomial("'$variable' appears inside a function, factorial or unit, which is not polynomial")
        }
    }

    private fun polyBinary(e: Binary, variable: String): Extraction<Poly> {
        percentContext(e)?.let { return it.forPoly(variable) }
        val l = poly(e.left, variable)
        if (l !is Extraction.Extracted) return l
        return when (e.op) {
            BinaryOp.ADD -> poly(e.right, variable).map { l.value + it }
            BinaryOp.SUB -> poly(e.right, variable).map { l.value - it }
            BinaryOp.MUL -> poly(e.right, variable).map { l.value * it }
            BinaryOp.DIV ->
                if (hasUnknown(e.right)) {
                    Extraction.NotPolynomial("dividing by an expression containing '$variable' is not polynomial")
                } else {
                    evalConstant(e.right).flatMap { k ->
                        if (k.isZero) Extraction.Failed(EvalError.DivideByZero(e.span))
                        else Extraction.Extracted(l.value.divideByScalar(k))
                    }
                }
            BinaryOp.POW ->
                if (hasUnknown(e.right)) {
                    Extraction.NotPolynomial("a variable exponent is not polynomial")
                } else {
                    evalConstant(e.right).flatMap { k ->
                        val n = k.asSmallNonNegativeInt()
                        if (n == null) Extraction.NotPolynomial("only non-negative integer powers are polynomial")
                        else Extraction.Extracted(l.value.pow(n))
                    }
                }
        }
    }

    /** Linear form over [unknowns]. */
    fun linear(e: Expr): Extraction<LinearForm> {
        if (!hasUnknown(e)) return evalConstant(e).map { LinearForm.constant(it) }
        return when (e) {
            is NameRef ->
                if (ReservedNames.isUnknown(e.name)) Extraction.Extracted(LinearForm.unknown(ReservedNames.canonicalUnknown(e.name)))
                else Extraction.NotPolynomial("'${e.name}' is not an unknown")
            is Unary -> linear(e.operand).map { if (e.op == UnaryOp.NEG) -it else it }
            is Binary -> linearBinary(e)
            else -> Extraction.NotPolynomial("an unknown appears inside a function, factorial or unit, which is not linear")
        }
    }

    private fun linearBinary(e: Binary): Extraction<LinearForm> {
        percentContext(e)?.let { return it.forLinear() }
        return when (e.op) {
            BinaryOp.ADD -> linear(e.left).flatMap { a -> linear(e.right).map { b -> a + b } }
            BinaryOp.SUB -> linear(e.left).flatMap { a -> linear(e.right).map { b -> a - b } }
            BinaryOp.MUL -> {
                val lHas = hasUnknown(e.left)
                val rHas = hasUnknown(e.right)
                when {
                    lHas && rHas -> Extraction.NotPolynomial("a product of two unknowns is not linear")
                    lHas -> evalConstant(e.right).flatMap { k -> linear(e.left).map { it.scale(k) } }
                    else -> evalConstant(e.left).flatMap { k -> linear(e.right).map { it.scale(k) } }
                }
            }
            BinaryOp.DIV ->
                if (hasUnknown(e.right)) {
                    Extraction.NotPolynomial("dividing by an expression containing an unknown is not linear")
                } else {
                    evalConstant(e.right).flatMap { k ->
                        if (k.isZero) Extraction.Failed(EvalError.DivideByZero(e.span))
                        else linear(e.left).map { it.scale(Num.ONE / k) }
                    }
                }
            BinaryOp.POW -> Extraction.NotPolynomial("an unknown under a power is not linear")
        }
    }

    /**
     * `a OP b%` — Casio contextual percent. Held in ONE place so the polynomial and
     * linear extractors apply the identical rule the evaluator does (Article 12).
     */
    private inner class PercentCtx(val op: BinaryOp, val left: Expr, val fraction: Num) {
        fun forPoly(variable: String): Extraction<Poly> {
            val base = poly(left, variable)
            if (base !is Extraction.Extracted) return base
            val f = Poly.constant(fraction)
            return when (op) {
                BinaryOp.ADD -> Extraction.Extracted(base.value + base.value * f)
                BinaryOp.SUB -> Extraction.Extracted(base.value - base.value * f)
                BinaryOp.MUL -> Extraction.Extracted(base.value * f)
                BinaryOp.DIV ->
                    if (fraction.isZero) Extraction.Failed(EvalError.DivideByZero(left.span))
                    else Extraction.Extracted(base.value.divideByScalar(fraction))
                BinaryOp.POW -> Extraction.NotPolynomial("a percent exponent is not polynomial")
            }
        }

        fun forLinear(): Extraction<LinearForm> {
            val base = linear(left)
            if (base !is Extraction.Extracted) return base
            return when (op) {
                BinaryOp.ADD -> Extraction.Extracted(base.value + base.value.scale(fraction))
                BinaryOp.SUB -> Extraction.Extracted(base.value - base.value.scale(fraction))
                BinaryOp.MUL -> Extraction.Extracted(base.value.scale(fraction))
                BinaryOp.DIV ->
                    if (fraction.isZero) Extraction.Failed(EvalError.DivideByZero(left.span))
                    else Extraction.Extracted(base.value.scale(Num.ONE / fraction))
                BinaryOp.POW -> Extraction.NotPolynomial("a percent exponent is not linear")
            }
        }
    }

    private fun percentContext(e: Binary): PercentCtx? {
        val right = e.right
        if (right !is Postfix || right.op != PostfixOp.PERCENT) return null
        val v = evalConstant(right.operand)
        if (v !is Extraction.Extracted) return null
        return PercentCtx(e.op, e.left, v.value / Num.of(100))
    }

    private fun Num.asSmallNonNegativeInt(): Int? {
        val i = this.toBigIntegerExactOrNull() ?: return null
        if (i.signum() < 0 || i.bitLength() > 31) return null
        return i.toInt()
    }
}
