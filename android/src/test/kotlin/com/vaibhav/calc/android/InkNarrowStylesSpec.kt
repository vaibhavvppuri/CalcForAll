package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Hand-specified paths, independent of the templates. Not population handwriting accuracy. */
class InkNarrowStylesSpec : StringSpec({
    fun path(vararg xy: Int) = xy.toList().chunked(2).map { InkPoint(it[0].toFloat(), it[1].toFloat()) }
    fun squeeze(ink: List<List<InkPoint>>, width: Float) = ink.map { line -> line.map { p ->
        InkPoint(23f + p.x * width, 17f + p.y)
    } }
    fun shift(ink: List<List<InkPoint>>, dx: Float, dy: Float = 0f) = ink.map { line ->
        line.map { p -> InkPoint(p.x + dx, p.y + dy) }
    }
    fun value(expression: String) = Calculator().evaluate(expression).shouldBeInstanceOf<CalcResult.Ok<RealValue>>().value.num
    val zero = listOf(path(45,5,25,9,14,28,13,75,25,101,46,108,65,99,76,72,77,34,65,11,45,5))
    val four = listOf(path(64,110,64,5,10,70,88,70))
    val nine = listOf(path(79,29,73,8,47,3,22,16,13,39,26,58,54,61,76,45,80,29,78,67,64,96,41,111))
    val two = listOf(path(10,27,18,9,40,3,66,10,78,29,72,48,56,64,30,83,11,102,42,109,74,101))
    val three = listOf(path(8,9,70,9,41,49,65,49,82,66,82,88,63,106,32,109,9,99))
    val narrow = listOf("0" to squeeze(zero,.45f), "4" to squeeze(four,.65f),
        "9" to squeeze(nine,.65f), "2" to squeeze(two,.65f))

    "narrow loop and turning digits receive their intended review-required candidate instead of one" {
        for ((label, ink) in narrow) withClue(label) {
            val candidates = InkRecognizer.recognize(ink)
            candidates.first().symbol shouldBe label
            candidates.first().requiresReview shouldBe true
            candidates.any { it.symbol == "1" } shouldBe true // Preserve the old interpretation for explicit comparison.
            val reading = MathInkRecognizer.readDetailed(ink)
            val draft = reading.layout.shouldBeInstanceOf<MathLayoutResult.Draft>()
            draft.expression shouldBe label
            reading.tokens.single().uncertain shouldBe true
            draft.ambiguous shouldBe true
            value(draft.expression) shouldBe Num.of(label.toLong())
        }
    }

    "narrow assistance retains bounded unique finite candidates under direction and size changes" {
        for ((label, ink) in narrow) {
            val transformed = ink.reversed().map { line -> line.reversed().map { p -> InkPoint(p.x * 1.7f + 151f, p.y * 1.7f - 50f) } }
            withClue(label) {
                val candidates = InkRecognizer.recognize(transformed)
                candidates.first().symbol shouldBe label
                candidates.first().requiresReview shouldBe true
                (candidates.size in 1..5) shouldBe true
                candidates.map { it.symbol }.distinct().size shouldBe candidates.size
                candidates.all { it.distance.isFinite() && it.distance >= 0f } shouldBe true
                candidates.zipWithNext().all { (a,b) -> a.distance <= b.distance } shouldBe true
            }
        }
    }

    "ordinary closed four and continuous nine keep their already useful recognition" {
        InkRecognizer.recognize(four).first().symbol shouldBe "4"
        InkRecognizer.recognize(nine).first().symbol shouldBe "9"
    }

    "a narrow flat-top three remains an explicit three or five ambiguity" {
        val ink = squeeze(three,.65f)
        val candidates = InkRecognizer.recognize(ink)
        candidates.map { it.symbol }.containsAll(listOf("3", "5")) shouldBe true
        candidates.first().requiresReview shouldBe true
        val reading = MathInkRecognizer.readDetailed(ink)
        reading.tokens.single().uncertain shouldBe true
        // Do not declare this still-ambiguous style accurately recognized from the top rank.
        val correction = InkReview.replace(reading.tokens, 0, "3")!!
        correction.layout.shouldBeInstanceOf<MathLayoutResult.Draft>().expression shouldBe "3"
    }

    "real narrow style metadata gates both math and spaced-line calculation despite a low score" {
        val ink = narrow.first().second
        val reading = MathInkRecognizer.readDetailed(ink)
        val draft = reading.layout.shouldBeInstanceOf<MathLayoutResult.Draft>()
        (reading.tokens.single().matchDistance!! < .075f) shouldBe true
        val math = InkWorkspace().apply { commitInk(ink); reviewTokens = reading.tokens; mathDraft = draft; expression = draft.expression }
        math.needsExpressionReview shouldBe true
        math.acknowledgeExpression() shouldBe true
        math.needsExpressionReview shouldBe false
        val line = InkLineRecognizer.recognize(ink).shouldBeInstanceOf<InkLineResult.Draft>()
        line.glyphs.single().candidates.first().requiresReview shouldBe true
        val workspace = InkWorkspace().apply {
            changeMode("Spaced line"); commitInk(ink); lineDraft = line
            lineSymbols = line.glyphs.map { it.candidates.first().symbol }; expression = line.text
        }
        workspace.needsExpressionReview shouldBe true
        workspace.replaceLineSymbol(0, "0") shouldBe true
        workspace.needsExpressionReview shouldBe false
    }

    "straight tilted hooked and retraced ones never trigger narrow digit assistance" {
        val ones = listOf(listOf(path(30,5,31,110)), listOf(path(30,5,10,110)),
            listOf(path(12,25,32,8,35,101)), listOf(path(20,0,40,100,20,0)))
        for (ink in ones) {
            val candidates = InkRecognizer.recognize(ink)
            candidates.first().symbol shouldBe "1"
            candidates.none { it.requiresReview } shouldBe true
        }
    }

    "ordinary plus minus equals and an exact slash keep their own candidates" {
        val marks = listOf(
            "+" to listOf(path(5,55,85,54),path(46,15,45,95)),
            "−" to listOf(path(5,40,85,41)),
            "=" to listOf(path(5,35,85,36),path(5,60,85,61)),
            "/" to listOf(path(80,0,0,80)))
        for ((label, ink) in marks) {
            val candidates = InkRecognizer.recognize(ink)
            candidates.first().symbol shouldBe label
            candidates.first().requiresReview shouldBe false
        }
    }

    "a steep straight slash offers slash and tilted-one review instead of inventing a radical" {
        val slash = listOf(path(65,5,15,110))
        for (ink in listOf(slash, slash.map { it.reversed() })) {
            val candidates = InkRecognizer.recognize(ink)
            candidates.map { it.symbol } shouldBe listOf("/", "1")
            candidates.all { it.requiresReview } shouldBe true
        }
        val ink = listOf(path(0,5,0,110)) + shift(slash,45f) + listOf(path(150,5,150,110))
        val reading = MathInkRecognizer.readDetailed(ink)
        val draft = reading.layout.shouldBeInstanceOf<MathLayoutResult.Draft>()
        draft.expression shouldBe "1/1"
        reading.tokens[1].uncertain shouldBe true
        value(draft.expression) shouldBe Num.ONE
        val line = InkLineRecognizer.recognize(ink).shouldBeInstanceOf<InkLineResult.Draft>()
        line.text shouldBe "1/1"
        InkRecovery.unclearLineSymbols(line, emptySet()).contains(1) shouldBe true
    }

    "a radical hook and overbar are not reclassified as a straight slash" {
        val root = listOf(path(0,55,18,78,42,2,100,0))
        InkRecognizer.recognize(root).first().symbol shouldBe "√"
        MathInkRecognizer.readDetailed(root).tokens.single().text shouldBe "√"
    }

    "single-tap decimal survives deduplication while separated taps cannot become one glyph" {
        InkRecognizer.recognize(listOf(path(22,31))).single().symbol shouldBe "."
        InkRecognizer.recognize(listOf(path(22,31), path(22,68))) shouldBe emptyList()
        InkRecognizer.recognize(listOf(listOf(InkPoint(Float.NaN, 0f)))) shouldBe emptyList()
        InkRecognizer.recognize(listOf(listOf(InkPoint(Float.MAX_VALUE, 0f)))) shouldBe emptyList()
        val decimal = listOf(path(0,0,0,100),path(25,100),path(50,0,50,100))
        MathInkRecognizer.read(decimal).shouldBeInstanceOf<MathLayoutResult.Draft>().expression shouldBe "1.1"
    }

    "neighboring narrow digits remain separate groups instead of one guessed glyph" {
        val ink = narrow[0].second + shift(narrow[1].second,90f)
        val reading = MathInkRecognizer.readDetailed(ink)
        reading.tokens.map { it.text } shouldBe listOf("0", "4")
        reading.tokens.map { it.sourceIndex }.distinct().size shouldBe 2
        reading.tokens.all { it.uncertain } shouldBe true
    }

    "a narrow denominator preserves the independent numerator and fraction bar" {
        val ink = listOf(path(45,-50,45,-15),path(20,0,90,0)) + narrow[1].second
        val reading = MathInkRecognizer.readDetailed(ink)
        val draft = reading.layout.shouldBeInstanceOf<MathLayoutResult.Draft>()
        draft.expression shouldBe "((1)/(4))"
        reading.tokens.size shouldBe 3
        value(draft.expression) shouldBe Num.of(1, 4)
    }
})
