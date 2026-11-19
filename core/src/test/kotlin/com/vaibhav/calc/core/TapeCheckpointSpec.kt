package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.tape.Tape
import com.vaibhav.calc.core.tape.UndoOutcome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TapeCheckpointSpec : StringSpec({
    fun source(text: String) = Editor.insert(EditorState(), text)

    "rollback restores IDs and all undo snapshots even after capacity eviction" {
        val tape = Tape()
        repeat(Tape.MAX_UNDO_TRANSACTIONS) { tape.append(source("1")) }
        val archive = tape.archive()
        val checkpoint = tape.checkpoint()
        tape.append(source("2"))
        tape.restore(checkpoint)
        tape.archive() shouldBe archive
        repeat(Tape.MAX_UNDO_TRANSACTIONS) { (tape.undo() is UndoOutcome.Undone) shouldBe true }
        tape.entries().isEmpty() shouldBe true
        tape.undo() shouldBe UndoOutcome.NoMoreUndos
    }

    "rollback restores redo and future-entry settings without recomputation" {
        val tape = Tape()
        tape.append(source("7 -> A")); tape.append(source("A+1")); tape.undo()
        val archive = tape.archive()
        val checkpoint = tape.checkpoint()
        tape.updateSettings(Settings(angle = AngleMode.RAD))
        tape.append(source("999"))
        tape.restore(checkpoint)
        tape.archive() shouldBe archive
        tape.canRedo shouldBe true
        (tape.redo() is UndoOutcome.Undone) shouldBe true
        tape.entries().last().source.toInput() shouldBe "A+1"
        tape.append(source("sin(30)"))
        tape.entries().last().settings!!.angle shouldBe AngleMode.DEG
    }

    "a checkpoint cannot replace a different tape" {
        val a = Tape(); val b = Tape()
        a.append(source("42"))
        shouldThrow<IllegalArgumentException> { b.restore(a.checkpoint()) }
        b.entries().isEmpty() shouldBe true
    }
})
