package com.vaibhav.calc.android

import kotlin.math.*

data class InkPoint(val x: Float, val y: Float)
data class InkCandidate(val symbol: String, val distance: Float, val requiresReview: Boolean = false)

/** Small offline symbol matcher, not a trained handwriting or layout model. Geometry only. */
object InkRecognizer {
    private fun stroke(vararg xy: Float) = xy.toList().chunked(2).map { InkPoint(it[0], it[1]) }
    private fun curve(cx: Float, cy: Float, rx: Float, ry: Float, start: Float = 0f, end: Float = 2f * PI.toFloat()) =
        (0..36).map { i -> val a = start + (end - start) * i / 36f; InkPoint(cx + rx * cos(a), cy + ry * sin(a)) }
    private val templates: List<Pair<String, List<List<InkPoint>>>> = listOf(
        "0" to listOf(curve(.5f, .5f, .35f, .5f)),
        "1" to listOf(stroke(.5f, 0f, .5f, 1f)),
        "1" to listOf(stroke(.2f, .22f, .5f, 0f, .5f, 1f)),
        "1" to listOf(stroke(.25f, .2f, .5f, 0f, .5f, 1f), stroke(.2f, 1f, .8f, 1f)),
        "2" to listOf(stroke(0f, .2f, .15f, .05f, .5f, 0f, .85f, .1f, 1f, .3f, .85f, .5f, 0f, 1f, 1f, 1f)),
        "3" to listOf(stroke(0f, .1f, .4f, 0f, .8f, .1f, 1f, .25f, .8f, .4f, .35f, .5f, .8f, .55f, 1f, .75f, .8f, .95f, .4f, 1f, 0f, .9f)),
        "4" to listOf(stroke(.8f, 0f, 0f, .65f, 1f, .65f), stroke(.8f, .1f, .8f, 1f)),
        "4" to listOf(stroke(.1f, 0f, .1f, .6f, .9f, .6f), stroke(.8f, 0f, .8f, 1f)),
        "5" to listOf(stroke(1f, 0f, .1f, 0f, .1f, .45f, .6f, .4f, .9f, .55f, 1f, .75f, .8f, .95f, .3f, 1f, 0f, .85f)),
        "5" to listOf(stroke(.1f, .1f, .1f, .45f, .6f, .4f, .9f, .55f, 1f, .75f, .8f, .95f, .3f, 1f, 0f, .85f), stroke(.1f, 0f, 1f, 0f)),
        "6" to listOf(stroke(.9f, .05f, .6f, 0f, .2f, .25f, 0f, .65f, .15f, .9f, .5f, 1f, .85f, .9f, 1f, .65f, .8f, .45f, .4f, .4f, .05f, .6f)),
        "7" to listOf(stroke(0f, 0f, 1f, 0f, .3f, 1f)),
        "7" to listOf(stroke(0f, 0f, 1f, 0f), stroke(.96f, .1f, .3f, 1f)),
        "7" to listOf(stroke(0f, 0f, 1f, 0f, .3f, 1f), stroke(.2f, .5f, .8f, .5f)),
        "7" to listOf(stroke(0f, 0f, 1f, 0f), stroke(.96f, .1f, .3f, 1f), stroke(.2f, .5f, .8f, .5f)),
        "8" to listOf(curve(.5f, .25f, .35f, .25f), curve(.5f, .75f, .45f, .25f)),
        "8" to listOf((0..72).map { i -> val a = 2f * PI.toFloat() * i / 72f
            InkPoint(.5f + .4f * sin(2f * a), .5f - .5f * cos(a)) }),
        "9" to listOf(curve(.5f, .3f, .4f, .3f), stroke(.9f, .25f, .85f, .65f, .65f, .95f, .2f, 1f)),
        "+" to listOf(stroke(0f, .5f, 1f, .5f), stroke(.5f, 0f, .5f, 1f)),
        "−" to listOf(stroke(0f, .5f, 1f, .5f)),
        "×" to listOf(stroke(0f, 0f, 1f, 1f), stroke(1f, 0f, 0f, 1f)),
        "/" to listOf(stroke(1f, 0f, 0f, 1f)),
        "=" to listOf(stroke(0f, .3f, 1f, .3f), stroke(0f, .7f, 1f, .7f)),
        "%" to listOf(curve(.22f, .22f, .15f, .18f), stroke(.85f, 0f, .15f, 1f), curve(.78f, .78f, .15f, .18f)),
        "!" to listOf(stroke(.5f, 0f, .5f, .72f), stroke(.5f, 1f)),
        "(" to listOf(curve(.8f, .5f, .5f, .5f, PI.toFloat()/2, PI.toFloat()*1.5f)),
        ")" to listOf(curve(.2f, .5f, .5f, .5f, -PI.toFloat()/2, PI.toFloat()/2)),
        "√" to listOf(stroke(0f, .55f, .2f, .8f, .4f, 0f, 1f, 0f)),
        "π" to listOf(stroke(0f, .1f, 1f, .1f), stroke(.25f, .1f, .2f, 1f), stroke(.75f, .1f, .75f, .9f, .9f, 1f)),
        "x" to listOf(stroke(0f, 0f, 1f, 1f), stroke(1f, 0f, 0f, 1f)),
        "y" to listOf(stroke(0f, 0f, .5f, .5f, 1f, 0f), stroke(.5f, .5f, .5f, 1f)),
        "y" to listOf(stroke(0f, 0f, .3f, .5f, .7f, .5f, 1f, 0f, .7f, .9f, .2f, 1f)),
        "z" to listOf(stroke(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)),
        "^" to listOf(stroke(0f, .7f, .5f, 0f, 1f, .7f)),
    )
    private val normalized by lazy { templates.map { (label, paths) -> Triple(label, normalize(paths), paths.size) } }
    private data class NarrowTemplate(val symbol: String, val cloud: List<InkPoint>, val strokes: Int, val aspect: Float)
    /** A small, fixed style family, not unconstrained anisotropic normalization of every mark. */
    private val narrowNormalized by lazy {
        templates.filter { it.first in setOf("0", "2", "3", "4", "5", "6", "7", "8", "9") }.flatMap { (label, paths) ->
            listOf(.45f, .70f).map { factor ->
                val adjusted = paths.map { path -> path.map { p -> InkPoint(p.x * factor, p.y) } }
                val points = adjusted.flatten()
                val width = points.maxOf { it.x } - points.minOf { it.x }
                val height = points.maxOf { it.y } - points.minOf { it.y }
                NarrowTemplate(label, normalize(adjusted), paths.size, width / height)
            }
        }
    }

