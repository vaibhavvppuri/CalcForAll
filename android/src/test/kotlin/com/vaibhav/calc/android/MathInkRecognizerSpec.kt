package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Manually specified, imperfect stroke fixtures; not an accuracy study or a trained-model benchmark. */
class MathInkRecognizerSpec : StringSpec({
    fun path(vararg xy: Int) = xy.toList().chunked(2).map { InkPoint(it[0].toFloat(), it[1].toFloat()) }
    fun draft(strokes: List<List<InkPoint>>) = MathInkRecognizer.read(strokes).shouldBeInstanceOf<MathLayoutResult.Draft>()
    fun value(expression: String) = ((Calculator().evaluate(expression) as CalcResult.Ok).value as RealValue).num

    "slanted ones and an imperfect plus are read regardless of stroke order" {
        val ink = listOf(path(10, 10, 14, 90), path(45, 49, 112, 52), path(78, 18, 80, 83), path(144, 10, 148, 90))
        val first = draft(ink)
        first.expression shouldBe "1+1"
        value(first.expression) shouldBe Num.of(2)
        draft(ink.reversed()).expression shouldBe first.expression
    }
    "a fraction bar does not merge numerator and denominator strokes into plus" {
        val d = draft(listOf(path(30, 0, 31, 30), path(10, 42, 70, 43), path(30, 56, 31, 86)))
        d.expression shouldBe "((1)/(1))"
        value(d.expression) shouldBe Num.ONE
    }
    "a raised smaller seven remains an exponent" {
        val d = draft(listOf(path(10, 35, 12, 110), path(28, 1, 57, 2, 39, 25)))
        d.expression shouldBe "1^(7)"
    }
    "a continuous radical overbar excludes the nine from glyph grouping" {
        val circle = (0..24).map { k ->
            val angle = 2 * PI * k / 24
            InkPoint((70 + 15 * cos(angle)).toFloat(), (35 + 15 * sin(angle)).toFloat())
        }
        val d = draft(listOf(path(0, 55, 18, 78, 42, 2, 100, 0), circle, path(85, 35, 82, 55, 75, 70, 58, 70)))
        d.expression shouldBe "sqrt(9)"
        value(d.expression) shouldBe Num.of(3)
    }
    "equals retains its two separate bars in an equation" {
        val d = draft(listOf(path(0, 0, 0, 80), path(30, 30, 90, 31), path(30, 51, 90, 50), path(120, 0, 120, 80)))
        d.expression shouldBe "1=1"
    }
    "single tap decimals and a three stroke division sign retain their meaning" {
        val d = draft(listOf(path(0, 0, 0, 80), path(25, 80), path(50, 0, 50, 80)))
        d.expression shouldBe "1.1"
        val division = draft(listOf(path(0, 0, 0, 80), path(30, 40, 90, 40), path(60, 20), path(60, 60), path(120, 0, 120, 80)))
        division.expression shouldBe "1/1"
        value(division.expression) shouldBe Num.ONE
    }
    "cross shaped multiplication and variable x remain explicit alternatives" {
        val d = draft(listOf(path(0, 0, 80, 80), path(80, 0, 0, 80)))
        d.ambiguous shouldBe true
        val token = d.sourceTokens.single()
        (token.text == "×" || token.text == "x") shouldBe true
        (token.alternatives + token.text).containsAll(listOf("×", "x")) shouldBe true
    }
    "y and z are available as geometry candidates without claiming word recognition" {
        InkRecognizer.recognize(listOf(path(0, 0, 40, 40, 80, 0), path(40, 40, 40, 80))).map { it.symbol }.contains("y") shouldBe true
        InkRecognizer.recognize(listOf(path(0, 0, 80, 0, 0, 80, 80, 80))).first().symbol shouldBe "z"
    }
    "input and point budgets reject before recognition" {
        MathInkRecognizer.read(emptyList()).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathInkRecognizer.read(List(65) { path(0, 0, 0, 80) }).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathInkRecognizer.read(listOf(List(12001) { InkPoint(0f, 0f) })).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathInkRecognizer.read(listOf(listOf(InkPoint(Float.NaN, 0f)))).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        MathInkRecognizer.read(listOf(listOf(InkPoint(Float.MAX_VALUE, 0f)))).shouldBeInstanceOf<MathLayoutResult.Rejected>()
    }
})
