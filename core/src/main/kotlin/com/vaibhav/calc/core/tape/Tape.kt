package com.vaibhav.calc.core.tape

import com.vaibhav.calc.core.ast.Assignment
import com.vaibhav.calc.core.ast.Binary
import com.vaibhav.calc.core.ast.Call
import com.vaibhav.calc.core.ast.Equation
import com.vaibhav.calc.core.ast.Expr
import com.vaibhav.calc.core.ast.ExprStatement
import com.vaibhav.calc.core.ast.NameRef
import com.vaibhav.calc.core.ast.NumberLit
import com.vaibhav.calc.core.ast.Postfix
import com.vaibhav.calc.core.ast.Program
import com.vaibhav.calc.core.ast.Unary
import com.vaibhav.calc.core.ast.UnitApply
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.entry.Func
import com.vaibhav.calc.core.entry.Row
import com.vaibhav.calc.core.entry.field
import com.vaibhav.calc.core.entry.fields
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.eval.Value
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.parse.Parser
import com.vaibhav.calc.core.route.ExpressionRouter
import com.vaibhav.calc.core.route.RouteResult
import java.util.PriorityQueue
import java.util.IdentityHashMap

/**
 * One line of the tape. The 2D [source] is the truth — tap-to-reuse restores structure, not text.
 *
 * [deadRefs] are the `#n` ids this source named that did NOT exist when the entry
 * was committed (D-036.1). Captured once, at commit/edit, and carried in the
 * snapshot: a later entry that receives one of these numbers can never be adopted,
 * which is what makes the rebound reference (BRAIN-RUN-REPORT F-1) unrepresentable.
 */
data class TapeEntry(
    val id: EntryId,
    val source: EditorState,
    val value: EntryValue,
    val dependsOn: Set<EntryId>,
    val deadRefs: Set<EntryId> = emptySet(),
    /** Evaluation policy at commit; changing the angle chip must not rewrite history. */
    val settings: Settings? = null,
)

/** An immutable snapshot of the whole tape. The undo unit (D-023). */
data class TapeState(val entries: List<TapeEntry>)

/** The result of a mutating tape operation — the count is the snackbar's "N results updated". */
data class Transaction(val state: TapeState, val updated: List<EntryId>) {
    val updatedCount: Int get() = updated.size
}

/**
 * Undoing is a state, never a failure.
 *
 * There is deliberately **no `UndoFailed`**: undo restores a snapshot, which cannot
 * fail, so manufacturing a failure representation for a non-failure would be the
 * very thing Article 11 forbids. [NoMoreUndos] means "there is nothing to undo" —
 * an answer, not an error.
 */
sealed interface UndoOutcome {
    data class Undone(val state: TapeState, val restored: Int) : UndoOutcome
    object NoMoreUndos : UndoOutcome
}

/**
 * The history tape (spec 12.1) with live recompute.
 *
 * **The undo unit is the transaction, not the edit** (D-023). Editing `#3`
 * recomputes `#5` and `#7`; undo restores *all* of them or none. Restoring `#3`
 * while `#5` kept its recomputed value would leave the tape showing results derived
 * from an expression that is no longer there — corruption that looks fine, which is
 * worse than a crash. Snapshotting the whole state makes partial undo unrepresentable.
 */
class Tape(private var settings: Settings = Settings()) {
    companion object {
        const val MAX_ENTRIES = 5_000
        const val MAX_UNDO_TRANSACTIONS = 100
        const val MAX_SOURCE_NODES = 16_384
        const val MAX_TOTAL_SOURCE_NODES = 90_000
        const val MAX_SOURCE_DEPTH = 128
    }

    private var current = TapeState(emptyList())
    private val undoStack = ArrayDeque<TapeState>()
    private val redoStack = ArrayDeque<TapeState>()
    private var nextId = 1

    val state: TapeState get() = current
    fun entries(): List<TapeEntry> = current.entries
    fun entry(id: EntryId): TapeEntry? = current.entries.firstOrNull { it.id == id }
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Opaque, in-process rollback state; unlike an archive it retains undo/redo and IDs. */
    class Checkpoint internal constructor(
        internal val owner: Tape,
        internal val state: TapeState,
        internal val undo: List<TapeState>,
        internal val redo: List<TapeState>,
        internal val nextId: Int,
        internal val settings: Settings,
    )