    /**
     * A detached cap is admitted only with the endpoint/topology of a five or seven.
     * This is not a generic "nearby bar" merge: a fraction rule, overline, minus,
     * or a bar over a vertical one must remain available to the layout reader.
     * The caller has already enforced the total drawing's coordinate/work bounds.
     */
    internal fun detachedTopDigit(cap: List<InkPoint>, body: List<List<InkPoint>>): InkCandidate? {
        if (cap.size < 2 || body.isEmpty() || body.size > 2 || body.any { it.size < 2 }) return null
        fun length(path: List<InkPoint>) = path.zipWithNext().sumOf { (a, b) ->
            hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
        }
        val all = body.flatten()
        val left = all.minOf { it.x }; val right = all.maxOf { it.x }
        val top = all.minOf { it.y }; val bottom = all.maxOf { it.y }
        val height = bottom - top; val width = right - left
        val capLeft = cap.minOf { it.x }; val capRight = cap.maxOf { it.x }
        val capTop = cap.minOf { it.y }; val capBottom = cap.maxOf { it.y }
        val capWidth = capRight - capLeft
        if (height < 12f || width !in height * .25f..height * 1.5f ||
            capWidth !in height * .35f..height * 1.5f || capBottom - capTop > capWidth * .12f ||
            top - capBottom !in 0f..height * .18f || length(cap) > capWidth * 1.2f ||
            left < capLeft - height * .15f || right > capRight + height * .15f) return null
        val main = body.maxBy { path -> path.maxOf { it.y } - path.minOf { it.y } }
        val oriented = if (main.first().y <= main.last().y) main else main.reversed()
        val start = oriented.first(); val end = oriented.last()
        if (start.y > top + height * .08f || end.y < bottom - height * .18f) return null
        val possibleSeven = abs(start.x - capRight) <= capWidth * .2f &&
            start.x - end.x >= width * .8f && end.y - start.y >= height * .85f &&
            length(main) <= hypot(width, height) * 1.15f &&
            body.filter { it !== main }.all { path ->
                val y = path.map { it.y }.average().toFloat()
                val w = path.maxOf { it.x } - path.minOf { it.x }
                path.maxOf { it.y } - path.minOf { it.y } <= height * .1f &&
                    w in height * .2f..height * .85f && y in top + height * .25f..top + height * .7f
            }
        val shoulder = oriented.firstOrNull { it.y >= top + height * .25f }
        val possibleFive = body.size == 1 && abs(start.x - capLeft) <= capWidth * .22f &&
            length(main) >= height * 1.6f && shoulder != null &&
            abs(shoulder.x - start.x) <= height * .18f && shoulder.y <= top + height * .65f
        if (!possibleFive && !possibleSeven) return null
        val best = recognize(listOf(cap) + body).firstOrNull() ?: return null
        return best.takeIf { it.distance <= .075f &&
            ((it.symbol == "5" && possibleFive) || (it.symbol == "7" && possibleSeven)) }
    }

