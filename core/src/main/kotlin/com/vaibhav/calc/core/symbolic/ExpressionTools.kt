package com.vaibhav.calc.core.symbolic

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.constants.Constants
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.name.Functions
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.parse.Parser
import com.vaibhav.calc.core.source.SourceSpan
import java.math.BigInteger

internal val GENERATED_SPAN = SourceSpan(0, 0)
internal val RAD_SETTINGS = Settings(angle = AngleMode.RAD, computePrecision = 48, displayPrecision = 40)
internal fun number(n: Num): Expr = NumberLit(n, GENERATED_SPAN)
internal fun integer(n: Int): Expr = number(Num.of(n))
internal fun variable(name: String): Expr = NameRef(name, GENERATED_SPAN)
internal fun binary(op: BinaryOp, a: Expr, b: Expr): Expr = Binary(op, a, b, GENERATED_SPAN)
internal fun add(a: Expr, b: Expr): Expr = binary(BinaryOp.ADD, a, b)
internal fun sub(a: Expr, b: Expr): Expr = binary(BinaryOp.SUB, a, b)
internal fun mul(a: Expr, b: Expr): Expr = binary(BinaryOp.MUL, a, b)
internal fun div(a: Expr, b: Expr): Expr = binary(BinaryOp.DIV, a, b)
internal fun pow(a: Expr, b: Expr): Expr = binary(BinaryOp.POW, a, b)
internal fun neg(a: Expr): Expr = Unary(UnaryOp.NEG, a, GENERATED_SPAN)
internal fun call(name: String, vararg args: Expr): Expr = Call(name, args.toList(), GENERATED_SPAN)
internal fun Expr.rational(): Rational? = (this as? NumberLit)?.value as? Rational
internal fun Expr.isNumber(n: Int): Boolean = rational() == Num.of(n)

internal class SymbolicFault(val error: EvalError) : RuntimeException(error.message)
internal fun unsupported(message: String, span: SourceSpan? = null): Nothing =
    throw SymbolicFault(EvalError.Unsupported(message, span))
internal fun domain(message: String, span: SourceSpan? = null): Nothing =
    throw SymbolicFault(EvalError.Domain(message, span))

internal inline fun <T> symbolicResult(block: () -> T): CalcResult<T> = try {
    CalcResult.Ok(block())
} catch (e: SymbolicFault) {
    CalcResult.Err(e.error)
} catch (e: ArithFault) {
    CalcResult.Err(when (e) {
        is ArithFault.DivideByZero -> EvalError.DivideByZero(null, e.message ?: "division by zero")
        is ArithFault.Domain -> EvalError.Domain(e.message ?: "domain error", null, e.function)
        is ArithFault.Overflow -> EvalError.Overflow(e.message ?: "result too large", null)
        is ArithFault.Underflow -> EvalError.Underflow(e.message ?: "result too small", null)
        is ArithFault.Unsupported -> EvalError.Unsupported(e.message ?: "unsupported expression", null)
    })
} catch (e: ArithmeticException) {
    CalcResult.Err(EvalError.Overflow(e.message ?: "arithmetic limit reached", null))
}

internal object Expressions {
    const val MAX_INPUT_LENGTH = 4096
    const val MAX_NODES = 1024
    const val MAX_DEPTH = 96
    const val MAX_RESULT_NODES = 4096
    private val MAX_BITS = 8192

    fun parse(input: String, selectedVariable: String): Expr {
        if (!ReservedNames.isUnknown(selectedVariable)) {
            unsupported("choose x, y or z as the calculus variable")
        }
        if (input.length > MAX_INPUT_LENGTH) unsupported("symbolic input exceeds $MAX_INPUT_LENGTH characters")
        // The ordinary parser has a larger recursion budget. Refuse pathological nesting
        // before entering it, then check the actual AST (flat sums can be deep too).
        var level = 0
        for (c in input) {
            if (c == '(' && ++level > MAX_DEPTH) unsupported("symbolic expression is nested too deeply")
            if (c == ')') level--
        }
        val parsed = when (val result = Parser.parse(input)) {
            is CalcResult.Err -> throw SymbolicFault(result.error)
            is CalcResult.Ok -> result.value
        }
        checkSize(parsed)
        val normalized = normalize(parsed)
        checkSize(normalized)
        ClosedExpressions.validate(normalized)
        return normalized
    }

