package com.vaibhav.calc.battery

import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.entry.Row
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.tape.EntryValue
import com.vaibhav.calc.core.tape.Registers
import com.vaibhav.calc.core.tape.StoreResult
import com.vaibhav.calc.core.tape.UndoOutcome
import kotlin.random.Random

/**
 * The B2 case generator. 500 cases, twelve axes. Near-duplicate purge rule:
 * duplicates iff same (event-sequence shape, start-state class, graph shape,
 * oracle) 4-tuple; size/length variants kept only where size is the axis.
 */
internal object B2Cases {

    private fun a(s: String) = B2Ev.Append(s)
    private fun e(ord: Int, s: String) = B2Ev.Edit(ord, s)
    private fun d(ord: Int) = B2Ev.Delete(ord)
    private val U = B2Ev.Undo
    private val RD = B2Ev.Redo

    /** A 2D source built through the editor — the same door keystrokes take. */
    private fun frac(num: String, den: String): EditorState {
        var s = Editor.insert(EditorState(), num)
        s = Editor.typeFraction(s)
        return Editor.insert(s, den)
    }

    private fun fn(name: String, arg: String): EditorState =
        Editor.insert(Editor.typeFunc(EditorState(), name), arg)

    /** A deep COPY of another source's nodes — the row-body tap (no reference). */
    private fun copyOf(src: EditorState, suffix: String = ""): EditorState {
        val pasted = com.vaibhav.calc.core.entry.Clipboard.paste(EditorState(), com.vaibhav.calc.core.entry.Clip(src.root.nodes))
        return if (suffix.isEmpty()) pasted else Editor.insert(pasted, suffix)
    }

    // --- AXIS K · CAP — 16 ---------------------------------------------------

