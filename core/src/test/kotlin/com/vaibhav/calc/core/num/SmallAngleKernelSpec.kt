package com.vaibhav.calc.core.num

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.MathContext

class SmallAngleKernelSpec : StringSpec({
    // Independent reference intervals: 160 exact rational Taylor terms at each
    // exact decimal argument. The next alternating term is below 1e-500; exact
    // integer comparisons place both sum and remainder inside this 120-place bin.
    val references = listOf(
        Triple("2.9999999999",
            "0.141120008158866471760083748494803224460122850901414678894551733853303457366314897934014874006163945095951522215721314120",
            "-0.989992496586333456460636110038208114827857640829657976773430289266090773874292572078779485933658104634793895620072160467"),
        Triple("3",
            "0.141120008059867222100744802808110279846933264252265584151882641232422009967014471911282172853449863750413672948267327416",
            "-0.989992496600445457271572794731261302393679096615588328814085932928329197513133220428294479355692602171495993112414169190"),
        Triple("3.0000000001",
            "0.141120007960867972439994657040818663012737405575102552170928388450374618590007215862539568240180742699476868023651555038",
            "-0.989992496614557458072609554458310035386793074391677738209615379314388674484560019909342393047368202373132267675677700127"),
    )
    fun reference(prefix: String): DecimalBounds {
        val lower = BigDecimal(prefix)
        return DecimalBounds(lower, lower.add(BigDecimal("1e-120")))
    }
    fun encloses(actual: DecimalBounds, expected: DecimalBounds) {
        withClue("$actual must contain the independent reference $expected") {
            (actual.lower <= expected.lower && actual.upper >= expected.upper) shouldBe true
        }
    }

    "trig enclosures retain precision on both sides of the positive and negative small-angle boundary" {
        for ((argument, sine, cosine) in references) for (precision in listOf(20, 40, 66)) for (sign in listOf(-1, 1)) {
            val x = BigDecimal(argument).multiply(BigDecimal(sign))
            val sin = CertifiedMath.sin(DecimalBounds.point(x), precision)
            val cos = CertifiedMath.cos(DecimalBounds.point(x), precision)
            encloses(sin, if (sign < 0) reference(sine).negate() else reference(sine))
            encloses(cos, reference(cosine))
            // A broad [-1,1] enclosure would be safe but would defeat the solver.
            val widthLimit = BigDecimal.ONE.scaleByPowerOfTen(3 - precision)
            (sin.upper.subtract(sin.lower) <= widthLimit) shouldBe true
            (cos.upper.subtract(cos.lower) <= widthLimit) shouldBe true
        }
    }

    "uncertain arguments spanning the small-angle boundary retain all endpoint values and unknown provenance" {
        val radius = BigDecimal("1e-10")
        val mc = MathContext(40)
        for (sign in listOf(-1, 1)) {
            val argument = Dec(BigDecimal(3 * sign), radius)
            val sin = NumMath.sin(argument, mc) as Dec
            val cos = NumMath.cos(argument, mc) as Dec
            for ((_, sine, cosine) in references) {
                encloses(sin.bounds()!!, if (sign < 0) reference(sine).negate() else reference(sine))
                encloses(cos.bounds()!!, reference(cosine))
            }
            (sin.absoluteError!!.signum() > 0) shouldBe true
            (cos.absoluteError!!.signum() > 0) shouldBe true
            val unknown = Dec(BigDecimal(3 * sign))
            val unknownSin = NumMath.sin(unknown, mc) as Dec
            val unknownCos = NumMath.cos(unknown, mc) as Dec
            unknownSin.absoluteError shouldBe null
            unknownCos.absoluteError shouldBe null
            val expectedSin = BigDecimal(references[1].second).multiply(BigDecimal(sign))
            (unknownSin.value.subtract(expectedSin).abs() < BigDecimal("1e-38")) shouldBe true
            (unknownCos.value.subtract(BigDecimal(references[1].third)).abs() < BigDecimal("1e-38")) shouldBe true
        }
    }

    "small-angle handling preserves wide uncertainty and existing argument magnitude guards" {
        val wide = DecimalBounds(BigDecimal(-3), BigDecimal(3))
        for (calculate in listOf(CertifiedMath::sin, CertifiedMath::cos)) {
            val result = calculate(wide, 66)
            result.lower.compareTo(BigDecimal.ONE.negate()) shouldBe 0
            result.upper.compareTo(BigDecimal.ONE) shouldBe 0
            for (sign in listOf(-1, 1)) {
                val outside = BigDecimal(NumericLimits.MAX_TRIG_ARGUMENT + 1).multiply(BigDecimal(sign))
                shouldThrow<ArithFault.Overflow> { calculate(DecimalBounds.point(outside), 66) }
            }
        }
    }
})
