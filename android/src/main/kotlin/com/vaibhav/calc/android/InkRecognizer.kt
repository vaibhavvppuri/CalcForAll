package com.vaibhav.calc.android

import kotlin.math.*

data class InkPoint(val x: Float, val y: Float)
data class InkCandidate(val symbol: String, val distance: Float)

/** Small offline symbol matcher, not a trained handwriting or layout model. Geometry only. */
object InkRecognizer {
    private fun stroke(vararg xy: Float) = xy.toList().chunked(2).map { InkPoint(it[0], it[1]) }
    private fun curve(cx: Float, cy: Float, rx: Float, ry: Float, start: Float = 0f, end: Float = 2f * PI.toFloat()) =
        (0..36).map { i -> val a = start + (end - start) * i / 36f; InkPoint(cx + rx * cos(a), cy + ry * sin(a)) }
    private val templates: List<Pair<String, List<List<InkPoint>>>> = listOf(
        "0" to listOf(curve(.5f, .5f, .35f, .5f)),
        "1" to listOf(stroke(.5f, 0f, .5f, 1f)),
        "1" to listOf(stroke(.25f, .2f, .5f, 0f, .5f, 1f), stroke(.2f, 1f, .8f, 1f)),
        "2" to listOf(stroke(0f, .2f, .15f, .05f, .5f, 0f, .85f, .1f, 1f, .3f, .85f, .5f, 0f, 1f, 1f, 1f)),
        "3" to listOf(stroke(0f, .1f, .4f, 0f, .8f, .1f, 1f, .25f, .8f, .4f, .35f, .5f, .8f, .55f, 1f, .75f, .8f, .95f, .4f, 1f, 0f, .9f)),
        "4" to listOf(stroke(.8f, 0f, 0f, .65f, 1f, .65f), stroke(.8f, .1f, .8f, 1f)),
        "4" to listOf(stroke(.1f, 0f, .1f, .6f, .9f, .6f), stroke(.8f, 0f, .8f, 1f)),
        "5" to listOf(stroke(1f, 0f, .1f, 0f, .1f, .45f, .6f, .4f, .9f, .55f, 1f, .75f, .8f, .95f, .3f, 1f, 0f, .85f)),
        "6" to listOf(stroke(.9f, .05f, .6f, 0f, .2f, .25f, 0f, .65f, .15f, .9f, .5f, 1f, .85f, .9f, 1f, .65f, .8f, .45f, .4f, .4f, .05f, .6f)),
        "7" to listOf(stroke(0f, 0f, 1f, 0f, .3f, 1f)),
        "7" to listOf(stroke(0f, 0f, 1f, 0f, .3f, 1f), stroke(.2f, .5f, .8f, .5f)),
        "8" to listOf(curve(.5f, .25f, .35f, .25f), curve(.5f, .75f, .45f, .25f)),
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

    fun recognize(strokes: List<List<InkPoint>>): List<InkCandidate> {
        // Refuse oversized input before flattening or visiting individual points.
        if (strokes.size > 32) return emptyList()
        var pointCount = 0
        for (stroke in strokes) {
            if (stroke.size > 12000 - pointCount) return emptyList()
            pointCount += stroke.size
        }
        if (pointCount < 2) return emptyList()
        val points = strokes.flatten()
        // These are screen coordinates, not calculator values. Bound finite input as
        // well: extreme Float coordinates can otherwise overflow geometry into NaN.
        if (points.any { !it.x.isFinite() || !it.y.isFinite() || abs(it.x) > 1_000_000f || abs(it.y) > 1_000_000f }) return emptyList()
        val width = points.maxOf { it.x } - points.minOf { it.x }
        val height = points.maxOf { it.y } - points.minOf { it.y }
        if (!width.isFinite() || !height.isFinite()) return emptyList()
        val cloud = normalize(strokes)
        if(cloud.isEmpty()) return if (max(width, height) < .00001f) listOf(InkCandidate(".", 0f)) else emptyList()
        fun distance(a: List<InkPoint>, b: List<InkPoint>): Float = a.sumOf { p ->
            b.minOf { q -> hypot(p.x - q.x, p.y - q.y) }.toDouble()
        }.toFloat() / a.size
        return normalized.map { (symbol, sample, count) ->
            InkCandidate(symbol, (distance(cloud, sample) + distance(sample, cloud)) / 2 + abs(strokes.size - count) * .008f)
        }.groupBy { it.symbol }.map { (_, variants) -> variants.minBy { it.distance } }.sortedBy { it.distance }.take(5)
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
