package com.vaibhav.calc.core.matrix

import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.eval.Value
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.unaryMinus
import com.vaibhav.calc.core.solve.Poly
import com.vaibhav.calc.core.solve.PolynomialRoots
import com.vaibhav.calc.core.solve.Solvers

/**
 * Matrix eigenvalues (CalcForAll 1.2): the roots of the characteristic polynomial
 * `det(λI − A)`, whose coefficients are computed exactly by the Faddeev–LeVerrier
 * recurrence and then handed to the same equation solvers the calculator uses for a
 * typed equation. So rational eigenvalues are exact, a 2 × 2 matrix keeps its surds
 * and complex pair, and anything numeric is marked approximate. Repeated eigenvalues
 * are listed once. Eigenvectors are not computed.
 */
object Eigen {
    const val MAX_SIZE = PolynomialRoots.MAX_DEGREE

    data class Result(val values: List<Value>, val polynomial: List<Num>)

    /** Coefficients of `det(λI − A)`, lowest power first; the leading coefficient is 1. */
    fun characteristicPolynomial(a: Matrix): List<Num> {
        if (!a.isSquare) throw ArithFault.Domain("eigenvalues need a square matrix", "eigen")
        val n = a.rows
        if (n > MAX_SIZE) throw ArithFault.Unsupported("eigenvalues are supported up to $MAX_SIZE × $MAX_SIZE matrices")
        val coefficients = MutableList<Num>(n + 1) { Num.ZERO }
        coefficients[n] = Num.ONE
        val identity = Matrix.identity(n)
        var m = identity
        for (k in 1..n) {
            if (k > 1) m = a * m + identity.scalarTimes(coefficients[n - k + 1])
            val product = a * m
            var trace: Num = Num.ZERO
            for (i in 0 until n) trace += product.get(i, i)
            coefficients[n - k] = -(trace / Num.of(k))
        }
        return coefficients
    }

    fun eigenvalues(a: Matrix, settings: Settings): Result {
        val c = characteristicPolynomial(a)
        val values: List<Value> = when (a.rows) {
            1 -> listOf(RealValue(-c[0]))
            2 -> Solvers.quadratic(c[2], c[1], c[0], settings).roots
            else -> PolynomialRoots.solve(Poly(c), settings).roots
        }
        return Result(values, c)
    }
}
