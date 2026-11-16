package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * **D-028 — permissive parsing is POLICY, not an accident.**
 *
 * The owner ruled ACCEPT on all ten permissive-parsing inputs the B1 battery
 * surfaced. Each produces the mathematically correct answer from forgiving input,
 * so refusing it would cost the user something and buy nothing.
 *
 * **Why these pins exist at all.** Nine of the ten fell out of the implementation
 * rather than a decision — `[`/`{` are aliased to `(` in the tokenizer's symbol
 * map, whitespace is skipped, unary operators nest naturally in a Pratt parser,
 * and fullwidth digits work because Kotlin's `Char.isDigit()` happens to return
 * true for `U+FF10`–`U+FF19`. Behaviour that arrives by accident **leaves
 * silently**: a future tokenizer refactor could delete any of it and every
 * existing test would still pass. Ratifying without pinning would change nothing,
 * which is the whole point of writing them down here.
 *
 * The counterweight is D-014 and stays in force: where a forgiving reading yields
 * a **wrong** value (`1..2 → 0.2`), the input is refused. Permissive on form,
 * strict on meaning — see [B1RegressionSpec].
 */
class D028PermissiveSpec : StringSpec({

    val c = Calculator()

    // --- 1-4 · unary chains ------------------------------------------------

    "D-028.1: `2++3` is 5 — a unary plus may follow a binary operator" {
        c.num("2++3").compareTo(Num.of(5)) shouldBe 0
    }

    "D-028.2: `--1` is 1 — double unary minus" {
        c.num("--1").compareTo(Num.ONE) shouldBe 0
        // and the sign still actually tracks: an odd run stays negative
        c.num("---1").compareTo(Num.of(-1)) shouldBe 0
    }

    "D-028.3: `+-1` is -1 — mixed unary prefix" {
        c.num("+-1").compareTo(Num.of(-1)) shouldBe 0
        c.num("-+1").compareTo(Num.of(-1)) shouldBe 0
    }

    "D-028.4: `+ 1` is 1 — a leading unary plus is not a syntax error" {
        c.num("+ 1").compareTo(Num.ONE) shouldBe 0
        c.num("+1").compareTo(Num.ONE) shouldBe 0
    }

    // --- 5 · trailing-dot literal ------------------------------------------

    "D-028.5: `1./2` is 1/2 — a trailing dot ends the literal, it does not void it" {
        c.num("1./2").compareTo(Num.of(1, 2)) shouldBe 0
        c.num("1.").compareTo(Num.ONE) shouldBe 0
    }

    // --- 6-7 · bracket and brace grouping ----------------------------------

    "D-028.6: `[1+2]` is 3 — brackets group" {
        c.num("[1+2]").compareTo(Num.of(3)) shouldBe 0
        c.num("2*[1+2]").compareTo(Num.of(6)) shouldBe 0
    }

    "D-028.7: `{1+2}` is 3 — braces group" {
        c.num("{1+2}").compareTo(Num.of(3)) shouldBe 0
        c.num("2*{1+2}").compareTo(Num.of(6)) shouldBe 0
    }

    "D-028.6/7: bracket forms mix with parens and still respect precedence" {
        c.num("[1+2]*(3+4)").compareTo(Num.of(21)) shouldBe 0
        c.num("2*[3+{4-1}]").compareTo(Num.of(12)) shouldBe 0
    }

    // --- 8 · whitespace ----------------------------------------------------

    "D-028.8: leading and embedded whitespace is skipped, including tabs" {
        c.num("\t1+1").compareTo(Num.of(2)) shouldBe 0
        c.num("  1  +  1  ").compareTo(Num.of(2)) shouldBe 0
        // The battery's case (B1-0147) leads with a TAB then a NEWLINE. Newline is
        // the system separator (D-016.3), so a leading one must not turn a single
        // expression into an empty first equation.
        c.num("\t\n1+1").compareTo(Num.of(2)) shouldBe 0
    }

    // --- 9 · negative zero -------------------------------------------------

    "D-028.9: `-0` folds to 0 — an exact tower has no signed zero" {
        c.num("-0").compareTo(Num.ZERO) shouldBe 0
        // It must also DISPLAY as `0`: a rendered `-0` would be the float64 wart
        // this engine exists to avoid, and it would not round-trip cleanly.
        c.ansOf("-0").exact shouldBe "0"
        c.num("0-0").compareTo(Num.ZERO) shouldBe 0
    }

    // --- 10 · fullwidth CJK digits -----------------------------------------

    "D-028.10: fullwidth CJK digits are digits — `１+２` is 3" {
        // U+FF11 `１`, U+FF12 `２`.
        c.num("１+２").compareTo(Num.of(3)) shouldBe 0
        // a multi-digit fullwidth number reads positionally
        c.num("１２３").compareTo(Num.of(123)) shouldBe 0
    }

    "D-028.10: the accepted set is ACTUALLY every Unicode decimal digit, not just fullwidth" {
        // MEASURED, not assumed. An earlier draft of this spec asserted that
        // Devanagari was rejected and D-028's text said so; the assertion failed
        // and the claim was wrong. `Char.isDigit()` is true for the whole Unicode
        // Nd category, and `java.math.BigDecimal(String)` decodes those digits too,
        // so every decimal script parses — fullwidth was never the boundary.
        c.num("१+२").compareTo(Num.of(3)) shouldBe 0        // Devanagari  U+0967/8
        c.num("१२३").compareTo(Num.of(123)) shouldBe 0      // reads positionally
        c.num("١+٢").compareTo(Num.of(3)) shouldBe 0        // Arabic-Indic U+0661/2
        c.num("১+২").compareTo(Num.of(3)) shouldBe 0        // Bengali     U+09E7/8
    }

    "D-028.10: scripts may even be MIXED within one expression — recorded, not endorsed" {
        // `१+1` is 2. Mathematically correct, so it is inside D-028's criterion and
        // is pinned as current behaviour — but whether a single expression SHOULD
        // mix numeral systems is an M8 localisation question (spec 14.4), not a
        // thing this decision settles. Pinned so the answer is a choice next time,
        // not a discovery.
        c.num("१+1").compareTo(Num.of(2)) shouldBe 0
    }

    // --- the counterweight -------------------------------------------------

    "D-028 does NOT loosen meaning: a forgiving read that would be WRONG is still refused" {
        // The boundary of this decision, asserted rather than described. D-014
        // governs here and outranks permissiveness.
        c.err("1..2").kind shouldBe EvalError.Kind.SYNTAX
        c.err("1e").kind shouldBe EvalError.Kind.SYNTAX
    }
})
