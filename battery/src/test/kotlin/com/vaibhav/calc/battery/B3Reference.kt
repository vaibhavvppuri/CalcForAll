package com.vaibhav.calc.battery

import com.vaibhav.calc.core.ast.Binary
import com.vaibhav.calc.core.ast.Equation
import com.vaibhav.calc.core.ast.Expr
import com.vaibhav.calc.core.ast.ExprStatement
import com.vaibhav.calc.core.ast.Unary
import com.vaibhav.calc.core.ast.UnitApply
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.entry.Atom
import com.vaibhav.calc.core.entry.Ans
import com.vaibhav.calc.core.entry.Cursor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.entry.Field
import com.vaibhav.calc.core.entry.Fraction
import com.vaibhav.calc.core.entry.Func
import com.vaibhav.calc.core.entry.Node
import com.vaibhav.calc.core.entry.Paren
import com.vaibhav.calc.core.entry.PathStep
import com.vaibhav.calc.core.entry.Power
import com.vaibhav.calc.core.entry.Ref
import com.vaibhav.calc.core.entry.Root
import com.vaibhav.calc.core.entry.Row
import com.vaibhav.calc.core.entry.Selection
import com.vaibhav.calc.core.entry.field
import com.vaibhav.calc.core.entry.fields
import com.vaibhav.calc.core.entry.rowAt
import com.vaibhav.calc.core.entry.toInput
import com.vaibhav.calc.core.entry.withRowAt
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.parse.Parser

/**
 * B3's REFERENCE MODEL — a second, independent implementation of the editing rules,
 * written from the RULE TEXT (D-018, D-019, D-034, DESIGN-HANDOFF-M7-2 §2.1/§4.4),
 * not from `Editor.kt`. It shares the DATA types with core (so states compare by
 * data equality) but none of the OPERATION code.
 *
 * Where the decisions are silent, this model mirrors observed engine behaviour and
 * the battery marks the case PROBE — an observation for an owner ruling, never a
 * PASS. The known silences, stated so they cannot masquerade as law:
 *
 *  - S1 (RESOLVED by D-036.4): `)` exits a BODY or a Func ARG; it stays a no-op
 *    inside NUM / DEN / EXP / RAD, which are never typed with parens.
 *  - S2: `/`-and-`^` capture of a preceding STRUCTURE as the one-operand run.
 *  - S3: a structure key pressed over a SELECTION deletes the selection first,
 *    then captures from what remains before the cursor.
 *  - S4: `Root` created by the editor always has a null index; `root(x,n)` is
 *    reachable only as a `Func` through the palette.
 */
internal object B3Reference {

    // --- rule: one keystroke, one edit ---------------------------------------

    fun insert(s: EditorState, c: Char): EditorState = when (c) {
        '(' -> paren(s)
        ')' -> closeParen(s)
        else -> atom(s, c)
    }

    fun insert(s: EditorState, text: String): EditorState = text.fold(s) { acc, ch -> insert(acc, ch) }

    private fun atom(s0: EditorState, c: Char): EditorState {
        val s = clearSelection(s0)
        val row = s.root.rowAt(s.cursor.path)
        val nodes = row.nodes.toMutableList()
        // D-036.2: '#' + digits IS a reference — the text and the badge build one node.
        if (c.isDigit() && s.cursor.offset > 0) {
            when (val prev = nodes[s.cursor.offset - 1]) {
                is Atom -> if (prev.char == '#') {
                    nodes[s.cursor.offset - 1] = Ref(c.digitToInt())
                    return s.copy(root = s.root.withRowAt(s.cursor.path, Row(nodes)), cursor = s.cursor, selection = null)
                }
                is Ref -> {
                    val ext = prev.n * 10L + c.digitToInt()
                    if (ext <= Int.MAX_VALUE) {
                        nodes[s.cursor.offset - 1] = Ref(ext.toInt())
                        return s.copy(root = s.root.withRowAt(s.cursor.path, Row(nodes)), cursor = s.cursor, selection = null)
                    }
                }
                else -> Unit
            }
        }
        nodes.add(s.cursor.offset, Atom(c))
        return s.copy(
            root = s.root.withRowAt(s.cursor.path, Row(nodes)),
            cursor = s.cursor.copy(offset = s.cursor.offset + 1),
            selection = null,
        )
    }

