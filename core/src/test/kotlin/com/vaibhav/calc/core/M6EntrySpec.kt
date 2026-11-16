package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.Atom
import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.entry.Field
import com.vaibhav.calc.core.entry.Fraction
import com.vaibhav.calc.core.entry.rowAt
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** M6 — the 2D entry model: the D-018/D-019 case table, and Article 12 round-trips. */
class M6EntrySpec : StringSpec({

    fun empty() = EditorState()
    fun EditorState.type(text: String) = Editor.insert(this, text)
    fun EditorState.bs() = Editor.backspace(this)
    fun EditorState.left() = Editor.moveLeft(this)
    fun EditorState.right() = Editor.moveRight(this)

    /** 2 / 3 with the cursor sitting at the end of the denominator. */
    fun twoOverThree(): EditorState = Editor.typeFraction(empty().type("2")).type("3")

    // --- typing builds what the grammar means --------------------------------
    "typing 1 + 2 / 3 captures only the trailing operand" {
        val s = Editor.typeFraction(empty().type("1+2")).type("3")
        s.toInput() shouldBe "1+(2)/(3)" // NOT (1+2)/(3)
        val v = (s.validate() as CalcResult.Ok).value
        Calculator().num(s.toInput()).compareTo(Num.of(5, 3)) shouldBe 0
        v shouldNotBe null
    }

    // --- F3: backspace inverts the `/` exactly --------------------------------
    "F3 — empty denominator collapses; the numerator survives and the cursor lands where the / was" {
        val s = Editor.typeFraction(empty().type("2")) // "2/" — cursor in the empty denominator
        val after = s.bs()
        after.toInput() shouldBe "2"
        after.cursor.offset shouldBe 1 // exactly where the cursor was when `/` was typed
    }

    // --- F2: the owner's stated case ------------------------------------------
    "F2 — empty numerator collapses; the denominator survives inline" {
        // 2/3, then walk to the numerator and delete the 2
        val s = twoOverThree().left().left() // → numerator, after the 2
        val emptied = s.bs() // numerator now empty
        emptied.root.rowAt(emptied.cursor.path).isEmpty shouldBe true
        val collapsed = emptied.bs()
        collapsed.toInput() shouldBe "3" // the denominator survived
    }

    // --- F4 --------------------------------------------------------------------
    "F4 — both sides empty: the structure just vanishes" {
        val s = Editor.typeFraction(empty()) // cursor in the empty numerator
        s.bs().toInput() shouldBe ""
    }

    // --- F5/F6 as revised by D-019: destruction requires visibility ------------
    "F5 — a populated boundary SELECTS; it does not silently exit or destroy" {
        val s = twoOverThree().left().left().left() // start of the populated numerator
        val first = s.bs()
        first.selection shouldNotBe null // the press is VISIBLE
        first.toInput() shouldBe "(2)/(3)" // and destroyed nothing
        val second = first.bs()
        second.toInput() shouldBe "" // the second press deletes what was shown
    }
    "Rule 3 generalises: ⌫ after a structure selects it rather than destroying it unseen" {
        val s = twoOverThree().right() // exit the fraction; cursor after it in the root
        val first = s.bs()
        first.selection shouldNotBe null
        first.toInput() shouldBe "(2)/(3)"
        first.bs().toInput() shouldBe ""
    }
    "navigation deselects" {
        val selected = twoOverThree().right().bs()
        selected.selection shouldNotBe null
        selected.left().selection shouldBe null
    }
    "an atom still dies on the first press — it is one visible character" {
        empty().type("12").bs().toInput() shouldBe "1"
    }

    // --- navigation retraces creation order ------------------------------------
    "→ from the end of the numerator enters the denominator" {
        val s = twoOverThree().left().left() // numerator, after the 2 (its end)
        s.cursor.path.last().field shouldBe Field.NUMERATOR
        val moved = s.right()
        moved.cursor.path.last().field shouldBe Field.DENOMINATOR
        moved.cursor.offset shouldBe 0
    }
    "→ from the end of the denominator exits the fraction" {
        val moved = twoOverThree().right()
        moved.cursor.path.isEmpty() shouldBe true
        moved.cursor.offset shouldBe 1
    }

    // --- nesting: one keystroke inverts one structure, never cascading ---------
    "nested collapse is local and does not cascade" {
        // numerator contains its own fraction: (a/b)/c
        var s = Editor.typeFraction(empty().type("a")) // a/…
        s = Editor.typeFraction(s.type("b")) // b/… inside the denominator? no — build explicitly
        // simpler: build 5/6 then wrap: type 5, `/`, 6 → 5/6 ; then in the denominator make 6/7
        var t = Editor.typeFraction(empty().type("5")).type("6")
        t = Editor.typeFraction(t).type("7") // denominator is now 6/7
        t.toInput() shouldBe "(5)/((6)/(7))"
        // empty the inner denominator, then collapse ONLY the inner fraction
        val inner = t.bs().bs() // delete 7, then collapse inner (empty denominator → 6 survives)
        inner.toInput() shouldBe "(5)/(6)" // outer fraction untouched — no cascade
    }

    // --- Article 12: the editor cannot build what the parser rejects -----------
    "every COMPLETE construction parses (editor → grammar)" {
        val built = listOf(
            empty().type("1+2"),
            Editor.typeFraction(empty().type("2")).type("3"),
            Editor.typePower(empty().type("x")).type("2"),
            Editor.typeRoot(empty()).type("9"),
            Editor.typeParen(empty()).type("1+2"),
            Editor.typeFraction(empty().type("1+2")).type("3"),
        )
        built.forEach { s ->
            s.isComplete shouldBe true
            withClue(s.toInput()) { (s.validate() is CalcResult.Ok) shouldBe true }
        }
    }
    "an incomplete entry is simply not valid yet — the PARSER says so, not a second rulebook" {
        val half = Editor.typeFraction(empty().type("2")) // "(2)/()" — denominator still empty
        half.isComplete shouldBe false
        (half.validate() is CalcResult.Err) shouldBe true
    }
    "everything that parses is constructible (grammar → editor)" {
        listOf("1+2", "sin(30)", "0xFF", "5 km + 300 m", "x^2-5x+6=0", "nCr(5,2)", "5 lakh").forEach { src ->
            val s = empty().type(src)
            s.toInput() shouldBe src // flat atoms round-trip the text exactly
            withClue(src) { (s.validate() is CalcResult.Ok) shouldBe true }
        }
    }
})

private inline fun <T> withClue(clue: Any?, block: () -> T): T =
    io.kotest.assertions.withClue(clue, block)
