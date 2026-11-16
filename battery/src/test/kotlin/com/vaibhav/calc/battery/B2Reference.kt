package com.vaibhav.calc.battery

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
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.eval.Value
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.parse.Parser
import com.vaibhav.calc.core.route.ExpressionRouter
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import com.vaibhav.calc.core.tape.EntryId

/**
 * B2's REFERENCE MODEL — an independent derivation of every entry's state, written
 * from **D-032's derivation table as text** (the seven rules, in precedence order),
 * not from `Tape.recompute`. Same public doors (`Parser`, `ExpressionRouter`,
 * `Environment`), none of the `Tape` code.
 *
 * The table, as implemented here:
 *  1. id in Kahn's non-orderable remainder            → Cycle(remainder, tape order)
 *  2. source blank                                    → Empty
 *  3. parse Err                                       → Failed
 *  4. any dependency handed no value                  → Blocked(first such, dep order)
 *  5. route Ok                                        → Computed
 *  6. route Err                                       → Failed
 *  7. route Ambiguous                                 → Ambiguous (no producer, D-031.8)
 *
 * D-036.5 resolved the two membership silences the first run probed: a bystander
 * depending on a loop is Blocked(by a member), and disjoint loops keep separate
 * member lists — Cycle = the entry's own SCC, in tape order.
 */
internal object B2Reference {

    data class Expect(
        val cls: String,                 // Computed | Empty | Failed | Blocked | Cycle | Ambiguous
        val deps: Set<EntryId>,
        val blockedBy: EntryId?,
        val cycleMembers: List<EntryId>?,
        val scalar: Value?,              // Computed's referenceable value (null for roots/verdicts)
        val hasScalar: Boolean,
    )

    /** Every `#k` a source names, existing or not — for the harness's I-DEADREF check. */
    fun refsIn(source: EditorState): Set<EntryId> {
        val program = (Parser.parseProgram(source.toInput()) as? CalcResult.Ok)?.value ?: return emptySet()
        val names = LinkedHashSet<String>()
        program.statements.forEach { st ->
            when (st) {
                is ExprStatement -> walk(st.expr, names)
                is Equation -> { walk(st.lhs, names); walk(st.rhs, names) }
                is Assignment -> walk(st.value, names)
            }
        }
        return names.filter { it.startsWith("#") }
            .mapNotNull { it.drop(1).toIntOrNull() }
            .map { EntryId(it) }.toSet()
    }

    /**
     * [dead] is each entry's commit-time dead-ref set (D-036.1). The BOOKKEEPING is
     * verified independently at write time by the harness's I-DEADREF check; this
     * classifier independently derives the CONSEQUENCES: dead refs never form
     * dependency edges, and a non-cyclic entry with dead refs is Failed.
     */
    fun derive(
        entries: List<Pair<EntryId, EditorState>>,
        settings: Settings,
        dead: Map<EntryId, Set<EntryId>> = emptyMap(),
    ): Map<EntryId, Expect> {
        val ids = entries.map { it.first }
        val inputs = entries.associate { (id, src) -> id to src.toInput() }
        val parsed = entries.associate { (id, _) -> id to Parser.parseProgram(inputs.getValue(id)) }

        // D-036.11: which registers each entry assigns.
        val assigns = entries.associate { (id, _) ->
            val program = (parsed.getValue(id) as? CalcResult.Ok)?.value
            id to (program?.statements?.filterIsInstance<Assignment>()
                ?.map { it.target }?.filter { it in ReservedNames.REGISTERS }?.toSet() ?: emptySet())
        }

        // Dependencies: #k names that exist on the tape, the row above when `ans` is
        // used, and — D-036.11 — the nearest prior assigner of each register read.
        // Self-references KEPT (D-033). Dead refs excluded EVEN IF an entry now wears
        // the id (D-036.1). Encounter order preserved.
        val deps = LinkedHashMap<EntryId, LinkedHashSet<EntryId>>()
        for ((i, pair) in entries.withIndex()) {
            val (id, _) = pair
            val out = LinkedHashSet<EntryId>()
            val program = (parsed.getValue(id) as? CalcResult.Ok)?.value
            if (program != null) {
                val names = LinkedHashSet<String>()
                program.statements.forEach { st ->
                    when (st) {
                        is ExprStatement -> walk(st.expr, names)
                        is Equation -> { walk(st.lhs, names); walk(st.rhs, names) }
                        is Assignment -> walk(st.value, names)
                    }
                }
                val deadHere = dead[id] ?: emptySet()
                for (n in names) {
                    if (n.startsWith("#")) n.drop(1).toIntOrNull()?.let { k ->
                        val ref = EntryId(k)
                        if (ref in ids && ref !in deadHere) out += ref
                    }
                    if (n.equals("ans", ignoreCase = true) && i > 0) out += entries[i - 1].first
                    if (n in ReservedNames.REGISTERS) {
                        for (j in i - 1 downTo 0) {
                            if (n in assigns.getValue(entries[j].first)) { out += entries[j].first; break }
                        }
                    }
                }
            }
            deps[id] = out
        }

        // D-036.5: cycle membership is SCC-based. An entry is cyclic iff it reaches
        // itself; its members are its own SCC in tape order. Cycle members never
        // block draining — dependants drain and land Blocked(by member) via rule 4.
        val reach = ids.associateWith { start ->
            val seen = LinkedHashSet<EntryId>()
            val stack = ArrayDeque(deps.getValue(start).toList())
            while (stack.isNotEmpty()) {
                val n = stack.removeLast()
                if (seen.add(n)) deps[n]?.forEach { stack.addLast(it) }
            }
            seen
        }
        val cyclicSet = ids.filter { it in reach.getValue(it) }.toSet()
        val remaining = ids.filterNot { it in cyclicSet }.toMutableSet()
        val ordered = ArrayList<EntryId>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { id -> deps.getValue(id).none { it in remaining } }
            if (ready.isEmpty()) break // defensive; cannot happen once SCCs are removed
            ready.sortedBy { ids.indexOf(it) }.forEach { ordered += it; remaining -= it }
        }

