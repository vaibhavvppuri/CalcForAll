package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.stats.Stats
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class RegressionStabilitySpec : StringSpec({
    "a constant response has a valid fitted line but undefined correlation" {
        val result = Stats.linearRegression(listOf(1L,2L,3L,4L).map { Num.of(it) to Num.of(5) }, Num.MC)
        result.slope shouldBe Num.ZERO
        result.intercept shouldBe Num.of(5)
        result.r shouldBe null
        result.r2 shouldBe null
    }

    "a constant predictor still returns a typed domain error" {
        Calculator().linearRegression(listOf(Num.ONE to Num.ONE, Num.ONE to Num.TWO))
            .shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.DOMAIN
    }

    "centering preserves the variation in large-offset decimal coordinates" {
        val offset = BigDecimal("10000000000000000000000000")
        val points = (0..2).map { i -> Dec(offset.add(BigDecimal(i)), BigDecimal.ZERO) to Num.of((2 * i + 1).toLong()) }
        val result = Stats.linearRegression(points, Num.MC)
        result.slope.compareTo(Num.TWO) shouldBe 0
        result.intercept.compareTo(Num.ofDecimal("-19999999999999999999999999")) shouldBe 0
        result.r!!.compareTo(Num.ONE) shouldBe 0
        result.r2!!.compareTo(Num.ONE) shouldBe 0
    }

    "negative perfect association stays signed while r squared remains one" {
        val result = Stats.linearRegression(listOf(1L,2L,3L,4L).map { Num.of(it) to Num.of(10 - 3 * it) }, Num.MC)
        result.slope shouldBe Num.of(-3)
        result.intercept shouldBe Num.of(10)
        result.r!!.compareTo(Num.of(-1)) shouldBe 0
        result.r2 shouldBe Num.ONE
    }

    "uncorrelated varying data has defined zero correlation" {
        val result = Stats.linearRegression(listOf(Num.of(-1) to Num.ONE, Num.ZERO to Num.ZERO, Num.ONE to Num.ONE), Num.MC)
        result.r shouldBe Num.ZERO
        result.r2 shouldBe Num.ZERO
        result.intercept shouldBe Num.of(2, 3)
    }
})
