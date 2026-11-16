package com.vaibhav.calc.core.solve

import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.eval.Value
import com.vaibhav.calc.core.eval.toValue
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Complex
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.NumMath
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.signum
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.num.unaryMinus

/**
 * Equation solvers (spec D5). All arithmetic is exact-first: coefficients and
 * solutions stay rational where they can, roots become surds/decimals or complex
 * only when the mathematics forces it. Degenerate cases raise a typed
 * [ArithFault] that the facade converts to a [com.vaibhav.calc.core.error.EvalError].
 */
object Solvers {

    data class LinearResult(val root: Num, val steps: List<String>)
    data class QuadraticResult(val roots: List<Value>, val discriminant: Num, val steps: List<String>)
    data class SystemResult(val solution: List<Num>, val determinant: Num, val steps: List<String>)

    /** a·x + b = 0. */
    fun linear(a: Num, b: Num, settings: Settings): LinearResult {
        if (a.isZero) throw ArithFault.Domain("coefficient a must be non-zero for a linear equation", "linear")
        val x = -b / a
        return LinearResult(
            x,
            listOf(
                "${f(a, settings)}x + ${f(b, settings)} = 0",
                "x = −b / a = ${f(x, settings)}",
            ),
        )
    }

    /** a·x² + b·x + c = 0, with worked steps. */
    fun quadratic(a: Num, b: Num, c: Num, settings: Settings): QuadraticResult {
        if (a.isZero) throw ArithFault.Domain("coefficient a must be non-zero for a quadratic equation", "quadratic")
        val mc = settings.computeMc
        val four = Num.of(4)
        val disc = b * b - four * a * c
        val twoA = Num.of(2) * a
        val negB = -b

        val steps = mutableListOf(
            "${f(a, settings)}x² + ${f(b, settings)}x + ${f(c, settings)} = 0",
            "D = b² − 4ac = ${f(disc, settings)}",
        )

        val roots: List<Value> = when {
            disc.isZero -> {
                val r = negB / twoA
                steps += "D = 0 → one repeated real root"
                steps += "x = −b / 2a = ${f(r, settings)}"
                listOf(RealValue(r))
            }
            disc.signum() > 0 -> {
                val sq = NumMath.sqrt(disc, mc)
                val plus = (negB + sq) / twoA
                val minus = (negB - sq) / twoA
                // ASCENDING (C-6). The design's hero frame shows `x = 2, 3` and the
                // gap analysis settles it: "ascending is what a student expects."
                //
                // Compared rather than swapped: which branch is the lesser depends on
                // the SIGN OF a — with a < 0 the `+√D` branch is already the smaller,
                // so a fix that merely exchanged the two positions would be correct
                // for a > 0 and wrong here. Pinned both ways in RootOrderSpec.
                val lo = if (plus <= minus) plus else minus
                val hi = if (plus <= minus) minus else plus
                steps += "D > 0 → two real roots"
                steps += "√D = ${f(sq, settings)}"
                // The steps name the roots in the SAME order the list returns them;
                // two renderings of one result may not drift (Article 12).
                steps += "x = (−b ± √D) / 2a → x₁ = ${f(lo, settings)}, x₂ = ${f(hi, settings)}"
                listOf(RealValue(lo), RealValue(hi))
            }
            else -> {
                val sqAbs = NumMath.sqrt(-disc, mc)
                val re = negB / twoA
                val im = sqAbs / twoA
                steps += "D < 0 → two complex conjugate roots"
                steps += "√|D| = ${f(sqAbs, settings)}"
                steps += "x = −b/2a ± (√|D|/2a) i"
                listOf(Complex(re, im).toValue(), Complex(re, -im).toValue())
            }
        }
        return QuadraticResult(roots, disc, steps)
    }

    /** { a1·x + b1·y = c1 ; a2·x + b2·y = c2 } via Cramer's rule. */
    fun system2x2(
        a1: Num, b1: Num, c1: Num,
        a2: Num, b2: Num, c2: Num,
        settings: Settings,
    ): SystemResult {
        val det = a1 * b2 - a2 * b1
        if (det.isZero) throw ArithFault.Domain("system has no unique solution (determinant = 0)", "system")
        val x = (c1 * b2 - c2 * b1) / det
        val y = (a1 * c2 - a2 * c1) / det
        return SystemResult(
            listOf(x, y),
            det,
            listOf(
                "det = a1·b2 − a2·b1 = ${f(det, settings)}",
                "x = ${f(x, settings)}, y = ${f(y, settings)}",
            ),
        )
    }

    /** A 3×3 linear system via Cramer's rule. Rows: aᵢ·x + bᵢ·y + cᵢ·z = dᵢ. */
    fun system3x3(
        a1: Num, b1: Num, c1: Num, d1: Num,
        a2: Num, b2: Num, c2: Num, d2: Num,
        a3: Num, b3: Num, c3: Num, d3: Num,
        settings: Settings,
    ): SystemResult {
        val det = det3(a1, b1, c1, a2, b2, c2, a3, b3, c3)
        if (det.isZero) throw ArithFault.Domain("system has no unique solution (determinant = 0)", "system")
        val dx = det3(d1, b1, c1, d2, b2, c2, d3, b3, c3)
        val dy = det3(a1, d1, c1, a2, d2, c2, a3, d3, c3)
        val dz = det3(a1, b1, d1, a2, b2, d2, a3, b3, d3)
        val x = dx / det
        val y = dy / det
        val z = dz / det
        return SystemResult(
            listOf(x, y, z),
            det,
            listOf(
                "det = ${f(det, settings)}",
                "x = ${f(x, settings)}, y = ${f(y, settings)}, z = ${f(z, settings)}",
            ),
        )
    }

    private fun det3(
        a1: Num, b1: Num, c1: Num,
        a2: Num, b2: Num, c2: Num,
        a3: Num, b3: Num, c3: Num,
    ): Num =
        a1 * (b2 * c3 - b3 * c2) - b1 * (a2 * c3 - a3 * c2) + c1 * (a2 * b3 - a3 * b2)

    private fun f(n: Num, settings: Settings): String = Formatter.exact(n, settings)
}