    fun typeRef(s0: EditorState, n: Int): EditorState {
        val s = clearSelection(s0)
        val row = s.root.rowAt(s.cursor.path)
        val nodes = row.nodes.toMutableList().also { it.add(s.cursor.offset, Ref(n)) }
        return s.copy(
            root = s.root.withRowAt(s.cursor.path, Row(nodes)),
            cursor = s.cursor.copy(offset = s.cursor.offset + 1),
            selection = null,
        )
    }

    /** D-037: Ans is one indivisible leaf; no production Editor operation used. */
    fun typeAns(s0: EditorState): EditorState {
        val s = clearSelection(s0)
        val row = s.root.rowAt(s.cursor.path)
        val nodes = row.nodes.toMutableList().also { it.add(s.cursor.offset, Ans) }
        return s.copy(root = s.root.withRowAt(s.cursor.path, Row(nodes)),
            cursor = s.cursor.copy(offset = s.cursor.offset + 1), selection = null)
    }

    fun rootKey(s: EditorState): EditorState = structure(s, Root(null, Row()), Field.RADICAND)
    fun paren(s: EditorState): EditorState = structure(s, Paren(Row()), Field.BODY)
    fun func(s: EditorState, name: String): EditorState = structure(s, Func(name, Row()), Field.ARG)

    private fun structure(s0: EditorState, node: Node, into: Field): EditorState {
        val s = clearSelection(s0)
        val row = s.root.rowAt(s.cursor.path)
        val nodes = row.nodes.toMutableList().also { it.add(s.cursor.offset, node) }
        return s.copy(
            root = s.root.withRowAt(s.cursor.path, Row(nodes)),
            cursor = Cursor(s.cursor.path + PathStep(s.cursor.offset, into), 0),
            selection = null,
        )
    }

    fun fraction(s: EditorState): EditorState =
        wrapTrailing(s, Field.NUMERATOR, Field.DENOMINATOR) { run -> Fraction(Row(run), Row()) }

    fun power(s: EditorState): EditorState =
        wrapTrailing(s, Field.BASE, Field.EXPONENT, wholeNumberWords = true) { run -> Power(Row(run), Row()) }

    /** Casio parity: the fraction/power key captures the TRAILING OPERAND only. */
    private fun wrapTrailing(
        s0: EditorState,
        firstField: Field,
        secondField: Field,
        wholeNumberWords: Boolean = false,
        build: (List<Node>) -> Node,
    ): EditorState {
        val s = clearSelection(s0) // S3
        val row = s.root.rowAt(s.cursor.path)
        var start = trailingStart(row, s.cursor.offset)
        // DECISIONS, 1.3: a number word is part of the number in front of it, so the power key
        // takes the word together with its operand — and the operand is whatever the PARSER binds
        // to the word (√4 in `√4 lakh`, 5! in `5! lakh`). Written from that rule text, not from
        // Editor.kt: the reference asks the parser for the unit application that ends the text.
        if (wholeNumberWords && spellsNumberWord(row.nodes.subList(start, s.cursor.offset))) {
            val leading = row.nodes.subList(0, s.cursor.offset)
            val text = leading.joinToString("") { it.toInput() }
            // The entry may be a statement (`x = 5 lakh`): its last statement's trailing
            // expression is what ends the text.
            val last = (Parser.parseProgram(text) as? CalcResult.Ok)?.value?.statements?.lastOrNull()
            var node: Expr? = when (last) {
                is ExprStatement -> last.expr
                is Equation -> last.rhs
                else -> null
            }
            while (node != null && !(node is UnitApply && node.span.end == text.length)) {
                node = when (node) {
                    is Binary -> node.right
                    is Unary -> node.operand
                    else -> null
                }
            }
            if (node != null) {
                // The operand begins in the node whose text contains that position: a bracket
                // group's own span starts one character inside its bracket.
                var position = 0
                for (index in leading.indices) {
                    val next = position + leading[index].toInput().length
                    if (node.span.start < next) { start = index; break }
                    position = next
                }
            } else {
                // The text is not a complete entry: the word, the spaces before it, and the
                // operand run or structure before those (the plain S2 capture, repeated).
                var end = s.cursor.offset
                while (start < end && spellsNumberWord(row.nodes.subList(start, end))) {
                    var before = start
                    while (before > 0 && (row.nodes[before - 1] as? Atom)?.char == ' ') before--
                    val operand = trailingStart(row, before)
                    if (operand == before) break
                    end = before
                    start = operand
                }
            }
        }
        val run = row.nodes.subList(start, s.cursor.offset).toList()
        val nodes = row.nodes.toMutableList()
        repeat(run.size) { nodes.removeAt(start) }
        nodes.add(start, build(run))
        val target = if (run.isEmpty()) firstField else secondField
        return s.copy(
            root = s.root.withRowAt(s.cursor.path, Row(nodes)),
            cursor = Cursor(s.cursor.path + PathStep(start, target), 0),
            selection = null,
        )
    }

