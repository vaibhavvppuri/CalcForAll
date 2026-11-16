package com.vaibhav.calc.core

import com.vaibhav.calc.core.eval.ExactForm
import com.vaibhav.calc.core.eval.GroupingMode
import com.vaibhav.calc.core.eval.NotationMode
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * **G-5 — digit grouping, both modes.**
 *
 * The design ships grouping as a first-class user toggle: *"Indian grouping,
 * native — 12,34,567 — three from the right, then pairs. Flip GROUPING up top and
 * the same engine regroups to 1,234,567."* That last clause is the requirement
 * this spec enforces: **the same engine**, one rule in one place, so the tape, the
 * graph tick labels, the matrix cells and the unit browser cannot each grow their
 * own copy (Article 12).
 *
 * **Grouping is presentation and nothing else.** It changes a string; it never
 * touches a value. The default is [GroupingMode.NONE], which is what keeps the
 * engine's own render→parse loop intact — a grouped string contains commas, and a
 * comma is an argument separator in this grammar, so grouped output is
 * deliberately NOT round-trip input. That boundary is asserted below rather than
 * left for someone to discover.
 */
class GroupingSpec : StringSpec({

    val plain = Settings(notation = NotationMode.PLAIN)
    val indian = plain.copy(grouping = GroupingMode.INDIAN)
    val western = plain.copy(grouping = GroupingMode.WESTERN)

    fun exact(n: Num, s: Settings) = Formatter.exact(n, s)

    // --- the design's own example, both ways --------------------------------

    "the SAME number renders 12,34,567 (Indian) and 1,234,567 (Western)" {
        val n = Num.of(1_234_567)
        exact(n, indian) shouldBe "12,34,567"
        exact(n, western) shouldBe "1,234,567"
    }

    "Indian grouping is three from the right, then pairs" {
        exact(Num.of(12_345_678), indian) shouldBe "1,23,45,678"
        exact(Num.of(100_000), indian) shouldBe "1,00,000"          // one lakh
        exact(Num.of(10_000_000), indian) shouldBe "1,00,00,000"    // one crore
    }

    "Western grouping is threes all the way" {
        exact(Num.of(12_345_678), western) shouldBe "12,345,678"
        exact(Num.of(100_000), western) shouldBe "100,000"
        exact(Num.of(10_000_000), western) shouldBe "10,000,000"
    }

    // --- boundaries ---------------------------------------------------------

    "numbers too small to need a separator get none, in either mode" {
        for (v in listOf(0L, 7L, 42L, 999L)) {
            exact(Num.of(v), indian) shouldBe v.toString()
            exact(Num.of(v), western) shouldBe v.toString()
        }
        // 1000 is the first value that separates, and both modes agree there
        exact(Num.of(1000), indian) shouldBe "1,000"
        exact(Num.of(1000), western) shouldBe "1,000"
    }

    "negatives group the digits and leave the sign alone" {
        exact(Num.of(-1_234_567), indian) shouldBe "-12,34,567"
        exact(Num.of(-1_234_567), western) shouldBe "-1,234,567"
        exact(Num.of(-999), indian) shouldBe "-999"
    }

    "the FRACTIONAL part is never grouped — only the integer part is" {
        // 1234567.891: the integer side groups, the decimals do not.
        val n = Num.ofDecimal("1234567.891")
        exact(n, indian) shouldBe "12,34,567.891"
        exact(n, western) shouldBe "1,234,567.891"
        // a long fractional run stays one unbroken block
        exact(Num.ofDecimal("1000.123456"), western) shouldBe "1,000.123456"
    }

    "EXPONENTS are never grouped" {
        val sci = Settings(notation = NotationMode.SCIENTIFIC, grouping = GroupingMode.INDIAN)
        // The exponent is a count, not a magnitude. A 5-digit one would visibly
        // group as `1e12,345` if the separator leaked into it — and that string is
        // not even a number any more.
        Formatter.decimal(Num.ofDecimal("1e12345"), sci) shouldBe "1e12345"
        Formatter.decimal(Num.ofDecimal("1e-12345"), sci) shouldBe "1e-12345"
    }

    "very large exact-tower integers group correctly" {
        // 20! = 2432902008176640000 — an exact Rational far past Long, straight
        // from the tower. Both expected strings are written out longhand: deriving
        // them with the same chunking the formatter uses would be a tautology that
        // passes even when the implementation is wrong.
        val f20 = Calculator().num("20!")
        f20.compareTo(Num.of(2_432_902_008_176_640_000L)) shouldBe 0
        exact(f20, western) shouldBe "2,432,902,008,176,640,000"
        exact(f20, indian) shouldBe "24,32,90,20,08,17,66,40,000"
    }

    // --- grouping is presentation, never value ------------------------------

    "grouping changes the STRING and never the VALUE" {
        val n = Num.of(1_234_567)
        // same Num object, three renderings, one value
        val a = exact(n, plain)
        val b = exact(n, indian)
        val c = exact(n, western)
        a shouldBe "1234567"
        b shouldNotBe a
        c shouldNotBe a
        // and the value the engine computes with is untouched by the setting
        val viaIndian = Calculator(indian).num("1234567+0")
        val viaWestern = Calculator(western).num("1234567+0")
        viaIndian.compareTo(viaWestern) shouldBe 0
        viaIndian.compareTo(Num.of(1_234_567)) shouldBe 0
    }

    "the default is NONE — ungrouped, so the engine can still re-read its own output" {
        Settings().grouping shouldBe GroupingMode.NONE
        exact(Num.of(1_234_567), Settings()) shouldBe "1234567"
        // Article 12's round-trip holds on the default path: this string parses back.
        Calculator().num("1234567").compareTo(Num.of(1_234_567)) shouldBe 0
    }

    // --- interaction with D-025 (S⇔D) --------------------------------------

    "D-025 decides WHICH form; grouping only decorates the digits of that form" {
        // A terminating rational renders as the decimal (D-025), and the integer
        // part of that decimal groups.
        val terminating = Num.of(2_500_000, 2) // 1,250,000 exactly
        exact(terminating, indian) shouldBe "12,50,000"
        exact(terminating, western) shouldBe "1,250,000"
    }

    "a fraction stays a ratio: n/d is NOT grouped, in either mode" {
        // D-025 keeps a non-terminating rational as a fraction because the fraction
        // IS the exact form there. A ratio is not a positional magnitude the eye
        // scans in lakhs, so the separator has no job inside one — and `1,23,456/7`
        // would be actively misleading. Stated as a decision, not an oversight.
        val third = Num.of(1_234_567, 3)
        exact(third, indian) shouldBe "1234567/3"
        exact(third, western) shouldBe "1234567/3"
    }

    "forcing the fraction with S⇔D also leaves it ungrouped" {
        val s = indian.copy(exactForm = ExactForm.FRACTION)
        Formatter.exact(Num.of(1_234_567, 2), s) shouldBe "1234567/2"
    }
})
