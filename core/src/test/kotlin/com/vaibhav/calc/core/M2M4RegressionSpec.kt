package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.matrix.Matrix
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Num
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

/** Regression tests for the four findings confirmed by the M2–M4 adversarial review. */
class M2M4RegressionSpec : StringSpec({

    // #1 — sqrt of a negative irrational real must return a clean pure-imaginary,
    // not a spurious DOMAIN error (rounding of the modulus drove the radicand < 0).
    "sqrt(-pi) and sqrt(-e) are pure-imaginary in complex mode, not a domain error" {
        val cx = Calculator(Settings(complexEnabled = true))
        val zp = cx.complexOf("sqrt(-pi)")
        zp.re.compareTo(Num.ZERO) shouldBe 0
        zp.im shouldBeGreaterThan Num.ofDecimal("1.77") // √π ≈ 1.7725
        zp.im shouldBeLessThan Num.ofDecimal("1.78")
        val ze = cx.complexOf("sqrt(-e)")
        ze.re.compareTo(Num.ZERO) shouldBe 0
        ze.im shouldBeGreaterThan Num.ofDecimal("1.64") // √e ≈ 1.6487
        ze.im shouldBeLessThan Num.ofDecimal("1.66")
    }

    // #2 — value-equal matrices with different Num representations must hash equal.
    "Matrix equals/hashCode contract holds across Num representations" {
        val a = Matrix.of(listOf(listOf(Num.of(2))))                     // Rational 2/1
        val b = Matrix.of(listOf(listOf(Dec(BigDecimal("2")))))          // Dec 2
        (a == b) shouldBe true
        (a.hashCode() == b.hashCode()) shouldBe true
        hashSetOf(a).contains(b) shouldBe true
    }

    // #3 — sample variance/stdev are undefined (null) for a single point.
    "sample variance/stdev of a single point are null, population stays 0" {
        val d = (Calculator().statsDescribe(listOf(Num.of(5))) as CalcResult.Ok).value
        d.varianceSample shouldBe null
        d.stdevSample shouldBe null
        d.variancePopulation.compareTo(Num.ZERO) shouldBe 0
    }

    // #4 — toBase/fromBase surface bad input as a typed error, never a throw.
    "base conversion never throws on bad input" {
        val c = Calculator()
        shouldNotThrowAny {
            c.fromBase("GG", 16)
            c.fromBase("", 16)
            c.fromBase("FF", 40)
            c.toBase(Num.ofDecimal("1.5"), 16)
            c.toBase(Num.of(255), 40)
        }
        c.fromBase("GG", 16).errorOrNull()?.kind shouldBe EvalError.Kind.DOMAIN
        c.toBase(Num.ofDecimal("1.5"), 16).errorOrNull()?.kind shouldBe EvalError.Kind.DOMAIN
    }
})
