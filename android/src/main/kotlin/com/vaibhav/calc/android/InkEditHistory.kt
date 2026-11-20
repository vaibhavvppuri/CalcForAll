package com.vaibhav.calc.android

import java.util.Collections
import kotlin.math.abs

/** Whole-gesture history. Retained snapshots and live ink share one bounded point budget. */
internal class InkEditHistory {
    private data class Snapshot(val strokes: List<List<InkPoint>>, val points: Int)
    private val undo = ArrayDeque<Snapshot>()
    private val redo = ArrayDeque<Snapshot>()
    private var savedPoints = 0

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
    internal val retainedEdits: Int get() = undo.size + redo.size
    internal val retainedPoints: Int get() = savedPoints

    /** A no-op leaves the redo branch intact. Validate before allocating snapshots. */
    fun record(before: List<List<InkPoint>>, after: List<List<InkPoint>>): Boolean {
        val oldPoints = checkedPointCount(before)
        val currentPoints = checkedPointCount(after)
        if (before == after) return false
        val previous = Snapshot(immutableCopy(before), oldPoints)
        savedPoints -= redo.sumOf { it.points }
        redo.clear()
        undo.addLast(previous)
        savedPoints += oldPoints
        trim(currentPoints)
        return true
    }

    fun undo(current: List<List<InkPoint>>): List<List<InkPoint>>? = move(undo, redo, current)
    fun redo(current: List<List<InkPoint>>): List<List<InkPoint>>? = move(redo, undo, current)

    fun clear() { undo.clear(); redo.clear(); savedPoints = 0 }

    private fun move(from: ArrayDeque<Snapshot>, to: ArrayDeque<Snapshot>, current: List<List<InkPoint>>): List<List<InkPoint>>? {
        if (from.isEmpty()) return null
        val currentPoints = checkedPointCount(current)
        val savedCurrent = Snapshot(immutableCopy(current), currentPoints)
        val restored = from.removeLast()
        savedPoints -= restored.points
        to.addLast(savedCurrent)
        savedPoints += currentPoints
        trim(restored.points)
        return restored.strokes
    }

    private fun trim(currentPoints: Int) {
        while (retainedEdits > MAX_EDITS || savedPoints + currentPoints > MAX_RETAINED_POINTS) {
            // Oldest undo is furthest from the current edit. Redo is trimmed only
            // if no undo remains (possible if the caller changed live ink directly).
            val removed = if (undo.isNotEmpty()) undo.removeFirst() else redo.removeFirst()
            savedPoints -= removed.points
        }
    }

    companion object {
        const val MAX_EDITS = 32
        const val MAX_RETAINED_POINTS = 96_000
        const val MAX_STROKES = 64
        const val MAX_CURRENT_POINTS = 12_000

        fun snapshot(strokes: List<List<InkPoint>>): List<List<InkPoint>> {
            checkedPointCount(strokes)
            return immutableCopy(strokes)
        }

        private fun checkedPointCount(strokes: List<List<InkPoint>>): Int {
            require(strokes.size <= MAX_STROKES) { "Too many ink strokes." }
            var count = 0
            for (stroke in strokes) {
                require(stroke.size <= MAX_CURRENT_POINTS - count) { "Too many ink points." }
                count += stroke.size
            }
            for (stroke in strokes) for (point in stroke) {
                require(point.x.isFinite() && point.y.isFinite() && abs(point.x) <= 1_000_000f && abs(point.y) <= 1_000_000f) {
                    "Invalid ink coordinates."
                }
            }
            return count
        }

        private fun immutableCopy(strokes: List<List<InkPoint>>): List<List<InkPoint>> =
            Collections.unmodifiableList(strokes.map { Collections.unmodifiableList(ArrayList(it)) })
    }
}
