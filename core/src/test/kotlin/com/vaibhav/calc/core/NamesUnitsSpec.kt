package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.QuantityValue
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The `names-units` lens — the one the agent harness never ran (its agent died on
 * the spend limit). Done manually and empirically instead: assert the correct
 * behaviour of the identifier partition (D-016.4) and the unit layer, and let the
 * failures name the bugs.
 */
class NamesUnitsSpec : StringSpec({

    fun calc() = Calculator()

    fun Calculator.ok(input: String): Outcome = when (val r = route(input)) {
        is RouteResult.Ok -> r.outcome
        is RouteResult.Err -> throw AssertionError("expected Ok for '$input' but got: ${r.error.message}")
        is RouteResult.Ambiguous -> throw AssertionError("unexpected Ambiguous for '$input'")
    }

    fun Calculator.routeErr(input: String): EvalError = when (val r = route(input)) {
        is RouteResult.Err -> r.error
        else -> throw AssertionError("expected Err for '$input' but got: $r")
    }

    fun scalarReal(o: Outcome): Num = ((o as Outcome.Scalar).value as RealValue).num
    fun scalarQty(o: Outcome) = ((o as Outcome.Scalar).value as QuantityValue).q

    // --- the case-sensitive collisions the partition exists to prevent ---------
    "M is a register, m is the metre" {
        val c = calc()
        scalarQty(c.ok("5 m")).unit.name shouldBe "m"
        c.ok("5 → M")
        scalarReal(c.ok("2M")).compareTo(Num.of(10)) shouldBe 0
    }
    "E is a register, e is Euler's number" {
        val c = calc()
        // e must stay the constant even with E stored
        c.ok("5 → E")
        scalarReal(c.ok("2E")).compareTo(Num.of(10)) shouldBe 0
        val euler = scalarReal(c.ok("e"))
        (euler.compareTo(Num.ofDecimal("2.71")) > 0 && euler.compareTo(Num.ofDecimal("2.72")) < 0) shouldBe true
    }
    "x and X are the same unknown (case-insensitive, no trap)" {
        val roots = (calc().ok("X^2-5X+6=0") as Outcome.Roots).roots.map { (it as RealValue).num }
        roots.any { it.compareTo(Num.of(2)) == 0 } shouldBe true
        roots.any { it.compareTo(Num.of(3)) == 0 } shouldBe true
    }

    // --- reserved names cannot be assigned, and each names its collision -------
    "units, constants and unknowns are all unassignable" {
        calc().routeErr("5 → km").message shouldContain "unit"
        calc().routeErr("5 → m").message shouldContain "unit"
        calc().routeErr("5 → pi").message shouldContain "constant"
        calc().routeErr("5 → i").message shouldContain "constant"
        calc().routeErr("5 → x").message shouldContain "unknown"
        calc().routeErr("5 → z").message shouldContain "unknown"
    }
    "registers A–F and M are assignable" {
        listOf("A", "B", "C", "D", "E", "F", "M").forEach { r ->
            (calc().ok("7 → $r") as Outcome.Stored).register shouldBe r
        }
    }

    // --- the three-way namespace collision: min is a unit AND a function -------
    "min is the function when called, the unit when suffixed" {
        scalarReal(calc().ok("min(1,2)")).compareTo(Num.of(1)) shouldBe 0
        scalarQty(calc().ok("5 min")).unit.name shouldBe "min"
        scalarQty(calc().ok("2 h")).unit.dimension shouldBe scalarQty(calc().ok("5 min")).unit.dimension
    }
    "single-letter units that are not registers still work" {
        scalarQty(calc().ok("1 t")).unit.name shouldBe "t"
        scalarQty(calc().ok("5 K")).unit.name shouldBe "K"
        scalarQty(calc().ok("12 in")).unit.name shouldBe "in"
        scalarQty(calc().ok("3 g")).unit.name shouldBe "g"
        scalarQty(calc().ok("2 L")).unit.name shouldBe "L"
    }

    // --- the dimensionless demotion boundary ----------------------------------
    "dimensionless demotion: factor 1 demotes, scaled units stay quantities" {
        scalarReal(calc().ok("5 one")).compareTo(Num.of(5)) shouldBe 0
        scalarQty(calc().ok("5 dozen")).unit.name shouldBe "dozen"
        (calc().ok("5 dozen = 60") as Outcome.Verdict).holds shouldBe true
        (calc().ok("5 lakh = 500000") as Outcome.Verdict).holds shouldBe true
    }

    // --- no hidden state: identifiers mean the same thing regardless of storage -
    "storing cannot change what an identifier means" {
        val c = calc()
        c.ok("3 → A")
        // A substitutes everywhere, including inside an equation
        ((c.ok("2A + x = 10") as Outcome.Roots).roots[0] as RealValue).num.compareTo(Num.of(4)) shouldBe 0
        // x is an unknown before AND after any storage attempt
        c.routeErr("5 → x").message shouldContain "unknown"
        c.routeErr("2x").kind shouldBe EvalError.Kind.DOMAIN // no value outside an equation
    }

    // --- unit application guards ----------------------------------------------
    "a value cannot take two units" {
        calc().routeErr("5 km m").message shouldContain "already has a unit"
    }
    "unknown units are named, not guessed" {
        calc().routeErr("5 furlong").kind shouldBe EvalError.Kind.UNKNOWN_SYMBOL
    }
})
