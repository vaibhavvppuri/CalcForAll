package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.Atom
import com.vaibhav.calc.core.entry.Clip
import com.vaibhav.calc.core.entry.Clipboard
import com.vaibhav.calc.core.entry.Row
import com.vaibhav.calc.core.entry.field
import com.vaibhav.calc.core.entry.fields
import com.vaibhav.calc.core.lex.Tokenizer
import com.vaibhav.calc.core.entry.CopyResult
import com.vaibhav.calc.core.entry.CutResult
import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.entry.PasteResult
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.route.ExpressionRouter
import com.vaibhav.calc.core.route.RouteResult
import com.vaibhav.calc.core.keypad.FnPalette
import com.vaibhav.calc.core.keypad.Key
import com.vaibhav.calc.core.keypad.KeyAction
import com.vaibhav.calc.core.keypad.Keypad
import com.vaibhav.calc.core.keypad.Layer
import com.vaibhav.calc.core.name.Autocomplete
import com.vaibhav.calc.core.name.Functions
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.name.SuggestionKind
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.tape.Registers
import com.vaibhav.calc.core.tape.StoreResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain as shouldContainText
import io.kotest.matchers.types.shouldBeInstanceOf

/** M6 — keypad model (11.1), autocomplete (11.5), clipboard (11.7), registers/ANS (12.2/12.3). */
class M6SessionSpec : StringSpec({

    // ================= 11.1 keypad model =================

    "the committed flick mapping is ALL FOUR: ÷↑√, ×↑xʸ, −↑a⁄b, .↑π" {
        val k = Keypad.basic()
        k.key("divide")!!.flickUp shouldBe KeyAction.Root
        k.key("divide")!!.flickUpLabel shouldBe "√"
        k.key("multiply")!!.flickUp shouldBe KeyAction.Power
        k.key("multiply")!!.flickUpLabel shouldBe "xʸ"
        k.key("minus")!!.flickUp shouldBe KeyAction.Fraction
        k.key("minus")!!.flickUpLabel shouldBe "a⁄b"
        // The fourth: a constant, not a structure — which is why it was the one lost
        // when the mapping was relayed as three.
        k.key("point")!!.flickUp shouldBe KeyAction.Insert("π")
        k.key("point")!!.flickUpLabel shouldBe "π"
        k.keys.count { it.flickUp != null } shouldBe 4
    }
    ".↑π inserts the constant the engine actually knows" {
        val point = Keypad.basic().key("point")!!
        Keypad.flickUp(EditorState(), point).toInput() shouldBe "π"
        // π is not decoration: it must evaluate.
        ExpressionRouter(Settings(), Environment())
            .route(Keypad.flickUp(EditorState(), point).toInput())
            .shouldBeInstanceOf<RouteResult.Ok>()
    }
    "a flick builds the structure; the tap on the same key still types the operator" {
        val divide = Keypad.basic().key("divide")!!
        Keypad.press(EditorState(), divide).toInput() shouldBe "/"
        // Flicking ÷ up gives a root, not a division.
        Keypad.flickUp(EditorState(), divide).toInput() shouldNotBe "/"
        Keypad.flickUp(EditorState(), divide).root.nodes.size shouldBe 1
    }
    "− flicked up builds a fraction with load-bearing parentheses" {
        val minus = Keypad.basic().key("minus")!!
        var s = Keypad.flickUp(EditorState(), minus)
        s = Editor.insert(s, "1")
        s.toInput() shouldBe "((1)/())"
    }
    "a flick on a key with no flick action does nothing — it does not fall back to the tap" {
        val plus = Keypad.basic().key("plus")!!
        plus.flickUp shouldBe null
        Keypad.flickUp(EditorState(), plus).toInput() shouldBe ""
    }
    "there is no C or AC key (D-018)" {
        val ids = Keypad.layouts().flatMap { it.keys }.map { it.id.lowercase() }
        ids.contains("c") shouldBe false
        ids.contains("ac") shouldBe false
        ids.contains("clear") shouldBe false
    }

    // --- the redline: ONE resting state ---------------------------------------

    "there is exactly ONE layout — two resting states is the design's definition of failure" {
        // §04's engineer's test: "if you can screenshot the keypad in two materially
        // different resting states, we have failed."
        Keypad.layouts().size shouldBe 1
        Keypad.layouts().single().name shouldBe "basic"
    }
    "the default surface shows User A NO scientific keys — the disqualified direction is gone" {
        val labels = Keypad.basic().keys.map { it.label }
        for (sci in listOf("sin", "cos", "tan", "ln", "log", "√", "n!", "x²", "eˣ")) {
            withClue("'$sci' is on the default pad — that is the always-on scientific pad") {
                labels.contains(sci) shouldBe false
            }
        }
    }
    "the fn doorway exists, and it is the ONLY concession on the default surface" {
        val fn = Keypad.basic().key("fn")!!
        fn.tap shouldBe KeyAction.OpenPalette
        // Exactly one key opens the palette. A second doorway would be chrome.
        Keypad.basic().keys.count { it.tap == KeyAction.OpenPalette } shouldBe 1
    }
    "opening the palette edits NOTHING — it is summoned, not a mode" {
        val s = Editor.insert(EditorState(), "2+3")
        Keypad.apply(s, KeyAction.OpenPalette) shouldBe s
    }
    "⌫ sits in the bottom arc, one slot left of = — not the top-right corner" {
        val rows = Keypad.basic().rows
        rows.first().map { it.id }.contains("backspace") shouldBe false // NOT the top row
        val last = rows.last().map { it.id }
        last shouldBe listOf("0", "point", "backspace", "equals")
        last.indexOf("backspace") shouldBe last.indexOf("equals") - 1
    }
    "Ans is not a keypad key — it is a display chip, and it was in ⌫'s slot" {
        Keypad.basic().key("ans") shouldBe null
    }

    // --- 11.1 the contextual fn palette ---------------------------------------

    "the palette carries the long tail: every v1 function is reachable" {
        val reachable = FnPalette.entries.mapNotNull { it.name }.toSet()
        for (must in listOf("sin", "cos", "tan", "asin", "ln", "log", "sqrt", "cbrt", "fact", "ncr", "abs")) {
            withClue("'$must' is unreachable from the palette") { reachable.contains(must) shouldBe true }
        }
    }
    "EVERY palette entry names a function the engine actually has (Article 12)" {
        val bad = FnPalette.entries.mapNotNull { it.name }
            .filterNot { Functions.isFunction(it) || ReservedNames.isConstant(it) }
        bad shouldBe emptyList()
    }
    "EVERY palette entry, applied and completed, produces something the ENGINE CAN READ" {
        val broken = mutableListOf<String>()
        for (e in FnPalette.entries) {
            val applied = Keypad.apply(Editor.insert(EditorState(), "2"), e)
            val completed = Editor.insert(applied, "3")
            val r = ExpressionRouter(Settings(), Environment()).route(completed.toInput())
            if (r is RouteResult.Err && (r.error is EvalError.Syntax || r.error is EvalError.UnknownSymbol)) {
                broken += "${e.label} -> '${completed.toInput()}'"
            }
        }
        broken shouldBe emptyList()
    }
    "the palette is SEARCHABLE — this is what carries Hick's Law" {
        FnPalette.search("sin").map { it.label } shouldContain "sin"
        FnPalette.search("log").map { it.label } shouldContain "log"
        FnPalette.search("sq").map { it.label } shouldContain "x²"
    }
    "search finds an entry by the label a student knows, not only the engine's spelling" {
        // A student looking for the inverse sine types sin⁻¹; the engine calls it asin.
        FnPalette.search("sin⁻¹").map { it.name } shouldContain "asin"
    }
    "a STRUCTURE is findable by the word for it — √ answers to 'sqrt'" {
        // √ builds a Root node rather than calling sqrt(), but they are one operation.
        // An entry that answers only to its glyph is unfindable by anyone who types
        // the word, which is the whole job of the search index.
        FnPalette.search("sqrt").map { it.label } shouldContain "√"
        FnPalette.search("pow").map { it.label } shouldContain "xʸ"
    }
    "an empty query returns nothing — every function is not a search result" {
        FnPalette.search("") shouldBe emptyList()
    }
    "the palette is categorised — an unknown function is found by group, not by scrolling 50" {
        FnPalette.categories.size shouldBe 7
        FnPalette.categories.all { it.entries.isNotEmpty() } shouldBe true
        FnPalette.entries.size shouldBe FnPalette.categories.sumOf { it.entries.size }
    }
    "a palette entry goes through the SAME door as a keystroke" {
        val sin = FnPalette.entries.first { it.name == "sin" }
        Keypad.apply(EditorState(), sin) shouldBe Keypad.apply(EditorState(), sin.insert)
    }
    "backspace hold repeats the same delete as a tap — it is not a bigger hammer" {
        var s = Editor.insert(EditorState(), "123")
        val tap = Keypad.apply(s, KeyAction.Backspace)
        val hold = Keypad.apply(s, KeyAction.BackspaceRepeat)
        tap.toInput() shouldBe "12"
        hold.toInput() shouldBe tap.toInput()
    }
    /**
     * The 2nd layer survives as a MODEL affordance but nothing uses it: with the
     * palette carrying the tail and the flick carrying the common four, no key needs
     * an alternate. `Layer.SECOND` therefore falls through to the tap everywhere —
     * asserted, so that "unused" is a fact rather than an assumption.
     */
    "no key needs a 2nd layer any more — the flick and the palette carry the tail" {
        for (key in Keypad.basic().keys) {
            key.second shouldBe null
            key.action(Layer.SECOND) shouldBe key.tap
        }
    }
    "EVERY key that inserts a call names a function the engine actually has (Article 12)" {
        val bad = Keypad.layouts().flatMap { it.keys }
            .flatMap { listOfNotNull(it.tap, it.second, it.flickUp) }
            .filterIsInstance<KeyAction.Insert>()
            .map { it.text }
            .filter { it.endsWith("(") }
            .map { it.dropLast(1) }
            .filterNot { Functions.isFunction(it) }
        bad shouldBe emptyList()
    }
    "a flick action without a label cannot be constructed — an unlabelled flick is undiscoverable" {
        val threw = try {
            Key("x", "x", KeyAction.Insert("1"), flickUp = KeyAction.Power)
            false
        } catch (e: IllegalArgumentException) {
            true
        }
        threw shouldBe true
    }
    /**
     * **This test replaces a vacuous one.** The original asserted only that `press`
     * did not throw — which is true of `sin(`, an unbalanced, unparseable state that
     * shipped. "Didn't crash" is not "works"; a test that cannot fail is Article 11's
     * shape wearing a green tick.
     *
     * The honest claim: **a key, followed by an operand, must parse.** Every key on
     * the pad exists to be pressed mid-expression and then completed. If pressing it
     * and typing a number cannot produce a readable program, the key is broken. This
     * is uniform across digits, operators, structures and functions — no key gets an
     * exemption, so no key can hide.
     */
    /**
     * **The third version of this test. The first two were both too weak, and each
     * weakness was found by breaking a key rather than by reading the test.**
     *
     *  1. *"press() doesn't throw"* — passed `sin(`, which shipped unparseable.
     *  2. *"the result parses"* — still passed a `sin` key with the paren dropped,
     *     because `2sin3` **is** valid syntax: `sin` lexes as a name and implicit
     *     multiplication does the rest. `validate()` is `parseProgram`, i.e. syntax
     *     only; the evaluator is what knows `sin` is not a variable.
     *
     * So the claim has to be what a key actually promises: **the engine can READ what
     * this key produces.** Syntax and unknown-symbol errors mean the key emitted
     * something the engine does not understand. Domain/arity errors are fine — those
     * are real mathematics answering a real question.
     */
    "EVERY key, pressed after an operand, produces something the ENGINE CAN READ" {
        val env = Environment().apply { setAns(RealValue(Num.of(7))) } // Ans is only pressed after a result

        // "The engine can read it" = not a Syntax or UnknownSymbol error. A Domain or
        // Arity error is fine: those are the engine UNDERSTANDING the input and
        // answering it. `sin()` is Arity — it knows sin, the argument is just not typed
        // yet. `sin` alone is UnknownSymbol — it does not know what that is at all.
        fun readable(s: EditorState): Boolean {
            val r = ExpressionRouter(Settings(), env).route(s.toInput())
            return !(r is RouteResult.Err && (r.error is EvalError.Syntax || r.error is EvalError.UnknownSymbol))
        }

        val broken = mutableListOf<String>()
        for (key in Keypad.layouts().flatMap { it.keys }.distinctBy { it.id }) {
            val pressed = Keypad.press(Editor.insert(EditorState(), "2"), key)
            // A key must read immediately, or read once one more operand is typed —
            // which is the whole of what a mid-expression keypress can honestly promise.
            if (!readable(pressed) && !readable(Editor.insert(pressed, "3"))) {
                broken += "${key.id} -> '${pressed.toInput()}' / '${Editor.insert(pressed, "3").toInput()}'"
            }
        }
        broken shouldBe emptyList()
    }

    /**
     * The one exclusion above, asserted rather than assumed. Scoping the test to
     * "after an operand" is only honest if the excluded case is stated: `÷` pressed
     * into an empty display gives `/3`, which the parser MUST refuse — a leading
     * binary operator is a user error, and refusing it is correct. A test that
     * quietly skipped this would be hiding its own coverage gap.
     */
    "a binary operator pressed into an empty display is REFUSED, not silently accepted" {
        for (id in listOf("divide", "multiply")) {
            val key = Keypad.basic().key(id)!!
            val s = Editor.insert(Keypad.press(EditorState(), key), "3")
            withClue("'${s.toInput()}' must not parse") { (s.validate() is CalcResult.Err) shouldBe true }
        }
        // −3 and +3 ARE valid — they are unary, not leading binary operators.
        for (id in listOf("minus", "plus")) {
            val s = Editor.insert(Keypad.press(EditorState(), Keypad.basic().key(id)!!), "3")
            withClue("'${s.toInput()}' must parse — it is unary") {
                (s.validate() is CalcResult.Ok) shouldBe true
            }
        }
    }

    "EVERY flick, followed by an operand, produces something the parser accepts" {
        val broken = mutableListOf<String>()
        for (key in Keypad.basic().keys.filter { it.flickUp != null }) {
            val flicked = Keypad.flickUp(Editor.insert(EditorState(), "2"), key)
            val completed = Editor.insert(flicked, "3")
            if (completed.validate() is CalcResult.Err) {
                broken += "${key.id} flick -> '${completed.toInput()}'"
            }
        }
        broken shouldBe emptyList()
    }

    "the 2nd layer holds to the same standard" {
        val broken = mutableListOf<String>()
        for (key in Keypad.basic().keys.filter { it.second != null }) {
            val pressed = Keypad.press(Editor.insert(EditorState(), "2"), key, Layer.SECOND)
            val completed = Editor.insert(pressed, "3")
            if (completed.validate() is CalcResult.Err) broken += "${key.id} 2nd -> '${completed.toInput()}'"
        }
        broken shouldBe emptyList()
    }

    "no keystroke sequence can build an unbalanced paren — Node.kt's invariant, asserted" {
        // Mechanically derived from the tokenizer's OWN accepted alphabet (A3), so it
        // cannot hand-exclude the character that breaks it.
        var s = EditorState()
        for (c in Tokenizer.ACCEPTED_CHARACTERS) s = Editor.insert(s, c)
        parenAtoms(s.root) shouldBe emptyList()
    }

    // ================= 11.5 autocomplete =================

    "autocomplete offers functions from the real registry" {
        val s = Autocomplete.suggest("si").map { it.text }
        s shouldContain "sin"
        s shouldContain "sinh"
    }
    "a function suggestion inserts its open paren — 'sin' alone is an unknown symbol" {
        val sin = Autocomplete.suggest("sin").first { it.text == "sin" }
        sin.insert shouldBe "sin("
        sin.kind shouldBe SuggestionKind.FUNCTION
    }
    "autocomplete offers units, constants and registers, each labelled by kind" {
        Autocomplete.suggest("km").first { it.text == "km" }.kind shouldBe SuggestionKind.UNIT
        Autocomplete.suggest("pi").first { it.text == "pi" }.kind shouldBe SuggestionKind.CONSTANT
        Autocomplete.suggest("A").first { it.text == "A" }.kind shouldBe SuggestionKind.REGISTER
    }
    "an empty prefix suggests nothing — every name is not a suggestion" {
        Autocomplete.suggest("") shouldBe emptyList()
        Autocomplete.suggest("   ") shouldBe emptyList()
    }
    "an exact match ranks first" {
        Autocomplete.suggest("log").first().text shouldBe "log"
        Autocomplete.suggest("sin").first().text shouldBe "sin"
    }
    "case matters where the engine says it matters: m offers the metre before the register M" {
        val s = Autocomplete.suggest("m").map { it.text }
        s.indexOf("m") shouldBe 0
    }
    "suggestAt completes the identifier under the cursor, not the whole input" {
        val input = "2+sq"
        Autocomplete.prefixAt(input, input.length) shouldBe "sq"
        Autocomplete.suggestAt(input, input.length).map { it.text } shouldContain "sqrt"
    }
    "suggestAt does not complete inside a number — 2e is a literal, not the constant e" {
        Autocomplete.suggestAt("2e", 2) shouldBe emptyList()
        Autocomplete.prefixAt("2e", 2) shouldBe ""
    }
    "an empty register is still offered, and says it is empty" {
        val env = Environment()
        val a = Autocomplete.suggest("A", env).first { it.text == "A" }
        a.summary shouldBe "register (empty)"
        env.set("A", RealValue(Num.of(5)))
        Autocomplete.suggest("A", env).first { it.text == "A" }.summary shouldBe "register"
    }
    "EVERY suggestion is a name the engine accepts (Article 12, end to end)" {
        // The proof that matters to the user: nothing the UI offers is rejected.
        val rejected = Autocomplete.suggest("s", limit = 100)
            .filter { it.kind == SuggestionKind.FUNCTION }
            .map { it.text }
            .filterNot { Functions.isFunction(it) }
        rejected shouldBe emptyList()
    }

    // ================= 11.7 clipboard =================

    "copy with nothing selected is NothingSelected, not an empty clip" {
        Clipboard.copy(Editor.insert(EditorState(), "12")) shouldBe CopyResult.NothingSelected
    }
    "copy takes the selected node; paste puts it back as structure" {
        var s = Editor.insert(EditorState(), "12")
        s = Editor.select(s, emptyList(), 0) // select the '1'
        val clip = (Clipboard.copy(s) as CopyResult.Copied).clip
        clip.toInput() shouldBe "1"
        val pasted = Clipboard.paste(Editor.insert(EditorState(), "9"), clip)
        pasted.toInput() shouldBe "91"
    }
    "copying a fraction and pasting it yields a FRACTION, not flattened text" {
        var s = Editor.typeFraction(EditorState())
        s = Editor.insert(s, "1")
        s = Editor.moveRight(s)
        s = Editor.insert(s, "2")
        s.toInput() shouldBe "((1)/(2))"
        // select the fraction node at the root
        val sel = Editor.select(s.copy(cursor = com.vaibhav.calc.core.entry.Cursor()), emptyList(), 0)
        val clip = (Clipboard.copy(sel) as CopyResult.Copied).clip
        clip.nodes.size shouldBe 1
        clip.nodes.first().shouldBeInstanceOf<com.vaibhav.calc.core.entry.Fraction>()
        Clipboard.paste(EditorState(), clip).toInput() shouldBe "((1)/(2))"
    }
    "cut removes what it copied" {
        var s = Editor.insert(EditorState(), "12")
        s = Editor.select(s, emptyList(), 0)
        val r = Clipboard.cut(s).shouldBeInstanceOf<CutResult.Cut>()
        r.clip.toInput() shouldBe "1"
        r.state.toInput() shouldBe "2"
    }
    "cut with nothing selected is NothingSelected" {
        Clipboard.cut(Editor.insert(EditorState(), "1")) shouldBe CutResult.NothingSelected
    }
    "pasting text the engine cannot read reports WHY, rather than silently doing nothing" {
        val r = Clipboard.pasteText(EditorState(), "((").shouldBeInstanceOf<PasteResult.NotReadable>()
        r.text shouldBe "(("
    }
    "pasting readable text inserts it through the same path a keystroke takes" {
        val r = Clipboard.pasteText(EditorState(), "2+3").shouldBeInstanceOf<PasteResult.Pasted>()
        r.state.toInput() shouldBe "2+3"
        (r.state.validate() as CalcResult.Ok)
    }
    "paste replaces the selection" {
        var s = Editor.insert(EditorState(), "12")
        s = Editor.select(s, emptyList(), 0) // select '1'
        val out = Clipboard.paste(s, Clip(Editor.insert(EditorState(), "9").root.nodes))
        out.toInput() shouldBe "92"
    }

    // ================= 12.2 / 12.3 registers and ANS =================

    "registers are a live view over the environment, never a copy" {
        val env = Environment()
        val regs = Registers(env)
        regs.get("A")!!.isEmpty shouldBe true
        env.set("A", RealValue(Num.of(7))) // stored via the router's path, not the panel
        regs.get("A")!!.isEmpty shouldBe false // the panel sees it anyway
    }
    "an empty register is empty, NOT zero" {
        val regs = Registers(Environment())
        regs.get("A")!!.value shouldBe null
        regs.occupied() shouldBe emptyList()
        regs.all().size shouldBe 7 // A-F, M
    }
    "clearing a register makes it empty again, not zero" {
        val env = Environment()
        val regs = Registers(env)
        regs.store("A", RealValue(Num.of(5)))
        regs.get("A")!!.isEmpty shouldBe false
        regs.clear("A")
        regs.get("A")!!.isEmpty shouldBe true
        env.get("A") shouldBe null
    }
    "storing to a non-register is blocked, and names the collision" {
        val regs = Registers(Environment())
        val r = regs.store("x", RealValue(Num.of(1))).shouldBeInstanceOf<StoreResult.Blocked>()
        r.reason shouldContainText "unknown"
        regs.store("km", RealValue(Num.of(1))).shouldBeInstanceOf<StoreResult.Blocked>()
        regs.store("pi", RealValue(Num.of(1))).shouldBeInstanceOf<StoreResult.Blocked>()
    }
    "the register panel and the expression language agree on what is storable" {
        val regs = Registers(Environment())
        for (name in com.vaibhav.calc.core.name.ReservedNames.REGISTERS) {
            regs.store(name, RealValue(Num.of(1))).shouldBeInstanceOf<StoreResult.Stored>()
        }
    }
    "registers list in A-F then M order, not alphabetically" {
        Registers(Environment()).all().map { it.name } shouldBe listOf("A", "B", "C", "D", "E", "F", "M")
    }
    "ans is null before anything is computed — not zero" {
        Registers(Environment()).ans() shouldBe null
    }
})

private fun <T> withClue(clue: String, block: () -> T): T =
    try {
        block()
    } catch (e: AssertionError) {
        throw AssertionError("$clue\n${e.message}")
    }

/** Every `(`/`)` that reached the tree as a bare atom — must always be empty. */
private fun parenAtoms(r: Row): List<Char> = r.nodes.flatMap { n ->
    when (n) {
        is Atom -> if (n.char == '(' || n.char == ')') listOf(n.char) else emptyList()
        else -> n.fields().flatMap { f -> parenAtoms(n.field(f)) }
    }
}
