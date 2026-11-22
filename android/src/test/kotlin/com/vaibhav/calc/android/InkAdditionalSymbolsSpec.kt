package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Independent imperfect strokes and refusal cases, not a population handwriting benchmark. */
class InkAdditionalSymbolsSpec : StringSpec({
    fun path(vararg xy: Int) = xy.toList().chunked(2).map { InkPoint(it[0].toFloat(), it[1].toFloat()) }
    fun moved(ink: List<List<InkPoint>>, scale: Float, dx: Float, dy: Float) = ink.map { line ->
        line.map { InkPoint(it.x * scale + dx, it.y * scale + dy) }
    }
    fun value(expression: String) = ((Calculator().evaluate(expression) as CalcResult.Ok).value as RealValue).num
    val divide = listOf(path(31, 40, 61, 41, 92, 39), path(61, 17, 62, 18), path(60, 63))
    val hook = path(0, 52, 13, 69, 20, 75, 43, 2)
    val overbar = path(47, 3, 73, 2, 122, 4)
    val root = listOf(hook, overbar)
    val loop = (0..28).map { i -> InkPoint((84 + 14 * cos(2 * PI * i / 28)).toFloat(),
        (37 + 17 * sin(2 * PI * i / 28)).toFloat()) }
    val nine = listOf(loop, path(98, 35, 96, 60, 90, 75, 72, 76))

    "a dotted division sign is recognized in single-symbol mode across stroke orders and scales" {
        for (ink in listOf(divide, divide.reversed().map { it.reversed() }, moved(divide, 1.8f, 250f, -73f))) {
            withClue(ink) {
                InkSymbolGeometry.division(ink) shouldBe true
                InkRecognizer.recognize(ink).first().symbol shouldBe "÷"
            }
        }
    }

    "spaced-line and math-layout division preserve the two dots and calculate the same expression" {
        val ink = listOf(path(0, 0, 1, 80)) + divide + listOf(path(122, 0, 123, 80))
        val line = InkLineRecognizer.recognize(ink).shouldBeInstanceOf<InkLineResult.Draft>()
        line.text shouldBe "1÷1"
        value(line.text) shouldBe Num.ONE
        val math = MathInkRecognizer.read(ink).shouldBeInstanceOf<MathLayoutResult.Draft>()
        math.expression shouldBe "1/1"
        math.sourceTokens.size shouldBe 3
        math.sourceTokens[1].text shouldBe "÷"
        math.sourceTokens[1].uncertain shouldBe true
    }

    "missing extra displaced or same-side dots cannot manufacture division" {
        val invalid = listOf(divide.take(2), divide + listOf(path(64, 70)),
            listOf(divide[0], path(10, 18), divide[2]),
            listOf(divide[0], path(61, 17), path(61, 26)),
            listOf(divide[0], path(60, 0, 60, 25), path(60, 55, 60, 80)))
        invalid.forEach { InkSymbolGeometry.division(it) shouldBe false }
        val fraction = MathInkRecognizer.read(listOf(path(60, 0, 60, 25), divide[0], path(60, 55, 60, 80)))
            .shouldBeInstanceOf<MathLayoutResult.Draft>()
        fraction.expression shouldBe "((1)/(1))"
        fraction.sourceTokens.none { it.text == "÷" } shouldBe true
    }

    "a separately drawn root hook and overbar are recognized without depending on stroke direction" {
        for (ink in listOf(root, root.reversed().map { it.reversed() }, moved(root, 1.7f, 370f, -85f))) {
            InkSymbolGeometry.separateRoot(ink) shouldBe true
            InkRecognizer.recognize(ink).first().symbol shouldBe "√"
        }
        // Spaced-line mode still refuses root scope; it must not flatten it into digits.
        InkLineRecognizer.recognize(root).shouldBeInstanceOf<InkLineResult.Rejected>()
    }

    "the complete separate overbar bounds the radicand and excludes a following addition" {
        val ink = root + nine + listOf(path(142, 45, 188, 46), path(165, 24, 164, 70), path(213, 18, 214, 78))
        for (drawing in listOf(ink, ink.reversed().map { it.reversed() })) {
            val reading = MathInkRecognizer.readDetailed(drawing)
            val result = reading.layout.shouldBeInstanceOf<MathLayoutResult.Draft>()
            result.expression shouldBe "sqrt(9)+1"
            value(result.expression) shouldBe Num.of(4)
            reading.tokens shouldBe result.sourceTokens
            reading.tokens.size shouldBe 4
            reading.tokens.first().text shouldBe "√"
            reading.tokens.first().bounds.right shouldBe 122f
            reading.tokens.first().uncertain shouldBe true
        }
    }

    "an empty separate root is refused but its complete token remains available for review" {
        val reading = MathInkRecognizer.readDetailed(root)
        reading.layout.shouldBeInstanceOf<MathLayoutResult.Rejected>()
        reading.tokens.single().text shouldBe "√"
        MathInkRecognizer.read(root).shouldBeInstanceOf<MathLayoutResult.Rejected>()
    }

    "unrelated overlines vertical strokes and a V do not satisfy the separate-root topology" {
        InkSymbolGeometry.separateRoot(path(43, 75, 43, 2), overbar) shouldBe false
        InkSymbolGeometry.separateRoot(path(0, 0, 20, 75, 43, 2), overbar) shouldBe false
        InkSymbolGeometry.separateRoot(hook, path(80, 25, 155, 25)) shouldBe false
        InkSymbolGeometry.separateRoot(hook, path(47, 3, 85, 45, 122, 4)) shouldBe false
        InkSymbolGeometry.separateRoot(root + listOf(path(140, 3, 170, 3))) shouldBe false
    }

    "competing root overbars refuse the whole grouping instead of selecting or dropping one" {
        val reading = MathInkRecognizer.readDetailed(root + listOf(path(48, 6, 116, 6)))
        reading.layout.shouldBeInstanceOf<MathLayoutResult.Rejected>()
        reading.tokens shouldBe emptyList()
    }

    "a missing or weak glyph is visible as unknown after every complete group has been processed" {
        val ink = listOf(path(0, 0, 0, 80), path(80, 0, 100, 40, 80, 80), path(140, 0, 140, 80))
        for (weak in listOf(emptyList(), listOf(InkCandidate("7", .55f)))) {
            val visited = mutableSetOf<Float>()
            val reading = MathInkRecognizer.readDetailed(ink) { glyph ->
                val left = glyph.flatten().minOf { it.x }
                visited += left
                if (left == 80f) weak else listOf(InkCandidate("1", .01f))
            }
            reading.layout.shouldBeInstanceOf<MathLayoutResult.Rejected>()
            reading.tokens.map { it.text } shouldBe listOf("1", "?", "1")
            reading.tokens.map { it.sourceIndex } shouldBe listOf(0, 1, 2)
            reading.tokens[1].uncertain shouldBe true
            reading.tokens[1].alternatives shouldBe weak.map { it.symbol }
            visited shouldBe setOf(0f, 80f, 140f)
        }
    }

    "whole-drawing validation failures never expose a recognized prefix" {
        val prefix = listOf(path(0, 0, 0, 80))
        val bad = listOf(prefix + listOf(listOf(InkPoint(Float.NaN, 0f))), List(65) { prefix.single() },
            listOf(List(12001) { InkPoint(it.toFloat(), 0f) }))
        for (ink in bad) {
            val reading = MathInkRecognizer.readDetailed(ink)
            reading.layout.shouldBeInstanceOf<MathLayoutResult.Rejected>()
            reading.tokens shouldBe emptyList()
        }
    }

    "an oversized connected glyph yields no editable prefix even after an earlier valid glyph" {
        val ink = listOf(path(0, 0, 0, 80)) + List(6) { i -> path(80 + i, 0, 100 - i, 80) }
        val reading = MathInkRecognizer.readDetailed(ink)
        reading.layout.shouldBeInstanceOf<MathLayoutResult.Rejected>()
        reading.tokens shouldBe emptyList()
    }

    "complete tokens survive a refused multiline layout without becoming an automatic expression" {
        val ink = listOf(path(0, 0, 0, 40), path(0, 100, 0, 140))
        val reading = MathInkRecognizer.readDetailed(ink)
        reading.layout.shouldBeInstanceOf<MathLayoutResult.Rejected>()
        reading.tokens.map { it.text } shouldBe listOf("1", "1")
        reading.tokens.map { it.sourceIndex } shouldBe listOf(0, 1)
    }
})
