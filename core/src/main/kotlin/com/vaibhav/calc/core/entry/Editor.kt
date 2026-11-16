package com.vaibhav.calc.core.entry

/**
 * The editing operations for 2D natural entry (spec 11.2, 1.9). Pure functions on
 * an immutable [EditorState].
 *
 * Three rules govern deletion (D-018, D-019):
 *  1. **Backspace inverts structure creation** — one keystroke made the structure,
 *     one keystroke unmakes it, one level, never cascading.
 *  2. **Collapse never destroys content** — which forces the corollary that collapse
 *     can only trigger from an *empty* field: the other side is what survives.
 *  3. **Destruction requires visibility** — a structure is only ever destroyed while
 *     visibly SELECTED. The first ⌫ selects, the second deletes.
 */
object Editor {

    // --- typing --------------------------------------------------------------

    /**
     * Insert one character.
     *
     * **Parentheses route to the paren structure, not to an atom.** `Node.kt` states
     * that unbalanced parens are unrepresentable; that claim was **false** while this
     * function made an `Atom('(')` — one concept with two implementations (Article 12),
     * and the weaker one reachable from every function key, which is how
     * `Keypad.press(sin)` came to produce the unparseable `sin(`. Typing `(` and
     * pressing the `(` key are the same act, so they are the same code.
     */
    fun insert(state: EditorState, char: Char): EditorState = when (char) {
        '(' -> typeParen(state)
        ')' -> typeCloseParen(state)
        else -> insertAtom(state, char)
    }

    private fun insertAtom(state: EditorState, char: Char): EditorState {
        val s = deleteSelectionIfAny(state)
        val row = s.root.rowAt(s.cursor.path)
        val nodes = row.nodes.toMutableList()

        // D-036.2: '#' followed by digits IS a reference, so typed/pasted text builds
        // the same atomic node the badge builds — two trees for one linearisation
        // would be the FM-1 divergence by construction.
        if (char.isDigit() && s.cursor.offset > 0) {
            when (val prev = nodes[s.cursor.offset - 1]) {
                is Atom -> if (prev.char == '#') {
                    nodes[s.cursor.offset - 1] = Ref(char.digitToInt())
                    return s.copy(
                        root = s.root.withRowAt(s.cursor.path, Row(nodes)),
                        cursor = s.cursor, // one node replaced one node
                        selection = null,
                    )
                }
                is Ref -> {
                    val extended = prev.n * 10L + char.digitToInt()
                    if (extended <= Int.MAX_VALUE) {
                        nodes[s.cursor.offset - 1] = Ref(extended.toInt())
                        return s.copy(
                            root = s.root.withRowAt(s.cursor.path, Row(nodes)),
                            cursor = s.cursor,
                            selection = null,
                        )
                    }
                }
                else -> Unit
            }
        }

        nodes.add(s.cursor.offset, Atom(char))
        return s.copy(
            root = s.root.withRowAt(s.cursor.path, Row(nodes)),
            cursor = s.cursor.copy(offset = s.cursor.offset + 1),
            selection = null,
        )
    }

    /**
     * The D-031.7 badge — a tape reference as ONE node (D-036.2), the same axiom
     * [typeFunc] carries: one keystroke, one node, one ⌫ to remove it whole.
     */
    fun typeRef(state: EditorState, n: Int): EditorState {
        val s = deleteSelectionIfAny(state)
        val row = s.root.rowAt(s.cursor.path)
        val nodes = row.nodes.toMutableList()
        nodes.add(s.cursor.offset, Ref(n))
        return s.copy(
            root = s.root.withRowAt(s.cursor.path, Row(nodes)),
            cursor = s.cursor.copy(offset = s.cursor.offset + 1),
            selection = null,
        )
    }

    fun insert(state: EditorState, text: String): EditorState =
        text.fold(state) { acc, c -> insert(acc, c) }

    fun typeAns(state: EditorState): EditorState {
        val s = deleteSelectionIfAny(state)
        val row = s.root.rowAt(s.cursor.path)
        val nodes = row.nodes.toMutableList().apply { add(s.cursor.offset, Ans) }
        return s.copy(root = s.root.withRowAt(s.cursor.path, Row(nodes)),
            cursor = s.cursor.copy(offset = s.cursor.offset + 1), selection = null)
    }

    /** `/` — the trailing operand becomes the numerator; the cursor lands in the denominator. */
    fun typeFraction(state: EditorState): EditorState =
        wrapTrailingOperand(state, Field.NUMERATOR, Field.DENOMINATOR) { run ->
            Fraction(Row(run), Row())
        }

    /** `^` — the trailing operand becomes the base; the cursor lands in the exponent. */
    fun typePower(state: EditorState): EditorState =
        wrapTrailingOperand(state, Field.BASE, Field.EXPONENT) { run -> Power(Row(run), Row()) }

    /** `√` — a prefix structure; the cursor lands in the radicand. */
    fun typeRoot(state: EditorState): EditorState =
        insertStructure(state, Root(null, Row()), Field.RADICAND)

