package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class MathLayoutSpec : StringSpec({
    fun token(text: String, x: Int, y: Int = 20, w: Int = 20, h: Int = 40) =
        MathToken(text, MathBox(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()))
    fun draft(vararg tokens: MathToken) = MathLayout.read(tokens.toList()).shouldBeInstanceOf<MathLayoutResult.Draft>()
    fun value(expression: String) = ((Calculator().evaluate(expression) as CalcResult.Ok).value as RealValue).num

    "ordinary tokens retain stable original indices despite spatial ordering" {
        val d = draft(token("2", 60), token("1", 0), token("+", 30, 30, 20, 20))
        d.expression shouldBe "1+2"
        value(d.expression) shouldBe Num.of(3)
        d.sourceTokens.map { it.text } shouldBe listOf("2", "1", "+")
        d.sourceTokens.map { it.sourceIndex } shouldBe listOf(0, 1, 2)
    }
    "a raised exponent binds to its base and not a following operator" {
        val d = draft(token("2", 0), token("3", 23, 0, 12, 20), token("+", 45, 30, 20, 20), token("1", 75))
        d.expression shouldBe "2^(3)+1"
        value(d.expression) shouldBe Num.of(9)
    }
    "multi digit signed and recursively raised exponents retain scope" {
        draft(token("x", 0), token("2", 23, 0, 12, 20), token("3", 38, 0, 12, 20)).expression shouldBe "x^(23)"
        draft(token("x", 0), token("-", 23, 8, 10, 1), token("1", 35, 0, 12, 20)).expression shouldBe "x^(-1)"
        draft(token("x", 0), token("2", 23, 0, 12, 20), token("3", 40, -15, 8, 12)).expression shouldBe "x^(2^(3))"
    }
    "Unicode superscripts in an OCR word become parser syntax" {
        val d = draft(token("2³+1", 0, 0, 80, 40))
        d.expression shouldBe "2^(3)+1"
        value(d.expression) shouldBe Num.of(9)
    }
    "separate Unicode superscript tokens attach even with full sized OCR boxes" {
        draft(token("x", 0), token("²", 23)).expression shouldBe "x^(2)"
        val d = draft(token("2", 0), token("³", 23), token("+", 50, 30, 20, 20), token("1", 80))
        d.expression shouldBe "2^(3)+1"
        value(d.expression) shouldBe Num.of(9)
        draft(token("x", 0), token("⁻¹", 23)).expression shouldBe "x^(-1)"
    }
    "an orphan superscript or strongly overlapping glyphs are not silently concatenated" {
        MathLayout.read(listOf(token("²", 0))).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathLayout.read(listOf(token("√", 0), token("²", 23))).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathLayout.read(listOf(token("1", 0), token("2", 0))).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathLayout.read(listOf(token("x", 0), token("²", 5))).shouldBeInstanceOf<MathLayoutResult.Rejected>()
    }
    "character level OCR preserves registered function names" {
        val exp = draft(token("e", 0), token("x", 21), token("p", 42), token("(", 63), token("0", 84), token(")", 105))
        exp.expression shouldBe "exp(0)"
        value(exp.expression) shouldBe Num.ONE
        val sin = draft(token("s", 0), token("i", 21), token("n", 42), token("(", 63), token("0", 84), token(")", 105))
        sin.expression shouldBe "sin(0)"
        value(sin.expression) shouldBe Num.ZERO
    }
    "juxtaposed variables and parentheses use explicit multiplication" {
        draft(token("x", 0), token("(", 25), token("2", 50), token(")", 75)).expression shouldBe "x*(2)"
        draft(token("x", 0), token("y", 25)).expression shouldBe "x*y"
        draft(token("x", 0), token("y", 25), token("2", 48, 0, 12, 20)).expression shouldBe "x*y^(2)"
    }
    "a stacked fraction is one atom in a larger expression" {
        val d = draft(token("1", 20, 0, 20, 30), token("-", 10, 40, 40, 2), token("2", 20, 50, 20, 30),
            token("+", 65, 30, 20, 20), token("3", 95))
        d.expression shouldBe "((1)/(2))+3"
        value(d.expression) shouldBe Num.of(7, 2)
    }
    "nested fraction bars recurse without dropping symbols" {
        val d = draft(token("1", 35, 0, 15, 20), token("-", 25, 30, 40, 1), token("2", 35, 40, 15, 20),
            token("-", 10, 75, 70, 1), token("3", 35, 90, 15, 20))
        value(d.expression) shouldBe Num.of(1, 6)
    }
    "an incomplete fraction is refused rather than transcribed as a negative number" {
        MathLayout.read(listOf(token("1", 30, 0, 10, 30), token("-", 10, 32, 50, 1)))
            .shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathLayout.read(listOf(token("-", 10, 0, 50, 1), token("1", 30, 3, 10, 30)))
            .shouldBeInstanceOf<MathLayoutResult.Rejected>()
    }
    "normal unary minus and subtraction retain their meanings" {
        value(draft(token("-", 0, 39, 20, 1), token("1", 30)).expression) shouldBe Num.of(-1)
        value(draft(token("1", 0), token("-", 30, 39, 20, 1), token("1", 60)).expression) shouldBe Num.ZERO
    }
    "the radical overbar includes the whole expression beneath it" {
        val d = draft(token("√", 0, 0, 145, 75), token("1", 50), token("+", 80, 30, 20, 20), token("3", 110))
        d.expression shouldBe "sqrt(1+3)"
        value(d.expression) shouldBe Num.of(2)
    }
    "a separately recognized radical overbar preserves compound scope" {
        val d = draft(token("√", 0, 0, 25, 70), token("-", 20, 0, 125, 1),
            token("1", 50), token("+", 80, 30, 20, 20), token("3", 110))
        value(d.expression) shouldBe Num.of(2)
        d.expression shouldBe "sqrt(1+3)"
    }
    "a root box that already includes the overbar does not count its rule twice" {
        val d = draft(token("√", 0, 0, 145, 75), token("-", 20, 0, 125, 1),
            token("1", 50), token("+", 80, 30, 20, 20), token("3", 110))
        d.expression shouldBe "sqrt(1+3)"
        value(d.expression) shouldBe Num.of(2)
    }
    "a fraction inside a radical keeps both scopes" {
        val d = draft(token("√", 0, 0, 120, 115), token("1", 60, 15, 20, 30), token("-", 45, 55, 50, 1), token("4", 60, 70, 20, 30))
        value(d.expression) shouldBe Num.of(1, 2)
    }
    "a printed root without an overbar takes its following atom or parentheses" {
        value(draft(token("√", 0), token("9", 26)).expression) shouldBe Num.of(3)
        val d = draft(token("√", 0), token("(", 25), token("1", 50), token("+", 75, 30, 20, 20), token("3", 100), token(")", 125))
        value(d.expression) shouldBe Num.of(2)
    }
    "a narrow root consumes a complete multi digit or decimal numeric atom" {
        val integer = draft(token("√", 0), token("1", 26), token("6", 47))
        integer.expression shouldBe "sqrt(16)"
        value(integer.expression) shouldBe Num.of(4)
        val decimal = draft(token("√", 0), token("1", 26), token(".", 48, 60, 0, 0), token("4", 52), token("4", 73))
        decimal.expression shouldBe "sqrt(1.44)"
        value(decimal.expression) shouldBe Num.of(6, 5)
        val leading = draft(token("√", 0), token(".", 26, 60, 0, 0), token("2", 30), token("5", 51))
        value(leading.expression) shouldBe Num.of(1, 2)
    }
    "character level scientific notation is one numeric atom rather than Euler multiplication" {
        val d = draft(token("1", 0), token("e", 21), token("3", 42))
        d.expression shouldBe "1e3"
        value(d.expression) shouldBe Num.of(1000)
        value(draft(token("√", 0), token("1", 26), token("e", 47), token("2", 68)).expression) shouldBe Num.of(10)
        val small = draft(token("1", 0), token("e", 21), token("-", 42, 39, 15, 1), token("2", 60))
        small.expression shouldBe "1e-2"
        value(small.expression) shouldBe Num.of(1, 100)
    }
    "large gaps between numbers are not silently joined or multiplied under a root" {
        MathLayout.read(listOf(token("1", 0), token("6", 400))).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathLayout.read(listOf(token("√", 0), token("1", 26), token("6", 400))).shouldBeInstanceOf<MathLayoutResult.Rejected>()
    }
    "a polynomial equation produced from geometry reaches the equation solver" {
        val d = draft(token("x", 0), token("2", 23, 0, 12, 20), token("-", 45, 40, 15, 1),
            token("5x+6=0", 70, 20, 150, 40))
        val outcome = (Calculator().route(d.expression) as RouteResult.Ok).outcome as Outcome.Roots
        outcome.roots.size shouldBe 2
    }
    "candidate uncertainty is surfaced without inventing confidence" {
        val supplied = token("x", 0).copy(alternatives = listOf("×"), matchDistance = .03f, uncertain = true, sourceIndex = 17)
        val d = draft(supplied)
        d.ambiguous shouldBe true
        d.sourceTokens.single().sourceIndex shouldBe 17
        d.sourceTokens.single().matchDistance shouldBe .03f
        d.warnings.any { "alternatives" in it } shouldBe true
        draft(token("1", 0)).sourceTokens.single().matchDistance shouldBe null
    }
    "mixed numbers are refused until addition versus multiplication is explicit" {
        MathLayout.read(listOf(token("1", 0), token("1", 45, 0, 15, 25), token("-", 35, 35, 40, 1), token("2", 45, 50, 15, 25)))
            .shouldBeInstanceOf<MathLayoutResult.Rejected>()
    }
    "multiple rows and subscripts are refused rather than concatenated" {
        MathLayout.read(listOf(token("1", 0, 0), token("2", 40, 100))).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathLayout.read(listOf(token("x", 0), token("2", 25, 65, 12, 20))).shouldBeInstanceOf<MathLayoutResult.Rejected>()
    }
    "empty nonfinite reversed and oversized inputs are typed refusals" {
        MathLayout.read(emptyList()).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathLayout.read(listOf(token("1", 0).copy(bounds = MathBox(Float.NaN, 0f, 10f, 20f)))).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathLayout.read(listOf(token("1", 0).copy(bounds = MathBox(10f, 0f, 0f, 20f)))).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathLayout.read(List(65) { token("1", it * 30) }).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathLayout.read(listOf(token("1".repeat(2049), 0))).shouldBeInstanceOf<MathLayoutResult.Rejected>()
    }
})