    /** Callers must serialize mutations while holding a checkpoint. No expressions are re-evaluated. */
    fun checkpoint() = Checkpoint(this, current, undoStack.toList(), redoStack.toList(), nextId, settings)

    fun restore(checkpoint: Checkpoint) {
        require(checkpoint.owner === this) { "A tape checkpoint belongs to its original tape." }
        current = checkpoint.state
        undoStack.clear(); undoStack.addAll(checkpoint.undo)
        redoStack.clear(); redoStack.addAll(checkpoint.redo)
        nextId = checkpoint.nextId
        settings = checkpoint.settings
    }

    /**
     * Independent memory for graphs/tools. A parseable assignment whose latest
     * result is failed, blocked or cyclic clears that register rather than
     * reviving an older value. This uses the same assignment detection as tape
     * recomputation; a source that cannot parse has no recognized assignments.
     * Only the last row can supply ans. Previous/stale values are never exposed.
     */
    fun snapshotEnvironment(): Environment {
        val rows = current.entries
        val snapshot = Environment()
        for (entry in rows) {
            val value = (entry.value as? EntryValue.Computed)?.value
            if (value != null) snapshot.set(entry.id.reference, value)
            val program = programOf(Parser.parseProgram(entry.source.toInput()))
            for (register in assignedRegisters(program)) {
                if (value != null) snapshot.set(register, value)
                else snapshot.remove(register)
            }
        }
        (rows.lastOrNull()?.value as? EntryValue.Computed)?.value?.let(snapshot::setAns)
        return snapshot
    }

    /** Only future commits/edits use the new policy. Existing rows retain theirs. */
    fun updateSettings(value: Settings) { settings = value }

    fun clear(): Transaction = transact { emptyList() }

    fun archive(): TapeArchive = TapeArchive(nextId, current.entries.map {
        ArchivedEntry(it.id, it.source, it.deadRefs, it.settings ?: settings)
    })

    /** Restores sources, never trusted cached answers. IDs and dead refs survive restarts. */
    fun restore(archive: TapeArchive) {
        require(archive.entries.size <= MAX_ENTRIES) { "History holds at most $MAX_ENTRIES entries." }
        require(archive.entries.map { it.id }.distinct().size == archive.entries.size) { "duplicate tape ids" }
        require(archive.entries.all { it.id.n > 0 && it.deadRefs.all { d -> d.n >= 0 } }) { "invalid tape id" }
        require(archive.nextId > (archive.entries.maxOfOrNull { it.id.n } ?: 0)) { "invalid next tape id" }
        val restored = recompute(TapeState(archive.entries.map {
            TapeEntry(it.id, it.source, EntryValue.Empty, emptySet(), it.deadRefs, it.settings)
        }))
        current = restored
        nextId = archive.nextId
        undoStack.clear()
        redoStack.clear()
    }

    /** Commit a new entry. Returns the transaction (what recomputed). */
    fun append(source: EditorState): Transaction {
        require(current.entries.size < MAX_ENTRIES) { "History is full ($MAX_ENTRIES entries). Delete entries before adding another." }
        require(nextId in 1 until Int.MAX_VALUE) { "History entry identifiers are exhausted." }
        val id = EntryId(nextId)
        val transaction = transact {
            // D-036.1: the guard sees the tape AS COMMITTED — including the new id
            // itself, so a self-reference stays a cycle of one (D-033), never a dead ref.
            val ids = current.entries.map { it.id }.toSet() + id
            validateSource(source)
            current.entries + TapeEntry(id, source, EntryValue.Empty, emptySet(), deadRefsIn(source, ids), settings)
        }
        nextId++
        return transaction
    }

