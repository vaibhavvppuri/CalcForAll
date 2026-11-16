package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.QuantityValue
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Regression tests for the D-016 adversarial review findings. Each asserts the
 * CORRECT behaviour, so a failure here is a confirmed bug.
 */
class D016ReviewSpec : StringSpec({

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

    fun rootsOf(o: Outcome): List<Num> = (o as Outcome.Roots).roots.map { (it as RealValue).num }

    // #3 — the public API must never throw. Contextual percent on an affine unit
    // computed its factor OUTSIDE the span guard, so the ArithFault escaped.
    "evaluate never throws on contextual percent over an affine unit" {
        shouldNotThrowAny {
            calc().evaluate("1 + 20 degC%")
            calc().evaluate("10 - 5 degF%")
            calc().evaluateAnswer("1 + 20 degC%")
            calc().route("1 + 20 degC%")
        }
        calc().evaluate("1 + 20 degC%").isOk shouldBe false
    }

    // #8 — the extractor must apply the SAME contextual-percent rule the evaluator
    // does, or it silently produces a wrong root (D-014's failure mode).
    "contextual percent in an equation gives the right root: x + 10% = 55 → x = 50" {
        rootsOf(calc().ok("x + 10% = 55")).single().compareTo(Num.of(50)) shouldBe 0
        rootsOf(calc().ok("x - 10% = 180")).single().compareTo(Num.of(200)) shouldBe 0
    }

    // #7 — `0x` with no hex digits must backtrack to 0·x, not throw.
    "0x is zero times x, not a malformed hex literal" {
        (calc().ok("0x = 0") is Outcome.Identity) shouldBe true
        val rs = rootsOf(calc().ok("x^2 + 0x - 4 = 0"))
        rs.any { it.compareTo(Num.of(2)) == 0 } shouldBe true
        rs.any { it.compareTo(Num.of(-2)) == 0 } shouldBe true
        // real hex literals still work
        calc().num("0xFF").compareTo(Num.of(255)) shouldBe 0
    }

    // #5 — a unit suffix must not bind inside an exponent: 10^3 kg = 1000 kg.
    "unit suffixes do not bind inside exponents" {
        val q = ((calc().ok("10^3 kg") as Outcome.Scalar).value as QuantityValue).q
        q.unit.name shouldBe "kg"
        q.value.compareTo(Num.of(1000)) shouldBe 0
        val q2 = ((calc().ok("2^10 m") as Outcome.Scalar).value as QuantityValue).q
        q2.value.compareTo(Num.of(1024)) shouldBe 0
    }

    // #6 — `5 m²` must work, and the engine's own output must be re-parseable.
    "superscripted units parse, and rendered output round-trips" {
        val q = ((calc().ok("5 m²") as Outcome.Scalar).value as QuantityValue).q
        q.value.compareTo(Num.of(5)) shouldBe 0
        // round-trip: the engine renders an area, and can read its own rendering
        val c = calc()
        val rendered = c.decimal((c.ok("5 m * 3 m") as Outcome.Scalar).value)
        val reparsed = ((c.ok(rendered) as Outcome.Scalar).value as QuantityValue).q
        reparsed.toBase().compareTo(Num.of(15)) shouldBe 0
    }

    // #4 — a real arithmetic/name error inside an equation must be reported as
    // itself, not disguised as "this form is unsolvable".
    "errors inside an equation keep their own kind" {
        calc().routeErr("x + 1/0 = 5").kind shouldBe EvalError.Kind.DIVIDE_BY_ZERO
        calc().routeErr("x + Q = 5").kind shouldBe EvalError.Kind.UNKNOWN_SYMBOL
    }

    // #2/#9 — nested powers must hit a bound fast, not allocate a degree-16M
    // polynomial. This test must COMPLETE, which is the whole point.
    "nested powers are bounded, not an OOM" {
        shouldNotThrowAny {
            calc().route("((x^64)^64)^64 = 0")
            calc().route("(((x^64)^64)^64)^64 = 0")
        }
        calc().routeErr("((x^64)^64)^64 = 0").kind shouldBe EvalError.Kind.OVERFLOW
    }
})
