package com.vaibhav.calc.battery

import com.vaibhav.calc.core.entry.Ans
import com.vaibhav.calc.core.entry.Atom
import com.vaibhav.calc.core.entry.Cursor
import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.entry.Field
import com.vaibhav.calc.core.entry.Fraction
import com.vaibhav.calc.core.entry.PathStep
import com.vaibhav.calc.core.entry.Row
import com.vaibhav.calc.core.entry.Selection
import com.vaibhav.calc.core.keypad.KeyAction
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** Supplementary harness checks; these do not alter the historical case sets. */
class HarnessSafetySpec : StringSpec({
    val probe = Case("safety-probe", 0, "safety", "PROBE", "unknown", null,
        null, null, null, emptyList(), null, null, false)

    "a probe cannot conceal an escaped exception" {
        val judgement = Runner.judge(probe, "PROBE", Outcome.Crash("IllegalStateException", "test"))
        judgement.verdict shouldBe "FAIL"
        judgement.severity shouldBe "CRITICAL"
    }

    "a probe cannot conceal a timeout" {
        val judgement = Runner.judge(probe, "PROBE", Outcome.Hang)
        judgement.verdict shouldBe "FAIL"
        judgement.severity shouldBe "CRITICAL"
    }

    "a typed refusal of an undefined probe remains an observation" {
        Runner.judge(probe, "PROBE", Outcome.Err("UNSUPPORTED", "not implemented")).verdict shouldBe "PROBE"
    }

    "Ans reference inserts one atomic leaf independently of Editor" {
        val expected = EditorState(Row(listOf(Ans)), Cursor(offset = 1))
        B3Harness.refAction(EditorState(), KeyAction.Ans) shouldBe expected
        Editor.typeAns(EditorState()) shouldBe expected
    }

    "Ans insertion replaces a selected range at its start" {
        val initial = EditorState(Row(listOf(Atom('1'), Atom('+'), Atom('2'))),
            Cursor(offset = 3), Selection(emptyList(), 0, 2))
        val expected = EditorState(Row(listOf(Ans, Atom('2'))), Cursor(offset = 1))
        B3Reference.typeAns(initial) shouldBe expected
        Editor.typeAns(initial) shouldBe expected
    }

    "Ans in a fraction is traversed and deleted as one leaf" {
        val path = listOf(PathStep(0, Field.NUMERATOR))
        val initial = EditorState(Row(listOf(Fraction(Row(listOf(Ans)), Row(listOf(Atom('2')))))),
            Cursor(path, 1))
        val before = initial.copy(cursor = Cursor(path, 0))
        B3Reference.left(initial) shouldBe before
        Editor.moveLeft(initial) shouldBe before
        B3Reference.right(before) shouldBe initial
        Editor.moveRight(before) shouldBe initial
        val deleted = EditorState(Row(listOf(Fraction(Row(), Row(listOf(Atom('2')))))), Cursor(path, 0))
        B3Reference.backspace(initial) shouldBe deleted
        Editor.backspace(initial) shouldBe deleted
    }
})
