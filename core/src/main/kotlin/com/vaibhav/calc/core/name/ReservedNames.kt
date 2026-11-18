package com.vaibhav.calc.core.name

import com.vaibhav.calc.core.constants.Constants
import com.vaibhav.calc.core.units.Units

/** What an identifier means. Decided **statically** — never by stored state. */
enum class NameKind { UNKNOWN, REGISTER, CONSTANT, UNIT, FREE }

/**
 * The identifier namespace partition (D-016.4). Units go inline, so `km` cannot
 * also be a variable; equations go inline, so `x` cannot also be a register.
 * Every rule here is static: the same identifier always means the same thing,
 * regardless of what is stored (the D-016.1 invariant).
 *
 * **These two sets are the whole policy — override them here.**
 *  - [UNKNOWNS] `x, y, z` — case-insensitive (they collide with no unit/register).
 *  - [REGISTERS] `A–F, M` — **uppercase only**, so `M` (register) stays distinct
 *    from `m` (metre) and `E` (register) from `e` (Euler's number).
 *
 * `X`/`Y` are deliberately NOT registers — see the flagged deviation in D-016.4.
 */
object ReservedNames {

    /** Equation unknowns. Reserved: cannot be assigned. */
    val UNKNOWNS: Set<String> = setOf("x", "y", "z")

    /** Casio-style memory registers. Storable; substitute everywhere, incl. equations. */
    val REGISTERS: Set<String> = setOf("A", "B", "C", "D", "E", "F", "M")

    /**
     * Symbolic and physical constants resolved by the evaluator. Reserved.
     *
     * **Derived from [Constants], not hand-listed** (D-026). This was
     * `setOf("pi", "e", "i", "tau")` while the registry also held `c`, `phi`, `NA`
     * and the rest — so the reference browser taught a vocabulary the router did not
     * accept, and `phi` came back UnknownSymbol. One table, or two vocabularies.
     *
     * `i` is added here because the imaginary unit is resolved structurally by the
     * evaluator rather than from the registry — it is a [Complex], not a [Num].
     */
    val CONSTANTS: Set<String> = Constants.SYMBOLS + "i"

    fun isUnknown(name: String): Boolean = name.lowercase() in UNKNOWNS

    fun isRegister(name: String): Boolean = name in REGISTERS // case-sensitive on purpose

    // Case-sensitive on purpose: register `E` must not resolve to Euler's `e`.
    fun isConstant(name: String): Boolean = name in CONSTANTS

    fun isUnit(name: String): Boolean = Units.get(name) != null

    /**
     * `lakh`, `crore`, `dozen`: unit-table entries that carry no dimension. They are part of
     * the number — `5 lakh` IS 500000 — so a power written after one raises the whole number,
     * where a power written after a dimensioned unit belongs to the unit (`5 m^2`).
     */
    fun isNumberScale(name: String): Boolean = Units.get(name)?.dimension?.isDimensionless == true

    fun kindOf(name: String): NameKind = when {
        isUnknown(name) -> NameKind.UNKNOWN
        isConstant(name) -> NameKind.CONSTANT
        isRegister(name) -> NameKind.REGISTER
        isUnit(name) -> NameKind.UNIT
        else -> NameKind.FREE
    }

    /** Canonical unknown key, so `X` and `x` are the same unknown. */
    fun canonicalUnknown(name: String): String = name.lowercase()

    /**
     * Why [name] may not be assigned, or null if it may. Assigning to a reserved
     * name returns a typed error that NAMES the collision (D-016.4).
     */
    fun assignmentBlockedReason(name: String): String? = when (kindOf(name)) {
        NameKind.UNKNOWN -> "'$name' is an equation unknown and cannot be assigned; registers are ${REGISTERS.joinToString(", ")}"
        NameKind.CONSTANT -> "'$name' is a reserved constant and cannot be assigned"
        NameKind.UNIT -> "'$name' is a unit name and cannot be assigned"
        NameKind.FREE -> "'$name' is not a register; assignable registers are ${REGISTERS.joinToString(", ")}"
        NameKind.REGISTER -> null
    }
}
