package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.ComplexValue
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.num.Complex
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** M2 — D2 (base conversion) + D5 (complex numbers, linear/quadratic/systems). */
class M2Spec : StringSpec({

    val c = Calculator()
    fun cx(re: Long, im: Long) = Complex(Num.of(re), Num.of(im))

    // --- complex arithmetic (spec 2.5) ---------------------------------------
    "i*i demotes to the real -1" { c.num("i*i").compareTo(Num.of(-1)) shouldBe 0 }
    "i^2 == -1" { c.num("i^2").compareTo(Num.of(-1)) shouldBe 0 }
    "(2+3i) + (1-i) == 3+2i" { c.complexOf("(2+3i)+(1-i)") shouldBe cx(3, 2) }
    "(2+3i) * (1-i) == 5+i" { c.complexOf("(2+3i)*(1-i)") shouldBe cx(5, 1) }
    "(2+3i) / (1-i) == -1/2 + 5/2 i" {
        c.complexOf("(2+3i)/(1-i)") shouldBe Complex(Num.of(-1, 2), Num.of(5, 2))
    }
    "(1+i)^2 == 2i" { c.complexOf("(1+i)^2") shouldBe cx(0, 2) }
    "conj(2+3i) == 2-3i" { c.complexOf("conj(2+3i)") shouldBe cx(2, -3) }
    "abs(3+4i) == 5 (exact)" { c.num("abs(3+4i)").compareTo(Num.of(5)) shouldBe 0 }
    "re and im" {
        c.num("re(2+3i)").compareTo(Num.of(2)) shouldBe 0
        c.num("im(2+3i)").compareTo(Num.of(3)) shouldBe 0
    }
    "arg(i) == 90 degrees" { Calculator(Settings()).ansOf("arg(i)").decimal shouldBe "90" }
    "sqrt(-4) is 2i when complex mode is on" {
        Calculator(Settings(complexEnabled = true)).complexOf("sqrt(-4)") shouldBe cx(0, 2)
    }
    "sqrt(-4) is a DOMAIN error when complex mode is off" {
        c.err("sqrt(-4)").kind shouldBe EvalError.Kind.DOMAIN
    }
    "complex formatting" {
        c.ansOf("2+3i").exact shouldBe "2 + 3i"
        c.ansOf("(2+3i)*i").exact shouldBe "-3 + 2i"
        c.ansOf("conj(i)").exact shouldBe "-i"
        c.ansOf("i").exact shouldBe "i"
    }

    // --- base conversion, radix 2..36 (spec 2.1) -----------------------------
    "hex/binary/octal literals" {
        c.num("0xFF").compareTo(Num.of(255)) shouldBe 0
        c.num("0b1010").compareTo(Num.of(10)) shouldBe 0
        c.num("0o17").compareTo(Num.of(15)) shouldBe 0
        c.num("0xFF + 1").compareTo(Num.of(256)) shouldBe 0
    }
    "toBase / fromBase across radixes" {
        fun tb(v: Long, r: Int) = (c.toBase(Num.of(v), r) as CalcResult.Ok).value
        fun fb(s: String, r: Int) = (c.fromBase(s, r) as CalcResult.Ok).value
        tb(255, 16) shouldBe "FF"
        tb(255, 2) shouldBe "11111111"
        tb(35, 36) shouldBe "Z"
        fb("FF", 16).compareTo(Num.of(255)) shouldBe 0
        fb("Z", 36).compareTo(Num.of(35)) shouldBe 0
        (c.toBase(fb("1010", 2), 2) as CalcResult.Ok).value shouldBe "1010"
    }

    // --- linear / quadratic / systems (spec 5.1, 5.2, 5.4) -------------------
    "linear 2x - 6 = 0 -> x = 3" {
        val r = (c.solveLinear(Num.of(2), Num.of(-6)) as CalcResult.Ok).value
        r.root.compareTo(Num.of(3)) shouldBe 0
    }
    "quadratic x^2 - 3x + 2 -> roots 2 and 1" {
        val q = (c.solveQuadratic(Num.of(1), Num.of(-3), Num.of(2)) as CalcResult.Ok).value
        q.discriminant.compareTo(Num.of(1)) shouldBe 0
        val roots = q.roots.map { (it as RealValue).num }
        (roots.any { it.compareTo(Num.of(2)) == 0 } && roots.any { it.compareTo(Num.of(1)) == 0 }) shouldBe true
    }
    "quadratic x^2 - 2 -> exact roots ±√2" {
        val q = (c.solveQuadratic(Num.of(1), Num.of(0), Num.of(-2)) as CalcResult.Ok).value
        // Order updated by C-6 (see RootOrderSpec): roots are ASCENDING, so the
        // negative surd comes first. This assertion pinned the old descending order
        // — the one the design contradicts — so reversing it here is the point of
        // that change, not collateral damage from it. The VALUES are unchanged.
        Formatter.exact(q.roots[0], c.settings) shouldBe "-√2"
        Formatter.exact(q.roots[1], c.settings) shouldBe "√2"
    }
    "quadratic x^2 - 2x + 1 -> repeated root 1" {
        val q = (c.solveQuadratic(Num.of(1), Num.of(-2), Num.of(1)) as CalcResult.Ok).value
        q.roots.size shouldBe 1
        (q.roots[0] as RealValue).num.compareTo(Num.of(1)) shouldBe 0
    }
    "quadratic x^2 + 1 -> complex roots ±i" {
        val q = (c.solveQuadratic(Num.of(1), Num.of(0), Num.of(1)) as CalcResult.Ok).value
        q.discriminant.compareTo(Num.of(-4)) shouldBe 0
        (q.roots[0] as ComplexValue).cx shouldBe cx(0, 1)
        (q.roots[1] as ComplexValue).cx shouldBe cx(0, -1)
    }
    "quadratic steps are produced" {
        val q = (c.solveQuadratic(Num.of(1), Num.of(-3), Num.of(2)) as CalcResult.Ok).value
        (q.steps.size >= 3) shouldBe true
    }
    "quadratic with a = 0 is a DOMAIN error" {
        c.solveQuadratic(Num.of(0), Num.of(1), Num.of(2)).errorOrNull()?.kind shouldBe EvalError.Kind.DOMAIN
    }
    "system 2x2: x+y=5, x-y=1 -> (3, 2)" {
        val s = (c.solveSystem2(Num.of(1), Num.of(1), Num.of(5), Num.of(1), Num.of(-1), Num.of(1)) as CalcResult.Ok).value
        s.solution[0].compareTo(Num.of(3)) shouldBe 0
        s.solution[1].compareTo(Num.of(2)) shouldBe 0
    }
    "singular 2x2 system is a DOMAIN error" {
        c.solveSystem2(Num.of(1), Num.of(1), Num.of(2), Num.of(2), Num.of(2), Num.of(5))
            .errorOrNull()?.kind shouldBe EvalError.Kind.DOMAIN
    }
    "system 3x3 -> (1, 2, 3)" {
        val s = (
            c.solveSystem3(
                Num.of(1), Num.of(1), Num.of(1), Num.of(6),
                Num.of(1), Num.of(-1), Num.of(1), Num.of(2),
                Num.of(2), Num.of(1), Num.of(1), Num.of(7),
            ) as CalcResult.Ok
            ).value
        s.solution[0].compareTo(Num.of(1)) shouldBe 0
        s.solution[1].compareTo(Num.of(2)) shouldBe 0
        s.solution[2].compareTo(Num.of(3)) shouldBe 0
    }
})
