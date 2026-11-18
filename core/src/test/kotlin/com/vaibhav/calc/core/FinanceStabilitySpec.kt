package com.vaibhav.calc.core

import com.vaibhav.calc.core.everyday.Finance
import com.vaibhav.calc.core.num.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class FinanceStabilitySpec : StringSpec({
    val mc = MathContext(40, RoundingMode.HALF_EVEN)
    // The independent textbook formula loses about 52 digits forming growth-1,
    // then another 50 when isolating interest from total payments.
    val reference = MathContext(220, RoundingMode.HALF_EVEN)
    val tinyRate = Num.ofDecimal("1e-50")
    val i = BigDecimal("1e-50").divide(BigDecimal(1200), reference)
    val factor = BigDecimal.ONE.add(i, reference).pow(12, reference)
    val gain = factor.subtract(BigDecimal.ONE)
    fun close(actual: Num, expected: BigDecimal) {
        val difference = actual.toBigDecimal(reference).subtract(expected).abs()
        val tolerance = expected.abs().multiply(BigDecimal("1e-37")).max(BigDecimal("1e-110"))
        (difference <= tolerance) shouldBe true
    }

    "a tiny positive loan rate does not become division by zero or zero interest" {
        val loan = Finance.loan(Num.of(1200), tinyRate, 12, mc)
        val payment = BigDecimal(1200).multiply(i, reference).multiply(factor, reference).divide(gain, reference)
        close(loan.payment, payment)
        close(loan.totalInterest, payment.multiply(BigDecimal(12), reference).subtract(BigDecimal(1200)))
        (loan.totalInterest.toBigDecimal(mc).signum() > 0) shouldBe true
    }

    "a tiny nonzero monthly investment return preserves principal and gain separately" {
        val result = Finance.monthlyInvestment(Num.of(100), tinyRate, 12, mc)
        val future = BigDecimal(100).multiply(gain, reference).divide(i, reference)
            .multiply(BigDecimal.ONE.add(i, reference), reference)
        close(result.futureValue, future)
        result.invested shouldBe Num.of(1200)
        close(result.gain, future.subtract(BigDecimal(1200)))
        (result.gain.toBigDecimal(mc).signum() > 0) shouldBe true
    }

    "affordability and savings goals remain finite at tiny positive rates" {
        val affordable = Finance.affordableLoan(Num.of(100), tinyRate, 12, mc)
        val principal = BigDecimal(100).multiply(gain, reference).divide(i.multiply(factor, reference), reference)
        close(affordable.principal, principal)
        close(affordable.totalInterest, BigDecimal(1200).subtract(principal))
        val goal = Finance.savingsGoal(Num.of(1200), tinyRate, 12, mc)
        val monthly = BigDecimal(1200).multiply(i, reference)
            .divide(gain.multiply(BigDecimal.ONE.add(i, reference), reference), reference)
        close(goal.monthly, monthly)
        close(goal.gain, BigDecimal(1200).subtract(monthly.multiply(BigDecimal(12), reference)))
        (goal.gain.toBigDecimal(mc).signum() > 0) shouldBe true
    }

    "whole-period compound interest retains a tiny positive gain" {
        val result = Finance.compound(Num.of(1000), tinyRate, Num.of(12), 1, mc)
        val expectedGain = BigDecimal(1000).multiply(BigDecimal.ONE.add(BigDecimal("1e-52"), reference)
            .pow(12, reference).subtract(BigDecimal.ONE), reference)
        close(result.interest, expectedGain)
        (result.interest.toBigDecimal(mc).signum() > 0) shouldBe true
    }

    "ordinary growth remains accurate at the maximum supported period count" {
        val result = Finance.monthlyInvestment(Num.of(10), Num.of(1), Finance.MAX_PERIODS, mc)
        val rate = BigDecimal.ONE.divide(BigDecimal(1200), reference)
        val growth = BigDecimal.ONE.add(rate, reference).pow(Finance.MAX_PERIODS, reference)
        val future = BigDecimal.TEN.multiply(growth.subtract(BigDecimal.ONE), reference)
            .divide(rate, reference).multiply(BigDecimal.ONE.add(rate, reference), reference)
        close(result.futureValue, future)
    }

    "unresolvable fractional-period growth refuses instead of reporting no interest" {
        shouldThrow<ArithFault.Unsupported> { Finance.compound(Num.of(1000), tinyRate, Num.HALF, 1, mc) }
        Finance.loan(Num.of(1200), Num.ZERO, 12, mc).payment shouldBe Num.of(100)
        Finance.monthlyInvestment(Num.of(100), Num.ZERO, 12, mc).gain shouldBe Num.ZERO
        Finance.savingsGoal(Num.of(1200), Num.ZERO, 12, mc).monthly shouldBe Num.of(100)
    }
})