    fun checkSize(root: Expr, result: Boolean = false) {
        val pending = ArrayDeque<Pair<Expr, Int>>()
        pending.addLast(root to 0)
        var count = 0
        while (pending.isNotEmpty()) {
            val (node, depth) = pending.removeLast()
            if (++count > (if (result) MAX_RESULT_NODES else MAX_NODES)) unsupported("symbolic expression has too many terms")
            if (depth > MAX_DEPTH) unsupported("symbolic expression is nested too deeply", node.span)
            if (node is NumberLit) {
                val r = node.value as? Rational
                if (r != null && (r.num.bitLength() > MAX_BITS || r.den.bitLength() > MAX_BITS)) {
                    unsupported("numeric literal is too large for symbolic processing", node.span)
                }
            }
            children(node).forEach { pending.addLast(it to depth + 1) }
        }
    }

    fun children(e: Expr): List<Expr> = when (e) {
        is NumberLit, is NameRef -> emptyList()
        is Unary -> listOf(e.operand)
        is Binary -> listOf(e.left, e.right)
        is Call -> e.args
        is Postfix -> listOf(e.operand)
        is UnitApply -> listOf(e.value)
    }

    /** Preserve the calculator's contextual-percent law before any algebra occurs. */
    private fun normalize(e: Expr): Expr = when (e) {
        is NumberLit -> e
        is NameRef -> {
            if (e.name == "i") unsupported("symbolic calculus currently works over the reals", e.span)
            if (ReservedNames.isUnknown(e.name)) e.copy(name = e.name.lowercase())
            else {
                val c = Constants.resolve(e.name, RAD_SETTINGS.computeMc)
                if (c != null && c !is RealValue) unsupported("symbolic calculus does not accept physical quantities", e.span)
                if (c == null && e.name !in ReservedNames.REGISTERS) {
                    unsupported("'${e.name}' is not a symbolic variable or real constant; use x, y or z", e.span)
                }
                e
            }
        }
        is UnitApply -> unsupported("use scalar expressions without units for symbolic calculus", e.span)
        is Unary -> e.copy(operand = normalize(e.operand))
        is Postfix -> when (e.op) {
            PostfixOp.PERCENT -> div(normalize(e.operand), integer(100))
            PostfixOp.FACTORIAL -> {
                val n = normalize(e.operand)
                val r = n.rational()?.toBigIntegerExactOrNull()
                    ?: unsupported("symbolic factorial is not supported", e.span)
                if (r.signum() < 0 || r > BigInteger.valueOf(500)) unsupported("symbolic factorial requires an integer from 0 to 500", e.span)
                number(NumMath.factorial(Num.of(r)))
            }
        }
        is Binary -> {
            val l = normalize(e.left)
            val r = normalize(e.right)
            if (e.right is Postfix && e.right.op == PostfixOp.PERCENT && e.op in setOf(BinaryOp.ADD, BinaryOp.SUB)) {
                binary(e.op, l, mul(l, r))
            } else e.copy(left = l, right = r)
        }
        is Call -> {
            val fn = Functions.get(e.name) ?: unsupported("unknown function '${e.name}'", e.span)
            if (e.args.size !in fn.arities) throw SymbolicFault(EvalError.Arity(fn.name, fn.arities.joinToString(" or "), e.args.size, e.span))
            val args = e.args.map(::normalize)
            when (fn.name) {
                "pow" -> pow(args[0], args[1])
                "sq" -> pow(args[0], integer(2))
                "percent" -> div(args[0], integer(100))
                "percentof" -> div(mul(args[0], args[1]), integer(100))
                "log" -> if (args.size == 1) call("log10", args[0]) else div(call("ln", args[1]), call("ln", args[0]))
                else -> e.copy(name = fn.name, args = args)
            }
        }
    }

    fun dependsOn(e: Expr, name: String): Boolean = when (e) {
        is NameRef -> e.name == name
        else -> children(e).any { dependsOn(it, name) }
    }

    fun containsSymbol(e: Expr): Boolean = when (e) {
        is NameRef -> !ReservedNames.isConstant(e.name)
        else -> children(e).any(::containsSymbol)
    }

