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

    "complete multiline equations remain available for the system solver" {
        val raw = "x+y=5\nx-y=1"
        val draft = PhotoMathDraft(raw, MathLayoutResult.Rejected("Review the separate rows."))
        draft.completeRawText shouldBe raw
        draft.expressionForReview shouldBe raw
    }

    "an oversized complete page can be reviewed but is not calculated automatically" {
        val raw = "1+".repeat(RecognitionCalculator.MAX_CHARS)
        val draft = PhotoMathDraft(raw, MathLayoutResult.Rejected("Crop one expression."))
        draft.expressionForReview shouldBe ""
        draft.completeRawText shouldBe raw
    }
})
