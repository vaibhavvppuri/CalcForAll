package com.vaibhav.calc.battery

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.entry.Atom
import com.vaibhav.calc.core.entry.Clip
import com.vaibhav.calc.core.entry.Clipboard
import com.vaibhav.calc.core.entry.CopyResult
import com.vaibhav.calc.core.entry.Cursor
import com.vaibhav.calc.core.entry.CutResult
import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.entry.Node
import com.vaibhav.calc.core.entry.PasteResult
import com.vaibhav.calc.core.entry.Row
import com.vaibhav.calc.core.entry.Selection
import com.vaibhav.calc.core.entry.field
import com.vaibhav.calc.core.entry.fields
import com.vaibhav.calc.core.entry.toInput
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.keypad.FnPalette
import com.vaibhav.calc.core.keypad.Key
import com.vaibhav.calc.core.keypad.KeyAction
import com.vaibhav.calc.core.keypad.Keypad
import com.vaibhav.calc.core.keypad.Layer
import com.vaibhav.calc.core.parse.Parser
import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * B3 — the EDITOR brain battery. Cases drive `Editor` / `Keypad` / `FnPalette` /
 * `Clipboard` directly (no UI), each op mirrored through [B3Reference] (the
 * rule-text model), with four invariants asserted after EVERY engine operation:
 *
 *  - I-CURSOR   the cursor path resolves, offsets and selections are in bounds
 *  - I-PAREN    no `Atom('(')`/`Atom(')')` exists anywhere in the tree
 *  - I-FLAT     `toInput()` equals the independent linearizer (the FM-1 tripwire)
 *  - I-SHRINK   content only ever shrinks visibly: over a selection, as a single
 *               atom under ⌫, or as a collapse that keeps every atom
 *
 * Severity (the B3 instantiation of the B1 ladder): CRITICAL = screen-vs-evaluated
 * divergence, crash/hang/non-termination, silent content destruction, invalid
 * cursor state. MAJOR = rule divergence without content loss; paste-contract
 * breach. PROBE = the decisions are silent; recorded for an owner ruling.
 */
internal sealed interface B3Op {
    data class Ins(val text: String) : B3Op
    object Frac : B3Op
    object Pow : B3Op
    object RootK : B3Op
    object ParenO : B3Op
    object ParenC : B3Op
    data class Fn(val name: String) : B3Op
    object Bksp : B3Op
    object L : B3Op
    object R : B3Op
    data class PasteT(val text: String) : B3Op
    object Copy : B3Op
    object Cut : B3Op
    object PasteClip : B3Op
    data class SelRange(val from: Int, val to: Int) : B3Op
    data class KeyTap(val id: String) : B3Op
    data class KeyFlick(val id: String) : B3Op
    data class KeySecond(val id: String) : B3Op
    data class Pal(val label: String) : B3Op
    object Eq : B3Op
    object OpenPal : B3Op
}

internal fun B3Op.show(): String = when (this) {
    is B3Op.Ins -> "Ins(${text.take(24)})"
    B3Op.Frac -> "Frac"; B3Op.Pow -> "Pow"; B3Op.RootK -> "Root"; B3Op.ParenO -> "("
    B3Op.ParenC -> ")"; is B3Op.Fn -> "Fn($name)"; B3Op.Bksp -> "⌫"; B3Op.L -> "←"; B3Op.R -> "→"
    is B3Op.PasteT -> "PasteT(${text.take(18)})"
    B3Op.Copy -> "Copy"; B3Op.Cut -> "Cut"; B3Op.PasteClip -> "PasteClip"
    is B3Op.SelRange -> "Sel[$from,$to)"
    is B3Op.KeyTap -> "Tap($id)"; is B3Op.KeyFlick -> "Flick($id)"; is B3Op.KeySecond -> "2nd($id)"
    is B3Op.Pal -> "Pal($label)"; B3Op.Eq -> "="; B3Op.OpenPal -> "fn"
}

internal data class B3Result(
    val verdict: String,      // PASS | FAIL | PROBE
    val severity: String?,    // CRITICAL | MAJOR | MINOR | null
    val detail: String?,
    val opsRun: Int,
    val finalInput: String?,
)

