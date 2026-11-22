package com.vaibhav.calc.android

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class InkReviewSpec : StringSpec({
    fun token(text: String, x: Float, index: Int) = MathToken(text, MathBox(x, 10f, x + 20f, 70f),
        alternatives = listOf("1", "7"), matchDistance = .3f, uncertain = true, sourceIndex = index)

    "two unknown glyphs can be corrected without losing the first explicit choice" {
        val input = listOf(token("?", 0f, 0), token("+", 40f, 1), token("?", 80f, 2))
        val first = InkReview.replace(input, 0, "4")!!
        first.layout.shouldBeInstanceOf<MathLayoutResult.Rejected>()
        first.tokens.map { it.text } shouldBe listOf("4", "+", "?")
        val second = InkReview.replace(first.tokens, 2, "2")!!
        second.layout.shouldBeInstanceOf<MathLayoutResult.Draft>().expression shouldBe "4+2"
        second.tokens.map { it.sourceIndex } shouldBe listOf(0, 1, 2)
        second.tokens.map { it.bounds } shouldBe input.map { it.bounds }
        input.map { it.text } shouldBe listOf("?", "+", "?")
    }

    "explicit replacement keeps geometry for a fraction rather than flattening its marks" {
        val input = listOf(MathToken("?", MathBox(20f, 0f, 40f, 30f)),
            MathToken("−", MathBox(5f, 42f, 65f, 43f)), MathToken("2", MathBox(20f, 56f, 40f, 86f)))
        val edit = InkReview.replace(input, 0, "1")!!
        edit.layout.shouldBeInstanceOf<MathLayoutResult.Draft>().expression shouldBe "((1)/(2))"
        edit.tokens.size shouldBe 3
    }

    "an unsupported arrangement remains rejected after a glyph correction" {
        val input = listOf(token("?", 0f, 0), token("2", 0f, 1))
        val edit = InkReview.replace(input, 0, "3")!!
        edit.layout.shouldBeInstanceOf<MathLayoutResult.Rejected>()
        edit.tokens.map { it.text } shouldBe listOf("3", "2")
    }

    "a correction that breaks a previously valid layout is kept through further corrections" {
        val original = listOf(token("1", 0f, 0), token("+", 40f, 1), token("1", 80f, 2))
        MathLayout.read(original).shouldBeInstanceOf<MathLayoutResult.Draft>()
        val rootWithoutArgument = InkReview.replace(original, 2, "√")!!
        rootWithoutArgument.layout.shouldBeInstanceOf<MathLayoutResult.Rejected>()
        rootWithoutArgument.tokens.map { it.text } shouldBe listOf("1", "+", "√")
        val anotherChoice = InkReview.replace(rootWithoutArgument.tokens, 0, "4")!!
        anotherChoice.layout.shouldBeInstanceOf<MathLayoutResult.Rejected>()
        anotherChoice.tokens.map { it.text } shouldBe listOf("4", "+", "√")
        val recovered = InkReview.replace(anotherChoice.tokens, 2, "2")!!
        recovered.layout.shouldBeInstanceOf<MathLayoutResult.Draft>().expression shouldBe "4+2"
        original.map { it.text } shouldBe listOf("1", "+", "1")
    }

    "manual replacement drops the machine mismatch score and does not mutate alternatives" {
        val alternatives = mutableListOf("1", "7")
        val input = mutableListOf(token("?", 0f, 0).copy(alternatives = alternatives))
        val edit = InkReview.replace(input, 0, "8")!!
        alternatives.clear(); input.clear()
        edit.tokens.single().text shouldBe "8"
        edit.tokens.single().alternatives shouldBe listOf("1", "7")
        edit.tokens.single().matchDistance shouldBe null
        edit.tokens.single().uncertain shouldBe false
    }

    "the full supported variable palette includes z without guessing a replacement" {
        val input = listOf(token("?", 0f, 0), token("+", 40f, 1), token("1", 80f, 2))
        val edit = InkReview.replace(input, 0, "z")!!
        edit.layout.shouldBeInstanceOf<MathLayoutResult.Draft>().expression shouldBe "z+1"
        input.first().text shouldBe "?"
    }

    "unsupported substitutions invalid positions and oversized data are refused unchanged" {
        val input = listOf(token("?", 0f, 0))
        for (symbol in listOf("", "?", "12", "sqrt", "1+2", "\n", "sin(30)"))
            InkReview.replace(input, 0, symbol) shouldBe null
        InkReview.replace(input, -1, "1") shouldBe null
        InkReview.replace(input, 1, "1") shouldBe null
        InkReview.replace(List(65) { token("?", it * 30f, it) }, 0, "1") shouldBe null
        InkReview.replace(listOf(input.single().copy(text = "1".repeat(2049))), 0, "1") shouldBe null
        InkReview.replace(listOf(input.single().copy(bounds = MathBox(Float.NaN, 0f, 10f, 20f))), 0, "1") shouldBe null
        InkReview.replace(listOf(input.single().copy(alternatives = List(9) { "1" })), 0, "1") shouldBe null
        input.single().text shouldBe "?"
    }
})