    /** Edit an existing entry — the destructive action that raises the snackbar. */
    fun edit(id: EntryId, source: EditorState): Transaction = transact {
        validateSource(source)
        // An edit is a fresh commit: dead refs are re-captured against the tape as
        // it stands now (D-036.1) — which is how a dead entry legitimately recovers.
        val ids = current.entries.map { it.id }.toSet()
        current.entries.map {
            if (it.id == id) it.copy(source = source, deadRefs = deadRefsIn(source, ids), settings = settings) else it
        }
    }

    fun delete(id: EntryId): Transaction = transact {
        current.entries.filterNot { it.id == id }
    }

    fun undo(): UndoOutcome {
        val previous = undoStack.removeLastOrNull() ?: return UndoOutcome.NoMoreUndos
        redoStack.addLast(current)
        val restored = changedIds(current, previous).size
        current = previous
        return UndoOutcome.Undone(current, restored)
    }

    fun redo(): UndoOutcome {
        val next = redoStack.removeLastOrNull() ?: return UndoOutcome.NoMoreUndos
        undoStack.addLast(current)
        trimUndo()
        val restored = changedIds(current, next).size
        current = next
        return UndoOutcome.Undone(current, restored)
    }

    // -----------------------------------------------------------------------

    private fun transact(mutate: () -> List<TapeEntry>): Transaction {
        val before = current
        // Validate and compute before changing either the visible state or undo.
        // A rejected operation must not erase redo or leave a phantom undo item.
        val mutated = mutate()
        val after = recompute(TapeState(mutated))
        undoStack.addLast(before)
        trimUndo()
        redoStack.clear()
        current = after
        return Transaction(current, changedIds(before, current))
    }

    private fun trimUndo() {
        while (undoStack.size > MAX_UNDO_TRANSACTIONS) undoStack.removeFirst()
    }

    private fun validateSource(source: EditorState): Int {
        var count = 0
        val rows = ArrayDeque<Pair<Row, Int>>()
        rows.addLast(source.root to 0)
        while (rows.isNotEmpty()) {
            val (row, depth) = rows.removeLast()
            require(depth <= MAX_SOURCE_DEPTH) { "Calculation is nested too deeply." }
            count += row.nodes.size
            require(count <= MAX_SOURCE_NODES) { "Calculation exceeds $MAX_SOURCE_NODES editor nodes." }
            for (node in row.nodes) {
                require(node !is Func || node.name.length <= 64) { "Function name is too long." }
                node.fields().forEach { rows.addLast(node.field(it) to depth + 1) }
            }
        }
        return count
    }

    private fun changedIds(a: TapeState, b: TapeState): List<EntryId> {
        val byId = a.entries.associateBy { it.id }
        // Every SCC shares its membership list. Compare each old/new list pair
        // once, instead of comparing an N-member list for all N cycle entries.
        val cycleEquality = IdentityHashMap<List<EntryId>, IdentityHashMap<List<EntryId>, Boolean>>()
        return b.entries.filter { entry ->
            val previous = byId[entry.id]?.value
            val next = entry.value
            if (previous is EntryValue.Cycle && next is EntryValue.Cycle) {
                val matches = cycleEquality.getOrPut(previous.members) { IdentityHashMap() }
                    .getOrPut(next.members) { previous.members == next.members }
                !matches
            } else previous != next
        }.map { it.id }
    }

