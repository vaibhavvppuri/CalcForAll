package com.vaibhav.calc.core.route

import com.vaibhav.calc.core.ast.Assignment
import com.vaibhav.calc.core.ast.Equation
import com.vaibhav.calc.core.ast.Expr
import com.vaibhav.calc.core.ast.ExprStatement
import com.vaibhav.calc.core.ast.Statement
import com.vaibhav.calc.core.ast.Binary
import com.vaibhav.calc.core.ast.Call
import com.vaibhav.calc.core.ast.NameRef
import com.vaibhav.calc.core.ast.NumberLit
import com.vaibhav.calc.core.ast.Postfix
import com.vaibhav.calc.core.ast.Unary
import com.vaibhav.calc.core.ast.UnitApply
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.ComplexValue
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Evaluator
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.eval.Value
import com.vaibhav.calc.core.eval.asQuantity
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.unaryMinus
import com.vaibhav.calc.core.parse.Parser
import com.vaibhav.calc.core.solve.Extraction
import com.vaibhav.calc.core.solve.LinearForm
import com.vaibhav.calc.core.solve.Solvers
import com.vaibhav.calc.core.solve.structureExtractor
import com.vaibhav.calc.core.source.SourceSpan

/** What the router worked out the input *was*. */
sealed interface Outcome {
    /** A plain value: `2+2`, `5 km + 300 m`, `sin(30)`. */
    data class Scalar(val value: Value) : Outcome

    /** An equation with zero unknowns is a truth verdict (D-016.2): `2+2=4`. */
    data class Verdict(val holds: Boolean) : Outcome

    /** Roots of a one-unknown equation. */
    data class Roots(val unknown: String, val roots: List<Value>, val steps: List<String>) : Outcome

    /** Solution of a system (D-016.3: equations on separate lines). */
    data class SystemSolution(val unknowns: List<String>, val values: List<Num>, val steps: List<String>) : Outcome

    /** `5 → A` stored. */
    data class Stored(val register: String, val value: Value) : Outcome

    /** True for every value of the unknown (`x = x`). */
    data class Identity(val unknown: String) : Outcome

    /** No value satisfies it (`x = x + 1`). */
    data class NoSolution(val unknown: String) : Outcome
}

/** One reading of an ambiguous input, with the answer that reading produces. */
data class Candidate(val reading: String, val outcome: Outcome)

/**
 * The router's result. [Ambiguous] is a **peer** of [Ok] and [Err], not a flavour
 * of error: ambiguity is a state, not a failure (D-016.5). It lives here rather
 * than on `CalcResult` because the typed APIs can never produce it.
 */
sealed interface RouteResult {
    data class Ok(val outcome: Outcome) : RouteResult
    data class Ambiguous(val candidates: List<Candidate>) : RouteResult
    data class Err(val error: EvalError) : RouteResult
}

/**
 * Raw input → intent → dispatch → one unified result (D-016).
 *
 * The router **only routes**: every solver, matrix and stats API underneath it is
 * unchanged. It exists so the user types `x²-5x+6=0` and gets roots, instead of
 * being asked to find a mode and fill in three coefficient fields.
 */
