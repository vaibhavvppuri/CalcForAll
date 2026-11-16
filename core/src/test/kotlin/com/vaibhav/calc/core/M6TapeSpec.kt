package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.tape.EntryId
import com.vaibhav.calc.core.tape.EntryValue
import com.vaibhav.calc.core.tape.Tape
import com.vaibhav.calc.core.tape.UndoOutcome
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** M6 — the history tape (12.1): live recompute, Article 11 states, transactional undo. */
class M6TapeSpec : StringSpec({

    fun src(text: String): EditorState = Editor.insert(EditorState(), text)
    fun Tape.numAt(n: Int): Num =
        ((entry(EntryId(n))!!.value as EntryValue.Computed).value as RealValue).num

    fun Tape.stateAt(n: Int): EntryValue = entry(EntryId(n))!!.value

    // --- live recompute --------------------------------------------------------
    "editing an entry recomputes its dependents" {
        val t = Tape()
        t.append(src("2"))
        t.append(src("#1*2"))
        t.numAt(1).compareTo(Num.of(2)) shouldBe 0
        t.numAt(2).compareTo(Num.of(4)) shouldBe 0
        t.edit(EntryId(1), src("5"))
        t.numAt(2).compareTo(Num.of(10)) shouldBe 0
    }
    "ans reads the entry above it" {
        val t = Tape()
        t.append(src("2+3"))
        t.append(src("ans*2"))
        t.numAt(2).compareTo(Num.of(10)) shouldBe 0
    }

    // --- ADDITION 1: Blocked ≠ Failed -----------------------------------------
    "a broken entry FAILS; the entry depending on it is BLOCKED, and names the culprit" {
        val t = Tape()
        t.append(src("1/0")) // #1: it ran and it broke
        t.append(src("#1*2")) // #2: never ran — its dependency is broken
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Failed>()
        val blocked = t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Blocked>()
        blocked.by shouldBe EntryId(1) // points at #3, greys #5 — doesn't redden both
    }
    "blocking is transitive but always names the immediate cause" {
        val t = Tape()
        t.append(src("1/0")) // #1 Failed
        t.append(src("#1+1")) // #2 Blocked by #1
        t.append(src("#2+1")) // #3 Blocked by #2
        (t.entry(EntryId(3))!!.value as EntryValue.Blocked).by shouldBe EntryId(2)
    }
    "a blocked entry recovers when its dependency is fixed — it was waiting, not broken" {
        val t = Tape()
        t.append(src("1/0"))
        t.append(src("#1*2"))
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Blocked>()
        t.edit(EntryId(1), src("4"))
        t.numAt(2).compareTo(Num.of(8)) shouldBe 0
    }
    "an empty entry is Empty — not failed, not blocked" {
        val t = Tape()
        t.append(EditorState())
        t.entry(EntryId(1))!!.value shouldBe EntryValue.Empty
    }

    // --- ADDITION 2: cycles are a state, not a failure -------------------------
    "a reference cycle is its own state and names its members" {
        val t = Tape()
        t.append(src("1"))
        t.append(src("2"))
        t.edit(EntryId(1), src("#2+1"))
        t.edit(EntryId(2), src("#1*2")) // now neither can be ordered
        val c1 = t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
        c1.members.toSet() shouldBe setOf(EntryId(1), EntryId(2))
    }
    "entries outside the cycle still compute" {
        val t = Tape()
        t.append(src("1"))
        t.append(src("2"))
        t.append(src("7")) // #3 is independent
        t.edit(EntryId(1), src("#2+1"))
        t.edit(EntryId(2), src("#1*2"))
        t.numAt(3).compareTo(Num.of(7)) shouldBe 0
    }
    "breaking the cycle restores computation" {
        val t = Tape()
        t.append(src("1"))
        t.append(src("2"))
        t.edit(EntryId(1), src("#2+1"))
        t.edit(EntryId(2), src("#1*2"))
        t.edit(EntryId(2), src("10")) // cycle broken
        t.numAt(2).compareTo(Num.of(10)) shouldBe 0
        t.numAt(1).compareTo(Num.of(11)) shouldBe 0
    }

    // --- ADDITION 3: the undo unit is the transaction --------------------------
    "undo after a cascade restores EVERY dependent, not just the edited entry" {
        val t = Tape()
        t.append(src("2")) // #1 = 2
        t.append(src("#1*2")) // #2 = 4
        t.append(src("#2+1")) // #3 = 5
        val tx = t.edit(EntryId(1), src("3")) // #1=3 → #2=6 → #3=7
        tx.updatedCount shouldBe 3 // the snackbar's "3 results updated"
        t.numAt(1).compareTo(Num.of(3)) shouldBe 0
        t.numAt(2).compareTo(Num.of(6)) shouldBe 0
        t.numAt(3).compareTo(Num.of(7)) shouldBe 0

        t.undo().shouldBeInstanceOf<UndoOutcome.Undone>()
        // All four restored or none. A tape showing #2 = 6 derived from a #1 that is
        // 2 again would be corruption that looks fine.
        t.numAt(1).compareTo(Num.of(2)) shouldBe 0
        t.numAt(2).compareTo(Num.of(4)) shouldBe 0
        t.numAt(3).compareTo(Num.of(5)) shouldBe 0
    }
    "redo re-applies the whole transaction" {
        val t = Tape()
        t.append(src("2"))
        t.append(src("#1*2"))
        t.edit(EntryId(1), src("3"))
        t.undo()
        t.redo().shouldBeInstanceOf<UndoOutcome.Undone>()
        t.numAt(1).compareTo(Num.of(3)) shouldBe 0
        t.numAt(2).compareTo(Num.of(6)) shouldBe 0
    }
    "undoing past the start is NoMoreUndos — a state, not a failure" {
        val t = Tape()
        t.undo() shouldBe UndoOutcome.NoMoreUndos
        t.append(src("1"))
        t.undo().shouldBeInstanceOf<UndoOutcome.Undone>()
        t.undo() shouldBe UndoOutcome.NoMoreUndos
    }
})
