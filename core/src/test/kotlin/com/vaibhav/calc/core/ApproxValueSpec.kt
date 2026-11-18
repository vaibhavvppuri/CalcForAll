package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.format.OutcomeFormatter
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.route.Outcome
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class ApproxValueSpec : StringSpec({
    fun real(source: String, settings: Settings = Settings()): RealValue =
        Calculator(settings).evaluate(source).getOrThrow().shouldBeInstanceOf<RealValue>()

    "approx preserves an explicit estimate marker even for a terminating number or zero" {
        for (input in listOf("0", "1", "1.2345678901234567890123456789")) {
            val value = real("approx($input)")
            value.num.shouldBeInstanceOf<Dec>().value.compareTo(BigDecimal(input)) shouldBe 0
            OutcomeFormatter.render(Outcome.Scalar(value), "approx($input)").approximate shouldBe true
        }
    }
    "ordinary literals remain exact and approx is an explicit opt in" {
        real("1.25").num shouldBe Num.of(5, 4)
        real("approx(1.25)").num.shouldBeInstanceOf<Dec>()
    }
    "nonterminating input is rounded to the requested compute precision" {
        val value = real("approx(1/3)", Settings(computePrecision = 16)).num.shouldBeInstanceOf<Dec>()
        value.value shouldBe BigDecimal("0.3333333333333333")
    }
    "ordinary arithmetic with an approximate value remains approximate" {
        val value = real("approx(1.25)+2").num.shouldBeInstanceOf<Dec>()
        value.value.compareTo(BigDecimal("3.25")) shouldBe 0
    }
    "approx accepts an explicit absolute error and rejects undeclared arities" {
        val bounded = real("approx(1,2)").num.shouldBeInstanceOf<Dec>()
        bounded.value.compareTo(BigDecimal.ONE) shouldBe 0
        bounded.absoluteError!!.compareTo(BigDecimal(2)) shouldBe 0
        for (source in listOf("approx()", "approx(1,2,3)")) {
            Calculator().evaluate(source).shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.ARITY
        }
    }
    "approx is real and dimensionless rather than silently discarding units or imaginary parts" {
        for (source in listOf("approx(2 m)", "approx(cmplx(1,2))")) {
            Calculator(Settings(complexEnabled = true)).evaluate(source)
                .shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.DOMAIN
        }
    }
})