    /** A deliberately small proof of totality over all real symbols. */
    fun isTotal(e: Expr): Boolean = when (e) {
        is NumberLit -> true
        is NameRef -> true
        is Unary -> isTotal(e.operand)
        is Binary -> when (e.op) {
            BinaryOp.ADD, BinaryOp.SUB, BinaryOp.MUL -> isTotal(e.left) && isTotal(e.right)
            BinaryOp.DIV -> e.right.rational()?.let { !it.isZero } == true && isTotal(e.left)
            BinaryOp.POW -> e.right.rational()?.toBigIntegerExactOrNull()?.let { it.signum() >= 0 } == true && isTotal(e.left)
        }
        is Call -> e.name in setOf("sin", "cos", "sinh", "cosh", "tanh", "asinh", "atan", "abs", "cbrt", "exp") && e.args.all(::isTotal)
        else -> false
    }

    /** No cancellation of x/x, 0*(1/x), or f(x)-f(x) with an unproved domain. */
    fun simplifyLocal(e: Expr): Expr {
        val s: Expr = when (e) {
            is NumberLit, is NameRef -> e
            is Unary -> {
                val v = simplifyLocal(e.operand)
                when {
                    e.op == UnaryOp.POS -> v
                    v is NumberLit -> number(-v.value)
                    v is Unary && v.op == UnaryOp.NEG -> v.operand
                    else -> e.copy(operand = v)
                }
            }
            is Binary -> {
                val l = simplifyLocal(e.left)
                val r = simplifyLocal(e.right)
                val a = l.rational()
                val b = r.rational()
                val exact: Num? = if (a != null && b != null) when (e.op) {
                    BinaryOp.ADD -> a + b
                    BinaryOp.SUB -> a - b
                    BinaryOp.MUL -> a * b
                    BinaryOp.DIV -> a / b
                    BinaryOp.POW -> b.toBigIntegerExactOrNull()?.takeIf { it.abs() <= BigInteger.valueOf(64) }
                        ?.let { NumMath.pow(a, b, RAD_SETTINGS.computeMc) }
                } else null
                when {
                    exact != null -> number(exact)
                    e.op == BinaryOp.ADD && l.isNumber(0) -> r
                    e.op in setOf(BinaryOp.ADD, BinaryOp.SUB) && r.isNumber(0) -> l
                    e.op == BinaryOp.SUB && l.isNumber(0) -> simplifyLocal(neg(r))
                    e.op == BinaryOp.SUB && render(l) == render(r) && isTotal(l) -> integer(0)
                    e.op == BinaryOp.MUL && l.isNumber(1) -> r
                    e.op == BinaryOp.MUL && r.isNumber(1) -> l
                    e.op == BinaryOp.MUL && l.isNumber(-1) -> simplifyLocal(neg(r))
                    e.op == BinaryOp.MUL && r.isNumber(-1) -> simplifyLocal(neg(l))
                    e.op == BinaryOp.MUL && l.isNumber(0) && isTotal(r) -> integer(0)
                    e.op == BinaryOp.MUL && r.isNumber(0) && isTotal(l) -> integer(0)
                    e.op == BinaryOp.DIV && r.isNumber(1) -> l
                    e.op == BinaryOp.DIV && r.isNumber(-1) -> simplifyLocal(neg(l))
                    e.op == BinaryOp.POW && r.isNumber(1) -> l
                    e.op == BinaryOp.POW && r.isNumber(0) && isTotal(l) -> integer(1)
                    else -> e.copy(left = l, right = r)
                }
            }
            is Call -> {
                val args = e.args.map(::simplifyLocal)
                when {
                    e.name == "ln" && args.singleOrNull().isNumberOrNull(1) -> integer(0)
                    e.name == "ln" && (args.singleOrNull() as? NameRef)?.name == "e" -> integer(1)
                    e.name == "exp" && args.singleOrNull().isNumberOrNull(0) -> integer(1)
                    e.name in setOf("sin", "tan", "sinh", "tanh", "asin", "atan", "asinh", "atanh") && args.singleOrNull().isNumberOrNull(0) -> integer(0)
                    e.name in setOf("cos", "cosh") && args.singleOrNull().isNumberOrNull(0) -> integer(1)
                    e.name == "sqrt" && args.singleOrNull()?.rational()?.let { it.num.bitLength() < 40 && it.den.bitLength() < 40 } == true -> number(NumMath.sqrt(args[0].rational()!!, RAD_SETTINGS.computeMc))
                    e.name == "abs" && args.singleOrNull() is NumberLit -> number((args[0] as NumberLit).value.abs())
                    else -> e.copy(args = args)
                }
            }
            else -> e
        }
        return s
    }

