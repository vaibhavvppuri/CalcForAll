package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.tape.EntryId
import com.vaibhav.calc.core.tape.Tape
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TapeEnvironmentSpec : StringSpec({
    fun src(text: String) = Editor.insert(EditorState(),text)

    "snapshot copies named registers references and immediately previous answer" {
        val tape = Tape()
        tape.append(src("5->A"))
        tape.append(src("#1+2"))
        val before = tape.snapshotEnvironment()
        before.get("A") shouldBe RealValue(Num.of(5))
        before.get("#1") shouldBe RealValue(Num.of(5))
        before.get("#2") shouldBe RealValue(Num.of(7))
        before.ans() shouldBe RealValue(Num.of(7))
        tape.edit(EntryId(1),src("9->A"))
        before.get("A") shouldBe RealValue(Num.of(5))
        before.ans() shouldBe RealValue(Num.of(7))
        tape.snapshotEnvironment().ans() shouldBe RealValue(Num.of(11))
        before.set("A",RealValue(Num.of(99)))
        tape.snapshotEnvironment().get("A") shouldBe RealValue(Num.of(9))
    }

    "failed later assignments clear their register without exposing prior values" {
        val tape = Tape()
        tape.append(src("5->A")); tape.append(src("7->B")); tape.append(src("A/0->B"))
        val env = tape.snapshotEnvironment()
        env.get("A") shouldBe RealValue(Num.of(5))
        env.get("B") shouldBe null
        env.get("#2") shouldBe RealValue(Num.of(7))
        env.get("#3") shouldBe null
        env.ans() shouldBe null
    }

    "blocked and cyclic assignments also invalidate the latest register" {
        val tape = Tape()
        tape.append(src("5->A")); tape.append(src("#1+1->B"))
        tape.edit(EntryId(1),src("1/0->A"))
        tape.snapshotEnvironment().get("A") shouldBe null
        tape.snapshotEnvironment().get("B") shouldBe null
        tape.append(src("#3->A"))
        tape.snapshotEnvironment().get("A") shouldBe null
        tape.snapshotEnvironment().get("#3") shouldBe null
    }

    "a final equation or empty row has no ans even when older values exist" {
        val tape = Tape()
        tape.append(src("7")); tape.append(src("x^2-1=0"))
        tape.snapshotEnvironment().get("#1") shouldBe RealValue(Num.of(7))
        tape.snapshotEnvironment().get("#2") shouldBe null
        tape.snapshotEnvironment().ans() shouldBe null
        tape.append(EditorState())
        tape.snapshotEnvironment().ans() shouldBe null
    }

    "all seven registers use their latest assignments" {
        val tape = Tape()
        val registers = listOf("A","B","C","D","E","F","M")
        registers.forEachIndexed { index,name -> tape.append(src("${index+1}->$name")) }
        val env = tape.snapshotEnvironment()
        registers.forEachIndexed { index,name -> env.get(name) shouldBe RealValue(Num.of(index+1)) }
    }
})
