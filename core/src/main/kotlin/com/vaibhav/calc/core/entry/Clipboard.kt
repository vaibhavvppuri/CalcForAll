package com.vaibhav.calc.core.entry

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError

/**
 * A copied sub-expression. Holds **nodes, not text**, so copying a fraction and
 * pasting it yields a fraction — not `(1)/(2)` flattened into six characters that
 * the user then has to rebuild.
 */
data class Clip(val nodes: List<Node>) {
    /** The text form, for handing to the system clipboard. */
    fun toInput(): String = Row(nodes).toInput()
    val isEmpty: Boolean get() = nodes.isEmpty()
}

/** Copying with nothing selected is a state, not a failure — there is simply nothing to copy. */
sealed interface CopyResult {
    data class Copied(val clip: Clip) : CopyResult
    object NothingSelected : CopyResult
}

sealed interface CutResult {
    data class Cut(val state: EditorState, val clip: Clip) : CutResult
    object NothingSelected : CutResult
}

/**
 * Pasting text from outside the app can genuinely fail — the text may not be
 * something the engine can read. [NotReadable] carries the parser's own complaint
 * rather than dropping the paste silently, which would leave the user staring at
 * an unchanged screen with no idea why (Article 11).
 */
sealed interface PasteResult {
    data class Pasted(val state: EditorState) : PasteResult
    data class NotReadable(val error: EvalError, val text: String) : PasteResult
}

/**
 * Copy / cut / paste (spec 11.7), built on the [Selection] substrate.
 *
 * **Selection was not built for this** — it was built for Rule 3, which requires
 * that destroying a structure be preceded by seeing it selected (D-019). The
 * clipboard needs the identical concept: a range of nodes in a row. One concept,
 * two jobs, one implementation (Article 12).
 */
object Clipboard {

    fun copy(state: EditorState): CopyResult {
        val nodes = Editor.selectedNodes(state)
        return if (nodes.isEmpty()) CopyResult.NothingSelected else CopyResult.Copied(Clip(nodes))
    }

    fun cut(state: EditorState): CutResult {
        val sel = state.selection ?: return CutResult.NothingSelected
        val nodes = Editor.selectedNodes(state)
        if (nodes.isEmpty()) return CutResult.NothingSelected
        return CutResult.Cut(Editor.deleteSelection(state, sel), Clip(nodes))
    }

    /**
     * Paste structure at the cursor, replacing any selection.
     *
     * This is the internal path: nodes go in as nodes. It cannot fail — the clip
     * came out of an editor, so it is by construction something an editor can hold.
     */
    fun paste(state: EditorState, clip: Clip): EditorState {
        if (clip.isEmpty) return state
        val target = state.selection?.let { Editor.deleteSelection(state, it) } ?: state
        val row = target.root.rowAt(target.cursor.path)
        val nodes = row.nodes.toMutableList()
        nodes.addAll(target.cursor.offset, clip.nodes)
        return target.copy(
            root = target.root.withRowAt(target.cursor.path, Row(nodes)),
            cursor = Cursor(target.cursor.path, target.cursor.offset + clip.nodes.size),
            selection = null,
        )
    }

    /**
     * Paste text from outside the app.
     *
     * Validated by **the real parser** before anything is inserted (Article 12):
     * the editor holds no grammar of its own, so it cannot accept a paste the
     * engine would then refuse to read. Insertion goes through [Editor.insert] —
     * the same path a keystroke takes — so pasted text obeys the same rules as
     * typed text, rather than a second, subtly different one.
     */
    fun pasteText(state: EditorState, text: String): PasteResult {
        if (text.isBlank()) return PasteResult.Pasted(state)
        // Validate the pasted text ALONE. Validating the merged result would reject
        // pasting `+2` into `3` — a legitimate paste that is only invalid in isolation
        // — while accepting garbage that happens to parse next to what's already there.
        when (val parsed = Editor.insert(EditorState(), text).validate()) {
            is CalcResult.Err -> return PasteResult.NotReadable(parsed.error, text)
            is CalcResult.Ok -> Unit
        }
        val target = state.selection?.let { Editor.deleteSelection(state, it) } ?: state
        return PasteResult.Pasted(Editor.insert(target, text))
    }
}
