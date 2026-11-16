package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.units.Dimension
import com.vaibhav.calc.core.units.Quantity
import com.vaibhav.calc.core.units.Units
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** M4 — D9: unit engine + dimensional analysis, constants, Indian units. */
class M4Spec : StringSpec({

    val c = Calculator()
    fun cv(v: String, from: String, to: String): Num =
        (c.convert(Num.ofDecimal(v), from, to) as CalcResult.Ok).value
    fun q(v: Long, unit: String) = Quantity(Num.of(v), Units.get(unit)!!)

    // --- conversion across categories (spec 9.1/9.2) -------------------------
    "length conversions are exact" {
        cv("1", "km", "m").compareTo(Num.of(1000)) shouldBe 0
        cv("1", "mi", "m").compareTo(Num.ofDecimal("1609.344")) shouldBe 0
        cv("100", "cm", "m").compareTo(Num.of(1)) shouldBe 0
        cv("12", "in", "ft").compareTo(Num.of(1)) shouldBe 0
    }
    "mass conversions incl. Indian tola" {
        cv("1", "kg", "g").compareTo(Num.of(1000)) shouldBe 0
        cv("1", "tola", "g").compareTo(Num.ofDecimal("11.6638")) shouldBe 0
    }
    "temperature is affine and converts correctly" {
        cv("100", "degC", "K").compareTo(Num.ofDecimal("373.15")) shouldBe 0
        cv("32", "degF", "degC").compareTo(Num.of(0)) shouldBe 0
        cv("212", "degF", "degC").compareTo(Num.of(100)) shouldBe 0
        cv("0", "degC", "degF").compareTo(Num.of(32)) shouldBe 0
    }
    "area conversions incl. Indian bigha" {
        cv("1", "hectare", "m2").compareTo(Num.of(10000)) shouldBe 0
        cv("1", "bigha", "m2").compareTo(Num.ofDecimal("2529.2851")) shouldBe 0
    }
    "speed conversion 36 kmph = 10 m/s" {
        cv("36", "kmph", "mps").compareTo(Num.of(10)) shouldBe 0
    }

    // --- Indian number scales (spec 9.6) ------------------------------------
    "lakh and crore" {
        cv("5", "lakh", "one").compareTo(Num.of(500000)) shouldBe 0
        cv("2", "crore", "one").compareTo(Num.of(20000000)) shouldBe 0
        cv("500000", "one", "lakh").compareTo(Num.of(5)) shouldBe 0
    }

    // --- dimensional analysis (spec 9.1) ------------------------------------
    "multiplying lengths yields an area" {
        val product = q(5, "m") * q(3, "m")
        product.value.compareTo(Num.of(15)) shouldBe 0
        product.unit.dimension shouldBe Dimension.AREA
    }
    "adding compatible lengths converts and sums" {
        val sum = q(5, "km") + q(300, "m")
        sum.toBase().compareTo(Num.of(5300)) shouldBe 0
        sum.value.compareTo(Num.of(53, 10)) shouldBe 0 // 5.3 km
    }
    "adding incompatible dimensions is rejected" {
        shouldThrow<ArithFault.Domain> { q(5, "km") + q(3, "kg") }
    }
    "arithmetic on affine temperature units is rejected" {
        shouldThrow<ArithFault.Domain> { q(20, "degC") + q(5, "degC") }
    }
    "converting incompatible dimensions is a DOMAIN error" {
        c.convert(Num.of(1), "km", "kg").errorOrNull()?.kind shouldBe EvalError.Kind.DOMAIN
    }
    "unknown unit is a DOMAIN error" {
        c.convert(Num.of(1), "km", "furlong").errorOrNull()?.kind shouldBe EvalError.Kind.DOMAIN
    }

    // --- constants (spec 9.4 / 9.5) -----------------------------------------
    "physical constants: exact vs measured" {
        c.physicalConstant("c")!!.value.compareTo(Num.of(299792458L)) shouldBe 0
        c.physicalConstant("c")!!.isExact shouldBe true
        // Renamed by D-026: `h` is the hour. Planck cannot also be `h`.
        c.physicalConstant("h_planck")!!.isExact shouldBe true
        c.physicalConstant("G")!!.isExact shouldBe false // measured → carries uncertainty
    }
    "math constants" {
        Formatter.exact(c.mathConstant("pi")!!, c.settings) shouldBe "π"
        val phi = c.mathConstant("phi")!!
        (phi.compareTo(Num.ofDecimal("1.61")) > 0 && phi.compareTo(Num.ofDecimal("1.62")) < 0) shouldBe true
    }
})