    /** `(` — a balanced structure; the cursor lands inside. */
    fun typeParen(state: EditorState): EditorState =
        insertStructure(state, Paren(Row()), Field.BODY)

    /**
     * A function key — ONE keystroke, one node, cursor inside the argument (D-034).
     *
     * [name] is the ENGINE's spelling (`asin`), never the display form (`sin⁻¹`).
     * Before this existed, `sin` was inserted as the text `sin(`, which produced four
     * atoms and an unbalanced paren — four backspaces to undo one press, breaking
     * D-018's axiom outright.
     */
    fun typeFunc(state: EditorState, name: String): EditorState =
        insertStructure(state, Func(name, Row()), Field.ARG)

    /**
     * `)` — exits the innermost paren body OR function argument rather than
     * inserting an unbalanced atom.
     *
     * ARG joined BODY by D-036.4: `sin(30` then `)` doing nothing was a dead
     * keypress — the exact shape D-019 killed for ⌫ — and every physical
     * calculator taught the closing paren to end the call. Other fields
     * (numerator, exponent, radicand…) are not typed with parens anywhere, so
     * `)` stays a no-op there: no muscle memory expects it to close them.
     */
    fun typeCloseParen(state: EditorState): EditorState {
        val step = state.cursor.path.lastOrNull() ?: return state
        if (step.field != Field.BODY && step.field != Field.ARG) return state
        return state.copy(
            cursor = Cursor(state.cursor.path.dropLast(1), step.nodeIndex + 1),
            selection = null,
        )
    }

    // --- deletion (the case table) -------------------------------------------

    fun backspace(state: EditorState): EditorState {
        // Rule 3: a selection is a confirmation — this press destroys it.
        state.selection?.let { return deleteSelection(state, it) }

        val row = state.root.rowAt(state.cursor.path)

        if (state.cursor.offset > 0) {
            val previous = row.nodes[state.cursor.offset - 1]
            // F1/F7: an atom dies immediately — it is one visible character. A Ref
            // dies whole the same way (D-036.2): it is one visible token, and one
            // keystroke created it.
            if (previous.fields().isEmpty()) return deleteNodeAt(state, state.cursor.offset - 1)
            // Rule 3: a structure never dies unseen. Select it; the next ⌫ deletes it.
            return select(state, state.cursor.path, state.cursor.offset - 1)
        }

        // offset == 0 — at the start of a row.
        val step = state.cursor.path.lastOrNull()
            ?: return state // start of the root: genuinely nothing to delete
        val parentPath = state.cursor.path.dropLast(1)
        val structure = state.root.rowAt(parentPath).nodes[step.nodeIndex]

        return if (row.isEmpty) {
            collapse(state, parentPath, step, structure) // F2 / F3 / F4
        } else {
            // F5 / F6 as revised by D-019. Exit-left would be a DEAD KEYPRESS: the
            // user sees nothing happen, presses ⌫ again, and destroys whatever sits
            // before the structure — something they were not looking at. Selecting
            // makes the first press visible and shows what dies before it dies.
            select(state, parentPath, step.nodeIndex)
        }
    }

    /** Not a keypad key (D-018) — used on commit. ⌫-hold repeat-delete is just [backspace] repeated. */
    fun clear(state: EditorState): EditorState = EditorState()

    // --- navigation (retraces creation order) --------------------------------

    fun moveRight(state: EditorState): EditorState {
        val s = state.copy(selection = null) // any navigation deselects (D-019)
        val row = s.root.rowAt(s.cursor.path)
        if (s.cursor.offset < row.size) {
            val next = row.nodes[s.cursor.offset]
            // A Ref is a leaf like an Atom: one step crosses it (D-036.2).
            if (next.fields().isEmpty()) return s.copy(cursor = s.cursor.copy(offset = s.cursor.offset + 1))
            // Descend into the structure's FIRST field — creation order.
            return s.copy(cursor = Cursor(s.cursor.path + PathStep(s.cursor.offset, next.fields().first()), 0))
        }
        val step = s.cursor.path.lastOrNull() ?: return s
        val parentPath = s.cursor.path.dropLast(1)
        val structure = s.root.rowAt(parentPath).nodes[step.nodeIndex]
        val fields = structure.fields()
        val i = fields.indexOf(step.field)
        // End of a field → the NEXT field (numerator → denominator), else exit right.
        return if (i < fields.size - 1) {
            s.copy(cursor = Cursor(parentPath + PathStep(step.nodeIndex, fields[i + 1]), 0))
        } else {
            s.copy(cursor = Cursor(parentPath, step.nodeIndex + 1))
        }
    }

