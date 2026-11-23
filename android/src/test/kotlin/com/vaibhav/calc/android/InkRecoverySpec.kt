package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class InkRecoverySpec : StringSpec({
    fun path(vararg xy: Int) = xy.toList().chunked(2).map { InkPoint(it[0].toFloat(), it[1].toFloat()) }
    val addition = listOf(path(0, 0, 0, 80), path(35, 40, 75, 40), path(55, 20, 55, 60), path(110, 0, 110, 80))
    val division = listOf(path(0, 0, 0, 80), path(31, 40, 61, 41, 92, 39), path(61, 17, 62, 18), path(60, 63), path(122, 0, 123, 80))
    fun value(expression: String) = Calculator().evaluate(expression).shouldBeInstanceOf<CalcResult.Ok<RealValue>>().value.num
    fun populate(ink: List<List<InkPoint>>): InkWorkspace {
        val reading = MathInkRecognizer.readDetailed(ink)
        val draft = reading.layout.shouldBeInstanceOf<MathLayoutResult.Draft>()
        return InkWorkspace().apply {
            commitInk(ink); reviewTokens = reading.tokens; mathDraft = draft; expression = draft.expression
            recognizedStrokes = strokes; recognizedMode = mode; recognizedRequest = recognitionRequest
        }
    }

    "a confident independently drawn one plus one still calculates automatically" {
        val workspace = populate(addition)
        workspace.expression shouldBe "1+1"
        workspace.needsExpressionReview shouldBe false
        value(workspace.expression) shouldBe Num.of(2)
    }

    "real dotted-division grouping requires review before its machine reading can calculate" {
        val workspace = populate(division)
        workspace.expression shouldBe "1/1"
        workspace.reviewTokens[1].uncertain shouldBe true
        workspace.needsExpressionReview shouldBe true
        workspace.acknowledgeExpression() shouldBe true
        workspace.needsExpressionReview shouldBe false
        value(workspace.expression) shouldBe Num.ONE
        workspace.invalidateRecognition()
        workspace.expressionWasReviewed shouldBe false
        workspace.expression shouldBe ""
    }

    "typing a correction is authoritative and an oversized paste is refused atomically" {
        val workspace = populate(division)
        workspace.editExpression("12/3") shouldBe true
        workspace.needsExpressionReview shouldBe false
        value(workspace.expression) shouldBe Num.of(4)
        workspace.editExpression("9".repeat(RecognitionCalculator.MAX_CHARS + 1)) shouldBe false
        workspace.expression shouldBe "12/3"
        workspace.expressionWasReviewed shouldBe true
    }

    "switching a real uncertain reading to one-symbol mode cannot approve its old expression" {
        val workspace = populate(division)
        workspace.expression shouldBe "1/1"
        workspace.needsExpressionReview shouldBe true
        val ink = workspace.strokes
        workspace.changeMode("One symbol") shouldBe true
        workspace.strokes shouldBe ink
        workspace.expression shouldBe ""
        workspace.expressionWasReviewed shouldBe false
        workspace.reviewTokens shouldBe emptyList()
        workspace.recognizedStrokes shouldBe null
    }

    "one-symbol text survives new strokes and a repeated mode tap but not a different mode" {
        val workspace = InkWorkspace()
        workspace.changeMode("One symbol") shouldBe true
        workspace.editExpression("12+") shouldBe true
        val revision = workspace.inkRevision
        workspace.changeMode("One symbol") shouldBe false
        workspace.inkRevision shouldBe revision
        workspace.expression shouldBe "12+"
        workspace.commitInk(listOf(path(0, 0, 0, 80))) shouldBe true
        workspace.expression shouldBe "12+"
        workspace.changeMode("Spaced line") shouldBe true
        workspace.expression shouldBe ""
        workspace.strokes.size shouldBe 1
    }

    "a function shortcut cannot silently approve uncertain underlying symbols" {
        val workspace = populate(division)
        workspace.transformExpression("sqrt(${workspace.expression})") shouldBe true
        workspace.needsExpressionReview shouldBe true
        workspace.acknowledgeExpression() shouldBe true
        workspace.transformExpression("abs(${workspace.expression})") shouldBe true
        workspace.expressionWasReviewed shouldBe true
        workspace.needsExpressionReview shouldBe false
        value(workspace.expression) shouldBe Num.ONE
    }

    "review acknowledgment is detached from mutable candidate and symbol lists" {
        val alternatives = mutableListOf("1", "7")
        val input = mutableListOf(MathToken("1", MathBox(0f, 0f, 2f, 80f), alternatives, .1f, true))
        val line = mutableListOf("1")
        val review = InkExpressionReview.capture(4L, "Math layout", "1", input, line)!!
        alternatives.clear(); input.clear(); line[0] = "9"
        review.tokens.single().alternatives shouldBe listOf("1", "7")
        review.lineSymbols shouldBe listOf("1")
        review.matches(4L, "Math layout", "1", review.tokens, listOf("1")) shouldBe true
        review.matches(5L, "Math layout", "1", review.tokens, listOf("1")) shouldBe false
        review.matches(4L, "Spaced line", "1", review.tokens, listOf("1")) shouldBe false
        review.matches(4L, "Math layout", "9", review.tokens, listOf("1")) shouldBe false
    }

    "review snapshots reject oversized input before copying it" {
        val token = MathToken("1", MathBox(0f, 0f, 2f, 80f))
        InkExpressionReview.capture(0L, "Math layout", "", emptyList(), emptyList()) shouldBe null
        InkExpressionReview.capture(0L, "Math layout", "1", List(65) { token }, emptyList()) shouldBe null
        InkExpressionReview.capture(0L, "Math layout", "1", listOf(token.copy(alternatives = listOf("x".repeat(65)))), emptyList()) shouldBe null
        InkExpressionReview.capture(0L, "Spaced line", "1", emptyList(), List(33) { "1" }) shouldBe null
    }

    "unknown groups receive targeted guidance before merely uncertain groups" {
        val tokens = listOf(MathToken("7", MathBox(0f, 0f, 20f, 80f), uncertain = true),
            MathToken("?", MathBox(50f, 0f, 70f, 80f), uncertain = true))
        val advice = InkRecovery.guidance(true, "Math layout", tokens, null, null)!!
        advice.firstSymbol shouldBe 1
        advice.message.contains("symbol 2") shouldBe true
        tokens.map { it.text } shouldBe listOf("7", "?")
        InkRecovery.guidance(false, "Math layout", tokens, null, null) shouldBe null
    }

    "same-ink retry retains explicit correction instead of replacing it with the machine winner" {
        val reading = MathInkRecognizer.readDetailed(addition)
        val edit = InkReview.replace(reading.tokens, 0, "4")!!
        val retried = InkRecovery.retainExplicitChoices(edit.tokens, MathInkRecognizer.readDetailed(addition))
        retried.layout.shouldBeInstanceOf<MathLayoutResult.Draft>().expression shouldBe "4+1"
        retried.tokens.first().text shouldBe "4"
        value((retried.layout as MathLayoutResult.Draft).expression) shouldBe Num.of(5)
    }

    "changed geometry or incomplete recognition cannot receive old glyph choices" {
        val reading = MathInkRecognizer.readDetailed(addition)
        val edit = InkReview.replace(reading.tokens, 0, "4")!!
        val moved = reading.copy(tokens = reading.tokens.map { it.copy(bounds = it.bounds.copy(left = it.bounds.left + 1f)) })
        InkRecovery.retainExplicitChoices(edit.tokens, moved) shouldBe moved
        val refused = MathInkReading(MathLayoutResult.Rejected("Too much work"), emptyList())
        InkRecovery.retainExplicitChoices(edit.tokens, refused) shouldBe refused
    }

    "a line glyph choice resolves only that glyph and cannot approve an unrelated ambiguity" {
        val workspace = InkWorkspace().apply {
            mode = "Spaced line"
            lineDraft = InkLineResult.Draft(listOf(
                InkGlyph(listOf(InkCandidate("1", 0f))),
                InkGlyph(listOf(InkCandidate("×", .02f), InkCandidate("x", .021f))),
                InkGlyph(listOf(InkCandidate("7", .13f), InkCandidate("1", .14f)))))
            lineSymbols = listOf("1", "×", "7"); expression = "1×7"
        }
        workspace.needsExpressionReview shouldBe true
        workspace.replaceLineSymbol(1, "×") shouldBe true
        workspace.needsExpressionReview shouldBe true
        workspace.replaceLineSymbol(2, "1") shouldBe true
        workspace.needsExpressionReview shouldBe false
        value(workspace.expression) shouldBe Num.ONE
        workspace.invalidateRecognition()
        workspace.reviewedLineSymbols shouldBe emptySet()
    }

    "pause and retry preserve a completed correction and acknowledged text" {
        val workspace = populate(addition)
        workspace.replaceInkSymbol(0, "4") shouldBe true
        workspace.editExpression("sqrt(4+1)") shouldBe true
        val tokens = workspace.reviewTokens
        val request = workspace.recognitionRequest
        workspace.pauseRecognition()
        workspace.recognitionPaused shouldBe true
        workspace.retryRecognition()
        workspace.recognitionPaused shouldBe false
        workspace.recognitionRequest shouldBe request + 1
        workspace.reviewTokens shouldBe tokens
        workspace.expression shouldBe "sqrt(4+1)"
        workspace.expressionWasReviewed shouldBe true
    }

    "late background edit cannot publish after a new stroke even if old tokens reappear" {
        val workspace = populate(addition)
        val revision = workspace.inkRevision
        val tokens = workspace.reviewTokens
        val edit = InkReview.replace(tokens, 0, "4")!!
        workspace.commitInk(addition + listOf(path(150, 0, 150, 80))) shouldBe true
        workspace.reviewTokens = tokens // Deliberately recreate the old shape; the revision still differs.
        workspace.applyInkEdit(revision, tokens, edit) shouldBe false
        workspace.expression shouldBe ""
    }

    "late background edit cannot overwrite a more recent symbol choice" {
        val workspace = populate(addition)
        val revision = workspace.inkRevision
        val tokens = workspace.reviewTokens
        val late = InkReview.replace(tokens, 0, "4")!!
        workspace.replaceInkSymbol(0, "8") shouldBe true
        workspace.applyInkEdit(revision, tokens, late) shouldBe false
        workspace.expression shouldBe "8+1"
    }

    "clearing a paused draft drops review state and old targets" {
        val workspace = populate(division)
        workspace.acknowledgeExpression(); workspace.pauseRecognition(); workspace.selectedGlyph = 1
        workspace.clear()
        workspace.expressionWasReviewed shouldBe false
        workspace.recognitionPaused shouldBe false
        workspace.reviewTokens shouldBe emptyList()
        workspace.selectedGlyph shouldBe null
        workspace.expression shouldBe ""
    }
})
