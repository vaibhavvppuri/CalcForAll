package com.vaibhav.calc.android

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PhotoDraftSpec : StringSpec({
    "an incomplete page cannot reintroduce a calculable prefix through raw text" {
        val layout = MathLayout.read(listOf(MathToken("12+34", MathBox(0f, 0f, 100f, 20f))))
        val draft = PhotoMathDraft("12+34", layout, truncated = true)
        draft.expressionForReview shouldBe ""
        draft.completeRawText shouldBe ""
        PhotoTextReview.lines(draft.completeRawText).isEmpty() shouldBe true
    }

    "complete multiline equations require explicit raw-text review after layout refusal" {
        val raw = "x+y=5\nx-y=1"
        val draft = PhotoMathDraft(raw, MathLayoutResult.Rejected("Review the separate rows."))
        draft.completeRawText shouldBe raw
        draft.expressionForReview shouldBe ""
    }

    "an oversized complete page can be reviewed but is not calculated automatically" {
        val raw = "1+".repeat(RecognitionCalculator.MAX_CHARS)
        val draft = PhotoMathDraft(raw, MathLayoutResult.Rejected("Crop one expression."))
        draft.expressionForReview shouldBe ""
        draft.completeRawText shouldBe raw
    }

    "a rejected incomplete fraction never calculates its flattened numerator" {
        val layout = MathLayout.read(listOf(MathToken("12", MathBox(30f, 10f, 60f, 50f)),
            MathToken("-", MathBox(20f, 65f, 80f, 68f))))
        (layout is MathLayoutResult.Rejected) shouldBe true
        val draft = PhotoMathDraft("12", layout)
        draft.expressionForReview shouldBe ""
        draft.completeRawText shouldBe "12" // Available only through explicit manual review.
    }

    "incomplete SDK coverage cannot reintroduce even an otherwise valid draft" {
        val layout = MathLayout.read(listOf(MathToken("12+34", MathBox(0f, 0f, 100f, 20f))))
        val draft = PhotoMathDraft("12+34", layout, incomplete = true)
        draft.expressionForReview shouldBe ""
        draft.completeRawText shouldBe ""
    }
})
