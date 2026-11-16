package com.vaibhav.calc.core

import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.graph.RootFinder
import com.vaibhav.calc.core.graph.RootResult
import com.vaibhav.calc.core.num.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class RootDiscontinuitySpec : StringSpec({
    val settings = Settings(angle = AngleMode.RAD)
    fun search(text: String, lo: Num = Num.ZERO, hi: Num = Num.ONE, samples: Int = 200): RootResult =
        RootFinder.roots(Calculator(settings).plot(text).getOrThrow(), lo, hi, settings, samples)

    "a tiny-scaled pole is refused despite passing the old absolute residual tolerance" {
        val pole = Num.ofDecimal("0.1234567")
        val oldCandidate = Num.ofDecimal("0.12345669999977236791471740109237594")
        val residual = (Num.ofDecimal("1e-30") / (oldCandidate - pole)).abs()
        (residual < RootFinder.VERIFY_TOLERANCE) shouldBe true
        search("1e-30/(x-0.1234567)").shouldBeInstanceOf<RootResult.Failed>().error.message shouldContain "variable-dependent denominator"
    }

    "a scaled jump with no zero cannot become a numeric root" {
        // floor is -1 or 0 throughout this interval, so the value is always -s or +s.
        search("1e-30*(2*floor(x-0.1234567)+1)")
            .shouldBeInstanceOf<RootResult.Failed>().error.message shouldContain "function 'floor'"
    }

    "nearby initial samples do not let a pole set an inflated residual scale" {
        val lo = Num.ofDecimal("0.1234566999999999999999999999999999999999")
        val hi = Num.ofDecimal("0.13")
        search("1e-30/(x-0.1234567)", lo, hi, 2)
            .shouldBeInstanceOf<RootResult.Failed>()
    }

    "one-sided approach to zero does not certify a jump as a root" {
        // Left values approach zero from below; right values jump to at least 1e-30.
        // There is no zero, including at the discontinuity itself.
        search("1e-30*(x-0.1234567+floor(x-0.1234567)+1)")
            .shouldBeInstanceOf<RootResult.Failed>().error.message shouldContain "function 'floor'"
    }

    "small-scale continuous numeric roots remain detectable" {
        for (scale in listOf("1", "1e-30", "1e-300")) {
            val found = search("$scale*sin(x-0.1234567)").shouldBeInstanceOf<RootResult.Found>()
            found.roots.size shouldBe 1
            ((found.roots.single().x - Num.ofDecimal("0.1234567")).abs() < Num.ofDecimal("1e-12")) shouldBe true
            found.method shouldContain "relative residual"
            found.method shouldContain "assume continuity"
            found.method shouldContain "not a completeness proof"
        }
    }

    "a small-scale polynomial of degree three retains its numeric root" {
        val found = search("1e-300*(x^3-2)", Num.ONE, Num.TWO, 2).shouldBeInstanceOf<RootResult.Found>()
        found.roots.size shouldBe 1
        ((found.roots.single().x - Num.ofDecimal("1.259921049894873164767210607278228350570" )).abs() < Num.ofDecimal("1e-12")) shouldBe true
        (found.roots.single().residual < Num.ofDecimal("1e-306")) shouldBe true
    }

    "removable denominator and logarithm holes are refused before candidate searching" {
        val p = "0.1234567"
        search("(x-$p)^2/(x-$p)").shouldBeInstanceOf<RootResult.Failed>().error.message shouldContain "variable-dependent denominator"
        search("(x-$p)*ln(abs(x-$p))").shouldBeInstanceOf<RootResult.Failed>().error.message shouldContain "function 'ln'"
        search("(x-$p)^(-1)").shouldBeInstanceOf<RootResult.Failed>().error.message shouldContain "negative or non-integer power"
    }

    "closed nonzero denominators use the curve's actual memory and remain supported" {
        val calc = Calculator(settings)
        calc.environment.set("A",RealValue(Num.TWO))
        val found = calc.graphRoots(calc.plot("sin(x-0.1234567)/A").getOrThrow(), Num.ZERO, Num.ONE)
            .shouldBeInstanceOf<RootResult.Found>()
        found.roots.size shouldBe 1
        ((found.roots.single().x - Num.ofDecimal("0.1234567")).abs() < Num.ofDecimal("1e-12")) shouldBe true
        calc.environment.set("A",RealValue(Num.ZERO))
        calc.graphRoots(calc.plot("sin(x-0.1234567)/A").getOrThrow(), Num.ZERO, Num.ONE)
            .shouldBeInstanceOf<RootResult.Failed>()
    }

    "positive closed-base exponential roots are allowed while restricted functions explain refusal" {
        search("2^x-1.5").shouldBeInstanceOf<RootResult.Found>().roots.size shouldBe 1
        for (input in listOf("tan(x)","log(x)","asin(x)","sign(x)","x!")) {
            search(input).shouldBeInstanceOf<RootResult.Failed>()
        }
    }
})