    fun recognize(strokes: List<List<InkPoint>>): List<InkCandidate> {
        // Refuse oversized input before flattening or visiting individual points.
        if (strokes.size > 32) return emptyList()
        var pointCount = 0
        for (stroke in strokes) {
            if (stroke.size > 12000 - pointCount) return emptyList()
            pointCount += stroke.size
        }
        if (pointCount == 0) return emptyList()
        val points = strokes.flatten()
        // These are screen coordinates, not calculator values. Bound finite input as
        // well: extreme Float coordinates can otherwise overflow geometry into NaN.
        if (points.any { !it.x.isFinite() || !it.y.isFinite() || abs(it.x) > 1_000_000f || abs(it.y) > 1_000_000f }) return emptyList()
        val width = points.maxOf { it.x } - points.minOf { it.x }
        val height = points.maxOf { it.y } - points.minOf { it.y }
        if (!width.isFinite() || !height.isFinite()) return emptyList()
        // These disconnected shapes require all their strokes. Their mismatch score
        // is a review cue, not a probability; the math reader also marks them uncertain.
        if (InkSymbolGeometry.division(strokes)) return listOf(InkCandidate("÷", .04f))
        if (InkSymbolGeometry.separateRoot(strokes)) return listOf(InkCandidate("√", .04f))
        val cloud = normalize(strokes)
        if(cloud.isEmpty()) return if (max(width, height) < .00001f) listOf(InkCandidate(".", 0f)) else emptyList()
        fun distance(a: List<InkPoint>, b: List<InkPoint>): Float = a.sumOf { p ->
            b.minOf { q -> hypot(p.x - q.x, p.y - q.y) }.toDouble()
        }.toFloat() / a.size
        val ordinary = normalized.map { (symbol, sample, count) ->
            InkCandidate(symbol, (distance(cloud, sample) + distance(sample, cloud)) / 2 + abs(strokes.size - count) * .008f)
        }
        // A genuinely straight stroke has neither a radical hook nor an overbar.
        // Its intended slash versus strongly tilted one is still a user's choice.
        if (ordinary.minBy { it.distance }.symbol != "/" && isDescendingStroke(strokes, width, height))
            return listOf(InkCandidate("/", .04f, requiresReview = true), InkCandidate("1", .05f, requiresReview = true))
        // Uniform normalization can make a narrow loop/turning digit resemble a straight
        // one. Offer only a fixed, aspect-compatible digit style when real lateral detail
        // and path length distinguish it from a line. A style-dependent result is always
        // review-required, regardless of how small its geometric distance happens to be.
        val narrow = if (ordinary.minOf { it.distance } > .025f && hasNarrowDetail(strokes, points, width, height)) {
            val aspect = width / height
            narrowNormalized.filter { aspect / it.aspect in .72f..1.38f }.map { variant ->
                InkCandidate(variant.symbol,
                    (distance(cloud, variant.cloud) + distance(variant.cloud, cloud)) / 2 +
                        abs(strokes.size - variant.strokes) * .008f + .006f,
                    requiresReview = true)
            }
        } else emptyList()
        val ranked = (ordinary + narrow).groupBy { it.symbol }.map { (_, variants) -> variants.minBy { it.distance } }
            .sortedBy { it.distance }.take(5)
        val original = ordinary.minBy { it.distance }
        return if (ranked.first().requiresReview && ranked.none { it.symbol == original.symbol })
            (ranked.take(4) + original).sortedBy { it.distance }
        else ranked
    }

