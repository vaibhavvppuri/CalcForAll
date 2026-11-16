package com.vaibhav.calc.core

import com.vaibhav.calc.core.num.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.math.BigInteger

class RationalPowerSpec : StringSpec({
    "large reduced exponent components do not create an enormous temporary power" {
        // The ratio is close to 0.585, while the old base^numerator temporary is enormous.
        val denominator = BigInteger.ONE.shiftLeft(40)
        val exponent = Num.of(BigInteger("643159705013"),denominator)
        val actual = NumMath.pow(Num.TWO,exponent,Num.MC)
        val decimalReference = NumMath.pow(Num.TWO,Dec(exponent.toBigDecimal(Num.MC)),Num.MC)
        ((actual - decimalReference).abs() < Num.ofDecimal("1e-45")) shouldBe true
        (actual > Num.ONE && actual < Num.TWO) shouldBe true
    }

    "half-integer and perfect higher roots stay exact" {
        NumMath.pow(Num.TWO,Num.of(3,2),Num.MC).shouldBeInstanceOf<Surd>()
        NumMath.pow(Num.of(4),Num.of(3,2),Num.MC) shouldBe Num.of(8)
        NumMath.pow(Num.of(81,16),Num.of(3,4),Num.MC) shouldBe Num.of(27,8)
        NumMath.pow(Num.of(-8),Num.of(2,3),Num.MC) shouldBe Num.of(4)
        NumMath.pow(Num.of(-8),Num.of(-2,3),Num.MC) shouldBe Num.of(1,4)
    }

    "odd-denominator negative-base powers retain sign and reciprocal conventions" {
        val even = NumMath.pow(Num.of(-2),Num.of(2,3),Num.MC)
        val positive = NumMath.pow(Num.TWO,Num.of(2,3),Num.MC)
        even shouldBe positive
        val negative = NumMath.pow(Num.of(-2),Num.of(-1,3),Num.MC)
        (negative.signum() < 0) shouldBe true
        ((negative * NumMath.cbrt(Num.TWO,Num.MC) + Num.ONE).abs() < Num.ofDecimal("1e-45")) shouldBe true
        shouldThrow<ArithFault.Domain> { NumMath.pow(Num.of(-2),Num.of(3,4),Num.MC) }
        shouldThrow<ArithFault.DivideByZero> { NumMath.pow(Num.ZERO,Num.of(-2,3),Num.MC) }
    }

    "actual enormous results remain bounded even when exponent components are modest" {
        shouldThrow<ArithFault.Overflow> { NumMath.pow(Num.ofDecimal("1e1000"),Num.of(10001,10),Num.MC) }
    }
})
