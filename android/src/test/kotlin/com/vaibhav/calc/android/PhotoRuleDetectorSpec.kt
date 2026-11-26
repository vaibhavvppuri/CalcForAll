package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.CancellationException

class PhotoRuleDetectorSpec : StringSpec({
    val width = 400; val height = 260
    fun color(value: Int) = 0xff000000.toInt() or (value shl 16) or (value shl 8) or value
    fun token(text: String, x: Int, y: Int) = MathToken(text, MathBox(x.toFloat(), y.toFloat(), x + 30f, y + 50f))
    fun raster(vararg bars: IntRange, gray: Int = 202, top: Int = 130): IntArray {
        val pixels = IntArray(width * height) { color(245) }
        for (y in top..top + 2) for (bar in bars) for (x in bar) pixels[y * width + x] = color(gray)
        return pixels
    }
    fun detect(pixels: IntArray, tokens: List<MathToken>, stop: () -> Boolean = { false }) =
        PhotoRuleDetector.detect(width, height, tokens,
            readRow = { y, row -> pixels.copyInto(row, 0, y * width, (y + 1) * width) }, shouldStop = stop)
    val fractions = listOf(token("1", 80, 20), token("2", 80, 175), token("+", 185, 100),
        token("1", 290, 20), token("3", 290, 175))

    "visible gray fraction bars missed by the old fixed darkness cutoff produce the independent five sixths result" {
        val pixels = raster(50..145, 260..355)
        // Every source bar pixel is above the former luminance<110 criterion.
        ((pixels[130 * width + 50] and 255) > 110) shouldBe true
        MathLayout.read(fractions).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        val recovered = detect(pixels, fractions)
        recovered.size shouldBe 2
        recovered.all { it.uncertain } shouldBe true
        val draft = MathLayout.read(fractions + recovered).shouldBeInstanceOf<MathLayoutResult.Draft>()
        val result = (Calculator().evaluate(draft.expression) as CalcResult.Ok).value.shouldBeInstanceOf<RealValue>()
        result.num shouldBe Num.of(5, 6)
    }

    "one sided faint bars retain evidence and refuse an incomplete fraction instead of returning the numeral" {
        val tokens = listOf(token("1", 80, 20))
        MathLayout.read(tokens).shouldBeInstanceOf<MathLayoutResult.Draft>().expression shouldBe "1"
        val recovered = detect(raster(50..145, top = 90), tokens)
        recovered.size shouldBe 1
        MathLayout.read(tokens + recovered).shouldBeInstanceOf<MathLayoutResult.Rejected>()
    }

    "a broad page rule cannot become a guessed fraction or disappear from an incomplete one" {
        val twoSided = listOf(token("1", 80, 20), token("2", 80, 175))
        val oneSided = listOf(token("1", 80, 20))
        for ((tokens, pixels) in listOf(twoSided to raster(0..399), oneSided to raster(0..399, top = 90))) {
            val recovered = detect(pixels, tokens)
            recovered.single().text shouldBe "?"
            recovered.single().uncertain shouldBe true
            MathLayout.read(tokens + recovered).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        }
    }

    "no rule is invented from a blank faint noise or short speck" {
        detect(raster(), fractions) shouldBe emptyList()
        detect(raster(50..145, gray = 239), fractions) shouldBe emptyList()
        detect(raster(70..76), fractions) shouldBe emptyList()
    }

    "a glyph crossing a rule and a thick shadow remain non-rules" {
        detect(raster(50..145), listOf(token("8", 80, 110))) shouldBe emptyList()
        val pixels = IntArray(width * height) { index -> color(if (index % width in 50..145) 150 else 245) }
        detect(pixels, fractions) shouldBe emptyList()
    }

    "line scanning has cancellable bounded rows and rejects invalid geometry" {
        var checks = 0
        shouldThrow<CancellationException> { detect(raster(50..145), fractions) { ++checks > 20 } }
        checks shouldBe 21
        detect(raster(), listOf(MathToken("1", MathBox(Float.NaN, 0f, 10f, 20f)))) shouldBe emptyList()
        shouldThrow<IllegalArgumentException> {
            PhotoRuleDetector.detect(2049, 1, fractions, { _, _ -> error("must not read") })
        }
    }
})
