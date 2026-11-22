package com.vaibhav.calc.android

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Narrow stroke-topology rules. Callers validate the whole drawing's size and coordinates first. */
internal object InkSymbolGeometry {
    private fun box(path: List<InkPoint>) = MathBox(path.minOf { it.x }, path.minOf { it.y },
        path.maxOf { it.x }, path.maxOf { it.y })
    private fun length(path: List<InkPoint>): Double {
        var result = 0.0
        for (i in 1 until path.size) result += hypot((path[i].x - path[i - 1].x).toDouble(),
            (path[i].y - path[i - 1].y).toDouble())
        return result
    }

    /** Exactly one nearly horizontal bar and two centered, compact marks on opposite sides. */
    fun division(strokes: List<List<InkPoint>>): Boolean {
        if (strokes.size != 3 || strokes.any { it.isEmpty() }) return false
        val boxes = strokes.map(::box)
        val bar = boxes.indices.maxBy { boxes[it].width }
        val b = boxes[bar]
        if (b.width < 6f || b.height > b.width * .10f || length(strokes[bar]) > b.width * 1.2) return false
        val dots = boxes.filterIndexed { index, _ -> index != bar }
        if (dots.any { max(it.width, it.height) > b.width * .16f ||
                abs(it.centerX - b.centerX) > b.width * .18f ||
                abs(it.centerY - b.centerY) !in b.width * .18f..b.width * .7f }) return false
        return dots.any { it.bottom < b.top } && dots.any { it.top > b.bottom }
    }

    /** A rising radical hook meeting a separate overbar; neither stroke is discarded. */
    fun separateRoot(hook: List<InkPoint>, bar: List<InkPoint>): Boolean {
        if (hook.size < 3 || bar.size < 2) return false
        val h = box(hook); val b = box(bar)
        if (h.height < 12f || h.width !in h.height * .18f..h.height * .9f ||
            b.width !in h.height * .35f..h.height * 12f || b.height > max(1f, b.width * .07f)) return false
        val forward = if (hook.first().y > hook.last().y) hook else hook.asReversed()
        val start = forward.first(); val end = forward.last()
        if (start.x > h.left + h.width * .12f || end.x < h.right - h.width * .12f ||
            start.y !in h.top + h.height * .3f..h.top + h.height * .85f || end.y > h.top + h.height * .08f ||
            b.left - end.x !in -h.height * .08f..h.height * .12f ||
            abs(b.centerY - end.y) > h.height * .08f || length(bar) > b.width * 1.2) return false
        val valley = forward.indices.maxBy { forward[it].y }
        if (valley == 0 || valley == forward.lastIndex) return false
        val low = forward[valley]
        if (low.y < start.y + h.height * .12f ||
            low.x !in h.left + h.width * .1f..h.right - h.width * .2f) return false
        fun straightEnough(path: List<InkPoint>): Boolean {
            val a = path.first(); val z = path.last()
            return length(path) <= hypot((a.x - z.x).toDouble(), (a.y - z.y).toDouble()) * 1.18
        }
        return straightEnough(forward.subList(0, valley + 1)) && straightEnough(forward.subList(valley, forward.size))
    }

    fun separateRoot(strokes: List<List<InkPoint>>): Boolean = strokes.size == 2 &&
        (separateRoot(strokes[0], strokes[1]) || separateRoot(strokes[1], strokes[0]))
}