    private fun hasNarrowDetail(strokes: List<List<InkPoint>>, points: List<InkPoint>, width: Float, height: Float): Boolean {
        if (strokes.size > 3 || height <= 0f || width / height !in .12f.. .70f) return false
        var length = 0.0
        for (path in strokes) for (i in 1 until path.size) length += hypot(
            (path[i].x - path[i - 1].x).toDouble(), (path[i].y - path[i - 1].y).toDouble())
        if (length / height !in 1.35..4.5) return false
        val top = points.minBy { it.y }; val bottom = points.maxBy { it.y }
        val dx = bottom.x.toDouble() - top.x; val dy = bottom.y.toDouble() - top.y
        val diagonal = hypot(dx, dy)
        // Retracing a tilted one increases path length but does not add a digit's turns.
        return points.any { p -> abs(dx * (p.y - top.y) - dy * (p.x - top.x)) / diagonal >= width * .18 }
    }

    private fun isDescendingStroke(strokes: List<List<InkPoint>>, width: Float, height: Float): Boolean {
        if (strokes.size != 1 || height <= 0f || width / height !in .35f..1.8f) return false
        val path = strokes.single()
        val a = path.first(); val b = path.last()
        val dx = b.x.toDouble() - a.x; val dy = b.y.toDouble() - a.y
        if (dx * dy >= 0.0) return false
        val diagonal = hypot(dx, dy)
        var length = 0.0
        for (i in 1 until path.size) length += hypot((path[i].x - path[i - 1].x).toDouble(),
            (path[i].y - path[i - 1].y).toDouble())
        return length <= diagonal * 1.04 && path.all { p ->
            abs(dx * (p.y - a.y) - dy * (p.x - a.x)) / diagonal <= diagonal * .025
        }
    }

    private fun normalize(strokes: List<List<InkPoint>>): List<InkPoint> {
        val all = strokes.flatten()
        if(all.isEmpty()) return emptyList()
        val minX = all.minOf { it.x }; val minY = all.minOf { it.y }
        val width = all.maxOf { it.x } - minX; val height = all.maxOf { it.y } - minY
        val scale = max(width, height)
        if(scale < .00001f) return emptyList()
        val lines = strokes.flatMap { it.zipWithNext() }.filter { (a,b) -> a != b }
        val lengths = lines.map { (a,b) -> hypot(a.x - b.x, a.y - b.y) }
        val length = lengths.sum()
        if(length == 0f) return emptyList()
        // A separate tap is meaningful in ! and dotted symbols. Arc-length-only sampling
        // used to erase every zero-length stroke from an otherwise nonempty glyph.
        val dots = strokes.filter { path -> path.isNotEmpty() && path.all { p -> p == path.first() } }.map { it.first() }.distinct()
        val dotSamples = minOf(16, dots.size * 4)
        val lineSamples = 64 - dotSamples
        val sampled = (0 until lineSamples).map { index ->
            var target = length * index / (lineSamples - 1)
            var segment = 0
            while(segment < lengths.lastIndex && target > lengths[segment]) { target -= lengths[segment]; segment++ }
            val (a,b) = lines[segment]; val fraction = (target / lengths[segment]).coerceIn(0f, 1f)
            InkPoint((a.x + (b.x-a.x)*fraction - minX) / scale, (a.y + (b.y-a.y)*fraction - minY) / scale)
        } + (0 until dotSamples).map { index ->
            val p = dots[index % dots.size]
            InkPoint((p.x - minX) / scale, (p.y - minY) / scale)
        }
        val cx = sampled.sumOf { it.x.toDouble() }.toFloat()/sampled.size
        val cy = sampled.sumOf { it.y.toDouble() }.toFloat()/sampled.size
        return sampled.map { InkPoint(it.x-cx, it.y-cy) }
    }
}
