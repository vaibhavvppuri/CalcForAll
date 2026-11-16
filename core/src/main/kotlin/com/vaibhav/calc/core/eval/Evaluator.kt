package com.vaibhav.calc.core.eval

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
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Complex
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Konst
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.NumMath
import com.vaibhav.calc.core.num.SymConst
import com.vaibhav.calc.core.num.abs
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.signum
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.num.toBigIntegerExactOrNull
import com.vaibhav.calc.core.num.unaryMinus
import com.vaibhav.calc.core.constants.Constants
import com.vaibhav.calc.core.name.Functions
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.source.SourceSpan
import com.vaibhav.calc.core.units.Quantity
import com.vaibhav.calc.core.units.Units
import java.math.BigInteger

/**
 * Walks an [Expr] tree and produces an exact-first [Value] (real or complex).
 * Internal [ArithFault]s from the number tower are caught here and turned into
 * typed [EvalError]s carrying the source span of the operation that raised them,
 * so the public API only ever returns typed results, never throws (spec M1.5).
 */
class Evaluator(
    private val settings: Settings,
    private val env: Environment,
    /**
     * Explicit bindings for equation unknowns, used by the graph layer to evaluate
     * `f(x)` at a sample point. Empty by default, so ordinary evaluation still
     * refuses a bare `x` — the static partition (D-016.4) is unchanged and this is
     * scoped to the caller rather than being hidden state.
     */
    private val unknownBindings: Map<String, Num> = emptyMap(),
) {
    private class EvalException(val error: EvalError) : RuntimeException(error.message)

    /** Current recursion depth, bounded by [MAX_EVAL_DEPTH]. */
    private var depth = 0

    fun eval(expr: Expr): CalcResult<Value> = try {
        CalcResult.Ok(evalNode(expr))
    } catch (e: EvalException) {
        CalcResult.Err(e.error)
    }

    /**
     * Evaluation carries **its own depth bound, over the AST**.
     *
     * The parser bounds its own recursion, and that guard is real — deep parens,
     * deep unary and deep `^` all fail there as typed SYNTAX errors. But it bounds
     * the PARSER's stack, not the tree's shape, and the two are not the same thing:
     * a flat `1+1+1+…` chain is assembled by an iterative loop, so parser recursion
     * stays ~2 deep while the AST it produces is N deep (B1-0035). A guard on one
     * route did nothing for the other.
     *
     * This bound is over the tree, so it holds for an [Expr] from ANY source — the
     * parser, the 2D entry model, the graph layer, or a future caller building one
     * directly. It is the backstop; [evalBinaryChain] is what keeps ordinary long
     * chains computing instead of merely refusing them.
     */
    private fun evalNode(e: Expr): Value {
        if (depth >= MAX_EVAL_DEPTH) {
            throw EvalException(
                EvalError.Unsupported("expression too deeply nested to evaluate", e.span),
            )
        }
        depth++
        try {
            return when (e) {
                is NumberLit -> RealValue(e.value)
                is NameRef -> resolveName(e)
                is Unary -> when (e.op) {
                    UnaryOp.NEG -> ValueOps.neg(evalNode(e.operand))
                    UnaryOp.POS -> evalNode(e.operand)
                }
                is Postfix -> evalPostfix(e)
                is Binary -> evalBinaryChain(e)
                is Call -> evalCall(e)
                is UnitApply -> evalUnitApply(e)
            }
        } finally {
            depth--
        }
    }

    // Constant names are matched CASE-SENSITIVELY so the register `E` stays
    // distinct from Euler's `e`, and `M` from the metre `m` (D-016.4).
    private fun resolveName(e: NameRef): Value {
        if (e.name == "i") return ComplexValue(Complex.I) // the imaginary unit is reserved
        // The constants registry IS the expression language's vocabulary (D-026).
        // It used to be reachable only through Calculator.physicalConstant(), a typed
        // API the router never consulted — so `c` and `phi` were UnknownSymbol while
        // the reference browser taught them. One table, one answer.
        Constants.resolve(e.name, settings.computeMc)?.let { return it }
        if (ReservedNames.isUnknown(e.name)) {
            unknownBindings[ReservedNames.canonicalUnknown(e.name)]?.let { return RealValue(it) }
            throw EvalException(
                EvalError.Domain(
                    "'${e.name}' is an equation unknown — it has no value outside an equation",
                    e.span,
                ),
            )
        }
        return env.get(e.name) ?: throw EvalException(EvalError.UnknownSymbol(e.name, e.span))
    }

    private fun evalUnitApply(e: UnitApply): Value {
        val v = evalNode(e.value)
        return spanned(e.span) {
            val unit = Units.get(e.unit) ?: throw ArithFault.Domain("unknown unit '${e.unit}'", "units")
            when (v) {
                is RealValue -> Quantity(v.num, unit).toValue()
                is QuantityValue -> throw ArithFault.Domain(
                    "value already has a unit (${v.q.unit.name}); cannot also apply '${e.unit}'",
                    "units",
                )
                is ComplexValue -> throw ArithFault.Unsupported("a complex number cannot carry units")
            }
        }
    }

    private fun evalPostfix(e: Postfix): Value {
        val v = evalNode(e.operand)
        return spanned(e.span) {
            when (e.op) {
                PostfixOp.FACTORIAL -> RealValue(NumMath.factorial(realArg(v, "factorial", e.span)))
                PostfixOp.PERCENT -> ValueOps.div(v, HUNDRED)
            }
        }
    }

    /**
     * Evaluate a binary node by walking its LEFT SPINE **iteratively**.
     *
     * `1+1+1+…` parses into a left-leaning spine N deep, and recursing into
     * `e.left` cost one frame per term — around 1,700 terms that overflowed the
     * JVM stack and a `StackOverflowError` escaped the public API, which Article 4
     * forbids absolutely (B1-0035). The spine is collected first and then folded
     * back outward, so a flat chain of any length costs O(1) stack.
     *
     * **Evaluation order is unchanged.** The old code evaluated `e.left` before
     * touching `e.right`; collecting the spine and folding from the leftmost leaf
     * outward visits operands in exactly that order, so an error still surfaces
     * from the same operand it always did.
     *
     * Right operands stay recursive: in a flat chain they are leaves, and a
     * genuinely deep RIGHT-leaning tree is caught by [evalNode]'s depth guard.
     */
    private fun evalBinaryChain(root: Binary): Value {
        val spine = ArrayList<Binary>()
        var node: Expr = root
        while (node is Binary) {
            spine.add(node)
            node = node.left
        }
        var acc = evalNode(node) // the leftmost leaf — the innermost value
        for (idx in spine.indices.reversed()) acc = evalBinary(spine[idx], acc)
        return acc
    }

    /** Apply one binary node, given its already-evaluated left operand. */
    private fun evalBinary(e: Binary, l: Value): Value {
        // Contextual percent (Casio semantics): a+b% = a + a·(b/100), etc.
        val right = e.right
        if (right is Postfix && right.op == PostfixOp.PERCENT) {
            val inner = evalNode(right.operand)
            return spanned(e.span) {
                // Computing bFrac INSIDE the guard matters: dividing an affine
                // quantity (`1 + 20 degC%`) raises an ArithFault, and outside the
                // guard it escaped the public API instead of becoming a typed error.
                val bFrac = ValueOps.div(inner, HUNDRED)
                when (e.op) {
                    BinaryOp.ADD -> ValueOps.add(l, ValueOps.mul(l, bFrac))
                    BinaryOp.SUB -> ValueOps.sub(l, ValueOps.mul(l, bFrac))
                    BinaryOp.MUL -> ValueOps.mul(l, bFrac)
                    BinaryOp.DIV -> ValueOps.div(l, bFrac)
                    BinaryOp.POW -> ValueOps.pow(l, bFrac, settings.computeMc)
                }
            }
        }

        val r = evalNode(e.right)
        return spanned(e.span) {
            when (e.op) {
                BinaryOp.ADD -> ValueOps.add(l, r)
                BinaryOp.SUB -> ValueOps.sub(l, r)
                BinaryOp.MUL -> ValueOps.mul(l, r)
                BinaryOp.DIV -> ValueOps.div(l, r)
                BinaryOp.POW -> ValueOps.pow(l, r, settings.computeMc)
            }
        }
    }

    private fun evalCall(e: Call): Value {
        val args = e.args.map { evalNode(it) }
        return spanned(e.span) { applyFunction(e, args) }
    }

    private fun applyFunction(call: Call, args: List<Value>): Value {
        val name = call.name.lowercase()
        // THE GATE (Article 12). Dispatch is admitted only for names the shared
        // registry lists, so the set the engine accepts can never exceed the set
        // autocomplete offers. The reverse inclusion is proved by M6NamesSpec.
        // Together: what the engine runs and what the UI suggests are the same set,
        // by construction rather than by vigilance.
        if (!Functions.isFunction(name)) throw EvalException(EvalError.UnknownSymbol(call.name, call.span))
        // Complex-aware functions handle Value directly.
        applyComplexAware(name, args, call)?.let { return it }
        // Everything else is real-only: unwrap arguments and run the real registry.
        val reals = args.map { realArg(it, call.name, call.span) }
        return RealValue(applyRealFunction(name, reals, call))
    }

    // -----------------------------------------------------------------------
    // Complex-aware functions (spec 2.5). Return null if not one of these.
    // -----------------------------------------------------------------------
    private fun applyComplexAware(name: String, args: List<Value>, call: Call): Value? {
        val mc = settings.computeMc
        return when (name) {
            "abs" -> {
                arity(call, 1, args.size)
                when (val a = args[0]) {
                    is RealValue -> RealValue(a.num.abs())
                    is ComplexValue -> RealValue(a.cx.modulus(mc))
                    is QuantityValue -> Quantity(a.q.value.abs(), a.q.unit).toValue()
                }
            }
            "sqrt" -> {
                arity(call, 1, args.size)
                complexAwareSqrt(args[0])
            }
            "re", "real" -> { arity(call, 1, args.size); RealValue(args[0].toComplex().re) }
            "im", "imag" -> { arity(call, 1, args.size); RealValue(args[0].toComplex().im) }
            "conj", "conjugate" -> { arity(call, 1, args.size); args[0].toComplex().conjugate().toValue() }
            "arg", "angle" -> { arity(call, 1, args.size); RealValue(fromRadians(args[0].toComplex().argument(mc))) }
            "cmplx", "complex", "rect" -> {
                arity(call, 2, args.size)
                Complex(realArg(args[0], name, call.span), realArg(args[1], name, call.span)).toValue()
            }
            "cpolar" -> {
                arity(call, 2, args.size)
                val r = realArg(args[0], name, call.span)
                val theta = toRadians(realArg(args[1], name, call.span))
                Complex.fromPolar(r, theta, mc).toValue()
            }
            else -> null
        }
    }

    private fun complexAwareSqrt(v: Value): Value = when (v) {
        is QuantityValue -> throw ArithFault.Unsupported(
            "square root of a quantity with units is not supported (it would need fractional dimensions)",
        )
        is ComplexValue -> v.cx.sqrt(settings.computeMc).toValue()
        is RealValue -> when {
            v.num.signum() >= 0 -> RealValue(NumMath.sqrt(v.num, settings.computeMc))
            settings.complexEnabled -> Complex.real(v.num).sqrt(settings.computeMc).toValue()
            else -> throw ArithFault.Domain("square root of a negative number", "sqrt")
        }
    }

    // -----------------------------------------------------------------------
    // The real function registry (D3). Angle-taking functions honour the mode.
    // -----------------------------------------------------------------------
    private fun applyRealFunction(name: String, args: List<Num>, call: Call): Num {
        val mc = settings.computeMc
        val n = args.size
        fun need(k: Int) = arity(call, k, n)

        return when (name) {
            "cbrt" -> { need(1); NumMath.cbrt(args[0], mc) }
            "root" -> { need(2); NumMath.root(args[0], integerIndex(args[1], call), mc) }
            "pow" -> { need(2); NumMath.pow(args[0], args[1], mc) }
            "exp" -> { need(1); NumMath.exp(args[0], mc) }
            "sq", "square" -> { need(1); args[0] * args[0] }
            "approx" -> { need(1); Dec(args[0].toBigDecimal(mc)) }

            "ln" -> { need(1); NumMath.ln(args[0], mc) }
            "log" -> when (n) {
                1 -> NumMath.log10(args[0], mc)
                2 -> NumMath.logBase(args[0], args[1], mc)
                else -> throw EvalException(EvalError.Arity(call.name, "1 or 2", n, call.span))
            }
            "log10", "lg" -> { need(1); NumMath.log10(args[0], mc) }
            "log2" -> { need(1); NumMath.log2(args[0], mc) }

            "sin" -> { need(1); NumMath.sin(toRadians(args[0]), mc) }
            "cos" -> { need(1); NumMath.cos(toRadians(args[0]), mc) }
            "tan" -> { need(1); NumMath.tan(toRadians(args[0]), mc) }
            "asin", "arcsin" -> { need(1); fromRadians(NumMath.asin(args[0], mc)) }
            "acos", "arccos" -> { need(1); fromRadians(NumMath.acos(args[0], mc)) }
            "atan", "arctan" -> { need(1); fromRadians(NumMath.atan(args[0], mc)) }
            "atan2" -> { need(2); fromRadians(NumMath.atan2(args[0], args[1], mc)) }

            "sinh" -> { need(1); NumMath.sinh(args[0], mc) }
            "cosh" -> { need(1); NumMath.cosh(args[0], mc) }
            "tanh" -> { need(1); NumMath.tanh(args[0], mc) }
            "asinh" -> { need(1); NumMath.asinh(args[0], mc) }
            "acosh" -> { need(1); NumMath.acosh(args[0], mc) }
            "atanh" -> { need(1); NumMath.atanh(args[0], mc) }

            "fact", "factorial" -> { need(1); NumMath.factorial(args[0]) }
            "npr", "perm" -> { need(2); NumMath.nPr(args[0], args[1]) }
            "ncr", "comb", "choose" -> { need(2); NumMath.nCr(args[0], args[1]) }

            "sign", "sgn", "signum" -> { need(1); Num.of(args[0].signum().toLong()) }
            "int", "trunc" -> { need(1); NumMath.truncate(args[0]) }
            "frac" -> { need(1); NumMath.frac(args[0]) }
            "floor" -> { need(1); NumMath.floor(args[0]) }
            "ceil" -> { need(1); NumMath.ceil(args[0]) }
            "round" -> { need(1); NumMath.round(args[0]) }
            "mod" -> { need(2); NumMath.mod(args[0], args[1]) }
            "min" -> { need(2); NumMath.min(args[0], args[1]) }
            "max" -> { need(2); NumMath.max(args[0], args[1]) }
            "gcd" -> { need(2); NumMath.gcd(args[0], args[1]) }
            "lcm" -> { need(2); NumMath.lcm(args[0], args[1]) }
            "hypot" -> { need(2); NumMath.hypot(args[0], args[1], mc) }

            "rad", "torad" -> { need(1); degreesToRadians(args[0]) }
            "deg", "todeg" -> { need(1); radiansToDegrees(args[0]) }
            "dms" -> { need(3); dmsToDecimal(args[0], args[1], args[2]) }
            "polarr" -> { need(2); NumMath.hypot(args[0], args[1], mc) }
            "polara" -> { need(2); fromRadians(NumMath.atan2(args[1], args[0], mc)) }
            "rectx" -> { need(2); args[0] * NumMath.cos(toRadians(args[1]), mc) }
            "recty" -> { need(2); args[0] * NumMath.sin(toRadians(args[1]), mc) }

            "percent" -> { need(1); args[0] / Num.of(100) }
            "percentof" -> { need(2); (args[0] / Num.of(100)) * args[1] }
            "percentchange" -> { need(2); (args[1] - args[0]) / args[0] * Num.of(100) }

            else -> throw EvalException(EvalError.UnknownSymbol(call.name, call.span))
        }
    }

    // -----------------------------------------------------------------------
    // Angle handling (real). Degrees/grads become an exact rational multiple of π.
    // -----------------------------------------------------------------------
    private fun toRadians(x: Num): Num = when (settings.angle) {
        AngleMode.RAD -> x
        AngleMode.DEG -> degreesToRadians(x)
        AngleMode.GRAD -> x * SymConst.of(Num.of(1, 200), Konst.PI)
    }

    private fun fromRadians(x: Num): Num = when (settings.angle) {
        AngleMode.RAD -> x
        AngleMode.DEG -> radiansToDegrees(x)
        AngleMode.GRAD -> x * Num.of(200) / SymConst.of(Num.ONE, Konst.PI)
    }

    private fun degreesToRadians(x: Num): Num = x * SymConst.of(Num.of(1, 180), Konst.PI)
    private fun radiansToDegrees(x: Num): Num = x * Num.of(180) / SymConst.of(Num.ONE, Konst.PI)

    private fun dmsToDecimal(d: Num, m: Num, s: Num): Num {
        val magnitude = d.abs() + m / Num.of(60) + s / Num.of(3600)
        return if (d.signum() < 0) -magnitude else magnitude
    }

    private fun integerIndex(x: Num, call: Call): BigInteger =
        x.toBigIntegerExactOrNull()
            ?: throw EvalException(EvalError.Domain("root index must be an integer", call.span, "root"))

    private fun realArg(v: Value, fn: String, span: SourceSpan): Num = when (v) {
        is RealValue -> v.num
        is ComplexValue -> throw EvalException(EvalError.Domain("'$fn' expects a real argument", span, fn))
        is QuantityValue -> throw EvalException(
            EvalError.Domain("'$fn' expects a plain number, not a quantity with units", span, fn),
        )
    }

    private fun arity(call: Call, expected: Int, actual: Int) {
        if (actual != expected) throw EvalException(EvalError.Arity(call.name, expected.toString(), actual, call.span))
    }

    private inline fun <T> spanned(span: SourceSpan, block: () -> T): T = try {
        block()
    } catch (f: ArithFault) {
        throw EvalException(faultToError(f, span))
    } catch (a: ArithmeticException) {
        throw EvalException(EvalError.Domain(a.message ?: "value is undefined or out of range", span))
    }

    private fun faultToError(f: ArithFault, span: SourceSpan): EvalError = when (f) {
        is ArithFault.DivideByZero -> EvalError.DivideByZero(span, f.message ?: "division by zero")
        is ArithFault.Domain -> EvalError.Domain(f.message ?: "domain error", span, f.function)
        is ArithFault.Overflow -> EvalError.Overflow(f.message ?: "result too large", span)
        is ArithFault.Underflow -> EvalError.Underflow(f.message ?: "result too small", span)
        is ArithFault.Unsupported -> EvalError.Unsupported(f.message ?: "unsupported", span)
    }

    private companion object {
        val HUNDRED: Value = RealValue(Num.of(100))

        /**
         * Maximum AST depth the evaluator will recurse through.
         *
         * Mirrors the parser's own bound, deliberately: with left spines walked
         * iteratively, no expression the parser accepts reaches this — nested
         * parens collapse to no AST depth at all, and every shape that DOES build
         * depth (`^` chains, unary runs, nested calls) recurses in the parser and
         * is capped there first. So this bounds trees that arrive by other means,
         * and leaves parseable input untouched.
         */
        const val MAX_EVAL_DEPTH = 512
    }
}
