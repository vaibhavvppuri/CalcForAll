package com.vaibhav.calc.android

/** Collects one gesture, including coalesced motion and its pen-up position. */
internal class InkStrokeBuffer(private val pointLimit: Int, private val width: Float, private val height: Float) {
    enum class Failure { POINT_LIMIT, INVALID_COORDINATE }
    var failure: Failure? = null
        private set
    private val points = ArrayList<InkPoint>()

    init {
        require(pointLimit in 1..MAX_POINTS)
        require(width.isFinite() && height.isFinite() && width > 0f && height > 0f)
    }

    fun append(position: InkPoint, historical: Sequence<InkPoint> = emptySequence()) {
        if (failure != null) return
        var visited = 0
        for (point in historical) {
            if (++visited > MAX_EVENT_SAMPLES) { failure = Failure.POINT_LIMIT; return }
            add(point)
            if (failure != null) return
        }
        add(position)
    }

    private fun add(point: InkPoint) {
        if (!point.x.isFinite() || !point.y.isFinite()) { failure = Failure.INVALID_COORDINATE; return }
        val clamped = InkPoint(point.x.coerceIn(0f, width), point.y.coerceIn(0f, height))
        if (points.lastOrNull() == clamped) return
        if (points.size == pointLimit) { failure = Failure.POINT_LIMIT; return }
        points += clamped
    }

    fun snapshot(): List<InkPoint> = points.toList()
    /** A rejected gesture can never return a truncated stroke for recognition. */
    fun completed(): List<InkPoint>? = if (failure == null) points.toList() else null

    companion object {
        const val MAX_POINTS = 2000
        private const val MAX_EVENT_SAMPLES = 12000
    }
}
