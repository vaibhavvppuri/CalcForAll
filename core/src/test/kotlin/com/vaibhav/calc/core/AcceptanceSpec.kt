package com.vaibhav.calc.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The M1.1 acceptance criteria — end-to-end through the public [Calculator],
 * exactly as the brief states them. These must pass for M1 to be done.
 */
class AcceptanceSpec : StringSpec({

    val calc = Calculator()

    infix fun String.isExactly(other: String) {
        calc.num(this).compareTo(calc.num(other)) shouldBe 0
    }

    "0.1 + 0.2 == 0.3, exactly (not 0.30000000000000004)" {
        "0.1 + 0.2" isExactly "0.3"
    }

    "1/3 + 1/6 == 1/2, exactly" {
        "1/3 + 1/6" isExactly "1/2"
    }

    "(1/3) * 3 == 1, exactly" {
        "(1/3) * 3" isExactly "1"
    }

    "√8 renders as 2√2 (Unicode radical)" {
        calc.ansOf("√8").exact shouldBe "2√2"
    }

    "sqrt(8) renders as 2√2 (function form)" {
        calc.ansOf("sqrt(8)").exact shouldBe "2√2"
    }

    "1/3 stays 1/3, not decimalised" {
        calc.ansOf("1/3").exact shouldBe "1/3"
    }

    "π stays π" {
        calc.ansOf("pi").exact shouldBe "π"
        calc.ansOf("2*pi").exact shouldBe "2π"
    }
})