    /**
     * Recompute the whole tape in dependency order.
     *
     * **Cycle membership is SCC-based (D-036.5).** An entry is IN a cycle iff it can
     * reach itself through the dependency graph; its `Cycle.members` are exactly its
     * own strongly-connected component, in tape order. An entry outside every loop
     * that depends on one is not a member — it is `Blocked(by that member)`, which
     * rule 4 already says once membership is honest. This replaced classifying
     * Kahn's whole non-orderable leftover as one merged `Cycle`, which branded
     * bystanders as members and fused disjoint loops (B2 battery, probes C-003..037).
     */
    private fun recompute(s: TapeState): TapeState {
        require(s.entries.size <= MAX_ENTRIES) { "History holds at most $MAX_ENTRIES entries." }
        var totalNodes = 0
        for (entry in s.entries) {
            totalNodes += validateSource(entry.source)
            require(totalNodes <= MAX_TOTAL_SOURCE_NODES) { "History exceeds $MAX_TOTAL_SOURCE_NODES editor nodes. Delete entries before adding more." }
            require(entry.deadRefs.size <= MAX_SOURCE_NODES) { "Too many missing references in one calculation." }
        }
        val ids = s.entries.map { it.id }
        val byId = s.entries.associateBy { it.id }
        val positions = ids.withIndex().associate { it.value to it.index }
        val knownIds = positions.keys
        val text = s.entries.associate { it.id to it.source.toInput() }
        val parsed = ids.associateWith { Parser.parseProgram(text.getValue(it)) }
        // D-036.11: which registers each entry ASSIGNS — the raw material for the
        // positional register edges below.
        val assigns = s.entries.associate { e ->
            e.id to assignedRegisters(programOf(parsed.getValue(e.id)))
        }
        val deps = LinkedHashMap<EntryId, Set<EntryId>>()
        val priorRegisters = HashMap<String, EntryId>()
        for ((index, e) in s.entries.withIndex()) {
            deps[e.id] = referencedEntries(programOf(parsed.getValue(e.id)), e,
                ids.getOrNull(index - 1), knownIds, priorRegisters)
            assigns.getValue(e.id).forEach { priorRegisters[it] = e.id }
        }

        // Iterative SCC traversal stores only graph edges, never a transitive
        // reachability set per row. Long linked histories remain linear in space.
        val cycles = cycleComponents(ids, deps, positions)
        val cyclicSet = cycles.keys
        val ordered = topologicalOrder(ids, deps, cyclicSet)

        val values = LinkedHashMap<EntryId, EntryValue>()
        for ((id, members) in cycles) {
            values[id] = EntryValue.Cycle(members)
        }

        for (id in ordered) {
            val e = byId.getValue(id)
            values[id] = when {
                text.getValue(id).isBlank() -> EntryValue.Empty
                parsed.getValue(id) is CalcResult.Err ->
                    EntryValue.Failed((parsed.getValue(id) as CalcResult.Err).error, previousOf(e))
                // D-036.1: refs that were missing at commit are a sticky typed error
                // naming the ids — never re-resolved against today's tape.
                e.deadRefs.isNotEmpty() -> EntryValue.Failed(
                    EvalError.MissingEntry(e.deadRefs.map { it.reference }, null),
                    previousOf(e),
                )
                else -> {
                    // A dependency that handed this entry no value blocks it. The
                    // entry did not fail — it never ran.
                    val blocker = deps.getValue(id).firstOrNull { d ->
                        (values[d] as? EntryValue.Computed)?.value == null
                    }
                    if (blocker != null) EntryValue.Blocked(blocker, previousOf(e))
                    else evaluate(e, text.getValue(id), deps.getValue(id), values,
                        ids.getOrNull(positions.getValue(id) - 1), positions, assigns)
                }
            }
        }

        return TapeState(
            s.entries.map {
                it.copy(value = values[it.id] ?: EntryValue.Empty, dependsOn = deps.getValue(it.id))
            },
        )
    }

    private fun evaluate(
        e: TapeEntry,
        text: String,
        dependsOn: Set<EntryId>,
        values: Map<EntryId, EntryValue>,
        previous: EntryId?,
        positions: Map<EntryId, Int>,
        assigns: Map<EntryId, Set<String>>,
    ): EntryValue {
        val env = Environment()
        for (d in dependsOn) {
            (values[d] as? EntryValue.Computed)?.value?.let { env.set(d.reference, it) }
        }
        // D-036.11: registers flow down the tape. Dependencies are injected in TAPE
        // ORDER so when two assigners of the same register are both dependencies,
        // the LATER (nearer) one wins — the same positional law `ans` follows.
        for (d in dependsOn.sortedBy { positions.getValue(it) }) {
            val v = (values[d] as? EntryValue.Computed)?.value ?: continue
            for (r in assigns.getValue(d)) env.set(r, v)
        }
        // `ans` reads the entry immediately above — resolved against the state being
        // recomputed, not `current`, which is still the pre-transaction tape here.
        previous?.let { prev ->
            (values[prev] as? EntryValue.Computed)?.value?.let { env.setAns(it) }
        }
        return when (val r = ExpressionRouter(e.settings ?: settings, env).route(text)) {
            is RouteResult.Ok -> EntryValue.Computed(r.outcome)
            is RouteResult.Err -> EntryValue.Failed(r.error, previousOf(e))
            is RouteResult.Ambiguous -> EntryValue.Ambiguous(r.candidates)
        }
    }