internal data class B3Case(
    val id: String,
    val axis: String,
    val cls: String,          // FAILABLE | PROBE
    val note: String,
    val ops: List<B3Op>,      // shown in the record; custom runners may go beyond it
    val runner: () -> B3Result,
)

// ---------------------------------------------------------------------------

internal class B3Harness(private val checkRef: Boolean = true) {
    var engine: EditorState = EditorState()
    var ref: EditorState = EditorState()
    private var clip: Clip? = null
    private var refClip: List<Node> = emptyList()

    var crash: String? = null
    var invariant: String? = null
    var contract: String? = null      // pasteText readable-flag mismatch
    var divergenceAt: Int = -1
    var divergence: String? = null
    var meaningDiverged: Boolean = false
    var opsRun: Int = 0

    fun key(id: String): Key = Keypad.basic().key(id) ?: error("no key '$id'")

    /** Apply one op to both models. Returns false when the case must stop. */
    fun apply(op: B3Op): Boolean {
        val before = engine
        val statsBefore = stats(engine.root)
        val selBefore = engine.selection?.let { it.size > 0 } ?: false
        var pasteReadableEngine: Boolean? = null

        engine = try {
            when (op) {
                is B3Op.Ins -> Editor.insert(engine, op.text)
                B3Op.Frac -> Editor.typeFraction(engine)
                B3Op.Pow -> Editor.typePower(engine)
                B3Op.RootK -> Editor.typeRoot(engine)
                B3Op.ParenO -> Editor.typeParen(engine)
                B3Op.ParenC -> Editor.typeCloseParen(engine)
                is B3Op.Fn -> Editor.typeFunc(engine, op.name)
                B3Op.Bksp -> Editor.backspace(engine)
                B3Op.L -> Editor.moveLeft(engine)
                B3Op.R -> Editor.moveRight(engine)
                is B3Op.PasteT -> when (val r = Clipboard.pasteText(engine, op.text)) {
                    is PasteResult.Pasted -> { pasteReadableEngine = true; r.state }
                    is PasteResult.NotReadable -> { pasteReadableEngine = false; engine }
                }
                B3Op.Copy -> { when (val r = Clipboard.copy(engine)) {
                    is CopyResult.Copied -> clip = r.clip
                    CopyResult.NothingSelected -> Unit
                }; engine }
                B3Op.Cut -> when (val r = Clipboard.cut(engine)) {
                    is CutResult.Cut -> { clip = r.clip; r.state }
                    CutResult.NothingSelected -> engine
                }
                B3Op.PasteClip -> clip?.let { Clipboard.paste(engine, it) } ?: engine
                is B3Op.SelRange -> engine.copy(
                    cursor = Cursor(emptyList(), op.to),
                    selection = Selection(emptyList(), op.from, op.to),
                )
                is B3Op.KeyTap -> Keypad.press(engine, key(op.id))
                is B3Op.KeyFlick -> Keypad.flickUp(engine, key(op.id))
                is B3Op.KeySecond -> Keypad.press(engine, key(op.id), Layer.SECOND)
                is B3Op.Pal -> Keypad.apply(engine, FnPalette.entries.first { it.label == op.label })
                B3Op.Eq -> Keypad.apply(engine, KeyAction.Equals)
                B3Op.OpenPal -> Keypad.apply(engine, KeyAction.OpenPalette)
            }
        } catch (t: Throwable) {
            crash = "op#$opsRun ${op.show()} threw ${t::class.java.simpleName}: ${t.message?.take(120)}"
            return false
        }
        opsRun++

        checkInvariants(op, before, statsBefore, selBefore, pasteReadableEngine)
        if (invariant != null) return false

        // The reference model walks the same op from the rule text.
        var pasteReadableRef: Boolean? = null
        ref = try {
            when (op) {
                is B3Op.Ins -> B3Reference.insert(ref, op.text)
                B3Op.Frac -> B3Reference.fraction(ref)
                B3Op.Pow -> B3Reference.power(ref)
                B3Op.RootK -> B3Reference.rootKey(ref)
                B3Op.ParenO -> B3Reference.paren(ref)
                B3Op.ParenC -> B3Reference.closeParen(ref)
                is B3Op.Fn -> B3Reference.func(ref, op.name)
                B3Op.Bksp -> B3Reference.backspace(ref)
                B3Op.L -> B3Reference.left(ref)
                B3Op.R -> B3Reference.right(ref)
                is B3Op.PasteT -> {
                    if (op.text.isBlank()) { pasteReadableRef = true; ref } // blank: unchanged, Pasted
                    else {
                        val fresh = B3Reference.insert(EditorState(), op.text)
                        val readable = Parser.parseProgram(B3Reference.flatten(fresh.root)) is CalcResult.Ok
                        pasteReadableRef = readable
                        if (!readable) ref
                        else {
                            val target = ref.selection?.let { B3Reference.deleteSelection(ref, it) } ?: ref
                            B3Reference.insert(target, op.text)
                        }
                    }
                }
                B3Op.Copy -> { ref.selection?.let { sel ->
                    if (sel.size > 0) refClip = refRowAt(ref, sel).nodes.subList(sel.from, sel.to).toList()
                }; ref }
                B3Op.Cut -> {
                    val sel = ref.selection
                    if (sel == null || sel.size == 0) ref
                    else { refClip = refRowAt(ref, sel).nodes.subList(sel.from, sel.to).toList()
                        B3Reference.deleteSelection(ref, sel) }
                }
                B3Op.PasteClip -> B3Reference.pasteNodes(ref, refClip)
                is B3Op.SelRange -> ref.copy(
                    cursor = Cursor(emptyList(), op.to),
                    selection = Selection(emptyList(), op.from, op.to),
                )
                is B3Op.KeyTap -> refAction(ref, key(op.id).action(Layer.PRIMARY))
                is B3Op.KeyFlick -> key(op.id).flickUp?.let { refAction(ref, it) } ?: ref
                is B3Op.KeySecond -> refAction(ref, key(op.id).action(Layer.SECOND))
                is B3Op.Pal -> refAction(ref, FnPalette.entries.first { it.label == op.label }.insert)
                B3Op.Eq, B3Op.OpenPal -> ref
            }
        } catch (t: Throwable) {
            crash = "REFERENCE op#${opsRun - 1} ${op.show()} threw ${t::class.java.simpleName}: ${t.message?.take(120)}"
            return false
        }

        if (pasteReadableEngine != null && pasteReadableRef != null &&
            pasteReadableEngine != pasteReadableRef && contract == null
        ) {
            contract = "pasteText readable mismatch at op#${opsRun - 1}: engine=$pasteReadableEngine ref=$pasteReadableRef"
        }

        if (checkRef && divergenceAt < 0 && engine != ref) {
            divergenceAt = opsRun - 1
            meaningDiverged = engine.toInput() != ref.toInput()
            divergence = "op#${opsRun - 1} ${op.show()}: engine=<${engine.toInput().take(60)}|c=${cursorShow(engine)}>" +
                " ref=<${ref.toInput().take(60)}|c=${cursorShow(ref)}>" +
                (if (meaningDiverged) " MEANING-DIVERGED" else " (cursor/selection only)")
        }
        // keep ref aligned after first divergence so later steps stay comparable
        if (divergenceAt >= 0) ref = engine
        return true
    }

