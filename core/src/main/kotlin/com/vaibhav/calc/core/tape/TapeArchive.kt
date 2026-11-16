package com.vaibhav.calc.core.tape

import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.eval.Settings

/** Durable source data. Derived values and dependency edges are recomputed on load. */
data class ArchivedEntry(
    val id: EntryId,
    val source: EditorState,
    val deadRefs: Set<EntryId>,
    val settings: Settings,
)

data class TapeArchive(val nextId: Int = 1, val entries: List<ArchivedEntry> = emptyList())
