package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.*
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.keypad.KeyAction
import com.vaibhav.calc.core.keypad.Keypad
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.tape.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

class SessionCompletionSpec : StringSpec({
    fun source(s: String) = Editor.insert(EditorState(), s)
    fun Tape.value(n: Int) = (entry(EntryId(n))!!.value as EntryValue.Computed).value

    "Ans is one node, one backspace and one operand for fractions" {
        val s = Keypad.apply(EditorState(), KeyAction.Ans)
        s.root.nodes shouldBe listOf(Ans)
        s.toInput() shouldBe "ans"
        Editor.backspace(s).toInput() shouldBe ""
        Editor.insert(Editor.typeFraction(s), "2").toInput() shouldBe "((ans)/(2))"
        Editor.moveLeft(s).cursor shouldBe Cursor(offset = 0)
        Editor.moveRight(Editor.moveLeft(s)).cursor shouldBe s.cursor
    }
    "a later angle change never silently changes an earlier answer" {
        val t = Tape()
        t.append(source("sin(30)"))
        t.updateSettings(Settings(angle = AngleMode.RAD))
        t.append(source("sin(pi/2)"))
        t.value(1) shouldBe RealValue(Num.HALF)
        t.value(2) shouldBe RealValue(Num.ONE)
        t.edit(EntryId(2), source("2"))
        t.value(1) shouldBe RealValue(Num.HALF)
    }
    "archive preserves reference identity, commit policy and next id" {
        val t = Tape()
        t.append(source("#3+1"))
        t.append(source("sin(30)"))
        t.delete(EntryId(2))
        val restored = Tape(Settings(angle = AngleMode.RAD))
        restored.restore(t.archive())
        restored.append(source("7"))
        restored.entry(EntryId(1))!!.value::class shouldBe EntryValue.Failed::class
        restored.entry(EntryId(3))!!.source.toInput() shouldBe "7"
    }
    "clear is undoable and ids are never recycled" {
        val t = Tape()
        t.append(source("2"))
        t.clear()
        t.entries() shouldBe emptyList()
        t.undo()
        t.value(1) shouldBe RealValue(Num.TWO)
        t.clear()
        t.append(source("3"))
        t.entries().single().id shouldBe EntryId(2)
    }
    "malformed archive is refused without replacing the tape" {
        val t = Tape()
        t.append(source("2"))
        shouldThrow<IllegalArgumentException> { t.restore(t.archive().copy(nextId = 1)) }
        t.value(1) shouldBe RealValue(Num.TWO)
    }
})
