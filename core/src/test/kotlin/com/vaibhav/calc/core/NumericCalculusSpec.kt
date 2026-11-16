package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.symbolic.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.math.MathContext

class NumericCalculusSpec : StringSpec({
    fun integral(source: String, a: String = "0", b: String = "1", options: IntegrationOptions = IntegrationOptions()) =
        NumericCalculus.integrate(source, Num.ofDecimal(a), Num.ofDecimal(b), options = options)
    fun close(actual: Num, expected: String, tolerance: String = "1e-9") {
        (actual.toBigDecimal(MathContext(40)).subtract(BigDecimal(expected)).abs() < BigDecimal(tolerance)) shouldBe true
    }

    for ((source, expected) in listOf(
        "1" to "1", "x" to "0.5", "x^2" to "0.3333333333333333333333333333333333",
        "x^10" to "0.0909090909090909090909090909090909",
        "sin(x)" to "0.4596976941318602825990633925570234",
        "cos(x)" to "0.8414709848078965066525023216302990",
        "exp(x)" to "1.7182818284590452353602874713526625",
        "1/(1+x^2)" to "0.7853981633974483096156608458198757",
        "sqrt(x)" to "0.6666666666666666666666666666666667",
        "abs(x-0.3)" to "0.29",
        "ln(x+1)" to "0.3862943611198906188344642429163531",
    )) {
        "numerically integrates $source against an independent known value" {
            val result = integral(source).shouldBeInstanceOf<CalcResult.Ok<IntegralEstimate>>().value
            close(result.value, expected)
            result.approximate shouldBe true
            result.angleMode shouldBe "RAD"
            (result.evaluations in 62..4096) shouldBe true
            result.warnings.isNotEmpty() shouldBe true
            (result.estimatedError.signum() >= 0) shouldBe true
        }
    }
    "handles reversed bounds and a zero-width defined interval" {
        close(integral("x^2", "1", "0").getOrThrow().value, "-0.3333333333333333333333333")
        integral("x^2", "3", "3").getOrThrow().value.isZero shouldBe true
        integral("1/x", "0", "0").shouldBeInstanceOf<CalcResult.Err>()
    }
    "integrates an odd-root function on the negative real axis" {
        close(integral("cbrt(x)", "-8", "-1").getOrThrow().value, "-11.25")
    }
    "selected y variable is evaluated without changing radians" {
        val result = NumericCalculus.integrate("y^2", Num.ZERO, Num.ONE, "y").getOrThrow()
        close(result.value, "0.333333333333333333333333")
    }
    "does not confuse a Cauchy principal value with a proper integral" {
        for (source in listOf("1/x", "1/(x-0.13)", "x/x", "1/x^2", "tan(x)", "ln(x)")) {
            integral(source, "-2", "2").shouldBeInstanceOf<CalcResult.Err>()
        }
    }
    "unsupported discontinuities, extra variables and complex values are explicit" {
        for (source in listOf("floor(x)", "round(x)", "x+y", "sqrt(-1)", "5 km")) {
            integral(source).shouldBeInstanceOf<CalcResult.Err>()
        }
    }
    "resource and option limits return typed errors" {
        for (options in listOf(IntegrationOptions(maxEvaluations = 1), IntegrationOptions(maxDepth = 1000),
            IntegrationOptions(computePrecision = 100000), IntegrationOptions(maxMillis = 0),
            IntegrationOptions(absoluteTolerance = Num.ofDecimal("1e-50")), IntegrationOptions(relativeTolerance = Num.ZERO))) {
            integral("x", options = options).shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.UNSUPPORTED
        }
        integral("x", "0", "1e13").shouldBeInstanceOf<CalcResult.Err>()
        integral("x", "0", "1e-30").shouldBeInstanceOf<CalcResult.Err>()
        integral("exp(1000000*x)").shouldBeInstanceOf<CalcResult.Err>()
    }
    "exhausted convergence budget returns an error, never a partial confident value" {
        integral("abs(x-0.123456789)", options = IntegrationOptions(maxEvaluations = 128,
            absoluteTolerance = Num.ofDecimal("1e-20"), relativeTolerance = Num.ofDecimal("1e-20")))
            .shouldBeInstanceOf<CalcResult.Err>()
    }
    "a higher precision request meets its numerical target on a smooth function" {
        val result = integral("exp(x)", options = IntegrationOptions(absoluteTolerance = Num.ofDecimal("1e-18"),
            relativeTolerance = Num.ofDecimal("1e-18"))).getOrThrow()
        close(result.value, "1.7182818284590452353602874713526625", "1e-18")
    }
    "distinct close bounds cannot silently collapse to a zero integral" {
        integral("1e100", "999999999999", "999999999999.0000000000000000000001",
            IntegrationOptions(computePrecision = 32)).shouldBeInstanceOf<CalcResult.Err>()
    }
    "exact polynomial cancellation happens before numerical sampling" {
        close(integral("(1e80+x)-1e80").getOrThrow().value, "0.5")
        val large = integral("1e80+x").getOrThrow()
        close(large.value / Num.ofDecimal("1e80"), "1", "1e-20")
        close(integral("1e80*x").getOrThrow().value / Num.ofDecimal("1e80"), "0.5", "1e-20")
    }
    "unresolved catastrophic intermediate rounding is refused" {
        integral("(1e80+sin(x))-1e80").shouldBeInstanceOf<CalcResult.Err>()
        integral("1e80*sin(x)-1e80*(sin(x)+1e-80)").shouldBeInstanceOf<CalcResult.Err>()
        integral("1e40*(sin(x)^2+cos(x)^2-1)").shouldBeInstanceOf<CalcResult.Err>()
    }
    "ordinary zeros and odd symmetric functions do not trigger conditioning rejection" {
        close(integral("sin(x)", "-1", "1").getOrThrow().value, "0")
        close(integral("cos(x)", "-1", "1").getOrThrow().value, "1.682941969615793013305004643260598")
        val result = NumericCalculus.integrate("sin(x)", Num.ZERO, Num.ofDecimal(Num.pi(MathContext(48)))).getOrThrow()
        close(result.value, "2")
    }
    "constant continuous expressions and numerically equal bounds remain valid" {
        close(integral("floor(1)").getOrThrow().value, "1")
        NumericCalculus.integrate("x", Dec(BigDecimal("1.0")), Dec(BigDecimal("1.00"))).getOrThrow().value.isZero shouldBe true
    }
})
