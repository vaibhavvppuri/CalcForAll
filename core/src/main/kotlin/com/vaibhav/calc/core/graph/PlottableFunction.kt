package com.vaibhav.calc.core.graph

import com.vaibhav.calc.core.ast.Binary
import com.vaibhav.calc.core.ast.BinaryOp
import com.vaibhav.calc.core.ast.Expr
import com.vaibhav.calc.core.ast.NameRef
import com.vaibhav.calc.core.ast.Unary
import com.vaibhav.calc.core.ast.Postfix
import com.vaibhav.calc.core.ast.Call
import com.vaibhav.calc.core.ast.UnitApply
import com.vaibhav.calc.core.ast.NumberLit
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Evaluator
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.eval.Value
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.parse.Parser
import com.vaibhav.calc.core.solve.Extraction
import com.vaibhav.calc.core.solve.Poly
import com.vaibhav.calc.core.solve.structureExtractor
import com.vaibhav.calc.core.source.SourceSpan

/**
 * A function of one unknown, ready to plot.
 *
 * **[at] is the ONE place a function's value at x is ever computed** (Article 12).
 * The sampler, the root finder, the intersection finder and trace all call it —
 * not "the same logic", the same function. That is what stops the sampler and the
 * root finder from ever disagreeing about what the curve does.
 */
class PlottableFunction private constructor(
    val expr: Expr,
    val unknown: String,
    private val settings: Settings,
    private val env: Environment,
) {

    /** Derivative certificates must use this curve's actual angle convention. */
    internal val angleMode get() = settings.angle

    /** THE evaluation point. Everything in this package goes through here. */
    fun at(x: Num): Sample =
        when (val r = Evaluator(settings, env, mapOf(unknown to x)).eval(expr)) {
            is CalcResult.Ok -> when (val v = r.value) {
                is RealValue -> Sample.Defined(x, v.num)
                // A complex or united value isn't plottable on a real 2-D axis, but
                // that's OUR limit, not a gap in the mathematics — so it's a hole.
                else -> Sample.Unevaluated(
                    x,
                    EvalError.Unsupported("a plotted function must be real-valued at every point", expr.span),
                )
            }
            is CalcResult.Err -> classify(x, r.error)
        }

    /**
     * The polynomial structure of this function in its unknown — used by the root
     * finder to choose exact solving vs numeric bisection. Built from the SAME
     * extractor the equation router uses (Article 12), and returning the same
     * three-way answer so "not polynomial" and "failed" stay distinguishable.
     */
    internal fun polynomial(): Extraction<Poly> =
        structureExtractor(settings, env).poly(expr, unknown)

    /** Used only after a bounded root-analysis walk proves the subtree has no unknown. */
    internal fun evaluateClosed(expression: Expr): CalcResult<Value> = Evaluator(settings, env).eval(expression)

    /** A combined AST must not silently reinterpret the other curve's memory or angle policy. */
    internal fun sharesEvaluationContext(other: PlottableFunction): Boolean {
        if (settings != other.settings) return false
        if (env === other.env) return true
        val names = env.names()
        return names == other.env.names() && names.all { env.get(it) == other.env.get(it) }
    }

    /**
     * Splits a failure into "the mathematics has no value here" (a GAP) versus "we
     * could not work it out" (a HOLE). This is the Article 11 boundary for plotting.
     */
    private fun classify(x: Num, e: EvalError): Sample = when (e.kind) {
        // The function genuinely does not exist at this x.
        EvalError.Kind.DIVIDE_BY_ZERO, EvalError.Kind.DOMAIN -> Sample.Undefined(x, e)
        // Overflow / unsupported / anything else: we failed, the maths may be fine.
        else -> Sample.Unevaluated(x, e)
    }

    companion object {
        /** Parse `input` as a function of [unknown] (default `x`). */
        fun of(
            input: String,
            settings: Settings,
            env: Environment,
            unknown: String = "x",
        ): CalcResult<PlottableFunction> {
            require(ReservedNames.isUnknown(unknown)) { "'$unknown' is not an unknown; use one of ${ReservedNames.UNKNOWNS}" }
            return when (val p = Parser.parse(input)) {
                is CalcResult.Err -> p
                is CalcResult.Ok -> {
                    val bound = ReservedNames.canonicalUnknown(unknown)
                    val extra = unboundUnknown(p.value,bound)
                    if (extra != null) return CalcResult.Err(EvalError.Unsupported(
                        "'${extra.name}' is not bound by a curve in '$bound'; plot one independent variable", extra.span))
                    CalcResult.Ok(PlottableFunction(p.value,bound,settings,env))
                }
            }
        }

        /** An extra unknown is a definition limitation, not a mathematical gap. */
        private fun unboundUnknown(expression: Expr, bound: String): NameRef? {
            val pending = ArrayDeque<Expr>()
            pending.addLast(expression)
            while (pending.isNotEmpty()) {
                when (val node = pending.removeLast()) {
                    is NameRef -> if (ReservedNames.isUnknown(node.name) && ReservedNames.canonicalUnknown(node.name) != bound) return node
                    is Unary -> pending.addLast(node.operand)
                    is Binary -> { pending.addLast(node.right); pending.addLast(node.left) }
                    is Postfix -> pending.addLast(node.operand)
                    is Call -> node.args.asReversed().forEach { pending.addLast(it) }
                    is UnitApply -> pending.addLast(node.value)
                    is NumberLit -> Unit
                }
            }
            return null
        }

        /**
         * `f − g`, for intersections. Built as an AST so the difference is evaluated
         * by the SAME [at] as everything else — an intersection is just a root of the
         * difference, and shares every guarantee root finding has.
         */
        fun difference(f: PlottableFunction, g: PlottableFunction): PlottableFunction {
            require(f.unknown == g.unknown) { "functions must share an unknown (${f.unknown} vs ${g.unknown})" }
            require(f.sharesEvaluationContext(g)) { "functions must share equal settings and memory bindings" }
            val span = SourceSpan(
                minOf(f.expr.span.start, g.expr.span.start),
                maxOf(f.expr.span.end, g.expr.span.end),
            )
            return PlottableFunction(
                Binary(BinaryOp.SUB, f.expr, g.expr, span),
                f.unknown,
                f.settings,
                f.env,
            )
        }
    }
}
