package com.vaibhav.calc.core

import com.vaibhav.calc.core.num.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.MathContext

class CertifiedKernelSpec : StringSpec({
    val mc = MathContext(40)
    fun encloses(value: Num, reference: BigDecimal) {
        val interval = value.bounds(100) ?: error("Expected an established enclosure")
        (reference >= interval.lower && reference <= interval.upper) shouldBe true
    }

    "constant enclosures contain independently expanded pi and e" {
        val pi = DecimalEnclosures.constant(Konst.PI, 70)
        (IndependentReference.PI >= pi.lower && IndependentReference.PI <= pi.upper) shouldBe true
        val e = DecimalEnclosures.constant(Konst.E, 70)
        (IndependentReference.E >= e.lower && IndependentReference.E <= e.upper) shouldBe true
    }

    "ordinary elementary kernels contain independent reference digits" {
        encloses(NumMath.exp(Num.ONE, mc), IndependentReference.E)
        encloses(NumMath.ln(Num.TWO, mc), BigDecimal("0.693147180559945309417232121458176568075500134360255254120680009"))
        encloses(NumMath.sin(Num.ONE, mc), BigDecimal("0.841470984807896506652502321630298999622563060798371065672751710"))
        encloses(NumMath.cos(Num.ONE, mc), BigDecimal("0.540302305868139717400936607442976603732310420617922227670097255"))
    }

    "square-root enclosures verify against exact squares at extreme scales" {
        for (text in listOf("2", "1e-5000", "2e2000")) {
            val radicand = BigDecimal(text)
            val root = DecimalEnclosures.sqrt(radicand, 70)
            (root.lower.multiply(root.lower) <= radicand) shouldBe true
            (root.upper.multiply(root.upper) >= radicand) shouldBe true
        }
    }

    "rounded arithmetic propagates input error and new rounding outward" {
        val a = Dec(BigDecimal("1.2"), BigDecimal("0.01"))
        val b = Dec(BigDecimal("2.3"), BigDecimal("0.02"))
        encloses(a + b, BigDecimal("3.53"))
        encloses(a * b, BigDecimal("1.21").multiply(BigDecimal("2.32")))
        encloses(a / b, BigDecimal("1.19").divide(BigDecimal("2.32"), MathContext(100)))
    }
})
