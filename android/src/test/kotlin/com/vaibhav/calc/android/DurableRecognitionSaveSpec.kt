package com.vaibhav.calc.android

import com.vaibhav.calc.core.tape.EntryId
import com.vaibhav.calc.core.tape.SessionCodec
import com.vaibhav.calc.core.tape.SessionSnapshot
import com.vaibhav.calc.core.tape.SnapshotRead
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException

class DurableRecognitionSaveSpec : StringSpec({
    fun CalcSession.solve(text: String) { replaceText(text); commit() }

    "success returns the exact persisted snapshot and one new history row" {
        val session = CalcSession()
        session.solve("7 -> A")
        var bytes: ByteArray? = null
        val result = session.saveRecognitionDurably("A*6", "DARK") { bytes = SessionCodec.encode(it) }
        result.accepted shouldBe true
        result.state.exact shouldBe "42"
        result.state.entries.size shouldBe 2
        result.snapshot!!.theme shouldBe "DARK"
        val restored = SessionCodec.decode(bytes!!).shouldBeInstanceOf<SnapshotRead.Restored>()
        restored.snapshot shouldBe result.snapshot
        result.environment!!.get("A") shouldBe session.snapshotEnvironment().get("A")
    }

    "write failure restores editor undo redo registers and ID allocation before retry" {
        val session = CalcSession()
        session.solve("7 -> A")
        session.replaceText("3+4"); session.replaceText("5+6"); session.undoEntry()
        val before = session.ui
        val archive = session.archive()
        val environment = session.snapshotEnvironment()
        val failed = session.saveRecognitionDurably("A*6") { throw IOException("disk full") }
        failed.accepted shouldBe false
        failed.error!! shouldContain "disk full"
        failed.state shouldBe before
        session.ui shouldBe before
        session.archive() shouldBe archive
        session.snapshotEnvironment().get("A") shouldBe environment.get("A")
        session.snapshotEnvironment().ans() shouldBe environment.ans()
        session.redoEntry().entryText shouldBe "5+6"
        session.undoEntry().entryText shouldBe "3+4"
        var persisted: SessionSnapshot? = null
        val retry = session.saveRecognitionDurably("A*6") { persisted = it }
        retry.accepted shouldBe true
        session.ui.entries.map { it.id.n } shouldBe listOf(1, 2)
        persisted!!.tape.nextId shouldBe 3
    }

    "write failure preserves a preexisting history undo action" {
        val session = CalcSession()
        session.solve("7 -> A"); session.solve("A+1")
        session.deleteEntry(EntryId(2))
        val before = session.ui
        session.saveRecognitionDurably("42") { throw IOException("read-only storage") }.accepted shouldBe false
        session.ui shouldBe before
        session.undoLastNotice().entries.map { it.id.n } shouldBe listOf(1, 2)
        session.ui.entries.last().source.toInput() shouldBe "A+1"
    }

    "invalid recognition and encoding failure retain a pending edit and never acknowledge save" {
        val session = CalcSession()
        session.solve("7 -> A"); session.beginEdit(EntryId(1)); session.replaceText("9 -> A")
        val before = session.ui
        val archive = session.archive()
        var calls = 0
        session.saveRecognitionDurably("1+") { calls++ }.accepted shouldBe false
        calls shouldBe 0
        session.ui shouldBe before
        val failed = session.saveRecognitionDurably("2+2") { throw IllegalArgumentException("snapshot too large") }
        failed.accepted shouldBe false
        failed.error!! shouldContain "snapshot too large"
        session.ui shouldBe before
        session.archive() shouldBe archive
        session.ui.editingId shouldBe EntryId(1)
    }
})
