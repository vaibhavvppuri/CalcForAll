package com.vaibhav.calc.android

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InkRecognizerBoundsSpec : StringSpec({
    "point and stroke limits reject before reading oversized point collections" {
        val unreadableStroke = object : AbstractList<InkPoint>() {
            override val size = 12001
            override fun get(index: Int): InkPoint = error("Oversized points must not be visited")
        }
        InkRecognizer.recognize(listOf(unreadableStroke)) shouldBe emptyList()
        val unreadableStrokes = object : AbstractList<List<InkPoint>>() {
            override val size = 33
            override fun get(index: Int): List<InkPoint> = error("Oversized strokes must not be visited")
        }
        InkRecognizer.recognize(unreadableStrokes) shouldBe emptyList()
        val unreadablePartial = object : AbstractList<InkPoint>() {
            override val size = 6001
            override fun get(index: Int): InkPoint = error("Combined oversized points must not be visited")
        }
        InkRecognizer.recognize(listOf(unreadablePartial, unreadablePartial)) shouldBe emptyList()
    }

    "finite extreme coordinates are refused before geometric overflow" {
        InkRecognizer.recognize(listOf(listOf(InkPoint(-Float.MAX_VALUE, 0f), InkPoint(Float.MAX_VALUE, 0f)))) shouldBe emptyList()
        InkRecognizer.recognize(listOf(listOf(InkPoint(0f, -Float.MAX_VALUE), InkPoint(0f, Float.MAX_VALUE)))) shouldBe emptyList()
        InkRecognizer.recognize(listOf(listOf(InkPoint(1_000_001f, 0f), InkPoint(1_000_001f, 100f)))) shouldBe emptyList()
    }

    "large permitted screen translations retain finite ordered candidates" {
        val horizontal = listOf(listOf(InkPoint(999_900f, -1_000_000f), InkPoint(1_000_000f, -1_000_000f)))
        val candidates = InkRecognizer.recognize(horizontal)
        candidates.first().symbol shouldBe "−"
        candidates.all { it.distance.isFinite() && it.distance >= 0f } shouldBe true
        candidates.zipWithNext().all { (a, b) -> a.distance <= b.distance } shouldBe true
    }
})
