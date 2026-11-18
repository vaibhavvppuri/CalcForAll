package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.num.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal
import java.math.MathContext

class UncertaintySpec : StringSpec({
    val calculator = Calculator()
    fun number(text: String): Num = (calculator.evaluate(text).getOrThrow() as RealValue).num
    fun unsupported(text: String) {
        ((calculator.evaluate(text) as? CalcResult.Err)?.error is EvalError.Unsupported) shouldBe true
    }
    fun enclosed(n: Num, expected: BigDecimal) {
        val d = n as Dec
        val error = d.absoluteError ?: error("No established bound")
        (d.value.subtract(expected).abs() <= error) shouldBe true
    }

    "fractional parts of exact irrational values retain established bounds through cancellation" {
        val values = listOf(
            "e" to IndependentReference.E,
            "pi" to IndependentReference.PI,
            "-pi" to IndependentReference.PI.negate(),
            "pi*10^60" to IndependentReference.PI.scaleByPowerOfTen(60),
        )
        for ((expression, reference) in values)
            enclosed(number("frac($expression)"), IndependentReference.fraction(reference))
        number("floor(10*frac(e))") shouldBe Num.of(7)
        number("ceil(10*frac(-pi))") shouldBe Num.of(-1)
    }

    "legacy estimates have unknown uncertainty and cannot prove a rounding decision" {
        val estimate = Dec(BigDecimal.ONE)
        estimate.absoluteError shouldBe null
        estimate.toBigIntegerExactOrNull() shouldBe null
        shouldThrow<ArithFault.Unsupported> { NumMath.floor(estimate) }
        unsupported("floor(approx(1))")
    }

    "known intervals decide only when every value rounds the same way" {
        number("floor(approx(1.25,0.01))") shouldBe Num.ONE
        number("ceil(approx(1.25,0.01))") shouldBe Num.TWO
        number("round(approx(-2.1,0.01))") shouldBe Num.of(-2)
        number("floor(approx(1,0))") shouldBe Num.ONE
        unsupported("floor(approx(1,1e-40))")
        unsupported("round(approx(2.5,1e-40))")
        unsupported("mod(approx(6,0.01),3)")
    }

    "approx round trips preserve bounded and unknown metadata" {
        val bounded = Dec(BigDecimal("1.25"), BigDecimal("0.001"))
        number(Formatter.reusableInput(bounded)) shouldBe bounded
        val unknown = Dec(BigDecimal("0.1234567890123456789012345678901234567890123456789"))
        number(Formatter.reusableInput(unknown)) shouldBe unknown
        (bounded == Dec(bounded.value)) shouldBe false
        ((calculator.evaluate("approx(2,-0.1)") as? CalcResult.Err)?.error is EvalError.Domain) shouldBe true
    }

    "estimated zeros do not become exact identities or vanish from products" {
        val uncertainZero = number("approx(0,1)") as Dec
        uncertainZero.isZero shouldBe false
        ((uncertainZero * Num.TWO) as Dec).absoluteError shouldBe BigDecimal(2)
        enclosed(number("approx(0,1)+1"), BigDecimal(2))
        enclosed(number("1-approx(0,1)"), BigDecimal.ZERO)
        enclosed(number("approx(0,1)/2"), BigDecimal("0.5"))
        number("0*approx(2,1)") shouldBe Num.ZERO
        unsupported("1/approx(0,1)")
        unsupported("0/approx(0)")
        unsupported("0/approx(2)")
        number("0/approx(2,1)") shouldBe Num.ZERO
        (number("exp(approx(0))") is Dec) shouldBe true
    }

    "uncertainty blocks integer and exact-angle shortcuts" {
        (number("sin(approx(0,1))") is Dec) shouldBe true
        (number("cos(approx(0,1))") is Dec) shouldBe true
        (number("2^approx(2,0.1)") is Dec) shouldBe true
        (number("log(approx(10,1))") is Dec) shouldBe true
        (calculator.evaluate("fact(approx(5,0.1))") is CalcResult.Err) shouldBe true
        val root = calculator.evaluate("root(approx(0,1),3)")
        (root is CalcResult.Err || ((root as CalcResult.Ok).value as RealValue).num is Dec) shouldBe true
    }

    "ordinary scientific rounding remains usable with established bounds" {
        number("floor(sin(1))") shouldBe Num.ZERO
        number("floor(log10(12))") shouldBe Num.ONE
        number("ceil(exp(1))") shouldBe Num.of(3)
        number("floor(ln(2)*10)") shouldBe Num.of(6)
        number("log10(1/100)") shouldBe Num.of(-2)
    }

    "a function rounded onto an integer cannot claim that integer's whole part" {
        unsupported("floor(tanh(60))")
        unsupported("ceil(tanh(-60))")
    }

    "cancellation carries its lost digits and exposes them in the display" {
        val result = number("10^49+sqrt(2)-10^49")
        enclosed(result, IndependentReference.root(2))
        Formatter.exact(result).shouldContain("±")
        unsupported("floor(10^49+sqrt(2)-10^49)")
        number("10^49+1-10^49") shouldBe Num.ONE
    }

    "a bounded imaginary component cannot disappear as zero or unit coefficient" {
        val uncertain = calculator.evaluate("approx(1,0.1)i").getOrThrow()
        (uncertain is ComplexValue) shouldBe true
        Formatter.exact(uncertain, Settings()).shouldContain("±")
        (calculator.evaluate("approx(0,1)i").getOrThrow() is ComplexValue) shouldBe true
        calculator.evaluate(Formatter.reusableInput(uncertain)).getOrThrow() shouldBe uncertain
    }

    "registers retain uncertainty rather than inventing fifty trustworthy digits" {
        (calculator.route("sqrt(2)+1 → A") is com.vaibhav.calc.core.route.RouteResult.Ok) shouldBe true
        val stored = number("A") as Dec
        (stored.absoluteError != null) shouldBe true
        unsupported("floor(A*10^60)")
    }

    "min max and sign respect the possible values rather than the nominal ordering" {
        enclosed(number("min(approx(0,1),approx(5,10))"), BigDecimal(-5))
        enclosed(number("max(approx(0,1),approx(-5,10))"), BigDecimal(5))
        unsupported("sign(approx(0,1))")
        number("sign(approx(2,1))") shouldBe Num.ONE
        number("sign(approx(-2,1))") shouldBe Num.of(-1)
    }

    "an imaginary interval that crosses zero keeps its signed coefficient" {
        val value = calculator.evaluate("approx(-1,2)i").getOrThrow()
        val displayed = Formatter.exact(value, Settings())
        displayed.shouldContain("(-1 ± 2)i")
        Formatter.exact(calculator.evaluate("approx(0,1)i").getOrThrow(), Settings()).shouldContain("(0 ± 1)i")
    }
})
