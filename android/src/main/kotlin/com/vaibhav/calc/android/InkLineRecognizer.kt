package com.vaibhav.calc.android

import kotlin.math.abs
import kotlin.math.max

data class InkGlyph(val candidates: List<InkCandidate>)

sealed interface InkLineResult {
    data class Draft(val glyphs: List<InkGlyph>) : InkLineResult {
        val text: String get() = glyphs.joinToString("") { it.candidates.first().symbol }
    }
    data class Rejected(val reason: String) : InkLineResult
}

/** Spatially separates a single line; every result still requires human review. */
object InkLineRecognizer {
    const val MAX_STROKES = 64
    const val MAX_POINTS = 12000
    const val MAX_SYMBOLS = 32
    private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val centerY get() = (top + bottom) / 2
    }
    private fun bounds(paths: List<List<InkPoint>>): Bounds {
        val points = paths.flatten()
        return Bounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    }

    fun recognize(strokes: List<List<InkPoint>>): InkLineResult {
        fun reject(reason: String) = InkLineResult.Rejected(reason)
        if (strokes.isEmpty()) return reject("Write a spaced line first.")
        if (strokes.size > MAX_STROKES || strokes.sumOf { it.size.toLong() } > MAX_POINTS)
            return reject("Use at most 64 strokes and 12,000 points per line.")
        if (strokes.any { it.isEmpty() } || strokes.any { path -> path.any {
                !it.x.isFinite() || !it.y.isFinite() || abs(it.x) > 1_000_000 || abs(it.y) > 1_000_000
            } }) return reject("The ink contains invalid points.")

        // Merge overlapping horizontal extents, independent of stroke order.
        // A late crossbar therefore stays with its plus, four or equals sign.
        val ordered = strokes.sortedBy { path -> path.minOf { it.x } }
        val groups = mutableListOf<MutableList<List<InkPoint>>>()
        var right = Float.NEGATIVE_INFINITY
        for (path in ordered) {
            val box = bounds(listOf(path))
            if (groups.isEmpty() || box.left > right + 1f) groups.add(mutableListOf(path))
            else groups.last().add(path)
            right = max(right, box.right)
        }
        if (groups.size > MAX_SYMBOLS) return reject("Use at most 32 separated symbols.")
        val boxes = groups.map(::bounds)
        val height = max(1f, boxes.maxOf { it.height })
        val centers = boxes.filter { it.height >= height * .35f }.map { it.centerY }.sorted()
        val center = centers.getOrNull(centers.size / 2) ?: boxes.first().centerY
        val glyphs = mutableListOf<InkGlyph>()
        for ((index, group) in groups.withIndex()) {
            val box = boxes[index]
            val dot = max(box.width, box.height) <= max(1f, height * .08f)
            if (group.size > 4 || box.width > max(12f, height * 1.7f))
                return reject("Symbols overlap or span other symbols. Leave a gap between each symbol; stacked fractions and radicals are not supported.")
            val detachedCap = if (group.size in 2..3) group.indices.firstOrNull { capIndex ->
                InkRecognizer.detachedTopDigit(group[capIndex], group.filterIndexed { index, _ -> index != capIndex }) != null
            } else null
            val division = InkSymbolGeometry.division(group)
            if (group.size >= 3) {
                val parts = group.map { bounds(listOf(it)) }
                val connected = mutableSetOf(0)
                val tolerance = max(1f, height * .03f)
                repeat(parts.size) {
                    parts.indices.filter { it !in connected }.forEach { next ->
                        val b = parts[next]
                        if (connected.any { old -> val a = parts[old]
                            a.left <= b.right + tolerance && b.left <= a.right + tolerance &&
                                a.top <= b.bottom + tolerance && b.top <= a.bottom + tolerance
                        }) connected.add(next)
                    }
                }
                if (connected.size != parts.size && detachedCap == null && !division)
                    return reject("Separate stacked marks are ambiguous. Use one line without fractions or superscripts.")
            }
            if (!dot && abs(box.centerY - center) > height * .3f)
                return reject("Use one horizontal line. Superscripts, stacked fractions and multiple lines are not supported.")
            if (dot && box.centerY < center)
                return reject("A raised dot is ambiguous. Write decimal dots near the baseline.")
            val raw = if (dot) listOf(InkCandidate(".", 0f)) else InkRecognizer.recognize(group)
            // A detached digit cap is meaningful, but an arbitrary overline must
            // not disappear into a digit (or into factorial's stem/dot template).
            // Equals, pi and ordinary connected crossbars retain their own rules.
            if (group.size > 1 && raw.firstOrNull()?.symbol?.let { it in "0123456789!" } == true) {
                for ((capIndex, cap) in group.withIndex()) {
                    val capBox = bounds(listOf(cap))
                    val body = group.filterIndexed { index, _ -> index != capIndex }
                    val bodyBox = bounds(body)
                    if (capBox.width >= max(8f, bodyBox.height * .35f) &&
                        capBox.height <= capBox.width * .12f &&
                        bodyBox.top > capBox.bottom + max(1f, bodyBox.height * .03f) &&
                        InkRecognizer.detachedTopDigit(cap, body) == null)
                        return reject("A bar above symbol ${index + 1} is ambiguous. Complete its digit strokes or use Math layout for fractions and roots.")
                }
            }
            val supported = "0123456789.+−×/÷=()^xπ%!"
            if (raw.isNotEmpty() && raw.first().symbol !in supported)
                return reject("Symbol ${index + 1} needs single-symbol mode. Radical bars and two-dimensional layouts are not supported in a line.")
            val candidates = raw.filter { it.symbol in supported }
            if (candidates.isEmpty() || candidates.first().distance > .22f)
                return reject("Symbol ${index + 1} is unclear. Separate the symbols or use single-symbol mode.")
            glyphs.add(InkGlyph(candidates))
        }
        return InkLineResult.Draft(glyphs)
    }
}
