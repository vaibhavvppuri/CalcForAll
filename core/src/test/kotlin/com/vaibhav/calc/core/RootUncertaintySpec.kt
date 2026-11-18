package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.graph.RootFinder
import com.vaibhav.calc.core.graph.RootResult
import com.vaibhav.calc.core.graph.RootSearchOptions
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class RootUncertaintySpec : StringSpec({
    val settings = Settings(angle = AngleMode.RAD)
    fun roots(expression: String, samples: Int = 16): RootResult = RootFinder.roots(
        Calculator(settings).plot(expression).getOrThrow(), Num.of(-1), Num.ONE, settings, samples,
        RootSearchOptions(maxMillis = 15_000),
    )
    fun unsupported(result: RootResult) {
        result.shouldBeInstanceOf<RootResult.Failed>().error.kind shouldBe EvalError.Kind.UNSUPPORTED
    }

    "an unknown sample value cannot supply a sign or a verified residual" {
        unsupported(roots("sin(x)+approx(0)"))
    }

    "wide sample intervals cannot turn nominal signs into a root bracket" {
        unsupported(roots("sin(x)+approx(0,1)"))
        unsupported(roots("0.00000000000000000001*sin(x)+approx(0,0.000000000000001)"))
    }

    "a constant interval containing zero cannot prove a root-free range" {
        unsupported(roots("approx(0,1)"))
        unsupported(roots("approx(0)"))
        roots("approx(2,0.1)").shouldBeInstanceOf<RootResult.Found>().roots.isEmpty() shouldBe true
    }

    "a nominal zero residual with a wide error bound remains unverified" {
        val result = roots("x-approx(0,0.1)").shouldBeInstanceOf<RootResult.Unverified>()
        result.verified.isEmpty() shouldBe true
        result.candidates.size shouldBe 1
    }

    "a continuity certificate needs a proved nonzero closed denominator" {
        unsupported(roots("sin(x)/approx(1,2)"))
        unsupported(roots("approx(1,2)^x-2"))
    }

    "known-point inputs retain ordinary bounded trigonometric root searches" {
        val root = roots("sin(x)-approx(0.5,0)").shouldBeInstanceOf<RootResult.Found>().roots.single()
        val x = root.x.toBigDecimal(Num.MC)
        (x.subtract(BigDecimal("0.523598775598298873077107230546583814")).abs() < BigDecimal("1e-10")) shouldBe true
        val residual = root.residual as Dec
        (residual.value.abs().add(checkNotNull(residual.absoluteError)) <= BigDecimal("0.000001")) shouldBe true
    }

    "exact sampled zeros and exact polynomial roots remain available" {
        roots("sin(x)", 3).shouldBeInstanceOf<RootResult.Found>().roots.single().x shouldBe Num.ZERO
        val result = RootFinder.roots(Calculator(settings).plot("x^2-4").getOrThrow(), Num.of(-3), Num.of(3), settings)
            .shouldBeInstanceOf<RootResult.Found>()
        result.roots.map { it.x } shouldBe listOf(Num.of(-2), Num.of(2))
        result.roots.all { it.residual.isZero } shouldBe true
    }

    "uncertain coordinates cannot define a nominal search range" {
        val f = Calculator(settings).plot("x").getOrThrow()
        unsupported(RootFinder.roots(f, Dec(BigDecimal.ONE.negate(), BigDecimal("0.1")), Num.ONE, settings))
    }
})
