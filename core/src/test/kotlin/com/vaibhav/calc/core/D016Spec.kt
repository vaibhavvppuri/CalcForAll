package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.QuantityValue
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import com.vaibhav.calc.core.units.Dimension
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** D-016 — the invocation layer: equations and units inline, routed, no modes. */
class D016Spec : StringSpec({

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

    fun rootsOf(o: Outcome): List<Num> =
        (o as Outcome.Roots).roots.map { (it as RealValue).num }

    // --- the thesis: type the equation, get roots (no mode) ------------------
    "x²-5x+6=0 gives roots 2 and 3" {
        val o = calc().ok("x²-5x+6=0")
        (o as Outcome.Roots).unknown shouldBe "x"
        val rs = rootsOf(o)
        rs.any { it.compareTo(Num.of(2)) == 0 } shouldBe true
        rs.any { it.compareTo(Num.of(3)) == 0 } shouldBe true
        o.steps.isNotEmpty() shouldBe true
    }
    "ASCII caret form works identically" {
        rootsOf(calc().ok("x^2-5x+6=0")).size shouldBe 2
    }
    "linear 2x+3=7 gives x = 2" {
        rootsOf(calc().ok("2x+3=7")).single().compareTo(Num.of(2)) shouldBe 0
    }

    // --- D-016.2: zero unknowns is a truth verdict ---------------------------
    "2+2=4 is Verdict(true); 2+2=5 is Verdict(false)" {
        (calc().ok("2+2=4") as Outcome.Verdict).holds shouldBe true
        (calc().ok("2+2=5") as Outcome.Verdict).holds shouldBe false
    }

    // --- D-016.1: `=` is an equation, `→` stores -----------------------------
    "x = 5 is an EQUATION, not an assignment" {
        rootsOf(calc().ok("x = 5")).single().compareTo(Num.of(5)) shouldBe 0
    }
    "5 → A stores, and A is then usable" {
        val c = calc()
        (c.ok("5 → A") as Outcome.Stored).register shouldBe "A"
        ((c.ok("2A") as Outcome.Scalar).value as RealValue).num.compareTo(Num.of(10)) shouldBe 0
    }
    "-> and sto are accepted aliases" {
        val c1 = calc(); (c1.ok("5 -> A") as Outcome.Stored).register shouldBe "A"
        val c2 = calc(); (c2.ok("5 sto B") as Outcome.Stored).register shouldBe "B"
    }
    "assigning to a reserved name names the collision" {
        calc().routeErr("5 → x").message shouldContain "unknown"
        calc().routeErr("5 → km").message shouldContain "unit"
        calc().routeErr("5 → pi").message shouldContain "constant"
        calc().routeErr("5 → q").message shouldContain "register"
    }
    "registers substitute inside equations: A=3, 2A+x=10 → x=4" {
        val c = calc()
        c.ok("3 → A")
        rootsOf(c.ok("2A + x = 10")).single().compareTo(Num.of(4)) shouldBe 0
    }

    // --- D-016.3: NEWLINE is the system separator ----------------------------
    "a 2x2 system on two lines" {
        val s = calc().ok("x+y=5\nx-y=1") as Outcome.SystemSolution
        s.unknowns shouldBe listOf("x", "y")
        s.values[0].compareTo(Num.of(3)) shouldBe 0
        s.values[1].compareTo(Num.of(2)) shouldBe 0
    }
    "';' is a tolerated alias for newline" {
        val s = calc().ok("x+y=5;x-y=1") as Outcome.SystemSolution
        s.values[0].compareTo(Num.of(3)) shouldBe 0
    }
    "a 3x3 system on three lines" {
        val s = calc().ok("x+y+z=6\nx-y+z=2\n2x+y+z=7") as Outcome.SystemSolution
        s.values[0].compareTo(Num.of(1)) shouldBe 0
        s.values[1].compareTo(Num.of(2)) shouldBe 0
        s.values[2].compareTo(Num.of(3)) shouldBe 0
    }

    // --- D-016.6: never silently pick an unknown -----------------------------
    "x+y=5 alone is underdetermined and names both unknowns" {
        val m = calc().routeErr("x+y=5").message
        m shouldContain "underdetermined"
        m shouldContain "x"
        m shouldContain "y"
    }
    "identity and no-solution are distinct outcomes" {
        (calc().ok("x = x") is Outcome.Identity) shouldBe true
        (calc().ok("x = x + 1") is Outcome.NoSolution) shouldBe true
    }
    "non-polynomial and high-degree equations are typed errors, not guesses" {
        calc().routeErr("sin(x) = 0").message shouldContain "cannot solve"
        calc().routeErr("x^3 = 8").message shouldContain "degree"
        calc().routeErr("x*y=6\nx+y=5").message shouldContain "linear"
    }

    // --- units inline (D-016) ------------------------------------------------
    "5 km + 300 m = 5.3 km, keeping the left unit" {
        val q = ((calc().ok("5 km + 300 m") as Outcome.Scalar).value as QuantityValue).q
        q.unit.name shouldBe "km"
        q.value.compareTo(Num.of(53, 10)) shouldBe 0
    }
    "scaling preserves the unit: 3 * 5 km = 15 km" {
        val q = ((calc().ok("3 * 5 km") as Outcome.Scalar).value as QuantityValue).q
        q.unit.name shouldBe "km"
        q.value.compareTo(Num.of(15)) shouldBe 0
    }
    "5 m / 1 s is a speed" {
        val q = ((calc().ok("5 m/s") as Outcome.Scalar).value as QuantityValue).q
        q.unit.dimension shouldBe Dimension.SPEED
        q.value.compareTo(Num.of(5)) shouldBe 0
    }
    "affine units are not silently scaled: 20 degC stays 20 degC" {
        val q = ((calc().ok("20 degC") as Outcome.Scalar).value as QuantityValue).q
        q.unit.name shouldBe "degC"
        q.value.compareTo(Num.of(20)) shouldBe 0
    }
    "dimensionless quantities demote to plain numbers: 3 + 5 lakh = 500003" {
        val v = (calc().ok("3 + 5 lakh") as Outcome.Scalar).value as RealValue
        v.num.compareTo(Num.of(500003)) shouldBe 0
    }
    "adding incompatible dimensions inline is a typed error" {
        calc().routeErr("5 km + 3 kg").message shouldContain "dimension"
    }

    // Regression (D-016 review, confirmed): a dimensionless unit must compare
    // equal to a plain number. This returned Verdict(false) — a confident WRONG
    // answer — on exactly the Indian-units edge (spec 9.6).
    "dimensionless units equal plain numbers: 5 lakh = 500000 is TRUE" {
        (calc().ok("5 lakh = 500000") as Outcome.Verdict).holds shouldBe true
        (calc().ok("500000 = 5 lakh") as Outcome.Verdict).holds shouldBe true
        (calc().ok("2 crore = 20000000") as Outcome.Verdict).holds shouldBe true
        (calc().ok("1 dozen = 12") as Outcome.Verdict).holds shouldBe true
    }
    "but a dimensioned quantity still never equals a bare number" {
        (calc().ok("5 km = 5") as Outcome.Verdict).holds shouldBe false
    }
    "cross-unit and affine comparisons still hold" {
        (calc().ok("100 cm = 1 m") as Outcome.Verdict).holds shouldBe true
        (calc().ok("0 degC = 273.15 K") as Outcome.Verdict).holds shouldBe true
        (calc().ok("1 lakh = 2 lakh") as Outcome.Verdict).holds shouldBe false
    }
})
