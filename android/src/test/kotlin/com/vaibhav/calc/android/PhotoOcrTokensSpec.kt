package com.vaibhav.calc.android

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PhotoOcrTokensSpec : StringSpec({
    val whole = MathBox(0f, 0f, 60f, 20f)
    fun symbol(text: String, x: Int) = PhotoOcrSymbol(text, MathBox(x.toFloat(), 0f, x + 10f, 20f))

    "an SDK symbol list missing a minus never silently changes subtraction into a number" {
        val adapted = PhotoOcrTokenAdapter.collect(sequenceOf(PhotoOcrElement("1-2", whole, listOf(symbol("1", 0), symbol("2", 40)))))
        adapted.tokens.map { it.text } shouldBe listOf("1-2")
        adapted.usedElementFallback shouldBe true
        adapted.missingGeometry shouldBe false
    }

    "complete symbol coverage retains geometry and tolerates only inter-symbol whitespace" {
        val adapted = PhotoOcrTokenAdapter.collect(sequenceOf(PhotoOcrElement("1 + 2", whole,
            listOf(symbol("1", 0), symbol("+", 20), symbol("2", 40)))))
        adapted.tokens.map { it.text } shouldBe listOf("1", "+", "2")
        adapted.usedElementFallback shouldBe false
        val changed = PhotoOcrTokenAdapter.collect(sequenceOf(PhotoOcrElement("x²", whole, listOf(symbol("x", 0), symbol("2", 20)))))
        changed.tokens.single().text shouldBe "x²"
    }

    "missing or invalid symbol geometry preserves the complete element or reports missing geometry" {
        val incomplete = listOf(symbol("1", 0), PhotoOcrSymbol("-", null), symbol("2", 40))
        PhotoOcrTokenAdapter.collect(sequenceOf(PhotoOcrElement("1-2", whole, incomplete))).tokens.single().text shouldBe "1-2"
        val invalid = PhotoOcrTokenAdapter.collect(sequenceOf(PhotoOcrElement("1-2", null, incomplete)))
        invalid.missingGeometry shouldBe true
        invalid.tokens shouldBe emptyList()
        PhotoOcrTokenAdapter.collect(sequenceOf(PhotoOcrElement("1", MathBox(Float.NaN, 0f, 10f, 20f), emptyList())))
            .missingGeometry shouldBe true
    }

    "over-budget OCR produces no usable prefix and bounds even empty-element traversal" {
        val many = PhotoOcrTokenAdapter.collect((0..64).asSequence().map { PhotoOcrElement("1", whole, emptyList()) })
        many.budgetExceeded shouldBe true
        many.tokens shouldBe emptyList()
        PhotoOcrTokenAdapter.collect(sequenceOf(PhotoOcrElement("1".repeat(2049), whole, emptyList()))).budgetExceeded shouldBe true
        var visited = 0
        val blanks = PhotoOcrTokenAdapter.collect(generateSequence { visited++; PhotoOcrElement("", null, emptyList()) })
        blanks.budgetExceeded shouldBe true
        (visited <= 257) shouldBe true
    }
})
