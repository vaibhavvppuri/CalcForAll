package com.vaibhav.calc.android

import com.vaibhav.calc.core.tape.EntryId
import com.vaibhav.calc.core.tape.TapeArchive
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class RecognitionSaveFailureSpec : StringSpec({
    "an exhausted tape rejects recognized input without replacing the prior editor" {
        val session = CalcSession()
        session.restore(TapeArchive(nextId = Int.MAX_VALUE))
        session.replaceText("3+4")
        val before = session.ui
        val archive = session.archive()
        val saved = session.saveRecognition("6*7")
        saved.accepted shouldBe false
        saved.state.editor shouldBe before.editor
        saved.state.entryText shouldBe "3+4"
        saved.state.entries shouldBe before.entries
        session.archive() shouldBe archive
        saved.state.notice!! shouldContain "identifiers are exhausted"
    }

    "append refusal preserves a pending edit and the previously committed register" {
        val session = CalcSession()
        session.replaceText("7 -> A"); session.commit()
        session.restore(session.archive().copy(nextId = Int.MAX_VALUE))
        session.beginEdit(EntryId(1))
        session.replaceText("9 -> A")
        val before = session.ui
        val archive = session.archive()
        val register = session.snapshotEnvironment().get("A")
        val saved = session.saveRecognition("6*7")
        saved.accepted shouldBe false
        saved.state.editor shouldBe before.editor
        saved.state.editingId shouldBe EntryId(1)
        session.archive() shouldBe archive
        session.snapshotEnvironment().get("A") shouldBe register
    }
})
