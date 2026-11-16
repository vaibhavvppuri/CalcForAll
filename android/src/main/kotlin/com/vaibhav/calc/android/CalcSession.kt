package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.entry.*
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.format.OutcomeFormatter
import com.vaibhav.calc.core.keypad.*
import com.vaibhav.calc.core.tape.*
import com.vaibhav.calc.core.route.Outcome

data class CalcUiState(
    val editor: EditorState = EditorState(),
    val exact: String? = null,
    val decimal: String? = null,
    val error: String? = null,
    val angle: AngleMode = AngleMode.DEG,
    val sdVisible: Boolean = false,
    val entries: List<TapeEntry> = emptyList(),
    val editingId: EntryId? = null,
    val notice: String? = null,
    val noticeUndo: Boolean = false,
    val steps: List<String> = emptyList(),
    val committed: Boolean = false,
    val clipboardResult: String? = null,
    val approximate: Boolean = false,
) {
    val entryText: String get() = editor.toInput()
}

/** A rejected replacement leaves the prior expression intact and exposes its reason. */
data class TextReplacement(val accepted: Boolean, val state: CalcUiState)

/** UI events use the structural editor and transactional tape. Calls are serialized by the UI. */
class CalcSession(private val calculator: Calculator = Calculator()) {
    private val tape = Tape(calculator.settings)
    private val undoEdits = ArrayDeque<EditorState>()
    private val redoEdits = ArrayDeque<EditorState>()
    private var undoNotice: (() -> CalcUiState)? = null
    private var lastValue: Value? = null
    private var lastOutcome: Outcome? = null
    val settings: Settings get() = calculator.settings
    var ui: CalcUiState = CalcUiState(angle = settings.angle)
        private set

    fun press(key: Key, layer: Layer = Layer.PRIMARY): CalcUiState = handle(key.action(layer))
    fun flickUp(key: Key): CalcUiState = key.flickUp?.let(::handle) ?: ui
    fun paletteInsert(entry: PaletteEntry): CalcUiState = handle(entry.insert)
    fun insertAns(): CalcUiState = handle(KeyAction.Ans)
    fun insertReference(n: Int): CalcUiState = handle(KeyAction.Ref(n))
    fun insertText(text: String): CalcUiState = handle(KeyAction.Insert(text))
    fun backspaceRepeat(): CalcUiState = handle(KeyAction.BackspaceRepeat)
    fun left(): CalcUiState = handle(KeyAction.Left)
    fun right(): CalcUiState = handle(KeyAction.Right)

    fun replaceText(text: String): CalcUiState = replaceTextResult(text).state

    /** Commit tool metadata only if the entire replacement passes the editor limits. */
    fun replaceToolText(text: String, angle: AngleMode? = null, notice: String? = null): TextReplacement {
        val replacement = replaceTextResult(text)
        if (!replacement.accepted) return replacement
        if (angle != null) updateSettings(settings.copy(angle = angle))
        if (notice != null) report(notice)
        return TextReplacement(true, ui)
    }

    private fun replaceTextResult(text: String): TextReplacement {
        if (text.length > MAX_ENTRY_CHARS) return rejectedReplacement("Expression is too long (maximum $MAX_ENTRY_CHARS characters).")
        if (!boundedNesting(text)) return rejectedReplacement("Expression is nested too deeply (maximum 64 levels).")
        return editResult(Editor.insert(EditorState(), text))
    }

    fun moveCursor(cursor: Cursor): CalcUiState {
        val row = ui.editor.root.rowAt(cursor.path)
        if (cursor.offset !in 0..row.size) return ui
        ui = ui.copy(editor = ui.editor.copy(cursor = cursor, selection = null))
        return ui
    }

    fun selectAll(): CalcUiState {
        if (ui.editor.root.isEmpty) return ui
        ui = ui.copy(editor = ui.editor.copy(selection = Selection(emptyList(), 0, ui.editor.root.size)))
        return ui
    }