        val out = LinkedHashMap<EntryId, Expect>()
        for (id in cyclicSet) {
            val members = ids.filter { m -> m == id || (m in reach.getValue(id) && id in reach.getValue(m)) }
            out[id] = Expect("Cycle", deps.getValue(id), null, members, null, hasScalar = false)
        }
        val scalars = HashMap<EntryId, Value?>()
        for (id in ordered) {
            val input = inputs.getValue(id)
            val d = deps.getValue(id)
            val expect: Expect = when {
                input.isBlank() -> Expect("Empty", d, null, null, null, false)
                parsed.getValue(id) is CalcResult.Err -> Expect("Failed", d, null, null, null, false)
                // D-036.1: commit-time dead refs are a sticky Failed, before blocking.
                (dead[id] ?: emptySet()).isNotEmpty() -> Expect("Failed", d, null, null, null, false)
                else -> {
                    val blocker = d.firstOrNull { dep ->
                        out.getValue(dep).cls != "Computed" || !out.getValue(dep).hasScalar
                    }
                    if (blocker != null) Expect("Blocked", d, blocker, null, null, false)
                    else {
                        val env = Environment()
                        for (dep in d) scalars[dep]?.let { env.set(dep.reference, it) }
                        // D-036.11: register values flow in tape order — nearest wins.
                        for (dep in d.sortedBy { ids.indexOf(it) }) {
                            val v = scalars[dep] ?: continue
                            for (r in assigns.getValue(dep)) env.set(r, v)
                        }
                        val i = ids.indexOf(id)
                        if (i > 0) {
                            val prev = ids[i - 1]
                            if (out[prev]?.cls == "Computed" && out.getValue(prev).hasScalar) {
                                scalars[prev]?.let { env.setAns(it) }
                            }
                        }
                        when (val r = ExpressionRouter(settings, env).route(input)) {
                            is RouteResult.Ok -> {
                                val v = scalarOf(r.outcome)
                                Expect("Computed", d, null, null, v, v != null)
                            }
                            is RouteResult.Err -> Expect("Failed", d, null, null, null, false)
                            is RouteResult.Ambiguous -> Expect("Ambiguous", d, null, null, null, false)
                        }
                    }
                }
            }
            out[id] = expect
            if (expect.cls == "Computed") scalars[id] = expect.scalar
        }
        return out
    }

    private fun scalarOf(o: Outcome): Value? = when (o) {
        is Outcome.Scalar -> o.value
        is Outcome.Stored -> o.value
        else -> null
    }

    private fun walk(e: Expr, into: MutableSet<String>) {
        when (e) {
            is NameRef -> into += e.name
            is Unary -> walk(e.operand, into)
            is Binary -> { walk(e.left, into); walk(e.right, into) }
            is Postfix -> walk(e.operand, into)
            is Call -> e.args.forEach { walk(it, into) }
            is UnitApply -> walk(e.value, into)
            is NumberLit -> {}
        }
    }
}
