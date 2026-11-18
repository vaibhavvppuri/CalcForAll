package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

class UncertainRootSpec : StringSpec({
    val mc = MathContext(40)

    "odd roots of an unknown nominal zero retain unknown uncertainty" {
        val calc = Calculator()
        val result = (calc.evaluate("cbrt(approx(0))").getOrThrow() as RealValue).num as Dec
        result.value.signum() shouldBe 0
        result.absoluteError shouldBe null
        result.isZero shouldBe false
        val fifth = NumMath.root(Dec(BigDecimal.ZERO), BigInteger.valueOf(5), mc) as Dec
        fifth.value.signum() shouldBe 0
        fifth.absoluteError shouldBe null
        ((calc.evaluate("floor(cbrt(approx(0)))") as CalcResult.Err).error is EvalError.Unsupported) shouldBe true
        NumMath.cbrt(Num.ZERO, mc) shouldBe Num.ZERO
    }

    "odd-root intervals crossing zero keep both endpoints and the original nominal value" {
        for ((index, radius) in listOf(3 to "8", 5 to "32")) {
            val result = NumMath.root(Dec(BigDecimal.ZERO, BigDecimal(radius)), BigInteger.valueOf(index.toLong()), mc) as Dec
            result.value.signum() shouldBe 0
            val interval = result.bounds(100)!!
            (interval.lower <= BigDecimal(-2) && interval.upper >= BigDecimal(2)) shouldBe true
            (interval.lower > BigDecimal(-3) && interval.upper < BigDecimal(3)) shouldBe true
            result.isZero shouldBe false
        }
        val shifted = NumMath.cbrt(Dec(BigDecimal(-1), BigDecimal(7)), mc) as Dec
        shifted.value.compareTo(BigDecimal(-1)) shouldBe 0
        val interval = shifted.bounds(100)!!
        (interval.lower.pow(3) <= BigDecimal(-8) && interval.upper.pow(3) >= BigDecimal(6)) shouldBe true
    }

    "even-root domain crossings refuse and an unknown square-root zero is no proof of exact zero" {
        val crossesNegative = Dec(BigDecimal.ZERO, BigDecimal.ONE)
        shouldThrow<ArithFault.Unsupported> { NumMath.root(crossesNegative, BigInteger.valueOf(4), mc) }
        shouldThrow<ArithFault.Unsupported> { NumMath.sqrt(crossesNegative, mc) }
        val nominal = NumMath.sqrt(Dec(BigDecimal.ZERO), mc) as Dec
        nominal.value.signum() shouldBe 0
        nominal.absoluteError shouldBe null
        nominal.isZero shouldBe false
        shouldThrow<ArithFault.Unsupported> { NumMath.floor(nominal) }
    }
})
