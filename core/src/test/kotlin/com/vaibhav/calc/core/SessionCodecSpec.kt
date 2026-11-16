package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.*
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.tape.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.util.zip.CRC32
import java.nio.ByteBuffer

class SessionCodecSpec : StringSpec({
    "snapshot round trips structured fields, cursor, settings and dead references" {
        val s = Editor.insert(Editor.typeFraction(Editor.typeAns(EditorState())), "2")
        val tape = Tape()
        tape.append(Editor.insert(EditorState(), "#9+2"))
        tape.append(s)
        val snapshot = SessionSnapshot(Settings(angle = AngleMode.GRAD, grouping = GroupingMode.INDIAN), s, tape.archive(), "AMOLED")
        SessionCodec.decode(SessionCodec.encode(snapshot)) shouldBe SnapshotRead.Restored(snapshot)
    }
    "corruption is visible and never silently becomes an empty session" {
        val bytes = SessionCodec.encode(SessionSnapshot())
        bytes[12] = (bytes[12].toInt() xor 1).toByte()
        (SessionCodec.decode(bytes) is SnapshotRead.Invalid) shouldBe true
        (SessionCodec.decode(byteArrayOf()) is SnapshotRead.Invalid) shouldBe true
        (SessionCodec.decode(ByteArray(SessionCodec.MAX_BYTES + 1)) is SnapshotRead.Invalid) shouldBe true
    }
    "each node kind and selection survives a save" {
        val row = Row(listOf(Atom('१'), Ref(3), Ans, Fraction(Row(listOf(Atom('1'))), Row(listOf(Atom('2')))),
            Power(Row(listOf(Atom('x'))), Row(listOf(Atom('2')))), Root(Row(listOf(Atom('3'))), Row(listOf(Atom('8')))),
            Paren(Row(listOf(Atom('5')))), Func("asin", Row(listOf(Atom('1'))))))
        val s = SessionSnapshot(editor = EditorState(row, Cursor(offset = row.size), Selection(emptyList(), 1, 3)))
        SessionCodec.decode(SessionCodec.encode(s)) shouldBe SnapshotRead.Restored(s)
    }
    "the write budget covers the whole session before a save can replace its predecessor" {
        val row = EditorState(Row(List(13000) { Atom('1') }))
        val archive = TapeArchive(9, (1..8).map { ArchivedEntry(EntryId(it), row, emptySet(), Settings()) })
        shouldThrow<IllegalArgumentException> { SessionCodec.encode(SessionSnapshot(tape = archive)) }
        val smaller = SessionSnapshot(tape = archive.copy(nextId = 8, entries = archive.entries.take(7)))
        SessionCodec.decode(SessionCodec.encode(smaller)) shouldBe SnapshotRead.Restored(smaller)
    }
    "invalid editor paths cannot be written or decoded even with a valid checksum" {
        val invalid = EditorState(Row(listOf(Atom('1'))), Cursor(listOf(PathStep(0, Field.BODY)), 0))
        shouldThrow<IllegalArgumentException> { SessionCodec.encode(SessionSnapshot(editor = invalid)) }
        val valid = EditorState(Row(listOf(Paren(Row(listOf(Atom('1')))))), Cursor(listOf(PathStep(0, Field.BODY)), 1))
        val bytes = SessionCodec.encode(SessionSnapshot(editor = valid))
        val marker = "BODY".toByteArray()
        val index = (0..bytes.size - marker.size).first { i -> marker.indices.all { bytes[i + it] == marker[it] } }
        "BASE".toByteArray().copyInto(bytes, index)
        val crc = CRC32().apply { update(bytes, 0, bytes.size - 8) }.value
        ByteBuffer.wrap(bytes, bytes.size - 8, 8).putLong(crc)
        (SessionCodec.decode(bytes) is SnapshotRead.Invalid) shouldBe true
    }
})
