package com.vaibhav.calc.android

import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.keypad.Keypad
import com.vaibhav.calc.core.tape.EntryId
import com.vaibhav.calc.core.tape.EntryValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class CompleteSessionSpec : StringSpec({
    fun CalcSession.solve(text: String): CalcUiState { replaceText(text); return commit() }
    "equals routes equations instead of rejecting an equals sign" {
        val s = CalcSession()
        s.solve("x^2-5x+6=0").exact shouldBe "x = 2, 3"
        s.ui.steps.isNotEmpty() shouldBe true
    }
    "units, verdicts and registers use the same route as the tape" {
        val s = CalcSession()
        s.solve("5 km+300 m").exact shouldBe "5.3 km"
        s.solve("2+2=4").exact shouldBe "True"
        s.solve("5→A").exact shouldBe "A = 5"
        s.solve("A+2").exact shouldBe "7"
    }
    "linked rows recompute together and undo restores all of them" {
        val s = CalcSession()
        s.solve("2"); s.solve("#1*3")
        s.beginEdit(EntryId(1)); s.replaceText("4"); s.commit()
        s.ui.entries[1].value.let { (it as EntryValue.Computed).value.toString() } shouldContain "12"
        s.undoLastNotice()
        s.ui.entries[0].source.toInput() shouldBe "2"
        s.ui.entries[1].value.let { (it as EntryValue.Computed).value.toString() } shouldContain "6"
    }
    "failed and never-ran rows remain distinguishable in the app" {
        val s = CalcSession()
        s.solve("1/0"); s.solve("#1+2")
        s.ui.entries[0].value::class shouldBe EntryValue.Failed::class
        s.ui.entries[1].value::class shouldBe EntryValue.Blocked::class
        s.ui.error shouldBe "waiting on #1"
    }
    "a new digit starts fresh, an operator continues from Ans" {
        val s = CalcSession()
        s.solve("2+3")
        s.press(Keypad.basic().key("multiply")!!)
        s.press(Keypad.basic().key("2")!!)
        s.commit().exact shouldBe "10"
        s.press(Keypad.basic().key("7")!!).entryText shouldBe "7"
    }
    "format settings never contaminate copied result syntax" {
        val s = CalcSession()
        s.updateSettings(s.settings.copy(grouping = GroupingMode.INDIAN, numerals = NumeralSystem.DEVANAGARI))
        s.solve("100000+1")
        s.copyResult() shouldBe "100001"
    }
    "editor undo is local, reference chips are single nodes, and commit resets edit undo" {
        val s = CalcSession()
        s.insertText("2"); s.insertText("3")
        s.undoEntry().entryText shouldBe "2"
        s.redoEntry().entryText shouldBe "23"
        s.commit(); s.undoEntry().entryText shouldBe "23"
        s.insertReference(1); s.backspaceRepeat().entryText shouldBe ""
    }
    "saved sources restore reference and angle semantics" {
        val s = CalcSession()
        s.solve("sin(30)")
        s.cycleAngle()
        s.solve("#1*2")
        val t = CalcSession()
        t.restore(s.archive())
        t.solve("ans+1").exact shouldBe "2"
    }
    "typing the same digit as a previous expression still starts a new editable input" {
        val s = CalcSession()
        s.solve("2")
        s.insertText("2"); s.insertText("3")
        s.ui.entryText shouldBe "23"
        s.ui.committed shouldBe false
        s.ui.exact shouldBe null
    }
    "undo restores the actual displayed value after editing an older history row" {
        val s = CalcSession()
        s.solve("2"); s.solve("3")
        s.beginEdit(EntryId(1)); s.replaceText("4"); s.commit()
        s.clearHistory(); s.undoLastNotice()
        s.ui.exact shouldBe "4"
        s.copyResult() shouldBe "4"
    }
    "stored outcomes keep their label through settings and the exact decimal switch" {
        val s = CalcSession()
        s.solve("1/3→A")
        s.updateSettings(s.settings.copy(displayPrecision = 8))
        s.ui.exact shouldContain "A = "
        s.sdToggle().exact shouldContain "A = "
        s.copyResult() shouldBe "1/3"
    }
    "deep pasted input is rejected before recursive editor construction" {
        val s = CalcSession()
        s.replaceText("(".repeat(1000) + "1" + ")".repeat(1000))
        s.ui.notice shouldContain "nested too deeply"
        s.ui.entryText shouldBe ""
    }
    "wrapping an existing power cannot evade the structural depth budget" {
        val s = CalcSession()
        s.insertText("1")
        repeat(65) {
            s.flickUp(Keypad.basic().key("multiply")!!)
            s.insertText("1"); s.right()
        }
        // Every accepted edit remains serializable; a cursor-only depth guard misses base wrapping.
        com.vaibhav.calc.core.tape.SessionCodec.encode(com.vaibhav.calc.core.tape.SessionSnapshot(editor = s.ui.editor)).isNotEmpty() shouldBe true
    }
    "deleting a dependency clears the old displayed and copied answer and undo restores it" {
        val s = CalcSession()
        s.solve("2"); s.solve("#1*3")
        s.deleteEntry(EntryId(1))
        s.ui.exact shouldBe null
        s.copyResult() shouldBe ""
        s.ui.entries.single().value::class shouldBe EntryValue.Failed::class
        s.undoLastNotice().exact shouldBe "6"
        s.copyResult() shouldBe "6"
    }
})
