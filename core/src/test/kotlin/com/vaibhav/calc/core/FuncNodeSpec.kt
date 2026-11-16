package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.entry.Func
import com.vaibhav.calc.core.entry.toInput
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.keypad.FnPalette
import com.vaibhav.calc.core.keypad.KeyAction
import com.vaibhav.calc.core.keypad.Keypad
import com.vaibhav.calc.core.name.Functions
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * **M-1 / G-3 — the `Func` entry node (D-034).**
 *
 * The four properties `DESIGN-HANDOFF-M7-2.md` §4.4 requires, each asserted:
 *  1. a container with a single `arg` field — one keystroke creates it, one
 *     backspace removes it whole (D-018's axiom);
 *  2. display name ≠ engine spelling (`asin` shows as `sin⁻¹`);
 *  3. `flatten`/`toInput` emits the ENGINE spelling, so it round-trips;
 *  4. the fn drawer inserts by engine name while labelling by display name.
 */
class FuncNodeSpec : StringSpec({

    val c = Calculator()

    // --- 1 · one keystroke, one node; one backspace, gone -------------------

    "D-034: one keystroke creates exactly ONE node" {
        val s = Editor.typeFunc(EditorState(), "sin")
        s.root.nodes.size shouldBe 1
        s.root.nodes[0].shouldBeInstanceOf<Func>().name shouldBe "sin"
    }

    "D-034: the cursor lands INSIDE the argument, so the next keystroke fills it" {
        var s = Editor.typeFunc(EditorState(), "sin")
        s = Editor.insert(s, '3')
        s = Editor.insert(s, '0')
        s.toInput() shouldBe "sin(30)"
        s.root.nodes.size shouldBe 1 // still one node — the digits went inside
    }

    "D-034: ONE backspace removes an empty function whole — D-018's axiom" {
        // Before this node existed, `sin` was the text `s`,`i`,`n`,`(` — four atoms
        // and four backspaces to undo one press. Now: type it, step out, one ⌫.
        var s = Editor.typeFunc(EditorState(), "sin")
        s.root.nodes.size shouldBe 1
        s = Editor.backspace(s) // empty arg, at offset 0 -> collapse the structure
        s.root.nodes.size shouldBe 0
        s.toInput() shouldBe ""
    }

    "D-034: a POPULATED function is selected first, then deleted — Rule 3 (D-019)" {
        // Destruction requires visibility: the first ⌫ selects, the second deletes.
        var s = Editor.typeFunc(EditorState(), "sin")
        s = Editor.insert(s, '3')
        s = Editor.moveRight(s) // step out of the argument
        s = Editor.backspace(s) // selects the whole function
        s.selection shouldNotBe null
        s.root.nodes.size shouldBe 1
        s = Editor.backspace(s) // now it dies
        s.root.nodes.size shouldBe 0
    }

    // --- 2 · display name ≠ engine spelling ---------------------------------

    "D-034: display name differs from the engine spelling, and the TREE holds the engine's" {
        val s = Editor.typeFunc(EditorState(), "asin")
        val f = s.root.nodes[0].shouldBeInstanceOf<Func>()
        f.name shouldBe "asin"          // what the parser reads
        f.displayName shouldBe "sin⁻¹"  // what a renderer draws
        f.name shouldNotBe f.displayName
    }

    "D-034: the four mapped functions, and the fall-through for everything else" {
        Functions.displayName("asin") shouldBe "sin⁻¹"
        Functions.displayName("acos") shouldBe "cos⁻¹"
        Functions.displayName("atan") shouldBe "tan⁻¹"
        Functions.displayName("cbrt") shouldBe "∛"
        // ln and log render as themselves, per the design
        Functions.displayName("ln") shouldBe "ln"
        Functions.displayName("log") shouldBe "log"
        Functions.displayName("sin") shouldBe "sin"
    }

    // --- 3 · flatten emits the engine form and round-trips -------------------

    "D-034: toInput emits the ENGINE spelling, and the engine can read it" {
        var s = Editor.typeFunc(EditorState(), "asin")
        s = Editor.insert(s, '1')
        s.toInput() shouldBe "asin(1)" // NOT "sin⁻¹(1)"
        s.validate().shouldBeInstanceOf<CalcResult.Ok<*>>()
        c.num(s.toInput()).compareTo(Num.of(90)) shouldBe 0 // asin(1) = 90° in DEG
    }

    "D-034: a function built by keystrokes evaluates to the right number" {
        var s = Editor.typeFunc(EditorState(), "sin")
        s = Editor.insert(s, '3'); s = Editor.insert(s, '0')
        c.num(s.toInput()).compareTo(Num.of(1, 2)) shouldBe 0
    }

    "D-034: NESTED functions work and round-trip" {
        var s = Editor.typeFunc(EditorState(), "asin")
        s = Editor.typeFunc(s, "sin")   // nested inside the argument
        s = Editor.insert(s, '3'); s = Editor.insert(s, '0')
        s.toInput() shouldBe "asin(sin(30))"
        s.validate().shouldBeInstanceOf<CalcResult.Ok<*>>()
        c.num(s.toInput()).compareTo(Num.of(30)) shouldBe 0
    }

    "D-034: a function composes with the other structures" {
        // sqrt is a Root structure; a Func inside it must still linearise correctly.
        var s = Editor.typeRoot(EditorState())
        s = Editor.typeFunc(s, "abs")
        s = Editor.insert(s, '-'); s = Editor.insert(s, '4')
        s.toInput() shouldBe "sqrt(abs(-4))"
        c.num(s.toInput()).compareTo(Num.of(2)) shouldBe 0
    }

    // --- 4 · the palette inserts by engine name, labels by display name ------

    "D-034: every palette function entry inserts a Func NODE, not text" {
        val entries = FnPalette.entries.filter { it.name != null && Functions.isFunction(it.name!!) }
        entries.isEmpty() shouldBe false
        entries.forEach { e ->
            // structures (√, xʸ, a⁄b) keep their own actions; function entries are Func
            if (e.insert is KeyAction.Func) {
                (e.insert as KeyAction.Func).name shouldBe e.name
            }
        }
        // the trig entries specifically
        val asin = FnPalette.entries.first { it.label == "sin⁻¹" }
        asin.insert.shouldBeInstanceOf<KeyAction.Func>().name shouldBe "asin"
    }

    "D-034: palette labels AGREE with the registry's display names (Article 12)" {
        // Two maps of "what does asin look like" would drift silently. This asserts
        // they are one truth: wherever the registry has a display name, the palette
        // entry for that function uses it.
        FnPalette.entries.forEach { e ->
            val n = e.name
            if (n != null && Functions.isFunction(n) && e.insert is KeyAction.Func) {
                val display = Functions.displayName(n)
                if (display != n) e.label shouldBe display
            }
        }
    }

    "D-034: a palette pick goes through the SAME door and yields one node" {
        val asin = FnPalette.entries.first { it.label == "sin⁻¹" }
        var s = Keypad.apply(EditorState(), asin)
        s.root.nodes.size shouldBe 1
        s = Editor.insert(s, '1')
        s.toInput() shouldBe "asin(1)"
    }

    "D-034: no keystroke path produces an unbalanced paren any more" {
        // The old `Insert(\"sin(\")` produced exactly that. Node.kt's invariant holds
        // for functions too now, because the paren is not a node at all.
        var s = Editor.typeFunc(EditorState(), "sin")
        s.root.nodes.none { it is com.vaibhav.calc.core.entry.Atom } shouldBe true
        s = Editor.insert(s, '1')
        s.validate().shouldBeInstanceOf<CalcResult.Ok<*>>()
    }
})
