package com.vaibhav.calc.core

import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Article 12 enforcement: **every value the engine renders must parse back to the
 * same value.** Comparison uses the engine's own equality (`=` → Verdict), so a
 * renderer/parser drift shows up as either an un-readable rendering or a false
 * verdict. "The engine could not re-read its own `15 m²` output" is the drift this
 * exists to catch.
 */
class RoundTripSpec : StringSpec({

    val c = Calculator()

    fun roundTrip(input: String) {
        val out = (c.route(input) as? RouteResult.Ok)?.outcome
            ?: throw AssertionError("input did not evaluate: '$input'")
        val value = (out as? Outcome.Scalar)?.value
            ?: throw AssertionError("not a scalar: '$input'")
        val rendered = c.exact(value)
        when (val r = c.route("($rendered) = ($input)")) {
            is RouteResult.Ok -> {
                val v = r.outcome
                if (v !is Outcome.Verdict) {
                    throw AssertionError("round-trip of '$input' → '$rendered' did not compare: $v")
                }
                if (!v.holds) {
                    throw AssertionError("round-trip MISMATCH: '$input' rendered as '$rendered', which is a different value")
                }
            }
            is RouteResult.Err ->
                throw AssertionError("engine cannot re-read its own rendering of '$input': '$rendered' → ${r.error.message}")
            is RouteResult.Ambiguous ->
                throw AssertionError("round-trip of '$input' → '$rendered' was ambiguous")
        }
    }

    "rationals round-trip" {
        listOf("0.1+0.2", "1/3", "10/4", "-3/4", "6/3", "2^10").forEach { roundTrip(it) }
    }
    "surds round-trip" {
        listOf("sqrt(8)", "sqrt(2)", "1/sqrt(2)", "sqrt(12)", "2^0.5").forEach { roundTrip(it) }
    }
    "symbolic constants round-trip" {
        listOf("pi", "2*pi", "pi/6", "-pi", "e", "tau").forEach { roundTrip(it) }
    }
    "complex numbers round-trip" {
        listOf("2+3i", "(2+3i)*i", "conj(i)", "i", "(1+i)^2", "(2+3i)/(1-i)").forEach { roundTrip(it) }
    }
    "simple quantities round-trip" {
        listOf("5 km + 300 m", "20 degC", "5 lakh", "3 * 5 km", "1 tola", "36 kmph").forEach { roundTrip(it) }
    }
    "derived-unit quantities round-trip (the engine must read its own SI symbols)" {
        listOf(
            "5 m/s",          // renders "m/s"
            "5 m * 3 m",      // renders "m²"
            "2 m * 3 m * 4 m", // renders "m³"
            "5 kg * 3 m / (2 s * 1 s)", // renders "kg·m/s²"
        ).forEach { roundTrip(it) }
    }
    "base conversion round-trips (toBase → fromBase)" {
        listOf(2, 8, 10, 16, 36).forEach { radix ->
            val s = (c.toBase(Num.of(123456), radix) as com.vaibhav.calc.core.error.CalcResult.Ok).value
            val back = (c.fromBase(s, radix) as com.vaibhav.calc.core.error.CalcResult.Ok).value
            back.compareTo(Num.of(123456)) shouldBe 0
        }
    }
})