    private fun trailingStart(row: Row, offset: Int): Int {
        if (offset == 0) return 0
        val prev = row.nodes[offset - 1]
        if (prev !is Atom) return offset - 1 // S2: one whole structure is one operand
        if (!operandChar(prev.char)) return offset // an operator captures nothing
        var i = offset
        while (i > 0) {
            val n = row.nodes[i - 1]
            if (n is Atom && operandChar(n.char)) i-- else break
        }
        return i
    }

    private fun operandChar(c: Char): Boolean = c.isLetterOrDigit() || c == '.'

    private fun spellsNumberWord(nodes: List<Node>): Boolean =
        nodes.all { it is Atom } && ReservedNames.isNumberScale(nodes.joinToString("") { (it as Atom).char.toString() })

    fun closeParen(s: EditorState): EditorState {
        val step = s.cursor.path.lastOrNull() ?: return s
        // D-036.4 resolved S1: `)` exits a BODY or an ARG; other fields stay no-ops.
        if (step.field != Field.BODY && step.field != Field.ARG) return s
        return s.copy(cursor = Cursor(s.cursor.path.dropLast(1), step.nodeIndex + 1), selection = null)
    }

    // --- rule: the three deletion laws (D-018 / D-019) -----------------------

    fun backspace(s: EditorState): EditorState {
        s.selection?.let { return deleteSelection(s, it) } // Rule 3: this press is the confirmation
        val row = s.root.rowAt(s.cursor.path)
        if (s.cursor.offset > 0) {
            val prev = row.nodes[s.cursor.offset - 1]
            if (prev is Atom || prev is Ref || prev == Ans) { // D-036.2/D-037: atomic visible token
                val nodes = row.nodes.toMutableList().also { it.removeAt(s.cursor.offset - 1) }
                return s.copy(
                    root = s.root.withRowAt(s.cursor.path, Row(nodes)),
                    cursor = s.cursor.copy(offset = s.cursor.offset - 1),
                    selection = null,
                )
            }
            return select(s, s.cursor.path, s.cursor.offset - 1) // Rule 3
        }
        val step = s.cursor.path.lastOrNull() ?: return s // start of root: nothing to delete
        val parentPath = s.cursor.path.dropLast(1)
        val structure = s.root.rowAt(parentPath).nodes[step.nodeIndex]
        return if (row.isEmpty) collapse(s, parentPath, step, structure)
        else select(s, parentPath, step.nodeIndex) // F5/F6 as revised by D-019
    }

    fun select(s: EditorState, path: List<PathStep>, k: Int): EditorState =
        s.copy(cursor = Cursor(path, k + 1), selection = Selection(path, k, k + 1))

    fun deleteSelection(s: EditorState, sel: Selection): EditorState {
        val row = s.root.rowAt(sel.path)
        val nodes = row.nodes.toMutableList()
        repeat(sel.size) { nodes.removeAt(sel.from) }
        return s.copy(
            root = s.root.withRowAt(sel.path, Row(nodes)),
            cursor = Cursor(sel.path, sel.from),
            selection = null,
        )
    }

    private fun clearSelection(s: EditorState): EditorState =
        s.selection?.let { deleteSelection(s, it) } ?: s

    /** Rule 2: collapse never destroys content — the other field(s) survive inline. */
    private fun collapse(
        s: EditorState,
        parentPath: List<PathStep>,
        step: PathStep,
        structure: Node,
    ): EditorState {
        val surviving = structure.fields().filter { it != step.field }.flatMap { structure.field(it).nodes }
        val parentRow = s.root.rowAt(parentPath)
        val nodes = parentRow.nodes.toMutableList()
        nodes.removeAt(step.nodeIndex)
        nodes.addAll(step.nodeIndex, surviving)
        // The exact inverse of the creating keystroke: first field -> the structure's
        // slot; a later field -> after the survivors, where the / was typed.
        val offset = if (structure.fields().indexOf(step.field) == 0) step.nodeIndex
        else step.nodeIndex + surviving.size
        return s.copy(
            root = s.root.withRowAt(parentPath, Row(nodes)),
            cursor = Cursor(parentPath, offset),
            selection = null,
        )
    }

