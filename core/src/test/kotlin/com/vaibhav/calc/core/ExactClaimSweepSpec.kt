package com.vaibhav.calc.core

import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * **The sweep** (owner ruling, after `sin(45) = √2/2` answered FALSE).
 *
 * *"If sin(45) lies, find its siblings — cos, tan, the surd comparisons, everything
 * that leaves the exact domain and comes back as Dec."*
 *
 * Every case here is a statement that is **true in mathematics**, written the way a
 * student writes it. The engine must not deny any of them. This is the regression
 * wall for the whole class of bug: an exact value leaves the exact domain, returns as
 * a truncated [com.vaibhav.calc.core.num.Dec], and is then compared against an exact
 * form it can no longer equal.
 *
 * A `Verdict(false)` here is never a test failure about formatting or style — it is
 * the engine telling a student that something true is false.
 */
class ExactClaimSweepSpec : StringSpec({

    fun verdict(input: String, settings: Settings = Settings()): Boolean {
        val r = com.vaibhav.calc.core.route.ExpressionRouter(settings, Environment()).route(input)
        return when (r) {
            is RouteResult.Ok -> when (val o = r.outcome) {
                is Outcome.Verdict -> o.holds
                else -> throw AssertionError("'$input' did not produce a verdict: $o")
            }
            is RouteResult.Err -> throw AssertionError("'$input' errored: ${r.error}")
            is RouteResult.Ambiguous -> throw AssertionError("'$input' was ambiguous")
        }
    }

    // --- the original bug, and every sibling angle -----------------------------

    "THE BUG: sin(45) = √2/2 is TRUE" { verdict("sin(45)=sqrt(2)/2") shouldBe true }
    "cos(45) = √2/2 is TRUE" { verdict("cos(45)=sqrt(2)/2") shouldBe true }
    "sin(60) = √3/2 is TRUE" { verdict("sin(60)=sqrt(3)/2") shouldBe true }
    "cos(30) = √3/2 is TRUE" { verdict("cos(30)=sqrt(3)/2") shouldBe true }
    "tan(60) = √3 is TRUE" { verdict("tan(60)=sqrt(3)") shouldBe true }
    "tan(30) = 1/√3 is TRUE" { verdict("tan(30)=1/sqrt(3)") shouldBe true }
    "sin(30) = 1/2 is TRUE (it always was — but by luck, not by design)" {
        verdict("sin(30)=(1)/(2)") shouldBe true
    }
    "sin(135) = √2/2 is TRUE — the second quadrant is not a special case" {
        verdict("sin(135)=sqrt(2)/2") shouldBe true
    }
    "cos(120) = −1/2 is TRUE" { verdict("cos(120)=-(1)/(2)") shouldBe true }
    "sin(210) = −1/2 is TRUE" { verdict("sin(210)=-(1)/(2)") shouldBe true }
    "sin(0) = 0, cos(0) = 1, tan(0) = 0" {
        verdict("sin(0)=0") shouldBe true
        verdict("cos(0)=1") shouldBe true
        verdict("tan(0)=0") shouldBe true
    }
    "sin(90) = 1 and cos(180) = −1" {
        verdict("sin(90)=1") shouldBe true
        verdict("cos(180)=-1") shouldBe true
    }

    // --- the Pythagorean identity: the strongest single check -----------------

    "sin²θ + cos²θ = 1 at EVERY standard angle" {
        for (d in listOf(0, 30, 45, 60, 90, 120, 135, 150, 180, 210, 225, 240, 270, 300, 315, 330)) {
            verdict("sin($d)^2+cos($d)^2=1") shouldBe true
        }
    }

    // --- RAD mode reaches the same table ---------------------------------------

    "the fast path is not DEG-only: sin(π/4) = √2/2 in RAD" {
        val rad = Settings(angle = com.vaibhav.calc.core.eval.AngleMode.RAD)
        verdict("sin(pi/4)=sqrt(2)/2", rad) shouldBe true
        verdict("sin(pi/6)=(1)/(2)", rad) shouldBe true
        verdict("cos(pi/3)=(1)/(2)", rad) shouldBe true
    }

    "GRAD reaches it too: sin(50 grad) = √2/2" {
        val grad = Settings(angle = com.vaibhav.calc.core.eval.AngleMode.GRAD)
        verdict("sin(50)=sqrt(2)/2", grad) shouldBe true
    }

    // --- the inverse direction: does it come back exact? -----------------------

    "asin(1/2) = 30, acos(0) = 90, atan(1) = 45" {
        verdict("asin((1)/(2))=30") shouldBe true
        verdict("acos(0)=90") shouldBe true
        verdict("atan(1)=45") shouldBe true
    }
    "asin(√2/2) = 45 — the inverse of THE bug" {
        verdict("asin(sqrt(2)/2)=45") shouldBe true
    }
    "round trip: asin(sin(30)) = 30" { verdict("asin(sin(30))=30") shouldBe true }

    // --- siblings OUTSIDE trig: everything that leaves exact and returns Dec ----

    "ln(e) = 1 and ln(1) = 0" {
        verdict("ln(e)=1") shouldBe true
        verdict("ln(1)=0") shouldBe true
    }
    "exp(0) = 1" { verdict("exp(0)=1") shouldBe true }
    "log(100) = 2 and log2(8) = 3 — integer logs stay exact" {
        verdict("log(100)=2") shouldBe true
        verdict("log2(8)=3") shouldBe true
        verdict("log10(1000)=3") shouldBe true
    }
    "sqrt(8) = 2√2 and sqrt(9) = 3" {
        verdict("sqrt(8)=2sqrt(2)") shouldBe true
        verdict("sqrt(9)=3") shouldBe true
    }
    "sqrt(2)*sqrt(2) = 2 — the surd collapses back exactly" {
        verdict("sqrt(2)*sqrt(2)=2") shouldBe true
    }
    "cbrt(8) = 2" { verdict("cbrt(8)=2") shouldBe true }
    "0.1+0.2 = 0.3 — Article 3's acceptance test" { verdict("0.1+0.2=0.3") shouldBe true }
    "(1/3)*3 = 1" { verdict("((1)/(3))*3=1") shouldBe true }
    "sinh(0) = 0, cosh(0) = 1, tanh(0) = 0" {
        verdict("sinh(0)=0") shouldBe true
        verdict("cosh(0)=1") shouldBe true
        verdict("tanh(0)=0") shouldBe true
    }
    "π and e stay symbolic through arithmetic: 2π/2 = π" {
        verdict("2pi/2=pi") shouldBe true
        verdict("tau=2pi") shouldBe true
    }
    "5 km + 300 m = 5.3 km — units compare across forms" {
        verdict("5 km + 300 m = 5.3 km") shouldBe true
    }
    "2 crore + 5 lakh = 205 lakh" { verdict("2 crore + 5 lakh = 205 lakh") shouldBe true }
})