    private fun previousOf(e: TapeEntry): Value? = (e.value as? EntryValue.Computed)?.value

    private fun programOf(r: CalcResult<Program>): Program? = (r as? CalcResult.Ok)?.value

    /** All `#n` ids a source names, existing or not — the commit guard's raw material. */
    private fun namedRefs(program: Program?): Set<EntryId> {
        if (program == null) return emptySet()
        val out = LinkedHashSet<EntryId>()
        for (n in collectedNames(program)) {
            if (n.startsWith("#")) n.drop(1).toIntOrNull()?.let { out += EntryId(it) }
        }
        return out
    }

    /** D-036.1: the refs in [source] that do not exist among [ids] — captured at commit. */
    private fun deadRefsIn(source: EditorState, ids: Set<EntryId>): Set<EntryId> =
        namedRefs(programOf(Parser.parseProgram(source.toInput()))).filterNot { it in ids }.toSet()

    private fun collectedNames(program: Program): Set<String> {
        val names = LinkedHashSet<String>()
        program.statements.forEach { st ->
            when (st) {
                is ExprStatement -> collectNames(st.expr, names)
                is Equation -> { collectNames(st.lhs, names); collectNames(st.rhs, names) }
                is Assignment -> collectNames(st.value, names)
            }
        }
        return names
    }

    /** The registers a program ASSIGNS — `5→A` makes this entry A's source (D-036.11). */
    private fun assignedRegisters(program: Program?): Set<String> {
        if (program == null) return emptySet()
        return program.statements.filterIsInstance<Assignment>()
            .map { it.target }
            .filter { it in ReservedNames.REGISTERS }
            .toSet()
    }

    /**
     * Entries referenced by `#n`, plus the entry above when `ans` is used, plus —
     * D-036.11 — the NEAREST PRIOR assigner of each register this entry reads.
     * Register edges point strictly up the tape, so they can never form a cycle.
     */
    private fun referencedEntries(
        program: Program?,
        entry: TapeEntry,
        previous: EntryId?,
        knownIds: Set<EntryId>,
        priorRegisters: Map<String, EntryId>,
    ): Set<EntryId> {
        if (program == null) return emptySet()
        val names = collectedNames(program)
        val out = LinkedHashSet<EntryId>()
        for (n in names) {
            if (n.startsWith("#")) n.drop(1).toIntOrNull()?.let { out += EntryId(it) }
            if (n.equals("ans", ignoreCase = true)) {
                previous?.let { out += it }
            }
            if (n in ReservedNames.REGISTERS) {
                priorRegisters[n]?.let { out += it }
            }
        }
        // A self-reference is KEPT (D-033). `#1 = #1+1` is a cycle of length one, and
        // the sort catches it for free: an entry that depends on itself can never be
        // drained, so it lands in Kahn's remainder and becomes Cycle([#1]).
        //
        // This line used to `remove(self)`, with a comment claiming the sort would
        // still catch it. It did not — removing self made the entry orderable, so it
        // reached the evaluator as an unbound name and came back Failed. The comment
        // described the intent; the code did the opposite. D-033 makes them agree.
        //
        // A reference to an entry that is NOT on the tape stays filtered out below —
        // that is a different fault (nothing is waiting on `#99`) and stays Failed.
        // A ref in deadRefs is excluded EVEN IF an entry now wears that id: the
        // commit guard (D-036.1) forbids the edge, or the stranger would become a
        // real dependency of a formula that never meant it.
        return out.filter { id -> id !in entry.deadRefs && id in knownIds }.toSet()
    }

