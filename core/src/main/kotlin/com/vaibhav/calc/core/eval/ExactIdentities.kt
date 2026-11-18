package com.vaibhav.calc.core.eval

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.num.*

/** Small domain-checked identities of the written expression, never inferred from rounded digits. */
internal object ExactIdentities {
    /** Source spans are irrelevant. Independent approximate-value constructors are not a shared quantity. */
    fun same(a: Expr, b: Expr): Boolean {
        val pending = ArrayDeque<Pair<Expr, Expr>>()
        pending.addLast(a to b)
        var visited = 0
        while (pending.isNotEmpty()) {
            if (++visited > 4096) return false
            val (x, y) = pending.removeLast()
            when {
                x is NumberLit && y is NumberLit -> if (x.value != y.value) return false
                x is NameRef && y is NameRef -> if (x.name != y.name) return false
                x is Unary && y is Unary -> { if (x.op != y.op) return false; pending.addLast(x.operand to y.operand) }
                x is Postfix && y is Postfix -> { if (x.op != y.op) return false; pending.addLast(x.operand to y.operand) }
                x is Binary && y is Binary -> {
                    if (x.op != y.op) return false
                    pending.addLast(x.left to y.left); pending.addLast(x.right to y.right)
                }
                x is Call && y is Call -> {
                    if (x.name.equals("approx", true) || !x.name.equals(y.name, true) || x.args.size != y.args.size) return false
                    x.args.indices.forEach { pending.addLast(x.args[it] to y.args[it]) }
                }
                x is UnitApply && y is UnitApply -> {
                    if (x.unit != y.unit) return false
                    pending.addLast(x.value to y.value)
                }
                else -> return false
            }
        }
        return true
    }

    private fun nonzero(n: Num): Boolean = try { n.establishedSign("a cancelled factor") != 0 } catch (_: ArithFault.Unsupported) { false }
    private fun within(n: Num, lo: Num, hi: Num): Boolean {
        if (n !is Dec && (n == lo || n == hi)) return true
        val b = n.bounds() ?: return false
        return b.lower >= lo.bounds()!!.upper && b.upper <= hi.bounds()!!.lower
    }
    private fun halfTurn(settings: Settings): Num = when (settings.angle) {
        AngleMode.DEG -> Num.of(180)
        AngleMode.RAD -> SymConst.of(Num.ONE, Konst.PI)
        AngleMode.GRAD -> Num.of(200)
    }
    private fun squareCall(e: Expr): Call? = (e as? Binary)?.takeIf {
        it.op == BinaryOp.POW && (it.right as? NumberLit)?.value == Num.TWO
    }?.left as? Call

    fun binary(e: Binary, left: Num, right: Num, settings: Settings, number: (Expr) -> Num?): Num? {
        val unknown = (left is Dec && left.absoluteError == null) || (right is Dec && right.absoluteError == null)
        if (unknown) {
            // A shared real register can cancel. A function evaluated only at its nominal
            // input may have an unproved domain, which an exact constant must not erase.
            return if (e.op == BinaryOp.SUB && e.left is NameRef && same(e.left, e.right)) Num.ZERO else null
        }
        if (e.op == BinaryOp.SUB && same(e.left, e.right)) return Num.ZERO
        val sum = e.left as? Binary
        if (e.op == BinaryOp.SUB && sum?.op == BinaryOp.ADD && same(sum.right, e.right)) return number(sum.left)
        if (e.op == BinaryOp.ADD && sum?.op == BinaryOp.SUB && same(sum.right, e.right)) return number(sum.left)
        if (e.op == BinaryOp.DIV && nonzero(right)) {
            if (same(e.left, e.right)) return Num.ONE
            val product = e.left as? Binary
            if (product?.op == BinaryOp.MUL) {
                if (same(product.right, e.right)) return number(product.left)
                if (same(product.left, e.right)) return number(product.right)
            }
            val a = e.left as? Call
            val b = e.right as? Call
            if (a != null && b != null && a.name.equals(b.name, true) &&
                a.name.lowercase() in setOf("ln", "log", "log10", "log2") && a.args.size == 1 && b.args.size == 1) {
                val x = number(a.args[0]) ?: return null
                val base = number(b.args[0]) ?: return null
                return NumMath.logBase(base, x, settings.computeMc)
            }
        }
        if (e.op == BinaryOp.POW) {
            val root = e.left as? Call
            if (root != null && root.args.size == 1 &&
                ((root.name.equals("cbrt", true) && right == Num.of(3)) || (root.name.equals("sqrt", true) && right == Num.TWO)))
                return number(root.args[0])
            val logarithm = e.right as? Call
            if (left is SymConst && left.konst == Konst.E && left.coeff == Num.ONE &&
                logarithm?.name?.lowercase() == "ln" && logarithm.args.size == 1) {
                val x = number(logarithm.args[0]) ?: return null
                if (nonzero(x) && x.signum() > 0) return x
            }
        }
        val a = squareCall(e.left)
        val b = squareCall(e.right)
        if (a != null && b != null && a.args.size == 1 && b.args.size == 1 && same(a.args[0], b.args[0])) {
            val names = a.name.lowercase() to b.name.lowercase()
            if (e.op == BinaryOp.ADD && names in setOf("sin" to "cos", "cos" to "sin")) return Num.ONE
            if (e.op == BinaryOp.SUB && names == ("cosh" to "sinh")) return Num.ONE
        }
        val ac = e.left as? Call
        val bc = e.right as? Call
        if (e.op == BinaryOp.ADD && ac != null && bc != null && ac.args.size == 1 && bc.args.size == 1 &&
            setOf(ac.name.lowercase(), bc.name.lowercase()) == setOf("asin", "acos") && same(ac.args[0], bc.args[0])) {
            val argument = number(ac.args[0]) ?: return null
            if (within(argument, -Num.ONE, Num.ONE)) return halfTurn(settings) / Num.TWO
        }
        return null
    }

    fun call(call: Call, settings: Settings, number: (Expr) -> Num?): Num? {
        if (call.name.equals("mod", true) && call.args.size == 2) {
            val product = call.args[0] as? Binary
            if (product?.op == BinaryOp.MUL) {
                val factor = when {
                    same(product.left, call.args[1]) -> product.right
                    same(product.right, call.args[1]) -> product.left
                    else -> null
                }
                if (factor != null && number(factor)?.toBigIntegerExactOrNull() != null &&
                    number(call.args[1])?.let(::nonzero) == true) return Num.ZERO
            }
        }
        val inner = call.args.singleOrNull() as? Call ?: return null
        if (inner.args.size != 1) return null
        val pair = call.name.lowercase() to inner.name.lowercase()
        val x = when (pair) {
            "exp" to "ln", "ln" to "exp", "tan" to "atan", "sin" to "asin", "cos" to "acos",
            "sinh" to "asinh", "asinh" to "sinh", "tanh" to "atanh", "atanh" to "tanh",
            "cosh" to "acosh", "acosh" to "cosh", "asin" to "sin", "acos" to "cos" -> number(inner.args[0]) ?: return null
            else -> return null
        }
        return when (pair) {
            "exp" to "ln" -> if (nonzero(x) && x.signum() > 0) x else null
            "sin" to "asin", "cos" to "acos", "tanh" to "atanh", "cosh" to "acosh" -> x // inner domain was checked
            "acosh" to "cosh" -> x.abs()
            "asin" to "sin" -> if (within(x, -halfTurn(settings) / Num.TWO, halfTurn(settings) / Num.TWO)) x else null
            "acos" to "cos" -> if (within(x, Num.ZERO, halfTurn(settings))) x else null
            else -> x
        }
    }
}
