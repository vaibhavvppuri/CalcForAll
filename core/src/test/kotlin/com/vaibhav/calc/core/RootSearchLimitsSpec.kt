package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.graph.*
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class RootSearchLimitsSpec : StringSpec({
    fun curve(input: String, settings: Settings = Settings()) = Calculator(settings).plot(input).getOrThrow()
    fun unsupported(result: RootResult, detail: String) {
        val error = result.shouldBeInstanceOf<RootResult.Failed>().error
        error.kind shouldBe EvalError.Kind.UNSUPPORTED
        error.message shouldContain detail
    }

    "sample and option limits reject before starting even an exact path" {
        val f = curve("x")
        for (samples in listOf(Int.MIN_VALUE, 0, 1, RootFinder.MAX_SAMPLES + 1, Int.MAX_VALUE)) {
            unsupported(RootFinder.roots(f, Num.ZERO, Num.ONE, Settings(), samples), "sample count")
        }
        for (count in listOf(0, RootFinder.MAX_EVALUATIONS + 1, Int.MAX_VALUE)) {
            unsupported(RootFinder.roots(f, Num.ZERO, Num.ONE, Settings(), options = RootSearchOptions(maxEvaluations = count)), "evaluation budget")
        }
        for (millis in listOf(0L, RootFinder.MAX_MILLIS + 1, Long.MAX_VALUE)) {
            unsupported(RootFinder.roots(f, Num.ZERO, Num.ONE, Settings(), options = RootSearchOptions(maxMillis = millis)), "time budget")
        }
    }

    "reversed and empty intervals return typed domain failures" {
        val f = curve("x")
        for (bounds in listOf(Num.ONE to Num.ZERO, Num.ONE to Num.ONE)) {
            RootFinder.roots(f, bounds.first, bounds.second, Settings()).shouldBeInstanceOf<RootResult.Failed>().error.kind shouldBe EvalError.Kind.DOMAIN
        }
    }

    "the exact quadratic path needs only its verification evaluations" {
        val f = curve("x^2-1")
        val result = RootFinder.roots(f, Num.of(-2), Num.of(2), Settings(), options = RootSearchOptions(maxEvaluations = 2))
            .shouldBeInstanceOf<RootResult.Found>()
        result.method shouldContain "exact"
        result.roots.map { it.x } shouldBe listOf(Num.of(-1), Num.ONE)
        result.roots.all { it.residual.isZero } shouldBe true
        unsupported(RootFinder.roots(f, Num.of(-2), Num.of(2), Settings(), options = RootSearchOptions(maxEvaluations = 1)), "evaluation budget")
    }

    "a completed numeric scan still needs budget to verify its candidate" {
        val f = curve("x^3-1")
        // One closed exponent certificate, five samples, then one final verification.
        unsupported(RootFinder.roots(f, Num.of(-1), Num.ONE, Settings(), 5, RootSearchOptions(maxEvaluations = 6)), "evaluation budget")
        val found = RootFinder.roots(f, Num.of(-1), Num.ONE, Settings(), 5, RootSearchOptions(maxEvaluations = 7))
            .shouldBeInstanceOf<RootResult.Found>()
        found.roots.single().x shouldBe Num.ONE
        found.roots.single().residual shouldBe Num.ZERO
    }

    "budget exhaustion never returns roots collected from a partial scan" {
        val f = curve("x^3-x")
        unsupported(RootFinder.roots(f, Num.of(-1), Num.ONE, Settings(), 5, RootSearchOptions(maxEvaluations = 3)), "evaluation budget")
    }

    "bisection and scanning share one evaluation budget" {
        val f = curve("x^3-2")
        unsupported(RootFinder.roots(f, Num.ONE, Num.TWO, Settings(), 2, RootSearchOptions(maxEvaluations = 3)), "evaluation budget")
        val found = RootFinder.roots(f, Num.ONE, Num.TWO, Settings(), 2, RootSearchOptions(maxEvaluations = 100))
            .shouldBeInstanceOf<RootResult.Found>()
        found.method shouldContain "numeric"
        found.roots.size shouldBe 1
        (found.roots.single().residual <= RootFinder.VERIFY_TOLERANCE) shouldBe true
    }

    "cooperative stop works before extraction and during analysis without coroutines" {
        val f = curve("x^3-x")
        unsupported(RootFinder.roots(f, Num.of(-1), Num.ONE, Settings(), options = RootSearchOptions(shouldStop = { true })), "cancelled")
        var checks = 0
        unsupported(RootFinder.roots(f, Num.of(-1), Num.ONE, Settings(), 5,
            RootSearchOptions(shouldStop = { ++checks >= 12 })), "cancelled")
        checks shouldBe 12
    }

    "long flat syntax is refused before recursive polynomial extraction" {
        unsupported(RootFinder.roots(curve(List(1800) { "x" }.joinToString("+")), Num.ZERO, Num.ONE, Settings()), "size or depth budget")
    }

    "an unresolved evaluation hole cannot masquerade as no roots" {
        val settings = Settings(complexEnabled = true)
        unsupported(RootFinder.roots(curve("sqrt(x)", settings), Num.of(-1), Num.ONE, settings, 5), "continuity")
    }

    "restricted-domain functions explicitly distinguish curve support from numeric root support" {
        val f = curve("sqrt(x)-1")
        f.at(Num.ONE).shouldBeInstanceOf<Sample.Defined>().y shouldBe Num.ZERO
        f.at(Num.of(-1)).shouldBeInstanceOf<Sample.Undefined>()
        unsupported(RootFinder.roots(f, Num.of(-1), Num.of(4), Settings(), 11), "function 'sqrt'")
    }

    "a sign-changing bracket through an undefined point is explicitly unresolved" {
        unsupported(RootFinder.roots(curve("1/x"), Num.of(-1), Num.ONE, Settings(), 2), "variable-dependent denominator")
    }

    "bisection refuses an interval it cannot resolve within its iteration cap" {
        unsupported(RootFinder.roots(curve("x^3-2"), Num.ofDecimal("-1e100"), Num.ofDecimal("1e100"), Settings(), 2), "iteration budget")
    }

    "intersections and Calculator facades share the same budget and preserve exact solving" {
        val calc = Calculator()
        val f = calc.plot("x^2").getOrThrow()
        val g = calc.plot("1").getOrThrow()
        unsupported(calc.graphIntersections(f, g, Num.of(-2), Num.of(2), options = RootSearchOptions(maxEvaluations = 1)), "evaluation budget")
        calc.graphIntersections(f, g, Num.of(-2), Num.of(2), options = RootSearchOptions(maxEvaluations = 2))
            .shouldBeInstanceOf<RootResult.Found>().roots.map { it.x } shouldBe listOf(Num.of(-1), Num.ONE)
        unsupported(calc.graphRoots(f, Num.of(-2), Num.of(2), options = RootSearchOptions(shouldStop = { true })), "cancelled")
        val y = calc.plot("y", "y").getOrThrow()
        unsupported(RootFinder.intersections(f, y, Num.of(-2), Num.of(2), Settings()), "independent variable")
    }

    "intersection refuses different memory rather than reinterpreting the second curve" {
        fun memory(a: Int) = Environment().apply { set("A", RealValue(Num.of(a))) }
        val f = PlottableFunction.of("A*x", Settings(), memory(2)).getOrThrow()
        val g = PlottableFunction.of("A*x+1", Settings(), memory(3)).getOrThrow()
        unsupported(RootFinder.intersections(f, g, Num.of(-2), Num.of(2), Settings()), "settings and memory")
        val same = PlottableFunction.of("A*x+1", Settings(), memory(2)).getOrThrow()
        RootFinder.intersections(f, same, Num.of(-2), Num.of(2), Settings())
            .shouldBeInstanceOf<RootResult.Found>().roots shouldBe emptyList()
        val otherAngle = PlottableFunction.of("A*x+1", Settings(angle = AngleMode.RAD), memory(2)).getOrThrow()
        unsupported(RootFinder.intersections(f, otherAngle, Num.of(-2), Num.of(2), Settings()), "settings and memory")
    }
})
