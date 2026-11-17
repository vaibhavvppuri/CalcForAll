package com.vaibhav.calc.android

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PhotoTextReviewSpec : StringSpec({
    "CRLF and blank lines preserve ordered separate questions" {
        PhotoTextReview.lines(" 12+34\r\n\r\n x²−5x+6=0 \r 5 km+300 m ") shouldBe
            listOf("12+34", "x²−5x+6=0", "5 km+300 m")
    }
    "does not silently correct ambiguous OCR or rewrite fractions and equations" {
        PhotoTextReview.lines("O+1\n1/2\nx+y=5;x-y=1\n2³\n½") shouldBe
            listOf("O+1", "1/2", "x+y=5;x-y=1", "2³", "½")
    }
    "line selection is bounded without merging distant expressions" {
        val lines = PhotoTextReview.lines((1..100).joinToString("\n") { "$it+1" })
        lines.size shouldBe 24
        lines.first() shouldBe "1+1"
        lines.last() shouldBe "24+1"
    }
    "empty input has no fabricated candidates" {
        PhotoTextReview.lines(" \r\n\t ") shouldBe emptyList()
    }
    "input character budget bounds the review candidates" {
        PhotoTextReview.lines("1".repeat(CalcSession.MAX_ENTRY_CHARS + 20)).single().length shouldBe CalcSession.MAX_ENTRY_CHARS
    }
    "duplicate source lines remain individually selectable in their original order" {
        PhotoTextReview.lines("1+1\n2+2\n1+1") shouldBe listOf("1+1", "2+2", "1+1")
    }
})
