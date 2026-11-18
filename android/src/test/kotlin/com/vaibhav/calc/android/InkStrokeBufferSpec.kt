package com.vaibhav.calc.android

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InkStrokeBufferSpec : StringSpec({
    "down directly followed by up retains a recognizable vertical stroke" {
        val capture = InkStrokeBuffer(20, 300f, 300f)
        capture.append(InkPoint(100f, 20f))
        capture.append(InkPoint(100f, 220f)) // Pen-up, with no intervening move events.
        capture.completed() shouldBe listOf(InkPoint(100f, 20f), InkPoint(100f, 220f))
        InkRecognizer.recognize(listOf(capture.completed()!!)).first().symbol shouldBe "1"
    }
    "stationary down and up remain one tap without exhausting a one-point budget" {
        val capture = InkStrokeBuffer(1, 100f, 100f)
        capture.append(InkPoint(20f, 30f))
        capture.append(InkPoint(20f, 30f))
        capture.completed() shouldBe listOf(InkPoint(20f, 30f))
        capture.failure shouldBe null
    }
    "coalesced corners are retained in order before the final up position" {
        val capture = InkStrokeBuffer(10, 100f, 100f)
        capture.append(InkPoint(10f, 10f))
        capture.append(InkPoint(90f, 90f), sequenceOf(InkPoint(10f, 10f), InkPoint(90f, 10f), InkPoint(10f, 90f)))
        capture.completed() shouldBe listOf(InkPoint(10f, 10f), InkPoint(90f, 10f), InkPoint(10f, 90f), InkPoint(90f, 90f))
    }
    "clamping precedes deduplication and a duplicate pen-up does not overflow" {
        val capture = InkStrokeBuffer(2, 100f, 100f)
        capture.append(InkPoint(-10f, -10f))
        capture.append(InkPoint(0f, 0f))
        capture.append(InkPoint(150f, 200f))
        capture.append(InkPoint(120f, 130f))
        capture.completed() shouldBe listOf(InkPoint(0f, 0f), InkPoint(100f, 100f))
    }
    "an overflowing history rejects the whole stroke instead of returning a prefix" {
        val capture = InkStrokeBuffer(2, 100f, 100f)
        capture.append(InkPoint(10f, 10f))
        capture.append(InkPoint(90f, 90f), sequenceOf(InkPoint(20f, 20f), InkPoint(30f, 30f)))
        capture.failure shouldBe InkStrokeBuffer.Failure.POINT_LIMIT
        capture.completed() shouldBe null
        capture.append(InkPoint(0f, 0f))
        capture.completed() shouldBe null
    }
    "nonfinite final coordinates reject the gesture" {
        val capture = InkStrokeBuffer(10, 100f, 100f)
        capture.append(InkPoint(10f, 10f))
        capture.append(InkPoint(Float.NaN, 20f))
        capture.failure shouldBe InkStrokeBuffer.Failure.INVALID_COORDINATE
        capture.completed() shouldBe null
    }
    "even endless duplicate historical samples have a bounded visit budget" {
        val capture = InkStrokeBuffer(10, 100f, 100f)
        var visited = 0
        capture.append(InkPoint(20f, 20f), generateSequence { visited++; InkPoint(10f, 10f) })
        visited shouldBe 12001
        capture.failure shouldBe InkStrokeBuffer.Failure.POINT_LIMIT
        capture.completed() shouldBe null
    }
})
