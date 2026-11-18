package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class PhotoMathCorrectionsSpec : StringSpec({
    fun token(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        MathToken(text, MathBox(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()))
    fun value(expression: String) = ((Calculator().evaluate(expression) as CalcResult.Ok).value as RealValue).num
    fun raster(vararg points: Pair<Int, Int>): IntArray {
        val pixels = IntArray(100 * 110) { -1 }
        points.toList().zipWithNext().forEach { (a, b) ->
            val steps = max(abs(a.first - b.first), abs(a.second - b.second))
            for (i in 0..steps) {
                val x = (a.first + (b.first - a.first) * i.toFloat() / steps).roundToInt()
                val y = (a.second + (b.second - a.second) * i.toFloat() / steps).roundToInt()
                pixels[y * 100 + x] = 0xff000000.toInt()
            }
        }
        return pixels
    }
    "a radical profile has a tall right arm and a lower left entry" {
        val root = raster(8 to 60, 30 to 95, 65 to 8, 88 to 8)
        PhotoMathCorrections.hasRadicalProfile(100, 110, root) shouldBe true
    }
    "a normal V and transparent or invalid pixels are not radical evidence" {
        PhotoMathCorrections.hasRadicalProfile(100, 110, raster(10 to 8, 50 to 95, 90 to 8)) shouldBe false
        PhotoMathCorrections.hasRadicalProfile(100, 110, IntArray(11000)) shouldBe false
        PhotoMathCorrections.hasRadicalProfile(100, 110, IntArray(1)) shouldBe false
        PhotoMathCorrections.hasRadicalProfile(513, 110, IntArray(513 * 110)) shouldBe false
    }
    "an offered root correction preserves the default V and all following digits" {
        val tokens = listOf(token("V", 500, 204, 580, 324), token("1", 584, 204, 632, 324), token("6", 652, 204, 708, 324))
        val unchanged = MathLayout.read(tokens).shouldBeInstanceOf<MathLayoutResult.Draft>()
        val correction = PhotoMathCorrections.suggest(tokens, setOf(0)).single()
        correction.expression shouldBe "sqrt(16)"
        value(correction.expression) shouldBe Num.of(4)
        val photo = PhotoMathDraft("V16", unchanged, corrections = listOf(correction))
        photo.expressionForReview shouldBe "V16"
        photo.completeRawText shouldBe "V16"
        tokens.first().text shouldBe "V"
        PhotoMathCorrections.suggest(tokens, emptySet()) shouldBe emptyList()
    }
    "a proposed decimal root keeps its complete radicand" {
        val tokens = listOf(token("V", 443, 199, 529, 326), token("1", 531, 202, 587, 328),
            token(".", 598, 204, 616, 329), token("4", 622, 205, 688, 332), token("4", 694, 208, 758, 335))
        val correction = PhotoMathCorrections.suggest(tokens, setOf(0)).single()
        correction.expression shouldBe "sqrt(1.44)"
        value(correction.expression) shouldBe Num.of(6, 5)
    }
    "overlapping OCR fragments get an explicit removal option without losing the source mark" {
        val tokens = listOf(token("N", 373, 266, 433, 284), token("3", 451, 207, 478, 253),
            token("2", 378, 274, 432, 364), token("+", 520, 274, 598, 364), token("1", 618, 274, 668, 364))
        val correction = PhotoMathCorrections.suggest(tokens, emptySet()).single()
        value(correction.expression) shouldBe Num.of(9)
        correction.explanation.contains("'N'") shouldBe true
        correction.explanation.contains("Omit it only") shouldBe true
        MathLayout.read(tokens).shouldBeInstanceOf<MathLayoutResult.Draft>().expression shouldBe "N2^(3)+1"
    }
    "separate letters and actual small variables are never offered as duplicate removal" {
        val digit = token("2", 378, 274, 432, 364)
        PhotoMathCorrections.suggest(listOf(token("N", 280, 266, 340, 284), digit), emptySet()) shouldBe emptyList()
        PhotoMathCorrections.suggest(listOf(token("x", 373, 266, 433, 284), digit), emptySet()) shouldBe emptyList()
        PhotoMathCorrections.suggest(listOf(token("3", 373, 266, 433, 284), digit), emptySet()) shouldBe emptyList()
    }
    "correction work and result counts are bounded" {
        PhotoMathCorrections.suggest(List(65) { token("V", it * 30, 0, it * 30 + 20, 40) }, setOf(0)) shouldBe emptyList()
        PhotoMathCorrections.suggest(listOf(token("V".repeat(2049), 0, 0, 20, 40)), setOf(0)) shouldBe emptyList()
        val tokens = (0 until 8).flatMap { i -> listOf(token("V", i * 80, 0, i * 80 + 25, 40), token("1", i * 80 + 30, 0, i * 80 + 50, 40)) }
        val suggestions = PhotoMathCorrections.suggest(tokens, tokens.indices.filter { it % 2 == 0 }.toSet())
        suggestions.size shouldBe PhotoMathCorrections.MAX_CORRECTIONS
        suggestions.all { it.expression.length <= 2048 } shouldBe true
    }
})
