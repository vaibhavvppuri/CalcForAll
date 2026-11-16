package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.NumMath
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Regression tests — every bug the adversarial review confirmed, pinned so it
 * cannot return. Each name references the finding it locks down.
 */
class RegressionSpec : StringSpec({

    val c = Calculator()

    // #1 — a huge-exponent literal is a typed SYNTAX error, not a thrown exception
    "huge-exponent literals never crash the engine" {
        c.err("1e2000000000").kind shouldBe EvalError.Kind.SYNTAX
        c.err("1e-2000000000").kind shouldBe EvalError.Kind.SYNTAX
        shouldNotThrowAny { c.evaluate("1e999999999") }
        // ordinary scientific notation still works
        c.num("1.5e3").compareTo(Num.of(1500)) shouldBe 0
    }

    // #7/#12 — deep nesting is a typed SYNTAX error, not a StackOverflowError
    "pathologically nested input returns a typed error, never StackOverflow" {
        c.err("(".repeat(4000)).kind shouldBe EvalError.Kind.SYNTAX
        c.err("-".repeat(4000) + "1").kind shouldBe EvalError.Kind.SYNTAX
        c.err("2" + "^2".repeat(4000)).kind shouldBe EvalError.Kind.SYNTAX
        // a sanely nested expression is still fine
        c.num("((((1+2))))").compareTo(Num.of(3)) shouldBe 0
    }

    // #2/#3/#4/#11/#14/#16 — root index no longer truncates via toInt() and no
    // longer hangs; huge indices fall to the decimal path with the right value
    "large root indices are correct and bounded" {
        val a = c.num("root(8, 4294967299)") // ~1.0000000005, NOT the cube root 2
        a shouldBeGreaterThan Num.ONE
        a shouldBeLessThan Num.of(2)
        shouldNotThrowAny { c.evaluate("root(2, 1000000000)") }
        c.num("root(2, 1000000000)") shouldBeGreaterThan Num.ONE
        c.num("root(2, 1000000000)") shouldBeLessThan Num.ofDecimal("1.01")
        // n within int range but larger than the base's bit length: no spurious error
        c.evaluate("root(8, 3000000000)").isOk shouldBe true
        // exact small roots still exact
        c.num("root(27, 3)").compareTo(Num.of(3)) shouldBe 0
    }

    // #6 — negative base with an odd-q rational exponent is consistent
    "(-8)^(2/3) and (-2)^(2/3) both evaluate (odd-root convention)" {
        c.num("(-8)^(2/3)").compareTo(Num.of(4)) shouldBe 0
        val v = c.num("(-2)^(2/3)") // cbrt(4) ~= 1.5874, not a DOMAIN error
        v shouldBeGreaterThan Num.ofDecimal("1.5")
        v shouldBeLessThan Num.ofDecimal("1.6")
    }

    // #9 — nPr/nCr are bounded like factorial
    "oversized nPr/nCr return OVERFLOW instead of hanging" {
        c.err("nPr(2000000, 2000000)").kind shouldBe EvalError.Kind.OVERFLOW
        c.err("nCr(2000000, 1000000)").kind shouldBe EvalError.Kind.OVERFLOW
        // but a cheap large-n, small-r combination still computes
        c.num("nCr(1000000000, 1)").compareTo(Num.of(1_000_000_000L)) shouldBe 0
    }

    // #8 — DMS applies the degree sign to the whole angle
    "dms(-1, 30, 0) is -1.5 degrees, not -0.5" {
        c.num("dms(-1, 30, 0)").compareTo(Num.of(-3, 2)) shouldBe 0
        c.num("dms(12, 30, 0)").compareTo(Num.of(25, 2)) shouldBe 0
    }

    // #10 — the formatter never fabricates trailing zeros that hide rounding
    "a value with more digits than displayPrecision shows in scientific" {
        c.ansOf("123456789012345").decimal shouldBe "1.23456789012e14"
        c.ansOf("123456789012345").exact shouldBe "123456789012345" // exact form is full
    }

    // #15 — an UNNECESSARY rounding mode is rejected up front, not at the display path
    "Settings rejects RoundingMode.UNNECESSARY" {
        shouldThrow<IllegalArgumentException> {
            Settings(rounding = RoundingMode.UNNECESSARY)
        }
    }

    // #5 — a tight-denominator terminating fraction converts exactly
    "1/2^128 converts to an exact decimal without throwing" {
        val r = Num.of(BigInteger.ONE, BigInteger.TWO.pow(128))
        val bd = r.toExactBigDecimalOrNull()
        bd shouldNotBe null
        bd!!.scale() shouldBe 128
    }

    // #13 — compareTo distinguishes distinct-but-very-close reals
    "compareTo does not report sqrt(2) equal to a 45-digit truncation of it" {
        val truncated = Num.ofDecimal("1.41421356237309504880168872420969807856967187")
        (NumMath.sqrt(Num.TWO, Num.MC) shouldBeGreaterThan truncated)
    }
})
