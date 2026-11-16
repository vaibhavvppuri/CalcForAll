package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** Allocation limits are part of the public error contract, not a timeout test. */
class ResourceLimitsSpec : StringSpec({
    "literal budgets reject before integer allocation and remain typed at the parser boundary" {
        val c = Calculator()
        listOf("1e20001", "1e-20001", "1e2147483647", "9".repeat(10_001),
            "0x" + "f".repeat(10_001), "2" + "⁹".repeat(10_001)).forEach {
            c.err(it).kind shouldBe EvalError.Kind.SYNTAX
        }
        c.err("1+".repeat(32_769) + "1").kind shouldBe EvalError.Kind.SYNTAX
        shouldThrow<ArithFault.Overflow> { Num.ofDecimal(BigDecimal("1e2147483647")) }
        shouldThrow<ArithFault.Overflow> { Num.ofDecimal("1".repeat(10_001)) }
    }

    "ordinary long arithmetic and scientific magnitudes still compute exactly" {
        val c = Calculator()
        c.num(List(1800) { "1" }.joinToString("+")) shouldBe Num.of(1800)
        c.num("1e309 * 1e-309") shouldBe Num.ONE
        c.num("1e-309 + 1e-309") shouldBe Num.ofDecimal("2e-309")
        c.num("0.1+0.2") shouldBe Num.of(3, 10)
    }

    "rational arithmetic cancels before allocating products" {
        val n = BigInteger.TWO.pow(100_000)
        val d = BigInteger.valueOf(3).pow(50_000)
        val a = Num.of(n, d)
        val b = Num.of(d, n)
        (a * b) shouldBe Num.ONE
        (a / a) shouldBe Num.ONE
        (a + -a) shouldBe Num.ZERO
        a.compareTo(a) shouldBe 0
        shouldThrow<ArithFault.Overflow> { a * a }
    }

    "large perfect-square fractions reduce operands before multiplying" {
        val n = BigInteger.TWO.pow(100_000)
        val d = BigInteger.valueOf(3).pow(40_000)
        NumMath.sqrt(Num.of(n, d), Num.MC) shouldBe
            Num.of(BigInteger.TWO.pow(50_000), BigInteger.valueOf(3).pow(20_000))
    }

    "portable integer square root agrees with the JVM oracle including adjacent non-squares" {
        for (n in 0L..1000L) integerSquareRoot(BigInteger.valueOf(n)) shouldBe BigInteger.valueOf(n).sqrt()
        val square = (BigInteger.ONE.shiftLeft(20_000) + BigInteger.valueOf(123)).pow(2)
        for (offset in -2L..2L) {
            val n = square + BigInteger.valueOf(offset)
            integerSquareRoot(n) shouldBe n.sqrt()
        }
    }

    "integer Newton roots preserve large perfect cubes and huge indices" {
        val root = BigInteger.ONE.shiftLeft(10_000) + BigInteger.valueOf(3)
        NumMath.root(Num.of(root.pow(3)), BigInteger.valueOf(3), Num.MC) shouldBe Num.of(root)
        val c = Calculator()
        c.num("root(2,1000000000)") shouldBeGreaterThan Num.ONE
        c.num("root(2,1000000000)") shouldBeLessThan Num.ofDecimal("1.01")
        c.num("root(-27,3)") shouldBe Num.of(-3)
    }

    "enormous powers and exponentials become overflow errors" {
        val c = Calculator(Settings(angle = AngleMode.RAD))
        listOf("2^1000000", "2^1000001", "exp(1e309)", "exp(-1e309)",
            "sinh(1e309)", "cosh(1e309)", "tanh(1e309)", "sin(1e309)",
            "10001!", "nPr(20000,10001)").forEach {
            c.err(it).kind shouldBe EvalError.Kind.OVERFLOW
        }
        shouldThrow<ArithFault.Overflow> {
            NumMath.pow(Num.ofDecimal("1e1000"), Dec(BigDecimal("1000.1")), Num.MC)
        }
        c.num("ln(1e309)") shouldBeGreaterThan Num.of(700)
        c.num("exp(1)") shouldBeGreaterThan Num.ofDecimal("2.71828")
    }

    "complex powers share the real exponent budget" {
        val c = Calculator(Settings(complexEnabled = true))
        c.err("i^1000001").kind shouldBe EvalError.Kind.OVERFLOW
        c.num("i^1000000") shouldBe Num.ONE
    }

    "exact angles bypass the decimal argument restriction" {
        val c = Calculator(Settings(angle = AngleMode.RAD))
        c.num("sin((10^1000)*pi)") shouldBe Num.ZERO
        c.num("cos((10^1000)*pi)") shouldBe Num.ONE
    }

    "inexact addition refuses a completely discarded nonzero operand" {
        val c = Calculator(Settings(angle = AngleMode.RAD))
        c.err("(1e80+sqrt(2))-1e80").kind shouldBe EvalError.Kind.UNSUPPORTED
        c.err("1e80-sqrt(2)").kind shouldBe EvalError.Kind.UNSUPPORTED
        c.num("(1e80+1)-1e80") shouldBe Num.ONE
        c.num("sqrt(2)+sqrt(3)") shouldBeGreaterThan Num.ofDecimal("3.1462")
        c.num("sqrt(2)+sqrt(3)") shouldBeLessThan Num.ofDecimal("3.1463")
        c.num("sin(1)-sin(1)").isZero shouldBe true
        c.num("sqrt(2)-sqrt(2)") shouldBe Num.ZERO
    }

    "bounded combinatoric products retain cancellation and small-r large-n cases" {
        val c = Calculator()
        c.num("nCr(1000000000,1)") shouldBe Num.of(1_000_000_000)
        for (n in 0..40) {
            var expected = BigInteger.ONE
            for (r in 0..n) {
                if (r > 0) expected = expected * BigInteger.valueOf((n - r + 1).toLong()) / BigInteger.valueOf(r.toLong())
                NumMath.nCr(Num.of(n.toLong()), Num.of(r.toLong())) shouldBe Num.of(expected)
            }
        }
    }

    "precision and direct decimal construction have explicit budgets" {
        shouldThrow<IllegalArgumentException> { Settings(computePrecision = Int.MAX_VALUE) }
        shouldThrow<IllegalArgumentException> { Settings(displayPrecision = Int.MAX_VALUE) }
        shouldThrow<IllegalArgumentException> { Settings(computePrecision = 51) }
        shouldThrow<IllegalArgumentException> { Settings(computePrecision = 50, displayPrecision = 47) }
        Settings(computePrecision = 50, displayPrecision = 46).computeMc.precision shouldBe 50
        shouldThrow<ArithFault.Overflow> { Num.pi(MathContext(100_000)) }
        shouldThrow<ArithFault.Overflow> { Dec(BigDecimal("1e2147483647")) }
        shouldThrow<ArithFault.Overflow> { Bases.parse("F".repeat(10_002), 16) }
    }
})
