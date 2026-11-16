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
 * **D-032 — the tape transition graph, enforced.**
 *
 * Every transition below was **derived from `Tape.recompute` and measured**, never
 * copied from the design's state gallery. The design draws eight exemplar rows with
 * blurbs; it contains no transition table, so inventing one from it would have been
 * fiction (D-032).
 *
 * **The structural fact that shapes this whole spec:** `Tape.recompute` is a TOTAL
 * RE-DERIVATION, not a step function. Every transaction recomputes every entry from
 * `(source, dependencies, cycle-membership)` alone. The previous state is consulted
 * for exactly one thing — `previousOf(e)` carries the last `Computed` value into
 * `Failed`/`Blocked` as `previous`. So there are **no forbidden transitions between
 * reachable states**: any reachable state can follow any other. That is not laxity,
 * it is what "recompute from source" means, and this spec pins it by exhausting all
 * 5 × 5 ordered pairs rather than asserting a hand-drawn arrow set.
 *
 * Reachable in v1: `Computed`, `Empty`, `Failed`, `Blocked`, `Cycle`.
 * Unreachable in v1: `Stale`, `Ambiguous` — asserted below, with the reason.
 */
class TapeTransitionSpec : StringSpec({

    fun src(text: String): EditorState = Editor.insert(EditorState(), text)

    /**
     * Each entry drives entry #2 into one named state, using #1 as the lever where a
     * dependency is needed. Applying one then another IS the transition under test.
     */
    val drivers: Map<String, (Tape) -> Unit> = linkedMapOf(
        "Computed" to { t: Tape -> t.edit(EntryId(1), src("5")); t.edit(EntryId(2), src("2+2")) },
        "Empty" to { t: Tape -> t.edit(EntryId(2), src("")) },
        "Failed" to { t: Tape -> t.edit(EntryId(2), src("2+*3")) },
        "Blocked" to { t: Tape -> t.edit(EntryId(1), src("1/0")); t.edit(EntryId(2), src("#1*2")) },
        "Cycle" to { t: Tape -> t.edit(EntryId(1), src("#2+1")); t.edit(EntryId(2), src("#1*2")) },
    )

    fun nameOf(v: EntryValue): String = when (v) {
        is EntryValue.Computed -> "Computed"
        is EntryValue.Empty -> "Empty"
        is EntryValue.Failed -> "Failed"
        is EntryValue.Blocked -> "Blocked"
        is EntryValue.Cycle -> "Cycle"
        is EntryValue.Stale -> "Stale"
        is EntryValue.Ambiguous -> "Ambiguous"
    }

    fun freshTape(): Tape = Tape().also { it.append(src("1")); it.append(src("1")) }

    // --- the 25 ordered pairs, each its own assertion -----------------------
    for ((from, driveFrom) in drivers) {
        for ((to, driveTo) in drivers) {
            "D-032 transition: $from → $to" {
                val t = freshTape()
                driveFrom(t)
                nameOf(t.entry(EntryId(2))!!.value) shouldBe from
                driveTo(t)
                nameOf(t.entry(EntryId(2))!!.value) shouldBe to
            }
        }
    }

    // --- what each state carries -------------------------------------------

    "D-032: Blocked names its immediate blocker" {
        val t = freshTape()
        drivers.getValue("Blocked")(t)
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Blocked>().by shouldBe EntryId(1)
    }

    "D-032: Cycle names every member" {
        val t = freshTape()
        drivers.getValue("Cycle")(t)
        val c = t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
        c.members.toSet() shouldBe setOf(EntryId(1), EntryId(2))
    }

    // --- the three entry conditions for Blocked, each measured --------------

    "D-032: a dependency holding ROOTS blocks — Computed with no value is still no value" {
        // The subtle one D-021 called out: #1 succeeded, but roots are not a value
        // another entry can multiply, so #2 never ran.
        val t = Tape()
        t.append(src("x^2-5x+6=0"))
        t.append(src("#1*2"))
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Blocked>().by shouldBe EntryId(1)
    }

    "D-032: a dependency that is Empty blocks" {
        val t = Tape()
        t.append(src(""))
        t.append(src("#1*2"))
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Blocked>().by shouldBe EntryId(1)
    }

    "D-032: blocking is transitive and always names the IMMEDIATE cause" {
        val t = Tape()
        t.append(src("1/0")); t.append(src("#1+1")); t.append(src("#2+1"))
        t.entry(EntryId(3))!!.value.shouldBeInstanceOf<EntryValue.Blocked>().by shouldBe EntryId(2)
    }

    // --- boundaries that are NOT what a reader would assume ------------------

    "D-032: a reference to a NON-EXISTENT entry is Failed, not Blocked" {
        // #99 is filtered out of the dependency set (it is not on the tape), so the
        // name survives to the evaluator and resolves as an unknown symbol.
        val t = Tape()
        t.append(src("#99*2"))
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Failed>()
    }

    "D-033: a SELF-reference is Cycle([#n]) — a cycle of length one" {
        // Was `Failed` until D-033. A self-reference IS a cycle, just a short one,
        // and classifying it differently from the two-entry case was an arbitrary
        // boundary. `Cycle` also names the actual fault where `Failed` is generic.
        val t = Tape()
        t.append(src("#1+1"))
        val c = t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
        c.members shouldBe listOf(EntryId(1))
    }

    "D-033: a self-reference anywhere on the tape is a cycle of one, and isolates" {
        // The self-cycle must not contaminate unrelated entries, and entries that
        // merely sit next to it keep computing.
        val t = Tape()
        t.append(src("2+2"))
        t.append(src("#2*3")) // #2 refers to itself
        t.append(src("7"))
        val c = t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
        c.members shouldBe listOf(EntryId(2))
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
        t.entry(EntryId(3))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
    }

    "D-033: breaking a self-reference restores computation" {
        val t = Tape()
        t.append(src("#1+1"))
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
        t.edit(EntryId(1), src("4+1"))
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
    }

    // --- unreachable states, asserted rather than assumed -------------------

    "D-032: Stale is UNREACHABLE in v1 — the tape recomputes eagerly" {
        // Nothing in Tape ever constructs Stale. It exists for an async UI layer
        // that does not exist yet. Swept across every operation the tape exposes.
        val t = Tape()
        t.append(src("2")); t.append(src("#1*2")); t.append(src(""))
        t.edit(EntryId(1), src("1/0"))
        t.edit(EntryId(1), src("7"))
        t.edit(EntryId(2), src("2+*3"))
        t.delete(EntryId(3))
        t.undo(); t.redo()
        t.entries().none { it.value is EntryValue.Stale } shouldBe true
    }

    "D-032: Ambiguous is UNREACHABLE in v1 — RouteResult.Ambiguous has no producer" {
        // D-016.1 removed the ambiguity rather than adjudicating it: `=` is
        // equation-only and storing is `5 → x`. So `x = 5` is an equation, and the
        // one input the design's gallery shows as Ambiguous computes instead.
        val t = Tape()
        t.append(src("x = 5"))
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Computed>()

        val sweep = Tape()
        listOf("x = 5", "2+2=4", "x+y=5", "5 → A", "sin(x)=0", "x^3=8", "1/0", "")
            .forEach { sweep.append(src(it)) }
        sweep.entries().none { it.value is EntryValue.Ambiguous } shouldBe true
    }

    // --- undo/redo are not transitions; they restore whole snapshots --------

    "D-032: undo restores a whole snapshot rather than transitioning one entry" {
        val t = Tape()
        t.append(src("2")); t.append(src("#1*2"))
        t.edit(EntryId(1), src("1/0"))
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Blocked>()
        t.undo()
        // both entries revert together — the transaction is the undo unit (D-023)
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Computed>()
    }
})