    fun runOps(ops: List<B3Op>): Boolean {
        for (op in ops) if (!apply(op)) return false
        return true
    }

    // --- invariants ----------------------------------------------------------

    private fun checkInvariants(
        op: B3Op,
        before: EditorState,
        statsBefore: Stats,
        selBefore: Boolean,
        pasteReadable: Boolean?,
    ) {
        validCursor(engine)?.let { invariant = "I-CURSOR after ${op.show()}: $it"; return }
        parenAtom(engine.root)?.let { invariant = "I-PAREN after ${op.show()}: $it"; return }
        val flat = B3Reference.flatten(engine.root)
        val input = engine.root.toInput()
        if (flat != input) { invariant = "I-FLAT after ${op.show()}: toInput=<$input> flatten=<$flat>"; return }

        val b = statsBefore
        val a = stats(engine.root)
        val atomsShrank = a.charCount < b.charCount
        val structsShrank = a.structs < b.structs
        val refsShrank = a.refs < b.refs
        if (!atomsShrank && !structsShrank && !refsShrank) return
        if (selBefore) return // visible: the selection was the confirmation
        if (pasteReadable == false) { // NotReadable must leave the state untouched
            if (engine != before) invariant = "I-SHRINK: NotReadable paste mutated state after ${op.show()}"
            return
        }
        // D-036.2: '#' + digit merging into one Ref legitimately consumes the '#' atom.
        if (a.refs > b.refs && a.charCount >= b.charCount - 1 && a.structs == b.structs) return
        val isBksp = op == B3Op.Bksp || (op is B3Op.KeyTap && op.id == "backspace")
        if (isBksp) {
            val atomDelete = a.charCount == b.charCount - 1 && a.structs == b.structs && a.refs == b.refs
            val collapse = a.chars == b.chars && a.structs == b.structs - 1 && a.refs == b.refs
            val refDelete = a.refs == b.refs - 1 && a.structs == b.structs && a.chars == b.chars // one visible token (D-036.2)
            if (atomDelete || collapse || refDelete) return
            invariant = "I-SHRINK after ⌫: chars ${b.charCount}->${a.charCount} " +
                "structs ${b.structs}->${a.structs} refs ${b.refs}->${a.refs} with no selection"
            return
        }
        invariant = "I-SHRINK after ${op.show()}: content shrank with no visible selection"
    }

