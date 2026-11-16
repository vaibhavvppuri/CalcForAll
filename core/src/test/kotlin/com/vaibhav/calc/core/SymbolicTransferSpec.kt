package com.vaibhav.calc.core

import com.vaibhav.calc.core.symbolic.Symbolic
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SymbolicTransferSpec : StringSpec({
    "polynomials and total real trigonometric derivatives permit reuse with radians context" {
        for (answer in listOf(Symbolic.simplify("(x+1)^2"), Symbolic.factor("x^2-1"),
            Symbolic.differentiate("sin(x)"), Symbolic.integrate("2*x"))) {
            answer.getOrThrow().domainRestricted shouldBe false
        }
    }
    "domain metadata follows both erased original restrictions and generated restrictions" {
        for (answer in listOf(Symbolic.differentiate("ln(x)"), Symbolic.differentiate("ln(y)"),
            Symbolic.simplify("x/x"), Symbolic.integrate("1/x"), Symbolic.differentiate("abs(x)"))) {
            answer.getOrThrow().domainRestricted shouldBe true
        }
    }
})
