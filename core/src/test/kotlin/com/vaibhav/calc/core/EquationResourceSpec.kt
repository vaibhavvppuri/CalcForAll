package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class EquationResourceSpec : StringSpec({
    fun refusal(source: String) {
        (source.length <= 8192) shouldBe true // accepted by the app's text-size gate
        val error = Calculator().route(source).shouldBeInstanceOf<RouteResult.Err>().error
        error.kind shouldBe EvalError.Kind.UNSUPPORTED
        error.message shouldContain "equation/system analysis"
    }

    "a flat four-thousand-term symbolic equation is refused before recursive extraction" {
        refusal(List(4000) { "x" }.joinToString("+") + "=0")
    }

    "a deep flat side of a small system is refused before unknown collection" {
        refusal(List(4000) { "x" }.joinToString("+") + "+y=1\nx-y=0")
    }

    "ordinary long numeric sums retain the iterative scalar path" {
        for (count in listOf(1800,4000)) {
            val outcome = Calculator().route(List(count) { "1" }.joinToString("+"))
                .shouldBeInstanceOf<RouteResult.Ok>().outcome.shouldBeInstanceOf<Outcome.Scalar>()
            outcome.value shouldBe RealValue(Num.of(count))
        }
    }

    "ordinary quadratic and linear-system solutions remain supported" {
        val quadratic = Calculator().route("x^2-5x+6=0").shouldBeInstanceOf<RouteResult.Ok>()
            .outcome.shouldBeInstanceOf<Outcome.Roots>()
        quadratic.roots shouldBe listOf(RealValue(Num.TWO),RealValue(Num.of(3)))
        val system = Calculator().route("x+y=3\nx-y=1").shouldBeInstanceOf<RouteResult.Ok>()
            .outcome.shouldBeInstanceOf<Outcome.SystemSolution>()
        system.unknowns shouldBe listOf("x","y")
        system.values shouldBe listOf(Num.TWO,Num.ONE)
    }
})
