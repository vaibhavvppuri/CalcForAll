package com.vaibhav.calc.core.symbolic

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.constants.Constants
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Evaluator
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.*
import java.math.BigDecimal
import java.math.BigInteger

/** Check closed subexpressions before a rule can erase an undefined constant. */
internal object ClosedExpressions {
    private val MAX_ARGUMENT = BigDecimal("1000000")
    private val MAX_EXP_ARGUMENT = BigDecimal("1000")
    private val MAX_MAGNITUDE = BigDecimal("1e1000")
    private val FUNCTIONS = setOf(
        "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh",
        "asinh", "acosh", "atanh", "sqrt", "cbrt", "root", "exp", "ln", "log10", "log2",
        "abs", "floor", "ceil", "round", "int", "frac", "mod", "min", "max", "gcd", "lcm", "hypot", "fact",
    )

    fun validate(root: Expr) {
        var evaluated = 0
        fun visit(e: Expr): Num? {
            if (Thread.currentThread().isInterrupted) unsupported("symbolic computation was cancelled")
            if (e is NumberLit) return e.value
            if (e is NameRef) return (Constants.resolve(e.name, RAD_SETTINGS.computeMc) as? RealValue)?.num
            val values = Expressions.children(e).map(::visit)
            if (e is Binary && e.op == BinaryOp.DIV && values[1]?.isZero == true) {
                throw SymbolicFault(com.vaibhav.calc.core.error.EvalError.DivideByZero(e.right.span))
            }
            if (values.any { it == null }) return null
            if (++evaluated > 512) unsupported("too many constant operations in symbolic input", e.span)
            val args = values.map { number(it!!) }
            val reduced = when (e) {
                is Unary -> e.copy(operand = args[0])
                is Binary -> {
                    if (e.op == BinaryOp.POW) {
                        val exponent = values[1]!!.toBigDecimal(RAD_SETTINGS.computeMc)
                        if (exponent.abs() > BigDecimal(1024)) unsupported("constant power exceeds the symbolic arithmetic budget", e.span)
                        val base = values[0] as? Rational
                        if (base != null && exponent.abs().multiply(BigDecimal(maxOf(base.num.bitLength(), base.den.bitLength()))) > BigDecimal(8192)) {
                            unsupported("constant power exceeds the symbolic arithmetic budget", e.span)
                        }
                    }
                    e.copy(left = args[0], right = args[1])
                }
                is Call -> {
                    if (e.name !in FUNCTIONS) unsupported("constant '${e.name}' is outside the symbolic evaluator's supported functions", e.span)
                    val limit = if (e.name in setOf("exp", "sinh", "cosh", "tanh")) MAX_EXP_ARGUMENT else MAX_ARGUMENT
                    if (values.any { it!!.toBigDecimal(RAD_SETTINGS.computeMc).abs() > limit }) {
                        unsupported("constant function argument exceeds the symbolic computation budget", e.span)
                    }
                    if (e.name == "fact" && (values[0]!!.toBigIntegerExactOrNull()?.let { it in BigInteger.ZERO..BigInteger.valueOf(500) } != true)) {
                        unsupported("symbolic factorial requires an integer from 0 to 500", e.span)
                    }
                    // root(x, n): the second argument is the index; a large
                    // radicand alone does not imply expensive root degree.
                    if (e.name == "root" && values[1]!!.toBigDecimal(RAD_SETTINGS.computeMc).abs() > BigDecimal(64)) {
                        unsupported("symbolic root degree exceeds 64", e.span)
                    }
                    e.copy(args = args)
                }
                else -> unsupported("unsupported closed symbolic expression", e.span)
            }
            val value = when (val result = Evaluator(RAD_SETTINGS, Environment()).eval(reduced)) {
                is CalcResult.Err -> throw SymbolicFault(result.error)
                is CalcResult.Ok -> (result.value as? RealValue)?.num
                    ?: domain("symbolic calculus requires real scalar values", e.span)
            }
            if (value is Rational && maxOf(value.num.bitLength(), value.den.bitLength()) > 8192) {
                unsupported("constant result exceeds the symbolic arithmetic budget", e.span)
            }
            if (value.toBigDecimal(RAD_SETTINGS.computeMc).abs() > MAX_MAGNITUDE) unsupported("constant result is too large for symbolic processing", e.span)
            return value
        }
        visit(root)
    }
}
