package com.vaibhav.calc.core.matrix

import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.NumMath
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.DecimalBounds
import com.vaibhav.calc.core.num.bounds
import com.vaibhav.calc.core.num.requireKnownPoint
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.num.unaryMinus
import java.math.MathContext

/** Vector arithmetic over the exact number tower (CalcForAll 1.2). Dot, cross and projection stay exact. */
object Vectors {
    const val MAX_DIMENSION = 16

    private fun check(a: List<Num>, b: List<Num>? = null) {
        if (a.isEmpty() || a.size > MAX_DIMENSION) throw ArithFault.Domain("a vector needs 1 to $MAX_DIMENSION components", "vector")
        if (b != null && b.size != a.size) throw ArithFault.Domain("both vectors need the same number of components", "vector")
    }

    fun dot(a: List<Num>, b: List<Num>): Num {
        check(a, b)
        var sum: Num = Num.ZERO
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    fun cross(a: List<Num>, b: List<Num>): List<Num> {
        if (a.size != 3 || b.size != 3) throw ArithFault.Domain("the cross product needs two vectors of 3 components", "vector")
        return listOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    }

    /** `|a|` — an exact integer or surd when the sum of squares allows it. */
    fun length(a: List<Num>, mc: MathContext): Num {
        check(a)
        return NumMath.sqrt(dot(a, a), mc)
    }

    /** The component of [a] along [b]: `(a·b / b·b)·b`. Exact. */
    fun projection(a: List<Num>, b: List<Num>): List<Num> {
        val bb = dot(b, b)
        if (bb.isZero) throw ArithFault.Domain("cannot project onto the zero vector", "vector")
        val k = dot(a, b) / bb
        return b.map { it * k }
    }

    /** `cos θ` between [a] and [b], clamped to [−1, 1] against rounding at the ends. */
    fun cosine(a: List<Num>, b: List<Num>, mc: MathContext): Num {
        a.forEach { it.requireKnownPoint("vector angle") }
        b.forEach { it.requireKnownPoint("vector angle") }
        val aa = dot(a, a)
        val bb = dot(b, b)
        if (aa.isZero || bb.isZero) throw ArithFault.Domain("the angle with a zero vector is undefined", "vector")
        val cos = dot(a, b) / NumMath.sqrt(aa * bb, mc)
        if (cos !is Dec) return cos
        val point = cos.value.max(java.math.BigDecimal.ONE.negate()).min(java.math.BigDecimal.ONE)
        val interval = cos.bounds() ?: return Dec(point)
        val lower = interval.lower.max(java.math.BigDecimal.ONE.negate())
        val upper = interval.upper.min(java.math.BigDecimal.ONE)
        if (lower > upper) throw ArithFault.Unsupported("the computed cosine bound is inconsistent with a real vector angle")
        return DecimalBounds(lower, upper).around(point)
    }
}
