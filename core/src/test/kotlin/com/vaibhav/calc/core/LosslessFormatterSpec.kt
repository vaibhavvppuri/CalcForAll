package com.vaibhav.calc.core

import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.format.OutcomeFormatter
import com.vaibhav.calc.core.num.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class LosslessFormatterSpec : StringSpec({
    "clipboard text preserves all stored approximate digits without display rounding" {
        val digits = "1.4142135623730950488016887242096980785696718753769"
        val value = RealValue(Dec(BigDecimal(digits)))
        Formatter.input(value) shouldBe digits
        (Calculator().evaluate(Formatter.input(value)).getOrThrow() as RealValue).num.toBigDecimal(Num.MC)
            .compareTo(BigDecimal(digits)) shouldBe 0
    }
    "clipboard text preserves a complex fractional coefficient and sign" {
        val value = ComplexValue(Complex(Num.of(1, 3), Num.of(-2, 7)))
        Calculator().evaluate(Formatter.input(value)).getOrThrow() shouldBe value
    }
    "quantity coefficients are parenthesized to retain their dimension" {
        val value = Calculator().evaluate("(1/3) km").getOrThrow()
        Calculator().evaluate(Formatter.input(value)).getOrThrow() shouldBe value
    }
    "exact symbolic forms survive a clipboard round trip" {
        listOf("sqrt(8)", "pi/6", "123456789/7").forEach { source ->
            val value = Calculator().evaluate(source).getOrThrow()
            Calculator().evaluate(Formatter.input(value)).getOrThrow() shouldBe value
        }
    }
    "a multiple of e is copied with its product sign, so it reads back and is never taken for an exponent" {
        listOf("2*e", "3*e/2", "-5*e", "e/7", "e").forEach { source ->
            val value = Calculator().evaluate(source).getOrThrow()
            Calculator().evaluate(Formatter.input(value)).getOrThrow() shouldBe value
        }
        val twoE = Calculator().evaluate("2*e").getOrThrow()
        Formatter.input(twoE) shouldBe "2*e"
        Formatter.exact(twoE, Settings()) shouldBe "2e" // the display form is unchanged
        // Typing on after a pasted value keeps the value: 2·e + 3, never 2 × 10³.
        val continued = Calculator().evaluate(Formatter.input(twoE) + "+3").getOrThrow() as RealValue
        continued.num.toBigDecimal(Num.MC).toDouble() shouldBe (2 * Math.E + 3 plusOrMinus 1e-12)
    }
    "scientific decimal storage is copied compactly without invented trailing zeros" {
        val value = Dec(BigDecimal("1.23456789E+1000"))
        Formatter.input(value) shouldBe "1.23456789E+1000"
    }
    "full integer display is exact even when decimal display precision is lower" {
        val settings = Settings(exactForm = ExactForm.DECIMAL, displayPrecision = 4)
        val value = RealValue(Num.of(123456789))
        Formatter.exact(value, settings) shouldBe "123456789"
        OutcomeFormatter.isApproximate(value, settings) shouldBe false
    }
})