    // --- rule: navigation retraces creation order; any navigation deselects ---

    fun right(s0: EditorState): EditorState {
        val s = s0.copy(selection = null)
        val row = s.root.rowAt(s.cursor.path)
        if (s.cursor.offset < row.size) {
            val next = row.nodes[s.cursor.offset]
            if (next is Atom || next is Ref || next == Ans) return s.copy(cursor = s.cursor.copy(offset = s.cursor.offset + 1))
            return s.copy(cursor = Cursor(s.cursor.path + PathStep(s.cursor.offset, next.fields().first()), 0))
        }
        val step = s.cursor.path.lastOrNull() ?: return s
        val parentPath = s.cursor.path.dropLast(1)
        val structure = s.root.rowAt(parentPath).nodes[step.nodeIndex]
        val fields = structure.fields()
        val i = fields.indexOf(step.field)
        return if (i < fields.size - 1) {
            s.copy(cursor = Cursor(parentPath + PathStep(step.nodeIndex, fields[i + 1]), 0))
        } else {
            s.copy(cursor = Cursor(parentPath, step.nodeIndex + 1))
        }
    }

    fun left(s0: EditorState): EditorState {
        val s = s0.copy(selection = null)
        val row = s.root.rowAt(s.cursor.path)
        if (s.cursor.offset > 0) {
            val prev = row.nodes[s.cursor.offset - 1]
            if (prev is Atom || prev is Ref || prev == Ans) return s.copy(cursor = s.cursor.copy(offset = s.cursor.offset - 1))
            val lastField = prev.fields().last()
            return s.copy(
                cursor = Cursor(
                    s.cursor.path + PathStep(s.cursor.offset - 1, lastField),
                    prev.field(lastField).size,
                ),
            )
        }
        val step = s.cursor.path.lastOrNull() ?: return s
        val parentPath = s.cursor.path.dropLast(1)
        val structure = s.root.rowAt(parentPath).nodes[step.nodeIndex]
        val fields = structure.fields()
        val i = fields.indexOf(step.field)
        return if (i > 0) {
            val prevField = fields[i - 1]
            s.copy(cursor = Cursor(parentPath + PathStep(step.nodeIndex, prevField), structure.field(prevField).size))
        } else {
            s.copy(cursor = Cursor(parentPath, step.nodeIndex))
        }
    }

    // --- clipboard semantics (11.7 on the Selection substrate) ---------------

    fun pasteNodes(s0: EditorState, clip: List<Node>): EditorState {
        if (clip.isEmpty()) return s0
        val s = clearSelection(s0)
        val row = s.root.rowAt(s.cursor.path)
        val nodes = row.nodes.toMutableList().also { it.addAll(s.cursor.offset, clip) }
        return s.copy(
            root = s.root.withRowAt(s.cursor.path, Row(nodes)),
            cursor = Cursor(s.cursor.path, s.cursor.offset + clip.size),
            selection = null,
        )
    }

    // --- the independent linearizer (design flatten, handoff §4.4) -----------

    /**
     * The screen's mathematical reading, linearised — written from the design's
     * `flatten`, independently of `Node.toInput()`. Agreement between the two is the
     * FM-1 tripwire: if they ever diverge, the expression evaluated is not the
     * expression drawn.
     *
     * Amended in 1.3 (DECISIONS, eighth pass): a fraction and a power carry an outer pair
     * of brackets. The handoff's flatten wrote `(n)/(d)` and `(b)^(e)`, which is the screen's
     * reading only while nothing follows the structure: the screen draws ½ followed by
     * `lakh` as half a lakh, and `(1)/(2) lakh` reads as 1/(2 lakh).
     */
    fun flatten(row: Row): String = row.nodes.joinToString("") { flatten(it) }

    private fun flatten(n: Node): String = when (n) {
        com.vaibhav.calc.core.entry.Ans -> "ans"
        is Atom -> n.char.toString()
        is Ref -> "#${n.n}"
        is Fraction -> "((${flatten(n.numerator)})/(${flatten(n.denominator)}))"
        is Power -> "((${flatten(n.base)})^(${flatten(n.exponent)}))"
        is Root -> if (n.index == null) "sqrt(${flatten(n.radicand)})"
        else "root((${flatten(n.radicand)}),(${flatten(n.index!!)}))"
        is Paren -> "(${flatten(n.body)})"
        is Func -> "${n.name}(${flatten(n.arg)})" // the ENGINE spelling, never the display form
    }
}
