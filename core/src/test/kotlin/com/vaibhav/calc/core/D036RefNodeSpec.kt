package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.Atom
import com.vaibhav.calc.core.entry.Clipboard
import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.entry.Fraction
import com.vaibhav.calc.core.entry.PasteResult
import com.vaibhav.calc.core.entry.Power
import com.vaibhav.calc.core.entry.Ref
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.keypad.KeyAction
import com.vaibhav.calc.core.keypad.Keypad
import com.vaibhav.calc.core.tape.EntryId
import com.vaibhav.calc.core.tape.EntryValue
import com.vaibhav.calc.core.tape.Tape
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * **D-036.2 — `#n` is an ATOMIC `Ref` node.**
 *
 * BRAIN-RUN-REPORT F-2 (MAJOR): the badge inserted `#1` as two atoms, and the
 * fraction key's trailing-operand capture took only the digit (`'#'` fails
 * `isOperandChar`), splitting the reference into the unparseable `#(1)/(2)`
 * (B2-R-030). The ruling follows D-034's `Func` pattern: a reference is ONE
 * thing — one keystroke inserts it whole (the D-031.7 badge routes here), one
 * backspace removes it whole, and structure keys treat it as a single operand.
 *
 * Text stays honest: typed or pasted `#` merges with following digits into the
 * same node the badge builds, because the linearisation `#1` + `2` IS `#12` to
 * the parser — two trees for one text would be FM-1 by construction.
 */
class D036RefNodeSpec : StringSpec({

    "D-036.2: the badge inserts ONE node, linearising to the engine's #n" {
        val s = Editor.typeRef(EditorState(), 3)
        s.root.nodes.size shouldBe 1
        s.root.nodes[0] shouldBe Ref(3)
        s.toInput() shouldBe "#3"
    }

    "D-036.2: one backspace removes a reference whole — even multi-digit" {
        val s = Editor.typeRef(EditorState(), 12)
        Editor.backspace(s) shouldBe EditorState()
    }

    "D-036.2: the fraction key captures the reference as ONE operand (B2-R-030's shape)" {
        var s = Editor.typeRef(EditorState(), 1)
        s = Editor.typeFraction(s)
        s = Editor.insert(s, "2")
        s.root.nodes.size shouldBe 1
        s.root.nodes[0].shouldBeInstanceOf<Fraction>().numerator.nodes shouldBe listOf(Ref(1))
        s.toInput() shouldBe "(#1)/(2)"
    }

    "D-036.2: the power key captures the reference as ONE operand" {
        var s = Editor.typeRef(EditorState(), 2)
        s = Editor.typePower(s)
        s = Editor.insert(s, "3")
        s.root.nodes[0].shouldBeInstanceOf<Power>().base.nodes shouldBe listOf(Ref(2))
        s.toInput() shouldBe "(#2)^(3)"
    }

    "D-036.2: typed text builds the same node the badge builds" {
        val s = Editor.insert(EditorState(), "#12+3")
        s.root.nodes[0] shouldBe Ref(12)
        s.toInput() shouldBe "#12+3"
    }

    "D-036.2: a digit typed after a reference extends it — the text #1 then 2 IS #12" {
        var s = Editor.typeRef(EditorState(), 1)
        s = Editor.insert(s, '2')
        s.root.nodes shouldBe listOf(Ref(12))
        s.toInput() shouldBe "#12"
    }

    "D-036.2: navigation crosses a reference in one step each way" {
        val s = Editor.insert(Editor.typeRef(Editor.insert(EditorState(), "1+"), 2), "+3")
        // tree: [1, +, Ref(2), +, 3] with cursor at the end (offset 5)
        val left = Editor.moveLeft(Editor.moveLeft(Editor.moveLeft(s)))
        left.cursor.offset shouldBe 2 // stepped over '3', '+', then the WHOLE ref
        val right = Editor.moveRight(left)
        right.cursor.offset shouldBe 3
    }

    "D-036.2: pasted references parse and arrive atomic" {
        val r = Clipboard.pasteText(EditorState(), "#1+#2")
        val s = r.shouldBeInstanceOf<PasteResult.Pasted>().state
        s.root.nodes.filterIsInstance<Ref>() shouldBe listOf(Ref(1), Ref(2))
        (s.validate() is CalcResult.Ok) shouldBe true
    }

    "D-036.2: a bare '#' stays an honest atom that does not parse" {
        val s = Editor.insert(EditorState(), "#")
        s.root.nodes shouldBe listOf(Atom('#'))
        (s.validate() is CalcResult.Ok) shouldBe false
    }

    "D-036.2: Rule 3 untouched — a populated structure holding a ref still selects first" {
        var s = Editor.typeRef(EditorState(), 1)
        s = Editor.typeFraction(s)
        s = Editor.insert(s, "2")
        s = Editor.moveLeft(s) // DEN offset 0
        val sel = Editor.backspace(s)
        sel.selection shouldBe com.vaibhav.calc.core.entry.Selection(emptyList(), 0, 1)
    }

    "D-036.2: KeyAction.Ref goes through the one keystroke door" {
        Keypad.apply(EditorState(), KeyAction.Ref(3)) shouldBe Editor.typeRef(EditorState(), 3)
    }

    "D-036.2 + D-031.7: a badge-built reference commits with a live dependency edge" {
        val t = Tape()
        t.append(Editor.insert(EditorState(), "5"))
        var s = Editor.typeRef(EditorState(), 1)
        s = Editor.insert(s, "*2")
        t.append(s)
        val e = t.entry(EntryId(2))!!
        e.dependsOn shouldBe setOf(EntryId(1))
        e.value.shouldBeInstanceOf<EntryValue.Computed>()
    }

    "D-036.2: the 2D self-badge is a cycle of one, intact through a fraction" {
        val t = Tape()
        var s = Editor.typeRef(EditorState(), 1)
        s = Editor.typeFraction(s) // captures the ref whole into the numerator
        s = Editor.insert(s, "2")
        t.append(s)
        t.entry(EntryId(1))!!.value.shouldBeInstanceOf<EntryValue.Cycle>()
            .members shouldBe listOf(EntryId(1))
    }
})