    fun copyInput(): String = if (ui.editor.selection != null) {
        (Clipboard.copy(ui.editor) as? CopyResult.Copied)?.clip?.toInput().orEmpty()
    } else ui.entryText

    /** Digit grouping is presentation, never clipboard syntax. */
    fun copyResult(): String = ui.clipboardResult ?: ui.exact.orEmpty()

    fun paste(text: String): CalcUiState {
        if (text.length + ui.entryText.length > MAX_ENTRY_CHARS) return report("Paste is too long.")
        if (!boundedNesting(text) || ui.editor.cursor.path.size > 32) return report("Pasted expression is nested too deeply.")
        return when (val result = Clipboard.pasteText(ui.editor, text)) {
            is PasteResult.Pasted -> edit(result.state)
            is PasteResult.NotReadable -> report(result.error.message)
        }
    }

    fun undoEntry(): CalcUiState {
        val previous = undoEdits.removeLastOrNull() ?: return ui
        redoEdits.addLast(ui.editor)
        return showEditor(previous)
    }
    fun redoEntry(): CalcUiState {
        val next = redoEdits.removeLastOrNull() ?: return ui
        undoEdits.addLast(ui.editor)
        return showEditor(next)
    }

    fun reuse(id: EntryId): CalcUiState {
        val entry = tape.entry(id) ?: return ui
        val before = ui
        val beforeOutcome = lastOutcome
        edit(entry.source.copy(cursor = Cursor(offset = entry.source.root.size), selection = null))
        ui = ui.copy(editingId = null, notice = "Expression copied from $id", noticeUndo = true)
        undoNotice = { restoreUi(before, beforeOutcome) }
        return ui
    }

    fun beginEdit(id: EntryId): CalcUiState {
        val entry = tape.entry(id) ?: return ui
        edit(entry.source.copy(cursor = Cursor(offset = entry.source.root.size), selection = null))
        ui = ui.copy(editingId = id, notice = "Editing $id · linked results update when you press =")
        return ui
    }
    fun cancelEdit(): CalcUiState {
        ui = ui.copy(editingId = null, notice = null)
        return ui
    }
    fun deleteEntry(id: EntryId): CalcUiState {
        if (tape.entry(id) == null) return ui
        val before = ui
        val beforeOutcome = lastOutcome
        tape.delete(id)
        ui = ui.copy(entries = tape.entries(), notice = "$id deleted", noticeUndo = true,
            editingId = if (ui.editingId == id) null else ui.editingId,
            exact = null, decimal = null, error = null, sdVisible = false, clipboardResult = null, steps = emptyList())
        lastValue = null; lastOutcome = null
        undoNotice = { tape.undo(); restoreUi(before, beforeOutcome) }
        return ui
    }
    fun clearHistory(): CalcUiState {
        val before = ui
        val beforeOutcome = lastOutcome
        tape.clear()
        lastValue = null; lastOutcome = null
        ui = CalcUiState(angle = settings.angle, notice = "History cleared", noticeUndo = true)
        undoEdits.clear(); redoEdits.clear()
        undoNotice = { tape.undo(); restoreUi(before, beforeOutcome) }
        return ui
    }
    fun undoLastNotice(): CalcUiState {
        val action = undoNotice ?: return ui
        undoNotice = null
        action()
        ui = ui.copy(notice = "Restored", noticeUndo = false)
        return ui
    }
    fun dismissNotice(): CalcUiState {
        undoNotice = null
        ui = ui.copy(notice = null, noticeUndo = false)
        return ui
    }