    private fun Expr?.isNumberOrNull(value: Int): Boolean = this?.isNumber(value) == true

    fun render(e: Expr): String {
        fun precedence(x: Expr): Int = when (x) {
            is Binary -> when (x.op) { BinaryOp.ADD, BinaryOp.SUB -> 10; BinaryOp.MUL, BinaryOp.DIV -> 20; BinaryOp.POW -> 40 }
            is Unary -> 30
            is Postfix -> 50
            is NumberLit -> when {
                x.value is Rational && x.value.den != BigInteger.ONE -> 20
                x.value !is Rational && x.value !is Dec -> 20
                x.value.signum() < 0 -> 30
                else -> 100
            }
            else -> 100
        }
        fun child(x: Expr, min: Int, equal: Boolean = false): String {
            val value = render(x)
            val p = precedence(x)
            return if (p < min || (equal && p == min)) "($value)" else value
        }
        return when (e) {
            is NumberLit -> Formatter.exact(e.value, RAD_SETTINGS.copy(exactForm = ExactForm.FRACTION))
            is NameRef -> e.name
            is Unary -> if (e.op == UnaryOp.NEG) "-${child(e.operand, 30)}" else child(e.operand, 30)
            is Binary -> when (e.op) {
                BinaryOp.ADD -> "${child(e.left, 10)} + ${child(e.right, 10, true)}"
                BinaryOp.SUB -> "${child(e.left, 10)} - ${child(e.right, 10, true)}"
                BinaryOp.MUL -> "${child(e.left, 20)}*${child(e.right, 20, true)}"
                BinaryOp.DIV -> "${child(e.left, 20)}/${child(e.right, 20, true)}"
                BinaryOp.POW -> "${child(e.left, 40, true)}^${child(e.right, 40)}"
            }
            is Call -> "${e.name}(${e.args.joinToString(", ") { render(it) }})"
            is Postfix -> child(e.operand, 50) + if (e.op == PostfixOp.PERCENT) "%" else "!"
            is UnitApply -> "${child(e.value, 40)} ${e.unit}"
        }
    }

    fun conditions(e: Expr, derivative: Boolean = false): List<String> {
        val found = linkedSetOf<String>()
        fun visit(x: Expr) {
            children(x).forEach(::visit)
            when (x) {
                is Binary -> when (x.op) {
                    BinaryOp.DIV -> if (x.right.rational() == null) found += "${render(x.right)} != 0"
                    BinaryOp.POW -> {
                        val n = x.right.rational()
                        when {
                            n == null -> found += "${render(x.left)} > 0 for a general real power"
                            n.den.mod(BigInteger.valueOf(2)) == BigInteger.ZERO -> found += "${render(x.left)} ${if (n.num.signum() < 0 || derivative) ">" else ">="} 0"
                            n.num.signum() < 0 -> found += "${render(x.left)} != 0"
                        }
                    }
                    else -> Unit
                }
                is Call -> if (x.args.isNotEmpty()) {
                    val a = render(x.args[0])
                    when (x.name) {
                        "ln", "log10", "log2" -> found += "$a > 0"
                        "sqrt" -> found += "$a ${if (derivative) ">" else ">="} 0"
                        "tan" -> found += "cos($a) != 0"
                        "asin", "acos" -> found += if (derivative) "-1 < $a < 1" else "-1 <= $a <= 1"
                        "acosh" -> found += "$a ${if (derivative) ">" else ">="} 1"
                        "atanh" -> found += "-1 < $a < 1"
                        "abs", "cbrt" -> if (derivative) found += "$a != 0"
                    }
                }
                else -> Unit
            }
        }
        visit(e)
        return found.toList()
    }
}
