package com.vaibhav.calc.core.eval

/**
 * Variable bindings and the `ans` register (spec 12.3). Values are [Value]s, so
 * `ans` can hold a complex result. Names are case-sensitive except `ans`, which
 * is normalised. Constants (π, e, i) are NOT stored here — the evaluator resolves
 * them structurally so they stay exact.
 */
class Environment {
    private val vars = HashMap<String, Value>()

    fun set(name: String, value: Value) {
        vars[normalize(name)] = value
    }

    fun get(name: String): Value? = vars[normalize(name)]

    fun has(name: String): Boolean = vars.containsKey(normalize(name))

    /**
     * Unbind [name]. Clearing a register must make it *empty*, not zero: storing 0
     * would leave the user unable to tell "nothing stored" from "stored zero", and
     * a later `A+1` would answer 1 with total confidence (Article 11).
     */
    fun remove(name: String) {
        vars.remove(normalize(name))
    }

    fun setAns(value: Value) = set("ans", value)

    fun ans(): Value? = get("ans")

    fun clear() = vars.clear()

    fun names(): Set<String> = vars.keys.toSet()

    private fun normalize(name: String): String =
        if (name.equals("ans", ignoreCase = true)) "ans" else name
}
