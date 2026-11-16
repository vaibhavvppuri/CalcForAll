package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.tape.EntryId
import com.vaibhav.calc.core.tape.EntryValue
import com.vaibhav.calc.core.tape.Tape
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * **D-036.1 — the commit-time reference guard.**
 *
 * BRAIN-RUN-REPORT F-1 (CRITICAL) proved the REBOUND REFERENCE: `#3+1` committed
 * while the tape had one entry was honestly `Failed` — until two later appends made
 * a brand-new, unrelated entry `#3`, at which point the old formula silently
 * computed against a stranger's value (B2-R-025/026, measured values 21 and 100).
 *
 * The ruling: a reference to an entry that does not exist AT COMMIT TIME is a
 * typed error that NAMES the missing id, and it is **sticky** — the set of dead
 * ids is captured on the entry, so a later entry that happens to receive the
 * number can never be adopted. An EDIT is a fresh commit and re-captures.
 */
class D036CommitGuardSpec : StringSpec({

    fun src(text: String): EditorState = Editor.insert(EditorState(), text)

    "D-036.1: committing a reference to a nonexistent entry is a typed error naming the id" {
        val t = Tape()
        t.append(src("#3+1"))
        val f = t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Failed>()
        f.error.shouldBeInstanceOf<EvalError.MissingEntry>()
        f.error.kind shouldBe EvalError.Kind.UNKNOWN_SYMBOL
        f.error.message shouldContain "#3"
    }

    "D-036.1: the rebound path is unreachable — a stranger wearing the id is never adopted" {
        val t = Tape()
        t.append(src("#3+1"))
        t.append(src("10"))
        t.append(src("20")) // a brand-new entry now wears #3
        val f = t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Failed>()
        f.error.message shouldContain "#3"
        // and the arrival of the stranger did not even count as a change to #1
        t.entry(EntryId(3))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
    }

    "D-036.1: a reference to a LATER-created id still cannot bind" {
        val t = Tape()
        t.append(src("#2+1"))
        t.append(src("5")) // #2 exists now
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Failed>()
            .error.shouldBeInstanceOf<EvalError.MissingEntry>()
    }

    "D-036.1: an EDIT is a fresh commit — re-referencing once the target exists computes" {
        val t = Tape()
        t.append(src("#3+1"))
        t.append(src("10"))
        t.append(src("20"))
        t.edit(EntryId(1), src("#3+1")) // same text, but NOW #3 exists at commit
        val c = t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
        // #3 holds 20, so the re-committed reference computes 20+1
        c.value shouldBe (Calculator().evaluate("21") as com.vaibhav.calc.core.error.CalcResult.Ok).value
    }

    "D-036.1: a ref that existed at commit and is later deleted is Failed but was never dead" {
        val t = Tape()
        t.append(src("5"))
        t.append(src("#1*2"))
        t.delete(EntryId(1))
        val f = t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Failed>()
        // the plain unknown-name path, not the commit guard: the commit was honest
        (f.error is EvalError.MissingEntry) shouldBe false
    }

    "D-036.1: D-033 intact — a self-reference at commit sees its own id and stays Cycle" {
        val t = Tape()
        t.append(src("#1+1"))
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
            .members shouldBe listOf(EntryId(1))
    }

    "D-036.1: undo and redo restore commit-time verdicts exactly" {
        val t = Tape()
        t.append(src("#3+1"))
        t.append(src("10"))
        t.append(src("20"))
        t.undo(); t.undo()
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Failed>()
        t.redo(); t.redo()
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Failed>()
            .error.shouldBeInstanceOf<EvalError.MissingEntry>()
    }

    "D-036.1: an edit that itself commits a dead ref is dead from the edit" {
        val t = Tape()
        t.append(src("1"))
        t.edit(EntryId(1), src("#2+1")) // #2 does not exist at this commit
        t.append(src("9")) // #2 exists now
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Failed>()
            .error.shouldBeInstanceOf<EvalError.MissingEntry>()
    }
})
