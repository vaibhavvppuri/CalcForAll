package com.vaibhav.calc.battery

import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.tape.EntryId
import com.vaibhav.calc.core.tape.EntryValue
import com.vaibhav.calc.core.tape.Tape
import com.vaibhav.calc.core.tape.TapeState
import com.vaibhav.calc.core.tape.Transaction
import com.vaibhav.calc.core.tape.UndoOutcome
import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * B2 — the TAPE brain battery. Cases drive the real [Tape] through the same door
 * `TapeTransitionSpec` uses (sources built via `Editor.insert`). After EVERY event
 * of every case the harness asserts:
 *
 *  - I-DEPS       every entry's `dependsOn` ⊆ ids on the tape
 *  - I-IDS        ids strictly ascending in list order, never re-issued
 *  - I-SILENT     any entry whose value changed is named in the transaction's
 *                 `updated` list — the named-harm tripwire (FM-1)
 *  - I-FORBIDDEN  no entry is ever `Stale` or `Ambiguous` (D-032 / D-031.8)
 *  - I-CLASS      state class, `Blocked.by`, `Cycle.members`, dep-set and scalar
 *                 value agree with [B2Reference] (D-032's table, implemented from text)
 *
 * Severity: CRITICAL = silent value change, dangling/rebound reference reaching a
 * value, crash/hang, forbidden state, wrong arithmetic. MAJOR = classifier
 * divergence without a silent change; wrong `updated` sets; misnamed blocker or
 * members. PROBE = decided-silence observations for the owner.
 */
internal sealed interface B2Ev {
    data class Append(val src: String) : B2Ev
    data class AppendState(val state: EditorState, val label: String) : B2Ev
    data class Edit(val ord: Int, val src: String) : B2Ev
    data class Delete(val ord: Int) : B2Ev
    object Undo : B2Ev
    object Redo : B2Ev
}

internal fun B2Ev.show(): String = when (this) {
    is B2Ev.Append -> "A(${src.take(20)})"
    is B2Ev.AppendState -> "A2D($label)"
    is B2Ev.Edit -> "E#$ord(${src.take(20)})"
    is B2Ev.Delete -> "D#$ord"
    B2Ev.Undo -> "undo"; B2Ev.Redo -> "redo"
}

internal data class B2Result(
    val verdict: String, val severity: String?, val detail: String?, val evsRun: Int,
)

internal data class B2Case(
    val id: String, val axis: String, val cls: String, val note: String,
    val evs: List<B2Ev>, val runner: () -> B2Result,
)

internal fun src(text: String): EditorState = Editor.insert(EditorState(), text)

internal class B2Harness(private val checkClassEvery: Boolean = true) {
    private val settings = Settings()
    val tape = Tape(settings)
    var evsRun = 0
    var crash: String? = null
    var violation: String? = null       // first invariant violation
    var violationSeverity: String? = null
    var lastTx: Transaction? = null
    var lastUndo: UndoOutcome? = null
    private var expectedNextId = 1
    val issuedIds = mutableListOf<EntryId>()

    fun idOf(ord: Int): EntryId = EntryId(ord)

    fun entry(ord: Int): com.vaibhav.calc.core.tape.TapeEntry? = tape.entry(EntryId(ord))
    fun valueOf(ord: Int): EntryValue? = entry(ord)?.value
    fun clsOf(ord: Int): String? = valueOf(ord)?.let { clsName(it) }

    fun apply(ev: B2Ev): Boolean {
        if (crash != null || violation != null) return false
        val before: Map<EntryId, EntryValue> = tape.entries().associate { it.id to it.value }
        val idsBefore: Set<EntryId> = tape.entries().map { it.id }.toSet()
        try {
            when (ev) {
                is B2Ev.Append -> {
                    lastTx = tape.append(src(ev.src))
                    val newId = tape.state.entries.lastOrNull()?.id
                    if (newId != EntryId(expectedNextId)) {
                        record("I-IDS: append issued $newId, expected #$expectedNextId (monotonic, never re-used)", "CRITICAL")
                    }
                    newId?.let { issuedIds += it }
                    expectedNextId++
                }
                is B2Ev.AppendState -> {
                    lastTx = tape.append(ev.state)
                    tape.state.entries.lastOrNull()?.id?.let { issuedIds += it }
                    expectedNextId++
                }
                is B2Ev.Edit -> lastTx = tape.edit(EntryId(ev.ord), src(ev.src))
                is B2Ev.Delete -> lastTx = tape.delete(EntryId(ev.ord))
                B2Ev.Undo -> { lastUndo = tape.undo(); lastTx = null }
                B2Ev.Redo -> { lastUndo = tape.redo(); lastTx = null }
            }
        } catch (t: Throwable) {
            crash = "ev#$evsRun ${ev.show()} threw ${t::class.java.simpleName}: ${t.message?.take(140)}"
            return false
        }
        evsRun++
        checkDeadRefBookkeeping(ev, idsBefore)
        checkInvariants(ev, before)
        return crash == null && violation == null
    }

    /**
     * I-DEADREF (D-036.1): the engine's commit-time capture, re-derived from this
     * harness's own view of the event. The classifier then TRUSTS the stored sets
     * (verified here) and independently derives their consequences.
     */
    private fun checkDeadRefBookkeeping(ev: B2Ev, idsBefore: Set<EntryId>) {
        val (target, source, idsAtCommit) = when (ev) {
            is B2Ev.Append -> {
                val e = tape.entries().lastOrNull() ?: return
                Triple(e, e.source, idsBefore + e.id)
            }
            is B2Ev.AppendState -> {
                val e = tape.entries().lastOrNull() ?: return
                Triple(e, e.source, idsBefore + e.id)
            }
            is B2Ev.Edit -> {
                val e = tape.entry(EntryId(ev.ord)) ?: return
                Triple(e, e.source, idsBefore)
            }
            else -> return
        }
        val expected = B2Reference.refsIn(source).filterNot { it in idsAtCommit }.toSet()
        if (target.deadRefs != expected) {
            record(
                "I-DEADREF after ${ev.show()}: ${target.id} deadRefs=${target.deadRefs}, " +
                    "harness derives $expected from the tape at commit",
                "CRITICAL",
            )
        }
    }

    fun run(evs: List<B2Ev>): Boolean { for (e in evs) if (!apply(e)) return false; return true }

    private fun record(msg: String, severity: String) {
        if (violation == null) { violation = msg; violationSeverity = severity }
    }

    private fun checkInvariants(ev: B2Ev, before: Map<EntryId, EntryValue>) {
        val entries = tape.entries()
        val ids = entries.map { it.id }

        // I-DEPS
        for (e in entries) {
            val missing = e.dependsOn.filterNot { it in ids }
            if (missing.isNotEmpty()) {
                record("I-DEPS after ${ev.show()}: ${e.id} dependsOn $missing not on the tape", "CRITICAL"); return
            }
        }
        // I-IDS
        if (ids.map { it.n } != ids.map { it.n }.sorted() || ids.toSet().size != ids.size) {
            record("I-IDS after ${ev.show()}: ids not strictly ascending unique: $ids", "CRITICAL"); return
        }
        // I-FORBIDDEN
        for (e in entries) {
            if (e.value is EntryValue.Stale || e.value is EntryValue.Ambiguous) {
                record("I-FORBIDDEN after ${ev.show()}: ${e.id} is ${clsName(e.value)} (unreachable per D-032/D-031.8)", "CRITICAL"); return
            }
        }
        // I-SILENT — the named harm: a value change the transaction did not report.
        lastTx?.let { tx ->
            if (ev is B2Ev.Append || ev is B2Ev.AppendState || ev is B2Ev.Edit || ev is B2Ev.Delete) {
                for (e in entries) {
                    val old = before[e.id] ?: continue
                    if (old != e.value && e.id !in tx.updated) {
                        record(
                            "I-SILENT after ${ev.show()}: ${e.id} value changed " +
                                "(${clsName(old)} -> ${clsName(e.value)}) but is NOT in Transaction.updated=${tx.updated}",
                            "CRITICAL",
                        ); return
                    }
                }
            }
        }
        // I-CLASS
        if (checkClassEvery) classCheck(ev)
    }

    fun classCheck(ev: B2Ev?): Boolean {
        val entries = tape.entries()
        val expect = try {
            B2Reference.derive(
                entries.map { it.id to it.source }, settings,
                entries.associate { it.id to it.deadRefs },
            )
        } catch (t: Throwable) {
            record("REFERENCE derive threw ${t::class.java.simpleName}: ${t.message?.take(120)}", "CRITICAL")
            return false
        }
        for (e in entries) {
            val x = expect.getValue(e.id)
            val actualCls = clsName(e.value)
            if (actualCls != x.cls) {
                record(evPrefix(ev) + "${e.id} <${e.source.toInput().take(30)}> is $actualCls, table says ${x.cls}", "MAJOR")
                return false
            }
            if (e.dependsOn != x.deps) {
                record(evPrefix(ev) + "${e.id} dependsOn=${e.dependsOn} table says ${x.deps}", "MAJOR"); return false
            }
            when (val v = e.value) {
                is EntryValue.Blocked -> if (v.by != x.blockedBy) {
                    record(evPrefix(ev) + "${e.id} Blocked by ${v.by}, table says ${x.blockedBy}", "MAJOR"); return false
                }
                is EntryValue.Cycle -> if (v.members != x.cycleMembers) {
                    record(evPrefix(ev) + "${e.id} Cycle members ${v.members}, table says ${x.cycleMembers}", "MAJOR"); return false
                }
                is EntryValue.Computed -> {
                    val ev1 = v.value; val ev2 = x.scalar
                    if ((ev1 == null) != (ev2 == null)) {
                        record(evPrefix(ev) + "${e.id} scalar-ness mismatch: engine=${ev1 != null} table=${ev2 != null}", "MAJOR")
                        return false
                    }
                    if (ev1 != null && ev2 != null && !valuesAgree(ev1, ev2)) {
                        record(
                            evPrefix(ev) + "${e.id} WRONG VALUE: engine=${Runner.numericOf(ev1)?.toPlainString()} " +
                                "independent=${Runner.numericOf(ev2)?.toPlainString()}",
                            "CRITICAL",
                        ); return false
                    }
                }
                else -> Unit
            }
        }
        return true
    }

    private fun evPrefix(ev: B2Ev?): String = "I-CLASS" + (ev?.let { " after ${it.show()}" } ?: "") + ": "

    // --- case-level oracle helpers ------------------------------------------

    fun expectCls(ord: Int, cls: String): String? {
        val actual = clsOf(ord) ?: return "#$ord not on tape (expected $cls)"
        return if (actual == cls) null else "#$ord is $actual, expected $cls"
    }

    fun expectScalar(ord: Int, expected: String): String? {
        val v = valueOf(ord) ?: return "#$ord not on tape"
        val c = v as? EntryValue.Computed ?: return "#$ord is ${clsName(v)}, expected Computed $expected"
        val value = c.value ?: return "#$ord Computed but valueless, expected $expected"
        val got = Runner.numericOf(value) ?: return "#$ord non-numeric, expected $expected"
        val exp = BigDecimal(expected)
        val diff = got.subtract(exp, MC).abs()
        val ok = if (exp.compareTo(BigDecimal.ZERO) == 0) diff <= TOL else diff.divide(exp.abs(), MC) <= TOL
        return if (ok) null else "#$ord = ${got.toPlainString()}, expected $expected — WRONG VALUE"
    }

    fun expectUpdated(ords: Set<Int>): String? {
        val tx = lastTx ?: return "no transaction to inspect"
        val got = tx.updated.map { it.n }.toSet()
        return if (got == ords) null else "updated=$got, expected $ords"
    }

    fun expectBlockedBy(ord: Int, by: Int): String? {
        val v = valueOf(ord) as? EntryValue.Blocked ?: return "#$ord is ${clsOf(ord)}, expected Blocked"
        return if (v.by.n == by) null else "#$ord blocked by ${v.by}, expected #$by"
    }

    fun expectCycleMembers(ord: Int, members: List<Int>): String? {
        val v = valueOf(ord) as? EntryValue.Cycle ?: return "#$ord is ${clsOf(ord)}, expected Cycle"
        val got = v.members.map { it.n }
        return if (got == members) null else "#$ord members $got, expected $members"
    }

    fun snapshot(): TapeState = tape.state

    companion object {
        private val MC = MathContext(30, RoundingMode.HALF_EVEN)
        private val TOL = BigDecimal("1e-9")

        fun clsName(v: EntryValue): String = when (v) {
            is EntryValue.Computed -> "Computed"
            EntryValue.Empty -> "Empty"
            is EntryValue.Stale -> "Stale"
            is EntryValue.Failed -> "Failed"
            is EntryValue.Blocked -> "Blocked"
            is EntryValue.Cycle -> "Cycle"
            is EntryValue.Ambiguous -> "Ambiguous"
        }

        fun valuesAgree(a: com.vaibhav.calc.core.eval.Value, b: com.vaibhav.calc.core.eval.Value): Boolean {
            val na = Runner.numericOf(a); val nb = Runner.numericOf(b)
            if (na != null && nb != null) {
                return na.compareTo(nb) == 0 && Runner.exactnessOf(a) == Runner.exactnessOf(b)
            }
            return Runner.exactnessOf(a) == Runner.exactnessOf(b)
        }
    }
}

// ---------------------------------------------------------------------------

internal object B2Run {

    /**
     * The standard runner: run the events under all five invariants, then apply the
     * case's own oracles (each returns null = satisfied, or a failure message).
     */
    fun case(
        id: String, axis: String, evs: List<B2Ev>,
        cls: String = "FAILABLE", note: String = "",
        classEvery: Boolean = true,
        oracleSeverity: String = "MAJOR",
        oracles: (B2Harness) -> List<String?> = { emptyList() },
    ): B2Case = B2Case(id, axis, cls, note, evs) {
        val h = B2Harness(checkClassEvery = classEvery)
        h.run(evs)
        finish(h, cls, oracleSeverity, oracles)
    }

    fun finish(
        h: B2Harness, cls: String, oracleSeverity: String,
        oracles: (B2Harness) -> List<String?>,
    ): B2Result {
        h.crash?.let { return B2Result("FAIL", "CRITICAL", "CRASH: $it", h.evsRun) }
        h.violation?.let {
            return if (cls == "PROBE" && h.violationSeverity != "CRITICAL") {
                B2Result("PROBE", null, "divergence(probe): $it", h.evsRun)
            } else B2Result("FAIL", h.violationSeverity, it, h.evsRun)
        }
        if (!h.classCheck(null)) {
            h.violation?.let {
                return if (cls == "PROBE" && h.violationSeverity != "CRITICAL") {
                    B2Result("PROBE", null, "divergence(probe): $it", h.evsRun)
                } else B2Result("FAIL", h.violationSeverity, it, h.evsRun)
            }
        }
        val failures = try { oracles(h).filterNotNull() } catch (t: Throwable) {
            return B2Result("FAIL", "CRITICAL", "oracle threw ${t::class.java.simpleName}: ${t.message?.take(120)}", h.evsRun)
        }
        if (failures.isNotEmpty()) {
            val sev = if (failures.any { it.contains("WRONG VALUE") }) "CRITICAL" else oracleSeverity
            return if (cls == "PROBE") B2Result("PROBE", null, failures.joinToString("; ").take(300), h.evsRun)
            else B2Result("FAIL", sev, failures.joinToString("; ").take(300), h.evsRun)
        }
        return if (cls == "PROBE") {
            val obs = h.tape.entries().joinToString(" | ") { "${it.id}=${B2Harness.clsName(it.value)}" }
            B2Result("PROBE", null, "observed: ${obs.take(240)}", h.evsRun)
        } else B2Result("PASS", null, null, h.evsRun)
    }

    fun custom(id: String, axis: String, cls: String, note: String, evs: List<B2Ev> = emptyList(), body: () -> B2Result): B2Case =
        B2Case(id, axis, cls, note, evs, body)
}

internal object B2Emit {
    fun write(file: File, rows: List<Pair<B2Case, B2Result>>) {
        file.parentFile?.mkdirs()
        val sb = StringBuilder("[\n")
        rows.forEachIndexed { i, (c, r) ->
            sb.append("  {")
            sb.append(MiniJson.field("id", c.id)).append(',')
            sb.append(MiniJson.field("axis", c.axis)).append(',')
            sb.append(MiniJson.field("class", c.cls)).append(',')
            sb.append(MiniJson.field("note", c.note)).append(',')
            sb.append(MiniJson.field("events", c.evs.joinToString(" ") { it.show() }.take(400))).append(',')
            sb.append(MiniJson.field("verdict", r.verdict)).append(',')
            sb.append(MiniJson.field("severity", r.severity)).append(',')
            sb.append(MiniJson.field("detail", r.detail)).append(',')
            sb.append(MiniJson.rawField("evsRun", r.evsRun.toString()))
            sb.append('}')
            if (i != rows.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append("]\n")
        file.writeText(sb.toString(), Charsets.UTF_8)
    }
}
