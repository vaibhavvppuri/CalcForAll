package com.vaibhav.calc.core

import com.vaibhav.calc.core.eval.ComplexValue
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.eval.Value
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.format.OutcomeFormatter
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.Surd
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.MathContext
import kotlin.math.abs

/**
 * CalcForAll 1.2: polynomial equations of degree 3–8 typed straight into the
 * calculator. The contract under test is the one a student relies on: exact where
 * exact is possible, ascending, complete (every distinct root or a typed refusal),
 * and visibly approximate when it is.
 */
class PolynomialRootsSpec : StringSpec({

    fun roots(input: String): Outcome.Roots = when (val r = Calculator().route(input)) {
        is RouteResult.Ok -> r.outcome as Outcome.Roots
        else -> throw AssertionError("expected roots for '$input' but got $r")
    }
    fun exact(vararg values: Num): List<Value> = values.map { RealValue(it) }
    fun Num.d(): Double = toBigDecimal(MathContext(25)).toDouble()
    fun Value.real(): Double = (this as RealValue).num.d()
    fun Value.parts(): Pair<Double, Double> = (this as ComplexValue).cx.let { it.re.d() to it.im.d() }

    /** |z^n·c_n + … + c_0| in plain doubles — an independent check of a claimed root. */
    fun residual(coefficients: List<Double>, re: Double, im: Double): Double {
        var ar = 0.0
        var ai = 0.0
        for (c in coefficients) { // highest power first
            val nr = ar * re - ai * im + c
            val ni = ar * im + ai * re
            ar = nr
            ai = ni
        }
        return abs(ar) + abs(ai)
    }

    "a cubic with rational roots is solved exactly, ascending" {
        val o = roots("x^3-6x^2+11x-6=0")
        o.unknown shouldBe "x"
        o.roots shouldBe exact(Num.of(1), Num.of(2), Num.of(3))
        OutcomeFormatter.render(o, "x^3-6x^2+11x-6=0").approximate shouldBe false
        o.steps.joinToString("\n") shouldContain "Exact rational roots"
    }

    "fractional rational roots are found" {
        roots("2x^3-3x^2-3x+2=0").roots shouldBe exact(Num.of(-1), Num.of(1, 2), Num.of(2))
    }

    "ascending order does not depend on the sign of the leading coefficient" {
        roots("-x^3+6x^2-11x+6=0").roots shouldBe exact(Num.of(1), Num.of(2), Num.of(3))
    }

    "x^3 = 8 keeps its complex pair exact: 2, -1 ± √3 i" {
        val o = roots("x^3=8")
        o.roots.size shouldBe 3
        o.roots[0] shouldBe RealValue(Num.of(2))
        val upper = (o.roots[1] as ComplexValue).cx
        val lower = (o.roots[2] as ComplexValue).cx
        upper.re shouldBe Num.of(-1)
        (upper.im * upper.im).compareTo(Num.of(3)) shouldBe 0
        (upper.im + lower.im).isZero shouldBe true
        OutcomeFormatter.render(o, "x^3=8").approximate shouldBe false
    }

    "a quartic with four rational roots" {
        roots("x^4-5x^2+4=0").roots shouldBe exact(Num.of(-2), Num.of(-1), Num.of(1), Num.of(2))
    }

    "zero is a root when the constant term vanishes" {
        roots("x^4=x^2").roots shouldBe exact(Num.of(-1), Num.of(0), Num.of(1))
    }

    "a leftover quadratic factor keeps exact surds: (x-1)(x²-2)" {
        val o = roots("x^3-x^2-2x+2=0")
        o.roots.size shouldBe 3
        o.roots[1] shouldBe RealValue(Num.of(1))
        o.roots[0].real() shouldBe (-1.4142135623730951 plusOrMinus 1e-12)
        o.roots[2].real() shouldBe (1.4142135623730951 plusOrMinus 1e-12)
        OutcomeFormatter.render(o, "x^3-x^2-2x+2=0").approximate shouldBe false
    }

    "repeated roots are listed once" {
        roots("(x-1)^3=0").roots shouldBe exact(Num.of(1))
        val o = roots("(x^2-2)^2*(x-1)=0")
        o.roots.size shouldBe 3
        o.roots[1] shouldBe RealValue(Num.of(1))
        o.steps.joinToString("\n") shouldContain "reported once"
        OutcomeFormatter.render(o, "(x^2-2)^2*(x-1)=0").approximate shouldBe false
    }

    "an irreducible cubic: one Sturm-counted real root and a verified complex pair" {
        val o = roots("x^3-2=0")
        o.roots.size shouldBe 3
        o.roots[0].real() shouldBe (1.2599210498948732 plusOrMinus 1e-12)
        val (re, im) = o.roots[1].parts()
        re shouldBe (-0.6299605249474366 plusOrMinus 1e-12)
        im shouldBe (1.0911236359717214 plusOrMinus 1e-12)
        o.roots[2].parts().second shouldBe (-1.0911236359717214 plusOrMinus 1e-12)
        OutcomeFormatter.render(o, "x^3-2=0").approximate shouldBe true
    }

    "x^4 = 2: two real roots and a purely imaginary pair" {
        val o = roots("x^4-2=0")
        o.roots.size shouldBe 4
        o.roots[0].real() shouldBe (-1.189207115002721 plusOrMinus 1e-12)
        o.roots[1].real() shouldBe (1.189207115002721 plusOrMinus 1e-12)
        val (re, im) = o.roots[2].parts()
        re shouldBe 0.0
        im shouldBe (1.189207115002721 plusOrMinus 1e-12)
    }

    "x^4 + 1 = 0 has no real root and lists all four complex ones" {
        val o = roots("x^4+1=0")
        o.roots.size shouldBe 4
        o.roots.forEach { root ->
            root.shouldBeInstanceOf<ComplexValue>()
            val (re, im) = root.parts()
            abs(re) shouldBe (0.7071067811865476 plusOrMinus 1e-12)
            abs(im) shouldBe (0.7071067811865476 plusOrMinus 1e-12)
            residual(listOf(1.0, 0.0, 0.0, 0.0, 1.0), re, im) shouldBe (0.0 plusOrMinus 1e-9)
        }
    }

    "x^5 - x - 1 = 0: every one of the five roots satisfies the equation" {
        val o = roots("x^5-x-1=0")
        o.roots.size shouldBe 5
        o.roots[0].real() shouldBe (1.1673039782614187 plusOrMinus 1e-12)
        val coefficients = listOf(1.0, 0.0, 0.0, 0.0, -1.0, -1.0)
        o.roots.drop(1).forEach { root ->
            val (re, im) = root.parts()
            residual(coefficients, re, im) shouldBe (0.0 plusOrMinus 1e-9)
        }
        // conjugates arrive as adjacent pairs
        o.roots[1].parts().second shouldBe (-o.roots[2].parts().second plusOrMinus 1e-12)
        o.roots[3].parts().second shouldBe (-o.roots[4].parts().second plusOrMinus 1e-12)
    }

    "degree 8 with eight rational roots" {
        roots("(x-1)*(x-2)*(x-3)*(x-4)*(x-5)*(x-6)*(x-7)*(x-8)=0").roots shouldBe
            exact(*(1..8).map { Num.of(it) }.toTypedArray())
    }

    "irrational coefficients are solved approximately and say so" {
        val o = roots("x^3-π*x=0")
        o.roots.size shouldBe 3
        o.roots[0].real() shouldBe (-1.772453850905516 plusOrMinus 1e-10)
        o.roots[1] shouldBe RealValue(Num.of(0))
        o.roots[2].real() shouldBe (1.772453850905516 plusOrMinus 1e-10)
        OutcomeFormatter.render(o, "x^3-π*x=0").approximate shouldBe true
    }

    "property: a product of distinct integer factors gives back exactly those roots" {
        checkAll(120, Arb.set(Arb.int(-12..12), 3..8)) { chosen ->
            val sorted = chosen.sorted()
            val equation = sorted.joinToString("*") { "(x-($it))" } + "=0"
            roots(equation).roots shouldBe sorted.map { RealValue(Num.of(it)) }
        }
    }

    "property: for random integer polynomials every root satisfies the equation and none is missing" {
        checkAll(150, Arb.list(Arb.int(-9..9), 4..7)) { raw ->
            val lowestFirst = raw.dropLast(1) + (if (raw.last() == 0) 1 else raw.last())
            val degree = lowestFirst.size - 1
            val equation = lowestFirst.withIndex().joinToString("+") { (k, c) ->
                when (k) {
                    0 -> "($c)"
                    1 -> "($c)*x"
                    else -> "($c)*x^$k"
                }
            } + "=0"
            val found = roots(equation).roots
            val highestFirst = lowestFirst.reversed().map { it.toDouble() }
            val points = found.map { if (it is RealValue) it.num.d() to 0.0 else it.parts() }
            withClue("$equation → $points") {
                // 1. each claimed root is a root (residual relative to the size of the terms)
                points.forEach { (re, im) ->
                    val magnitude = abs(re) + abs(im)
                    val scale = highestFirst.foldIndexed(0.0) { i, acc, c -> acc + abs(c) * Math.pow(magnitude, (degree - i).toDouble()) }
                    residual(highestFirst, re, im) shouldBe (0.0 plusOrMinus 1e-7 * maxOf(scale, 1.0))
                }
                // 2. real roots first and ascending
                val reals = found.takeWhile { it is RealValue }.map { it.real() }
                reals shouldBe reals.sorted()
                found.drop(reals.size).all { it is ComplexValue } shouldBe true
                // 3. completeness: with no repeated root, Vieta's sum of all roots is −c[n−1]/c[n]
                if (found.size == degree) {
                    val expected = -highestFirst[1] / highestFirst[0]
                    points.sumOf { it.first } shouldBe (expected plusOrMinus 1e-6 * (1 + abs(expected)))
                    points.sumOf { it.second } shouldBe (0.0 plusOrMinus 1e-6)
                } else {
                    // fewer roots than the degree is only legitimate when one of them repeats: p'(r) = 0
                    (found.size < degree) shouldBe true
                    val slope = highestFirst.dropLast(1).mapIndexed { i, c -> c * (degree - i) }
                    points.any { (re, im) -> residual(slope, re, im) < 1e-6 * maxOf(1.0, slope.sumOf { abs(it) }) } shouldBe true
                }
            }
        }
    }

    // ---- regressions from the 1.2 review ------------------------------------

    "irrational coefficients with a repeated root: the root is reported once, never twice or as a complex pair" {
        for (equation in listOf("(x-sqrt(2))^2*(x+1)=0", "(x-sqrt(11))^2*(x+1)=0")) {
            val o = roots(equation)
            withClue("$equation → ${o.roots}") {
                o.roots.size shouldBe 2
                o.roots.all { it is RealValue } shouldBe true
                o.roots[0].real() shouldBe (-1.0 plusOrMinus 1e-10)
                o.steps.joinToString("\n") shouldContain "reported once"
            }
        }
        roots("(x-sqrt(2))^2*(x+1)=0").roots[1].real() shouldBe (1.4142135623730951 plusOrMinus 1e-10)
        // The written positive power proves that its base has exactly the same root set.
        roots("(x-sqrt(3))^3=0").roots shouldBe exact(Surd.of(Num.ONE, java.math.BigInteger.valueOf(3)))
    }

    "a genuine complex pair with a tiny imaginary part is kept, not flattened to a real root (second-review regression)" {
        // Roots: π and 1 ± 1e-13·i. Coefficient noise places a double root to about 1e-24, so 1e-13 is real structure.
        val o = roots("(x-π)*((x-1)^2+10^-26)=0")
        o.roots.size shouldBe 3
        o.roots[0].real() shouldBe (3.141592653589793 plusOrMinus 1e-10)
        val (re, im) = o.roots[1].parts()
        re shouldBe (1.0 plusOrMinus 1e-9)
        (abs(im) / 1e-13) shouldBe (1.0 plusOrMinus 1e-3)
    }

    "a repeated complex root of an equation with irrational coefficients is reported once (third-review regression)" {
        // (x²+π)² = 0 has ±i√π, each twice. The 50-digit π² is not a perfect square, so the two
        // pairs arrive about 1e-26 apart, with real parts that are nothing but that noise.
        val o = roots("(x^2+π)^2=0")
        withClue("${o.roots}") {
            o.roots.size shouldBe 2
            o.roots.forEach { root ->
                val (re, im) = root.parts()
                re shouldBe 0.0
                abs(im) shouldBe (1.7724538509055159 plusOrMinus 1e-12)
            }
            o.steps.joinToString("\n") shouldContain "reported once"
        }
        // Off the imaginary axis the same root sits at −1 ± i√π.
        val shifted = roots("((x+1)^2+π)^2=0")
        withClue("${shifted.roots}") {
            shifted.roots.size shouldBe 2
            shifted.roots.forEach { root ->
                val (re, im) = root.parts()
                re shouldBe (-1.0 plusOrMinus 1e-12)
                abs(im) shouldBe (1.7724538509055159 plusOrMinus 1e-12)
            }
        }
        // The cube has the same two roots as its written base; no numerical clustering is needed.
        val triple = roots("(x^2+π)^3=0")
        triple.roots shouldBe o.roots
        val imaginary = triple.roots.map { it.parts().second }.sorted()
        imaginary[0] shouldBe (-1.7724538509055159 plusOrMinus 1e-12)
        imaginary[1] shouldBe (1.7724538509055159 plusOrMinus 1e-12)
    }

    "roots sixty orders of magnitude apart are compared without tripping the number tower's guard" {
        val values = roots("(x^2-π)*(x-10^60)=0").roots.map { it.real() }
        values.size shouldBe 3
        values[0] shouldBe (-1.772453850905516 plusOrMinus 1e-10)
        values[1] shouldBe (1.772453850905516 plusOrMinus 1e-10)
        (values[2] / 1e60) shouldBe (1.0 plusOrMinus 1e-12)
    }

    "a leftover quadratic keeps its simplest surd even when a large prime denominator was cleared" {
        val o = roots("x^3+x^2/10007-2x-2/10007=0")
        o.roots[1] shouldBe RealValue(Num.of(-1, 10007))
        Formatter.exact(o.roots[2], Settings()) shouldBe "√2"
        OutcomeFormatter.render(o, "x^3+x^2/10007-2x-2/10007=0").approximate shouldBe false
    }

    "a very small root keeps its digits: the bracket tolerance is relative to the root" {
        val tiny = roots("x^3+x=1/10^25")
        (tiny.roots[0].real() / 1e-25) shouldBe (1.0 plusOrMinus 1e-12)
        val three = roots("x^3-3x/10^80+1/10^120=0").roots.map { it.real() }
        three.size shouldBe 3
        three.all { kotlin.math.abs(it) > 1e-42 && kotlin.math.abs(it) < 1e-39 } shouldBe true
        // Vieta: the three roots of x³ + px + q sum to zero
        (three.sum() / three.maxOf { kotlin.math.abs(it) }) shouldBe (0.0 plusOrMinus 1e-10)
    }

    "a whole-number root beyond the rational-root search is still exact, and the steps do not claim a test that was skipped" {
        val o = roots("x^3=10^15")
        o.roots[0] shouldBe RealValue(Num.of(100_000))
        val steps = o.steps.joinToString("\n")
        steps shouldContain "skipped"
        (steps.contains("No rational roots")) shouldBe false
    }

    "coefficients whose common denominator is enormous are refused at once, not ground through" {
        val terms = listOf(1, 3, 7, 9, 13, 19, 21, 27).mapIndexed { k, c ->
            if (k == 0) "1/(10^90+$c)" else "x^$k/(10^90+$c)"
        }
        val r = Calculator().route(terms.joinToString("+") + "=0")
        r.shouldBeInstanceOf<RouteResult.Err>()
        r.error.message shouldContain "too large"
    }

    "degree 9 is a typed refusal, not a guess" {
        val r = Calculator().route("x^9=2")
        r.shouldBeInstanceOf<RouteResult.Err>()
        r.error.message shouldContain "degree 9"
    }
})
