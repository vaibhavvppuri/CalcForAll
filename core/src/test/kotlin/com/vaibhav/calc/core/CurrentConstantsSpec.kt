package com.vaibhav.calc.core

import com.vaibhav.calc.core.constants.Constants
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.Dec
import java.math.BigDecimal
import com.vaibhav.calc.core.num.times
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** NIST 2022 CODATA: https://physics.nist.gov/cuu/Constants/Table/allascii.txt */
class CurrentConstantsSpec : StringSpec({
    "measured electron and proton masses carry 2022 values and uncertainties" {
        val electron = Constants.physical("me")!!
        val electronEstimate = electron.value.shouldBeInstanceOf<Dec>()
        electronEstimate.value.compareTo(BigDecimal("9.1093837139e-31")) shouldBe 0
        electronEstimate.absoluteError shouldBe null // Standard uncertainty is not a hard enclosure.
        electron.uncertainty shouldBe Num.ofDecimal("2.8e-40")
        electron.isExact shouldBe false
        val proton = Constants.physical("mp")!!
        val protonEstimate = proton.value.shouldBeInstanceOf<Dec>()
        protonEstimate.value.compareTo(BigDecimal("1.67262192595e-27")) shouldBe 0
        protonEstimate.absoluteError shouldBe null
        proton.uncertainty shouldBe Num.ofDecimal("5.2e-37")
        proton.isExact shouldBe false
    }
    "molar gas constant is the entire exact SI product rather than a truncated literal" {
        val gas = Constants.physical("R")!!
        gas.value shouldBe (Constants.physical("NA")!!.value * Constants.physical("k_B")!!.value)
        gas.value shouldBe Num.ofDecimal("8.31446261815324")
        gas.isExact shouldBe true
    }
})
