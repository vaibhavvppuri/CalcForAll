package com.vaibhav.calc.core

import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.ExactForm
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.route.ExpressionRouter
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * **D-025 — exact-first means the most UNDERSTOOD exact form, not the most canonical.**
 *
 * Article 3's acceptance test is `0.1 + 0.2 == 0.3`. Rendering `3/10` passes the
 * engine's own test and fails the user's — the whole point of that test was a human
 * seeing `0.3`. These cases are the design's proof frames, which is exactly what this
 * ruling exists to make true.
 */
class FormatterRulingSpec : StringSpec({

    /** The full pipeline a user meets: type text, get a rendered answer. */
    fun show(input: String, settings: Settings = Settings()): String {
        val r = ExpressionRouter(settings, Environment()).route(input)
        val v = ((r as RouteResult.Ok).outcome as Outcome.Scalar).value
        return Formatter.forInput(v, input, settings)
    }

    // --- the design's proof frames --------------------------------------------

    "0.1 + 0.2 renders 0.3 — Article 3's acceptance test, as a human reads it" {
        show("0.1+0.2") shouldBe "0.3"
    }
    "5 km + 300 m renders 5.3 km, not 53/10 km" {
        show("5 km + 300 m") shouldBe "5.3 km"
    }
    "2 crore + 5 lakh renders 2.05 crore, not 41/20 crore" {
        show("2 crore + 5 lakh") shouldBe "2.05 crore"
    }
    "√8 is still 2√2 — a surd is not a decimal" {
        show("sqrt(8)") shouldBe "2√2"
    }
    "(1/3)*3 is exactly 1" { show("(1)/(3)*3") shouldBe "1" }

    // --- the rule itself -------------------------------------------------------

    "a terminating rational renders as the decimal" {
        Formatter.exact(Num.of(53, 10)) shouldBe "5.3"
        Formatter.exact(Num.of(3, 10)) shouldBe "0.3"
        Formatter.exact(Num.of(41, 20)) shouldBe "2.05"
        Formatter.exact(Num.of(1, 2)) shouldBe "0.5"
        Formatter.exact(Num.of(-7, 4)) shouldBe "-1.75"
    }
    "a NON-terminating rational stays a fraction — the fraction IS the exact form" {
        Formatter.exact(Num.of(1, 3)) shouldBe "1/3"
        Formatter.exact(Num.of(2, 7)) shouldBe "2/7"
        Formatter.exact(Num.of(22, 7)) shouldBe "22/7"
    }
    "integers are themselves" {
        Formatter.exact(Num.of(5)) shouldBe "5"
        Formatter.exact(Num.of(83810205)) shouldBe "83810205"
    }
    "a terminating expansion too long to read stays a fraction — 39 digits is not 'understood'" {
        // 1/2^128 terminates, after 39 significant digits. The fraction is the
        // readable form; the decimal is technically exact and humanly useless.
        val tiny = Num.of(java.math.BigInteger.ONE, java.math.BigInteger.TWO.pow(128))
        Formatter.exact(tiny).contains("/") shouldBe true
    }

    // --- output form follows input form ---------------------------------------

    "type 1/2, see 1/2 — the user's own notation is evidence of what they want back" {
        show("1/2") shouldBe "1/2"
        show("(1)/(2)") shouldBe "1/2"
        show("10/4") shouldBe "5/2"
    }
    "type 0.5, see 0.5" {
        show("0.5") shouldBe "0.5"
        show("0.25+0.25") shouldBe "0.5"
    }
    "no fraction in the input means no fraction in the output" {
        Formatter.preferredForm("5 km + 300 m") shouldBe ExactForm.AUTO
        Formatter.preferredForm("0.1+0.2") shouldBe ExactForm.AUTO
        Formatter.preferredForm("1/2") shouldBe ExactForm.FRACTION
        Formatter.preferredForm("2+3/4") shouldBe ExactForm.FRACTION
    }
    "a division that is not a fraction of integers does not trigger fraction form" {
        Formatter.preferredForm("1.5/3") shouldBe ExactForm.AUTO
        show("1.5/3") shouldBe "0.5"
    }
    "an unreadable input is not the place to report a syntax error" {
        Formatter.preferredForm("((") shouldBe ExactForm.AUTO
    }

    // --- S⇔D: the explicit override (census 1.4, fx-991 parity) ---------------

    "S⇔D forces the fraction" {
        val s = Settings(exactForm = ExactForm.FRACTION)
        show("0.1+0.2", s) shouldBe "3/10"
        show("5 km + 300 m", s) shouldBe "(53/10) km"
    }
    "S⇔D forces the decimal" {
        val d = Settings(exactForm = ExactForm.DECIMAL)
        show("1/2", d) shouldBe "0.5"
        show("10/4", d) shouldBe "2.5"
    }
    "an explicit S⇔D choice OUTRANKS what the input implied" {
        // Input says fraction; the user pressed the key and said decimal. The key wins.
        show("1/2", Settings(exactForm = ExactForm.DECIMAL)) shouldBe "0.5"
        show("0.5", Settings(exactForm = ExactForm.FRACTION)) shouldBe "1/2"
    }
    "forcing the decimal on a non-terminating rational rounds, and says so via displayPrecision" {
        val d = Settings(exactForm = ExactForm.DECIMAL, displayPrecision = 6, computePrecision = 40)
        show("1/3", d) shouldBe "0.333333"
    }
    "S⇔D is only offered when the two forms actually differ" {
        val s = Settings()
        Formatter.hasDistinctForms(com.vaibhav.calc.core.eval.RealValue(Num.of(1, 2)), s) shouldBe true
        Formatter.hasDistinctForms(com.vaibhav.calc.core.eval.RealValue(Num.of(5)), s) shouldBe false
        // 1/3 has no readable decimal form, so there is nothing to toggle to.
        Formatter.hasDistinctForms(com.vaibhav.calc.core.eval.RealValue(Num.of(1, 3)), s) shouldBe true
    }

    // --- Article 12: the engine must re-read what it wrote ---------------------

    "5.3 km needs no parentheses and still round-trips" {
        val out = show("5 km + 300 m")
        out shouldBe "5.3 km"
        show(out) shouldBe "5.3 km" // the engine reads its own output back unchanged
    }
    "the fraction form still parenthesises, because a unit binds tighter than /" {
        val s = Settings(exactForm = ExactForm.FRACTION)
        val out = show("5 km + 300 m", s)
        out shouldBe "(53/10) km"
        show(out, s) shouldBe "(53/10) km"
    }
})
