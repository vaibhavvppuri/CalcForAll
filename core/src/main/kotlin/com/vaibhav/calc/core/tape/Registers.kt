package com.vaibhav.calc.core.tape

import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Value
import com.vaibhav.calc.core.name.ReservedNames

/** What one register holds. Empty is a state, not a zero (spec 12.2). */
data class RegisterSlot(val name: String, val value: Value?) {
    val isEmpty: Boolean get() = value == null
}

/**
 * The register panel's model (spec 12.2) and `ans` (12.3).
 *
 * **A read-only view over the live [Environment]** — it stores nothing itself.
 * A register panel keeping its own copy of A–F would be one rule in two places
 * (Article 12), and the copy would drift the moment `5 → A` ran through the
 * router instead of through the panel.
 *
 * **Empty is not zero.** Showing `0` for an untouched register is a confident
 * wrong answer: the user cannot tell "I stored nothing" from "I stored zero", and
 * would read a stale computation as a real one (Article 11).
 */
class Registers(private val env: Environment) {

    /** Every register, in the canonical A–F, M order — including the empty ones. */
    fun all(): List<RegisterSlot> =
        ReservedNames.REGISTERS.sortedWith(canonicalOrder).map { RegisterSlot(it, env.get(it)) }

    fun get(name: String): RegisterSlot? =
        if (ReservedNames.isRegister(name)) RegisterSlot(name, env.get(name)) else null

    /** Registers holding something — what a compact panel shows. */
    fun occupied(): List<RegisterSlot> = all().filterNot { it.isEmpty }

    /** The last computed value, or null if nothing has been computed yet. */
    fun ans(): Value? = env.ans()

    /**
     * Store [value] into [name].
     *
     * Refuses non-registers through the **same** rule the router's `5 → A` uses
     * ([ReservedNames.assignmentBlockedReason]), so the panel and the expression
     * language can never disagree about what is storable.
     */
    fun store(name: String, value: Value): StoreResult {
        ReservedNames.assignmentBlockedReason(name)?.let { return StoreResult.Blocked(it) }
        env.set(name, value)
        return StoreResult.Stored(RegisterSlot(name, value))
    }

    /** Clearing makes a register EMPTY, not zero. */
    fun clear(name: String): StoreResult {
        ReservedNames.assignmentBlockedReason(name)?.let { return StoreResult.Blocked(it) }
        env.remove(name)
        return StoreResult.Stored(RegisterSlot(name, null))
    }

    private companion object {
        /** A–F then M — the Casio order the user confirmed, not alphabetical (M would sort mid-list). */
        val canonicalOrder = Comparator<String> { a, b ->
            fun rank(s: String) = if (s == "M") 1 else 0
            compareValuesBy(a, b, { rank(it) }, { it })
        }
    }
}

sealed interface StoreResult {
    data class Stored(val slot: RegisterSlot) : StoreResult
    /** Names the collision, rather than failing mutely (D-016.4). */
    data class Blocked(val reason: String) : StoreResult
}
