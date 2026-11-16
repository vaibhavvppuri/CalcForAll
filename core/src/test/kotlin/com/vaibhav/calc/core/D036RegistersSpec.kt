package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.tape.EntryId
import com.vaibhav.calc.core.tape.EntryValue
import com.vaibhav.calc.core.tape.Tape
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * **D-036.11 — registers are TAPE-GLOBAL.**
 *
 * Measured first (B2-V-001): `Tape.evaluate` built a fresh `Environment` per entry,
 * so `5→A` on one row left `A` unknown on the next — the registers panel and the
 * tape disagreed about what `A` means. The ruling makes a register read a
 * POSITIONAL dependency, exactly like `ans`: the reader depends on the NEAREST
 * PRIOR entry that assigns that register, recompute order respects the edge, and
 * the assigner's stored value flows in. No new cycle class is possible: register
 * edges point strictly up the tape.
 */
class D036RegistersSpec : StringSpec({

    fun src(text: String): EditorState = Editor.insert(EditorState(), text)

    "D-036.11: the ruling's example — 5→A then A+1 yields 6" {
        val t = Tape()
        t.append(src("5→A"))
        t.append(src("A+1"))
        val c = t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
        c.value shouldBe (Calculator().evaluate("6") as com.vaibhav.calc.core.error.CalcResult.Ok).value
    }

    "D-036.11: the register read is a real dependency edge" {
        val t = Tape()
        t.append(src("5→A"))
        t.append(src("A+1"))
        t.entry(EntryId(2))!!.dependsOn shouldBe setOf(EntryId(1))
    }

    "D-036.11: editing the assigner recomputes the reader, honestly named" {
        val t = Tape()
        t.append(src("5→A"))
        t.append(src("A+1"))
        val tx = t.edit(EntryId(1), src("7→A"))
        val c = t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
        c.value shouldBe (Calculator().evaluate("8") as com.vaibhav.calc.core.error.CalcResult.Ok).value
        (EntryId(2) in tx.updated) shouldBe true
    }

    "D-036.11: the NEAREST prior assigner wins, like ans" {
        val t = Tape()
        t.append(src("5→A"))
        t.append(src("9→A"))
        t.append(src("A+1"))
        t.entry(EntryId(3))!!.dependsOn shouldBe setOf(EntryId(2))
        t.entry(EntryId(3))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
            .value shouldBe (Calculator().evaluate("10") as com.vaibhav.calc.core.error.CalcResult.Ok).value
    }

    "D-036.11: a failed assigner blocks the reader — waiting, not failed" {
        val t = Tape()
        t.append(src("1/0→A"))
        t.append(src("A+1"))
        val b = t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Blocked>()
        b.by shouldBe EntryId(1)
        b.description shouldBe "waiting on #1"
    }

    "D-036.11: no prior assigner — the read is an honest unknown-name failure" {
        val t = Tape()
        t.append(src("A+1"))
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Failed>()
    }

    "D-036.11: deleting the assigner returns the reader to unknown" {
        val t = Tape()
        t.append(src("5→A"))
        t.append(src("A+1"))
        t.delete(EntryId(1))
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Failed>()
    }

    "D-036.11: undo restores the register flow exactly" {
        val t = Tape()
        t.append(src("5→A"))
        t.append(src("A+1"))
        t.edit(EntryId(1), src("7→A"))
        t.undo()
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
            .value shouldBe (Calculator().evaluate("6") as com.vaibhav.calc.core.error.CalcResult.Ok).value
    }

    "D-036.11: A+1→A reads the PRIOR assignment and stores the successor" {
        val t = Tape()
        t.append(src("5→A"))
        t.append(src("A+1→A"))
        t.entry(EntryId(2))!!.dependsOn shouldBe setOf(EntryId(1))
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
            .value shouldBe (Calculator().evaluate("6") as com.vaibhav.calc.core.error.CalcResult.Ok).value
    }

    "D-036.11: unknowns stay refused — 5→x is still D-016.4's typed error" {
        val t = Tape()
        t.append(src("5→x"))
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Failed>()
    }
})
