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
 * **D-036.5 — cycle membership is SCC-based.**
 *
 * The B2 run measured Kahn's whole non-orderable leftover being labelled `Cycle`:
 * a bystander that merely DEPENDED on a loop was branded a member, and two
 * disjoint loops shared one merged members list — against the design's own words
 * ("Blocked points at the culprit"; "Cycle can name every entry in the loop").
 * The ruling: `Cycle` = the entry's own strongly-connected component; an entry
 * outside every loop that waits on one is `Blocked(by that member)` — rule 4
 * already says exactly that once membership is honest.
 */
class D036SccCycleSpec : StringSpec({

    fun src(text: String): EditorState = Editor.insert(EditorState(), text)

    /** A→B→A ring with the ids given, built edit-closed (D-036.1). */
    fun tapeWithRing(): Tape {
        val t = Tape()
        t.append(src("0")); t.append(src("0"))
        t.edit(EntryId(1), src("#2+1"))
        t.edit(EntryId(2), src("#1+1"))
        return t
    }

    "D-036.5: the ruling's example — A↔B cycle, C→A is Blocked(by A), not a member" {
        val t = tapeWithRing()
        t.append(src("#1*2")) // C
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
            .members shouldBe listOf(EntryId(1), EntryId(2))
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
            .members shouldBe listOf(EntryId(1), EntryId(2))
        t.entry(EntryId(3))!!.value.shouldBeInstanceOf<EntryValue.Blocked>()
            .by shouldBe EntryId(1)
    }

    "D-036.5: two disjoint loops report as SEPARATE cycles" {
        val t = Tape()
        repeat(4) { t.append(src("0")) }
        t.edit(EntryId(1), src("#2+1")); t.edit(EntryId(2), src("#1+1"))
        t.edit(EntryId(3), src("#4+1")); t.edit(EntryId(4), src("#3+1"))
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
            .members shouldBe listOf(EntryId(1), EntryId(2))
        t.entry(EntryId(4))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
            .members shouldBe listOf(EntryId(3), EntryId(4))
    }

    "D-036.5: disjoint 2-loop and 3-loop keep their own member lists" {
        val t = Tape()
        repeat(5) { t.append(src("0")) }
        t.edit(EntryId(1), src("#2+1")); t.edit(EntryId(2), src("#1+1"))
        t.edit(EntryId(3), src("#4+1")); t.edit(EntryId(4), src("#5+1")); t.edit(EntryId(5), src("#3+1"))
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
            .members shouldBe listOf(EntryId(1), EntryId(2))
        t.entry(EntryId(3))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
            .members shouldBe listOf(EntryId(3), EntryId(4), EntryId(5))
    }

    "D-036.5: D-033 intact — a self-loop is its own SCC of one" {
        val t = Tape()
        t.append(src("#1+1"))
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
            .members shouldBe listOf(EntryId(1))
    }

    "D-036.5: blocking chains off a cycle stay transitive with immediate causes" {
        val t = tapeWithRing()
        t.append(src("#1*2"))  // C → cycle member
        t.append(src("#3+1"))  // D → C
        t.entry(EntryId(3))!!.value.shouldBeInstanceOf<EntryValue.Blocked>().by shouldBe EntryId(1)
        t.entry(EntryId(4))!!.value.shouldBeInstanceOf<EntryValue.Blocked>().by shouldBe EntryId(3)
    }

    "D-036.5: a member with an extra self-loop is one SCC, not two verdicts" {
        val t = Tape()
        t.append(src("0")); t.append(src("#1+1"))
        t.edit(EntryId(1), src("#2+#1"))
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
            .members shouldBe listOf(EntryId(1), EntryId(2))
    }

    "D-036.5: breaking the loop releases the bystander through normal recompute" {
        val t = tapeWithRing()
        t.append(src("#1*2"))
        t.edit(EntryId(2), src("7"))
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
        t.entry(EntryId(3))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
    }

    "D-036.5: an entry waiting on two disjoint loops names its first-encountered blocker" {
        val t = Tape()
        repeat(4) { t.append(src("0")) }
        t.edit(EntryId(1), src("#2+1")); t.edit(EntryId(2), src("#1+1"))
        t.edit(EntryId(3), src("#4+1")); t.edit(EntryId(4), src("#3+1"))
        t.append(src("#3 + #1"))
        t.entry(EntryId(5))!!.value.shouldBeInstanceOf<EntryValue.Blocked>().by shouldBe EntryId(3)
    }
})
