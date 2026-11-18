package com.vaibhav.calc.core

import com.vaibhav.calc.core.num.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.MathContext

class PointTrigSpec : StringSpec({
    val mc = MathContext(40)
    val referencePrecision = MathContext(180)
    // Independent Taylor oracle for |x|<=1. After 120 terms the omitted term is
    // below 1/240!; 1e-160 also exceeds accumulated 180-digit decimal rounding.
    fun reference(x: BigDecimal, cosine: Boolean): DecimalBounds {
        require(x.abs() <= BigDecimal.ONE)
        var term = if (cosine) BigDecimal.ONE else x
        var sum = term
        val negativeSquare = x.multiply(x).negate()
        for (k in 1..120) {
            val divisor = if (cosine) (2L*k-1)*(2L*k) else (2L*k)*(2L*k+1)
            term = term.multiply(negativeSquare, referencePrecision).divide(BigDecimal(divisor), referencePrecision)
            sum = sum.add(term, referencePrecision)
        }
        val error = BigDecimal("1e-160")
        return DecimalBounds(sum.subtract(error), sum.add(error))
    }
    fun contains(actual: Num, expected: DecimalBounds) {
        val interval = actual.bounds(120) ?: error("missing established bound")
        withClue("$interval must contain $expected") {
            (interval.lower <= expected.lower && interval.upper >= expected.upper) shouldBe true
        }
    }
    fun evaluate(x: Num, cosine: Boolean) = if (cosine) NumMath.cos(x, mc) else NumMath.sin(x, mc)

    "exact-input sine and cosine enclose independent references and retain rounded nominal digits" {
        for (text in listOf("1", "-1", "0.25", "1e-12")) for (cosine in listOf(false, true)) {
            val expected = reference(BigDecimal(text), cosine)
            val actual = evaluate(Num.ofDecimal(text), cosine) as Dec
            contains(actual, expected)
            actual.value.compareTo(expected.lower.add(expected.upper).divide(BigDecimal(2)).round(mc)) shouldBe 0
        }
    }

    "a known-point decimal uses the same established enclosure as its exact input" {
        for (cosine in listOf(false, true)) {
            val exact = evaluate(Num.of(1, 4), cosine)
            val point = evaluate(Dec(BigDecimal("0.25"), BigDecimal.ZERO), cosine)
            point shouldBe exact
            contains(point, reference(BigDecimal("0.25"), cosine))
        }
    }

    "unknown and nonzero-bound inputs keep their original nominal-value semantics" {
        for (cosine in listOf(false, true)) {
            val expected = reference(BigDecimal("0.5"), cosine)
            val nominal = expected.lower.add(expected.upper).divide(BigDecimal(2)).round(mc)
            val unknown = evaluate(Dec(BigDecimal("0.5")), cosine) as Dec
            unknown.value.compareTo(nominal) shouldBe 0
            unknown.absoluteError shouldBe null
            val bounded = evaluate(Dec(BigDecimal("0.5"), BigDecimal("0.25")), cosine) as Dec
            bounded.value.compareTo(nominal) shouldBe 0
            contains(bounded, reference(BigDecimal("0.25"), cosine))
            contains(bounded, reference(BigDecimal("0.75"), cosine))
        }
    }

    "original magnitude and precision guards remain while exact angles keep their fast path" {
        shouldThrow<ArithFault.Overflow> { NumMath.sin(Num.of(1_000_001), mc) }
        shouldThrow<ArithFault.Overflow> { NumMath.cos(Num.of(1_000_001), mc) }
        shouldThrow<ArithFault.Overflow> { NumMath.sin(Num.ONE, MathContext(0)) }
        shouldThrow<ArithFault.Overflow> { NumMath.cos(Num.ONE, MathContext(0)) }
        val largeExactAngle = SymConst.of(Num.of(3_000_000), Konst.PI)
        NumMath.sin(largeExactAngle, mc) shouldBe Num.ZERO
        NumMath.cos(largeExactAngle, mc) shouldBe Num.ONE
    }
})