    fun moveLeft(state: EditorState): EditorState {
        val s = state.copy(selection = null)
        val row = s.root.rowAt(s.cursor.path)
        if (s.cursor.offset > 0) {
            val previous = row.nodes[s.cursor.offset - 1]
            if (previous.fields().isEmpty()) return s.copy(cursor = s.cursor.copy(offset = s.cursor.offset - 1))
            val lastField = previous.fields().last()
            val inner = previous.field(lastField)
            return s.copy(cursor = Cursor(s.cursor.path + PathStep(s.cursor.offset - 1, lastField), inner.size))
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

    // --- selection (Rule 3 + clipboard) --------------------------------------

    fun select(state: EditorState, path: List<PathStep>, nodeIndex: Int): EditorState =
        state.copy(
            cursor = Cursor(path, nodeIndex + 1),
            selection = Selection(path, nodeIndex, nodeIndex + 1),
        )

    fun deleteSelection(state: EditorState, sel: Selection): EditorState {
        val row = state.root.rowAt(sel.path)
        val nodes = row.nodes.toMutableList()
        repeat(sel.size) { nodes.removeAt(sel.from) }
        return state.copy(
            root = state.root.withRowAt(sel.path, Row(nodes)),
            cursor = Cursor(sel.path, sel.from),
            selection = null,
        )
    }

    /** The selected nodes — what the clipboard copies (11.7). */
    fun selectedNodes(state: EditorState): List<Node> {
        val sel = state.selection ?: return emptyList()
        return state.root.rowAt(sel.path).nodes.subList(sel.from, sel.to).toList()
    }

    // --- internals -----------------------------------------------------------

    private fun deleteSelectionIfAny(state: EditorState): EditorState =
        state.selection?.let { deleteSelection(state, it) } ?: state

    private fun deleteNodeAt(state: EditorState, index: Int): EditorState {
        val row = state.root.rowAt(state.cursor.path)
        val nodes = row.nodes.toMutableList()
        nodes.removeAt(index)
        return state.copy(
            root = state.root.withRowAt(state.cursor.path, Row(nodes)),
            cursor = state.cursor.copy(offset = index),
            selection = null,
        )
    }

    /**
     * Collapse: remove the structure, splice the surviving field(s) inline.
     *
     * The cursor lands as the exact inverse of the creating keystroke: collapsing
     * from the FIRST field leaves it where the structure began; collapsing from a
     * LATER field leaves it after the survivors — i.e. where the `/` was when it
     * was typed.
     */
    private fun collapse(
        state: EditorState,
        parentPath: List<PathStep>,
        step: PathStep,
        structure: Node,
    ): EditorState {
        val surviving = structure.survivingContent(step.field)
        val parentRow = state.root.rowAt(parentPath)
        val nodes = parentRow.nodes.toMutableList()
        nodes.removeAt(step.nodeIndex)
        nodes.addAll(step.nodeIndex, surviving)

        val fieldIndex = structure.fields().indexOf(step.field)
        val offset = if (fieldIndex == 0) step.nodeIndex else step.nodeIndex + surviving.size

        return state.copy(
            root = state.root.withRowAt(parentPath, Row(nodes)),
            cursor = Cursor(parentPath, offset),
            selection = null,
        )
    }

    private fun insertStructure(state: EditorState, node: Node, into: Field): EditorState {
        val s = deleteSelectionIfAny(state)
        val row = s.root.rowAt(s.cursor.path)
        val nodes = row.nodes.toMutableList()
        nodes.add(s.cursor.offset, node)
        return s.copy(
            root = s.root.withRowAt(s.cursor.path, Row(nodes)),
            cursor = Cursor(s.cursor.path + PathStep(s.cursor.offset, into), 0),
            selection = null,
        )
    }

    /**
     * `/` and `^` capture the trailing operand — so `1+2/` gives `1 + (2/…)`, not
     * `(1+2)/…`, matching how a Casio's fraction key behaves.
     */
    private fun wrapTrailingOperand(
        state: EditorState,
        firstField: Field,
        secondField: Field,
        build: (List<Node>) -> Node,
    ): EditorState {
        val s = deleteSelectionIfAny(state)
        val row = s.root.rowAt(s.cursor.path)
        val start = trailingOperandStart(row, s.cursor.offset)
        val run = row.nodes.subList(start, s.cursor.offset).toList()

        val nodes = row.nodes.toMutableList()
        repeat(run.size) { nodes.removeAt(start) }
        nodes.add(start, build(run))

        // With no operand to capture, the cursor goes to the FIRST field so the user
        // can type it; otherwise to the second, exactly where typing `/` leaves you.
        val target = if (run.isEmpty()) firstField else secondField
        return s.copy(
            root = s.root.withRowAt(s.cursor.path, Row(nodes)),
            cursor = Cursor(s.cursor.path + PathStep(start, target), 0),
            selection = null,
        )
    }

    private fun trailingOperandStart(row: Row, offset: Int): Int {
        if (offset == 0) return 0
        val previous = row.nodes[offset - 1]
        if (previous !is Atom) return offset - 1 // a whole structure is one operand
        if (!previous.char.isOperandChar()) return offset // an operator: no operand to capture
        var i = offset
        while (i > 0) {
            val n = row.nodes[i - 1]
            if (n is Atom && n.char.isOperandChar()) i-- else break
        }
        return i
    }

    private fun Char.isOperandChar(): Boolean = isLetterOrDigit() || this == '.'
}
