package com.vaibhav.calc.core

import com.vaibhav.calc.core.num.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.MathContext

class InverseKernelSpec : StringSpec({
    val mc = MathContext(40)
    // Independent rational series: ln(2)=2*sum((1/3)^(2k+1)/(2k+1)).
    // 180 terms and its positive geometric tail establish this 150-digit prefix.
    val ln2 = BigDecimal("0.693147180559945309417232121458176568075500134360255254120680009493393621969694715605863326996418687542001481020570685733685520235758130557032670751635")
    val referenceError = BigDecimal("1e-150")
    fun contains(value: Num, expected: BigDecimal, oracleError: BigDecimal = BigDecimal.ZERO) {
        val interval = value.bounds(100) ?: error("An established enclosure is required")
        withClue("enclosure=$interval; reference=$expected ± $oracleError") {
            (interval.lower <= expected.subtract(oracleError) && expected.add(oracleError) <= interval.upper) shouldBe true
        }
    }

    "inverse kernels enclose independently known values" {
        contains(NumMath.atan(Num.HALF, mc), BigDecimal("0.463647609000806116214256231461214402028537054286120263810933088720197864165741705300600283984887892556529852251190837513505818181625011155471530569944"), referenceError)
        contains(NumMath.asinh(Num.of(3, 4), mc), ln2, referenceError)
        contains(NumMath.acosh(Num.of(5, 4), mc), ln2, referenceError)
        contains(NumMath.atanh(Num.of(1, 3), mc), ln2.divide(BigDecimal(2)), referenceError)
        contains(NumMath.asinh(Num.of(-3, 4), mc), ln2.negate(), referenceError)
    }

    "inverse sine endpoints and acosh one preserve their exact values" {
        NumMath.asin(Num.ONE, mc) shouldBe SymConst.of(Num.HALF, Konst.PI)
        NumMath.asin(-Num.ONE, mc) shouldBe SymConst.of(Num.of(-1, 2), Konst.PI)
        contains(NumMath.acosh(Num.ONE, mc), BigDecimal.ZERO)
        val near = NumMath.acosh(Num.ONE + Num.ofDecimal("1e-30"), mc).bounds()!!
        (near.lower > BigDecimal.ZERO && near.upper < BigDecimal("2e-15")) shouldBe true
    }

    "atan resolves small and large arguments without losing an established bound" {
        val tiny = NumMath.atan(Num.ofDecimal("1e-40"), mc).bounds(100)!!
        (tiny.lower > BigDecimal.ZERO && tiny.upper >= BigDecimal("1e-40").subtract(BigDecimal("1e-120"))) shouldBe true
        contains(NumMath.atan(Num.ofDecimal("1e40"), mc),
            IndependentReference.PI.divide(BigDecimal(2)).subtract(BigDecimal("1e-40")))
    }

    "atan2 respects all quadrants and refuses rectangles across its branch cut or origin" {
        val pi = SymConst.of(Num.ONE, Konst.PI)
        NumMath.atan2(Num.ONE, Num.ONE, mc) shouldBe pi / Num.of(4)
        NumMath.atan2(Num.ONE, -Num.ONE, mc) shouldBe pi * Num.of(3, 4)
        NumMath.atan2(-Num.ONE, -Num.ONE, mc) shouldBe pi * Num.of(-3, 4)
        NumMath.atan2(-Num.ONE, Num.ONE, mc) shouldBe pi * Num.of(-1, 4)
        val crossingX = NumMath.atan2(Num.ONE, Dec(BigDecimal.ZERO, BigDecimal("0.1")), mc).bounds()!!
        (crossingX.lower > BigDecimal.ONE && crossingX.upper < BigDecimal(2)) shouldBe true
        shouldThrow<ArithFault.Unsupported> {
            NumMath.atan2(Dec(BigDecimal("-0.05"), BigDecimal("0.05")), -Num.ONE, mc)
        }
        shouldThrow<ArithFault.Unsupported> {
            NumMath.atan2(Dec(BigDecimal.ZERO, BigDecimal.ONE), Dec(BigDecimal.ZERO, BigDecimal.ONE), mc)
        }
    }

    "inverse domains reject uncertainty crossing a boundary" {
        shouldThrow<ArithFault.Unsupported> { NumMath.asin(Dec(BigDecimal.ONE, BigDecimal("0.01")), mc) }
        shouldThrow<ArithFault.Unsupported> { NumMath.acosh(Dec(BigDecimal.ONE, BigDecimal("0.01")), mc) }
        shouldThrow<ArithFault.Unsupported> { NumMath.atanh(Dec(BigDecimal("0.99"), BigDecimal("0.02")), mc) }
    }
})
