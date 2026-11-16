package com.vaibhav.calc.core.entry

import com.vaibhav.calc.core.ast.Program
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.parse.Parser

/** One descent into a structure's field. */
data class PathStep(val nodeIndex: Int, val field: Field)

/** An offset between nodes of the row reached by [path]. */
data class Cursor(val path: List<PathStep> = emptyList(), val offset: Int = 0)

/**
 * A half-open range `[from, to)` of the row at [path].
 *
 * **One concept, two jobs** (D-019): a *selected structure* is simply a range of
 * length one — which is what Rule 3 needs — and a multi-node range is what the
 * clipboard (11.7) needs to copy a sub-expression. Built once, used for both.
 */
data class Selection(val path: List<PathStep>, val from: Int, val to: Int) {
    init { require(from in 0..to) { "invalid selection [$from, $to)" } }
    val size: Int get() = to - from
}

data class EditorState(
    val root: Row = Row(),
    val cursor: Cursor = Cursor(),
    val selection: Selection? = null,
) {
    /** Linearise to the engine's text grammar. */
    fun toInput(): String = root.toInput()

    /**
     * Validity is **the real parser's opinion** (Article 12). The editor holds no
     * grammar of its own, so it cannot build something the parser would reject —
     * "your app let me type this and then refused to read it" is unreachable.
     *
     * It validates against the FULL grammar ([Parser.parseProgram]) — equations,
     * assignments and systems, not merely expressions. Validating against
     * `Parser.parse` was itself a drift: the editor could build `x²-5x+6=0`, the
     * thesis input, and then call it invalid. Round-trip testing caught it.
     */
    fun validate(): CalcResult<Program> = Parser.parseProgram(toInput())

    /** No structure field is left empty — i.e. the entry is finished being typed. */
    val isComplete: Boolean get() = root.hasNoEmptyFields()
}

// --- path resolution ---------------------------------------------------------

fun Row.rowAt(path: List<PathStep>): Row {
    var r = this
    for (s in path) r = r.nodes[s.nodeIndex].field(s.field)
    return r
}

fun Row.withRowAt(path: List<PathStep>, newRow: Row): Row {
    if (path.isEmpty()) return newRow
    val s = path.first()
    val node = nodes[s.nodeIndex]
    val inner = node.field(s.field).withRowAt(path.drop(1), newRow)
    val updated = nodes.toMutableList()
    updated[s.nodeIndex] = node.withField(s.field, inner)
    return Row(updated)
}

fun Row.hasNoEmptyFields(): Boolean = nodes.all { n ->
    when (n) {
        is Atom -> true
        else -> n.fields().all { f -> !n.field(f).isEmpty && n.field(f).hasNoEmptyFields() }
    }
}
