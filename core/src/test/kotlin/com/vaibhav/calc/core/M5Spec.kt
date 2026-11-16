package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.graph.Axes
import com.vaibhav.calc.core.graph.PlottableFunction
import com.vaibhav.calc.core.graph.RootResult
import com.vaibhav.calc.core.graph.Sample
import com.vaibhav.calc.core.graph.Sampler
import com.vaibhav.calc.core.graph.Viewport
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.solve.Extraction
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/** M5 — D10: the graphing core. core/ only: sampling, roots, intersections, trace, viewport. */
class M5Spec : StringSpec({

    fun calc(s: Settings = Settings()) = Calculator(s)
    fun Calculator.f(src: String): PlottableFunction = (plot(src) as CalcResult.Ok).value
    fun n(v: Long) = Num.of(v)

    // --- Article 11: a GAP in the curve is not a HOLE in the data ------------
    "1/x at 0 is Undefined — the mathematics has no value there (a gap)" {
        val s = calc().f("1/x").at(Num.ZERO)
        s.shouldBeInstanceOf<Sample.Undefined>()
        s.cause.kind shouldBe EvalError.Kind.DIVIDE_BY_ZERO
    }
    "ln(x) at -1 is Undefined (a gap), not a failure" {
        calc().f("ln(x)").at(n(-1)).shouldBeInstanceOf<Sample.Undefined>()
    }
    "a complex value is Unevaluated — a hole in OUR data, not a gap in the maths" {
        // sqrt(-1) exists (it is i); we simply cannot plot it on a real axis.
        val s = calc(Settings(complexEnabled = true)).f("sqrt(x)").at(n(-1))
        s.shouldBeInstanceOf<Sample.Unevaluated>()
    }
    "gaps and holes are different types and can never be confused" {
        val gap = calc().f("1/x").at(Num.ZERO)
        val hole = calc(Settings(complexEnabled = true)).f("sqrt(x)").at(n(-1))
        (gap is Sample.Undefined) shouldBe true
        (hole is Sample.Unevaluated) shouldBe true
        (gap::class == hole::class) shouldBe false
    }
    "ordinary points are Defined" {
        val s = calc().f("x^2").at(n(3))
        s.shouldBeInstanceOf<Sample.Defined>()
        s.y.compareTo(n(9)) shouldBe 0
    }

    // --- the sealed extraction drives the right method (why Part 1 came first) -
    "a polynomial is solved exactly; a non-polynomial falls back to numeric" {
        (calc().f("x^2-4").polynomial() is Extraction.Extracted) shouldBe true
        (calc().f("sin(x)").polynomial() is Extraction.NotPolynomial) shouldBe true
    }
    "a FAILED extraction stops — it must never be mistaken for 'not polynomial'" {
        val c = calc()
        val f = c.f("x + Q") // Q is unbound: a real failure, not a shape we can't handle
        (f.polynomial() is Extraction.Failed) shouldBe true
        val r = c.graphRoots(f, n(-10), n(10))
        r.shouldBeInstanceOf<RootResult.Failed>()
        r.error.kind shouldBe EvalError.Kind.UNKNOWN_SYMBOL
    }

    // --- roots: verified by substitution, always ------------------------------
    "exact quadratic roots, each verified with residual 0" {
        val c = calc()
        val r = c.graphRoots(c.f("x^2-5x+6"), n(-10), n(10))
        r.shouldBeInstanceOf<RootResult.Found>()
        r.method shouldContain "exact"
        val xs = r.roots.map { it.x }
        xs.any { it.compareTo(n(2)) == 0 } shouldBe true
        xs.any { it.compareTo(n(3)) == 0 } shouldBe true
        r.roots.all { it.residual.compareTo(Num.ZERO) == 0 } shouldBe true // exact proof
    }
    "exact linear root" {
        val c = calc()
        val r = c.graphRoots(c.f("2x+3"), n(-10), n(10))
        r.shouldBeInstanceOf<RootResult.Found>()
        r.roots.single().x.compareTo(Num.of(-3, 2)) shouldBe 0
    }
    "irrational roots verify exactly: x^2-2 → ±√2 with residual 0" {
        val c = calc()
        val r = c.graphRoots(c.f("x^2-2"), n(-10), n(10))
        r.shouldBeInstanceOf<RootResult.Found>()
        r.roots.size shouldBe 2
        r.roots.all { it.residual.compareTo(Num.ZERO) == 0 } shouldBe true
    }
    "a numeric root is found and verified: sin(x) on [-1,1] → 0" {
        val c = calc(Settings(angle = AngleMode.RAD))
        val r = c.graphRoots(c.f("sin(x)"), n(-1), n(1))
        r.shouldBeInstanceOf<RootResult.Found>()
        r.method shouldContain "numeric"
        r.roots.single().x.compareTo(Num.ZERO) shouldBe 0
    }
    "an asymptote is NEVER reported as a root: 1/x on [-1,1]" {
        val c = calc()
        val r = c.graphRoots(c.f("1/x"), n(-1), n(1))
        // 1/x changes sign across 0 and bisection converges there — but f(0) is
        // Undefined, so it can never be verified. No root may be reported.
        val reported = when (r) {
            is RootResult.Found -> r.roots
            is RootResult.Unverified -> r.verified
            is RootResult.Failed -> emptyList()
        }
        reported.isEmpty() shouldBe true
    }
    "roots outside the range are not reported" {
        val c = calc()
        val r = c.graphRoots(c.f("x^2-5x+6"), n(-10), Num.of(5, 2)) // only x=2 is in range
        r.shouldBeInstanceOf<RootResult.Found>()
        r.roots.single().x.compareTo(n(2)) shouldBe 0
    }

    // --- intersections are roots of the difference ----------------------------
    "x^2 meets x+2 at x = 2 and x = -1" {
        val c = calc()
        val r = c.graphIntersections(c.f("x^2"), c.f("x+2"), n(-10), n(10))
        r.shouldBeInstanceOf<RootResult.Found>()
        val xs = r.roots.map { it.x }
        xs.any { it.compareTo(n(2)) == 0 } shouldBe true
        xs.any { it.compareTo(n(-1)) == 0 } shouldBe true
    }

    // --- sampling & trace ------------------------------------------------------
    "sampling covers the viewport and every point comes from the same f" {
        val c = calc()
        val f = c.f("x^2")
        // An EXPLICIT window, not Viewport.DEFAULT: this tests the sampler's
        // mechanics, and coupling it to the product's opening frame made a design
        // decision about what the graph shows break a test about arithmetic (D-027).
        val window = Viewport(n(-10), n(10), n(-10), n(10))
        val samples = Sampler(f).sample(window, 21)
        samples.size shouldBe 21
        (samples.first() as Sample.Defined).x.compareTo(n(-10)) shouldBe 0
        (samples.last() as Sample.Defined).x.compareTo(n(10)) shouldBe 0
        // sampler and direct evaluation agree, because they are the same call
        val mid = samples[10] as Sample.Defined
        (f.at(mid.x) as Sample.Defined).y.compareTo(mid.y) shouldBe 0
    }
    "sampling 1/x yields a gap rather than a fabricated point" {
        val samples = Sampler(calc().f("1/x")).samples(n(-1), n(1), 3) // -1, 0, 1
        samples[1].shouldBeInstanceOf<Sample.Undefined>()
    }
    "trace returns the same value the sampler would" {
        val f = calc().f("x^2+1")
        (Sampler(f).trace(n(3)) as Sample.Defined).y.compareTo(n(10)) shouldBe 0
    }

    // --- viewport: exact, and animatable (the grid→axes reveal) ---------------
    "lerp is exact and hits both endpoints" {
        val a = Viewport(n(-10), n(10), n(-10), n(10))
        val b = Viewport(n(-1), n(1), n(-1), n(1))
        a.lerp(b, Num.ZERO) shouldBe a
        a.lerp(b, Num.ONE) shouldBe b
        val half = a.lerp(b, Num.HALF)
        half.xMin.compareTo(Num.of(-11, 2)) shouldBe 0 // exactly -5.5, no drift
        half.xMax.compareTo(Num.of(11, 2)) shouldBe 0
    }
    "zoom keeps its focal point fixed" {
        val v = Viewport(n(-10), n(10), n(-10), n(10))
        val about = n(2)
        val z = v.zoom(Num.HALF, aboutX = about, aboutY = Num.ZERO)
        // the focal point sits at the same fraction across the viewport
        val before = (about - v.xMin) / v.width
        val after = (about - z.xMin) / z.width
        before.compareTo(after) shouldBe 0
    }
    "pan shifts without resizing" {
        val v = Viewport(n(-10), n(10), n(-10), n(10)).pan(n(5), n(-2))
        v.xMin.compareTo(n(-5)) shouldBe 0
        v.width.compareTo(n(20)) shouldBe 0
    }

    /**
     * **D-027 — one opening frame.** There were three, and none knew about the others:
     * this engine's `[-10,10]²`, the design's graph default `[-6.6,6.6]×[-4.2,4.2]`,
     * and its reveal's `[-5,5]×[-3.4,6.6]`. Invisible in the design's demo (separate
     * canvases); a visible snap in the app, where the reveal and the graph are the
     * same surface. Pinned here deliberately, so changing it is a decision rather than
     * an accident.
     */
    "there is ONE opening frame, and it is the design's — exactly, in rationals" {
        Viewport.DEFAULT.xMin.compareTo(Num.of(-33, 5)) shouldBe 0 // -6.6
        Viewport.DEFAULT.xMax.compareTo(Num.of(33, 5)) shouldBe 0 // 6.6
        Viewport.DEFAULT.yMin.compareTo(Num.of(-21, 5)) shouldBe 0 // -4.2
        Viewport.DEFAULT.yMax.compareTo(Num.of(21, 5)) shouldBe 0 // 4.2
    }
    "the opening frame is exact — no Double ever touched it (Article 3)" {
        // 6.6 is not representable in binary floating point. As a rational it is.
        Viewport.DEFAULT.width.compareTo(Num.of(66, 5)) shouldBe 0
        Viewport.DEFAULT.centerX.compareTo(Num.ZERO) shouldBe 0
    }
    "a reveal that starts elsewhere can LAND on the opening frame rather than snap" {
        // If the design keeps its own reveal frame for composition, lerp is the bridge.
        val revealFrame = Viewport(n(-5), n(5), Num.of(-17, 5), Num.of(33, 5))
        revealFrame.lerp(Viewport.DEFAULT, Num.ONE) shouldBe Viewport.DEFAULT
        revealFrame.lerp(Viewport.DEFAULT, Num.ZERO) shouldBe revealFrame
    }
    "world → screen → world round-trips exactly" {
        val v = Viewport.DEFAULT
        val x = Num.of(7, 3)
        v.toWorldX(v.toScreenX(x, 800), 800).compareTo(x) shouldBe 0
        val y = Num.of(-5, 4)
        v.toWorldY(v.toScreenY(y, 480), 480).compareTo(y) shouldBe 0
    }

    // --- axes -----------------------------------------------------------------
    "nice ticks are exact 1/2/5 × 10ⁿ steps" {
        val t = Axes.ticks(n(-10), n(10), 10)
        t.step.compareTo(n(2)) shouldBe 0
        t.values.first().compareTo(n(-10)) shouldBe 0
        t.values.last().compareTo(n(10)) shouldBe 0
        t.values.size shouldBe 11
    }
    "sub-unit ranges get exact fractional steps" {
        val t = Axes.ticks(Num.ZERO, Num.ONE, 10)
        t.step.compareTo(Num.of(1, 10)) shouldBe 0 // exactly 1/10, not 0.1000000001
    }
    "ticks for a viewport cover both axes" {
        val (xs, ys) = Axes.forViewport(Viewport.DEFAULT, 10)
        xs.values.isNotEmpty() shouldBe true
        ys.values.isNotEmpty() shouldBe true
    }
})
