package com.vaibhav.calc.core

import com.vaibhav.calc.core.eval.ComplexValue
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.everyday.Finance
import com.vaibhav.calc.core.matrix.Eigen
import com.vaibhav.calc.core.matrix.Matrix
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.abs
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.unaryMinus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.math.MathContext
import java.math.RoundingMode

/** CalcForAll 1.2: matrix eigenvalues through the equation solvers, and the loan repayment schedule. */
class EigenScheduleSpec : StringSpec({
    val settings = Settings()
    val mc = MathContext(40, RoundingMode.HALF_EVEN)
    fun matrix(vararg rows: List<Int>): Matrix = Matrix.of(rows.map { row -> row.map { Num.of(it) as Num } })
    fun Num.d(): Double = toBigDecimal(MathContext(25)).toDouble()
    fun reals(m: Matrix): List<Double> = Eigen.eigenvalues(m, settings).values.map { (it as RealValue).num.d() }

    "the characteristic polynomial of a 2 × 2 matrix, and its exact eigenvalues" {
        val m = matrix(listOf(2, 1), listOf(1, 2))
        Eigen.characteristicPolynomial(m) shouldBe listOf(Num.of(3), Num.of(-4), Num.of(1))
        Eigen.eigenvalues(m, settings).values shouldBe listOf(RealValue(Num.of(1)), RealValue(Num.of(3)))
    }

    "the constant term is (−1)ⁿ·det(A) and the next-to-leading one is −trace(A)" {
        val m = matrix(listOf(2, -1, 0, 3), listOf(1, 4, -2, 0), listOf(0, 5, 1, 1), listOf(3, 0, 2, -1))
        val c = Eigen.characteristicPolynomial(m)
        c.size shouldBe 5
        c[4] shouldBe Num.ONE
        c[0] shouldBe m.determinant() // n = 4, so the sign is +
        c[3] shouldBe Num.of(-(2 + 4 + 1 - 1))
        val odd = matrix(listOf(2, 0, 1), listOf(1, 3, 0), listOf(0, 1, 4))
        Eigen.characteristicPolynomial(odd)[0] shouldBe -odd.determinant()
    }

    "a diagonal matrix returns its diagonal; a repeated eigenvalue is listed once" {
        Eigen.eigenvalues(matrix(listOf(1, 0, 0), listOf(0, 2, 0), listOf(0, 0, 3)), settings).values shouldBe
            listOf(1, 2, 3).map { RealValue(Num.of(it)) }
        Eigen.eigenvalues(matrix(listOf(1, 0, 0), listOf(0, 1, 0), listOf(0, 0, 1)), settings).values shouldBe
            listOf(RealValue(Num.of(1)))
        Eigen.eigenvalues(matrix(listOf(7)), settings).values shouldBe listOf(RealValue(Num.of(7)))
    }

    "a symmetric 3 × 3 matrix with rational eigenvalues is exact" {
        Eigen.eigenvalues(matrix(listOf(2, 0, 0), listOf(0, 3, 4), listOf(0, 4, 9)), settings).values shouldBe
            listOf(1, 2, 11).map { RealValue(Num.of(it)) }
    }

    "a rotation has the exact complex pair ±i" {
        val values = Eigen.eigenvalues(matrix(listOf(0, -1), listOf(1, 0)), settings).values
        values.size shouldBe 2
        val first = (values[0] as ComplexValue).cx
        first.re shouldBe Num.ZERO
        first.im.abs() shouldBe Num.ONE
        ((values[1] as ComplexValue).cx.im + first.im).isZero shouldBe true
    }

    "irrational eigenvalues of a 4 × 4 tridiagonal matrix: 2 ± φ and 2 ± (φ − 1)" {
        val m = matrix(listOf(2, 1, 0, 0), listOf(1, 2, 1, 0), listOf(0, 1, 2, 1), listOf(0, 0, 1, 2))
        Eigen.characteristicPolynomial(m) shouldBe listOf(5, -20, 21, -8, 1).map { Num.of(it) }
        val values = reals(m)
        values.size shouldBe 4
        values[0] shouldBe (0.3819660112501051 plusOrMinus 1e-10)
        values[1] shouldBe (1.381966011250105 plusOrMinus 1e-10)
        values[2] shouldBe (2.618033988749895 plusOrMinus 1e-10)
        values[3] shouldBe (3.618033988749895 plusOrMinus 1e-10)
    }

    "eigenvalues need a square matrix" {
        shouldThrow<ArithFault.Domain> { Eigen.characteristicPolynomial(matrix(listOf(1, 2, 3), listOf(4, 5, 6))) }
    }

    // ---- loan schedule -----------------------------------------------------

    "a one-year schedule: the interest matches the EMI total and the loan is fully repaid" {
        val rows = Finance.schedule(Num.of(100_000), Num.of(12), 12, mc)
        rows.size shouldBe 1
        rows[0].year shouldBe 1
        rows[0].interest.toBigDecimal(mc).setScale(2, RoundingMode.HALF_UP).toPlainString() shouldBe "6618.55"
        ((rows[0].principal - Num.of(100_000)).abs() < Num.of(1, 1_000_000)) shouldBe true
        rows[0].balance.isZero shouldBe true
    }

    "a longer schedule: one row per year, a falling balance, and principal that adds up to the loan" {
        val loan = Finance.loan(Num.of(1_000_000), Num.of(17, 2), 240, mc)
        val rows = Finance.schedule(Num.of(1_000_000), Num.of(17, 2), 240, mc)
        rows.map { it.year } shouldBe (1..20).toList()
        rows.zipWithNext().all { (a, b) -> b.balance < a.balance } shouldBe true
        rows.last().balance.isZero shouldBe true
        val repaid = rows.fold(Num.ZERO as Num) { acc, row -> acc + row.principal }
        ((repaid - Num.of(1_000_000)).abs() < Num.of(1, 1_000_000)) shouldBe true
        val interest = rows.fold(Num.ZERO as Num) { acc, row -> acc + row.interest }
        ((interest - loan.totalInterest).abs() < Num.of(1, 1000)) shouldBe true
        // interest falls and repayment rises as the loan ages
        (rows.first().interest > rows.last().interest) shouldBe true
        (rows.first().principal < rows.last().principal) shouldBe true
    }

    "a part-year tail gets its own row, a zero rate stays exact, and the length is bounded" {
        Finance.schedule(Num.of(50_000), Num.of(10), 18, mc).map { it.year } shouldBe listOf(1, 2)
        val free = Finance.schedule(Num.of(1200), Num.ZERO, 12, mc)
        free.single().principal shouldBe Num.of(1200)
        free.single().interest shouldBe Num.ZERO
        shouldThrow<ArithFault.Domain> { Finance.schedule(Num.of(1000), Num.of(5), 601, mc) }
    }
})
