package com.vaibhav.calc.core.keypad

import com.vaibhav.calc.core.name.Autocomplete
import com.vaibhav.calc.core.name.Functions
import com.vaibhav.calc.core.name.Suggestion
import com.vaibhav.calc.core.name.SuggestionKind

/**
 * One entry in the palette. [insert] is the action, so the palette cannot offer
 * anything the editor cannot perform — the same guarantee [KeyAction] gives the pad.
 */
data class PaletteEntry(
    val label: String,
    val insert: KeyAction,
    val summary: String = "",
    /** The spelling the engine knows, for the search index. Null for structures. */
    val name: String? = null,
)

data class PaletteCategory(val title: String, val entries: List<PaletteEntry>)

/**
 * The contextual fn palette (spec 11.1) — **the other half of the shipped answer.**
 *
 * The design's committed direction is *"Flick + contextual fn palette"*, composite
 * **8.9**. It is two mechanisms, and they carry different laws:
 *
 *  - the **flick** keeps the pad tiny, which is Fitts (9);
 *  - the **palette** makes the long tail *searchable*, which is Hick (8) and
 *    Recognition (8) — the two criteria where long-press collapses to 4 and 3.
 *
 * Modelling the flick alone and answering Hick with a second static keypad rebuilt
 * `Keypad.scientific()` — the direction the design's own redline **DISQUALIFIED**:
 * *"User A meets 30+ scientific keys on the default surface. The fx-991's answer —
 * and our own first build."* It is deleted. There is one resting state, which is
 * §04's engineer's test: *if you can screenshot the keypad in two materially
 * different resting states, we have failed.*
 *
 * **Transient by construction.** The palette is not a mode: it holds no state of its
 * own, and an entry produces a [KeyAction] the caller applies and dismisses. There is
 * nothing here to be stranded in.
 *
 * **Article 12: nothing here is hand-listed.** Every function entry is checked against
 * [Functions] at construction, and [search] delegates to [Autocomplete], which draws
 * on the live registries. A palette advertising a function the engine lacks fails
 * loudly here rather than under the user's thumb.
 */
object FnPalette {

    private fun fn(label: String, name: String, summary: String = ""): PaletteEntry {
        require(Functions.isFunction(name)) {
            "palette entry '$label' inserts '$name', which is not a registered function"
        }
        return PaletteEntry(
            label = label,
            // ONE node, not the text `sin(` (D-034). The palette and the keypad now
            // build the same structure, so a function reached either way backspaces
            // in one press.
            insert = KeyAction.Func(name),
            summary = summary.ifEmpty { Functions.get(name)?.summary ?: "" },
            name = name,
        )
    }

    /**
     * A structure entry — `√` builds a [Root] node rather than calling `sqrt(…)`.
     *
     * [name] is the engine's spelling for the SAME operation, and it is there for the
     * search index: a student who types "sqrt" is looking for `√`, and an entry that
     * answers only to its glyph is unfindable by the word for it. The structure and
     * the call are one operation, so they are one search key.
     */
    private fun structure(label: String, action: KeyAction, summary: String, name: String? = null): PaletteEntry {
        require(name == null || Functions.isFunction(name)) { "structure '$label' claims unknown function '$name'" }
        return PaletteEntry(label, action, summary, name)
    }

    /**
     * The categories, in the order they are shown.
     *
     * Categorisation is what Hick's Law actually buys: it is not decoration, it is
     * the difference between scanning ~50 items and scanning one group of six.
     */
    val categories: List<PaletteCategory> = listOf(
        PaletteCategory(
            "Trigonometry",
            listOf(
                fn("sin", "sin"), fn("cos", "cos"), fn("tan", "tan"),
                // Rendered as the student writes them; `asin` is the spelling the
                // engine knows, and the palette is where that translation belongs.
                fn("sin⁻¹", "asin"), fn("cos⁻¹", "acos"), fn("tan⁻¹", "atan"),
                fn("sinh", "sinh"), fn("cosh", "cosh"), fn("tanh", "tanh"),
                fn("sinh⁻¹", "asinh"), fn("cosh⁻¹", "acosh"), fn("tanh⁻¹", "atanh"),
            ),
        ),
        PaletteCategory(
            "Powers & roots",
            listOf(
                structure("xʸ", KeyAction.Power, "raise to a power", name = "pow"),
                structure("√", KeyAction.Root, "square root", name = "sqrt"),
                fn("∛", "cbrt"),
                fn("ⁿ√", "root", "nth root: root(x, n)"),
                fn("x²", "sq", "square"),
                fn("eˣ", "exp"),
            ),
        ),
        PaletteCategory(
            "Logarithms",
            listOf(fn("ln", "ln"), fn("log", "log"), fn("log₁₀", "log10"), fn("log₂", "log2")),
        ),
        PaletteCategory(
            "Numbers",
            listOf(
                structure("a⁄b", KeyAction.Fraction, "fraction"),
                fn("|x|", "abs"),
                fn("n!", "fact"),
                fn("nPr", "npr"), fn("nCr", "ncr"),
                fn("round", "round"), fn("⌊x⌋", "floor"), fn("⌈x⌉", "ceil"),
                fn("mod", "mod"), fn("gcd", "gcd"), fn("lcm", "lcm"),
            ),
        ),
        PaletteCategory(
            "Constants",
            listOf(
                PaletteEntry("π", KeyAction.Insert("π"), "pi", "pi"),
                PaletteEntry("e", KeyAction.Insert("e"), "Euler's number", "e"),
            ),
        ),
        PaletteCategory(
            "Angles & coordinates",
            listOf(
                fn("→rad", "rad"), fn("→deg", "deg"), fn("DMS", "dms"),
                fn("hypot", "hypot"), fn("atan2", "atan2"),
            ),
        ),
        PaletteCategory(
            "Complex",
            listOf(fn("Re", "re"), fn("Im", "im"), fn("conj", "conj"), fn("arg", "arg")),
        ),
    )

    val entries: List<PaletteEntry> get() = categories.flatMap { it.entries }

    /**
     * Search the palette. **This is what carries Hick's Law** — *"you cannot search
     * what has no index"* is exactly why long-press scored 4.
     *
     * Delegates to [Autocomplete], so the palette and the type-ahead rank the same
     * names the same way, and neither can offer what the engine would reject.
     */
    fun search(query: String, limit: Int = 12): List<PaletteEntry> {
        if (query.isBlank()) return emptyList()
        val byName = entries.filter { it.name != null }.associateBy { it.name }
        val suggested: List<PaletteEntry> = Autocomplete.suggest(query, limit = limit)
            .filter { it.kind == SuggestionKind.FUNCTION || it.kind == SuggestionKind.CONSTANT }
            .mapNotNull { s: Suggestion -> byName[s.text] }
        // A student searching "inverse sine" types `sin⁻¹`, not `asin` — the label is
        // part of the index, or the palette only answers to names the user came to
        // find. Registry order is preserved so the list does not reshuffle.
        val byLabel = entries.filter { it.label.startsWith(query, ignoreCase = true) }
        return (suggested + byLabel).distinct().take(limit)
    }

    init {
        // A duplicate label would make one entry unreachable by search; a duplicate
        // action would mean two entries doing the same thing under different names.
        val labels = entries.map { it.label }
        require(labels.size == labels.toSet().size) {
            "duplicate palette labels: ${labels.groupBy { it }.filterValues { it.size > 1 }.keys}"
        }
    }
}
