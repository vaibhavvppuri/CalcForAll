package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** D1 + D3 behaviour: functions, angle modes, percent semantics, typed errors. */
class EvaluatorSpec : StringSpec({

    val c = Calculator()

    fun eq(input: String, n: Long) = c.num(input).compareTo(Num.of(n)) shouldBe 0
    fun eq(input: String, a: Long, b: Long) = c.num(input).compareTo(Num.of(a, b)) shouldBe 0

    // --- exact powers, roots, combinatorics ---------------------------------
    "2^10 == 1024" { eq("2^10", 1024) }
    "2^-3 == 1/8" { eq("2^-3", 1, 8) }
    "8^(1/3) == 2 (exact cube root)" { eq("8^(1/3)", 2) }
    "root(27, 3) == 3" { eq("root(27,3)", 3) }
    "5! == 120" { eq("5!", 120) }
    "nCr(5, 2) == 10" { eq("nCr(5,2)", 10) }
    "nPr(5, 2) == 20" { eq("nPr(5,2)", 20) }
    "gcd(12, 18) == 6" { eq("gcd(12,18)", 6) }
    "lcm(4, 6) == 12" { eq("lcm(4,6)", 12) }
    "hypot(3, 4) == 5 (exact)" { eq("hypot(3,4)", 5) }

    // --- rounding family ----------------------------------------------------
    "int(3.7) == 3" { eq("int(3.7)", 3) }
    "int(-3.7) == -3 (toward zero)" { eq("int(-3.7)", -3) }
    "floor(-3.2) == -4" { eq("floor(-3.2)", -4) }
    "ceil(3.2) == 4" { eq("ceil(3.2)", 4) }
    "round(2.5) == 3 (ties away from zero)" { eq("round(2.5)", 3) }
    "mod(7, 3) == 1" { eq("mod(7,3)", 1) }
    "mod(-1, 3) == 2 (floored)" { eq("mod(-1,3)", 2) }
    "min(3, 8) == 3; max(3, 8) == 8" { eq("min(3,8)", 3); eq("max(3,8)", 8) }
    "abs(-5) == 5" { eq("abs(-5)", 5) }

    // --- logarithms ---------------------------------------------------------
    "log(1000) == 3 (exact integer log)" { eq("log(1000)", 3) }
    "log2(1024) == 10" { eq("log2(1024)", 10) }
    "log(2, 8) == 3 (base form)" { eq("log(2,8)", 3) }
    "ln(e) == 1 (exact)" { eq("ln(e)", 1) }

    // --- angle modes --------------------------------------------------------
    "DEG: sin(30) == 0.5" { Calculator(Settings(angle = AngleMode.DEG)).ansOf("sin(30)").decimal shouldBe "0.5" }
    "DEG: cos(60) == 0.5" { Calculator(Settings(angle = AngleMode.DEG)).ansOf("cos(60)").decimal shouldBe "0.5" }
    "DEG: tan(45) == 1" { Calculator(Settings(angle = AngleMode.DEG)).ansOf("tan(45)").decimal shouldBe "1" }
    "RAD: sin(pi/2) == 1" { Calculator(Settings(angle = AngleMode.RAD)).ansOf("sin(pi/2)").decimal shouldBe "1" }
    "DEG: asin(1) == 90" { Calculator(Settings(angle = AngleMode.DEG)).ansOf("asin(1)").decimal shouldBe "90" }
    "DMS: dms(12, 30, 0) == 12.5 degrees" { eq("dms(12,30,0)", 25, 2) }

    // --- percent semantics (spec 1.6) --------------------------------------
    "plain: 50% == 1/2" { eq("50%", 1, 2) }
    "increase: 50 + 10% == 55" { eq("50 + 10%", 55) }
    "decrease: 200 - 10% == 180" { eq("200 - 10%", 180) }
    "of (times): 50 * 20% == 10" { eq("50 * 20%", 10) }
    "of keyword: 20% of 50 == 10" { eq("20% of 50", 10) }
    "percentOf(20, 50) == 10" { eq("percentOf(20,50)", 10) }

    // --- variables & ans ----------------------------------------------------
    "registers: with A=5, 2A+1 == 11" {
        val v = Calculator()
        v.setVariable("A", Num.of(5)) // x/y/z are equation unknowns; A–F, M are registers
        v.num("2A+1").compareTo(Num.of(11)) shouldBe 0
    }
    "ans register carries the previous result" {
        val v = Calculator()
        v.num("2+3").compareTo(Num.of(5)) shouldBe 0
        v.num("ans*2").compareTo(Num.of(10)) shouldBe 0
    }

    // --- typed errors, each with a source position --------------------------
    "1/0 is a typed DIVIDE_BY_ZERO with a span" {
        val e = c.err("1/0")
        e.kind shouldBe EvalError.Kind.DIVIDE_BY_ZERO
        e.span shouldNotBe null
    }
    "sqrt(-4) is a DOMAIN error" { c.err("sqrt(-4)").kind shouldBe EvalError.Kind.DOMAIN }
    "ln(0) is a DOMAIN error" { c.err("ln(0)").kind shouldBe EvalError.Kind.DOMAIN }
    "asin(2) is a DOMAIN error" { c.err("asin(2)").kind shouldBe EvalError.Kind.DOMAIN }
    "2 + * 3 is a SYNTAX error with a span" {
        val e = c.err("2 + * 3")
        e.kind shouldBe EvalError.Kind.SYNTAX
        e.span shouldNotBe null
    }
    "unknown function is UNKNOWN_SYMBOL" { c.err("wibble(2)").kind shouldBe EvalError.Kind.UNKNOWN_SYMBOL }
    "undefined variable is UNKNOWN_SYMBOL" { c.err("q + 1").kind shouldBe EvalError.Kind.UNKNOWN_SYMBOL }
    "wrong arity is an ARITY error" { c.err("sin(1, 2)").kind shouldBe EvalError.Kind.ARITY }
    "huge factorial is an OVERFLOW error" { c.err("200000!").kind shouldBe EvalError.Kind.OVERFLOW }
})
