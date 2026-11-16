package com.vaibhav.calc.core.keypad

import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.name.Functions

/**
 * What a key does.
 *
 * **Every case maps onto an operation [Editor] actually has** (Article 12). The
 * keypad therefore cannot express a keystroke the editor can't perform — "the
 * button exists but does nothing" is unreachable, because [Keypad.apply] is a
 * total function over this type and the compiler checks the match is exhaustive.
 *
 * There is deliberately no `Clear` case: no C, no AC, no long-press-to-clear
 * (D-018). "Clear the tape" is a TAPE action, not a keystroke.
 */
sealed interface KeyAction {
    /** Literal text: digits, operators, constants, and function names (with their open paren). */
    data class Insert(val text: String) : KeyAction

    /** Structure creation — the 2D forms the editor builds. */
    object Fraction : KeyAction
    object Power : KeyAction
    object Root : KeyAction

    /**
     * A function application, built as ONE node (D-034). [name] is the engine's
     * spelling (`asin`); the display form (`sin⁻¹`) is derived, never stored.
     *
     * This replaced `Insert("sin(")`, which produced four atoms plus an unbalanced
     * paren for a single press — four backspaces to undo one keystroke, which is
     * exactly what D-018's axiom forbids.
     */
    data class Func(val name: String) : KeyAction

    /**
     * A tape reference, inserted whole (D-036.2). Not a key on the pad — the
     * D-031.7 badge is the one affordance that fires this, and it can only name
     * an entry that exists, which is half of what keeps references honest (the
     * commit guard, D-036.1, is the other half).
     */
    data class Ref(val n: Int) : KeyAction
    data object Ans : KeyAction
    object OpenParen : KeyAction
    object CloseParen : KeyAction

    /** ⌫ tap: delete exactly one thing (D-018). */
    object Backspace : KeyAction

    /** ⌫ hold: repeat delete — the inherited convention, not a clear (D-018). */
    object BackspaceRepeat : KeyAction

    object Left : KeyAction
    object Right : KeyAction

    /** Commit the entry to the tape. Handled by the session, not the editor. */
    object Equals : KeyAction

    /**
     * Open the contextual fn palette ([FnPalette]) — the one visible doorway.
     *
     * The design charges this honestly against its highest-weighted criterion: the
     * `fn` key is visible to User A, who never presses it, and that is *"the single
     * dent in her otherwise perfect surface"*. It buys Fitts and Hick in full. It is
     * not a mode: the palette is summoned, drops a token in, and dismisses — the
     * keypad you return to is the one you left.
     */
    object OpenPalette : KeyAction
}

/**
 * One key.
 *
 * @param flickUp the action a short upward swipe performs. The design committed
 *   to **up only** — modelling flick-down/left/right would be the model inventing
 *   interactions the design has not made.
 * @param second the action under the 2nd-function layer.
 */
data class Key(
    val id: String,
    val label: String,
    val tap: KeyAction,
    val flickUp: KeyAction? = null,
    val flickUpLabel: String? = null,
    val second: KeyAction? = null,
    val secondLabel: String? = null,
) {
    init {
        require(flickUp == null || flickUpLabel != null) {
            // An unlabelled flick is an undiscoverable one: the affordance IS the label.
            "key '$id' has a flick action with no label"
        }
        require(second == null || secondLabel != null) { "key '$id' has a 2nd action with no label" }
    }

    /** The action for a press, given whether the 2nd layer is engaged. */
    fun action(layer: Layer): KeyAction = when (layer) {
        Layer.PRIMARY -> tap
        Layer.SECOND -> second ?: tap
    }

    /** What the key reads as, given the layer. */
    fun labelFor(layer: Layer): String = when (layer) {
        Layer.PRIMARY -> label
        Layer.SECOND -> secondLabel ?: label
    }
}

enum class Layer { PRIMARY, SECOND }

data class KeypadLayout(val name: String, val rows: List<List<Key>>) {
    val keys: List<Key> get() = rows.flatten()
    fun key(id: String): Key? = keys.firstOrNull { it.id == id }
}

/**
 * The keypad model (spec 11.1). **Model only — the view is M7.**
 *
 * **One layout. One resting state.** The design's engineer's test for "is this a
 * mode?" is: *if you can screenshot the keypad in two materially different resting
 * states, we have failed.* There was a `scientific()` layout here — 12 function keys
 * on a second pad — and it was the direction the design's redline DISQUALIFIED:
 * *"User A meets 30+ scientific keys on the default surface. The fx-991's answer —
 * and our own first build."* Deleted. The tail lives in [FnPalette], summoned and
 * dismissed.
 *
 * The flick mapping is the design's committed one: **÷ ↑ √, × ↑ xʸ, − ↑ a⁄b, . ↑ π**.
 * The first three pair an operator with the structure a student reaches for next to
 * it; the fourth is the odd one out — it inserts a *constant*, not a structure —
 * which is presumably how it went missing when the mapping was described to me as
 * three. The design is authoritative.
 */
