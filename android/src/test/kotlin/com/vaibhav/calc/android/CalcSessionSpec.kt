package com.vaibhav.calc.android

import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.keypad.FnPalette
import com.vaibhav.calc.core.keypad.Key
import com.vaibhav.calc.core.keypad.Keypad
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Headless tests of the app's wiring: UI event → Keypad → Editor → evaluator →
 * Formatter. This is the module's only test infra (plain JVM unit tests; no
 * instrumented/Robolectric setup exists) — it tests the session state holder,
 * not pixels. Keys are looked up from the real layout by id, so a test cannot
 * press a key the pad does not have.
 */
class CalcSessionSpec : StringSpec({

    fun key(id: String): Key = requireNotNull(Keypad.basic().key(id)) { "no key '$id' on the pad" }

    fun CalcSession.type(vararg ids: String): CalcUiState {
        var s = ui
        for (id in ids) s = press(key(id))
        return s
    }

    "1 + 2 = shows exact 3" {
        val s = CalcSession()
        val ui = s.type("1", "plus", "2", "equals")
        ui.exact shouldBe "3"
        ui.error.shouldBeNull()
    }

    "0.1 + 0.2 = renders 0.3 through the app's own path (Article 3 as the user sees it)" {
        val s = CalcSession()
        val ui = s.type("0", "point", "1", "plus", "0", "point", "2", "equals")
        ui.exact shouldBe "0.3"
    }

    "flick ÷↑√ builds a root: √8 = 2√2" {
        val s = CalcSession()
        s.flickUp(key("divide"))
        val ui = s.type("8", "equals")
        ui.entryText shouldBe "sqrt(8)"
        ui.exact shouldBe "2√2"
    }

    "a flick on a key with no flick action is a no-op, not a tap" {
        val s = CalcSession()
        s.type("5")
        val ui = s.flickUp(key("plus"))
        ui.entryText shouldBe "5"
    }

    "a palette pick inserts through the same door: sin, 30 = 0.5" {
        val s = CalcSession()
        val sin = requireNotNull(FnPalette.entries.first { it.label == "sin" })
        s.paletteInsert(sin)
        val ui = s.type("3", "0", "equals")
        ui.entryText shouldBe "sin(30)"
        ui.exact shouldBe "0.5"
    }

    "1 divided by 0 is a typed error on screen, not a crash" {
        val s = CalcSession()
        // ÷ creates the fraction structure (trailing-operand capture), 0 fills it.
        val ui = s.type("1", "divide", "0", "equals")
        ui.error.shouldNotBeNull()
        ui.exact.shouldBeNull()
    }

    "an edit after = clears the shown result — no stale answer over a changed entry" {
        val s = CalcSession()
        s.type("1", "plus", "2", "equals").exact shouldBe "3"
        val ui = s.type("backspace")
        ui.exact.shouldBeNull()
        ui.decimal.shouldBeNull()
    }

    "equals on an empty entry is a no-op, not an error" {
        val s = CalcSession()
        val ui = s.type("equals")
        ui.exact.shouldBeNull()
        ui.error.shouldBeNull()
        ui.entryText shouldBe ""
    }

    "an incomplete entry commits to the parser's own refusal, not a crash" {
        val s = CalcSession()
        val ui = s.type("1", "plus", "equals")
        ui.error.shouldNotBeNull()
    }

    "the fn key edits nothing — opening the palette is the view's job" {
        val s = CalcSession()
        s.type("7")
        val ui = s.press(key("fn"))
        ui.entryText shouldBe "7"
    }

    // ---- M7-2 Stage A: the chips and hold-repeat the design strip adds -----

    "the Ans chip inserts the token and reuses the last answer" {
        val s = CalcSession()
        s.type("1", "plus", "2", "equals").exact shouldBe "3"
        repeat(3) { s.type("backspace") } // clear the committed entry
        s.insertAns()
        val ui = s.type("plus", "1", "equals")
        ui.entryText shouldBe "ans+1"
        ui.exact shouldBe "4"
    }

    "the angle chip cycles DEG → RAD → GRAD → DEG (M-6)" {
        val s = CalcSession()
        s.ui.angle shouldBe AngleMode.DEG
        s.cycleAngle().angle shouldBe AngleMode.RAD
        s.cycleAngle().angle shouldBe AngleMode.GRAD
        s.cycleAngle().angle shouldBe AngleMode.DEG
    }

    "the engine follows the angle chip: sin(100) in GRAD is 1" {
        val s = CalcSession()
        s.cycleAngle() // RAD
        s.cycleAngle() // GRAD
        val sin = requireNotNull(FnPalette.entries.first { it.label == "sin" })
        s.paletteInsert(sin)
        val ui = s.type("1", "0", "0", "equals")
        ui.exact shouldBe "1"
    }

    "an edit preserves the angle chip's mode — a setting is not a result" {
        val s = CalcSession()
        s.cycleAngle() // RAD
        val ui = s.type("5")
        ui.angle shouldBe AngleMode.RAD
    }

    "S⇔D appears only when the two exact forms differ" {
        val s = CalcSession()
        s.type("1", "plus", "2", "equals").sdVisible shouldBe false
        val t = CalcSession()
        val ui = t.type("1", "divide", "2", "equals")
        ui.exact shouldBe "1/2"
        ui.sdVisible shouldBe true
    }

    "S⇔D flips the shown form in place, both ways" {
        val s = CalcSession()
        s.type("1", "divide", "2", "equals").exact shouldBe "1/2"
        s.sdToggle().exact shouldBe "0.5"
        s.sdToggle().exact shouldBe "1/2"
        s.ui.decimal shouldBe "0.5" // the ≈ line never moves
    }

    "S⇔D is scoped to the shown result — recommitting re-reads the input's own notation (D-025)" {
        val s = CalcSession()
        s.type("1", "divide", "2", "equals").exact shouldBe "1/2"
        s.sdToggle().exact shouldBe "0.5"
        // The same input committed again: the typed fraction asks for a fraction.
        // If the toggle had leaked into Settings.exactForm this would stay "0.5".
        s.type("equals").exact shouldBe "1/2"
    }

    "the Ans chip is atomic — one press and one backspace" {
        val s = CalcSession()
        s.insertAns().entryText shouldBe "ans"
        s.type("backspace").entryText shouldBe ""
    }

    "S⇔D with nothing shown is a silent no-op (D-031.1)" {
        val s = CalcSession()
        val ui = s.sdToggle()
        ui.exact.shouldBeNull()
        ui.error.shouldBeNull()
        ui.entryText shouldBe ""
    }

    "one ⌫ hold-repeat step deletes exactly one thing (D-018)" {
        val s = CalcSession()
        s.type("1", "2", "3")
        s.backspaceRepeat().entryText shouldBe "12"
        s.backspaceRepeat().entryText shouldBe "1"
    }
})
