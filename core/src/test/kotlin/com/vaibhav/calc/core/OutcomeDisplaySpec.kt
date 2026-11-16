package com.vaibhav.calc.core

import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.format.*
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.error.CalcResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class OutcomeDisplaySpec : StringSpec({
    "finite precision fallback is identified separately from an exact fraction" {
        val calc = Calculator()
        val sine = calc.evaluate("sin(1)").getOrThrow()
        OutcomeFormatter.render(Outcome.Scalar(sine), "sin(1)").approximate shouldBe true
        val exact = calc.evaluate("0.1+0.2").getOrThrow()
        OutcomeFormatter.render(Outcome.Scalar(exact), "0.1+0.2").approximate shouldBe false
    }
    "a decimal view is approximate only if it rounds the exact rational" {
        val decimal = Settings(exactForm = ExactForm.DECIMAL)
        OutcomeFormatter.isApproximate(RealValue(Num.of(1, 3)), decimal) shouldBe true
        OutcomeFormatter.isApproximate(RealValue(Num.of(1, 2)), decimal) shouldBe false
        OutcomeFormatter.isApproximate(RealValue(Num.of(1, 3)), Settings()) shouldBe false
    }
    "long flat expressions format through an iterative notation scan" {
        val expression = List(1800) { "1" }.joinToString("+")
        val result = Calculator().evaluateAnswer(expression) as CalcResult.Ok
        result.value.exact shouldBe "1800"
        Formatter.preferredForm(expression + "+1/2") shouldBe ExactForm.FRACTION
    }
})
