package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.graph.PlottableFunction
import com.vaibhav.calc.core.graph.Sample
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class GraphBindingSpec : StringSpec({
    "an extra equation unknown is an unsupported curve definition rather than a mathematical gap" {
        for (input in listOf("x+y", "z", "X+Y")) {
            Calculator().plot(input).shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.UNSUPPORTED
        }
    }

    "the existing selected-variable API still supports y and z case insensitively" {
        val y = PlottableFunction.of("y^2",Settings(),Environment(),"Y").getOrThrow()
        y.at(Num.of(3)).shouldBeInstanceOf<Sample.Defined>().y shouldBe Num.of(9)
        val z = PlottableFunction.of("Z+1",Settings(),Environment(),"z").getOrThrow()
        z.at(Num.of(3)).shouldBeInstanceOf<Sample.Defined>().y shouldBe Num.of(4)
    }

    "missing ordinary memory is still an unevaluated point with a typed cause" {
        val curve = Calculator().plot("A+x").getOrThrow()
        curve.at(Num.ONE).shouldBeInstanceOf<Sample.Unevaluated>().error.kind shouldBe EvalError.Kind.UNKNOWN_SYMBOL
    }

    "the variable gate walks long flat ASTs iteratively" {
        val input = List(1800) { "x" }.joinToString("+") + "+y"
        Calculator().plot(input).shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.UNSUPPORTED
    }
})
