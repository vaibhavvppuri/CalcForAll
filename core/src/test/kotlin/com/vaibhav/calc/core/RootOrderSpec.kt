package com.vaibhav.calc.core

import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.graph.RootResult
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * **C-6 — root order follows the design: ASCENDING.**
 *
 * `GAP-ANALYSIS-DESIGN-V1.md`, §C-6, states it without ambiguity:
 *
 * > *"Root order: design shows `x = 2, 3`; engine returns `3, 2`. Verified:
 * > `x^2-5x+6=0 => ROOTS x = 3, 2`. §03 frame ③ shows `x = 2, 3`. Cosmetic, but it
 * > is in the design's hero frame, and **ascending is what a student expects**."*
 *
 * The engine emitted `(−b+√D)/2a` first, which is the LARGER root whenever `a > 0`
 * — descending, and backwards from every textbook.
 *
 * **This also removed an inconsistency inside the engine**, not just against the
 * design: `graph/RootFinder` returns degree-≥3 roots from a left-to-right sample
 * scan (ascending) while degree-2 roots came from `Solvers.quadratic` (descending).
 * The same graph could order its roots two different ways depending on the degree
 * of the polynomial. One rule now, at the one place that produces them.
 */
class RootOrderSpec : StringSpec({

    val c = Calculator()

    fun reals(a: Long, b: Long, cc: Long): List<Num> {
        val q = (c.solveQuadratic(Num.of(a), Num.of(b), Num.of(cc)) as CalcResult.Ok).value
        return q.roots.map { (it as RealValue).num }
    }

    "C-6: x² − 5x + 6 = 0 gives 2 then 3 — the design's hero frame, ascending" {
        val r = reals(1, -5, 6)
        r.size shouldBe 2
        r[0].compareTo(Num.of(2)) shouldBe 0
        r[1].compareTo(Num.of(3)) shouldBe 0
    }

    "C-6: ascending holds through the router, which is what the app calls" {
        // The gap analysis measured this through ExpressionRouter, so the pin has
        // to sit there too — a solver fixed in isolation could still be re-ordered
        // on the way out.
        val out = (c.route("x^2-5x+6=0") as RouteResult.Ok).outcome as Outcome.Roots
        val roots = out.roots.map { (it as RealValue).num }
        roots[0].compareTo(Num.of(2)) shouldBe 0
        roots[1].compareTo(Num.of(3)) shouldBe 0
    }

    "C-6: negative and straddling roots order ascending too" {
        // x² − x − 6 = (x−3)(x+2) → −2, 3
        val r = reals(1, -1, -6)
        r[0].compareTo(Num.of(-2)) shouldBe 0
        r[1].compareTo(Num.of(3)) shouldBe 0
    }

    "C-6: a NEGATIVE leading coefficient still orders ascending" {
        // The old code emitted (−b+√D)/2a first, which flips when a < 0 — so a fix
        // that merely swapped the two positions would be right for a > 0 and wrong
        // here. −x² + 5x − 6 = 0 has the same roots 2 and 3.
        val r = reals(-1, 5, -6)
        r[0].compareTo(Num.of(2)) shouldBe 0
        r[1].compareTo(Num.of(3)) shouldBe 0
    }

    "C-6: exact surd roots order ascending: x² − 2 → −√2 then √2" {
        val q = (c.solveQuadratic(Num.of(1), Num.of(0), Num.of(-2)) as CalcResult.Ok).value
        com.vaibhav.calc.core.format.Formatter.exact(q.roots[0], Settings()) shouldBe "-√2"
        com.vaibhav.calc.core.format.Formatter.exact(q.roots[1], Settings()) shouldBe "√2"
    }

    "C-6: a repeated root is still a single root, not a duplicated pair" {
        // x² − 2x + 1 = (x−1)². Ordering is vacuous for one element; what matters
        // is that the fix did not turn D = 0 into two entries.
        val q = (c.solveQuadratic(Num.of(1), Num.of(-2), Num.of(1)) as CalcResult.Ok).value
        q.roots.size shouldBe 1
        (q.roots[0] as RealValue).num.compareTo(Num.ONE) shouldBe 0
    }

    "C-6: the worked STEPS agree with the order they are shown beside" {
        // x₁/x₂ in the steps must name the same roots in the same order as the
        // returned list — two renderings of one result may not drift (Article 12).
        val q = (c.solveQuadratic(Num.of(1), Num.of(-5), Num.of(6)) as CalcResult.Ok).value
        val line = q.steps.first { it.contains("x₁") }
        line.substringAfter("x₁ = ").substringBefore(",").trim() shouldBe "2"
        line.substringAfter("x₂ = ").trim() shouldBe "3"
    }

    "C-6: the graph root finder agrees with the solver at degree 2" {
        // The inconsistency this closes: degree ≥ 3 comes from a left-to-right
        // sample scan (ascending) and degree 2 came from the solver (descending).
        val f = (c.plot("x^2-5x+6") as CalcResult.Ok).value
        val found = c.graphRoots(f, Num.of(-10), Num.of(10)) as RootResult.Found
        val xs = found.roots.map { it.x }
        xs[0].compareTo(Num.of(2)) shouldBe 0
        xs[1].compareTo(Num.of(3)) shouldBe 0
    }
})
