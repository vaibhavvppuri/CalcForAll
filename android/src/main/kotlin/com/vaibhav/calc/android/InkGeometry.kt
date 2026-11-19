package com.vaibhav.calc.android

import kotlin.math.min

/** A fixed paper coordinate system avoids distorting or clipping ink after a resize. */
internal data class InkViewport(val paperWidth: Float, val paperHeight: Float, val width: Float, val height: Float) {
    init { require(listOf(paperWidth, paperHeight, width, height).all { it.isFinite() && it > 0f }) }
    val scale = min(width / paperWidth, height / paperHeight)
    val left = (width - paperWidth * scale) / 2f
    val top = (height - paperHeight * scale) / 2f
    fun toPaper(x: Float, y: Float) = InkPoint((x - left) / scale, (y - top) / scale)
    fun contains(x: Float, y: Float) = x >= left && x <= left + paperWidth * scale && y >= top && y <= top + paperHeight * scale
}

internal object InkGeometry {
    /** Sweep a circular eraser over visible segments, including two-point fast strokes. */
    fun erase(strokes: List<List<InkPoint>>, from: InkPoint, to: InkPoint, radius: Float): List<List<InkPoint>> {
        require(radius.isFinite() && radius > 0f)
        require(listOf(from.x, from.y, to.x, to.y).all { it.isFinite() })
        val squared = radius.toDouble() * radius
        val kept = strokes.filterNot { path ->
            if (path.size == 1) pointSegment(path[0], from, to) <= squared
            else path.zipWithNext().any { (a, b) -> segmentDistance(a, b, from, to) <= squared }
        }
        return if (kept.size == strokes.size) strokes else kept
    }

    private fun pointSegment(p: InkPoint, a: InkPoint, b: InkPoint): Double {
        val dx = b.x.toDouble() - a.x; val dy = b.y.toDouble() - a.y
        val length = dx * dx + dy * dy
        val t = if (length == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / length).coerceIn(0.0, 1.0)
        val x = p.x - a.x - t * dx; val y = p.y - a.y - t * dy
        return x * x + y * y
    }

    private fun segmentDistance(a: InkPoint, b: InkPoint, c: InkPoint, d: InkPoint): Double {
        fun cross(p: InkPoint, q: InkPoint, r: InkPoint) = (q.x.toDouble() - p.x) * (r.y - p.y) - (q.y.toDouble() - p.y) * (r.x - p.x)
        val c1 = cross(a, b, c); val c2 = cross(a, b, d); val c3 = cross(c, d, a); val c4 = cross(c, d, b)
        if ((c1 < 0 && c2 > 0 || c1 > 0 && c2 < 0) && (c3 < 0 && c4 > 0 || c3 > 0 && c4 < 0)) return 0.0
        return minOf(pointSegment(a, c, d), pointSegment(b, c, d), pointSegment(c, a, b), pointSegment(d, a, b))
    }
}
