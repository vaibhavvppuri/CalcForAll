package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Hand-specified imperfect paths, not copies of training templates or an accuracy benchmark. */
class InkDigitStylesSpec : StringSpec({
    fun path(vararg xy: Int) = xy.toList().chunked(2).map { InkPoint(it[0].toFloat(), it[1].toFloat()) }
    fun shift(ink: List<List<InkPoint>>, dx: Float) = ink.map { s -> s.map { p -> p.copy(x = p.x + dx) } }
    fun draft(ink: List<List<InkPoint>>) = MathInkRecognizer.read(ink).shouldBeInstanceOf<MathLayoutResult.Draft>()
    fun value(expression: String) = ((Calculator().evaluate(expression) as CalcResult.Ok).value as RealValue).num
    val hookedOne = listOf(path(12, 25, 32, 8, 35, 101))
    val five = listOf(path(17, 12, 91, 9), path(15, 21, 17, 51, 55, 48, 78, 60, 88, 80, 73, 101, 39, 108, 14, 96))
    val seven = listOf(path(10, 11, 90, 13), path(86, 23, 66, 58, 36, 106))
    val crossedSeven = seven + listOf(path(43, 58, 83, 60))
    val eight = listOf(path(45, 8, 24, 11, 14, 28, 20, 42, 43, 52, 68, 66, 76, 84, 63, 100,
        39, 106, 16, 96, 9, 80, 18, 65, 43, 52, 62, 39, 67, 25, 58, 12, 45, 8))

    "hooked one and a continuous eight have useful candidates without foot or two separate loops" {
        InkRecognizer.recognize(hookedOne).first().symbol shouldBe "1"
        // This off-template continuous eight used to rank 3 ahead of 8.
        val alternatives = InkRecognizer.recognize(eight)
        alternatives.first().symbol shouldBe "8"
        alternatives.any { it.symbol == "3" } shouldBe true
        draft(eight).ambiguous shouldBe true
        value(draft(eight).expression) shouldBe Num.of(8)
    }

    "digit alternatives remain ordered finite and unique under direction order and scale changes" {
        for ((label, ink) in listOf("1" to hookedOne, "5" to five, "7" to seven, "7" to crossedSeven, "8" to eight)) {
            val transformed = ink.reversed().map { s -> s.reversed().map { p -> InkPoint(321f + p.x * 1.7f, -47f + p.y * 1.7f) } }
            withClue(label) {
                val candidates = InkRecognizer.recognize(transformed)
                candidates.first().symbol shouldBe label
                (candidates.size in 1..5) shouldBe true
                candidates.map { it.symbol }.distinct().size shouldBe candidates.size
                candidates.all { it.distance.isFinite() && it.distance >= 0f } shouldBe true
                candidates.zipWithNext().all { (a, b) -> a.distance <= b.distance } shouldBe true
            }
        }
    }

    "detached five and seven keep their caps and compute twelve" {
        val plus = listOf(path(115, 57, 170, 59), path(143, 30, 141, 90))
        val ink = five + plus + shift(seven, 190f)
        val result = draft(ink)
        result.expression shouldBe "5+7"
        value(result.expression) shouldBe Num.of(12)
        result.sourceTokens.size shouldBe 3
        result.sourceTokens.filter { it.text in setOf("5", "7") }.all { it.uncertain } shouldBe true
        result.sourceTokens.map { it.sourceIndex }.distinct().size shouldBe 3
        draft(ink.reversed().map { it.reversed() }).expression shouldBe "5+7"
    }

    "a detached cap and crossing stroke remain one seven" {
        val result = draft(crossedSeven)
        result.expression shouldBe "7"
        result.sourceTokens.size shouldBe 1
        result.sourceTokens.single().uncertain shouldBe true
        draft(crossedSeven.reversed()).expression shouldBe "7"
    }

    "spaced line reads detached digits and hooked one while retaining ordinary plus" {
        val ink = five + listOf(path(115, 57, 170, 59), path(143, 30, 141, 90)) + shift(seven, 190f)
        InkLineRecognizer.recognize(ink).shouldBeInstanceOf<InkLineResult.Draft>().text shouldBe "5+7"
        InkLineRecognizer.recognize(crossedSeven).shouldBeInstanceOf<InkLineResult.Draft>().text shouldBe "7"
        InkLineRecognizer.recognize(hookedOne).shouldBeInstanceOf<InkLineResult.Draft>().text shouldBe "1"
    }

    "a bar over a vertical one is an overline not a detached seven or factorial" {
        val cap = path(10, 10, 90, 10)
        val body = listOf(path(50, 20, 50, 110))
        InkRecognizer.detachedTopDigit(cap, body) shouldBe null
        MathInkRecognizer.read(listOf(cap) + body).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        InkLineRecognizer.recognize(listOf(cap) + body).shouldBeInstanceOf<InkLineResult.Rejected>()
    }

    "an extra overline above a complete seven is never silently discarded" {
        val ink = listOf(path(0, 0, 100, 0), path(10, 15, 90, 17, 35, 110))
        InkRecognizer.detachedTopDigit(ink[0], listOf(ink[1])) shouldBe null
        MathInkRecognizer.read(ink).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        InkLineRecognizer.recognize(ink).shouldBeInstanceOf<InkLineResult.Rejected>()
    }

    "distant caps and vertical side strokes do not satisfy the detached digit rule" {
        InkRecognizer.detachedTopDigit(path(17, -30, 91, -30), listOf(five[1])) shouldBe null
        InkRecognizer.detachedTopDigit(path(10, 11, 90, 13), listOf(path(86, 23, 85, 106))) shouldBe null
        InkRecognizer.detachedTopDigit(path(-90, 10, -10, 10), listOf(seven[1])) shouldBe null
    }

    "a numerator above a candidate digit cap preserves the fraction rather than vanishing" {
        val ink = listOf(path(45, -40, 45, -8), path(10, 11, 90, 13), seven[1])
        when (val result = MathInkRecognizer.read(ink)) {
            is MathLayoutResult.Draft -> {
                result.expression.startsWith("((1)/(") shouldBe true
                result.sourceTokens.size shouldBe 3
                result.sourceTokens.none { it.text == "7" } shouldBe true
            }
            is MathLayoutResult.Rejected -> Unit
        }
    }

    "two bodies under one cap are not merged into a single guessed digit" {
        val ink = seven + listOf(path(42, 25, 25, 60, 12, 104))
        when (val result = MathInkRecognizer.read(ink)) {
            is MathLayoutResult.Draft -> (result.sourceTokens.size > 1) shouldBe true
            is MathLayoutResult.Rejected -> Unit
        }
    }

    "a normal subtraction sign beside seven stays a subtraction" {
        val ink = hookedOne + listOf(path(61, 56, 106, 58)) + shift(seven, 135f)
        val result = draft(ink)
        result.expression shouldBe "1-7"
        value(result.expression) shouldBe Num.of(-6)
    }

    "retracing a shared stroke object does not erase all body entries or throw" {
        val one = path(20, 0, 20, 90)
        InkLineRecognizer.recognize(listOf(one, one)).shouldBeInstanceOf<InkLineResult.Draft>().text shouldBe "1"
        draft(listOf(one, one)).expression shouldBe "1"
    }
})
