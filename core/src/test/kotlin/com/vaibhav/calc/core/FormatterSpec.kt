package com.vaibhav.calc.core

import com.vaibhav.calc.core.eval.NotationMode
import com.vaibhav.calc.core.eval.Settings
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** Golden tests on formatting output (Article 4 / spec M1.6). */
class FormatterSpec : StringSpec({

    val c = Calculator()
    fun exact(s: String) = c.ansOf(s).exact
    fun dec(s: String) = c.ansOf(s).decimal

    // --- exact forms --------------------------------------------------------
    /**
     * **Updated by D-025.** `0.1 + 0.2` rendered `3/10` here — the engine passing its
     * own headline test and failing the user's, since the whole point of Article 3's
     * acceptance test is a human seeing `0.3`.
     *
     * The other four rows are unchanged, and that is the interesting part: they were
     * all written with a fraction in the input, so "output form follows input form"
     * reproduces the goldens a human already chose. The rule was not invented to fit
     * one case.
     */
    "exact fractions" {
        exact("0.1 + 0.2") shouldBe "0.3" // was "3/10" — D-025
        exact("10/4") shouldBe "5/2"
        exact("1/3") shouldBe "1/3"
        exact("6/3") shouldBe "2"
        exact("-3/4") shouldBe "-3/4"
    }
    "exact surds" {
        exact("sqrt(8)") shouldBe "2√2"
        exact("sqrt(2)") shouldBe "√2"
        exact("2^0.5") shouldBe "√2"
        exact("1/sqrt(2)") shouldBe "(√2)/2"
        exact("sqrt(12)") shouldBe "2√3"
    }
    "exact constants" {
        exact("pi") shouldBe "π"
        exact("2*pi") shouldBe "2π"
        exact("pi/6") shouldBe "π/6"
        exact("-pi") shouldBe "-π"
    }

    // --- decimal forms ------------------------------------------------------
    "decimals at 12 significant figures" {
        dec("0.1 + 0.2") shouldBe "0.3"
        dec("1/2") shouldBe "0.5"
        dec("1/3") shouldBe "0.333333333333"
        dec("pi") shouldBe "3.14159265359"
        dec("e") shouldBe "2.71828182846"
        dec("sqrt(2)") shouldBe "1.41421356237"
    }

    // --- AUTO notation switches to scientific at the extremes ---------------
    "AUTO uses plain for moderate magnitudes, scientific at the extremes" {
        dec("123456789012") shouldBe "123456789012"
        dec("10^20") shouldBe "1e20"
        dec("1/10^6") shouldBe "1e-6"
    }

    // --- forced notation modes ---------------------------------------------
    "SCIENTIFIC and ENGINEERING notation" {
        val sci = Calculator(Settings(notation = NotationMode.SCIENTIFIC))
        val eng = Calculator(Settings(notation = NotationMode.ENGINEERING))
        sci.ansOf("12345").decimal shouldBe "1.2345e4"
        eng.ansOf("12345").decimal shouldBe "12.345e3"
    }
})
