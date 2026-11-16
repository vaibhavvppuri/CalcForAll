package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.QuantityValue
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.Rational
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import com.vaibhav.calc.core.units.Dimension
import com.vaibhav.calc.core.units.Quantity
import com.vaibhav.calc.core.units.Units
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * **G-4 — the six units the design's browser teaches that the engine did not speak.**
 *
 * `ac`, `guntha`, `maund`, `seer`, `ly`, `nmi`. The gap analysis put it sharply:
 * §09's job is *"learning that 'crore' and 'ly' and 'tola' are words the engine
 * parses. Once you know, you type them."* A browser that teaches a word the engine
 * answers `UnknownSymbol` to inverts the section's purpose.
 *
 * **Every factor here is exact.** Each is a rational number, so each is defined in
 * the tower as one — no `Double` anywhere, per Article 3. The relations that make
 * them *mean* something (an acre is 4840 square yards, a guntha is 1/40 acre, a
 * maund is 40 seer) are built structurally rather than asserted after the fact, so
 * they cannot drift apart; the tests below prove the arithmetic lands where the
 * statute says it does.
 */
class G4UnitsSpec : StringSpec({

    val c = Calculator()

    fun cv(v: String, from: String, to: String): Num =
        (c.convert(Num.ofDecimal(v), from, to) as CalcResult.Ok).value

    fun inline(expr: String): String {
        val out = (c.route(expr) as RouteResult.Ok).outcome as Outcome.Scalar
        return Formatter.exact(out.value, c.settings)
    }

    fun quantityOf(expr: String): Quantity {
        val out = (c.route(expr) as RouteResult.Ok).outcome as Outcome.Scalar
        return (out.value as QuantityValue).q
    }

    // --- all six exist and carry the right dimension ------------------------

    "all six units the design's browser teaches now exist" {
        for (n in listOf("ac", "guntha", "maund", "seer", "ly", "nmi")) {
            Units.get(n) shouldNotBe null
        }
        Units.get("ac")!!.dimension shouldBe Dimension.AREA
        Units.get("guntha")!!.dimension shouldBe Dimension.AREA
        Units.get("maund")!!.dimension shouldBe Dimension.MASS
        Units.get("seer")!!.dimension shouldBe Dimension.MASS
        Units.get("ly")!!.dimension shouldBe Dimension.LENGTH
        Units.get("nmi")!!.dimension shouldBe Dimension.LENGTH
    }

    "`ac` is the symbol for the acre already in the registry — one unit, two spellings" {
        // Not a second definition: aliasing is what stops the two drifting apart.
        Units.get("ac") shouldBe Units.get("acre")
    }

    // --- every factor is EXACT: a Rational, never a decimal fallback --------

    "every new factor is an exact Rational — no floating point anywhere" {
        for (n in listOf("ac", "guntha", "maund", "seer", "ly", "nmi")) {
            (Units.get(n)!!.factor is Rational) shouldBe true
        }
    }

    "the statutory values are exact to the last digit" {
        cv("1", "ac", "m2").compareTo(Num.ofDecimal("4046.8564224")) shouldBe 0
        cv("1", "guntha", "m2").compareTo(Num.ofDecimal("101.17141056")) shouldBe 0
        cv("1", "nmi", "m").compareTo(Num.of(1852)) shouldBe 0
        cv("1", "seer", "kg").compareTo(Num.ofDecimal("0.9331")) shouldBe 0
        cv("1", "maund", "kg").compareTo(Num.ofDecimal("37.3240")) shouldBe 0
        cv("1", "ly", "m").compareTo(Num.of(9_460_730_472_580_800L)) shouldBe 0
    }

    // --- the defining relations, verified rather than trusted ---------------

    "a light-year IS 299792458 m/s × 86400 s × 365.25 days — verified as a product" {
        val c0 = Num.of(299_792_458)
        val secondsPerDay = Num.of(86_400)
        val daysPerJulianYear = Num.ofDecimal("365.25")
        val product = c0 * secondsPerDay * daysPerJulianYear
        product.compareTo(Num.of(9_460_730_472_580_800L)) shouldBe 0
        cv("1", "ly", "m").compareTo(product) shouldBe 0
    }

    "an acre is 4840 square yards and a guntha is 121 — the yard is the shared root" {
        val sqYard = Num.ofDecimal("0.9144") * Num.ofDecimal("0.9144")
        cv("1", "ac", "m2").compareTo(Num.of(4840) * sqYard) shouldBe 0
        cv("1", "guntha", "m2").compareTo(Num.of(121) * sqYard) shouldBe 0
    }

    "40 guntha == 1 acre EXACTLY — no floating-point drift" {
        // The headline exactness proof. In binary floating point this would land a
        // few ulps off; in the tower the difference is exactly zero.
        val fortyGuntha = cv("40", "guntha", "m2")
        val oneAcre = cv("1", "ac", "m2")
        (fortyGuntha - oneAcre).isZero shouldBe true
        fortyGuntha.compareTo(oneAcre) shouldBe 0
        cv("40", "guntha", "ac").compareTo(Num.ONE) shouldBe 0
        cv("1", "ac", "guntha").compareTo(Num.of(40)) shouldBe 0
    }

    "D-030: 1 maund − 40 seer == 0 exactly" {
        // A maund IS 40 seer — a plain definition every source agrees on, made
        // structural so this is zero by construction rather than by a lucky
        // rounding. (D-029 framed it as resolving a 0.2 g conflict with the 1956
        // Act; D-030 withdrew that as unsupported. The arithmetic is unchanged.)
        val maundKg = cv("1", "maund", "kg")
        val fortySeerKg = cv("40", "seer", "kg")
        (maundKg - fortySeerKg).isZero shouldBe true
        cv("1", "maund", "seer").compareTo(Num.of(40)) shouldBe 0
    }

    "D-030: the seer is the TABULATED 80-tola value, ~4.3 mg below the 80-tola product" {
        // The one real imprecision here, pinned with its true size so it cannot be
        // misremembered — and deliberately in MILLIgrams: 80 × 11.6638038 g =
        // 933.104304 g against a tabulated 933.10 g, a difference of 0.004304 g.
        // That is 4304 µg; calling it "micrograms" would be off by 1000×.
        val bisTola = Num.ofDecimal("11.6638038")          // grams
        val eightyTolas = Num.of(80) * bisTola             // 933.104304 g
        val tabulatedSeer = cv("1", "seer", "kg") * Num.of(1000) // 933.10 g
        eightyTolas.compareTo(Num.ofDecimal("933.104304")) shouldBe 0
        (eightyTolas - tabulatedSeer).compareTo(Num.ofDecimal("0.004304")) shouldBe 0
        // and it is NOT zero — the registered seer is the published figure, not the
        // product, which is the choice D-030 records
        (eightyTolas - tabulatedSeer).isZero shouldBe false
    }

    "D-030: the metric seer of 1.25 kg is NOT implemented — a known absence" {
        // Sources give the 1956 Act's metric seer as exactly 1.25 kg. This build
        // ships only the traditional 80-tola seer, so `seer` must not silently be
        // the metric one. Asserted so the absence stays a recorded state.
        cv("1", "seer", "kg").compareTo(Num.ofDecimal("1.25")) shouldNotBe 0
        cv("1", "seer", "kg").compareTo(Num.ofDecimal("0.9331")) shouldBe 0
    }

    // --- same-unit folds ----------------------------------------------------

    "same-unit folds work for each new unit" {
        // `ac` is an ALIAS, so it renders under the canonical name `acre` — the same
        // established behaviour as `h` rendering back as `hr`. Round-trip safe,
        // because `acre` parses; pinned below in its own test.
        inline("2 ac + 3 ac") shouldBe "5 acre"
        inline("2 acre + 3 acre") shouldBe "5 acre"
        inline("2 guntha + 3 guntha") shouldBe "5 guntha"
        inline("2 maund + 3 maund") shouldBe "5 maund"
        inline("2 seer + 3 seer") shouldBe "5 seer"
        inline("2 ly + 3 ly") shouldBe "5 ly"
        inline("2 nmi + 3 nmi") shouldBe "5 nmi"
    }

    // --- cross-unit folds within a dimension --------------------------------

    "cross-unit folds keep the left operand's unit" {
        // 1 acre + 40 guntha = 2 acre (a guntha is exactly 1/40 acre)
        inline("1 ac + 40 guntha") shouldBe "2 acre"
        // 1 maund + 40 seer = 2 maund
        inline("1 maund + 40 seer") shouldBe "2 maund"
        // 1 seer + 933.1 g = 2 seer  (933.1 g = 0.9331 kg = 1 seer)
        inline("1 seer + 933.1 g") shouldBe "2 seer"
        // 1 nmi + 1852 m = 2 nmi
        inline("1 nmi + 1852 m") shouldBe "2 nmi"
        // 1 nmi in km — 1.852 km exactly
        cv("1", "nmi", "km").compareTo(Num.ofDecimal("1.852")) shouldBe 0
        // guntha → acre
        cv("20", "guntha", "ac").compareTo(Num.of(1, 2)) shouldBe 0
        // seer → kg
        cv("2", "seer", "kg").compareTo(Num.ofDecimal("1.8662")) shouldBe 0
    }

    "a light-year is a LENGTH and folds with metres" {
        val q = quantityOf("1 ly + 1852 m")
        q.unit.name shouldBe "ly"
        // exactly 1 + 1852/9460730472580800 light-years — an exact rational, and
        // the tower keeps every digit of it
        val expected = Num.ONE + Num.of(1852L, 9_460_730_472_580_800L)
        q.value.compareTo(expected) shouldBe 0
    }

    // --- dimensional analysis still refuses the impossible ------------------

    "dimension mismatches involving the new units are still typed errors" {
        for (bad in listOf("1 ac + 1 kg", "1 seer + 1 m", "1 ly + 1 maund", "1 nmi + 1 guntha")) {
            val r = c.route(bad)
            (r is RouteResult.Err) shouldBe true
            (r as RouteResult.Err).error.kind shouldBe EvalError.Kind.DOMAIN
        }
    }

    "converting across dimensions is still a typed DOMAIN error, never a number" {
        (c.convert(Num.ONE, "seer", "m") as CalcResult.Err).error.kind shouldBe EvalError.Kind.DOMAIN
        (c.convert(Num.ONE, "ly", "kg") as CalcResult.Err).error.kind shouldBe EvalError.Kind.DOMAIN
    }

    // --- the vocabulary is reachable the way a user reaches it --------------

    "an alias renders under its canonical name, and that output re-parses (Article 12)" {
        // `ac` in, `acre` out. The engine must be able to re-read what it printed,
        // and it can — this is the same rule `h` → `hr` already follows.
        val rendered = inline("2 ac + 3 ac")
        rendered shouldBe "5 acre"
        inline(rendered).let { it shouldBe "5 acre" }
    }

    "the new names are reserved, so they cannot be shadowed by a register" {
        for (n in listOf("ac", "guntha", "maund", "seer", "ly", "nmi")) {
            com.vaibhav.calc.core.name.ReservedNames.isUnit(n) shouldBe true
        }
    }
})
