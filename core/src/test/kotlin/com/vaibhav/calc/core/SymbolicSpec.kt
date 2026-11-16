package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.eval.Evaluator
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.parse.Parser
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.symbolic.Symbolic
import com.vaibhav.calc.core.symbolic.SymbolicAnswer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class SymbolicSpec : StringSpec({
    val settings = Settings(angle = AngleMode.RAD, computePrecision = 48, displayPrecision = 30)
    fun evalResult(expression: String, x: String = "2", y: String = "3") = Evaluator(settings, Environment(),
        mapOf("x" to Num.ofDecimal(x), "y" to Num.ofDecimal(y))).eval(Parser.parse(expression).getOrThrow())
    fun value(expression: String, x: String = "2", y: String = "3"): Num =
        (evalResult(expression, x, y).getOrThrow() as RealValue).num
    fun close(actual: Num, expected: Num, tolerance: String = "1e-26") {
        (actual.toBigDecimal(settings.computeMc).subtract(expected.toBigDecimal(settings.computeMc)).abs() < BigDecimal(tolerance)) shouldBe true
    }
    fun answer(result: CalcResult<SymbolicAnswer>) = result.shouldBeInstanceOf<CalcResult.Ok<SymbolicAnswer>>().value

    for ((source, expected) in listOf(
        "2*x+3*x-4" to "5*x-4", "(x+2)*(x-2)" to "x^2-4",
        "(x+y)^3" to "x^3+3*x^2*y+3*x*y^2+y^3", "x+10%" to "11*x/10",
        "x-10%" to "9*x/10", "3!*x" to "6*x", "0*x^2+1*x" to "x",
        "(x/3+x/6)*2" to "x", "sq(x)+pow(x,2)" to "2*x^2",
    )) {
        "simplifies $source with exact coefficients" {
            val result = answer(Symbolic.simplify(source))
            for (x in listOf("-3", "0", "0.25", "7")) close(value(result.expression, x), value(expected, x))
        }
    }

    "expansion produces an equivalent polynomial and rejects non-polynomials" {
        val expanded = answer(Symbolic.expand("(x+2)^4"))
        expanded.expression shouldBe "x^4 + 8*x^3 + 24*x^2 + 32*x + 16"
        Symbolic.expand("sin(x)").shouldBeInstanceOf<CalcResult.Err>()
    }
    for (input in listOf("x^2-5*x+6", "2*x^2-5*x+2", "x^4-16", "x^3", "x^2+1", "x/2-3/4", "0")) {
        "factorisation preserves $input exactly" {
            val result = answer(Symbolic.factor(input))
            for (x in listOf("-4", "0", "0.5", "3")) close(value(result.expression, x), value(input, x))
        }
    }
    "factorisation exposes confirmed rational factors" {
        val factored = answer(Symbolic.factor("x^2-5*x+6")).expression
        factored shouldContain "x - 2"
        factored shouldContain "x - 3"
    }

    for ((source, expected, x) in listOf(
        Triple("x^5-3*x^2+7", "5*x^4-6*x", "2"),
        Triple("(3*x+1)^4", "12*(3*x+1)^3", "0.5"),
        Triple("sin(x^2)", "2*x*cos(x^2)", "0.7"),
        Triple("cos(3*x)", "-3*sin(3*x)", "0.2"),
        Triple("tan(x)", "1/cos(x)^2", "0.4"),
        Triple("exp(x)*sin(x)", "exp(x)*(sin(x)+cos(x))", "0.6"),
        Triple("ln(x)", "1/x", "2"),
        Triple("log(2,x)", "1/(x*ln(2))", "3"),
        Triple("sqrt(x)", "1/(2*sqrt(x))", "3"),
        Triple("cbrt(x)", "1/(3*cbrt(x)^2)", "-8"),
        Triple("x/(x+1)", "1/(x+1)^2", "2"),
        Triple("x^x", "x^x*(ln(x)+1)", "2"),
        Triple("2^x", "2^x*ln(2)", "3"),
        Triple("e^x", "e^x", "0.5"),
        Triple("asin(x)", "1/sqrt(1-x^2)", "0.2"),
        Triple("acos(x)", "-1/sqrt(1-x^2)", "0.2"),
        Triple("atan(x)", "1/(1+x^2)", "0.2"),
        Triple("sinh(x)", "cosh(x)", "0.2"),
        Triple("cosh(x)", "sinh(x)", "0.2"),
        Triple("tanh(x)", "1/cosh(x)^2", "0.2"),
        Triple("asinh(x)", "1/sqrt(x^2+1)", "0.2"),
        Triple("acosh(x)", "1/(sqrt(x-1)*sqrt(x+1))", "2"),
        Triple("atanh(x)", "1/(1-x^2)", "0.2"),
        Triple("abs(x)", "-1", "-2"),
        Triple("pi*x", "pi", "2"),
        Triple("x*y+y^2", "y", "2"),
        Triple("x^0", "0", "0"),
    )) {
        "differentiates $source" {
            val result = answer(Symbolic.differentiate(source))
            result.angleMode shouldBe "RAD"
            close(value(result.expression, x), value(expected, x))
        }
    }
    "derivative also agrees with an independent centered finite difference" {
        val src = "sin(x^2)*exp(x)/(x+2)"
        val derivative = answer(Symbolic.differentiate(src))
        val h = Num.ofDecimal("1e-6")
        val difference = (value(src, "0.700001") - value(src, "0.699999")) / (Num.TWO * h)
        close(value(derivative.expression, "0.7"), difference, "1e-10")
    }
    "selected variable and other symbols are distinguished" {
        val result = answer(Symbolic.differentiate("x*y^3", "y"))
        close(value(result.expression), value("3*x*y^2"))
    }

    for ((source, expected, a, b) in listOf(
        listOf("x^3-2*x+1", "x^4/4-x^2+x", "-2", "3"),
        listOf("sin(2*x+1)", "-cos(2*x+1)/2", "0", "1"),
        listOf("cos(3*x)", "sin(3*x)/3", "0", "1"),
        listOf("exp(2*x)", "exp(2*x)/2", "0", "1"),
        listOf("1/(3*x+1)", "ln(abs(3*x+1))/3", "0", "1"),
        listOf("1/(1+x^2)", "atan(x)", "0", "1"),
        listOf("x^-2", "-1/x", "1", "2"),
        listOf("sqrt(x)", "2*x^(3/2)/3", "1", "4"),
        listOf("cbrt(x)", "3*x^(4/3)/4", "-8", "-1"),
        listOf("abs(x)", "x*abs(x)/2", "-3", "2"),
        listOf("ln(x)", "x*ln(x)-x", "1", "2"),
        listOf("2^x", "2^x/ln(2)", "0", "2"),
        listOf("x*exp(x)", "exp(x)*(x-1)", "0", "1"),
        listOf("x*sin(x)", "sin(x)-x*cos(x)", "0", "1"),
        listOf("x^2*cos(x)", "x^2*sin(x)+2*x*cos(x)-2*sin(x)", "0", "1"),
    )) {
        "finds an antiderivative for $source" {
            val result = answer(Symbolic.integrate(source))
            result.constantOfIntegration shouldBe true
            result.display shouldContain "+ C"
            close(value(result.expression, b) - value(result.expression, a), value(expected, b) - value(expected, a))
        }
    }

    "preserves holes instead of silently cancelling them" {
        for (source in listOf("x/x", "0*(1/x)", "1/x-1/x", "(1/x)^0")) {
            val result = answer(Symbolic.simplify(source))
            result.conditions.any { "!= 0" in it } shouldBe true
            evalResult(result.expression, "0").shouldBeInstanceOf<CalcResult.Err>()
        }
    }
    "derivative qualifications retain original holes and nondifferentiable points" {
        answer(Symbolic.differentiate("x/x")).conditions.any { "x != 0" in it } shouldBe true
        answer(Symbolic.differentiate("abs(x)")).conditions.any { "x != 0" in it } shouldBe true
        answer(Symbolic.differentiate("sqrt(x)")).conditions.any { "x > 0" in it } shouldBe true
    }
    "undefined closed terms cannot disappear under zero multiplication or differentiation" {
        for (source in listOf("0*ln(-1)", "0*(1/0)", "ln(-1)+x", "sqrt(-1)")) {
            Symbolic.simplify(source).shouldBeInstanceOf<CalcResult.Err>()
            Symbolic.differentiate(source).shouldBeInstanceOf<CalcResult.Err>()
        }
    }
    "power rules cannot introduce a logarithm of a nonpositive closed base" {
        for (source in listOf("(-1)^x", "0^x", "sin(-pi/2)^x", "(1-2)^x")) {
            Symbolic.differentiate(source).shouldBeInstanceOf<CalcResult.Err>()
            Symbolic.integrate(source).shouldBeInstanceOf<CalcResult.Err>()
        }
    }
    "closed root budget distinguishes radicand from degree" {
        for ((source, expected) in listOf("root(729,3)" to "9", "root(-729,3)" to "-9", "root(256,4)" to "4")) {
            close(value(answer(Symbolic.simplify(source)).expression), Num.ofDecimal(expected))
            close(value(answer(Symbolic.differentiate("$source*x")).expression), Num.ofDecimal(expected))
        }
    }
    "closed root index is bounded and invalid degrees retain domain errors" {
        Symbolic.simplify("root(2,65)").shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.UNSUPPORTED
        Symbolic.simplify("root(2,0)").shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.DOMAIN
        Symbolic.simplify("root(-729,2)").shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.DOMAIN
    }
    "unsupported forms return honest typed errors" {
        for (result in listOf(Symbolic.integrate("exp(x^2)"), Symbolic.differentiate("floor(x)"),
            Symbolic.factor("sin(x)"), Symbolic.simplify("i*x"), Symbolic.simplify("x", "t"),
            Symbolic.simplify("x!"), Symbolic.simplify("5 km"))) {
            result.shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.UNSUPPORTED
        }
    }
    "symbolic resource limits reject before explosive work" {
        for (result in listOf(Symbolic.expand("(x+y+z)^32"), Symbolic.simplify("(".repeat(100)+"x"+")".repeat(100)),
            Symbolic.simplify("x".repeat(4097)), Symbolic.simplify("exp(1000000)+x"))) {
            result.shouldBeInstanceOf<CalcResult.Err>()
        }
    }
})
