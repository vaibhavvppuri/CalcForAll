package com.vaibhav.calc.core.name

/**
 * The enumerable registry of every function the engine can call.
 *
 * **Why this exists (Article 12).** The evaluator dispatches through two `when`
 * blocks over string literals, which no other component can read. Autocomplete
 * (11.5) and the keypad (11.1) both need the list of callable names. Re-typing
 * that list into an autocomplete file would be one rule in two places — and it
 * would rot *silently*: add a function and autocomplete never offers it; delete
 * one and autocomplete offers a name that errors.
 *
 * **How drift is made unrepresentable, rather than merely discouraged.** This
 * table is the evaluator's *gate*: `Evaluator.applyFunction` rejects any name that
 * is not in [ALL] before dispatching. So:
 *
 *  - accepted ⊆ [ALL] — by construction, because the gate runs first;
 *  - [ALL] ⊆ accepted — by test (`M6NamesSpec`: every listed name resolves and
 *    accepts its declared arity).
 *
 * Two inclusions, therefore equality. Autocomplete offering a name the engine
 * rejects, or the engine accepting a name autocomplete hides, is not a bug that
 * review has to catch — it is a state the pair cannot reach.
 */
object Functions {

    enum class Kind {
        /** Takes/returns [com.vaibhav.calc.core.eval.Value] — may be complex or carry units. */
        COMPLEX_AWARE,

        /** Real-only: arguments are unwrapped to `Num` before the call. */
        REAL,
    }

    /**
     * @param name the canonical, lowercase name — what autocomplete offers.
     * @param arities every argument count the function accepts (`log` takes 1 or 2).
     * @param aliases other spellings that dispatch to the same implementation.
     */
    data class Fn(
        val name: String,
        val arities: Set<Int>,
        val kind: Kind,
        val aliases: List<String> = emptyList(),
        val summary: String = "",
    ) {
        /** Every spelling that resolves to this function. */
        val spellings: List<String> get() = listOf(name) + aliases
    }

    /**
     * How a function is WRITTEN when a student writes it, where that differs from the
     * spelling the engine parses (D-034).
     *
     * `asin(` is what the grammar accepts; `sin⁻¹` is what the entry renders. Keeping
     * both in **one** map here — rather than in the 2D entry model *and* in the fn
     * palette — is Article 12: two copies of "what does `asin` look like" would drift,
     * and the drift would be invisible until a user saw two different labels for one
     * function. `M6NamesSpec` asserts the palette agrees with this map.
     *
     * Only the four the design maps are listed; everything else displays as it parses
     * (`ln` is `ln`, `log` is `log`), and [displayName] falls through to the name.
     */
    private val DISPLAY: Map<String, String> = mapOf(
        "asin" to "sin⁻¹",
        "acos" to "cos⁻¹",
        "atan" to "tan⁻¹",
        "cbrt" to "∛",
    )

    /** What [name] looks like on screen. Falls through to the engine spelling. */
    fun displayName(name: String): String = DISPLAY[name.lowercase()] ?: name

    private fun real(name: String, arity: Int, summary: String, vararg aliases: String) =
        Fn(name, setOf(arity), Kind.REAL, aliases.toList(), summary)

    private fun cx(name: String, arity: Int, summary: String, vararg aliases: String) =
        Fn(name, setOf(arity), Kind.COMPLEX_AWARE, aliases.toList(), summary)

