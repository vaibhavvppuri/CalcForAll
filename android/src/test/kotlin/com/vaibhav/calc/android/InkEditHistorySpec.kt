package com.vaibhav.calc.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InkEditHistorySpec : StringSpec({
    fun ink(x: Int) = listOf(listOf(InkPoint(x.toFloat(), 0f), InkPoint(x.toFloat(), 10f)))

    "draw erase and clear each undo and redo as complete edits" {
        val history = InkEditHistory()
        val one = ink(1); val two = one + ink(2); val erased = ink(2)
        history.record(emptyList(), one) shouldBe true
        history.record(one, two) shouldBe true
        history.record(two, erased) shouldBe true
        history.record(erased, emptyList()) shouldBe true
        var current = emptyList<List<InkPoint>>()
        for (expected in listOf(erased, two, one, emptyList())) {
            current = history.undo(current)!!
            current shouldBe expected
        }
        history.undo(current) shouldBe null
        for (expected in listOf(one, two, erased, emptyList())) {
            current = history.redo(current)!!
            current shouldBe expected
        }
        history.redo(current) shouldBe null
    }

    "a new edit after undo discards only the abandoned redo branch" {
        val history = InkEditHistory()
        history.record(emptyList(), ink(1)); history.record(ink(1), ink(2))
        history.undo(ink(2)) shouldBe ink(1)
        history.record(ink(1), ink(3)) shouldBe true
        history.canRedo shouldBe false
        history.undo(ink(3)) shouldBe ink(1)
        history.undo(ink(1)) shouldBe emptyList()
    }

    "no-op edits preserve a redo branch and do not consume a history slot" {
        val history = InkEditHistory()
        history.record(emptyList(), ink(1))
        history.undo(ink(1)) shouldBe emptyList()
        val count = history.retainedEdits
        history.record(emptyList(), emptyList()) shouldBe false
        history.retainedEdits shouldBe count
        history.canRedo shouldBe true
        history.redo(emptyList()) shouldBe ink(1)
    }

    "only the most recent thirty-two edits are retained" {
        val history = InkEditHistory()
        var current = emptyList<List<InkPoint>>()
        for (i in 1..40) { val next = ink(i); history.record(current, next); current = next }
        history.retainedEdits shouldBe 32
        repeat(32) { current = history.undo(current)!! }
        current shouldBe ink(8)
        history.canUndo shouldBe false
        repeat(32) { current = history.redo(current)!! }
        current shouldBe ink(40)
    }

    "point budget includes live ink and stays bounded through undo and redo" {
        val history = InkEditHistory()
        var current = emptyList<List<InkPoint>>()
        fun checkBudget() {
            (history.retainedPoints + current.sumOf { it.size } <= InkEditHistory.MAX_RETAINED_POINTS) shouldBe true
            (history.retainedEdits <= InkEditHistory.MAX_EDITS) shouldBe true
        }
        for (i in 1..20) {
            val next = listOf(List(12_000) { InkPoint(it.toFloat(), i.toFloat()) })
            history.record(current, next); current = next; checkBudget()
        }
        var undone = 0
        while (history.canUndo) { current = history.undo(current)!!; undone++; checkBudget() }
        undone shouldBe 7
        while (history.canRedo) { current = history.redo(current)!!; checkBudget() }
        current.first().first().y shouldBe 20f
    }

    "snapshots defensively copy both list levels and cannot be mutated through returned lists" {
        val history = InkEditHistory()
        val beforeStroke = mutableListOf(InkPoint(1f, 2f))
        val before = mutableListOf<List<InkPoint>>(beforeStroke)
        val afterStroke = mutableListOf(InkPoint(3f, 4f))
        val after = mutableListOf<List<InkPoint>>(afterStroke)
        history.record(before, after)
        beforeStroke.clear(); before.clear()
        val restored = history.undo(after)!!
        restored shouldBe listOf(listOf(InkPoint(1f, 2f)))
        afterStroke.clear(); after.clear()
        history.redo(restored) shouldBe listOf(listOf(InkPoint(3f, 4f)))
        shouldThrow<UnsupportedOperationException> { (restored as MutableList<List<InkPoint>>).clear() }
        shouldThrow<UnsupportedOperationException> { (restored.first() as MutableList<InkPoint>).clear() }
    }

    "oversized lists are rejected before reading points or changing history" {
        val history = InkEditHistory()
        history.record(emptyList(), ink(1)); history.undo(ink(1))
        val tooManyStrokes = object : AbstractList<List<InkPoint>>() {
            override val size = 65
            override fun get(index: Int): List<InkPoint> = error("Must reject before reading strokes")
        }
        val tooManyPoints = object : AbstractList<InkPoint>() {
            override val size = 12_001
            override fun get(index: Int): InkPoint = error("Must reject before reading points")
        }
        shouldThrow<IllegalArgumentException> { history.record(emptyList(), tooManyStrokes) }
        shouldThrow<IllegalArgumentException> { history.record(emptyList(), listOf(tooManyPoints)) }
        history.redo(emptyList()) shouldBe ink(1)
    }

    "invalid geometry is refused without destroying redo" {
        val history = InkEditHistory()
        history.record(emptyList(), ink(1)); history.undo(ink(1))
        for (point in listOf(InkPoint(Float.NaN, 0f), InkPoint(0f, Float.POSITIVE_INFINITY), InkPoint(1_000_001f, 0f))) {
            shouldThrow<IllegalArgumentException> { history.record(emptyList(), listOf(listOf(point))) }
        }
        history.redo(emptyList()) shouldBe ink(1)
    }

    "workspace records a live eraser transaction once and publishes undo redo availability" {
        val workspace = InkWorkspace()
        val two = ink(1) + ink(2)
        workspace.commitInk(two) shouldBe true
        workspace.canUndoInk shouldBe true
        workspace.recognizedStrokes = two; workspace.recognizedMode = workspace.mode
        workspace.strokes = ink(2) // Root gesture code applies live erasing before committing.
        workspace.recordInkEdit(two) shouldBe true
        workspace.recognizedStrokes shouldBe null
        workspace.undoInk() shouldBe true
        workspace.strokes shouldBe two
        workspace.canRedoInk shouldBe true
        workspace.commitInk(two) shouldBe false
        workspace.canRedoInk shouldBe true
        workspace.redoInk() shouldBe true
        workspace.strokes shouldBe ink(2)
        workspace.resetInkHistory()
        workspace.canUndoInk shouldBe false
        workspace.canRedoInk shouldBe false
        workspace.strokes shouldBe ink(2)
    }

    "clearing a workspace releases its snapshots and corrected draft" {
        val workspace = InkWorkspace()
        workspace.commitInk(ink(1)); workspace.commitInk(emptyList()); workspace.undoInk()
        workspace.expression = "1+1"; workspace.paperWidth = 200f; workspace.paperHeight = 100f
        workspace.clear()
        workspace.strokes shouldBe emptyList()
        workspace.expression shouldBe ""
        workspace.canUndoInk shouldBe false
        workspace.canRedoInk shouldBe false
        workspace.paperWidth shouldBe 0f
        workspace.paperHeight shouldBe 0f
    }
})
