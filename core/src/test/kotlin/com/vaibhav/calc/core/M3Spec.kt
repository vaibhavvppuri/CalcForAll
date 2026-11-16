package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.matrix.Matrix
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** M3 — D7 (matrices) + D8 (statistics, regression). */
class M3Spec : StringSpec({

    val c = Calculator()
    fun m(vararg rows: List<Long>) = Matrix.of(rows.map { row -> row.map { Num.of(it) } })
    fun ok(r: CalcResult<Matrix>) = (r as CalcResult.Ok).value

    // --- matrices (spec 7.1/7.2/7.3) ----------------------------------------
    "determinant 2x2 and 3x3" {
        (c.matrixDeterminant(m(listOf(1, 2), listOf(3, 4))) as CalcResult.Ok).value.compareTo(Num.of(-2)) shouldBe 0
        (c.matrixDeterminant(Matrix.identity(3)) as CalcResult.Ok).value.compareTo(Num.of(1)) shouldBe 0
        (c.matrixDeterminant(m(listOf(2, 0, 0), listOf(0, 3, 0), listOf(0, 0, 4))) as CalcResult.Ok)
            .value.compareTo(Num.of(24)) shouldBe 0
    }
    "matrix multiply" {
        ok(c.matrixMultiply(m(listOf(1, 2), listOf(3, 4)), m(listOf(5, 6), listOf(7, 8)))) shouldBe
            m(listOf(19, 22), listOf(43, 50))
    }
    "matrix add / transpose / scalar" {
        ok(c.matrixAdd(m(listOf(1, 2)), m(listOf(3, 4)))) shouldBe m(listOf(4, 6))
        m(listOf(1, 2, 3)).transpose() shouldBe m(listOf(1), listOf(2), listOf(3))
        m(listOf(1, 2)).scalarTimes(Num.of(3)) shouldBe m(listOf(3, 6))
    }
    "inverse: A · A⁻¹ = I (exact)" {
        val a = m(listOf(1, 2), listOf(3, 4))
        val inv = ok(c.matrixInverse(a))
        ok(c.matrixMultiply(a, inv)) shouldBe Matrix.identity(2)
    }
    "rank" {
        m(listOf(1, 2), listOf(3, 4)).rank() shouldBe 2
        m(listOf(1, 2), listOf(2, 4)).rank() shouldBe 1
        m(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).rank() shouldBe 2
    }
    "singular inverse is a DOMAIN error" {
        c.matrixInverse(m(listOf(1, 2), listOf(2, 4))).errorOrNull()?.kind shouldBe EvalError.Kind.DOMAIN
    }
    "non-square determinant is a DOMAIN error" {
        c.matrixDeterminant(m(listOf(1, 2, 3), listOf(4, 5, 6))).errorOrNull()?.kind shouldBe EvalError.Kind.DOMAIN
    }
    "dimension-mismatch multiply is a DOMAIN error" {
        c.matrixMultiply(m(listOf(1, 2)), m(listOf(1, 2))).errorOrNull()?.kind shouldBe EvalError.Kind.DOMAIN
    }

    // --- descriptive statistics (spec 8.2) ----------------------------------
    "descriptive stats of 2,4,4,4,5,5,7,9" {
        val d = (c.statsDescribe(listOf(2L, 4, 4, 4, 5, 5, 7, 9).map { Num.of(it) }) as CalcResult.Ok).value
        d.count shouldBe 8
        d.mean.compareTo(Num.of(5)) shouldBe 0
        d.median.compareTo(Num.of(9, 2)) shouldBe 0
        d.range.compareTo(Num.of(7)) shouldBe 0
        d.variancePopulation.compareTo(Num.of(4)) shouldBe 0
        d.stdevPopulation.compareTo(Num.of(2)) shouldBe 0
        d.varianceSample!!.compareTo(Num.of(32, 7)) shouldBe 0
    }
    "empty data is a DOMAIN error" {
        c.statsDescribe(emptyList()).errorOrNull()?.kind shouldBe EvalError.Kind.DOMAIN
    }

    // --- regression (spec 8.4) ----------------------------------------------
    "perfect fit y = 2x -> slope 2, intercept 0, r = 1" {
        val r = (c.linearRegression(listOf(1L to 2L, 2L to 4L, 3L to 6L).map { Num.of(it.first) to Num.of(it.second) }) as CalcResult.Ok).value
        r.slope.compareTo(Num.of(2)) shouldBe 0
        r.intercept.compareTo(Num.of(0)) shouldBe 0
        r.r!!.compareTo(Num.of(1)) shouldBe 0
        r.r2!!.compareTo(Num.of(1)) shouldBe 0
    }
    "y = 2x + 1 -> slope 2, intercept 1" {
        val r = (c.linearRegression(listOf(0L to 1L, 1L to 3L, 2L to 5L).map { Num.of(it.first) to Num.of(it.second) }) as CalcResult.Ok).value
        r.slope.compareTo(Num.of(2)) shouldBe 0
        r.intercept.compareTo(Num.of(1)) shouldBe 0
    }
    "regression needs at least two points" {
        c.linearRegression(listOf(Num.of(1) to Num.of(2))).errorOrNull()?.kind shouldBe EvalError.Kind.DOMAIN
    }
})
