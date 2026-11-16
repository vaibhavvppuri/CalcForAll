package com.vaibhav.calc.core

import com.vaibhav.calc.core.constants.Constants
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.times
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** NIST 2022 CODATA: https://physics.nist.gov/cuu/Constants/Table/allascii.txt */
class CurrentConstantsSpec : StringSpec({
    "measured electron and proton masses carry 2022 values and uncertainties" {
        val electron = Constants.physical("me")!!
        electron.value shouldBe Num.ofDecimal("9.1093837139e-31")
        electron.uncertainty shouldBe Num.ofDecimal("2.8e-40")
        electron.isExact shouldBe false
        val proton = Constants.physical("mp")!!
        proton.value shouldBe Num.ofDecimal("1.67262192595e-27")
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