    private fun collectNames(e: Expr, into: MutableSet<String>) {
        val stack = ArrayDeque<Expr>()
        stack.addLast(e)
        while (stack.isNotEmpty()) {
            when (val node = stack.removeLast()) {
                is NameRef -> into += node.name
                is Unary -> stack.addLast(node.operand)
                is Binary -> { stack.addLast(node.right); stack.addLast(node.left) }
                is Postfix -> stack.addLast(node.operand)
                is Call -> node.args.asReversed().forEach { stack.addLast(it) }
                is UnitApply -> stack.addLast(node.value)
                is NumberLit -> {}
            }
        }
    }

    /** Kosaraju SCCs with explicit stacks: safe for the maximum history length. */
    private fun cycleComponents(
        ids: List<EntryId>, deps: Map<EntryId, Set<EntryId>>, positions: Map<EntryId, Int>,
    ): Map<EntryId, List<EntryId>> {
        data class Frame(val id: EntryId, val children: Iterator<EntryId>)
        val visited = HashSet<EntryId>()
        val finished = ArrayList<EntryId>(ids.size)
        for (start in ids) {
            if (!visited.add(start)) continue
            val stack = ArrayDeque<Frame>()
            stack.addLast(Frame(start, deps.getValue(start).iterator()))
            while (stack.isNotEmpty()) {
                val frame = stack.last()
                if (frame.children.hasNext()) {
                    val next = frame.children.next()
                    if (visited.add(next)) stack.addLast(Frame(next, deps.getValue(next).iterator()))
                } else {
                    finished += frame.id
                    stack.removeLast()
                }
            }
        }
        val reverse = ids.associateWith { ArrayList<EntryId>() }
        for ((id, edges) in deps) for (edge in edges) reverse.getValue(edge).add(id)
        visited.clear()
        val cycles = LinkedHashMap<EntryId, List<EntryId>>()
        for (start in finished.asReversed()) {
            if (!visited.add(start)) continue
            val stack = ArrayDeque<EntryId>()
            stack.addLast(start)
            val members = ArrayList<EntryId>()
            while (stack.isNotEmpty()) {
                val id = stack.removeLast()
                members += id
                for (next in reverse.getValue(id)) if (visited.add(next)) stack.addLast(next)
            }
            if (members.size > 1 || start in deps.getValue(start)) {
                val ordered = members.sortedBy { positions.getValue(it) }
                for (id in ordered) cycles[id] = ordered
            }
        }
        return cycles
    }

    /**
     * Kahn's algorithm over the non-cyclic entries. Cycle members (already
     * classified via their SCCs, D-036.5) are treated as resolved-but-valueless:
     * they never block draining, so an entry that waits on one drains normally and
     * rule 4 marks it `Blocked(by that member)`. With every SCC removed, what
     * remains is a DAG by construction — the drain always completes.
     */
    private fun topologicalOrder(
        ids: List<EntryId>,
        deps: Map<EntryId, Set<EntryId>>,
        cyclicSet: Set<EntryId>,
    ): List<EntryId> {
        val positions = ids.withIndex().associate { it.value to it.index }
        val pending = HashMap<EntryId, Int>()
        val dependents = ids.associateWith { ArrayList<EntryId>() }
        for (id in ids) {
            if (id in cyclicSet) continue
            val edges = deps.getValue(id).filterNot { it in cyclicSet }
            pending[id] = edges.size
            for (edge in edges) dependents.getValue(edge).add(id)
        }
        // Explicit initial capacity keeps this constructor available on API 23.
        val ready = PriorityQueue<EntryId>(11, compareBy { positions.getValue(it) })
        for (id in ids) if (pending[id] == 0) ready.add(id)
        val ordered = ArrayList<EntryId>()
        while (ready.isNotEmpty()) {
            val id = ready.remove()
            ordered += id
            for (next in dependents.getValue(id)) {
                val count = pending.getValue(next) - 1
                pending[next] = count
                if (count == 0) ready.add(next)
            }
        }
        check(ordered.size == pending.size) { "non-cyclic entries failed to drain — SCC detection is broken" }
        return ordered
    }
}
