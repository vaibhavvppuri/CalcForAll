package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.graph.*
import com.vaibhav.calc.core.num.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class RootMonotonicitySpec : StringSpec({
    val degrees = Settings()
    val radians = Settings(angle = AngleMode.RAD)
    fun search(expression: String, settings: Settings = degrees, lo: Int = -1, hi: Int = 1,
               samples: Int = 200, evaluations: Int = 64): RootResult =
        RootFinder.roots(Calculator(settings).plot(expression).getOrThrow(), Num.of(lo), Num.of(hi), settings,
            samples, RootSearchOptions(maxEvaluations = evaluations))
    fun limited(result: RootResult) {
        val error = result.shouldBeInstanceOf<RootResult.Failed>().error
        error.kind shouldBe EvalError.Kind.UNSUPPORTED
        error.message shouldContain "evaluation budget"
    }

    "the default degree cosine root retains independent accuracy within sixty four evaluations" {
        val result = search("cos(x)-x", lo = 0, hi = 2).shouldBeInstanceOf<RootResult.Found>()
        result.method shouldContain "strict derivative enclosure"
        val root = result.roots.single()
        // Independent reference: exact rational Machin expansions (80 terms each)
        // plus 16-term alternating cosine sums establish opposite signs at these
        // two decimal endpoints. Their separation is 1e-50, not a probe agreement.
        val referenceLo = BigDecimal("0.99984774153108811295981076867979979918187258615277")
        val referenceHi = referenceLo.add(BigDecimal("1e-50"))
        val x = root.x.toBigDecimal(Num.MC)
        (x >= referenceLo.subtract(BigDecimal("1e-12")) && x <= referenceHi.add(BigDecimal("1e-12"))) shouldBe true
        val residual = root.residual.bounds()!!
        (maxOf(residual.lower.abs(), residual.upper.abs()) < BigDecimal("1e-12")) shouldBe true
        // The old mandatory 200-point scan cannot fit this cap. This verifies a
        // structural work reduction without a fragile wall-clock assertion.
        limited(search("cos(x)-x", lo = 0, hi = 2, evaluations = 2))
    }

    "a strict increasing trigonometric perturbation has the independently known zero" {
        val found = search("2*x+sin(x)", radians, evaluations = 8).shouldBeInstanceOf<RootResult.Found>()
        found.method shouldContain "strict derivative enclosure"
        found.roots.single().x shouldBe Num.ZERO
        found.roots.single().residual shouldBe Num.ZERO
        search("x+sin(sin(x))", evaluations = 8).shouldBeInstanceOf<RootResult.Found>().roots.single().x shouldBe Num.ZERO
    }

    "endpoint zeros remain exact and consume their final original function verification" {
        for ((lo, hi) in listOf(0 to 1, -1 to 0)) {
            val found = search("x+sin(x)", lo = lo, hi = hi, evaluations = 3).shouldBeInstanceOf<RootResult.Found>()
            found.roots.single().x shouldBe Num.ZERO
            found.roots.single().residual shouldBe Num.ZERO
            limited(search("x+sin(x)", lo = lo, hi = hi, evaluations = 2))
        }
    }

    "equal sign endpoints prove no roots only with a strict derivative enclosure" {
        val found = search("x+sin(x)", lo = 1, hi = 2, evaluations = 2).shouldBeInstanceOf<RootResult.Found>()
        found.method shouldContain "strict derivative enclosure"
        found.roots shouldBe emptyList()
        // sin has several zeros despite both endpoints having the same sign.
        limited(search("sin(x)", radians, lo = 1, hi = 8, evaluations = 2))
    }

    "a derivative enclosure touching zero does not become a strict certificate" {
        limited(search("x+sin(x)", radians, lo = -4, hi = 4))
        limited(search("cos(x)-x", radians, lo = 0, hi = 2))
    }

    "oscillating functions retain the full scan and all five independently known roots" {
        val found = search("sin(x)", radians, lo = -7, hi = 7, samples = 201, evaluations = 1000)
            .shouldBeInstanceOf<RootResult.Found>()
        found.method.contains("strict derivative enclosure") shouldBe false
        found.roots.size shouldBe 5
        val pi = BigDecimal("3.1415926535897932384626433832795028841971693993751")
        for ((root, multiple) in found.roots.sortedBy { it.x }.zip(-2..2)) {
            (root.x.toBigDecimal(Num.MC).subtract(pi.multiply(BigDecimal(multiple))).abs() < BigDecimal("1e-10")) shouldBe true
        }
    }

    "uncertain coefficients fall back while explicitly known point coefficients can certify" {
        limited(search("approx(2,0.01)*x+sin(x)", radians))
        search("approx(2)*x+sin(x)", radians).shouldBeInstanceOf<RootResult.Failed>().error.kind shouldBe EvalError.Kind.UNSUPPORTED
        val known = search("approx(2,0)*x+sin(x)", radians, evaluations = 8).shouldBeInstanceOf<RootResult.Found>()
        known.method shouldContain "strict derivative enclosure"
        known.roots.single().x shouldBe Num.ZERO
    }

    "certification uses the plotted curve angle mode rather than a separate solver settings argument" {
        val curve = Calculator(radians).plot("cos(x)-x").getOrThrow()
        limited(RootFinder.roots(curve, Num.ZERO, Num.TWO, degrees, 200, RootSearchOptions(maxEvaluations = 64)))
        val grads = Settings(angle = AngleMode.GRAD)
        search("cos(x)-x", grads, lo = 0, hi = 2).shouldBeInstanceOf<RootResult.Found>().method shouldContain "strict derivative enclosure"
    }

    "contextual percent keeps its evaluator meaning and is not differentiated as ordinary subtraction" {
        // x-150% = -x/2, not x-3/2: the true derivative is negative in DEG.
        val result = search("x-150%+sin(x)", samples = 5, evaluations = 12).shouldBeInstanceOf<RootResult.Found>()
        result.method.contains("strict derivative enclosure") shouldBe false
        result.roots.single().x shouldBe Num.ZERO
    }

    "discontinuities and restricted domains are still refused before monotonic analysis" {
        for (expression in listOf("sin(x)/x+x", "x+ln(x)", "x+sign(x)", "x+sqrt(x)")) {
            val error = search(expression).shouldBeInstanceOf<RootResult.Failed>().error
            error.kind shouldBe EvalError.Kind.UNSUPPORTED
            error.message shouldContain "continuity"
        }
    }
})
