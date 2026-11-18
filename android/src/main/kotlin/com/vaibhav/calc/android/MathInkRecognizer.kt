package com.vaibhav.calc.android

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Local geometry templates plus bounded 2D grouping; not a trained handwriting model. */
object MathInkRecognizer {
    const val MAX_STROKES = 64
    const val MAX_POINTS = 12000
    private const val MAX_MILLIS = 3000L

    fun read(strokes: List<List<InkPoint>>): MathLayoutResult {
        if (strokes.isEmpty()) return MathLayoutResult.Rejected("Write an expression first.")
        if (strokes.size > MAX_STROKES || strokes.sumOf { it.size.toLong() } > MAX_POINTS)
            return MathLayoutResult.Rejected("Use at most $MAX_STROKES strokes and $MAX_POINTS points.")
        if (strokes.any { it.isEmpty() || it.any { p -> !p.x.isFinite() || !p.y.isFinite() || abs(p.x) > 1_000_000f || abs(p.y) > 1_000_000f } })
            return MathLayoutResult.Rejected("The ink contains invalid coordinates.")
        val started = System.nanoTime()
        fun expired() = System.nanoTime() - started > MAX_MILLIS * 1_000_000
        val boxes = strokes.map(::bounds)
        val tall = boxes.map { it.height }.filter { it > 3f }.sorted()
        val typical = tall.getOrNull(tall.size / 2) ?: max(10f, boxes.maxOf { it.width })
        val tolerance = max(1.2f, typical * .045f)
        val paths = strokes.map { sample(it, 28) }
        val parent = IntArray(strokes.size) { it }
        fun root(index: Int): Int { var i = index; while (parent[i] != i) i = parent[i]; return i }
        fun join(a: Int, b: Int) { parent[root(b)] = root(a) }
        val isolated = mutableSetOf<Int>()
        val forced = mutableMapOf<Int, String>()
        val candidates = mutableMapOf<Int, List<InkCandidate>>()
        fun horizontal(i: Int): Boolean {
            val b = boxes[i]
            return b.width > max(4f, typical * .3f) && b.height <= max(1.5f, b.width * .12f) &&
                paths[i].zipWithNext().sumOf { (a, c) -> hypot((c.x - a.x).toDouble(), (c.y - a.y).toDouble()) } <= b.width * 1.35
        }
        fun tiny(i: Int) = max(boxes[i].width, boxes[i].height) <= max(2f, typical * .09f)
        val bars = strokes.indices.filter(::horizontal)

        // Dots around a short bar form a division sign, rather than a fraction of dots.
        for (bar in bars) {
            if (bar in isolated) continue
            val b = boxes[bar]
            val dots = strokes.indices.filter { it != bar && tiny(it) && abs(boxes[it].centerX - b.centerX) <= b.width * .18f &&
                abs(boxes[it].centerY - b.centerY) in b.width * .13f..b.width * .7f }
            val above = dots.filter { boxes[it].centerY < b.centerY }
            val below = dots.filter { boxes[it].centerY > b.centerY }
            if (above.size == 1 && below.size == 1) {
                join(bar, above.single()); join(bar, below.single())
                isolated += listOf(bar, above.single(), below.single()); forced[bar] = "÷"
            }
        }
        // A fraction bar remains separate from both sides, even when their X ranges overlap.
        for (bar in bars) {
            if (bar in isolated) continue
            val b = boxes[bar]
            val covered = strokes.indices.filter { it != bar && boxes[it].centerX >= b.left && boxes[it].centerX <= b.right }
            if (covered.any { boxes[it].bottom < b.centerY - tolerance } && covered.any { boxes[it].top > b.centerY + tolerance }) {
                isolated += bar; forced[bar] = "−"
            }
        }
        // Keep a continuous radical with its overbar separate from the glyphs underneath.
        for (i in strokes.indices) {
            if (i in isolated || horizontal(i) || tiny(i)) continue
            if (expired()) return MathLayoutResult.Rejected("This drawing needs too much recognition work. Read a smaller expression.")
            val matches = InkRecognizer.recognize(listOf(strokes[i]))
            candidates[i] = matches
            if (matches.firstOrNull()?.let { it.symbol == "√" && it.distance < .13f } == true) {
                isolated += i; forced[i] = "√"
            }
        }
        // Touching strokes belong to one glyph. Segment distance catches crosses even when
        // neither stroke has a sampled point exactly at the intersection.
        for (i in strokes.indices) for (j in i + 1 until strokes.size) {
            if (i in isolated || j in isolated) continue
            if (overlap(boxes[i], boxes[j], tolerance) && near(paths[i], paths[j], tolerance)) join(i, j)
        }
        // Equals consists of disconnected, similarly sized parallel bars with nothing between.
        for (i in bars) for (j in bars) {
            if (j <= i || i in isolated || j in isolated || root(i) != i || root(j) != j) continue
            val a = boxes[i]; val b = boxes[j]
            val common = min(a.right, b.right) - max(a.left, b.left)
            val gap = abs(a.centerY - b.centerY)
            if (common >= max(a.width, b.width) * .8f && gap >= max(2f, max(a.height, b.height) * 2) && gap <= min(a.width, b.width) * .75f) {
                val between = strokes.indices.any { k -> k != i && k != j && boxes[k].centerX in max(a.left, b.left)..min(a.right, b.right) &&
                    boxes[k].centerY > min(a.centerY, b.centerY) && boxes[k].centerY < max(a.centerY, b.centerY) }
                if (!between) join(i, j)
            }
        }
        val groups = strokes.indices.groupBy(::root).values.sortedWith(compareBy<List<Int>> { group -> group.minOf { boxes[it].left } }
            .thenBy { group -> group.minOf { boxes[it].top } })
        if (groups.size > MathLayout.MAX_TOKENS) return MathLayoutResult.Rejected("There are too many symbols to read at once.")
        val tokens = ArrayList<MathToken>()
        for ((index, group) in groups.withIndex()) {
            if (expired()) return MathLayoutResult.Rejected("This drawing needs too much recognition work. Read a smaller expression.")
            if (group.size > 5) return MathLayoutResult.Rejected("Several connected symbols are unclear. Separate the glyphs or edit the expression.")
            val b = union(group.map { boxes[it] })
            val force = group.firstNotNullOfOrNull { forced[it] }
            val matches = when {
                force == "√" -> candidates[group.single()] ?: listOf(InkCandidate("√", 0f))
                force != null -> listOf(InkCandidate(force, 0f))
                group.all(::tiny) -> listOf(InkCandidate(".", 0f))
                group.size == 1 -> candidates[group.single()] ?: InkRecognizer.recognize(listOf(strokes[group.single()]))
                else -> InkRecognizer.recognize(group.map { strokes[it] })
            }
            val best = matches.firstOrNull() ?: return MathLayoutResult.Rejected("Symbol ${index + 1} could not be read. Rewrite it or type it.")
            if (!best.distance.isFinite() || best.distance > .19f || (b.width > max(typical, b.height) * 2.4f && force == null))
                return MathLayoutResult.Rejected("Symbol ${index + 1} is unclear or contains connected writing. Separate it before calculating.")
            val uncertain = best.distance > .075f || matches.drop(1).any { it.distance - best.distance < .018f && it.symbol != best.symbol }
            tokens += MathToken(best.symbol, b, matches.drop(1).map { it.symbol }, if (force == null) best.distance else null,
                uncertain = uncertain || force != null, sourceIndex = index)
        }
        return MathLayout.read(tokens)
    }