    private fun validCursor(s: EditorState): String? {
        fun resolve(path: List<com.vaibhav.calc.core.entry.PathStep>): Pair<Row?, String?> {
            var row = s.root
            for (step in path) {
                if (step.nodeIndex !in row.nodes.indices) return null to "path index ${step.nodeIndex} outside row of ${row.size}"
                val n = row.nodes[step.nodeIndex]
                if (n is Atom) return null to "path descends into an Atom"
                if (step.field !in n.fields()) return null to "field ${step.field} not a field of ${n::class.java.simpleName}"
                row = n.field(step.field)
            }
            return row to null
        }
        val (row, err) = resolve(s.cursor.path)
        if (err != null) return err
        if (s.cursor.offset !in 0..row!!.size) return "offset ${s.cursor.offset} outside 0..${row.size}"
        s.selection?.let { sel ->
            val (srow, serr) = resolve(sel.path)
            if (serr != null) return "selection: $serr"
            if (sel.from !in 0..srow!!.size || sel.to !in 0..srow.size || sel.from > sel.to) {
                return "selection [${sel.from},${sel.to}) outside row of ${srow.size}"
            }
        }
        return null
    }

    private fun parenAtom(row: Row): String? {
        for (n in row.nodes) {
            when (n) {
                is Atom -> if (n.char == '(' || n.char == ')') return "Atom('${n.char}') present"
                else -> for (f in n.fields()) parenAtom(n.field(f))?.let { return it }
            }
        }
        return null
    }

    data class Stats(val chars: Map<Char, Int>, val structs: Int, val refs: Int) {
        val charCount: Int get() = chars.values.sum()
    }

    companion object {
        fun stats(row: Row): Stats {
            val atoms = mutableMapOf<Char, Int>()
            var structs = 0
            var refs = 0
            fun walk(r: Row) {
                for (n in r.nodes) when (n) {
                    is Atom -> atoms.merge(n.char, 1, Int::plus)
                    is com.vaibhav.calc.core.entry.Ref -> refs++
                    else -> { structs++; for (f in n.fields()) walk(n.field(f)) }
                }
            }
            walk(row)
            return Stats(atoms, structs, refs)
        }

        fun count(m: Map<Char, Int>): Int = m.values.sum()

        fun cursorShow(s: EditorState): String =
            s.cursor.path.joinToString("/") { "${it.nodeIndex}.${it.field}" } + ":" + s.cursor.offset +
                (s.selection?.let { "[sel ${it.from},${it.to})" } ?: "")

        fun refRowAt(s: EditorState, sel: Selection): Row {
            var row = s.root
            for (step in sel.path) row = (row.nodes[step.nodeIndex]).field(step.field)
            return row
        }

        fun refAction(s: EditorState, a: KeyAction): EditorState = when (a) {
            KeyAction.Ans -> B3Reference.typeAns(s)
            is KeyAction.Insert -> B3Reference.insert(s, a.text)
            KeyAction.Fraction -> B3Reference.fraction(s)
            KeyAction.Power -> B3Reference.power(s)
            KeyAction.Root -> B3Reference.rootKey(s)
            is KeyAction.Func -> B3Reference.func(s, a.name)
            KeyAction.OpenParen -> B3Reference.paren(s)
            KeyAction.CloseParen -> B3Reference.closeParen(s)
            is KeyAction.Ref -> B3Reference.typeRef(s, a.n)
            KeyAction.Backspace, KeyAction.BackspaceRepeat -> B3Reference.backspace(s)
            KeyAction.Left -> B3Reference.left(s)
            KeyAction.Right -> B3Reference.right(s)
            KeyAction.Equals, KeyAction.OpenPalette -> s
        }
    }

}