class ExpressionRouter(
    private val settings: Settings,
    private val env: Environment,
) {

    fun route(input: String): RouteResult {
        val program = when (val p = Parser.parseProgram(input)) {
            is CalcResult.Err -> return RouteResult.Err(p.error)
            is CalcResult.Ok -> p.value
        }
        return try {
            val statements = program.statements
            equationStructureError(statements.filterIsInstance<Equation>())?.let { return RouteResult.Err(it) }
            when {
                statements.size == 1 -> routeSingle(statements[0])
                statements.all { it is Equation } ->
                    routeSystem(statements.map { it as Equation }, program.span)
                else -> RouteResult.Err(
                    EvalError.Unsupported(
                        "multiple lines are only supported as a system of equations",
                        program.span,
                    ),
                )
            }
        } catch (f: ArithFault) {
            RouteResult.Err(faultToError(f, program.span))
        }
    }

    // -----------------------------------------------------------------------

    /**
     * The structure extractor is recursive. A flat left-associated sum can
     * have thousands of AST levels without deeply nested parentheses, so the
     * parser's recursion guard alone cannot protect equation/system analysis.
     * Plain scalar evaluation retains its separate iterative long-sum path.
     */
    private fun equationStructureError(equations: List<Equation>): EvalError? {
        val pending = ArrayDeque<Pair<Expr,Int>>()
        var nodes = 0
        for (equation in equations) {
            pending.addLast(equation.lhs to 1)
            pending.addLast(equation.rhs to 1)
            while (pending.isNotEmpty()) {
                val (node,depth) = pending.removeLast()
                if (++nodes > MAX_EQUATION_NODES || depth > MAX_EQUATION_DEPTH) {
                    return EvalError.Unsupported(
                        "equation/system analysis supports at most $MAX_EQUATION_NODES expression nodes and depth $MAX_EQUATION_DEPTH; simplify the expression",
                        node.span,
                    )
                }
                when (node) {
                    is Binary -> { pending.addLast(node.left to depth + 1); pending.addLast(node.right to depth + 1) }
                    is Unary -> pending.addLast(node.operand to depth + 1)
                    is Postfix -> pending.addLast(node.operand to depth + 1)
                    is Call -> node.args.forEach { pending.addLast(it to depth + 1) }
                    is UnitApply -> pending.addLast(node.value to depth + 1)
                    is NameRef, is NumberLit -> Unit
                }
            }
        }
        return null
    }

    private companion object {
        const val MAX_EQUATION_NODES = 512
        const val MAX_EQUATION_DEPTH = 64
    }

    private fun routeSingle(s: Statement): RouteResult = when (s) {
        is ExprStatement -> when (val r = evaluate(s.expr)) {
            is CalcResult.Err -> RouteResult.Err(r.error)
            is CalcResult.Ok -> {
                env.setAns(r.value)
                RouteResult.Ok(Outcome.Scalar(r.value))
            }
        }
        is Assignment -> routeAssignment(s)
        is Equation -> routeEquation(s)
    }

    private fun routeAssignment(a: Assignment): RouteResult {
        ReservedNames.assignmentBlockedReason(a.target)?.let {
            return RouteResult.Err(EvalError.Domain(it, a.span))
        }
        return when (val r = evaluate(a.value)) {
            is CalcResult.Err -> RouteResult.Err(r.error)
            is CalcResult.Ok -> {
                env.set(a.target, r.value)
                env.setAns(r.value)
                RouteResult.Ok(Outcome.Stored(a.target, r.value))
            }
        }
    }

    private fun routeEquation(eq: Equation): RouteResult {
        val extractor = extractor()
        val unknowns = (extractor.unknownsIn(eq.lhs) + extractor.unknownsIn(eq.rhs)).toList()

        // Zero unknowns → a truth verdict, never "underdetermined" (D-016.2).
        if (unknowns.isEmpty()) {
            val l = evaluate(eq.lhs)
            if (l is CalcResult.Err) return RouteResult.Err(l.error)
            val r = evaluate(eq.rhs)
            if (r is CalcResult.Err) return RouteResult.Err(r.error)
            val lv = (l as CalcResult.Ok).value
            val rv = (r as CalcResult.Ok).value
            return RouteResult.Ok(Outcome.Verdict(valuesEqual(lv, rv)))
        }

        // Two or more unknowns in ONE equation → underdetermined. Never silently
        // pick a variable to solve for (D-014 generalised, D-016.6).
        if (unknowns.size > 1) {
            return RouteResult.Err(
                EvalError.Unsupported(
                    "underdetermined: ${unknowns.size} unknowns (${unknowns.joinToString(", ")}) in one equation — " +
                        "add another equation on a new line, or graph it",
                    eq.span,
                ),
            )
        }

        val u = unknowns.single()
        // Failure and not-polynomial are handled SEPARATELY and always have been
        // meant to be: one reports, the other explains. They can no longer be
        // confused because the type cannot express them as the same thing.
        val lp = when (val lhs = extractor.poly(eq.lhs, u)) {
            is Extraction.Failed -> return RouteResult.Err(lhs.error)
            is Extraction.NotPolynomial ->
                return RouteResult.Err(EvalError.Unsupported("cannot solve for '$u': ${lhs.reason}", eq.span))
            is Extraction.Extracted -> lhs.value
        }
        val rp = when (val rhs = extractor.poly(eq.rhs, u)) {
            is Extraction.Failed -> return RouteResult.Err(rhs.error)
            is Extraction.NotPolynomial ->
                return RouteResult.Err(EvalError.Unsupported("cannot solve for '$u': ${rhs.reason}", eq.span))
            is Extraction.Extracted -> rhs.value
        }
        val diff = lp - rp // solve diff = 0
        return when (diff.degree) {
            -1 -> RouteResult.Ok(Outcome.Identity(u))
            0 -> RouteResult.Ok(Outcome.NoSolution(u))
            1 -> {
                val res = Solvers.linear(diff.coeff(1), diff.coeff(0), settings)
                RouteResult.Ok(Outcome.Roots(u, listOf(RealValue(res.root)), res.steps))
            }
            2 -> {
                val res = Solvers.quadratic(diff.coeff(2), diff.coeff(1), diff.coeff(0), settings)
                RouteResult.Ok(Outcome.Roots(u, res.roots, res.steps))
            }
            else -> RouteResult.Err(
                EvalError.Unsupported("degree ${diff.degree} equations are not supported (linear and quadratic only)", eq.span),
            )
        }
    }

    private fun routeSystem(equations: List<Equation>, span: SourceSpan): RouteResult {
        val extractor = extractor()
        val present = LinkedHashSet<String>()
        for (eq in equations) {
            present += extractor.unknownsIn(eq.lhs)
            present += extractor.unknownsIn(eq.rhs)
        }
        // Deterministic order: x, y, z.
        val unknowns = ReservedNames.UNKNOWNS.filter { it in present }
        if (unknowns.isEmpty()) {
            return RouteResult.Err(EvalError.Unsupported("a system needs at least one unknown", span))
        }
        if (unknowns.size != equations.size) {
            return RouteResult.Err(
                EvalError.Unsupported(
                    "${equations.size} equations but ${unknowns.size} unknowns (${unknowns.joinToString(", ")}) — " +
                        "a unique solution needs one equation per unknown",
                    span,
                ),
            )
        }
        if (unknowns.size !in 2..3) {
            return RouteResult.Err(EvalError.Unsupported("only 2×2 and 3×3 systems are supported", span))
        }

        val forms = ArrayList<LinearForm>()
        for (eq in equations) {
            val lf = when (val l = extractor.linear(eq.lhs)) {
                is Extraction.Failed -> return RouteResult.Err(l.error)
                is Extraction.NotPolynomial ->
                    return RouteResult.Err(EvalError.Unsupported("systems must be linear in their unknowns: ${l.reason}", eq.span))
                is Extraction.Extracted -> l.value
            }
            val rf = when (val r = extractor.linear(eq.rhs)) {
                is Extraction.Failed -> return RouteResult.Err(r.error)
                is Extraction.NotPolynomial ->
                    return RouteResult.Err(EvalError.Unsupported("systems must be linear in their unknowns: ${r.reason}", eq.span))
                is Extraction.Extracted -> r.value
            }
            forms += (lf - rf) // form = 0
        }

        // form: Σ cᵢ·uᵢ + k = 0  →  Σ cᵢ·uᵢ = −k
        val res = if (unknowns.size == 2) {
            val (x, y) = unknowns
            Solvers.system2x2(
                forms[0].coeff(x), forms[0].coeff(y), -forms[0].constant,
                forms[1].coeff(x), forms[1].coeff(y), -forms[1].constant,
                settings,
            )
        } else {
            val (x, y, z) = unknowns
            Solvers.system3x3(
                forms[0].coeff(x), forms[0].coeff(y), forms[0].coeff(z), -forms[0].constant,
                forms[1].coeff(x), forms[1].coeff(y), forms[1].coeff(z), -forms[1].constant,
                forms[2].coeff(x), forms[2].coeff(y), forms[2].coeff(z), -forms[2].constant,
                settings,
            )
        }
        return RouteResult.Ok(Outcome.SystemSolution(unknowns, res.solution, res.steps))
    }

    // -----------------------------------------------------------------------

    /** Shared with the graph layer — one rule, one implementation (Article 12). */
    private fun extractor() = structureExtractor(settings, env)

    private fun evaluate(e: Expr): CalcResult<Value> = Evaluator(settings, env).eval(e)

    private fun valuesEqual(a: Value, b: Value): Boolean = when {
        a is RealValue && b is RealValue -> a.num.compareTo(b.num) == 0
        a is ComplexValue || b is ComplexValue ->
            a is ComplexValue && b is ComplexValue &&
                a.cx.re.compareTo(b.cx.re) == 0 && a.cx.im.compareTo(b.cx.im) == 0
        // At least one side carries a unit. Compare through the SAME promotion
        // ValueOps uses, so `5 lakh = 500000` is true (both dimensionless, equal
        // base value) while `5 km = 5` stays false (LENGTH vs dimensionless).
        // Comparing only same-type pairs made a *true* statement return false —
        // a confident wrong answer, which D-016.5 forbids above all.
        else -> {
            val qa = a.asQuantity()
            val qb = b.asQuantity()
            qa.unit.dimension == qb.unit.dimension && qa.toBase().compareTo(qb.toBase()) == 0
        }
    }

    private fun faultToError(f: ArithFault, span: SourceSpan): EvalError = when (f) {
        is ArithFault.DivideByZero -> EvalError.DivideByZero(span, f.message ?: "division by zero")
        is ArithFault.Domain -> EvalError.Domain(f.message ?: "domain error", span, f.function)
        is ArithFault.Overflow -> EvalError.Overflow(f.message ?: "result too large", span)
        is ArithFault.Underflow -> EvalError.Underflow(f.message ?: "result too small", span)
        is ArithFault.Unsupported -> EvalError.Unsupported(f.message ?: "unsupported", span)
    }
}
