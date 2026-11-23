package com.vaibhav.calc.android

import kotlin.math.abs

/** Heuristic advice only: it is not an OCR score, blur diagnosis or a reason to hide a draft. */
data class PhotoQualityReport(val lowContrast: Boolean, val mostlyDark: Boolean, val softEdges: Boolean, val sampleCount: Int) {
    val messages: List<String> get() = buildList {
        if (mostlyDark) add("The image is mostly dark. Try Light text on dark or better lighting.")
        if (lowContrast) add("The image has little contrast or detail. Try Boost contrast, crop closer, or retake it in better light.")
        if (softEdges) add("Edges appear soft. If symbols are unclear, refocus or retake the photo; corrections cannot restore missing detail.")
    }
}

/** Sample adjacent original pixels, rather than blur a downscaled copy before measuring. */
internal class PhotoQualitySamples {
    private var count = 0
    private val histogram = IntArray(256)
    private var total = 0L
    private var gradient = 0L
    private var curvature = 0L

    fun add(center: Int, left: Int, right: Int, above: Int, below: Int) {
        require(count < MAX_SAMPLES && center in 0..255 && left in 0..255 && right in 0..255 && above in 0..255 && below in 0..255) {
            "Invalid or oversized photo quality sample."
        }
        count++; histogram[center]++; total += center
        gradient += abs(right - left) + abs(below - above)
        curvature += abs(left + right + above + below - 4 * center)
    }

    fun report(): PhotoQualityReport {
        require(count > 0) { "The photo has no quality samples." }
        val low = histogram.indexOfFirst { it > 0 }; val high = histogram.indexOfLast { it > 0 }
        var darkPixels = 0
        for (value in 0 until 100) darkPixels += histogram[value]
        return PhotoQualityReport(lowContrast = high - low < 48,
            mostlyDark = total < count * 65L && darkPixels * 10L >= count * 9L,
            softEdges = high - low >= 48 && gradient >= maxOf(300L, count * 2L) && curvature * 10L < gradient * 7L,
            sampleCount = count)
    }

    companion object { const val MAX_SAMPLES = 128 * 128 }
}