// ---------------------------------------------------------------------------
// Case runners
// ---------------------------------------------------------------------------

internal object B3Run {

    private val MC = MathContext(30, RoundingMode.HALF_EVEN)
    private val EVAL_TOL = BigDecimal("1e-9")

    fun refEq(id: String, axis: String, ops: List<B3Op>, cls: String = "FAILABLE", note: String = ""): B3Case =
        B3Case(id, axis, cls, note, ops) {
            val h = B3Harness(checkRef = true)
            h.runOps(ops)
            verdictOf(h, cls)
        }

    fun verdictOf(h: B3Harness, cls: String): B3Result {
        val fin = runCatching { h.engine.toInput() }.getOrNull()
        h.crash?.let { return B3Result("FAIL", "CRITICAL", "CRASH: $it", h.opsRun, fin) }
        h.invariant?.let { return B3Result("FAIL", "CRITICAL", it, h.opsRun, fin) }
        h.contract?.let { return B3Result("FAIL", "MAJOR", it, h.opsRun, fin) }
        if (h.divergenceAt >= 0) {
            return if (cls == "PROBE") B3Result("PROBE", null, "divergence(probe): ${h.divergence}", h.opsRun, fin)
            else B3Result("FAIL", if (h.meaningDiverged) "CRITICAL" else "MAJOR", h.divergence, h.opsRun, fin)
        }
        return if (cls == "PROBE") B3Result("PROBE", null, "observed <$fin> cursor=${B3Harness.cursorShow(h.engine)}", h.opsRun, fin)
        else B3Result("PASS", null, null, h.opsRun, fin)
    }

    /** D-018's axiom: the creating keystroke, then ⌫, restores the pre-state exactly. */
    fun invert(id: String, prefix: List<B3Op>, create: B3Op, note: String = ""): B3Case =
        B3Case(id, "B", "FAILABLE", note, prefix + create + B3Op.Bksp) {
            val h = B3Harness(checkRef = true)
            h.runOps(prefix)
            val snap = h.engine
            h.runOps(listOf(create, B3Op.Bksp))
            h.crash?.let { return@B3Case B3Result("FAIL", "CRITICAL", "CRASH: $it", h.opsRun, null) }
            h.invariant?.let { return@B3Case B3Result("FAIL", "CRITICAL", it, h.opsRun, null) }
            if (h.engine == snap) B3Result("PASS", null, null, h.opsRun, h.engine.toInput())
            else {
                val sa = B3Harness.stats(h.engine.root); val sb = B3Harness.stats(snap.root)
                val sev = if (sa.chars != sb.chars || sa.refs != sb.refs) "CRITICAL" else "MAJOR"
                B3Result("FAIL", sev, "create+⌫ ≠ pre-state: pre=<${snap.toInput()}|${B3Harness.cursorShow(snap)}> " +
                    "post=<${h.engine.toInput()}|${B3Harness.cursorShow(h.engine)}>", h.opsRun, h.engine.toInput())
            }
        }

