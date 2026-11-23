package com.vaibhav.calc.core.graph

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.name.Functions
import com.vaibhav.calc.core.num.*
import java.math.BigDecimal
import java.util.IdentityHashMap

/**
 * Optional, deliberately small derivative certificate. A strict global derivative
 * sign proves at most one root, so a continuous function needs only its endpoints
 * to establish a bracket. Unproved forms retain the ordinary sampled search.
 *
 * sin/cos derivatives are enclosed by [-1,1], including the DEG/GRAD chain factor.
 * No sampled derivatives, nominal comparisons, or proximity rules supply a proof.
 */
internal object RootMonotonicity {
    private const val PRECISION = 66
    private val ZERO = DecimalBounds.point(BigDecimal.ZERO)
    private val ONE = DecimalBounds.point(BigDecimal.ONE)
    private val UNIT_RANGE = DecimalBounds(BigDecimal.ONE.negate(), BigDecimal.ONE)

    fun direction(
        expression: Expr,
        unknown: String,
        angle: AngleMode,
        constant: (Expr) -> Num?,
        keepGoing: () -> Boolean,
    ): Int? {
        fun children(node: Expr): List<Expr> = when (node) {
            is Unary -> listOf(node.operand)
            is Binary -> listOf(node.left, node.right)
            is Call -> node.args
            is Postfix -> listOf(node.operand)
            is UnitApply -> listOf(node.value)
            is NumberLit, is NameRef -> emptyList()
        }
        val dependent = IdentityHashMap<Expr, Boolean>()
        val ordered = ArrayList<Expr>()
        val pending = ArrayDeque<Pair<Expr, Boolean>>()
        pending.addLast(expression to false)
        var visited = 0
        while (pending.isNotEmpty()) {
            if (!keepGoing()) return null
            val (node, complete) = pending.removeLast()
            if (!complete) {
                if (++visited > RootFinder.MAX_EXPRESSION_NODES) return null
                pending.addLast(node to true)
                children(node).forEach { pending.addLast(it to false) }
            } else {
                dependent[node] = (node is NameRef && node.name.equals(unknown, ignoreCase = true)) ||
                    children(node).any { dependent[it] == true }
                ordered += node
            }
        }
        // Check support before doing any optional closed-subtree evaluations. In
        // particular polynomial powers retain their established scan/work contract.
        for (node in ordered) {
            if (!keepGoing()) return null
            if (dependent[node] != true) continue
            when (node) {
                is Binary -> {
                    // a+b% means a+a*b/100 in the evaluator, not ordinary addition.
                    if (node.right is Postfix && node.right.op == PostfixOp.PERCENT) return null
                    when (node.op) {
                        BinaryOp.ADD, BinaryOp.SUB -> Unit
                        BinaryOp.MUL -> if (dependent[node.left] == true && dependent[node.right] == true) return null
                        BinaryOp.DIV -> if (dependent[node.right] == true) return null
                        BinaryOp.POW -> return null
                    }
                }
                is Call -> if (node.args.size != 1 || Functions.get(node.name)?.name !in setOf("sin", "cos")) return null
                is Postfix, is UnitApply -> return null
                is NumberLit, is NameRef, is Unary -> Unit
            }
        }
        val closed = IdentityHashMap<Expr, DecimalBounds>()
        fun coefficient(node: Expr): DecimalBounds? {
            closed[node]?.let { return it }
            val value = constant(node) ?: return null
            // A measured/unknown coefficient does not define a single certified
            // function here. Leave its uncertainty decisions to the original scan.
            if (value is Dec && value.absoluteError?.signum() != 0) return null
            return value.bounds(PRECISION)?.also { closed[node] = it }
        }
        val derivative = IdentityHashMap<Expr, DecimalBounds>()
        var radiansPerUnit: DecimalBounds? = null
        fun angleScale(): DecimalBounds = radiansPerUnit ?: when (angle) {
            AngleMode.RAD -> ONE
            AngleMode.DEG -> DecimalEnclosures.constant(Konst.PI, PRECISION)
                .divide(DecimalBounds.point(BigDecimal(180)), PRECISION)
            AngleMode.GRAD -> DecimalEnclosures.constant(Konst.PI, PRECISION)
                .divide(DecimalBounds.point(BigDecimal(200)), PRECISION)
        }.also { radiansPerUnit = it }
        try {
            for (node in ordered) {
                if (!keepGoing()) return null
                val value = if (dependent[node] != true) ZERO else when (node) {
                    is NameRef -> ONE
                    is Unary -> derivative.getValue(node.operand).let { if (node.op == UnaryOp.NEG) it.negate() else it }
                    is Binary -> when (node.op) {
                        BinaryOp.ADD -> derivative.getValue(node.left).add(derivative.getValue(node.right), PRECISION)
                        BinaryOp.SUB -> derivative.getValue(node.left).subtract(derivative.getValue(node.right), PRECISION)
                        BinaryOp.MUL -> if (dependent[node.left] != true)
                            (coefficient(node.left) ?: return null).multiply(derivative.getValue(node.right), PRECISION)
                        else derivative.getValue(node.left).multiply(coefficient(node.right) ?: return null, PRECISION)
                        BinaryOp.DIV -> derivative.getValue(node.left).divide(coefficient(node.right) ?: return null, PRECISION)
                        BinaryOp.POW -> return null
                    }
                    is Call -> UNIT_RANGE.multiply(angleScale(), PRECISION)
                        .multiply(derivative.getValue(node.args.single()), PRECISION)
                    else -> return null
                }
                derivative[node] = value
            }
        } catch (_: ArithFault) {
            // Optional certification must not introduce a new numerical refusal.
            // The original evaluator/search still applies its own resource guards.
            return null
        }
        val bound = derivative[expression] ?: return null
        return when {
            bound.lower.signum() > 0 -> 1
            bound.upper.signum() < 0 -> -1
            else -> null
        }
    }
}
