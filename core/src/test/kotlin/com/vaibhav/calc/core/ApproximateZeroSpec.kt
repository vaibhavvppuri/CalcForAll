package com.vaibhav.calc.core

import com.vaibhav.calc.core.num.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class ApproximateZeroSpec : StringSpec({
    val estimate = Dec(BigDecimal.ZERO)
    fun decimal(value: Num, expected: String) {
        value.shouldBeInstanceOf<Dec>().value.compareTo(BigDecimal(expected)) shouldBe 0
    }

    "adding and subtracting an estimated zero retains approximate provenance" {
        decimal(estimate + Num.ONE, "1")
        decimal(Num.ONE + estimate, "1")
        decimal(estimate - Num.ONE, "-1")
        decimal(Num.ONE - estimate, "1")
        decimal(estimate + Num.ZERO, "0")
        decimal(Num.ZERO - estimate, "0")
    }

    "multiplication by an approximate operand retains provenance even at zero" {
        decimal(estimate * Num.TWO, "0")
        decimal(Num.TWO * estimate, "0")
        decimal(estimate * Dec(BigDecimal("7")), "0")
        decimal(Dec(BigDecimal("7")) * estimate, "0")
    }

    "zero division preserves approximate inputs and still refuses a zero divisor" {
        decimal(estimate / Num.TWO, "0")
        decimal(estimate / Dec(BigDecimal("2")), "0")
        shouldThrow<ArithFault.Unsupported> { Num.ONE / estimate }
        shouldThrow<ArithFault.Unsupported> { estimate / estimate }
        shouldThrow<ArithFault.DivideByZero> { Num.ONE / Dec(BigDecimal.ZERO, BigDecimal.ZERO) }
    }

    "exact-only zero algebra remains exact including irrational denominators" {
        val surd = NumMath.sqrt(Num.TWO,Num.MC)
        val pi = Calculator().evaluate("pi").getOrThrow().let { (it as com.vaibhav.calc.core.eval.RealValue).num }
        (Num.ZERO + Num.ONE).shouldBeInstanceOf<Rational>() shouldBe Num.ONE
        (Num.ZERO - Num.ONE).shouldBeInstanceOf<Rational>() shouldBe Num.of(-1)
        (Num.ZERO * surd).shouldBeInstanceOf<Rational>() shouldBe Num.ZERO
        (Num.ZERO / surd).shouldBeInstanceOf<Rational>() shouldBe Num.ZERO
        (Num.ZERO / pi).shouldBeInstanceOf<Rational>() shouldBe Num.ZERO
        (surd - surd).shouldBeInstanceOf<Rational>() shouldBe Num.ZERO
        (Num.ZERO * Dec(BigDecimal("7"))).shouldBeInstanceOf<Rational>() shouldBe Num.ZERO
        (Dec(BigDecimal("7")) * Num.ZERO).shouldBeInstanceOf<Rational>() shouldBe Num.ZERO
        (Num.ZERO / Dec(BigDecimal("2"), BigDecimal.ZERO)).shouldBeInstanceOf<Rational>() shouldBe Num.ZERO
        shouldThrow<ArithFault.Unsupported> { Num.ZERO / Dec(BigDecimal("2")) }
        (Num.ZERO * estimate).shouldBeInstanceOf<Rational>() shouldBe Num.ZERO
    }

    "an exact zero does not round an already stored decimal approximation" {
        val value = Dec(BigDecimal("1.2345678901234567890123456789012345678901234567890123456789"))
        (value + Num.ZERO) shouldBe value
        (Num.ZERO + value) shouldBe value
    }
})
