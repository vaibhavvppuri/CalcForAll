package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.matrix.Matrix
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import com.vaibhav.calc.core.solve.Poly
import com.vaibhav.calc.core.solve.PolynomialRoots
import com.vaibhav.calc.core.solve.Solvers
import com.vaibhav.calc.core.stats.Stats
import com.vaibhav.calc.core.stats.Quartiles
import com.vaibhav.calc.core.stats.Distributions
import com.vaibhav.calc.core.stats.CurveFit
import com.vaibhav.calc.core.everyday.BodyMass
import com.vaibhav.calc.core.matrix.Vectors
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class UncertainDecisionSpec : StringSpec({
    val settings = Settings()
    fun unsupported(text: String) {
        ((Calculator().route(text) as? RouteResult.Err)?.error is EvalError.Unsupported) shouldBe true
    }
    fun verdict(text: String): Boolean = ((Calculator().route(text) as RouteResult.Ok).outcome as Outcome.Verdict).holds

    "equal nominal estimates do not prove an equation" {
        unsupported("approx(1)=1")
        unsupported("approx(1,0.1)=approx(1,0.1)")
        verdict("approx(1,0)=1") shouldBe true
        verdict("approx(1,0.1)=2") shouldBe false
    }

    "written trigonometric identities hold while unresolved logarithmic residuals are undecided" {
        verdict("sin(20)^2+cos(20)^2=1") shouldBe true
        unsupported("2*ln(3)-ln(9)=0")
        verdict("sin(30)=1/2") shouldBe true
    }

    "one stored uncertain quantity equals itself without losing its metadata" {
        val calc = Calculator()
        (calc.route("approx(1,0.1) → A") is RouteResult.Ok) shouldBe true
        ((calc.route("A=A") as RouteResult.Ok).outcome as Outcome.Verdict).holds shouldBe true
        ((calc.route("A=1") as? RouteResult.Err)?.error is EvalError.Unsupported) shouldBe true
    }

    "ambiguous discriminants cannot be declared repeated roots" {
        shouldThrow<ArithFault.Unsupported> {
            Solvers.quadratic(Num.ONE, Num.of(-2), Dec(BigDecimal.ONE, BigDecimal("1e-30")), settings)
        }
        unsupported("x^2-2*e*x+e^2=0")
    }

    "explicit repeated factors retain their exact dependency" {
        val result = (Calculator().route("(x-e)^2=0") as RouteResult.Ok).outcome as Outcome.Roots
        result.roots shouldBe listOf(RealValue(SymConst.of(Num.ONE, Konst.E)))
        val fourth = (Calculator().route("(x-e)^4=0") as RouteResult.Ok).outcome as Outcome.Roots
        fourth.roots shouldBe listOf(RealValue(SymConst.of(Num.ONE, Konst.E)))
    }

    "exact close quadratic roots remain two different roots" {
        val epsilon = Num.ofDecimal(BigDecimal("1e-30"))
        val result = Solvers.quadratic(Num.ONE, Num.of(-2), Num.ONE - epsilon * epsilon, settings)
        result.roots shouldBe listOf(RealValue(Num.ONE - epsilon), RealValue(Num.ONE + epsilon))
    }

    "system uncertainty produces unsupported rather than a false singular verdict" {
        shouldThrow<ArithFault.Unsupported> {
            Solvers.system2x2(Num.ONE, Num.ONE, Num.ONE, Num.ONE,
                Dec(BigDecimal.ONE, BigDecimal("0.1")), Num.TWO, settings)
        }
        shouldThrow<ArithFault.Domain> {
            Solvers.system2x2(Num.ONE, Num.ONE, Num.ONE, Num.ONE, Num.ONE, Num.TWO, settings)
        }
    }

    "higher-degree uncertain coefficients are not replaced by nominal exact data" {
        shouldThrow<ArithFault.Unsupported> {
            PolynomialRoots.solve(Poly(listOf(Dec(BigDecimal.ONE, BigDecimal("0.1")), Num.ONE, Num.ZERO, Num.ONE)), settings)
        }
        unsupported("x^3-pi=0")
    }

    "an exact common symbolic factor can cancel before rational polynomial solving" {
        val pi = SymConst.of(Num.ONE, Konst.PI)
        val result = PolynomialRoots.solve(Poly(listOf(-pi, Num.ZERO, Num.ZERO, pi)), settings)
        result.roots.size shouldBe 3
        result.roots.contains(RealValue(Num.ONE)) shouldBe true
    }

    "Sturm-isolated real roots retain their exact rational bracket as an error bound" {
        val result = PolynomialRoots.solve(Poly(listOf(Num.of(-2), Num.ZERO, Num.ZERO, Num.ONE)), settings)
        val root = (result.roots.filterIsInstance<RealValue>().single().num as Dec)
        (root.absoluteError != null) shouldBe true
        val bounds = root.bounds()!!
        (bounds.lower.pow(3) <= BigDecimal(2)) shouldBe true
        (bounds.upper.pow(3) >= BigDecimal(2)) shouldBe true
    }

    "matrix rank and inverse require a definite pivot but can choose another row" {
        val uncertain = Dec(BigDecimal.ZERO, BigDecimal.ONE)
        val ambiguous = Matrix.of(listOf(listOf(uncertain)))
        shouldThrow<ArithFault.Unsupported> { ambiguous.rank() }
        shouldThrow<ArithFault.Unsupported> { ambiguous.inverse() }
        val alternative = Matrix.of(listOf(listOf(uncertain, Num.ONE), listOf(Num.ONE, Num.ZERO)))
        alternative.rank() shouldBe 2
        alternative.determinant() shouldBe Num.of(-1)
        alternative.inverse().get(1, 1) shouldBe -uncertain
    }

    "matrix equality does not erase uncertainty" {
        val a = Matrix.of(listOf(listOf(Dec(BigDecimal.ONE, BigDecimal("0.1")))))
        val b = Matrix.of(listOf(listOf(Dec(BigDecimal.ONE, BigDecimal("0.2")))))
        (a == b) shouldBe false
    }

    "statistics refuse uncertain ordering instead of publishing an undersized minimum bound" {
        val data = listOf(Dec(BigDecimal.ZERO, BigDecimal.ONE), Dec(BigDecimal(5), BigDecimal.TEN))
        shouldThrow<ArithFault.Unsupported> { Stats.describe(data, Num.MC) }
        shouldThrow<ArithFault.Unsupported> { Quartiles.of(data) }
        shouldThrow<ArithFault.Unsupported> {
            Stats.linearRegression(listOf(data[0] to Num.ONE, data[1] to Num.TWO), Num.MC)
        }
        Stats.describe(listOf(Num.ONE, Num.TWO, Num.of(3)), Num.MC).median shouldBe Num.TWO
    }

    "domain-sensitive tools refuse uncertain observations at their shared core boundary" {
        val unit = Dec(BigDecimal.ONE, BigDecimal("0.1"))
        shouldThrow<ArithFault.Unsupported> { Distributions.binomial(5, 5, unit, Num.MC) }
        shouldThrow<ArithFault.Unsupported> { Distributions.poisson(2, unit, Num.MC) }
        shouldThrow<ArithFault.Unsupported> { Distributions.normalCdf(unit, Num.MC) }
        shouldThrow<ArithFault.Unsupported> { BodyMass.index(Dec(BigDecimal(64), BigDecimal("0.1")), Num.of(160)) }
        shouldThrow<ArithFault.Unsupported> { Vectors.cosine(listOf(unit), listOf(Num.ONE), Num.MC) }
        shouldThrow<ArithFault.Unsupported> {
            CurveFit.exponential(listOf(Num.ONE to unit, Num.TWO to Num.TWO), Num.MC)
        }
        BodyMass.index(Num.of(64), Num.of(160)).band shouldBe BodyMass.Band.OVERWEIGHT
    }
})
