package com.vaibhav.calc.core.entry

import com.vaibhav.calc.core.name.Functions

/**
 * The 2D entry tree (spec 11.2) — the MODEL, not a view.
 *
 * An [Atom] is one character, so the cursor and backspace behave exactly like a
 * text field (an inherited convention). Everything else is a *structure*, and each
 * structure was created by exactly ONE keystroke — which is precisely what
 * backspace inverts (D-018).
 */
sealed interface Node

/** One character. Cursor motion and ⌫ are per-atom, i.e. per character. */
data class Atom(val char: Char) : Node

/** Created by `/`. */
data class Fraction(val numerator: Row, val denominator: Row) : Node

/** Created by `^` (or an x² key). */
data class Power(val base: Row, val exponent: Row) : Node

/** Created by `√`. [index] is null for a square root. */
data class Root(val index: Row?, val radicand: Row) : Node

/**
 * Created by `(`. **Balanced by construction — unbalanced parens are unrepresentable.**
 *
 * This is an invariant, not an aspiration, and it is worth stating why it needed
 * saying: it was **false** for a while. `Editor.insert(state, '(')` made an
 * `Atom('(')`, so every function key produced an unbalanced paren and this comment
 * lied to anyone who read it. A false invariant in a comment is worse than no
 * invariant, because the next person builds on it.
 *
 * What makes it true: `Editor.insert` routes `(` and `)` to [Editor.typeParen] /
 * [Editor.typeCloseParen], so there is exactly one way a paren enters the tree.
 * `M6EntrySpec` asserts it by exhausting the tokenizer's own accepted alphabet —
 * no character sequence the editor accepts can produce an `Atom('(')`.
 */
data class Paren(val body: Row) : Node

/**
 * A function application — `sin(…)`, `asin(…)` — created by ONE keystroke (D-034).
 *
 * **Why this is a structure and not four atoms.** Pressing `sin` used to insert the
 * characters `s`, `i`, `n`, `(` — four nodes needing four backspaces, which breaks
 * D-018's axiom that *"each structure was created by exactly ONE keystroke — which is
 * precisely what backspace inverts."* As a container it obeys the axiom for free: one
 * press builds it, one press (Rule 3: select, then delete) removes it whole.
 *
 * **[name] is the ENGINE's spelling, never the display form.** `asin` is stored;
 * `sin⁻¹` is what a renderer shows via [Functions.displayName]. Keeping the engine
 * word in the tree is what lets [toInput] emit `asin(…)` and round-trip through the
 * real parser — a tree holding `sin⁻¹` could not.
 *
 * One field, so collapsing from [Field.ARG] can only happen while the argument is
 * empty (the editor only collapses an empty row) — no content is ever destroyed,
 * exactly as for [Root] and [Paren].
 */
data class Func(val name: String, val arg: Row) : Node {
    /** What a renderer shows: `asin` → `sin⁻¹`. Never what [toInput] emits. */
    val displayName: String get() = Functions.displayName(name)
}

/**
 * A tape reference — `#n` as ONE node (D-036.2), following [Func]'s pattern.
 *
 * **Why a node and not atoms.** The D-031.7 badge inserts `#1`; as two atoms the
 * fraction key's trailing-operand capture took only the digit (`'#'` is not an
 * operand char) and split the reference into the unparseable `#(1)/(2)` —
 * BRAIN-RUN-REPORT F-2. As a leaf node it is one keystroke to insert, one ⌫ to
 * remove, one operand to capture, and one step to walk past.
 *
 * A digit typed immediately after a Ref EXTENDS it (`#1` then `2` becomes `#12`),
 * because that is what the linearised text means to the parser — two trees for
 * one text would be the FM-1 divergence by construction.
 */
data class Ref(val n: Int) : Node

/** The answer chip is one visible operand and one editing operation (D-037). */
data object Ans : Node

/** An ordered run of nodes. The cursor's offset is an index between nodes. */
data class Row(val nodes: List<Node> = emptyList()) {
    val size: Int get() = nodes.size
    val isEmpty: Boolean get() = nodes.isEmpty()
}

