package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.symbolic.Limits
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class LimitScaleSpec : StringSpec({
    fun limit(expression: String) = Limits.at(expression, "x", Num.ZERO, Settings()).getOrThrow()
    fun nominal(side: Limits.Side): BigDecimal = side.shouldBeInstanceOf<Limits.Side.Settles>().value
        .shouldBeInstanceOf<Dec>().also { it.absoluteError.shouldBeNull() }.value

    "a tiny nonzero constant remains nonzero at finite and infinite limits" {
        val finite = limit("0.00000000000000000001")
        nominal(checkNotNull(finite.twoSided)).compareTo(BigDecimal("1e-20")) shouldBe 0
        val infinite = Limits.atInfinity("0.00000000000000000001", "x", true, Settings()).getOrThrow()
        nominal(infinite.right).compareTo(BigDecimal("1e-20")) shouldBe 0
    }

    "a tiny two-sided jump is not erased by a unit-scale tolerance" {
        val jump = limit("0.00000001+0.000000001*abs(x)/x")
        nominal(jump.left).compareTo(BigDecimal("9e-9")) shouldBe 0
        nominal(jump.right).compareTo(BigDecimal("1.1e-8")) shouldBe 0
        jump.twoSided.shouldBeNull()
    }

    "two sides must agree at the significant digits they actually report" {
        val jump = limit("1+0.00000001*abs(x)/x")
        nominal(jump.left).compareTo(BigDecimal("0.99999999")) shouldBe 0
        nominal(jump.right).compareTo(BigDecimal("1.00000001")) shouldBe 0
        jump.twoSided.shouldBeNull()
    }

    "supported continuous zero approaches remain approximate zero estimates" {
        val zero = checkNotNull(limit("x*cos(x)").twoSided)
        nominal(zero).compareTo(BigDecimal.ZERO) shouldBe 0
        zero.value.isZero shouldBe false // Sampling output does not become an exact algebraic value.
    }

    "a proper exact rational function has a supported zero limit at infinity" {
        for (positive in listOf(true, false)) {
            val estimate = Limits.atInfinity("1/x", "x", positive, Settings()).getOrThrow()
            nominal(estimate.right).compareTo(BigDecimal.ZERO) shouldBe 0
        }
    }

    "an unresolved tiny offset is never replaced by a zero limit" {
        val finite = limit("0.00000000000000000001+x")
        finite.twoSided.shouldBeNull()
        val infinite = Limits.atInfinity("0.00000000000000000001+1/x", "x", true, Settings()).getOrThrow()
        infinite.right shouldBe Limits.Side.Unsettled
    }

    "unknown evaluation error cannot masquerade as ten agreeing digits" {
        val uncertain = limit("approx(1)")
        uncertain.left shouldBe Limits.Side.Unevaluated
        uncertain.right shouldBe Limits.Side.Unevaluated
        uncertain.twoSided.shouldBeNull()
    }

    "uncertain limit points and oversized expressions return typed refusals" {
        val uncertain = Limits.at("x", "x", Dec(BigDecimal.ZERO, BigDecimal.ONE), Settings())
        uncertain.shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.UNSUPPORTED
        val deep = Limits.at("x" + "+1".repeat(80), "x", Num.ZERO, Settings())
        deep.shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.UNSUPPORTED
    }
})