    /** ⌫ repeated (the hold) must reach the empty state, within the bound. */
    fun fixpoint(id: String, build: List<B3Op>, maxPresses: Int, note: String = "", axis: String = "B"): B3Case =
        B3Case(id, axis, "FAILABLE", note, build) {
            val h = B3Harness(checkRef = true)
            if (!h.runOps(build)) return@B3Case verdictOf(h, "FAILABLE")
            var presses = 0
            while (presses < maxPresses) {
                val prev = h.engine
                if (!h.apply(B3Op.Bksp)) return@B3Case verdictOf(h, "FAILABLE")
                presses++
                if (h.engine == prev) break
            }
            h.divergenceAt.takeIf { it >= 0 }?.let { return@B3Case verdictOf(h, "FAILABLE") }
            if (h.engine == EditorState()) B3Result("PASS", null, "emptied in $presses presses", h.opsRun, "")
            else B3Result("FAIL", "MAJOR", "⌫ fixpoint after $presses presses is not empty: <${h.engine.toInput().take(60)}>",
                h.opsRun, h.engine.toInput())
        }

    fun traversal(id: String, axis: String, build: List<B3Op>, rightward: Boolean, bound: Int, note: String = ""): B3Case =
        B3Case(id, axis, "FAILABLE", note, build) {
            val h = B3Harness(checkRef = true)
            if (!h.runOps(build)) return@B3Case verdictOf(h, "FAILABLE")
            // park at the far end first
            val toStart = if (rightward) B3Op.L else B3Op.R
            var steps = 0
            while (steps < bound) { val p = h.engine; if (!h.apply(toStart)) return@B3Case verdictOf(h, "FAILABLE"); steps++; if (h.engine == p) break }
            if (steps >= bound) return@B3Case B3Result("FAIL", "CRITICAL", "non-termination parking to start ($bound steps)", h.opsRun, null)
            val move = if (rightward) B3Op.R else B3Op.L
            steps = 0
            while (steps < bound) { val p = h.engine; if (!h.apply(move)) return@B3Case verdictOf(h, "FAILABLE"); steps++; if (h.engine == p) break }
            if (steps >= bound) return@B3Case B3Result("FAIL", "CRITICAL", "non-termination traversing ($bound steps)", h.opsRun, null)
            h.divergenceAt.takeIf { it >= 0 }?.let { return@B3Case verdictOf(h, "FAILABLE") }
            B3Result("PASS", null, "traversed in $steps steps", h.opsRun, h.engine.toInput())
        }

    /** →← (or ←→) is an inverse wherever the first move actually moved. */
    fun navInverse(id: String, build: List<B3Op>, rightFirst: Boolean, bound: Int): B3Case =
        B3Case(id, "C", "FAILABLE", "nav inverse", build) {
            val h = B3Harness(checkRef = true)
            if (!h.runOps(build)) return@B3Case verdictOf(h, "FAILABLE")
            try {
                var s = h.engine
                var guard = 0
                while (guard < bound) { val p = Editor.moveLeft(s); if (p == s) break; s = p; guard++ }
                guard = 0
                val bad = StringBuilder()
                while (guard < bound) {
                    val fwd = if (rightFirst) Editor.moveRight(s) else Editor.moveLeft(s)
                    if (fwd == s) break
                    val back = if (rightFirst) Editor.moveLeft(fwd) else Editor.moveRight(fwd)
                    if (back.cursor != s.cursor) {
                        bad.append("at ${B3Harness.cursorShow(s)} -> ${B3Harness.cursorShow(fwd)} -> ${B3Harness.cursorShow(back)}; ")
                    }
                    s = if (rightFirst) Editor.moveRight(s) else Editor.moveLeft(s)
                    guard++
                }
                if (guard >= bound) return@B3Case B3Result("FAIL", "CRITICAL", "non-termination in inverse walk", h.opsRun, null)
                if (bad.isEmpty()) B3Result("PASS", null, null, h.opsRun, h.engine.toInput())
                else B3Result("FAIL", "MAJOR", "inverse broken: ${bad.take(240)}", h.opsRun, h.engine.toInput())
            } catch (t: Throwable) {
                B3Result("FAIL", "CRITICAL", "CRASH in nav walk: ${t::class.java.simpleName}: ${t.message?.take(120)}", h.opsRun, null)
            }
        }

