package com.vaibhav.calc.android

import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Visible, thin horizontal rules omitted by OCR. Every recovered rule remains uncertain. */
internal object PhotoRuleDetector {
    fun detect(width: Int, height: Int, tokens: List<MathToken>, readRow: (Int, IntArray) -> Unit,
               shouldStop: () -> Boolean = { false }): List<MathToken> {
        require(width in 1..PhotoCropGeometry.MAX_EDGE && height in 1..PhotoCropGeometry.MAX_EDGE) {
            "Read a decoded image with edges no larger than 2048 pixels."
        }
        if (tokens.isEmpty() || tokens.size > MathLayout.MAX_TOKENS || tokens.any { token ->
                val b = token.bounds
                listOf(b.left, b.top, b.right, b.bottom).any { !it.isFinite() || abs(it) > 1_000_000f } ||
                    b.width <= 0 || b.height <= 0
            }) return emptyList()
        fun checkActive() {
            if (Thread.currentThread().isInterrupted || shouldStop()) throw CancellationException("Photo line analysis cancelled.")
        }
        checkActive()
        val heights = tokens.map { it.bounds.height }.filter { it >= 4f }.sorted()
        val nominal = heights.getOrNull(heights.size / 2) ?: return emptyList()
        val minRun = max(8, (nominal * .65f).toInt())
        val margin = nominal * .75f
        val top = (tokens.minOf { it.bounds.top } - margin).toInt().coerceIn(0, height - 1)
        val bottom = (tokens.maxOf { it.bounds.bottom } + margin).toInt().coerceIn(0, height - 1)
        if (top > bottom) return emptyList()
        val row = IntArray(width)
        val histogram = IntArray(256)
        for (y in top..bottom) {
            checkActive(); readRow(y, row)
            for (pixel in row) histogram[PhotoToneCurve.gray(pixel, PhotoImageTreatment.ORIGINAL)]++
        }
        val total = width * (bottom - top + 1)
        fun quantile(target: Int): Int {
            var count = 0
            for (i in histogram.indices) { count += histogram[i]; if (count >= target) return i }
            return 255
        }
        val paper = quantile(max(1, total * 95 / 100))
        val ink = histogram.indexOfFirst { it > 0 }
        if (paper - ink < 18) return emptyList()
        // A fixed <110 threshold loses visibly gray rules. Require at least twelve
        // grayscale levels below the light-paper estimate; very weak marks remain
        // unsupported. Geometric guards below still distinguish rules from glyphs.
        val threshold = paper - max(12, (paper - ink) / 10)
        val rules = mutableListOf<MathBox>()
        for (y in top..bottom) {
            checkActive(); readRow(y, row)
            var start = -1
            for (x in 0..width) {
                val dark = x < width && PhotoToneCurve.gray(row[x], PhotoImageTreatment.ORIGINAL) < threshold
                if (dark && start < 0) start = x
                if (!dark && start >= 0) {
                    if (x - start >= minRun) {
                        val previous = rules.indexOfLast { it.bottom >= y - 1 && abs(it.left - start) <= 2 && abs(it.right - x) <= 2 }
                        if (previous >= 0) rules[previous] = rules[previous].copy(bottom = y + 1f)
                        else {
                            require(rules.size < 512) { "This image has too many lines or marks. Crop one expression and try again." }
                            rules += MathBox(start.toFloat(), y.toFloat(), x.toFloat(), y + 1f)
                        }
                    }
                    start = -1
                }
            }
        }
        checkActive()
        return rules.mapNotNull { b ->
            val root = tokens.firstOrNull { token -> val q = token.bounds
                token.text == "√" && abs(b.top - q.top) <= nominal * .2f && abs(b.left - q.right) <= nominal * .25f &&
                    b.right > q.right + nominal * .3f }
            val clear = tokens.none { token -> val q = token.bounds
                token !== root && q.left < b.right && q.right > b.left && q.top <= b.bottom && q.bottom >= b.top }
            val neighbours = tokens.filter { token -> val q = token.bounds
                q.centerX in b.left..b.right && ((q.top > b.bottom && q.top - b.bottom < nominal * 2.5f) ||
                    (q.bottom < b.top && b.top - q.bottom < nominal * 2.5f)) }
            val below = neighbours.any { it.bounds.top > b.bottom }
            val above = neighbours.any { it.bounds.bottom < b.top }
            // Do not turn a page-wide/table rule into a fraction of a tiny nearby
            // number, or silently discard a long one-sided fraction bar. Preserve
            // ambiguous geometry as '?' so automatic layout must refuse it.
            val fitted = neighbours.isNotEmpty() && b.width <=
                neighbours.maxOf { it.bounds.right } - neighbours.minOf { it.bounds.left } + nominal * 2.25f
            if (b.height > max(2f, nominal * .18f) || !clear ||
                !(if (root != null) below else above || below)) return@mapNotNull null
            MathToken(if (fitted) "-" else "?", b, uncertain = true)
        }.take(MathLayout.MAX_TOKENS - tokens.size + 1)
    }
}
