package com.vaibhav.calc.core.num

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger

/**
 * The M1.1 acceptance criteria, exercised directly at the number-tower level —
 * the exact-first guarantees of Constitution Article 3, before any parser sits
 * on top. `compareTo(...) shouldBe 0` asserts *value* equality.
 */
class NumTowerSpec : StringSpec({

    "0.1 + 0.2 == 0.3 exactly (not 0.30000000000000004)" {
        (Num.ofDecimal("0.1") + Num.ofDecimal("0.2")).compareTo(Num.ofDecimal("0.3")) shouldBe 0
    }

    "1/3 + 1/6 == 1/2 exactly" {
        (Num.of(1, 3) + Num.of(1, 6)).compareTo(Num.of(1, 2)) shouldBe 0
    }

    "(1/3) * 3 == 1 exactly" {
        (Num.of(1, 3) * Num.of(3)).compareTo(Num.ONE) shouldBe 0
    }

    "1/3 stays 1/3 (reduced, not decimalised)" {
        val third = Num.of(2, 6)
        (third is Rational) shouldBe true
        (third as Rational).num shouldBe BigInteger.ONE
        third.den shouldBe BigInteger.valueOf(3)
    }

    "sqrt(8) is exactly 2√2" {
        val s = NumMath.sqrt(Num.of(8), Num.MC)
        (s is Surd) shouldBe true
        s as Surd
        s.coeff shouldBe Num.of(2)
        s.radicand shouldBe BigInteger.valueOf(2)
    }

    "sqrt(2) * sqrt(2) collapses back to exactly 2" {
        val root2 = NumMath.sqrt(Num.TWO, Num.MC)
        (root2 * root2).compareTo(Num.TWO) shouldBe 0
    }

    "sqrt(9) collapses to the integer 3" {
        val s = NumMath.sqrt(Num.of(9), Num.MC)
        (s is Rational) shouldBe true
        s.compareTo(Num.of(3)) shouldBe 0
    }

    "π stays symbolic under scaling and addition" {
        val pi = SymConst.of(Num.ONE, Konst.PI)
        ((pi + pi) is SymConst) shouldBe true
        ((Num.of(3) * pi) is SymConst) shouldBe true
    }

    "cbrt(8) is exactly 2" {
        NumMath.cbrt(Num.of(8), Num.MC).compareTo(Num.of(2)) shouldBe 0
    }
})
