package com.vaibhav.calc.android

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Tokens are complete grouped input, never a partial result from a failed recognition pass. */
data class MathInkReading(val layout: MathLayoutResult, val tokens: List<MathToken>)

/** Local geometry templates plus bounded 2D grouping; not a trained handwriting model. */
object MathInkRecognizer {
    const val MAX_STROKES = 64
    const val MAX_POINTS = 12000
    private const val MAX_MILLIS = 3000L

    fun read(strokes: List<List<InkPoint>>): MathLayoutResult = readDetailed(strokes).layout

    fun readDetailed(strokes: List<List<InkPoint>>): MathInkReading = readDetailed(strokes, InkRecognizer::recognize)

    /** Internal seam for exercising a missing/weak matcher result without weakening production thresholds. */
    internal fun readDetailed(strokes: List<List<InkPoint>>,
                              recognizeGlyph: (List<List<InkPoint>>) -> List<InkCandidate>): MathInkReading {
        fun reject(reason: String) = MathInkReading(MathLayoutResult.Rejected(reason), emptyList())
        if (strokes.isEmpty()) return reject("Write an expression first.")
        if (strokes.size > MAX_STROKES || strokes.sumOf { it.size.toLong() } > MAX_POINTS)
            return reject("Use at most $MAX_STROKES strokes and $MAX_POINTS points.")
        if (strokes.any { it.isEmpty() || it.any { p -> !p.x.isFinite() || !p.y.isFinite() || abs(p.x) > 1_000_000f || abs(p.y) > 1_000_000f } })
            return reject("The ink contains invalid coordinates.")
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
        val detachedDigits = mutableSetOf<Int>()
        fun horizontal(i: Int): Boolean {
            val b = boxes[i]
            return b.width > max(4f, typical * .3f) && b.height <= max(1.5f, b.width * .12f) &&
                paths[i].zipWithNext().sumOf { (a, c) -> hypot((c.x - a.x).toDouble(), (c.y - a.y).toDouble()) } <= b.width * 1.35
        }
        fun tiny(i: Int) = max(boxes[i].width, boxes[i].height) <= max(2f, typical * .09f)
        val bars = strokes.indices.filter(::horizontal)

        fun pathLength(i: Int) = paths[i].zipWithNext().sumOf { (a, b) ->
            hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        }
        // A separate overbar must belong to exactly one radical hook, and vice versa.
        // Group before fraction/ordinary touching rules so the bar keeps its full scope.
        val roots = mutableListOf<Pair<Int, Int>>()
        for (bar in bars) for (hook in strokes.indices) {
            if (hook == bar || hook in bars) continue
            if (expired()) return reject("This drawing needs too much recognition work. Read a smaller expression.")
            if (InkSymbolGeometry.separateRoot(strokes[hook], strokes[bar])) roots += hook to bar
        }
        if (roots.any { pair -> roots.count { it.first == pair.first } != 1 || roots.count { it.second == pair.second } != 1 })
            return reject("A square-root hook has competing overbars. Separate the roots or correct the drawing.")
        for ((hook, bar) in roots) {
            join(hook, bar); isolated += listOf(hook, bar); forced[hook] = "√"
            candidates[hook] = listOf(InkCandidate("√", .04f))
        }
        // Percent's two loops do not touch its slash. The diagonal and upper-left /
        // lower-right arrangement distinguish it from ordinary baseline 0/0.
        for (slash in strokes.indices) {
            if (slash in isolated) continue
            val b = boxes[slash]; val first = strokes[slash].first(); val last = strokes[slash].last()
            if (b.height < 12f || b.width !in b.height * .25f..b.height * 1.5f ||
                (last.x - first.x) * (last.y - first.y) >= 0f ||
                abs(last.x - first.x) < b.width * .9f || abs(last.y - first.y) < b.height * .9f ||
                pathLength(slash) > hypot(b.width, b.height) * 1.15f) continue
            fun loop(i: Int): Boolean {
                val q = boxes[i]
                if (i == slash || i in isolated || q.width !in b.height * .1f..b.height * .6f ||
                    q.height !in b.height * .1f..b.height * .6f || q.width !in q.height * .45f..q.height * 1.8f) return false
                val a = strokes[i].first(); val z = strokes[i].last()
                return hypot(a.x - z.x, a.y - z.y) <= max(2f, max(q.width, q.height) * .25f) &&
                    pathLength(i) in max(q.width, q.height) * 2.0..max(q.width, q.height) * 4.5
            }
            val upper = strokes.indices.filter { i -> val q = boxes[i]
                loop(i) && q.centerY in b.top + b.height * .05f..b.top + b.height * .42f &&
                    q.centerX in b.left - b.height * .25f..b.centerX - b.height * .05f }
            val lower = strokes.indices.filter { i -> val q = boxes[i]
                loop(i) && q.centerY in b.top + b.height * .58f..b.top + b.height * .95f &&
                    q.centerX in b.centerX + b.height * .05f..b.right + b.height * .25f }
            if (upper.size == 1 && lower.size == 1) {
                join(slash, upper.single()); join(slash, lower.single())
                isolated += listOf(slash, upper.single(), lower.single()); forced[slash] = "%"
            }
        }
        // Factorial's dot is directly below its stem, separated from the baseline.
        // A normal decimal beside a digit or a dot across a fraction rule is not a bang.
        for (stem in strokes.indices) {
            if (stem in isolated) continue
            val b = boxes[stem]
            if (b.height < 12f || b.width > b.height * .18f || pathLength(stem) > b.height * 1.2f) continue
            val dots = strokes.indices.filter { i -> val q = boxes[i]
                i != stem && i !in isolated && tiny(i) &&
                    abs(q.centerX - b.centerX) <= max(2f, b.height * .12f) &&
                    q.centerY - b.bottom in b.height * .08f..b.height * .45f &&
                    strokes.indices.none { k -> val r = boxes[k]
                        k != stem && k != i && r.left <= b.centerX + tolerance && r.right >= b.centerX - tolerance &&
                            r.bottom > b.bottom && r.top < q.top }
            }
            if (dots.size == 1) {
                join(stem, dots.single()); isolated += listOf(stem, dots.single()); forced[stem] = "!"
            }
        }

        // Dots around a short bar form a division sign, rather than a fraction of dots.
        for (bar in bars) {
            if (bar in isolated) continue
            val b = boxes[bar]
            val dots = strokes.indices.filter { it != bar && it !in isolated && tiny(it) && abs(boxes[it].centerX - b.centerX) <= b.width * .18f &&
                abs(boxes[it].centerY - b.centerY) in b.width * .13f..b.width * .7f }
            val above = dots.filter { boxes[it].centerY < b.centerY }
            val below = dots.filter { boxes[it].centerY > b.centerY }
            if (above.size == 1 && below.size == 1 && InkSymbolGeometry.division(
                    listOf(strokes[bar], strokes[above.single()], strokes[below.single()]))) {
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
            if (expired()) return reject("This drawing needs too much recognition work. Read a smaller expression.")
            val matches = recognizeGlyph(listOf(strokes[i]))
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
        // Some writers lift the pen before adding the top of 5/7. Recognize the
        // complete bounded shape, require a unique pairing in BOTH directions,
        // and leave uncertain/spanning bars to the existing fraction reader.
        val components = strokes.indices.filter { it !in isolated }.groupBy(::root).values.toList()
        val topPairs = mutableListOf<Pair<Int, List<Int>>>()
        for (bar in bars) {
            if (bar in isolated || components.none { it.size == 1 && it.single() == bar }) continue
            for (body in components) {
                if (bar in body || body.size > 2) continue
                if (expired()) return reject("This drawing needs too much recognition work. Read a smaller expression.")
                val b = union(body.map { boxes[it] }); val cap = boxes[bar]
                if (b.top < cap.bottom || b.top - cap.bottom > b.height * .18f) continue
                // An independent numerator or another mark inside this cap/body
                // footprint makes joining ambiguous. Do not consume it as a digit.
                if (strokes.indices.any { k -> k != bar && k !in body &&
                        boxes[k].centerX in cap.left..cap.right &&
                        boxes[k].bottom >= cap.top - b.height * .6f && boxes[k].top <= b.bottom }) continue
                if (InkRecognizer.detachedTopDigit(strokes[bar], body.map { strokes[it] }) != null)
                    topPairs += bar to body
            }
        }
        for ((bar, body) in topPairs) {
            if (topPairs.count { it.first == bar } != 1 || topPairs.count { it.second == body } != 1) continue
            body.forEach { join(bar, it) }
            detachedDigits += bar
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
        if (groups.size > MathLayout.MAX_TOKENS) return reject("There are too many symbols to read at once.")
        val tokens = ArrayList<MathToken>()
        for ((index, group) in groups.withIndex()) {
            if (expired()) return reject("This drawing needs too much recognition work. Read a smaller expression.")
            if (group.size > 5) return reject("Several connected symbols are unclear. Separate the glyphs or edit the expression.")
            val b = union(group.map { boxes[it] })
            val force = group.firstNotNullOfOrNull { forced[it] }
            val matches = when {
                force == "√" -> candidates[group.first { forced[it] == "√" }] ?: listOf(InkCandidate("√", 0f))
                force != null -> listOf(InkCandidate(force, 0f))
                group.all(::tiny) -> listOf(InkCandidate(".", 0f))
                group.size == 1 -> candidates[group.single()] ?: recognizeGlyph(listOf(strokes[group.single()]))
                else -> recognizeGlyph(group.map { strokes[it] })
            }
            if (b.width > max(typical, b.height) * 2.4f && force == null)
                return reject("Symbol ${index + 1} may contain connected writing. Separate it before calculating.")
            val best = matches.firstOrNull()
            if (best == null || !best.distance.isFinite() || best.distance > .19f) {
                tokens += MathToken("?", b, matches.filter { it.distance.isFinite() }.map { it.symbol }.distinct().take(5),
                    uncertain = true, sourceIndex = index)
                continue
            }
            val uncertain = best.distance > .075f || matches.drop(1).any { it.distance - best.distance < .018f && it.symbol != best.symbol }
            tokens += MathToken(best.symbol, b, matches.drop(1).map { it.symbol }, if (force == null) best.distance else null,
                uncertain = uncertain || force != null || group.any { it in detachedDigits }, sourceIndex = index)
        }
        if (expired()) return reject("This drawing needs too much recognition work. Read a smaller expression.")
        val complete = tokens.toList()
        return MathInkReading(MathLayout.read(complete), complete)
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