    private fun bounds(points: List<InkPoint>) = MathBox(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    private fun union(boxes: List<MathBox>) = MathBox(boxes.minOf { it.left }, boxes.minOf { it.top }, boxes.maxOf { it.right }, boxes.maxOf { it.bottom })
    private fun overlap(a: MathBox, b: MathBox, pad: Float) = a.left <= b.right + pad && b.left <= a.right + pad && a.top <= b.bottom + pad && b.top <= a.bottom + pad
    private fun sample(points: List<InkPoint>, count: Int): List<InkPoint> = if (points.size <= count) points else
        (0 until count).map { points[(it.toLong() * (points.size - 1) / (count - 1)).toInt()] }
    private fun near(a: List<InkPoint>, b: List<InkPoint>, distance: Float): Boolean {
        if (a.size == 1 && b.size == 1) return hypot(a[0].x - b[0].x, a[0].y - b[0].y) <= distance
        val aa = if (a.size == 1) listOf(a[0] to a[0]) else a.zipWithNext()
        val bb = if (b.size == 1) listOf(b[0] to b[0]) else b.zipWithNext()
        return aa.any { (p, q) -> bb.any { (r, s) -> segmentDistance(p, q, r, s) <= distance } }
    }
    private fun segmentDistance(a: InkPoint, b: InkPoint, c: InkPoint, d: InkPoint): Float {
        fun cross(p: InkPoint, q: InkPoint, r: InkPoint) = (q.x - p.x).toDouble() * (r.y - p.y) - (q.y - p.y).toDouble() * (r.x - p.x)
        val c1 = cross(a, b, c); val c2 = cross(a, b, d); val c3 = cross(c, d, a); val c4 = cross(c, d, b)
        if (c1 * c2 < 0.0 && c3 * c4 < 0.0) return 0f
        fun point(p: InkPoint, q: InkPoint, r: InkPoint): Float {
            val dx = r.x.toDouble() - q.x; val dy = r.y.toDouble() - q.y
            val length = dx * dx + dy * dy
            val t = if (length == 0.0) 0.0 else (((p.x - q.x) * dx + (p.y - q.y) * dy) / length).coerceIn(0.0, 1.0)
            return hypot(p.x - q.x - t * dx, p.y - q.y - t * dy).toFloat()
        }
        return minOf(point(a, c, d), point(b, c, d), point(c, a, b), point(d, a, b))
    }
}