/**
 * Linearise to the engine's own text grammar.
 *
 * **The parentheses are load-bearing, not cosmetic.** A `Fraction([1,+,2], [3])`
 * *displays* as (1+2)/3, so it must linearise as `((1+2)/(3))`. Emitting `1+2/3`
 * would make the parser read something the user never wrote — the editor and the
 * grammar disagreeing, which is exactly the drift Article 12 forbids.
 *
 * **The outer pair is load-bearing too** (1.3). A fraction or a power is ONE value on
 * screen, whatever is typed after it. Without the outer brackets the parser took what
 * followed into the last field: ½ followed by `lakh` read as 1/(2 lakh) — a tenth of a
 * millionth of what was drawn, with no error — ½ followed by `%` as 1/(2%), and 2³ followed
 * by `!` as 2^(3!). A unit, a number word and the postfix operators all bind tighter than
 * `/`, and the postfix ones tighter than `^`.
 *
 * An empty field linearises to `()`, which does not parse — and that is correct:
 * a half-typed entry is genuinely not yet valid, and the *parser* says so rather
 * than a second copy of its rules.
 */
fun Row.toInput(): String = nodes.joinToString("") { it.toInput() }

fun Node.toInput(): String = when (this) {
    is Atom -> char.toString()
    is Ref -> "#$n" // the engine's reference grammar (lex/Tokenizer.entryReference)
    Ans -> "ans"
    is Fraction -> "((${numerator.toInput()})/(${denominator.toInput()}))"
    is Power -> "((${base.toInput()})^(${exponent.toInput()}))"
    is Root ->
        if (index == null) "sqrt(${radicand.toInput()})"
        else "root((${radicand.toInput()}),(${index.toInput()}))"
    is Paren -> "(${body.toInput()})"
    // The ENGINE's spelling, never the display form — this string goes to the parser.
    is Func -> "$name(${arg.toInput()})"
}

/** Which field of a structure a path step descends into. */
enum class Field { NUMERATOR, DENOMINATOR, BASE, EXPONENT, INDEX, RADICAND, BODY, ARG }

/** The fields of a structure, in **creation order** — which is also traversal order. */
fun Node.fields(): List<Field> = when (this) {
    is Atom -> emptyList()
    is Ref -> emptyList()
    Ans -> emptyList()
    is Fraction -> listOf(Field.NUMERATOR, Field.DENOMINATOR)
    is Power -> listOf(Field.BASE, Field.EXPONENT)
    is Root -> if (index == null) listOf(Field.RADICAND) else listOf(Field.RADICAND, Field.INDEX)
    is Paren -> listOf(Field.BODY)
    is Func -> listOf(Field.ARG)
}

fun Node.field(f: Field): Row = when (this) {
    is Fraction -> if (f == Field.NUMERATOR) numerator else denominator
    is Power -> if (f == Field.BASE) base else exponent
    is Root -> if (f == Field.RADICAND) radicand else (index ?: Row())
    is Paren -> body
    is Func -> arg
    is Atom -> error("an atom has no fields")
    is Ref -> error("a reference has no fields")
    Ans -> error("ans has no fields")
}

fun Node.withField(f: Field, row: Row): Node = when (this) {
    is Fraction -> if (f == Field.NUMERATOR) copy(numerator = row) else copy(denominator = row)
    is Power -> if (f == Field.BASE) copy(base = row) else copy(exponent = row)
    is Root -> if (f == Field.RADICAND) copy(radicand = row) else copy(index = row)
    is Paren -> copy(body = row)
    is Func -> copy(arg = row)
    is Atom -> error("an atom has no fields")
    is Ref -> error("a reference has no fields")
    Ans -> error("ans has no fields")
}

/**
 * The content that survives when this structure COLLAPSES, in reading order
 * (D-018: collapse never destroys content).
 */
fun Node.survivingContent(collapsingFrom: Field): List<Node> =
    fields().filter { it != collapsingFrom }.flatMap { field(it).nodes }
