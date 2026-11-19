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

/** Synthetic geometry regressions, not a handwriting-accuracy benchmark. */
class InkPunctuationSpec : StringSpec({
    fun path(vararg xy: Int) = xy.toList().chunked(2).map { InkPoint(it[0].toFloat(), it[1].toFloat()) }
    fun oval(cx: Int, cy: Int, rx: Int, ry: Int) = (0..36).map { i ->
        InkPoint((cx + rx * cos(2 * PI * i / 36)).toFloat(), (cy + ry * sin(2 * PI * i / 36)).toFloat())
    }
    fun shifted(paths: List<List<InkPoint>>, x: Float) = paths.map { line -> line.map { it.copy(x = it.x + x) } }
    fun draft(ink: List<List<InkPoint>>) = MathInkRecognizer.read(ink).shouldBeInstanceOf<MathLayoutResult.Draft>()
    fun value(expression: String) = ((Calculator().evaluate(expression) as CalcResult.Ok).value as RealValue).num
    val percent = listOf(oval(22, 22, 14, 16), path(85, 0, 15, 100), oval(78, 78, 15, 17))
    val bang = listOf(path(2, 0, 0, 78), path(1, 108))
    val five = path(80, 0, 9, 1, 10, 37, 48, 33, 73, 45, 80, 61, 64, 77, 25, 80, 1, 69)

    "percent is a geometry candidate across stroke directions and order" {
        InkRecognizer.recognize(percent).first().symbol shouldBe "%"
        InkRecognizer.recognize(percent.reversed().map { it.reversed() }).first().symbol shouldBe "%"
    }
    "a separate tapped dot distinguishes a bang from a plain one" {
        InkRecognizer.recognize(bang).first().symbol shouldBe "!"
        InkRecognizer.recognize(listOf(bang.first())).first().symbol shouldBe "1"
    }
    "tap-only input cannot become a non-dot glyph" {
        InkRecognizer.recognize(listOf(path(5, 5), path(50, 50))) shouldBe emptyList()
        InkRecognizer.recognize(listOf(path(5, 5, 5, 5))).single().symbol shouldBe "."
        draft(listOf(path(5, 5))).sourceTokens.single().text shouldBe "."
    }
    "a percent expression keeps all three disconnected parts as one uncertain symbol" {
        val result = draft(listOf(path(0, 0, 0, 100)) + shifted(percent, 50f))
        result.expression shouldBe "1%"
        value(result.expression) shouldBe Num.of(1, 100)
        result.sourceTokens.size shouldBe 2
        result.sourceTokens.last().uncertain shouldBe true
        draft((listOf(path(0, 0, 0, 100)) + shifted(percent, 50f)).reversed()).expression shouldBe result.expression
    }
    "a factorial after five produces 120 rather than a concatenated one and dot" {
        val result = draft(listOf(five) + shifted(bang, 115f))
        result.expression shouldBe "5!"
        value(result.expression) shouldBe Num.of(120)
        result.sourceTokens.last().uncertain shouldBe true
    }
    "spaced line vocabulary also accepts percent and factorial" {
        val p = InkLineRecognizer.recognize(listOf(path(0, 0, 0, 100)) + shifted(percent, 50f))
            .shouldBeInstanceOf<InkLineResult.Draft>()
        p.text shouldBe "1%"
        val f = InkLineRecognizer.recognize(listOf(five) + shifted(bang, 115f))
            .shouldBeInstanceOf<InkLineResult.Draft>()
        f.text shouldBe "5!"
    }
    "ordinary decimal dots stay beside digits and do not become factorials" {
        val decimal = draft(listOf(path(0, 0, 0, 80), path(25, 80), path(50, 0, 50, 80)))
        decimal.expression shouldBe "1.1"
        value(decimal.expression) shouldBe Num.of(11, 10)
        draft(listOf(path(0, 0, 0, 80), path(25, 80))).expression shouldBe "1."
    }
    "baseline zero division zero is never a percent grouping" {
        val division = draft(listOf(oval(20, 40, 20, 40), path(140, 0, 60, 80), oval(180, 40, 20, 40)))
        division.expression shouldBe "0/0"
        division.sourceTokens.any { it.text == "%" } shouldBe false
    }
    "a fraction rule between a stem and a dot prevents factorial grouping" {
        val result = MathInkRecognizer.read(listOf(path(30, 0, 30, 80), path(10, 90, 70, 90), path(30, 105)))
        when (result) {
            is MathLayoutResult.Draft -> result.sourceTokens.none { it.text == "!" } shouldBe true
            is MathLayoutResult.Rejected -> Unit
        }
        val ordinary = draft(listOf(path(30, 0, 31, 30), path(10, 42, 70, 43), path(30, 56, 31, 86)))
        ordinary.expression shouldBe "((1)/(1))"
        value(ordinary.expression) shouldBe Num.ONE
    }
})