    fun cycleAngle(): CalcUiState {
        val next = when (settings.angle) {
            AngleMode.DEG -> AngleMode.RAD
            AngleMode.RAD -> AngleMode.GRAD
            AngleMode.GRAD -> AngleMode.DEG
        }
        return updateSettings(settings.copy(angle = next))
    }
    fun updateSettings(value: Settings): CalcUiState {
        calculator.settings = value
        tape.updateSettings(value)
        ui = ui.copy(angle = value.angle)
        // Reformat a stored value; never recalculate history on a setting change.
        lastOutcome?.let { outcome ->
            val rendered = OutcomeFormatter.render(outcome, ui.entryText, value)
            ui = ui.copy(exact = rendered.primary, decimal = rendered.decimal,
                sdVisible = rendered.value?.let { Formatter.hasDistinctForms(it, value) } == true, approximate = rendered.approximate)
        }
        return ui
    }
    fun sdToggle(): CalcUiState {
        val v = lastValue ?: return ui
        val prefix = (lastOutcome as? Outcome.Stored)?.let { "${it.register} = " }.orEmpty()
        val fraction = Formatter.exact(v, settings.copy(exactForm = ExactForm.FRACTION))
        val form = if (ui.exact == prefix + fraction) ExactForm.DECIMAL else ExactForm.FRACTION
        ui = ui.copy(exact = prefix + Formatter.exact(v, settings.copy(exactForm = form)),
            approximate = OutcomeFormatter.isApproximate(v, settings.copy(exactForm = form)))
        return ui
    }

    fun archive(): TapeArchive = tape.archive()
    /** Independent graph context, including resolved registers, live links and Ans. */
    fun snapshotEnvironment(): Environment = tape.snapshotEnvironment()
    fun restore(archive: TapeArchive, editor: EditorState = EditorState()): CalcUiState {
        tape.restore(archive)
        lastValue = null; lastOutcome = null
        undoEdits.clear(); redoEdits.clear(); undoNotice = null
        ui = CalcUiState(editor = editor, entries = tape.entries(), angle = settings.angle)
        return ui
    }
    fun report(message: String): CalcUiState {
        ui = ui.copy(notice = message, noticeUndo = false)
        undoNotice = null
        return ui
    }

    private fun handle(action: KeyAction): CalcUiState = when (action) {
        KeyAction.Equals -> commit()
        KeyAction.OpenPalette -> ui
        KeyAction.Left, KeyAction.Right -> {
            ui = ui.copy(editor = Keypad.apply(ui.editor, action)); ui
        }
        else -> {
            var editor = ui.editor
            // A new digit starts fresh; an operator continues from Ans.
            // Backspace still edits the expression the user can see.
            if (ui.committed && ui.editingId == null && action != KeyAction.Backspace && action != KeyAction.BackspaceRepeat) {
                editor = if (action is KeyAction.Insert && action.text in listOf("+", "-", "*", "/", "^", "%")) {
                    Editor.typeAns(EditorState())
                } else EditorState()
            }
            if (action is KeyAction.Insert && editor.toInput().length + action.text.length > MAX_ENTRY_CHARS) report("Expression is too long.")
            else if (action is KeyAction.Insert && !boundedNesting(action.text, editor.cursor.path.size)) report("Expression is nested too deeply.")
            else edit(Keypad.apply(editor, action))
        }
    }

    private fun edit(next: EditorState): CalcUiState = editResult(next).state

    private fun rejectedReplacement(message: String) = TextReplacement(false, report(message))

    private fun editResult(next: EditorState): TextReplacement {
        val queue = ArrayDeque<Pair<Row, Int>>()
        queue.addLast(next.root to 0)
        var nodes = 0
        while(queue.isNotEmpty()) {
            val (row, depth) = queue.removeLast()
            if(depth > 64) return rejectedReplacement("Expression is nested too deeply (maximum 64 levels).")
            nodes += row.size
            if(nodes > MAX_ENTRY_CHARS) return rejectedReplacement("Expression is too large.")
            row.nodes.forEach { node -> node.fields().forEach { queue.addLast(node.field(it) to depth + 1) } }
        }
        if (next == ui.editor) return TextReplacement(true, if (ui.committed) showEditor(next) else ui)
        undoEdits.addLast(ui.editor)
        if (undoEdits.size > MAX_EDITOR_UNDOS) undoEdits.removeFirst()
        redoEdits.clear()
        return TextReplacement(true, showEditor(next))
    }
    private fun showEditor(next: EditorState): CalcUiState {
        lastValue = null; lastOutcome = null; undoNotice = null
        ui = ui.copy(editor = next, exact = null, decimal = null, error = null, sdVisible = false,
            steps = emptyList(), notice = null, noticeUndo = false, committed = false, clipboardResult = null, approximate = false)
        return ui
    }
    private fun restoreUi(previous: CalcUiState, outcome: Outcome?): CalcUiState {
        ui = previous.copy(entries = tape.entries(), angle = settings.angle)
        lastOutcome = outcome
        lastValue = outcome?.let { OutcomeFormatter.render(it, previous.entryText, settings).value }
        return ui
    }

