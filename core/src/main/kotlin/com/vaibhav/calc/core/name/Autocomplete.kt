package com.vaibhav.calc.core.name

import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.units.Units

/** What a suggestion is, so the UI can icon/group it — and so `m` (metre) reads differently from `M` (register). */
enum class SuggestionKind { FUNCTION, UNIT, CONSTANT, REGISTER, UNKNOWN }

/**
 * @param insert the exact text to insert — `sin(` for a function, `km` for a unit.
 *   The UI must never re-derive this: a suggestion that inserts something the
 *   engine can't read would be the editor inventing grammar.
 */
data class Suggestion(
    val text: String,
    val kind: SuggestionKind,
    val summary: String = "",
    val insert: String = text,
)

/**
 * Name completion (spec 11.5).
 *
 * **Every candidate is drawn from a live registry** — [Functions.spellings],
 * [Units.names], [ReservedNames] (Article 12). Nothing here is hand-typed, so a
 * function added to the engine is offered the same day, and a name this offers is
 * one the engine provably accepts (see `M6NamesSpec`'s two-inclusion proof).
 */
object Autocomplete {

    /**
     * Suggestions for [prefix], best first.
     *
     * An empty [prefix] yields nothing: with no characters typed, every name in
     * the engine "matches", which is not a suggestion — it's a phone book.
     */
    fun suggest(prefix: String, env: Environment? = null, limit: Int = 8): List<Suggestion> {
        if (prefix.isBlank()) return emptyList()
        return candidates(env)
            .filter { it.text.startsWith(prefix, ignoreCase = true) }
            .sortedWith(rank(prefix))
            .take(limit)
    }

    /**
     * Suggestions for the identifier the cursor sits at the end of.
     *
     * Uses the SAME notion of an identifier character the tokenizer does, so a
     * prefix this finds is one the tokenizer would have lexed as a name.
     */
    fun suggestAt(input: String, cursor: Int, env: Environment? = null, limit: Int = 8): List<Suggestion> {
        val end = cursor.coerceIn(0, input.length)
        var start = end
        while (start > 0 && isIdentPart(input[start - 1])) start--
        // A name cannot start with a digit — `2e` is a literal, not the name `e`.
        if (start < end && input[start].isDigit()) return emptyList()
        return suggest(input.substring(start, end), env, limit)
    }

    /** The identifier prefix [suggestAt] would complete — exposed so the UI can highlight it. */
    fun prefixAt(input: String, cursor: Int): String {
        val end = cursor.coerceIn(0, input.length)
        var start = end
        while (start > 0 && isIdentPart(input[start - 1])) start--
        if (start < end && input[start].isDigit()) return ""
        return input.substring(start, end)
    }

    private fun isIdentPart(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c == '_' || c.isDigit()

    private fun candidates(env: Environment?): List<Suggestion> = buildList {
        for (fn in Functions.ALL) {
            for (spelling in fn.spellings) {
                // Inserting the open paren is the point: `sin` alone is an unknown
                // symbol, `sin(` is the start of a call.
                add(Suggestion(spelling, SuggestionKind.FUNCTION, fn.summary, insert = "$spelling("))
            }
        }
        for (name in Units.names()) {
            add(Suggestion(name, SuggestionKind.UNIT, "unit"))
        }
        for (name in ReservedNames.CONSTANTS) {
            add(Suggestion(name, SuggestionKind.CONSTANT, "constant"))
        }
        for (name in ReservedNames.REGISTERS) {
            // Show what a register holds; an empty one is still offered, because
            // "A is empty" is a fact worth seeing, not a reason to hide the key.
            val held = env?.get(name)
            add(Suggestion(name, SuggestionKind.REGISTER, if (held != null) "register" else "register (empty)"))
        }
        for (name in ReservedNames.UNKNOWNS) {
            add(Suggestion(name, SuggestionKind.UNKNOWN, "equation unknown"))
        }
    }

    /**
     * Exact match first, then case-sensitive prefix hits over case-insensitive
     * ones (so typing `m` offers the metre before the register `M`), then shorter
     * names, then alphabetical for a stable, non-jumping list.
     */
    private fun rank(prefix: String): Comparator<Suggestion> =
        compareBy<Suggestion> { if (it.text.equals(prefix, ignoreCase = true)) 0 else 1 }
            .thenBy { if (it.text.startsWith(prefix)) 0 else 1 }
            .thenBy { it.text.length }
            .thenBy { it.text }
}
