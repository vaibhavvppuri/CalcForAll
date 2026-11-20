package com.vaibhav.calc.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PhotoToneCurveSpec : StringSpec({
    fun histogram(vararg entries: Pair<Int, Int>) = IntArray(256).also { h -> entries.forEach { h[it.first] = it.second } }

    "alpha is composited on white before explicit inversion" {
        PhotoToneCurve.gray(0, PhotoImageTreatment.CONTRAST) shouldBe 255
        PhotoToneCurve.gray(0, PhotoImageTreatment.INVERT_CONTRAST) shouldBe 0
        PhotoToneCurve.gray(0x80000000.toInt(), PhotoImageTreatment.CONTRAST) shouldBe 127
        PhotoToneCurve.gray(0xff000000.toInt(), PhotoImageTreatment.INVERT_CONTRAST) shouldBe 255
        PhotoToneCurve.gray(-1, PhotoImageTreatment.CONTRAST) shouldBe 255
    }
    "a faint two-tone expression stretches to dark ink on white paper" {
        val curve = PhotoToneCurve.fromHistogram(histogram(212 to 100, 236 to 900))
        curve.low shouldBe 212; curve.high shouldBe 236
        curve.value(212) shouldBe 0; curve.value(236) shouldBe 255
        curve.value(224) shouldBe 128
        curve.argb(236) shouldBe -1
    }
    "the contrast curve is monotone and clamps without reversing symbol polarity" {
        val curve = PhotoToneCurve.fromHistogram(histogram(30 to 300, 200 to 700))
        val mapped = (0..255).map(curve::value)
        mapped.zipWithNext().all { (a, b) -> a <= b } shouldBe true
        curve.value(0) shouldBe 0; curve.value(255) shouldBe 255
    }
    "a uniform page stays uniform instead of gaining synthetic ink" {
        for (gray in listOf(0, 64, 128, 255)) {
            val curve = PhotoToneCurve.fromHistogram(histogram(gray to 1000))
            curve.value(gray) shouldBe gray
        }
    }
    "a sparse decimal point survives a mostly blank histogram" {
        val curve = PhotoToneCurve.fromHistogram(histogram(100 to 1, 240 to 1000))
        curve.low shouldBe 100; curve.high shouldBe 240
        curve.value(100) shouldBe 0; curve.value(240) shouldBe 255
    }
    "histograms reject malformed counts and enforce the decoded pixel budget" {
        shouldThrow<IllegalArgumentException> { PhotoToneCurve.fromHistogram(IntArray(255)) }
        shouldThrow<IllegalArgumentException> { PhotoToneCurve.fromHistogram(IntArray(256)) }
        shouldThrow<IllegalArgumentException> { PhotoToneCurve.fromHistogram(histogram(1 to -1)) }
        shouldThrow<IllegalArgumentException> { PhotoToneCurve.fromHistogram(histogram(1 to 4_194_305)) }
        shouldThrow<IllegalArgumentException> { PhotoToneCurve.fromHistogram(histogram(1 to Int.MAX_VALUE, 2 to Int.MAX_VALUE)) }
        PhotoToneCurve.fromHistogram(histogram(255 to 4_194_304)).value(255) shouldBe 255
    }
})