    private fun axisK(): List<B2Case> {
        val out = mutableListOf<B2Case>()
        var n = 0
        fun id() = "B2-K-%03d".format(++n)

        out += B2Run.case(id(), "K", (1..50).map { a("1") }, note = "no engine cap: 50 appends retained", classEvery = false,
            oracles = { h -> listOf(if (h.tape.entries().size == 50) null else "size=${h.tape.entries().size}, expected 50 (the slice(-6) cap is the DEMO's, not the engine's)") })
        out += B2Run.case(id(), "K", (1..200).map { a("1") }, note = "no engine cap: 200 appends retained", classEvery = false,
            oracles = { h -> listOf(if (h.tape.entries().size == 200) null else "size=${h.tape.entries().size}, expected 200") })
        out += B2Run.case(id(), "K", listOf(a("1")) + (1..48).map { a("2") } + a("#1+41"), note = "#1 referenced from row 50 — far outside any 6-row window", classEvery = false,
            oracles = { h -> listOf(h.expectScalar(50, "42")) })
        out += B2Run.case(id(), "K", listOf(a("5"), a("#1*2"), d(1)), note = "delete is the only eviction; dependent goes Failed",
            oracles = { h -> listOf(h.expectCls(2, "Failed")) })
        out += B2Run.case(id(), "K", listOf(a("5"), d(1), a("7")), note = "id never re-issued after delete",
            oracles = { h -> listOf(if (h.entry(1) == null && h.entry(2) != null) null else "expected #1 gone, #2 issued") })
        out += B2Run.case(id(), "K", listOf(a("5"), a("#1*2"), d(1), U), note = "undo of the eviction restores the dependent",
            oracles = { h -> listOf(h.expectScalar(2, "10")) })
        out += B2Run.case(id(), "K", listOf(a("1"), a("2"), a("ans+10"), d(2)), note = "deleting a middle row rebinds ans below — honestly",
            oracles = { h -> listOf(h.expectScalar(3, "11"), h.expectUpdated(setOf(3))) })
        out += B2Run.case(id(), "K", listOf(a("1"), a("2"), d(2)), note = "delete last row",
            oracles = { h -> listOf(if (h.tape.entries().size == 1) null else "size != 1", h.expectScalar(1, "1")) })
        out += B2Run.case(id(), "K", listOf(a("1"), d(99)), note = "delete of a non-existent id is a no-op transaction",
            oracles = { h -> listOf(h.expectUpdated(emptySet()), if (h.tape.entries().size == 1) null else "size changed") })
        out += B2Run.case(id(), "K", listOf(a("1"), d(1), d(1)), note = "double delete: second is a no-op",
            oracles = { h -> listOf(h.expectUpdated(emptySet())) })
        out += B2Run.case(id(), "K", listOf(a("1"), d(1), e(1, "9")), note = "edit of a deleted id is a no-op",
            oracles = { h -> listOf(h.expectUpdated(emptySet()), if (h.tape.entries().isEmpty()) null else "tape not empty") })
        out += B2Run.case(id(), "K", (1..8).map { a(it.toString()) } + a("ans+1"), note = "ans binds to the row above regardless of any UI window",
            oracles = { h -> listOf(h.expectScalar(9, "9")) })
        out += B2Run.case(id(), "K", (1..50).map { a("1") } + d(1), note = "delete #1 of 50 standalone rows", classEvery = false,
            oracles = { h -> listOf(if (h.tape.entries().size == 49) null else "size != 49") })
        out += B2Run.custom(id(), "K", "FAILABLE", "D-036.1: a reference typed before its target exists is dead at commit and never recovers by append") {
            val h = B2Harness()
            h.run(listOf(a("#3+1")))
            val s1 = h.expectCls(1, "Failed")
            h.run(listOf(a("1"), a("20"))) // #3 exists now — must change nothing
            val s2 = h.expectCls(1, "Failed")
            h.run(listOf(e(1, "#3+1"))) // an EDIT is a fresh commit — this recovers
            val s3 = h.expectScalar(1, "21")
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(s1, s2, s3) }
        }
        out += B2Run.case(id(), "K", (1..200).map { a("1") } + d(1), note = "delete first of 200", classEvery = false,
            oracles = { h -> listOf(if (h.tape.entries().size == 199) null else "size != 199") })
        out += B2Run.case(id(), "K", listOf(a("1")) + (1..99).map { a("2") } + a("#1+99"), note = "reference across a 100-row gap", classEvery = false,
            oracles = { h -> listOf(h.expectScalar(101, "100")) })
        return out
    }

    // --- AXIS T · TRANSITIONS — 95 -------------------------------------------

    private val fromSrc = linkedMapOf(
        "Computed" to "2+2", "Empty" to "", "Failed" to "5/0", "Blocked" to "#1+1", "Cycle" to "#2+1",
    )
    private val toSrc = linkedMapOf(
        "Computed" to "7", "Empty" to "", "Failed" to "1/0", "Blocked" to "#1+1", "Cycle" to "#2+1",
    )

    private fun axisT(): List<B2Case> {
        val out = mutableListOf<B2Case>()
        var n = 0
        fun id() = "B2-T-%03d".format(++n)

        // T-25: the D-032 pair matrix, re-measured. Staged: FROM verified, then TO.
        for ((from, fs) in fromSrc) for ((to, ts) in toSrc) {
            out += B2Run.custom(id(), "T", "FAILABLE", "pair $from → $to", listOf(a("1/0"), a(fs), e(2, ts))) {
                val h = B2Harness()
                h.run(listOf(a("1/0"), a(fs)))
                val pre = h.expectCls(2, from)
                h.run(listOf(e(2, ts)))
                B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(pre?.let { "FROM not established: $it" }, h.expectCls(2, to)) }
            }
        }

        // T-EXT: 5 start states × 9 events D-032 never covered.
        data class Ext(val name: String, val evs: List<B2Ev>, val expect: (String, B2Harness) -> String?)
        val exts = listOf(
            Ext("delete-a-dep", listOf(d(1))) { s, h ->
                h.expectCls(2, when (s) { "Blocked" -> "Failed"; else -> s })
            },
            Ext("delete-self", listOf(d(2))) { _, h ->
                if (h.entry(2) == null && h.entry(1) != null) null else "expected #2 gone, #1 kept"
            },
            Ext("badge-ref-append", listOf(a("#2+1"))) { s, h ->
                when (s) {
                    "Computed" -> h.expectScalar(3, "5")
                    // D-036.5: a dependent of a cycle is Blocked by the member, never a member
                    else -> h.expectBlockedBy(3, 2)
                }
            },
            Ext("dep-edit-good", listOf(e(1, "7"))) { s, h ->
                when (s) { "Blocked" -> h.expectScalar(2, "8"); else -> h.expectCls(2, s) }
            },
            Ext("dep-edit-bad", listOf(e(1, "2/0"))) { s, h ->
                h.expectCls(2, s) // Blocked stays Blocked; the rest never referenced #1
            },
            Ext("dep-edit-blank", listOf(e(1, ""))) { s, h ->
                h.expectCls(2, s) // an Empty dependency still hands no value
            },
            Ext("edit-forms-mutual-cycle", listOf(a("#2+1"), e(2, "#3+1"))) { _, h ->
                h.expectCls(2, "Cycle") ?: h.expectCls(3, "Cycle")
            },
            Ext("undo-restores-start", listOf(e(2, "9"), U)) { s, h -> h.expectCls(2, s) },
            Ext("redo-reapplies", listOf(e(2, "9"), U, RD)) { _, h -> h.expectScalar(2, "9") },
        )
        for ((sName, fs) in fromSrc) for (ext in exts) {
            out += B2Run.custom(
                id(), "T", "FAILABLE",
                "$sName × ${ext.name}",
                listOf(a("1/0"), a(fs)) + ext.evs,
            ) {
                val h = B2Harness()
                h.run(listOf(a("1/0"), a(fs)))
                val pre = h.expectCls(2, sName)
                h.run(ext.evs)
                B2Run.finish(h, "FAILABLE", "MAJOR") {
                    listOf(pre?.let { "FROM not established: $it" }, ext.expect(sName, h))
                }
            }
        }

        // T-SELF: edit-self from each state, with a DEPENDENT below — the ripple is the oracle.
        val selfTargets = linkedMapOf(
            "good" to "7", "bad" to "1/0", "blank" to "", "cycle-self" to "#2+1", "cycle-mutual" to "#3+1",
        )
        for ((sName, fs) in fromSrc) for ((tName, ts) in selfTargets) {
            out += B2Run.custom(id(), "T", "FAILABLE", "self-edit $sName → $tName, ripple to dependent",
                listOf(a("1/0"), a(fs), a("#2*2"), e(2, ts))) {
                val h = B2Harness()
                h.run(listOf(a("1/0"), a(fs), a("#2*2")))
                val pre = h.expectCls(2, sName)
                h.run(listOf(e(2, ts)))
                val pair: List<String?> = when (tName) {
                    "good" -> listOf(h.expectScalar(2, "7"), h.expectScalar(3, "14"))
                    "bad" -> listOf(h.expectCls(2, "Failed"), h.expectBlockedBy(3, 2))
                    "blank" -> listOf(h.expectCls(2, "Empty"), h.expectBlockedBy(3, 2))
                    // D-036.5: the self-loop is its own SCC; the dependent is Blocked, not branded
                    "cycle-self" -> listOf(h.expectCycleMembers(2, listOf(2)), h.expectBlockedBy(3, 2))
                    "cycle-mutual" -> listOf(h.expectCycleMembers(2, listOf(2, 3)), h.expectCycleMembers(3, listOf(2, 3)))
                    else -> emptyList()
                }
                B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(pre?.let { "FROM not established: $it" }) + pair }
            }
        }
        return out
    }

    // --- AXIS D · DEPENDENCIES — 69 ------------------------------------------

    private fun chain(n: Int): List<B2Ev> = listOf(a("1")) + (2..n).map { a("#${it - 1}+1") }
    private fun fan(n: Int): List<B2Ev> = listOf(a("7")) + (1..n).map { a("#1+$it") }

    private fun axisD(): List<B2Case> {
        val out = mutableListOf<B2Case>()
        var n = 0
        fun id() = "B2-D-%03d".format(++n)

        for (len in listOf(2, 5, 10, 25, 50, 100)) {
            val big = len >= 50
            out += B2Run.case(id(), "D", chain(len), note = "chain $len build: #k = k", classEvery = !big,
                oracles = { h -> listOf(h.expectScalar(1, "1"), h.expectScalar(len / 2 + 1, "${len / 2 + 1}"), h.expectScalar(len, "$len")) })
            out += B2Run.case(id(), "D", chain(len) + e(1, "11"), note = "chain $len cascade: #k = k+10", classEvery = !big,
                oracles = { h -> listOf(h.expectScalar(len, "${len + 10}"), h.expectScalar(2.coerceAtMost(len), "12")) })
            out += B2Run.case(id(), "D", chain(len) + e(1, "11") + U, note = "chain $len cascade undone exactly", classEvery = !big,
                oracles = { h -> listOf(h.expectScalar(1, "1"), h.expectScalar(len, "$len")) })
            out += B2Run.case(id(), "D", chain(len) + e(1, "11"), note = "chain $len updated set is the whole chain", classEvery = !big,
                oracles = { h -> listOf(h.expectUpdated((1..len).toSet())) })
        }
        for (width in listOf(2, 5, 10, 25, 50)) {
            val big = width >= 50
            out += B2Run.case(id(), "D", fan(width), note = "fan $width build: leaf i = 7+i", classEvery = !big,
                oracles = { h -> listOf(h.expectScalar(2, "8"), h.expectScalar(width + 1, "${7 + width}")) })
            out += B2Run.case(id(), "D", fan(width) + e(1, "10"), note = "fan $width cascade", classEvery = !big,
                oracles = { h -> listOf(h.expectScalar(2, "11"), h.expectScalar(width + 1, "${10 + width}")) })
            out += B2Run.case(id(), "D", fan(width) + e(1, "10") + U, note = "fan $width undone", classEvery = !big,
                oracles = { h -> listOf(h.expectScalar(width + 1, "${7 + width}")) })
            out += B2Run.case(id(), "D", fan(width) + e(1, "10"), note = "fan $width updated set", classEvery = !big,
                oracles = { h -> listOf(h.expectUpdated((1..width + 1).toSet())) })
        }
        val diamond = listOf(a("1"), a("#1+1"), a("#1+2"), a("#2+#3"))
        out += B2Run.case(id(), "D", diamond, note = "diamond build", oracles = { h -> listOf(h.expectScalar(4, "5")) })
        out += B2Run.case(id(), "D", diamond + e(1, "2"), note = "diamond cascade", oracles = { h -> listOf(h.expectScalar(4, "7"), h.expectUpdated(setOf(1, 2, 3, 4))) })
        val doubleDiamond = diamond + a("#4+#1")
        out += B2Run.case(id(), "D", doubleDiamond, note = "double diamond build", oracles = { h -> listOf(h.expectScalar(5, "6")) })
        out += B2Run.case(id(), "D", doubleDiamond + e(1, "2"), note = "double diamond cascade", oracles = { h -> listOf(h.expectScalar(5, "9")) })
        val fanIn = (1..5).map { a("$it") } + a("#1+#2+#3+#4+#5")
        out += B2Run.case(id(), "D", fanIn, note = "fan-in of 5", oracles = { h -> listOf(h.expectScalar(6, "15")) })
        out += B2Run.case(id(), "D", fanIn + e(3, "30"), note = "fan-in cascade via one leg", oracles = { h -> listOf(h.expectScalar(6, "42"), h.expectUpdated(setOf(3, 6))) })
        val mixed = listOf(a("1"), a("#1+1"), a("#2+1"), a("#3+#1"), a("#4+#2"))
        out += B2Run.case(id(), "D", mixed, note = "mixed chain+fan build", oracles = { h -> listOf(h.expectScalar(5, "6")) })
        out += B2Run.case(id(), "D", mixed + e(1, "2"), note = "mixed cascade: #5 = (#3+#1)+(#2) = 6+3", oracles = { h -> listOf(h.expectScalar(5, "9")) })

        for (len in listOf(2, 5, 10)) {
            val evs = listOf(a("1")) + (2..len).map { a("ans+1") }
            out += B2Run.case(id(), "D", evs, note = "ans-chain $len build", oracles = { h -> listOf(h.expectScalar(len, "$len")) })
            out += B2Run.case(id(), "D", evs + e(1, "11"), note = "ans-chain $len cascade", oracles = { h -> listOf(h.expectScalar(len, "${len + 10}")) })
            out += B2Run.case(id(), "D", evs + e(1, "11") + U, note = "ans-chain $len undone", oracles = { h -> listOf(h.expectScalar(len, "$len")) })
        }

        out += B2Run.custom(id(), "D", "FAILABLE", "D-036.1: forward reference is dead at commit — the target's arrival changes nothing") {
            val h = B2Harness()
            h.run(listOf(a("#2+1")))
            val s1 = h.expectCls(1, "Failed")
            h.run(listOf(a("5")))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(s1, h.expectCls(1, "Failed"), h.expectScalar(2, "5")) }
        }
        out += B2Run.custom(id(), "D", "FAILABLE", "D-036.1: forward reference two ahead — dead, sticky") {
            val h = B2Harness()
            h.run(listOf(a("#3+1"), a("1")))
            val s1 = h.expectCls(1, "Failed")
            h.run(listOf(a("9")))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(s1, h.expectCls(1, "Failed")) }
        }
        out += B2Run.case(id(), "D", listOf(a("#2+1"), a("#1+1")), note = "D-036.1: mutual FORWARD appends are dead+blocked, not a cycle — rings need edits now",
            oracles = { h -> listOf(h.expectCls(1, "Failed"), h.expectBlockedBy(2, 1)) })
        out += B2Run.case(id(), "D", listOf(a("5"), a("#1+1"), d(2), a("#2+1")), note = "reference to a deleted mid id stays dead",
            oracles = { h -> listOf(h.expectCls(3, "Failed")) })

        out += B2Run.case(id(), "D", listOf(a("x^2-5x+6=0"), a("#1*2")), note = "solve outcome hands no value — dependent Blocked (D-021)",
            oracles = { h -> listOf(h.expectCls(1, "Computed"), h.expectBlockedBy(2, 1)) })
        out += B2Run.case(id(), "D", listOf(a("5→A"), a("#1*2")), note = "Stored outcome HAS a value — dependent computes",
            oracles = { h -> listOf(h.expectScalar(2, "10")) })
        out += B2Run.case(id(), "D", listOf(a("x=5"), a("#1+1")), cls = "PROBE", note = "equation-with-unknown as a dependency (outcome kind measured)")
        out += B2Run.case(id(), "D", listOf(a("2+2"), a("#1=4")), cls = "PROBE", note = "verdict equation referencing the tape (D-016.2)")
        return out
    }

    // --- AXIS C · CYCLES — 49 ------------------------------------------------

    /**
     * D-036.1 moved this builder: a forward APPEND (`#2+1` before #2 exists) is now a
     * commit-time dead ref, so rings are built the way a real user must build them —
     * placeholders first, then edits that close the loop against existing targets.
     */
    private fun ring(len: Int): List<B2Ev> =
        (1..len).map { a("0") } + (1..len).map { k -> e(k, if (k < len) "#${k + 1}+1" else "#1+1") }

    private fun axisC(): List<B2Case> {
        val out = mutableListOf<B2Case>()
        var n = 0
        fun id() = "B2-C-%03d".format(++n)

        for (len in 2..6) {
            out += B2Run.case(id(), "C", ring(len), note = "ring $len: every member Cycle",
                oracles = { h -> (1..len).map { h.expectCls(it, "Cycle") } })
            out += B2Run.case(id(), "C", ring(len), note = "ring $len: members exact, tape order",
                oracles = { h -> listOf(h.expectCycleMembers(1, (1..len).toList())) })
            out += B2Run.case(id(), "C", ring(len) + a("#2+1"),
                note = "ring $len + bystander: Blocked(by the member), never a member (D-036.5)",
                oracles = { h -> listOf(h.expectBlockedBy(len + 1, 2), h.expectCycleMembers(1, (1..len).toList())) })
            out += B2Run.case(id(), "C", ring(len) + e(1, "5"), note = "ring $len broken by edit: values cascade",
                oracles = { h -> listOf(h.expectScalar(1, "5"), h.expectScalar(2.coerceAtMost(len), if (len == 2) "6" else "${5 + len - 1}")) })
            out += B2Run.case(id(), "C", ring(len) + d(1), note = "ring $len broken by delete",
                oracles = { h -> listOf(h.expectCls(len, "Failed")) + (2 until len).map { h.expectCls(it, "Blocked") } })
            out += B2Run.case(id(), "C", ring(len) + e(1, "5") + U, note = "ring $len re-forms on undo",
                oracles = { h -> listOf(h.expectCycleMembers(1, (1..len).toList())) })
        }

        out += B2Run.case(id(), "C", listOf(a("#1+1")), note = "self-cycle of one (D-033)",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1))) })
        out += B2Run.case(id(), "C", listOf(a("2+2"), a("#2*3"), a("7")), note = "self-cycle isolates its neighbours (D-033)",
            oracles = { h -> listOf(h.expectScalar(1, "4"), h.expectCycleMembers(2, listOf(2)), h.expectScalar(3, "7")) })
        out += B2Run.case(id(), "C", listOf(a("#1+1"), e(1, "5")), note = "self-cycle recovers on edit",
            oracles = { h -> listOf(h.expectScalar(1, "5")) })
        out += B2Run.case(id(), "C", listOf(a("#1+1"), e(1, "5"), U), note = "self-cycle re-forms on undo",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1))) })

        out += B2Run.case(id(), "C",
            (1..4).map { a("0") } + listOf(e(1, "#2+1"), e(2, "#1+1"), e(3, "#4+1"), e(4, "#3+1")),
            note = "two disjoint 2-loops report as SEPARATE cycles (D-036.5)",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1, 2)), h.expectCycleMembers(3, listOf(3, 4))) })
        out += B2Run.case(id(), "C",
            (1..5).map { a("0") } + listOf(e(1, "#2+1"), e(2, "#1+1"), e(3, "#4+1"), e(4, "#5+1"), e(5, "#3+1")),
            note = "disjoint 2-loop + 3-loop keep their own member lists (D-036.5)",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1, 2)), h.expectCycleMembers(4, listOf(3, 4, 5))) })
        out += B2Run.case(id(), "C",
            (1..6).map { a("0") } + listOf(e(1, "#2+1"), e(2, "#3+1"), e(3, "#1+1"), e(4, "#5+1"), e(5, "#6+1"), e(6, "#4+1")),
            note = "two disjoint 3-loops keep their own member lists (D-036.5)",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1, 2, 3)), h.expectCycleMembers(6, listOf(4, 5, 6))) })

        for (len in listOf(2, 3, 4)) {
            val build = chain(len) + e(1, "#$len+1")
            out += B2Run.case(id(), "C", build, note = "cycle formed by editing a $len-chain's head",
                oracles = { h -> (1..len).map { h.expectCls(it, "Cycle") } })
        }

        out += B2Run.case(id(), "C", listOf(a("0"), a("ans+1"), e(1, "#2+1")), note = "ans-loop (edit-closed): #1→#2 by ref, #2→#1 by ans",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1, 2))) })
        out += B2Run.case(id(), "C", listOf(a("1"), a("0"), a("ans+1"), e(2, "#3+1")), note = "ans-loop one row down (edit-closed)",
            oracles = { h -> listOf(h.expectScalar(1, "1"), h.expectCycleMembers(2, listOf(2, 3))) })

        out += B2Run.case(id(), "C", listOf(B2Ev.AppendState(frac("1", "#1"), "1/(#1)")), note = "2D self-reference in a denominator is a cycle of one",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1))) })
        out += B2Run.case(id(), "C", listOf(B2Ev.AppendState(fn("sin", "#1"), "sin(#1)")), note = "2D self-reference in a Func arg",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1))) })
        out += B2Run.case(id(), "C", listOf(a("5"), B2Ev.AppendState(frac("1", "#1"), "1/(#1)")), note = "2D reference computes: 1/(5)",
            oracles = { h -> listOf(h.expectScalar(2, "0.2")) })
        out += B2Run.case(id(), "C", listOf(a("4"), B2Ev.AppendState(Editor.insert(Editor.typeRoot(EditorState()), "#1"), "sqrt(#1)")), note = "2D √#1",
            oracles = { h -> listOf(h.expectScalar(2, "2")) })

        out += B2Run.case(id(), "C", listOf(a("1"), a("#1+1"), e(1, "#2+1")), note = "2-ring built backward (edit closes it)",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1, 2))) })
        out += B2Run.case(id(), "C", listOf(a("#2+1"), a("#1+1")), note = "D-036.1: a forward-append 'ring' cannot form — dead ref + blocked, never Cycle",
            oracles = { h -> listOf(h.expectCls(1, "Failed"), h.expectBlockedBy(2, 1)) })
        out += B2Run.case(id(), "C", listOf(a("0"), a("#1+1"), e(1, "#2+#1")), note = "ring with an extra self-loop on a member (edit-closed)",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1, 2))) })
        return out
    }

    // --- AXIS E · EDIT / UNDO — 50 -------------------------------------------

    private fun axisE(): List<B2Case> {
        val out = mutableListOf<B2Case>()
        var n = 0
        fun id() = "B2-E-%03d".format(++n)

        for (nDeps in listOf(1, 3, 10)) {
            out += B2Run.case(id(), "E", chain(nDeps + 1) + e(1, "11"), note = "edit with $nDeps chained dependents",
                oracles = { h -> listOf(h.expectScalar(nDeps + 1, "${nDeps + 11}"), h.expectUpdated((1..nDeps + 1).toSet())) })
            out += B2Run.case(id(), "E", fan(nDeps) + e(1, "10"), note = "edit with $nDeps fanned dependents",
                oracles = { h -> listOf(h.expectUpdated((1..nDeps + 1).toSet())) })
        }
        for (nDeps in listOf(1, 3, 10)) {
            out += B2Run.custom(id(), "E", "FAILABLE", "cascade undo restores the whole $nDeps-chain snapshot") {
                val h = B2Harness()
                h.run(chain(nDeps + 1))
                val snap = h.snapshot()
                h.run(listOf(e(1, "11"), U))
                B2Run.finish(h, "FAILABLE", "MAJOR") {
                    listOf(if (h.snapshot() == snap) null else "undo did not restore the exact snapshot (D-023)")
                }
            }
        }
        out += B2Run.custom(id(), "E", "FAILABLE", "undo then redo lands on the exact post-edit state") {
            val h = B2Harness()
            h.run(chain(3) + e(1, "11"))
            val post = h.snapshot()
            h.run(listOf(U, RD))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(if (h.snapshot() == post) null else "redo ≠ post-edit state") }
        }
        out += B2Run.case(id(), "E", chain(3) + e(1, "11") + U + RD, note = "redo values",
            oracles = { h -> listOf(h.expectScalar(3, "13")) })
        out += B2Run.custom(id(), "E", "FAILABLE", "redo reports restored count") {
            val h = B2Harness()
            h.run(chain(3) + e(1, "11") + U)
            h.run(listOf(RD))
            val u = h.lastUndo
            B2Run.finish(h, "FAILABLE", "MINOR") {
                listOf(if (u is UndoOutcome.Undone) null else "redo returned $u")
            }
        }
        out += B2Run.case(id(), "E", listOf(a("1"), e(1, "2"), U, RD, U, RD), note = "undo/redo ping-pong stability",
            oracles = { h -> listOf(h.expectScalar(1, "2")) })

        for (k in listOf(1, 3, 10)) {
            out += B2Run.custom(id(), "E", "FAILABLE", "undo stack exhausts after $k transactions") {
                val h = B2Harness()
                h.run((1..k).map { a("$it") })
                repeat(k) { h.apply(U) }
                h.apply(U)
                B2Run.finish(h, "FAILABLE", "MAJOR") {
                    listOf(
                        if (h.lastUndo == UndoOutcome.NoMoreUndos) null else "expected NoMoreUndos, got ${h.lastUndo}",
                        if (h.tape.entries().isEmpty()) null else "tape not empty after full unwind",
                    )
                }
            }
        }
        out += B2Run.custom(id(), "E", "FAILABLE", "undo on a fresh tape is NoMoreUndos") {
            val h = B2Harness()
            h.apply(U)
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(if (h.lastUndo == UndoOutcome.NoMoreUndos) null else "got ${h.lastUndo}") }
        }
        out += B2Run.custom(id(), "E", "FAILABLE", "a new transaction clears the redo stack") {
            val h = B2Harness()
            h.run(listOf(a("1"), e(1, "2"), U, a("9")))
            h.apply(RD)
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(if (h.lastUndo == UndoOutcome.NoMoreUndos) null else "redo survived a new transaction") }
        }
        out += B2Run.custom(id(), "E", "FAILABLE", "a delete also clears the redo stack") {
            val h = B2Harness()
            h.run(listOf(a("1"), a("2"), e(1, "3"), U, d(2)))
            h.apply(RD)
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(if (h.lastUndo == UndoOutcome.NoMoreUndos) null else "redo survived a delete") }
        }
        out += B2Run.case(id(), "E", listOf(a("1"), a("2"), U), note = "undo of append removes the row",
            oracles = { h -> listOf(if (h.tape.entries().size == 1) null else "size != 1") })
        out += B2Run.case(id(), "E", listOf(a("1"), U), note = "undo of the first append empties the tape",
            oracles = { h -> listOf(if (h.tape.entries().isEmpty()) null else "not empty") })
        out += B2Run.case(id(), "E", listOf(a("5"), a("#1*2"), d(1), U), note = "undo of delete restores the row and its dependents",
            oracles = { h -> listOf(h.expectScalar(1, "5"), h.expectScalar(2, "10")) })
        out += B2Run.custom(id(), "E", "FAILABLE", "undo of delete restores the exact snapshot") {
            val h = B2Harness()
            h.run(listOf(a("5"), a("#1*2")))
            val snap = h.snapshot()
            h.run(listOf(d(1), U))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(if (h.snapshot() == snap) null else "snapshot mismatch") }
        }
        out += B2Run.case(id(), "E", listOf(a("2"), e(1, "1+1")), cls = "PROBE",
            note = "edit to the same VALUE: updated is empty — the snackbar would say '0 results updated'",
            oracles = { h -> listOf(h.expectUpdated(emptySet())) })
        out += B2Run.case(id(), "E", listOf(a("4"), a("#1+1"), e(1, "2+2")), cls = "PROBE",
            note = "same-value edit with a dependent: nothing counts as updated",
            oracles = { h -> listOf(h.expectUpdated(emptySet())) })
        out += B2Run.case(id(), "E", listOf(a(""), e(1, "5")), note = "Empty → value",
            oracles = { h -> listOf(h.expectScalar(1, "5")) })
        out += B2Run.case(id(), "E", listOf(a("5"), e(1, "")), note = "value → Empty",
            oracles = { h -> listOf(h.expectCls(1, "Empty")) })
        out += B2Run.case(id(), "E", listOf(a("5"), a("#1*2"), e(1, "")), note = "value → Empty blocks the dependent",
            oracles = { h -> listOf(h.expectBlockedBy(2, 1)) })
        out += B2Run.case(id(), "E", listOf(a(""), a("#1+1"), e(1, "4")), note = "Empty → value unblocks the dependent",
            oracles = { h -> listOf(h.expectScalar(2, "5")) })
        out += B2Run.case(id(), "E", listOf(a("1"), e(1, "2"), U, e(1, "3"), U, RD), note = "interleaved edit/undo/edit/undo/redo",
            oracles = { h -> listOf(h.expectScalar(1, "3")) })
        out += B2Run.case(id(), "E", listOf(a("1"), e(1, "2"), U, e(1, "3")), note = "edit after undo diverges from the redone branch",
            oracles = { h -> listOf(h.expectScalar(1, "3")) })
        out += B2Run.case(id(), "E", listOf(a("1"), a("#1+1"), e(1, "5"), U, e(2, "#1+2")), note = "undo then edit the other row",
            oracles = { h -> listOf(h.expectScalar(2, "3")) })
        out += B2Run.case(id(), "E", listOf(a("1"), a("#1+1"), e(2, "#1+5"), U), note = "undo of a leaf edit",
            oracles = { h -> listOf(h.expectScalar(2, "2")) })
        out += B2Run.case(id(), "E", chain(10) + e(5, "55"), note = "mid-chain edit updates only downstream",
            oracles = { h -> listOf(h.expectUpdated((5..10).toSet()), h.expectScalar(4, "4"), h.expectScalar(10, "60")) })
        out += B2Run.case(id(), "E", chain(10) + e(5, "55") + U, note = "mid-chain edit undone",
            oracles = { h -> listOf(h.expectScalar(10, "10")) })
        out += B2Run.case(id(), "E", chain(5) + e(3, "0"), note = "mid-chain edit values",
            oracles = { h -> listOf(h.expectScalar(5, "2")) })
        out += B2Run.case(id(), "E", chain(5) + e(3, "1/0"), note = "mid-chain edit to Failed blocks downstream",
            oracles = { h -> listOf(h.expectBlockedBy(4, 3), h.expectBlockedBy(5, 4)) })
        out += B2Run.case(id(), "E", listOf(a("1"), a("#1+1"), e(1, "2"), e(1, "3")), note = "two sequential edits, second counts alone",
            oracles = { h -> listOf(h.expectScalar(2, "4"), h.expectUpdated(setOf(1, 2))) })
        out += B2Run.case(id(), "E", listOf(a("1"), e(1, "2"), e(1, "2")), note = "repeat of an identical edit updates nothing",
            oracles = { h -> listOf(h.expectUpdated(emptySet())) })
        out += B2Run.custom(id(), "E", "PROBE", "undo-of-append restored count (measured semantics)") {
            val h = B2Harness()
            h.run(listOf(a("5")))
            h.apply(U)
            val u = h.lastUndo
            B2Run.finish(h, "PROBE", "MINOR") {
                listOf(if (u is UndoOutcome.Undone) "restored=${u.restored} for undo-of-append (the appended row is absent from the restored state)" else "got $u")
            }
        }
        out += B2Run.custom(id(), "E", "PROBE", "undo-of-edit restored count") {
            val h = B2Harness()
            h.run(listOf(a("5"), a("#1*2"), e(1, "6")))
            h.apply(U)
            val u = h.lastUndo
            B2Run.finish(h, "PROBE", "MINOR") {
                listOf(if (u is UndoOutcome.Undone) "restored=${u.restored} for undo-of-edit-with-one-dependent" else "got $u")
            }
        }
        out += B2Run.case(id(), "E", listOf(a("1"), d(1), U, RD), note = "redo of a delete",
            oracles = { h -> listOf(if (h.tape.entries().isEmpty()) null else "row survived redo of its delete") })
        out += B2Run.case(id(), "E", listOf(a("5"), a("#1*2"), d(1), U, RD), note = "redo of a delete re-breaks the dependent",
            oracles = { h -> listOf(h.expectCls(2, "Failed")) })
        out += B2Run.custom(id(), "E", "FAILABLE", "undo×3 then redo×3 is an exact round trip") {
            val h = B2Harness()
            h.run(listOf(a("1"), a("2"), a("3")))
            val snap = h.snapshot()
            h.run(listOf(U, U, U, RD, RD, RD))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(if (h.snapshot() == snap) null else "round trip drifted") }
        }
        out += B2Run.custom(id(), "E", "FAILABLE", "undo×2/redo×2 mid-history round trip") {
            val h = B2Harness()
            h.run(listOf(a("1"), e(1, "2"), e(1, "3")))
            val snap = h.snapshot()
            h.run(listOf(U, U, RD, RD))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(if (h.snapshot() == snap) null else "round trip drifted") }
        }
        out += B2Run.case(id(), "E", listOf(a("1"), e(9, "5")), note = "edit of a never-issued id is a no-op",
            oracles = { h -> listOf(h.expectUpdated(emptySet()), h.expectScalar(1, "1")) })
        out += B2Run.case(id(), "E", listOf(d(1)), note = "delete on an empty tape is a no-op",
            oracles = { h -> listOf(h.expectUpdated(emptySet())) })
        out += B2Run.case(id(), "E", listOf(a("1"), a("2"), a("3"), d(2)), note = "deleting a row with no dependents moves no values",
            oracles = { h -> listOf(h.expectUpdated(emptySet()), h.expectScalar(1, "1"), h.expectScalar(3, "3")) })
        out += B2Run.case(id(), "E", listOf(a(""), e(1, "")), note = "blank-to-blank edit is a no-op",
            oracles = { h -> listOf(h.expectUpdated(emptySet()), h.expectCls(1, "Empty")) })
        out += B2Run.case(id(), "E", listOf(a("1"), d(1), a("2"), d(2), U), note = "undo after two deletes restores only the latest",
            oracles = { h -> listOf(h.expectScalar(2, "2"), if (h.entry(1) == null) null else "#1 resurrected by the wrong undo") })
        return out
    }

    // --- AXIS R · REFERENCES / BADGE (D-031.7) — 42 --------------------------

    private fun axisR(): List<B2Case> {
        val out = mutableListOf<B2Case>()
        var n = 0
        fun id() = "B2-R-%03d".format(++n)

        // badge-insert contexts: the token goes in through the editor door
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1+1")), note = "badge ref at root end",
            oracles = { h -> listOf(h.expectScalar(2, "6"), if (h.entry(2)?.dependsOn == setOf(h.idOf(1))) null else "deps wrong") })
        out += B2Run.case(id(), "R", listOf(a("5"), a("1+#1+2")), note = "badge ref mid-expression",
            oracles = { h -> listOf(h.expectScalar(2, "8")) })
        out += B2Run.case(id(), "R", listOf(a("5"), B2Ev.AppendState(frac("1", "#1"), "1/(#1)")), note = "badge ref into a denominator",
            oracles = { h -> listOf(h.expectScalar(2, "0.2")) })
        out += B2Run.case(id(), "R", listOf(a("30"), B2Ev.AppendState(fn("sin", "#1"), "sin(#1)")), note = "badge ref into a Func arg",
            oracles = { h -> listOf(h.expectScalar(2, "0.5")) })

        // row-tap copy: nodes, not a reference — no edge, no tracking
        out += B2Run.custom(id(), "R", "FAILABLE", "row-tap copy carries no dependency edge") {
            val h = B2Harness()
            h.run(listOf(a("2+3")))
            val copied = copyOf(h.entry(1)!!.source)
            h.run(listOf(B2Ev.AppendState(copied, "copy(2+3)")))
            B2Run.finish(h, "FAILABLE", "MAJOR") {
                listOf(h.expectScalar(2, "5"), if (h.entry(2)?.dependsOn?.isEmpty() == true) null else "copy has deps ${h.entry(2)?.dependsOn}")
            }
        }
        out += B2Run.custom(id(), "R", "FAILABLE", "editing the original does not move the copy") {
            val h = B2Harness()
            h.run(listOf(a("2+3")))
            h.run(listOf(B2Ev.AppendState(copyOf(h.entry(1)!!.source), "copy(2+3)"), e(1, "9")))
            B2Run.finish(h, "FAILABLE", "MAJOR") {
                listOf(h.expectScalar(1, "9"), h.expectScalar(2, "5"), h.expectUpdated(setOf(1)))
            }
        }
        out += B2Run.custom(id(), "R", "FAILABLE", "copy with a suffix stays untracked") {
            val h = B2Harness()
            h.run(listOf(a("4")))
            h.run(listOf(B2Ev.AppendState(copyOf(h.entry(1)!!.source, "+1"), "copy(4)+1"), e(1, "7")))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(h.expectScalar(2, "5")) }
        }
        out += B2Run.custom(id(), "R", "FAILABLE", "deleting the original leaves the copy computing") {
            val h = B2Harness()
            h.run(listOf(a("2+3")))
            h.run(listOf(B2Ev.AppendState(copyOf(h.entry(1)!!.source), "copy(2+3)"), d(1)))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(h.expectScalar(2, "5")) }
        }

        // the copy-vs-reference divergence matrix, measured
        out += B2Run.custom(id(), "R", "FAILABLE", "matrix: edit original — ref moves, copy does not") {
            val h = B2Harness()
            h.run(listOf(a("2+3")))
            h.run(listOf(B2Ev.AppendState(copyOf(h.entry(1)!!.source), "copy"), a("#1+0"), e(1, "10")))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(h.expectScalar(2, "5"), h.expectScalar(3, "10")) }
        }
        out += B2Run.custom(id(), "R", "FAILABLE", "matrix: delete original — ref dies, copy lives") {
            val h = B2Harness()
            h.run(listOf(a("2+3")))
            h.run(listOf(B2Ev.AppendState(copyOf(h.entry(1)!!.source), "copy"), a("#1+0"), d(1)))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(h.expectScalar(2, "5"), h.expectCls(3, "Failed")) }
        }
        out += B2Run.custom(id(), "R", "FAILABLE", "matrix: undo of the edit — ref returns, copy never moved") {
            val h = B2Harness()
            h.run(listOf(a("2+3")))
            h.run(listOf(B2Ev.AppendState(copyOf(h.entry(1)!!.source), "copy"), a("#1+0"), e(1, "10"), U))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(h.expectScalar(2, "5"), h.expectScalar(3, "5")) }
        }
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1+0"), e(1, "1/0")), note = "matrix: ref inherits the failure as Blocked",
            oracles = { h -> listOf(h.expectBlockedBy(2, 1)) })
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1+0"), e(1, "")), note = "matrix: ref blocked by Empty",
            oracles = { h -> listOf(h.expectBlockedBy(2, 1)) })
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1+0"), e(1, "6"), U, RD), note = "matrix: redo re-moves the ref",
            oracles = { h -> listOf(h.expectScalar(2, "6")) })

        // the dangle proof
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1*2"), d(1)), note = "dangle(a): committed ref, target deleted → typed Failed",
            oracles = { h -> listOf(h.expectCls(2, "Failed")) })
        out += B2Run.case(id(), "R", listOf(a("5"), d(1), a("#1*2")), note = "dangle(a): target deleted before commit → typed Failed",
            oracles = { h -> listOf(h.expectCls(2, "Failed")) })
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1*2"), d(1), U), note = "dangle(a): undo restores the target and the value",
            oracles = { h -> listOf(h.expectScalar(2, "10")) })
        out += B2Run.case(id(), "R", listOf(a("1"), a("2"), d(2), a("3")), note = "dangle(b): deleted id never re-issued",
            oracles = { h -> listOf(if (h.entry(2) == null && h.entry(3) != null) null else "id bookkeeping wrong") })
        out += B2Run.case(id(), "R", listOf(a("1"), d(1), a("2"), d(2), a("3")), note = "dangle(b): repeated delete/append keeps climbing",
            oracles = { h -> listOf(if (h.entry(3) != null && h.tape.entries().size == 1) null else "expected only #3") })
        out += B2Run.case(id(), "R", listOf(a("1"), a("2"), U, a("3")), note = "dangle(b): undo of append does not rewind the id counter",
            oracles = { h -> listOf(if (h.entry(2) == null && h.entry(3) != null) null else "id counter rewound — re-issue hazard") })
        out += B2Run.custom(id(), "R", "FAILABLE", "dangle(c): ref to an undone id stays dead") {
            val h = B2Harness()
            h.run(listOf(a("1"), a("2"), U, a("#2+1")))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(h.expectCls(3, "Failed")) }
        }
        out += B2Run.case(id(), "R", listOf(a("1"), a("2"), U, RD, a("#2+1")), note = "dangle(c): redo resurrects the target legitimately",
            oracles = { h -> listOf(h.expectScalar(3, "3")) })
        out += B2Run.case(id(), "R", listOf(a("5"), e(1, "6"), a("#1+1")), note = "dangle(d): ref binds the CURRENT value at recompute",
            oracles = { h -> listOf(h.expectScalar(2, "7")) })
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1+1"), e(1, "6")), note = "dangle(d): ref keeps following later edits",
            oracles = { h -> listOf(h.expectScalar(2, "7")) })

        // THE REBOUND HUNT — resolved by D-036.1: the commit guard makes adoption
        // unrepresentable. These four cases moved from findings to enforcement.
        out += B2Run.custom(id(), "R", "FAILABLE", "D-036.1: dangling #3 is a typed error naming the id; a stranger wearing #3 is never adopted") {
            val h = B2Harness()
            h.run(listOf(a("#3+1")))
            val s1 = h.expectCls(1, "Failed")
            val msg = (h.valueOf(1) as? EntryValue.Failed)?.error?.message
            h.run(listOf(a("10"), a("20"))) // a brand-new entry now wears #3
            B2Run.finish(h, "FAILABLE", "CRITICAL") {
                listOf(
                    s1,
                    if (msg?.contains("#3") == true) null else "commit error does not name #3: <$msg>",
                    if (h.clsOf(1) == "Failed") null else "REBOUND: #1 became ${h.clsOf(1)} when the stranger arrived",
                )
            }
        }
        out += B2Run.custom(id(), "R", "FAILABLE", "D-036.1: larger gap — #5 stays dead when entry #5 arrives") {
            val h = B2Harness()
            h.run(listOf(a("#5+1"), a("1"), a("1"), a("1")))
            val s1 = h.expectCls(1, "Failed")
            h.run(listOf(a("99")))
            B2Run.finish(h, "FAILABLE", "CRITICAL") {
                listOf(s1, if (h.clsOf(1) == "Failed") null else "REBOUND: #1 adopted the stranger (=100)")
            }
        }
        out += B2Run.custom(id(), "R", "FAILABLE", "D-036.1: edits of the stranger never reach the dead entry") {
            val h = B2Harness()
            h.run(listOf(a("#3+1"), a("10"), a("20"), e(3, "30")))
            B2Run.finish(h, "FAILABLE", "CRITICAL") {
                listOf(h.expectCls(1, "Failed"))
            }
        }
        out += B2Run.custom(id(), "R", "FAILABLE", "D-036.1: dead before, dead after undo of the id-wearing append") {
            val h = B2Harness()
            h.run(listOf(a("#3+1"), a("10"), a("20"), U))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(h.expectCls(1, "Failed")) }
        }

        out += B2Run.case(id(), "R", listOf(a("#1+1")), note = "self-badge is a cycle of one (D-033)",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1))) })
        out += B2Run.case(id(), "R", listOf(B2Ev.AppendState(frac("#1", "2"), "(#1)/(2)")), note = "2D self-badge",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1))) })
        out += B2Run.case(id(), "R", listOf(a("2"), a("3"), a("4"), a("#1+#2+#3")), note = "multi-ref sums",
            oracles = { h -> listOf(h.expectScalar(4, "9")) })
        out += B2Run.case(id(), "R", listOf(a("2"), a("3"), a("4"), a("#1+#2+#3"), e(1, "1/0")), note = "multi-ref blocked by the FIRST broken dep",
            oracles = { h -> listOf(h.expectBlockedBy(4, 1)) })
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1+1"), e(1, "1/0")), note = "ref target → Failed: dependent Blocked",
            oracles = { h -> listOf(h.expectBlockedBy(2, 1)) })
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1+1"), e(1, "")), note = "ref target → Empty: dependent Blocked",
            oracles = { h -> listOf(h.expectBlockedBy(2, 1)) })
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1+1"), e(1, "#1+1")),
            note = "ref target becomes a self-cycle: dependent is Blocked by it (D-036.5)",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1)), h.expectBlockedBy(2, 1)) })
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1+1"), d(1)), note = "ref target deleted: dependent Failed",
            oracles = { h -> listOf(h.expectCls(2, "Failed")) })
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1+#1")), note = "duplicate refs dedup to one edge",
            oracles = { h -> listOf(h.expectScalar(2, "10"), if (h.entry(2)?.dependsOn == setOf(h.idOf(1))) null else "deps not deduped") })
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1*#1")), note = "duplicate refs: product",
            oracles = { h -> listOf(h.expectScalar(2, "25")) })
        out += B2Run.case(id(), "R", listOf(a("2"), a("x=#1")), cls = "PROBE", note = "ref inside an equation source (outcome measured)")
        out += B2Run.case(id(), "R", listOf(a("5"), a("#1 km")), note = "ref inside a unit application",
            oracles = { h -> listOf(if ((h.valueOf(2) as? EntryValue.Computed)?.value != null) null else "expected a quantity value") })
        out += B2Run.custom(id(), "R", "FAILABLE", "mixed: a copy of #1's nodes plus a live #2 ref") {
            val h = B2Harness()
            h.run(listOf(a("4"), a("6")))
            h.run(listOf(B2Ev.AppendState(copyOf(h.entry(1)!!.source, "+#2"), "copy(4)+#2")))
            B2Run.finish(h, "FAILABLE", "MAJOR") {
                listOf(h.expectScalar(3, "10"), if (h.entry(3)?.dependsOn == setOf(h.idOf(2))) null else "deps should be exactly {#2}")
            }
        }
        out += B2Run.custom(id(), "R", "FAILABLE", "mixed: editing the copied source moves nothing; editing the ref target moves the sum") {
            val h = B2Harness()
            h.run(listOf(a("4"), a("6")))
            h.run(listOf(B2Ev.AppendState(copyOf(h.entry(1)!!.source, "+#2"), "copy+ref"), e(1, "9"), e(2, "10")))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(h.expectScalar(3, "14")) }
        }
        return out
    }

    // --- AXIS S · STATE HONESTY — 45 -----------------------------------------

    private fun axisS(): List<B2Case> {
        val out = mutableListOf<B2Case>()
        var n = 0
        fun id() = "B2-S-%03d".format(++n)

        out += B2Run.case(id(), "S", listOf(a("1"), a("2"), a("ans+10"), d(2)), note = "ans-rebind on middle delete is named",
            oracles = { h -> listOf(h.expectScalar(3, "11"), h.expectUpdated(setOf(3))) })
        out += B2Run.case(id(), "S", listOf(a("1"), a("2"), a("ans+10"), d(1)), note = "ans-rebind on first delete (row above unchanged)",
            oracles = { h -> listOf(h.expectScalar(3, "12"), h.expectUpdated(emptySet())) })
        out += B2Run.case(id(), "S", listOf(a("1"), a("2"), a("ans+10"), a("ans+100"), d(2)), note = "two ans rows below a delete",
            oracles = { h -> listOf(h.expectScalar(3, "11"), h.expectScalar(4, "111"), h.expectUpdated(setOf(3, 4))) })
        out += B2Run.case(id(), "S", listOf(a("1"), a("2"), a("ans+10"), d(2), U), note = "ans-rebind undone",
            oracles = { h -> listOf(h.expectScalar(3, "12")) })

        out += B2Run.case(id(), "S", chain(3) + e(1, "11"), note = "updated set: chain", oracles = { h -> listOf(h.expectUpdated(setOf(1, 2, 3))) })
        out += B2Run.case(id(), "S", fan(3) + e(1, "70"), note = "updated set: fan", oracles = { h -> listOf(h.expectUpdated(setOf(1, 2, 3, 4))) })
        out += B2Run.case(id(), "S", listOf(a("1"), a("#1+1"), a("#1+2"), a("#2+#3"), e(1, "2")), note = "updated set: diamond",
            oracles = { h -> listOf(h.expectUpdated(setOf(1, 2, 3, 4))) })
        out += B2Run.case(id(), "S", listOf(a("1"), a("9"), e(2, "9")), note = "updated set: identical edit is empty",
            oracles = { h -> listOf(h.expectUpdated(emptySet())) })

        out += B2Run.custom(id(), "S", "FAILABLE", "Failed keeps the previous Computed value") {
            val h = B2Harness()
            h.run(listOf(a("2+2"), e(1, "1/0")))
            val v = h.valueOf(1) as? EntryValue.Failed
            B2Run.finish(h, "FAILABLE", "MAJOR") {
                listOf(if (v?.previous != null && Runner.numericOf(v.previous!!)?.toPlainString() == "4") null
                else "Failed.previous=${v?.previous} — 'was 4' lost")
            }
        }
        out += B2Run.custom(id(), "S", "FAILABLE", "Blocked keeps the previous Computed value") {
            val h = B2Harness()
            h.run(listOf(a("5"), a("#1+1"), e(1, "1/0")))
            val v = h.valueOf(2) as? EntryValue.Blocked
            B2Run.finish(h, "FAILABLE", "MAJOR") {
                listOf(if (v?.previous != null && Runner.numericOf(v.previous!!)?.toPlainString() == "6") null
                else "Blocked.previous=${v?.previous} — 'was 6' lost")
            }
        }
        out += B2Run.custom(id(), "S", "PROBE", "does previous SURVIVE the next unrelated transaction?") {
            val h = B2Harness()
            h.run(listOf(a("2+2"), e(1, "1/0"), a("9")))
            val v = h.valueOf(1) as? EntryValue.Failed
            B2Run.finish(h, "PROBE", "MINOR") {
                listOf("after one more transaction, Failed.previous=" +
                    (v?.previous?.let { Runner.numericOf(it)?.toPlainString() } ?: "null — the 'was' value does not survive recompute"))
            }
        }
        out += B2Run.custom(id(), "S", "PROBE", "Cycle has no previous field — the 'was' value is dropped") {
            val h = B2Harness()
            h.run(listOf(a("2+2"), e(1, "#1+1")))
            B2Run.finish(h, "PROBE", "MINOR") { listOf("Cycle carries members only; previous 4 is gone (design: 'was, now updating')") }
        }

        out += B2Run.case(id(), "S", listOf(a("1/0"), a("#1+1"), a("#2+1")), note = "transitive blocking names the IMMEDIATE cause",
            oracles = { h -> listOf(h.expectBlockedBy(2, 1), h.expectBlockedBy(3, 2)) })
        out += B2Run.case(id(), "S", listOf(a("1/0"), a(""), a("#1+#2")), note = "two broken deps: the FIRST is named",
            oracles = { h -> listOf(h.expectBlockedBy(3, 1)) })
        out += B2Run.case(id(), "S", listOf(a(""), a("#1+1")), note = "blocked by Empty",
            oracles = { h -> listOf(h.expectBlockedBy(2, 1)) })

        out += B2Run.case(id(), "S", listOf(a("x^2-5x+6=0"), a("#1*2")), cls = "PROBE",
            note = "Blocked by a SUCCESSFUL solve — 'why: it failed' would be a lie in the UI",
            oracles = { h -> listOf(h.expectBlockedBy(2, 1)) })
        out += B2Run.case(id(), "S", listOf(a("1=1"), a("#1+1")), cls = "PROBE",
            note = "Blocked by a truth verdict (D-016.2 outcome has no referenceable value)",
            oracles = { h -> listOf(h.expectCls(1, "Computed"), h.expectBlockedBy(2, 1)) })

        out += B2Run.case(id(), "S", listOf(a("1/0"), a("#1+1"), d(1)), note = "Blocked → Failed on eviction of the blocker",
            oracles = { h -> listOf(h.expectCls(2, "Failed")) })
        out += B2Run.case(id(), "S", listOf(a(""), a("#1+1"), d(1)), note = "Blocked-by-Empty → Failed on eviction",
            oracles = { h -> listOf(h.expectCls(2, "Failed")) })

        out += B2Run.case(id(), "S", listOf(a("#99+1")), note = "non-existent reference is Failed, not Blocked (D-033)",
            oracles = { h -> listOf(h.expectCls(1, "Failed")) })
        out += B2Run.case(id(), "S", listOf(a("#0+1")), note = "#0 never exists — Failed",
            oracles = { h -> listOf(h.expectCls(1, "Failed")) })

        out += B2Run.custom(id(), "S", "FAILABLE", "undo restores value-level deep equality after delete") {
            val h = B2Harness()
            h.run(listOf(a("2+2"), a("#1*3"), a("ans+1")))
            val snap = h.snapshot()
            h.run(listOf(d(2), U))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(if (h.snapshot() == snap) null else "snapshot drifted") }
        }
        out += B2Run.custom(id(), "S", "FAILABLE", "undo restores deep equality after a chain of edits") {
            val h = B2Harness()
            h.run(listOf(a("1"), a("#1+1")))
            val snap = h.snapshot()
            h.run(listOf(e(1, "5"), e(2, "#1+9"), U, U))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(if (h.snapshot() == snap) null else "snapshot drifted") }
        }

        out += B2Run.case(id(), "S", listOf(a("2"), e(1, "1+1")), cls = "PROBE", note = "updated-contract: source changed, value did not — counts 0",
            oracles = { h -> listOf(h.expectUpdated(emptySet())) })
        out += B2Run.case(id(), "S", listOf(a("6"), a("#1/2"), e(1, "2*3")), cls = "PROBE", note = "updated-contract with dependent: still 0",
            oracles = { h -> listOf(h.expectUpdated(emptySet())) })

        // carried-data pins ×15
        out += B2Run.custom(id(), "S", "FAILABLE", "Failed(parse) and Failed(eval) carry distinct error kinds") {
            val h = B2Harness()
            h.run(listOf(a("1+"), a("1/0")))
            val p = (h.valueOf(1) as? EntryValue.Failed)?.error?.kind?.name
            val v = (h.valueOf(2) as? EntryValue.Failed)?.error?.kind?.name
            B2Run.finish(h, "FAILABLE", "MINOR") {
                listOf(if (p != null && v != null && p != v) null else "kinds: parse=$p eval=$v — not distinct")
            }
        }
        out += B2Run.custom(id(), "S", "FAILABLE", "Failed carries a non-empty message") {
            val h = B2Harness()
            h.run(listOf(a("1/0")))
            val msg = (h.valueOf(1) as? EntryValue.Failed)?.error?.message
            B2Run.finish(h, "FAILABLE", "MINOR") { listOf(if (!msg.isNullOrBlank()) null else "blank Failed message") }
        }
        out += B2Run.custom(id(), "S", "FAILABLE", "first-ever Failed has null previous") {
            val h = B2Harness()
            h.run(listOf(a("1/0")))
            val v = h.valueOf(1) as? EntryValue.Failed
            B2Run.finish(h, "FAILABLE", "MINOR") { listOf(if (v != null && v.previous == null) null else "previous=${v?.previous}") }
        }
        out += B2Run.custom(id(), "S", "FAILABLE", "Blocked names its blocker and starts with null previous") {
            val h = B2Harness()
            h.run(listOf(a("1/0"), a("#1+1")))
            val v = h.valueOf(2) as? EntryValue.Blocked
            B2Run.finish(h, "FAILABLE", "MINOR") { listOf(if (v != null && v.by.n == 1 && v.previous == null) null else "by=${v?.by} prev=${v?.previous}") }
        }
        out += B2Run.case(id(), "S", listOf(a("1/0"), a("#1+1"), e(1, "7"), e(1, "1/0")), note = "Blocked regains previous after a computed interlude",
            oracles = { h ->
                val v = h.valueOf(2) as? EntryValue.Blocked
                listOf(if (v?.previous != null) null else "previous lost after recovery+re-break")
            })
        out += B2Run.case(id(), "S", ring(2), note = "Cycle members in tape order [1,2], not reversed",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1, 2)), h.expectCycleMembers(2, listOf(1, 2))) })
        out += B2Run.case(id(), "S", listOf(a("#1+1")), note = "self-cycle members exactly [1]",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1))) })
        out += B2Run.case(id(), "S", listOf(a("5"), a("#2+1"), a("#2+1")), note = "self-cycle among neighbours: SCC of one, neighbour Blocked (D-036.5)",
            oracles = { h -> listOf(h.expectCycleMembers(2, listOf(2)), h.expectBlockedBy(3, 2)) })
        out += B2Run.custom(id(), "S", "FAILABLE", "Computed(Scalar) exposes a value") {
            val h = B2Harness()
            h.run(listOf(a("2+2")))
            val c = h.valueOf(1) as? EntryValue.Computed
            B2Run.finish(h, "FAILABLE", "MINOR") { listOf(if (c?.value != null) null else "scalar outcome with no value") }
        }
        out += B2Run.custom(id(), "S", "FAILABLE", "Computed(Stored) exposes the stored value") {
            val h = B2Harness()
            h.run(listOf(a("5→A")))
            val c = h.valueOf(1) as? EntryValue.Computed
            B2Run.finish(h, "FAILABLE", "MINOR") { listOf(if (c?.value != null && Runner.numericOf(c.value!!)?.toPlainString() == "5") null else "Stored value wrong") }
        }
        out += B2Run.custom(id(), "S", "FAILABLE", "Computed(solve) has NO referenceable value — by design (D-021)") {
            val h = B2Harness()
            h.run(listOf(a("x^2-5x+6=0")))
            val c = h.valueOf(1) as? EntryValue.Computed
            B2Run.finish(h, "FAILABLE", "MINOR") { listOf(if (c != null && c.value == null) null else "solve outcome unexpectedly scalar") }
        }
        out += B2Run.case(id(), "S", listOf(a("")), note = "blank source is Empty, not Failed",
            oracles = { h -> listOf(h.expectCls(1, "Empty")) })
        out += B2Run.case(id(), "S", listOf(a("   ")), note = "whitespace-only source is Empty",
            oracles = { h -> listOf(h.expectCls(1, "Empty")) })
        out += B2Run.case(id(), "S", listOf(a(" 1 ")), cls = "PROBE", note = "padded source (parse behaviour measured)")
        out += B2Run.custom(id(), "S", "FAILABLE", "Empty entry has an empty dependency set") {
            val h = B2Harness()
            h.run(listOf(a("")))
            B2Run.finish(h, "FAILABLE", "MINOR") { listOf(if (h.entry(1)?.dependsOn?.isEmpty() == true) null else "Empty with deps") }
        }

        out += B2Run.custom(id(), "S", "FAILABLE", "parse-Failed vs eval-Failed messages differ in kind name") {
            val h = B2Harness()
            h.run(listOf(a("((("), a("sqrt(-1)")))
            val k1 = (h.valueOf(1) as? EntryValue.Failed)?.error?.kind?.name
            B2Run.finish(h, "FAILABLE", "MINOR") { listOf(if (k1 != null) null else "unbalanced parse not Failed") }
        }
        out += B2Run.case(id(), "S", listOf(a("#1")), note = "bare self-reference is still a cycle of one",
            oracles = { h -> listOf(h.expectCls(1, "Cycle")) })
        out += B2Run.case(id(), "S", listOf(a("5"), a("#1")), note = "bare reference passes the value through",
            oracles = { h -> listOf(h.expectScalar(2, "5")) })
        out += B2Run.case(id(), "S", listOf(a("1"), a("2"), a("3"), d(3)), note = "deleting the last row reports no updates",
            oracles = { h -> listOf(h.expectUpdated(emptySet())) })
        out += B2Run.case(id(), "S", listOf(a("   "), a("ans+1")), note = "ans after a whitespace-Empty row is Blocked by it",
            oracles = { h -> listOf(h.expectBlockedBy(2, 1)) })
        return out
    }

    // --- AXIS A · ANS — 14 ---------------------------------------------------

    private fun axisA2(): List<B2Case> {
        val out = mutableListOf<B2Case>()
        var n = 0
        fun id() = "B2-A-%03d".format(++n)

        out += B2Run.case(id(), "A", listOf(a("ans+1")), note = "ans on the first row: nothing above — Failed (unbound)",
            oracles = { h -> listOf(h.expectCls(1, "Failed")) })
        out += B2Run.case(id(), "A", listOf(a("1"), U, a("ans+1")), note = "ans after undo-to-empty is still first-row (new row is #2 — ids never rewind)",
            oracles = { h -> listOf(h.expectCls(2, "Failed"), if (h.entry(1) == null) null else "#1 resurrected") })
        out += B2Run.case(id(), "A", listOf(a("1/0"), a("ans+1")), note = "ans after a Failed row: Blocked by it",
            oracles = { h -> listOf(h.expectBlockedBy(2, 1)) })
        out += B2Run.case(id(), "A", listOf(a(""), a("ans+1")), note = "ans after an Empty row: Blocked by it",
            oracles = { h -> listOf(h.expectBlockedBy(2, 1)) })
        out += B2Run.case(id(), "A", listOf(a("x^2-5x+6=0"), a("ans+1")), note = "ans after a solve (no value): Blocked",
            oracles = { h -> listOf(h.expectBlockedBy(2, 1)) })
        out += B2Run.case(id(), "A", listOf(a("5→A"), a("ans+1")), note = "ans after a store (has value): computes",
            oracles = { h -> listOf(h.expectScalar(2, "6")) })
        out += B2Run.case(id(), "A", listOf(a("1"), a("ans+1"), a("ans+1")), note = "ans dep-set names the row above",
            oracles = { h -> listOf(if (h.entry(3)?.dependsOn == setOf(h.idOf(2))) null else "deps=${h.entry(3)?.dependsOn}") })
        out += B2Run.case(id(), "A", listOf(a("1"), a("2"), a("ans+1"), d(2)), note = "delete swaps which row ans names",
            oracles = { h -> listOf(if (h.entry(3)?.dependsOn == setOf(h.idOf(1))) null else "deps=${h.entry(3)?.dependsOn}", h.expectScalar(3, "2")) })
        out += B2Run.case(id(), "A", listOf(a("1"), a("ans+1"), e(1, "10"), U), note = "ans chain under undo",
            oracles = { h -> listOf(h.expectScalar(2, "2")) })
        out += B2Run.case(id(), "A", listOf(a("1"), a("ans+1"), d(1), U), note = "ans survives delete+undo of its target",
            oracles = { h -> listOf(h.expectScalar(2, "2")) })
        out += B2Run.case(id(), "A", listOf(a("3"), a("ANS+1")), note = "ANS is case-insensitive",
            oracles = { h -> listOf(h.expectScalar(2, "4")) })
        out += B2Run.case(id(), "A", listOf(a("3"), a("Ans*2")), note = "Ans mixed case",
            oracles = { h -> listOf(h.expectScalar(2, "6")) })
        out += B2Run.case(id(), "A", listOf(a("5"), a("ans+1"), a("#1+1")), note = "ans and #1 agree while adjacent",
            oracles = { h -> listOf(h.expectScalar(2, "6"), h.expectScalar(3, "6")) })
        out += B2Run.case(id(), "A", listOf(a("5"), a("ans+1"), a("#1+1"), d(1)), note = "under delete they diverge: ans rebinds, #1 dies",
            oracles = { h -> listOf(h.expectCls(3, "Failed"), if (h.clsOf(2) == "Failed") null else "#2(ans) now ${h.clsOf(2)} — rebinding measured") })
        return out
    }

    // --- AXIS V · LANGUAGE × TAPE — 36 ---------------------------------------

    private fun axisV(): List<B2Case> {
        val out = mutableListOf<B2Case>()
        var n = 0
        fun id() = "B2-V-%03d".format(++n)

        out += B2Run.case(id(), "V", listOf(a("5→A"), a("A+1")),
            note = "registers are tape-global (D-036.11): 5→A then A+1 = 6, edge on the assigner",
            oracles = { h -> listOf(h.expectScalar(2, "6"), if (h.entry(2)?.dependsOn == setOf(h.idOf(1))) null else "deps=${h.entry(2)?.dependsOn}") })
        out += B2Run.case(id(), "V", listOf(a("5→A"), a("#1+1")), note = "the ref route to a stored value works",
            oracles = { h -> listOf(h.expectScalar(2, "6")) })
        out += B2Run.case(id(), "V", listOf(a("5→x"), a("x+1")), cls = "PROBE",
            note = "variable isolation: same question for x (measured)")
        out += B2Run.custom(id(), "V", "FAILABLE", "Registers panel: store, read back, A–F,M order") {
            val env = Environment()
            val regs = Registers(env)
            val five = (com.vaibhav.calc.core.Calculator().evaluate("5") as com.vaibhav.calc.core.error.CalcResult.Ok).value
            val stored = regs.store("A", five)
            val names = regs.all().map { it.name }
            val failures = mutableListOf<String?>()
            failures += if (stored is StoreResult.Stored) null else "store(A) = $stored"
            failures += if (regs.get("A")?.value != null) null else "A reads empty after store"
            failures += if (names == listOf("A", "B", "C", "D", "E", "F", "M")) null else "order=$names"
            val fails = failures.filterNotNull()
            if (fails.isEmpty()) B2Result("PASS", null, null, 0) else B2Result("FAIL", "MAJOR", fails.joinToString("; "), 0)
        }
        out += B2Run.custom(id(), "V", "FAILABLE", "Registers: clear makes EMPTY, not zero; reserved names blocked") {
            val env = Environment()
            val regs = Registers(env)
            val five = (com.vaibhav.calc.core.Calculator().evaluate("5") as com.vaibhav.calc.core.error.CalcResult.Ok).value
            regs.store("A", five)
            regs.clear("A")
            val a = regs.get("A")
            val blocked = regs.store("pi", five)
            val fails = listOfNotNull(
                if (a != null && a.isEmpty) null else "cleared A not empty",
                if (blocked is StoreResult.Blocked) null else "storing into 'pi' not blocked",
            )
            if (fails.isEmpty()) B2Result("PASS", null, null, 0) else B2Result("FAIL", "MAJOR", fails.joinToString("; "), 0)
        }
        out += B2Run.custom(id(), "V", "FAILABLE", "Registers: occupied() lists only stored slots") {
            val env = Environment()
            val regs = Registers(env)
            val five = (com.vaibhav.calc.core.Calculator().evaluate("5") as com.vaibhav.calc.core.error.CalcResult.Ok).value
            regs.store("C", five)
            val occ = regs.occupied().map { it.name }
            if (occ == listOf("C")) B2Result("PASS", null, null, 0) else B2Result("FAIL", "MINOR", "occupied=$occ", 0)
        }

        out += B2Run.case(id(), "V", listOf(a("१+२")), note = "Devanagari input on the tape (D-028)",
            oracles = { h -> listOf(h.expectScalar(1, "3")) })
        out += B2Run.case(id(), "V", listOf(a("2"), a("#1+१")), note = "Devanagari mixed with a reference",
            oracles = { h -> listOf(h.expectScalar(2, "3")) })
        out += B2Run.case(id(), "V", listOf(a("5"), e(1, "१०")), note = "edit to Devanagari (१० = 10)",
            oracles = { h -> listOf(h.expectScalar(1, "10")) })
        out += B2Run.case(id(), "V", listOf(a("०"), a("#1+१"), e(1, "#2+१")), note = "Devanagari ring (edit-closed per D-036.1)",
            oracles = { h -> listOf(h.expectCycleMembers(1, listOf(1, 2))) })

        out += B2Run.case(id(), "V", listOf(a("5 km")), note = "quantity entry",
            oracles = { h -> listOf(if ((h.valueOf(1) as? EntryValue.Computed)?.value != null) null else "no value") })
        out += B2Run.case(id(), "V", listOf(a("5 km"), a("#1 + 300 m")), note = "left operand's unit kept (D-014): 5.3 km",
            oracles = { h -> listOf(h.expectScalar(2, "5.3")) })
        out += B2Run.case(id(), "V", listOf(a("5 km"), a("#1*2")), note = "quantity scales through a reference",
            oracles = { h -> listOf(h.expectScalar(2, "10")) })
        out += B2Run.case(id(), "V", listOf(a("5 km"), a("#1 + 300 m"), a("#2 + #1")), note = "quantity chain",
            oracles = { h -> listOf(h.expectScalar(3, "10.3")) })
        out += B2Run.case(id(), "V", listOf(a("2 m"), a("ans * 3")), note = "quantity through ans",
            oracles = { h -> listOf(h.expectScalar(2, "6")) })
        out += B2Run.case(id(), "V", listOf(a("5 km + 3 s")), note = "incompatible units fail typed",
            oracles = { h -> listOf(h.expectCls(1, "Failed")) })

        out += B2Run.case(id(), "V", listOf(a("x^2-5x+6=0")), note = "solve entry is Computed",
            oracles = { h -> listOf(h.expectCls(1, "Computed")) })
        out += B2Run.case(id(), "V", listOf(a("x^2-5x+6=0"), a("#1+1"), e(1, "7")), note = "solve → scalar recovery unblocks",
            oracles = { h -> listOf(h.expectScalar(2, "8")) })
        out += B2Run.case(id(), "V", listOf(a("x^2-5x+6=0"), a("x^2-1=0")), note = "two solves coexist",
            oracles = { h -> listOf(h.expectCls(1, "Computed"), h.expectCls(2, "Computed")) })
        out += B2Run.case(id(), "V", listOf(a("x^2-5x+6=0"), e(1, "x^2-9=0")), note = "solve edited to another solve",
            oracles = { h -> listOf(h.expectCls(1, "Computed")) })

        out += B2Run.case(id(), "V", listOf(a("50%")), note = "percent literal", oracles = { h -> listOf(h.expectScalar(1, "0.5")) })
        out += B2Run.case(id(), "V", listOf(a("3"), a("#1!")), note = "factorial of a reference",
            oracles = { h -> listOf(h.expectScalar(2, "6")) })
        out += B2Run.case(id(), "V", listOf(a("5%+1")), note = "percent in arithmetic", oracles = { h -> listOf(h.expectScalar(1, "1.05")) })
        out += B2Run.case(id(), "V", listOf(a("4"), a("#1!+#1")), note = "factorial + ref", oracles = { h -> listOf(h.expectScalar(2, "28")) })

        out += B2Run.case(id(), "V", listOf(a("0xFF")), note = "hex literal (D-012)", oracles = { h -> listOf(h.expectScalar(1, "255")) })
        out += B2Run.case(id(), "V", listOf(a("2"), a("#1+0b10")), note = "binary literal with ref", oracles = { h -> listOf(h.expectScalar(2, "4")) })
        out += B2Run.case(id(), "V", listOf(a("10"), e(1, "0x0A")), note = "edit to hex, same value — updated empty", cls = "PROBE",
            oracles = { h -> listOf(h.expectUpdated(emptySet())) })
        out += B2Run.case(id(), "V", listOf(a("#1+0x1")), note = "hex self-ref is still a cycle",
            oracles = { h -> listOf(h.expectCls(1, "Cycle")) })

        out += B2Run.case(id(), "V", listOf(a("re(3)")), note = "re() of a real", oracles = { h -> listOf(h.expectScalar(1, "3")) })
        out += B2Run.case(id(), "V", listOf(a("conj(2)")), note = "conj of a real", oracles = { h -> listOf(h.expectScalar(1, "2")) })
        out += B2Run.case(id(), "V", listOf(a("i*i")), cls = "PROBE", note = "i*i under default settings (measured)")
        out += B2Run.case(id(), "V", listOf(a("sqrt(-1)")), cls = "PROBE", note = "sqrt(-1) under default complexEnabled (D-011, measured)")

        out += B2Run.case(id(), "V", listOf(a("pi*e")), note = "constants", oracles = { h -> listOf(h.expectScalar(1, "8.539734222673568")) })
        out += B2Run.case(id(), "V", listOf(a("sin(30)")), note = "DEG default on the tape (D-005)", oracles = { h -> listOf(h.expectScalar(1, "0.5")) })
        out += B2Run.case(id(), "V", listOf(a("√9")), note = "√ glyph parses", oracles = { h -> listOf(h.expectScalar(1, "3")) })
        out += B2Run.case(id(), "V", listOf(a("root(8,3)")), note = "root(x,n) argument order (D-008)", oracles = { h -> listOf(h.expectScalar(1, "2")) })
        return out
    }

    // --- AXIS I · DETERMINISM — 26 -------------------------------------------

    private fun axisI(): List<B2Case> {
        val out = mutableListOf<B2Case>()
        var n = 0
        fun id() = "B2-I-%03d".format(++n)

        data class Scenario(val note: String, val build: List<B2Ev>, val editOrd: Int, val sameSrc: String)
        val scenarios = listOf(
            Scenario("standalone", listOf(a("2+2")), 1, "2+2"),
            Scenario("chain2", chain(2), 2, "#1+1"),
            Scenario("chain5-head", chain(5), 1, "1"),
            Scenario("chain5-mid", chain(5), 3, "#2+1"),
            Scenario("fan3-root", fan(3), 1, "7"),
            Scenario("fan3-leaf", fan(3), 2, "#1+1"),
            Scenario("diamond", listOf(a("1"), a("#1+1"), a("#1+2"), a("#2+#3")), 4, "#2+#3"),
            Scenario("failed", listOf(a("1/0")), 1, "1/0"),
            Scenario("blocked", listOf(a("1/0"), a("#1+1")), 2, "#1+1"),
            Scenario("cycle2", ring(2), 1, "#2+1"),
            Scenario("cycle3", ring(3), 2, "#3+1"),
            Scenario("self", listOf(a("#1+1")), 1, "#1+1"),
            Scenario("empty", listOf(a("")), 1, ""),
            Scenario("ans-chain", listOf(a("1"), a("ans+1")), 2, "ans+1"),
            Scenario("solve", listOf(a("x^2-5x+6=0")), 1, "x^2-5x+6=0"),
            Scenario("stored", listOf(a("5→A")), 1, "5→A"),
            Scenario("unit", listOf(a("5 km")), 1, "5 km"),
            Scenario("multiref", listOf(a("2"), a("3"), a("#1+#2")), 3, "#1+#2"),
            // D-036.1 note: a no-op RE-EDIT of a dead-ref entry is deliberately NOT
            // idempotent (the edit re-captures), so the old forward-recovered
            // scenario left this list; a stable quantity chain replaced it.
            Scenario("quantity-chain", listOf(a("5 km"), a("#1*2")), 2, "#1*2"),
            Scenario("disjoint-cycles",
                (1..4).map { a("0") } + listOf(e(1, "#2+1"), e(2, "#1+1"), e(3, "#4+1"), e(4, "#3+1")),
                1, "#2+1"),
        )
        for (sc in scenarios) {
            out += B2Run.custom(id(), "I", "FAILABLE", "no-op edit idempotence: ${sc.note}") {
                val h = B2Harness()
                h.run(sc.build)
                val snap = h.snapshot()
                h.run(listOf(e(sc.editOrd, sc.sameSrc)))
                B2Run.finish(h, "FAILABLE", "MAJOR") {
                    listOf(
                        if (h.snapshot() == snap) null else "no-op edit changed the state",
                        h.expectUpdated(emptySet()),
                    )
                }
            }
        }

        // order independence: same logical graph, two construction routes → same value
        // multiset. D-036.1 moved every "reversed append" variant to placeholder+edit
        // construction (a forward APPEND is now a dead ref, by design).
        data class Orders(val note: String, val o1: List<B2Ev>, val o2: List<B2Ev>, val values: Set<String>)
        val orders = listOf(
            Orders("pair", listOf(a("7"), a("#1+1")),
                listOf(a("0"), a("7"), e(1, "#2+1")), setOf("7", "8")),
            Orders("triple-chain", listOf(a("7"), a("#1+1"), a("#2+1")),
                listOf(a("0"), a("0"), a("7"), e(2, "#3+1"), e(1, "#2+1")), setOf("7", "8", "9")),
            Orders("fan", listOf(a("7"), a("#1+1"), a("#1+2")),
                listOf(a("0"), a("0"), a("7"), e(1, "#3+1"), e(2, "#3+2")), setOf("7", "8", "9")),
            Orders("diamond", listOf(a("1"), a("#1+1"), a("#1+2"), a("#2+#3")),
                listOf(a("0"), a("0"), a("0"), a("1"), e(2, "#4+1"), e(3, "#4+2"), e(1, "#2+#3")), setOf("1", "2", "3", "5")),
            Orders("mixed", listOf(a("2"), a("#1*3"), a("#2-#1")),
                listOf(a("0"), a("0"), a("2"), e(2, "#3*3"), e(1, "#2-#3")), setOf("2", "6", "4")),
            Orders("deep", listOf(a("1"), a("#1+1"), a("#2+1"), a("#3+1")),
                listOf(a("0"), a("0"), a("0"), a("1"), e(3, "#4+1"), e(2, "#3+1"), e(1, "#2+1")), setOf("1", "2", "3", "4")),
        )
        for (o in orders) {
            out += B2Run.custom(id(), "I", "FAILABLE", "order independence: ${o.note}") {
                fun valuesOf(evs: List<B2Ev>): Pair<Set<String>?, String?> {
                    val h = B2Harness()
                    if (!h.run(evs)) return null to (h.crash ?: h.violation)
                    val vals = h.tape.entries().mapNotNull { en ->
                        (en.value as? EntryValue.Computed)?.value?.let { Runner.numericOf(it)?.stripTrailingZeros()?.toPlainString() }
                    }.toSet()
                    return vals to null
                }
                val (v1, e1) = valuesOf(o.o1)
                val (v2, e2) = valuesOf(o.o2)
                val fails = listOfNotNull(
                    e1?.let { "order1: $it" }, e2?.let { "order2: $it" },
                    if (v1 != null && v1 != o.values) "order1 values $v1 != ${o.values}" else null,
                    if (v2 != null && v2 != o.values) "order2 values $v2 != ${o.values}" else null,
                )
                if (fails.isEmpty()) B2Result("PASS", null, null, o.o1.size + o.o2.size)
                else B2Result("FAIL", "MAJOR", fails.joinToString("; "), o.o1.size + o.o2.size)
            }
        }
        return out
    }

    // --- AXIS X · STRESS — 50 ------------------------------------------------

    private fun axisX(): List<B2Case> {
        val out = mutableListOf<B2Case>()
        var n = 0
        fun id() = "B2-X-%03d".format(++n)

        out += B2Run.case(id(), "X", chain(200), note = "200-chain build", classEvery = false,
            oracles = { h -> listOf(h.expectScalar(1, "1"), h.expectScalar(100, "100"), h.expectScalar(200, "200")) })
        out += B2Run.case(id(), "X", chain(200) + e(1, "11"), note = "200-chain cascade", classEvery = false,
            oracles = { h -> listOf(h.expectScalar(100, "110"), h.expectScalar(200, "210"), h.expectUpdated((1..200).toSet())) })
        out += B2Run.case(id(), "X", fan(100), note = "100-fan build", classEvery = false,
            oracles = { h -> listOf(h.expectScalar(101, "107")) })
        out += B2Run.case(id(), "X", fan(100) + e(1, "0"), note = "100-fan cascade", classEvery = false,
            oracles = { h -> listOf(h.expectScalar(101, "100"), h.expectUpdated((1..101).toSet())) })
        out += B2Run.custom(id(), "X", "FAILABLE", "50 appends, 50 undos — clean unwind") {
            val h = B2Harness(checkClassEvery = false)
            h.run((1..50).map { a("$it") })
            repeat(25) { h.apply(U) }
            val mid = h.tape.entries().size
            repeat(25) { h.apply(U) }
            B2Run.finish(h, "FAILABLE", "MAJOR") {
                listOf(if (mid == 25) null else "midpoint size $mid != 25", if (h.tape.entries().isEmpty()) null else "not empty")
            }
        }
        out += B2Run.custom(id(), "X", "FAILABLE", "50 undos then 50 redos — full round trip") {
            val h = B2Harness(checkClassEvery = false)
            h.run((1..50).map { a("$it") })
            val snap = h.snapshot()
            repeat(50) { h.apply(U) }
            repeat(50) { h.apply(RD) }
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(if (h.snapshot() == snap) null else "round trip drifted") }
        }
        out += B2Run.custom(id(), "X", "FAILABLE", "append/delete alternation ×50 ends empty") {
            val h = B2Harness(checkClassEvery = false)
            for (i in 1..50) { h.apply(a("$i")); h.apply(d(i)) }
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(if (h.tape.entries().isEmpty()) null else "not empty") }
        }
        out += B2Run.custom(id(), "X", "FAILABLE", "alternation preserves id monotonicity") {
            val h = B2Harness(checkClassEvery = false)
            for (i in 1..50) { h.apply(a("$i")); h.apply(d(i)) }
            h.apply(a("final"))
            B2Run.finish(h, "FAILABLE", "MAJOR") {
                listOf(if (h.tape.entries().singleOrNull()?.id?.n == 51) null else "final id ${h.tape.entries().singleOrNull()?.id} != #51")
            }
        }
        val twenty = (1..20).map { a("$it") } + a((1..20).joinToString("+") { "#$it" })
        out += B2Run.case(id(), "X", twenty, note = "one row referencing 20 others", classEvery = false,
            oracles = { h -> listOf(h.expectScalar(21, "210")) })
        out += B2Run.case(id(), "X", twenty + e(5, "105"), note = "20-ref row follows one leg", classEvery = false,
            oracles = { h -> listOf(h.expectScalar(21, "310")) })
        val triangle = listOf(a("1")) + (2..30).map { k -> a((1 until k).joinToString("+") { "#$it" }) }
        out += B2Run.case(id(), "X", triangle, note = "dense 30-entry triangle: #30 = 2^28", classEvery = false,
            oracles = { h -> listOf(h.expectScalar(30, "268435456")) })
        out += B2Run.case(id(), "X", triangle + e(1, "2"), note = "triangle cascade: #30 doubles to 2^29", classEvery = false,
            oracles = { h -> listOf(h.expectScalar(30, "536870912")) })
        out += B2Run.case(id(), "X", listOf(B2Ev.Undo), note = "undo on empty tape",
            oracles = { h -> listOf(if (h.lastUndo == UndoOutcome.NoMoreUndos) null else "got ${h.lastUndo}") })
        out += B2Run.case(id(), "X", listOf(B2Ev.Redo), note = "redo on empty tape",
            oracles = { h -> listOf(if (h.lastUndo == UndoOutcome.NoMoreUndos) null else "got ${h.lastUndo}") })
        out += B2Run.case(id(), "X", listOf(d(1)), note = "delete on empty tape", oracles = { h -> listOf(h.expectUpdated(emptySet())) })
        out += B2Run.case(id(), "X", listOf(e(1, "5")), note = "edit on empty tape", oracles = { h -> listOf(h.expectUpdated(emptySet())) })
        out += B2Run.case(id(), "X", listOf(a("1"), a(""), a("ans+1")), note = "ans binds to the blank row above — Blocked, honestly",
            oracles = { h -> listOf(h.expectBlockedBy(3, 2)) })
        out += B2Run.case(id(), "X", listOf(a("1"), a(""), a("#1+1")), note = "#n skips the blank row",
            oracles = { h -> listOf(h.expectScalar(3, "2")) })

        // seeded storms — all invariants live
        val pool = listOf("1", "2+2", "#1+1", "#2*2", "ans+1", "1/0", "", "#9+1", "x^2-5x+6=0", "५+५")
        for (seed in 0 until 12) {
            out += B2Run.custom(id(), "X", "FAILABLE", "event storm #$seed (40 events, invariants live)") {
                val h = B2Harness()
                val rng = Random(3000 + seed)
                var issued = 0
                for (step in 0 until 40) {
                    val ev: B2Ev = when (rng.nextInt(13)) {
                        in 0..4 -> { issued++; a(pool[rng.nextInt(pool.size)]) }
                        in 5..7 -> e(rng.nextInt(1, issued + 2), pool[rng.nextInt(pool.size)])
                        in 8..9 -> d(rng.nextInt(1, issued + 2))
                        in 10..11 -> U
                        else -> RD
                    }
                    if (!h.apply(ev)) break
                }
                B2Run.finish(h, "FAILABLE", "MAJOR") { emptyList() }
            }
        }
        for (seed in 0 until 2) {
            out += B2Run.custom(id(), "X", "FAILABLE", "long storm #$seed (100 events, final classifier check)") {
                val h = B2Harness(checkClassEvery = false)
                val rng = Random(4000 + seed)
                var issued = 0
                for (step in 0 until 100) {
                    val ev: B2Ev = when (rng.nextInt(13)) {
                        in 0..4 -> { issued++; a(pool[rng.nextInt(pool.size)]) }
                        in 5..7 -> e(rng.nextInt(1, issued + 2), pool[rng.nextInt(pool.size)])
                        in 8..9 -> d(rng.nextInt(1, issued + 2))
                        in 10..11 -> U
                        else -> RD
                    }
                    if (!h.apply(ev)) break
                }
                B2Run.finish(h, "FAILABLE", "MAJOR") { emptyList() }
            }
        }
        for (seed in 0 until 4) {
            out += B2Run.custom(id(), "X", "FAILABLE", "2D-source storm #$seed") {
                val h = B2Harness()
                val rng = Random(5000 + seed)
                var issued = 0
                for (step in 0 until 20) {
                    val ev: B2Ev = when (rng.nextInt(10)) {
                        in 0..3 -> { issued++; a(pool[rng.nextInt(pool.size)]) }
                        4 -> { issued++; B2Ev.AppendState(frac("1", "#" + rng.nextInt(1, issued + 1)), "1/(#k)") }
                        5 -> { issued++; B2Ev.AppendState(fn("sin", "#" + rng.nextInt(1, issued + 1)), "sin(#k)") }
                        in 6..7 -> e(rng.nextInt(1, issued + 2), pool[rng.nextInt(pool.size)])
                        8 -> d(rng.nextInt(1, issued + 2))
                        else -> U
                    }
                    if (!h.apply(ev)) break
                }
                B2Run.finish(h, "FAILABLE", "MAJOR") { emptyList() }
            }
        }
        for (seed in 0 until 4) {
            out += B2Run.custom(id(), "X", "FAILABLE", "delete-heavy storm #$seed") {
                val h = B2Harness()
                val rng = Random(6000 + seed)
                var issued = 0
                for (step in 0 until 30) {
                    val ev: B2Ev = when (rng.nextInt(10)) {
                        in 0..3 -> { issued++; a(pool[rng.nextInt(pool.size)]) }
                        in 4..6 -> d(rng.nextInt(1, issued + 2))
                        in 7..8 -> U
                        else -> RD
                    }
                    if (!h.apply(ev)) break
                }
                B2Run.finish(h, "FAILABLE", "MAJOR") { emptyList() }
            }
        }
        for (seed in 0 until 2) {
            out += B2Run.custom(id(), "X", "FAILABLE", "undo/redo ping-pong ×50 #$seed") {
                val h = B2Harness()
                h.run(listOf(a("1"), a("#1+1"), e(1, "5")))
                val snap = h.snapshot()
                repeat(50) { h.apply(U); h.apply(RD) }
                B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(if (h.snapshot() == snap) null else "ping-pong drifted") }
            }
        }
        out += B2Run.custom(id(), "X", "FAILABLE", "mass Failure: 30 dependents lose their target") {
            val h = B2Harness(checkClassEvery = false)
            h.run(listOf(a("1")) + (1..30).map { a("#1+$it") } + listOf(d(1)))
            B2Run.finish(h, "FAILABLE", "MAJOR") {
                (2..31).map { h.expectCls(it, "Failed") }
            }
        }
        out += B2Run.custom(id(), "X", "FAILABLE", "mass Failure never rebinds: a later append is NOT #1") {
            val h = B2Harness(checkClassEvery = false)
            h.run(listOf(a("1")) + (1..30).map { a("#1+$it") } + listOf(d(1), a("999")))
            B2Run.finish(h, "FAILABLE", "CRITICAL") {
                (2..31).map { if (h.clsOf(it) == "Failed") null else "#$it adopted the new entry — rebound via deletion" }
            }
        }
        out += B2Run.custom(id(), "X", "FAILABLE", "mass delete undone restores all 30") {
            val h = B2Harness(checkClassEvery = false)
            h.run(listOf(a("1")) + (1..30).map { a("#1+$it") })
            val snap = h.snapshot()
            h.run(listOf(d(1), U))
            B2Run.finish(h, "FAILABLE", "MAJOR") { listOf(if (h.snapshot() == snap) null else "not restored") }
        }
        out += B2Run.case(id(), "X", listOf(a("1"), a("#1+1"), d(1), U, d(2), U), note = "alternating delete/undo of both rows",
            oracles = { h -> listOf(h.expectScalar(2, "2")) })
        out += B2Run.case(id(), "X", (1..5).map { a("x^2-${it}x=0") } + (1..5).map { a("#$it+1") }, note = "solve-heavy: five solves, five blocked dependents", classEvery = false,
            oracles = { h -> (6..10).map { h.expectBlockedBy(it, it - 5) } })
        out += B2Run.case(id(), "X", (1..5).map { a("x^2-${it}x=0") } + (1..5).map { a("#$it+1") } + e(1, "1"), note = "solve-heavy partial recovery", classEvery = false,
            oracles = { h -> listOf(h.expectScalar(6, "2"), h.expectBlockedBy(7, 2)) })
        out += B2Run.case(id(), "X", listOf(a("99^9")), note = "big value computes", oracles = { h -> listOf(h.expectCls(1, "Computed")) })
        out += B2Run.case(id(), "X", listOf(a("20!"), a("#1+1")), note = "20! through a reference",
            oracles = { h -> listOf(h.expectScalar(2, "2432902008176640001")) })
        return out
    }

    // --- AXIS U · FORBIDDEN STATES — 8 ---------------------------------------

    private fun axisU(): List<B2Case> {
        val out = mutableListOf<B2Case>()
        var n = 0
        fun id() = "B2-U-%03d".format(++n)
        // I-FORBIDDEN is live on every case; these sweep representative sequences explicitly.
        out += B2Run.case(id(), "U", listOf(a("1"), a("#1+1"), e(1, "2"), d(2), U, RD, U), note = "sweep 1: Stale/Ambiguous never constructed",
            oracles = { _ -> emptyList() })
        out += B2Run.case(id(), "U", ring(3) + listOf(e(1, "5"), U, d(2), U), note = "sweep 2: cycles round-trip without forbidden states",
            oracles = { _ -> emptyList() })
        out += B2Run.case(id(), "U", listOf(a("x=5")), note = "x=5 routes as an EQUATION (D-016.1) — never Ambiguous",
            oracles = { h -> listOf(h.expectCls(1, "Computed")) })
        out += B2Run.case(id(), "U", listOf(a("5→x")), cls = "PROBE",
            note = "D-016.1's own example `5 → x` is REFUSED — x is an unknown (D-016.4); doc-vs-engine tension, owner ruling",
            oracles = { h -> listOf(h.expectCls(1, "Failed")) })
        out += B2Run.case(id(), "U", listOf(a("x^3-4x^2+x+6=0")), cls = "PROBE",
            note = "M-2 gap: the design's Unverified state does not exist in EntryValue — cubic outcome recorded")
        out += B2Run.case(id(), "U", listOf(a("x^2+1=0")), cls = "PROBE",
            note = "M-2 gap: no-real-roots outcome recorded (state class measured)")
        out += B2Run.case(id(), "U", listOf(a("1"), e(1, "x=1"), e(1, "5→x"), e(1, "1")), note = "sweep 3: router outcomes cycle through without Ambiguous",
            oracles = { h -> listOf(h.expectScalar(1, "1")) })
        out += B2Run.case(id(), "U", listOf(a("ans"), a("ans"), a("ans")), note = "sweep 4: bare ans chain (first Failed, rest follow)",
            oracles = { h -> listOf(h.expectCls(1, "Failed"), h.expectBlockedBy(2, 1), h.expectBlockedBy(3, 2)) })
        return out
    }

    // --- assembly ------------------------------------------------------------

    fun all(): List<B2Case> =
        axisK() + axisT() + axisD() + axisC() + axisE() + axisR() + axisS() + axisA2() + axisV() + axisI() + axisX() + axisU()

    val declared: Map<String, Int> = mapOf(
        "K" to 16, "T" to 95, "D" to 69, "C" to 49, "E" to 50, "R" to 42,
        "S" to 45, "A" to 14, "V" to 36, "I" to 26, "X" to 50, "U" to 8,
    )
}
