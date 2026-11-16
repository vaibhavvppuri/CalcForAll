package com.vaibhav.calc.android

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class InkRecognizerSpec : StringSpec({
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = listOf(InkPoint(x1, y1), InkPoint(x2, y2))
    val plus = listOf(line(0f, 50f, 100f, 50f), line(50f, 0f, 50f, 100f))

    "basic strokes produce candidates while empty or nonfinite input produces none" {
        InkRecognizer.recognize(emptyList()) shouldBe emptyList()
        InkRecognizer.recognize(listOf(listOf(InkPoint(Float.NaN, 0f), InkPoint(1f, 1f)))) shouldBe emptyList()
        InkRecognizer.recognize(listOf(line(0f, 0f, Float.POSITIVE_INFINITY, 1f))) shouldBe emptyList()
        InkRecognizer.recognize(listOf(line(0f, 0f, 100f, 0f))).first().symbol shouldBe "−"
        InkRecognizer.recognize(listOf(line(0f, 0f, 0f, 100f))).first().symbol shouldBe "1"
    }

    "translation and uniform scaling do not change a symbol's identity" {
        val original = InkRecognizer.recognize(plus)
        val moved = InkRecognizer.recognize(plus.map { stroke -> stroke.map { InkPoint(it.x * 7f + 123f, it.y * 7f - 41f) } })
        original.first().symbol shouldBe "+"
        moved.first().symbol shouldBe "+"
        (abs(original.first().distance - moved.first().distance) < 0.0001f) shouldBe true
    }

    "drawing direction and stroke order preserve a plus candidate" {
        InkRecognizer.recognize(plus.reversed().map { it.reversed() }).first().symbol shouldBe "+"
        val dense = plus.map { stroke ->
            val a = stroke.first(); val b = stroke.last()
            (0..50).map { i -> InkPoint(a.x + (b.x - a.x) * i / 50f, a.y + (b.y - a.y) * i / 50f) }
        }
        InkRecognizer.recognize(dense).first().symbol shouldBe "+"
    }

    "ambiguous multiplication and variable x remain separate choices" {
        val candidates = InkRecognizer.recognize(listOf(line(0f, 0f, 100f, 100f), line(100f, 0f, 0f, 100f)))
        candidates.map { it.symbol }.containsAll(listOf("×", "x")) shouldBe true
        (candidates.size <= 5) shouldBe true
        candidates.map { it.symbol }.distinct().size shouldBe candidates.size
        candidates.all { it.distance.isFinite() && it.distance >= 0f } shouldBe true
        candidates.zipWithNext().all { (a, b) -> a.distance <= b.distance } shouldBe true
    }

    "a stationary mark is offered as a decimal point" {
        InkRecognizer.recognize(listOf(listOf(InkPoint(8f, 9f), InkPoint(8f, 9f)))).single().symbol shouldBe "."
    }

    "stroke and point budgets refuse oversize drawings" {
        InkRecognizer.recognize(List(33) { line(0f, 0f, 1f, 1f) }) shouldBe emptyList()
        InkRecognizer.recognize(listOf(List(12001) { InkPoint(it.toFloat(), 0f) })) shouldBe emptyList()
    }
})