    /** The named harm, tested head-on: the screen reading, hand-computed, vs the engine. */
    fun eval(id: String, ops: List<B3Op>, expected: String, note: String, axis: String = "H"): B3Case =
        B3Case(id, axis, "FAILABLE", note, ops) {
            val h = B3Harness(checkRef = true)
            if (!h.runOps(ops)) return@B3Case verdictOf(h, "FAILABLE")
            h.divergenceAt.takeIf { it >= 0 }?.let { return@B3Case verdictOf(h, "FAILABLE") }
            val input = h.engine.toInput()
            if (!h.engine.isComplete) return@B3Case B3Result("FAIL", "MAJOR", "construction incomplete: <$input>", h.opsRun, input)
            val ans = try { Calculator().evaluateAnswer(input) } catch (t: Throwable) {
                return@B3Case B3Result("FAIL", "CRITICAL", "CRASH evaluating <$input>: ${t::class.java.simpleName}", h.opsRun, input)
            }
            when (ans) {
                is CalcResult.Err -> B3Result("FAIL", "MAJOR",
                    "engine declined a screen-valid entry <$input>: ${ans.error.kind} ${ans.error.message}", h.opsRun, input)
                is CalcResult.Ok -> {
                    val got = Runner.numericOf(ans.value.value)
                        ?: return@B3Case B3Result("FAIL", "MAJOR", "<$input> returned non-real ${ans.value.exact}", h.opsRun, input)
                    val exp = BigDecimal(expected)
                    val diff = got.subtract(exp, MC).abs()
                    val ok = if (exp.compareTo(BigDecimal.ZERO) == 0) diff <= EVAL_TOL
                    else diff.divide(exp.abs(), MC) <= EVAL_TOL
                    if (ok) B3Result("PASS", null, "<$input> = ${ans.value.decimal}", h.opsRun, input)
                    else B3Result("FAIL", "CRITICAL",
                        "SCREEN-VS-EVALUATED: screen reads $expected, engine computed ${got.toPlainString()} for <$input>",
                        h.opsRun, input)
                }
            }
        }

    fun identity(id: String, axis: String, build: List<B3Op>, probe: B3Op, note: String): B3Case =
        B3Case(id, axis, "FAILABLE", note, build + probe) {
            val h = B3Harness(checkRef = true)
            if (!h.runOps(build)) return@B3Case verdictOf(h, "FAILABLE")
            val snap = h.engine
            if (!h.apply(probe)) return@B3Case verdictOf(h, "FAILABLE")
            if (h.engine == snap) B3Result("PASS", null, null, h.opsRun, h.engine.toInput())
            else B3Result("FAIL", "MAJOR", "${probe.show()} was not an identity: " +
                "before=<${snap.toInput()}|${B3Harness.cursorShow(snap)}> after=<${h.engine.toInput()}|${B3Harness.cursorShow(h.engine)}>",
                h.opsRun, h.engine.toInput())
        }

    fun custom(id: String, axis: String, cls: String, note: String, ops: List<B3Op> = emptyList(), body: () -> B3Result): B3Case =
        B3Case(id, axis, cls, note, ops, body)
}

internal object B3Emit {
    fun write(file: File, rows: List<Pair<B3Case, B3Result>>) {
        file.parentFile?.mkdirs()
        val sb = StringBuilder("[\n")
        rows.forEachIndexed { i, (c, r) ->
            sb.append("  {")
            sb.append(MiniJson.field("id", c.id)).append(',')
            sb.append(MiniJson.field("axis", c.axis)).append(',')
            sb.append(MiniJson.field("class", c.cls)).append(',')
            sb.append(MiniJson.field("note", c.note)).append(',')
            sb.append(MiniJson.field("ops", c.ops.joinToString(" ") { it.show() }.take(400))).append(',')
            sb.append(MiniJson.field("verdict", r.verdict)).append(',')
            sb.append(MiniJson.field("severity", r.severity)).append(',')
            sb.append(MiniJson.field("detail", r.detail)).append(',')
            sb.append(MiniJson.rawField("opsRun", r.opsRun.toString())).append(',')
            sb.append(MiniJson.field("finalInput", r.finalInput?.take(200)))
            sb.append('}')
            if (i != rows.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append("]\n")
        file.writeText(sb.toString(), Charsets.UTF_8)
    }
}
