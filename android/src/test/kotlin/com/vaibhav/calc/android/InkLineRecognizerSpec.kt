package com.vaibhav.calc.android

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InkLineRecognizerSpec : StringSpec({
    fun stroke(x1: Float, y1: Float, x2: Float, y2: Float) = listOf(InkPoint(x1, y1), InkPoint(x2, y2))
    val onePlusOne = listOf(stroke(0f, 0f, 0f, 100f), stroke(30f, 50f, 130f, 50f),
        stroke(80f, 0f, 80f, 100f), stroke(160f, 0f, 160f, 100f))
    "separated line retains a multi stroke plus and horizontal ordering" {
        (InkLineRecognizer.recognize(onePlusOne) as InkLineResult.Draft).text shouldBe "1+1"
        (InkLineRecognizer.recognize(onePlusOne.reversed()) as InkLineResult.Draft).text shouldBe "1+1"
    }
    "equals strokes stay grouped between digits" {
        val ink = listOf(stroke(0f, 0f, 0f, 100f), stroke(30f, 30f, 130f, 30f),
            stroke(30f, 70f, 130f, 70f), stroke(160f, 0f, 160f, 100f))
        (InkLineRecognizer.recognize(ink) as InkLineResult.Draft).text shouldBe "1=1"
    }
    "a single tap decimal is retained at the baseline" {
        val ink = listOf(stroke(0f, 0f, 0f, 100f), listOf(InkPoint(20f, 100f)), stroke(40f, 0f, 40f, 100f))
        (InkLineRecognizer.recognize(ink) as InkLineResult.Draft).text shouldBe "1.1"
    }
    "stacked fraction components are refused rather than emitted as plus" {
        val fraction = listOf(stroke(50f, 0f, 50f, 30f), stroke(0f, 50f, 100f, 50f), stroke(50f, 70f, 50f, 100f))
        (InkLineRecognizer.recognize(fraction) is InkLineResult.Rejected) shouldBe true
    }
    "raised symbols and spanning bars are refused atomically" {
        (InkLineRecognizer.recognize(listOf(stroke(0f, 0f, 0f, 100f), stroke(30f, -80f, 30f, -30f))) is InkLineResult.Rejected) shouldBe true
        (InkLineRecognizer.recognize(listOf(stroke(0f, 50f, 500f, 50f), stroke(50f, 0f, 50f, 100f))) is InkLineResult.Rejected) shouldBe true
    }
    "line count point and symbol limits refuse oversized input" {
        (InkLineRecognizer.recognize(List(65) { stroke(0f, 0f, 0f, 100f) }) is InkLineResult.Rejected) shouldBe true
        (InkLineRecognizer.recognize(listOf(List(12001) { InkPoint(0f, 0f) })) is InkLineResult.Rejected) shouldBe true
        (InkLineRecognizer.recognize(List(33) { stroke(it * 5f, 0f, it * 5f, 100f) }) is InkLineResult.Rejected) shouldBe true
    }
    "empty and invalid coordinates are refused" {
        (InkLineRecognizer.recognize(emptyList()) is InkLineResult.Rejected) shouldBe true
        (InkLineRecognizer.recognize(listOf(stroke(Float.MAX_VALUE, 0f, 0f, 1f))) is InkLineResult.Rejected) shouldBe true
    }
    "a recognized radical is refused instead of falling back to another symbol" {
        val radical = listOf(listOf(InkPoint(0f, 55f), InkPoint(20f, 80f), InkPoint(40f, 0f), InkPoint(100f, 0f)))
        InkRecognizer.recognize(radical).first().symbol shouldBe "√"
        (InkLineRecognizer.recognize(radical) is InkLineResult.Rejected) shouldBe true
    }
})
