package com.vaibhav.calc.core

import com.vaibhav.calc.core.eval.GroupingMode
import com.vaibhav.calc.core.eval.NotationMode
import com.vaibhav.calc.core.eval.NumeralSystem
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * **M-3 — Devanagari numeral OUTPUT (D-035).**
 *
 * The engine already *accepts* Devanagari input (D-028/D-030, measured: `१+२` is 3).
 * This is the output half, and it is the sibling of [GroupingMode]: an explicit
 * setting on an immutable object, never a global mutable default.
 *
 * **Digits only** — never operators, never unit names, never the stored value.
 */
class NumeralSystemSpec : StringSpec({

    val plain = Settings(notation = NotationMode.PLAIN)
    val dev = plain.copy(numerals = NumeralSystem.DEVANAGARI)

    // --- the same value, two numeral systems --------------------------------

    "D-035: the SAME value renders in Western and Devanagari digits" {
        val n = Num.of(1_234_567)
        Formatter.exact(n, plain) shouldBe "1234567"
        Formatter.exact(n, dev) shouldBe "१२३४५६७"
    }

    "D-035: every digit maps, 0 through 9" {
        Formatter.exact(Num.of(1_234_567_890L), dev) shouldBe "१२३४५६७८९०"
    }

    "D-035: the default is WESTERN — nothing changes unless asked" {
        Settings().numerals shouldBe NumeralSystem.WESTERN
        Formatter.exact(Num.of(42), Settings()) shouldBe "42"
    }

    // --- negatives and fractional parts --------------------------------------

    "D-035: negatives keep the sign and convert the digits" {
        Formatter.exact(Num.of(-1_234), dev) shouldBe "-१२३४"
    }

    "D-035: the fractional part converts too, and the decimal point does not" {
        Formatter.exact(Num.ofDecimal("1234.56"), dev) shouldBe "१२३४.५६"
        Formatter.exact(Num.ofDecimal("-0.75"), dev) shouldBe "-०.७५"
    }

    "D-035: a non-terminating rational stays a fraction, digits converted, slash intact" {
        Formatter.exact(Num.of(1, 3), dev) shouldBe "१/३"
    }

    // --- composition with BOTH grouping modes --------------------------------

    "D-035 × G-5: Indian grouping composes with Devanagari numerals" {
        val s = dev.copy(grouping = GroupingMode.INDIAN)
        // 12,34,567 with Devanagari digits — separators stay ASCII commas
        Formatter.exact(Num.of(1_234_567), s) shouldBe "१२,३४,५६७"
    }

    "D-035 × G-5: Western grouping composes with Devanagari numerals" {
        val s = dev.copy(grouping = GroupingMode.WESTERN)
        Formatter.exact(Num.of(1_234_567), s) shouldBe "१,२३४,५६७"
    }

    "D-035 × G-5: the two axes are INDEPENDENT — Indian grouping with Western digits" {
        val s = plain.copy(grouping = GroupingMode.INDIAN)
        Formatter.exact(Num.of(1_234_567), s) shouldBe "12,34,567"
    }

    "D-035 × G-5: grouping still skips the fractional part under Devanagari" {
        val s = dev.copy(grouping = GroupingMode.INDIAN)
        Formatter.exact(Num.ofDecimal("1234567.891"), s) shouldBe "१२,३४,५६७.८९१"
    }

    // --- what must NOT convert ------------------------------------------------

    "D-035: UNIT NAMES never convert — the value is Devanagari, the SI symbol is not" {
        val c = Calculator(dev)
        val q = c.quantityOf("15 m")
        val rendered = Formatter.exact(com.vaibhav.calc.core.eval.QuantityValue(q), dev)
        rendered shouldBe "१५ m"
        // and a unit whose NAME contains a digit keeps it: m2 stays m2
        val area = c.quantityOf("15 m2")
        Formatter.exact(com.vaibhav.calc.core.eval.QuantityValue(area), dev) shouldBe "१५ m2"
    }

    "D-035: operators and symbols never convert" {
        // surd, symbolic constant, and the imaginary unit keep their glyphs
        Formatter.exact(Calculator(dev).num("sqrt(8)"), dev) shouldBe "२√२"
        Formatter.exact(Calculator(dev).num("2*pi"), dev) shouldBe "२π"
    }

    "D-035: a quantity coefficient is not wrongly parenthesised under Devanagari" {
        // `coefficient()` asks "is this a bare number?" with a regex. An ASCII-only
        // \d would have failed on १५ and wrapped it as (१५) m — the exact bug this
        // pins. \p{Nd} keeps the question numeral-system agnostic.
        val c = Calculator(dev)
        val q = c.quantityOf("15 m")
        Formatter.exact(com.vaibhav.calc.core.eval.QuantityValue(q), dev).startsWith("(") shouldBe false
    }

    // --- presentation only: the VALUE never moves ----------------------------

    "D-035: the numeral system changes the STRING and never the VALUE" {
        val western = Calculator(plain)
        val devanagari = Calculator(dev)
        val a = western.num("1234567+1")
        val b = devanagari.num("1234567+1")
        a.compareTo(b) shouldBe 0
        a.compareTo(Num.of(1_234_568)) shouldBe 0
        // only the rendering differs
        Formatter.exact(a, plain) shouldNotBe Formatter.exact(a, dev)
    }

    "D-035: round-trip — Devanagari OUT re-parses to the same value" {
        // The engine accepts Devanagari input (D-028/D-030), so its own Devanagari
        // output is readable back. That closes the loop the Western path already has.
        val n = Num.of(98_765)
        val rendered = Formatter.exact(n, dev)
        rendered shouldBe "९८७६५"
        Calculator().num(rendered).compareTo(n) shouldBe 0
    }

    "D-035: transliteration is idempotent — already-Devanagari digits are untouched" {
        // `exact` may delegate to `decimal` internally; a second pass must not corrupt.
        val once = Formatter.exact(Num.ofDecimal("12.5"), dev)
        once shouldBe "१२.५"
        Formatter.exact(Calculator().num(once), dev) shouldBe "१२.५"
    }

    "D-035: scientific notation converts its digits, keeping the exponent marker" {
        // `decimal`, not `exact`: NotationMode governs the decimal renderer, while
        // `exact` of an integer Rational is the full positional expansion (12,346
        // digits here) and never scientific. Same call GroupingSpec uses.
        val sci = dev.copy(notation = NotationMode.SCIENTIFIC)
        Formatter.decimal(Num.ofDecimal("1e12345"), sci) shouldBe "१e१२३४५"
        // the exponent's SIGN survives too
        Formatter.decimal(Num.ofDecimal("1e-12345"), sci) shouldBe "१e-१२३४५"
    }
})