    fun commit(): CalcUiState {
        if (ui.entryText.isBlank()) return ui
        if (ui.entryText.length > MAX_ENTRY_CHARS) return report("Expression is too long.")
        val before = ui
        val beforeOutcome = lastOutcome
        val id = ui.editingId
        val transaction = if (id == null) tape.append(ui.editor) else tape.edit(id, ui.editor)
        val entry = if (id == null) tape.entries().last() else tape.entry(id)!!
        lastValue = null; lastOutcome = null
        ui = ui.copy(entries = tape.entries(), exact = null, decimal = null, error = null,
            steps = emptyList(), sdVisible = false, editingId = null, committed = true,
            notice = null, noticeUndo = false, clipboardResult = null, approximate = false)
        when (val value = entry.value) {
            is EntryValue.Computed -> {
                lastOutcome = value.outcome
                val rendered = OutcomeFormatter.render(value.outcome, ui.entryText, settings)
                lastValue = rendered.value
                ui = ui.copy(exact = rendered.primary, decimal = rendered.decimal, steps = rendered.steps, approximate = rendered.approximate,
                    sdVisible = rendered.value?.let { Formatter.hasDistinctForms(it, settings) } == true,
                    clipboardResult = rendered.value?.let(::copyExpression) ?: rendered.primary)
            }
            is EntryValue.Failed -> ui = ui.copy(error = value.error.message)
            is EntryValue.Blocked -> ui = ui.copy(error = value.description)
            is EntryValue.Cycle -> ui = ui.copy(error = "Reference cycle: ${value.members.joinToString(" ↔ ")}")
            is EntryValue.Ambiguous -> ui = ui.copy(error = "This expression has more than one interpretation.")
            is EntryValue.Stale -> ui = ui.copy(error = "Waiting for recomputation")
            EntryValue.Empty -> Unit
        }
        undoEdits.clear(); redoEdits.clear(); undoNotice = null
        if (id != null) {
            ui = ui.copy(notice = "$id edited · ${transaction.updatedCount} results updated", noticeUndo = true)
            undoNotice = { tape.undo(); restoreUi(before, beforeOutcome) }
        }
        return ui
    }

    companion object {
        const val MAX_ENTRY_CHARS = 8192
        private const val MAX_EDITOR_UNDOS = 200
    }

    /** Approximate numbers remain explicitly approximate when pasted back into the app. */
    private fun copyExpression(value: Value): String {
        fun number(n: com.vaibhav.calc.core.num.Num): String = Formatter.input(n).let {
            if(n is com.vaibhav.calc.core.num.Dec) "approx($it)" else it
        }
        return when(value) {
            is RealValue -> number(value.num)
            is ComplexValue -> "(${number(value.cx.re)})+(${number(value.cx.im)})i"
            is QuantityValue -> "(${number(value.q.value)}) ${value.q.unit.name}"
        }
    }

    private fun boundedNesting(text: String, initialDepth: Int = 0): Boolean {
        var depth = initialDepth
        for (char in text) {
            if (char == '(') { depth++; if (depth > 64) return false }
            if (char == ')') depth = (depth - 1).coerceAtLeast(0)
        }
        return true
    }
}
