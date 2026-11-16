package com.vaibhav.calc.core

import com.vaibhav.calc.core.ast.Binary
import com.vaibhav.calc.core.ast.BinaryOp
import com.vaibhav.calc.core.ast.Expr
import com.vaibhav.calc.core.ast.NumberLit
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Evaluator
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.source.SourceSpan
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Regression tests for the defects the **B1 evaluation-core battery** surfaced
 * (report `f77ec06`). Each name references the battery id it locks down.
 */
class B1RegressionSpec : StringSpec({

    val c = Calculator()

    // -----------------------------------------------------------------------
    // B1-0035 — a flat sum of ~1,800 terms threw StackOverflowError out of the
    // public API (Article 4 breach).
    //
    // WHY THE EXISTING GUARD MISSED IT, and why these tests are shaped this way:
    // `RegressionSpec`'s "pathologically nested input" test exercises deep parens,
    // deep unary and deep `^` — every one of which recurses *in the parser*, where
    // a depth guard already existed. A flat `1+1+1+…` chain is different: the
    // parser builds it ITERATIVELY into a left-leaning spine, so parser recursion
    // stays ~2 deep and its guard never fires, while the AST handed to the
    // evaluator is N deep. The defence existed for one route; the bug arrived by
    // another (the gap-analysis F-1 pattern).
    //
    // So these tests assert on AST / EVALUATION depth. The second one builds the
    // tree WITHOUT the parser, which a parser-side guard cannot possibly satisfy.
    // -----------------------------------------------------------------------

    "B1-0035: a long FLAT sum evaluates correctly — never a StackOverflowError" {
        // 1,600 evaluated fine before the fix and 1,800 overflowed (measured), so
        // the ladder brackets the old threshold and runs well past it.
        for (n in listOf(500, 1_600, 1_800, 2_000, 5_000)) {
            val expr = List(n) { "1" }.joinToString("+")
            shouldNotThrowAny { c.evaluate(expr) }
            c.num(expr).compareTo(Num.of(n.toLong())) shouldBe 0
        }
    }

    "B1-0035: a long flat chain of MIXED left-associative operators also evaluates" {
        // `-` and `*` build the same left spine; the fix must not be `+`-shaped.
        val minus = "10000" + "-1".repeat(3_000)
        shouldNotThrowAny { c.evaluate(minus) }
        c.num(minus).compareTo(Num.of(7_000)) shouldBe 0

        val times = "1" + "*1".repeat(3_000)
        shouldNotThrowAny { c.evaluate(times) }
        c.num(times).compareTo(Num.ONE) shouldBe 0
    }

    "B1-0035: the evaluator guards AST DEPTH itself, independently of the parser" {
        // Built directly, so no parser guard can be what makes this pass. The tree
        // leans RIGHT, which the iterative left-spine walk deliberately does not
        // flatten — this is the genuinely pathological shape, and it must come back
        // as a TYPED error rather than a thrown StackOverflowError.
        val span = SourceSpan(0, 1)
        var e: Expr = NumberLit(Num.ONE, span)
        repeat(60_000) { e = Binary(BinaryOp.ADD, NumberLit(Num.ONE, span), e, span) }

        var result: CalcResult<*>? = null
        shouldNotThrowAny { result = Evaluator(Settings(), Environment()).eval(e) }
        (result is CalcResult.Err) shouldBe true
    }

    "B1-0035: an ordinary shallow expression is untouched by the depth guard" {
        c.num("1+2*3").compareTo(Num.of(7)) shouldBe 0
        c.num("((((1+2))))").compareTo(Num.of(3)) shouldBe 0
    }

    // -----------------------------------------------------------------------
    // `1..2` returned 0.2 — a CONFIDENT WRONG VALUE, which D-014 names as the
    // worst outcome. It was the only wrong finite value from non-hostile input in
    // all 1,000 battery cases, and a double-tap on the decimal key is exactly what
    // an ordinary user produces by fat-fingering.
    //
    // The mechanism: `1.` lexed as one number and `.2` as another, and implicit
    // multiplication silently folded them — 1.0 × 0.2 = 0.2. Nothing was
    // malformed to the parser; two well-formed literals simply sat side by side.
    // -----------------------------------------------------------------------

    "1..2 is a typed SYNTAX error, not 0.2 (D-014: never a confident wrong value)" {
        c.err("1..2").kind shouldBe EvalError.Kind.SYNTAX
        c.err("1..").kind shouldBe EvalError.Kind.SYNTAX
        c.err("1.2.3").kind shouldBe EvalError.Kind.SYNTAX
        // `..5` was already rejected (a lone `.` is not a number start), but it is
        // pinned here so the whole family is covered by one name.
        c.err("..5").kind shouldBe EvalError.Kind.SYNTAX
    }

    "the fix is surgical — every legitimate decimal form still works" {
        c.num("1.2").compareTo(Num.of(6, 5)) shouldBe 0
        c.num(".5").compareTo(Num.of(1, 2)) shouldBe 0
        c.num("1.").compareTo(Num.ONE) shouldBe 0
        // A trailing dot followed by an OPERATOR is a permissive-parsing case the
        // owner has not ruled on (battery B1-0308) — it must keep working, so this
        // fix cannot be "reject any dot next to a dot-ish thing".
        c.num("1./2").compareTo(Num.of(1, 2)) shouldBe 0
        c.num(".5+.5").compareTo(Num.ONE) shouldBe 0
        c.num("1.5e3").compareTo(Num.of(1500)) shouldBe 0
    }

    // -----------------------------------------------------------------------
    // `1e` evaluated to `e` (Euler's number), contradicting D-016.6: *"numeric-
    // position `e` is always an exponent."* The truncated form fell out of numeric
    // position and was re-read as the constant, so `1e` silently became `1 × e`.
    //
    // D-016.6's whole purpose is that the same keystrokes always mean the same
    // thing; an incomplete exponent that quietly becomes a different operation is
    // precisely what it forbids.
    // -----------------------------------------------------------------------

    "1e is an incomplete exponent — a typed SYNTAX error, not Euler's e" {
        c.err("1e").kind shouldBe EvalError.Kind.SYNTAX
        c.err("2.5e").kind shouldBe EvalError.Kind.SYNTAX
        c.err("1e+").kind shouldBe EvalError.Kind.SYNTAX
        c.err("1e-").kind shouldBe EvalError.Kind.SYNTAX
    }

    "…and it is LOWERCASE-only, because case is load-bearing (D-016.4)" {
        // `E` is the REGISTER, not Euler's constant, so `2E` is `2 × E` — receipted
        // by D-016.4 and pinned by NamesUnitsSpec ("2E" == 10 with 5 stored in E).
        // An earlier draft of this fix treated `e` and `E` alike and took that test
        // down with it: the ambiguity D-016.6 resolves is between the exponent
        // marker and Euler's `e`, both lowercase, and `E` is not part of it.
        val withRegister = Calculator()
        withRegister.route("5 → E")
        withRegister.num("2E").compareTo(Num.of(10)) shouldBe 0
        // and an uppercase exponent with real digits is still scientific notation
        withRegister.num("1E3").compareTo(Num.of(1000)) shouldBe 0
    }

    "the fix is surgical — real exponents and the constant e are both untouched" {
        // valid exponents still work, in every sign and case
        c.num("1e3").compareTo(Num.of(1000)) shouldBe 0
        c.num("1E3").compareTo(Num.of(1000)) shouldBe 0
        c.num("1e+3").compareTo(Num.of(1000)) shouldBe 0
        c.num("2.5e2").compareTo(Num.of(250)) shouldBe 0
        c.num("1e-3").compareTo(Num.of(1, 1000)) shouldBe 0
        // standalone `e` is still the constant, and still usable in arithmetic
        c.num("e").compareTo(Num.ONE) shouldBe 1
        c.num("ln(e)").compareTo(Num.ONE) shouldBe 0
        c.num("2*e").compareTo(c.num("e") * Num.of(2)) shouldBe 0
        // An `e` that merely STARTS a longer name is not an exponent marker at all:
        // `2exp(3)` is implicit multiplication and must keep parsing. Rejecting any
        // digit-adjacent `e` would have broken this.
        c.num("2exp(0)").compareTo(Num.of(2)) shouldBe 0
        c.num("2e3exp(0)").compareTo(Num.of(2000)) shouldBe 0
    }
})