object Keypad {

    private fun digit(d: Char) = Key(d.toString(), d.toString(), KeyAction.Insert(d.toString()))

    // --- the committed flick keys ------------------------------------------
    private val divide = Key(
        id = "divide", label = "÷", tap = KeyAction.Insert("/"),
        flickUp = KeyAction.Root, flickUpLabel = "√",
    )
    private val multiply = Key(
        id = "multiply", label = "×", tap = KeyAction.Insert("*"),
        flickUp = KeyAction.Power, flickUpLabel = "xʸ",
    )
    private val minus = Key(
        id = "minus", label = "−", tap = KeyAction.Insert("-"),
        flickUp = KeyAction.Fraction, flickUpLabel = "a⁄b",
    )
    /**
     * The fourth committed flick: **. ↑ π**. It inserts a constant rather than
     * building a structure, which is why it does not fit the "operator pairs with the
     * structure beside it" story the other three tell — and why it was the one lost.
     */
    private val point = Key(
        id = "point", label = ".", tap = KeyAction.Insert("."),
        flickUp = KeyAction.Insert("π"), flickUpLabel = "π",
    )

    private val plus = Key("plus", "+", KeyAction.Insert("+"))
    private val equals = Key("equals", "=", KeyAction.Equals)
    private val openParen = Key("lparen", "(", KeyAction.OpenParen)
    private val closeParen = Key("rparen", ")", KeyAction.CloseParen)

    /** The one visible doorway to the long tail. */
    private val fnKey = Key("fn", "fn", KeyAction.OpenPalette)

    /**
     * ⌫ — **in the bottom arc, one slot left of `=`. Not the top-right corner.**
     *
     * It sat top-right, which is the worst reach in portrait for a right thumb, on a
     * top-5 key. The design made this exact mistake, caught it in its own rescore, and
     * moved it: *"a frequent target moved toward the pivot, not away"* — Fitts holds
     * at 9 because of it. This build made the same mistake independently and is fixing
     * it from the design's finding.
     */
    private val backspace = Key("backspace", "⌫", KeyAction.Backspace)

    /**
     * The one layout.
     *
     * `Ans` is deliberately **not** a key: it is a chip in the display area, next to
     * `S⇔D`, where the design puts it. It was occupying the slot ⌫ needed.
     */
    fun basic(): KeypadLayout = KeypadLayout(
        "basic",
        listOf(
            listOf(fnKey, openParen, closeParen, divide),
            listOf(digit('7'), digit('8'), digit('9'), multiply),
            listOf(digit('4'), digit('5'), digit('6'), minus),
            listOf(digit('1'), digit('2'), digit('3'), plus),
            listOf(digit('0'), point, backspace, equals),
        ),
    )

    /** There is exactly one. Kept as a list so callers cannot assume otherwise. */
    fun layouts(): List<KeypadLayout> = listOf(basic())

    /**
     * Perform [action] on [state].
     *
     * The single place a keystroke becomes an edit. [KeyAction.Equals] returns the
     * state unchanged: committing is the session's job (the tape must receive it),
     * and quietly doing nothing here would hide that.
     */
    fun apply(state: EditorState, action: KeyAction): EditorState = when (action) {
        is KeyAction.Insert -> Editor.insert(state, action.text)
        KeyAction.Fraction -> Editor.typeFraction(state)
        KeyAction.Power -> Editor.typePower(state)
        KeyAction.Root -> Editor.typeRoot(state)
        is KeyAction.Func -> Editor.typeFunc(state, action.name)
        is KeyAction.Ref -> Editor.typeRef(state, action.n)
        KeyAction.Ans -> Editor.typeAns(state)
        KeyAction.OpenParen -> Editor.typeParen(state)
        KeyAction.CloseParen -> Editor.typeCloseParen(state)
        KeyAction.Backspace -> Editor.backspace(state)
        // Hold repeats the SAME operation — it is not a distinct, more destructive
        // key. One hold step = one tap (D-018); the repeat rate is the view's.
        KeyAction.BackspaceRepeat -> Editor.backspace(state)
        KeyAction.Left -> Editor.moveLeft(state)
        KeyAction.Right -> Editor.moveRight(state)
        KeyAction.Equals -> state
        // Opening the palette edits nothing. The entry the user picks is applied
        // through this same function, so the palette gains no second edit path.
        KeyAction.OpenPalette -> state
    }

    /** Apply a palette entry. The same door every keystroke goes through. */
    fun apply(state: EditorState, entry: PaletteEntry): EditorState = apply(state, entry.insert)

    fun press(state: EditorState, key: Key, layer: Layer = Layer.PRIMARY): EditorState =
        apply(state, key.action(layer))

    /** A flick on a key with no flick action is a no-op, not a fallback to the tap. */
    fun flickUp(state: EditorState, key: Key): EditorState =
        key.flickUp?.let { apply(state, it) } ?: state
}