    /** The one declaration. Mirrors `Evaluator.applyComplexAware`/`applyRealFunction`. */
    val ALL: List<Fn> = listOf(
        // complex-aware
        cx("abs", 1, "absolute value / modulus"),
        cx("sqrt", 1, "square root"),
        cx("re", 1, "real part", "real"),
        cx("im", 1, "imaginary part", "imag"),
        cx("conj", 1, "complex conjugate", "conjugate"),
        cx("arg", 1, "complex argument", "angle"),
        cx("cmplx", 2, "complex from real and imaginary parts", "complex", "rect"),
        cx("cpolar", 2, "complex from modulus and angle"),

        // powers and roots
        real("cbrt", 1, "cube root"),
        real("root", 2, "nth root: root(x, n)"),
        real("pow", 2, "x to the power y"),
        real("exp", 1, "e to the power x"),
        real("sq", 1, "square", "square"),
        real("approx", 1, "explicit approximate real value"),

        // logarithms
        real("ln", 1, "natural logarithm"),
        Fn("log", setOf(1, 2), Kind.REAL, emptyList(), "log10(x), or log(base, x)"),
        real("log10", 1, "base-10 logarithm", "lg"),
        real("log2", 1, "base-2 logarithm"),

        // trigonometry
        real("sin", 1, "sine"),
        real("cos", 1, "cosine"),
        real("tan", 1, "tangent"),
        real("asin", 1, "inverse sine", "arcsin"),
        real("acos", 1, "inverse cosine", "arccos"),
        real("atan", 1, "inverse tangent", "arctan"),
        real("atan2", 2, "angle of the point (x, y)"),

        // hyperbolics
        real("sinh", 1, "hyperbolic sine"),
        real("cosh", 1, "hyperbolic cosine"),
        real("tanh", 1, "hyperbolic tangent"),
        real("asinh", 1, "inverse hyperbolic sine"),
        real("acosh", 1, "inverse hyperbolic cosine"),
        real("atanh", 1, "inverse hyperbolic tangent"),

        // combinatorics
        real("fact", 1, "factorial", "factorial"),
        real("npr", 2, "permutations", "perm"),
        real("ncr", 2, "combinations", "comb", "choose"),

        // rounding and integers
        real("sign", 1, "sign of x", "sgn", "signum"),
        real("int", 1, "integer part", "trunc"),
        real("frac", 1, "fractional part"),
        real("floor", 1, "round down"),
        real("ceil", 1, "round up"),
        real("round", 1, "round to nearest"),
        real("mod", 2, "remainder"),
        real("min", 2, "smaller of two"),
        real("max", 2, "larger of two"),
        real("gcd", 2, "greatest common divisor"),
        real("lcm", 2, "least common multiple"),
        real("hypot", 2, "√(x²+y²)"),

        // angles and coordinates
        real("rad", 1, "degrees to radians", "torad"),
        real("deg", 1, "radians to degrees", "todeg"),
        real("dms", 3, "degrees/minutes/seconds to decimal"),
        real("polarr", 2, "polar radius from (x, y)"),
        real("polara", 2, "polar angle from (x, y)"),
        real("rectx", 2, "x from (r, θ)"),
        real("recty", 2, "y from (r, θ)"),

        // percent
        real("percent", 1, "x percent as a fraction"),
        real("percentof", 2, "x percent of y"),
        real("percentchange", 2, "percent change from x to y"),
    )

    private val bySpelling: Map<String, Fn> =
        ALL.flatMap { fn -> fn.spellings.map { it to fn } }.toMap()

    init {
        // A duplicate spelling would make one entry silently unreachable — the
        // registry would then claim a function the engine never dispatches to.
        val all = ALL.flatMap { it.spellings }
        val dupes = all.groupBy { it }.filterValues { it.size > 1 }.keys
        require(dupes.isEmpty()) { "duplicate function spellings: $dupes" }
    }

    /** Resolve any spelling (case-insensitive, matching the evaluator's dispatch). */
    fun get(name: String): Fn? = bySpelling[name.lowercase()]

    fun isFunction(name: String): Boolean = get(name) != null

    /** Every spelling the engine accepts — the superset autocomplete draws from. */
    fun spellings(): List<String> = bySpelling.keys.sorted()

    /** Canonical names only, for menus and keypads that shouldn't list aliases. */
    fun canonicalNames(): List<String> = ALL.map { it.name }.sorted()
}
