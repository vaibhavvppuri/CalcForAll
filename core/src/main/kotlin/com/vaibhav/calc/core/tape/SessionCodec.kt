package com.vaibhav.calc.core.tape

import com.vaibhav.calc.core.entry.*
import com.vaibhav.calc.core.eval.*
import java.io.*
import java.math.RoundingMode
import java.util.zip.CRC32

data class SessionSnapshot(
    val settings: Settings = Settings(),
    val editor: EditorState = EditorState(),
    val tape: TapeArchive = TapeArchive(),
    val theme: String = "SYSTEM",
)

sealed interface SnapshotRead {
    data class Restored(val snapshot: SessionSnapshot) : SnapshotRead
    data class Invalid(val reason: String) : SnapshotRead
}

/** Versioned bounded data format. No Java object deserialization, code, or external resources. */
object SessionCodec {
    private const val MAGIC = 0x43414C43
    private const val VERSION = 1
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_NODES = 100_000
    private const val MAX_DEPTH = 128

    fun encode(snapshot: SessionSnapshot): ByteArray {
        require(snapshot.theme in listOf("SYSTEM", "LIGHT", "DARK", "AMOLED")) { "Unknown theme." }
        require(snapshot.tape.entries.map { it.id }.distinct().size == snapshot.tape.entries.size) { "Duplicate tape ids." }
        require(snapshot.tape.nextId > (snapshot.tape.entries.maxOfOrNull { it.id.n } ?: 0)) { "Invalid next tape id." }
        val budget = Budget()
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION)
            out.writeUTF(snapshot.theme)
            writeSettings(out, snapshot.settings)
            writeEditor(out, snapshot.editor, budget)
            out.writeInt(snapshot.tape.nextId)
            require(snapshot.tape.entries.size <= 5000) { "too many saved entries" }
            out.writeInt(snapshot.tape.entries.size)
            snapshot.tape.entries.forEach { e ->
                require(e.id.n > 0 && e.deadRefs.size <= 8192 && e.deadRefs.all { it.n >= 0 }) { "Invalid tape references." }
                out.writeInt(e.id.n); writeSettings(out, e.settings); writeEditor(out, e.source, budget)
                out.writeInt(e.deadRefs.size); e.deadRefs.forEach { out.writeInt(it.n) }
                require(bytes.size() <= MAX_BYTES - 8) { "session is too large to save" }
            }
        }
        val payload = bytes.toByteArray()
        require(payload.size <= MAX_BYTES - 8) { "session is too large to save" }
        val checksum = CRC32().apply { update(payload) }.value
        return ByteArrayOutputStream().also { stream ->
            stream.write(payload); DataOutputStream(stream).writeLong(checksum)
        }.toByteArray()
    }

    fun decode(bytes: ByteArray): SnapshotRead {
        if (bytes.size !in 16..MAX_BYTES) return SnapshotRead.Invalid("Saved session has an invalid size.")
        return try {
            val n = bytes.size - 8
            val checksum = DataInputStream(ByteArrayInputStream(bytes, n, 8)).readLong()
            require(CRC32().apply { update(bytes, 0, n) }.value == checksum) { "Saved session checksum does not match." }
            val input = DataInputStream(ByteArrayInputStream(bytes, 0, n))
            require(input.readInt() == MAGIC) { "Not a calculator session." }
            require(input.readInt() == VERSION) { "Unsupported saved-session version." }
            val theme = input.readUTF().also { require(it in listOf("SYSTEM", "LIGHT", "DARK", "AMOLED")) }
            val settings = readSettings(input)
            val budget = Budget()
            val editor = readEditor(input, budget)
            val nextId = input.readInt()
            val count = input.readInt().also { require(it in 0..5000) }
            val entries = List(count) {
                val id = EntryId(input.readInt().also { require(it > 0) })
                val policy = readSettings(input)
                val source = readEditor(input, budget)
                val deadCount = input.readInt().also { require(it in 0..8192) }
                val deadRefs = List(deadCount) { EntryId(input.readInt().also { require(it >= 0) }) }.toSet()
                ArchivedEntry(id, source, deadRefs, policy)
            }
            require(input.available() == 0) { "Unexpected trailing session data." }
            require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate saved entry ids." }
            require(nextId > (entries.maxOfOrNull { it.id.n } ?: 0)) { "Invalid saved next entry id." }
            SnapshotRead.Restored(SessionSnapshot(settings, editor, TapeArchive(nextId, entries), theme))
        } catch (e: IOException) {
            SnapshotRead.Invalid("Saved session is incomplete or unreadable: ${e.message.orEmpty()}")
        } catch (e: IllegalArgumentException) {
            SnapshotRead.Invalid(e.message ?: "Saved session contains invalid data.")
        } catch (e: IndexOutOfBoundsException) {
            SnapshotRead.Invalid("Saved cursor points outside its expression.")
        }
    }

    private fun writeSettings(out: DataOutputStream, s: Settings) {
        out.writeUTF(s.angle.name); out.writeInt(s.computePrecision); out.writeInt(s.displayPrecision)
        out.writeUTF(s.rounding.name); out.writeUTF(s.notation.name); out.writeUTF(s.exactForm.name)
        out.writeUTF(s.grouping.name); out.writeUTF(s.numerals.name); out.writeBoolean(s.complexEnabled)
    }
    private fun readSettings(input: DataInputStream): Settings {
        val angle = AngleMode.valueOf(input.readUTF())
        val compute = input.readInt().also { require(it in 5..1024) }
        val display = input.readInt().also { require(it in 1..1020) }
        return Settings(angle, compute, display, RoundingMode.valueOf(input.readUTF()),
            NotationMode.valueOf(input.readUTF()), ExactForm.valueOf(input.readUTF()),
            GroupingMode.valueOf(input.readUTF()), NumeralSystem.valueOf(input.readUTF()), input.readBoolean())
    }

    private fun writeEditor(out: DataOutputStream, s: EditorState, budget: Budget) {
        require(s.cursor.offset in 0..validatedRow(s.root, s.cursor.path).size) { "Invalid cursor offset." }
        s.selection?.let { require(it.from >= 0 && it.to <= validatedRow(s.root, it.path).size && it.from <= it.to) { "Invalid selection." } }
        writeRow(out, s.root, 0, budget)
        writePath(out, s.cursor.path); out.writeInt(s.cursor.offset)
        out.writeBoolean(s.selection != null)
        s.selection?.let { writePath(out, it.path); out.writeInt(it.from); out.writeInt(it.to) }
    }
    private fun readEditor(input: DataInputStream, budget: Budget): EditorState {
        val root = readRow(input, 0, budget)
        val path = readPath(input)
        val offset = input.readInt()
        require(offset in 0..validatedRow(root, path).size) { "Invalid cursor offset." }
        val sel = if (input.readBoolean()) {
            val p = readPath(input); val from = input.readInt(); val to = input.readInt()
            require(from >= 0 && to <= validatedRow(root, p).size && from <= to) { "Invalid selection." }
            Selection(p, from, to)
        } else null
        return EditorState(root, Cursor(path, offset), sel)
    }
    private fun writePath(out: DataOutputStream, path: List<PathStep>) {
        require(path.size <= MAX_DEPTH) { "Expression nesting is too deep to save." }
        out.writeInt(path.size)
        path.forEach { out.writeInt(it.nodeIndex); out.writeUTF(it.field.name) }
    }
    private fun readPath(input: DataInputStream): List<PathStep> =
        List(input.readInt().also { require(it in 0..MAX_DEPTH) }) {
            PathStep(input.readInt().also { require(it >= 0) }, Field.valueOf(input.readUTF()))
        }

    private fun validatedRow(root: Row, path: List<PathStep>): Row {
        require(path.size <= MAX_DEPTH) { "Cursor path is nested too deeply." }
        var row = root
        path.forEach { step ->
            require(step.nodeIndex in row.nodes.indices) { "Cursor points outside its expression." }
            val node = row.nodes[step.nodeIndex]
            require(step.field in node.fields()) { "Cursor points to an invalid expression field." }
            row = node.field(step.field)
        }
        return row
    }

    private fun writeRow(out: DataOutputStream, row: Row, depth: Int, budget: Budget) {
        require(depth <= MAX_DEPTH) { "Expression nesting is too deep to save." }
        require(row.size <= MAX_NODES) { "Expression is too large to save." }
        budget.nodes += row.size
        require(budget.nodes <= MAX_NODES) { "Session contains too many expression nodes to save." }
        fun child(row: Row) = writeRow(out, row, depth + 1, budget)
        out.writeInt(row.size)
        row.nodes.forEach { node ->
            when (node) {
                is Atom -> { out.writeByte(0); out.writeChar(node.char.code) }
                is Ref -> { require(node.n >= 0); out.writeByte(1); out.writeInt(node.n) }
                Ans -> out.writeByte(2)
                is Fraction -> { out.writeByte(3); child(node.numerator); child(node.denominator) }
                is Power -> { out.writeByte(4); child(node.base); child(node.exponent) }
                is Root -> { out.writeByte(5); out.writeBoolean(node.index != null)
                    node.index?.let { child(it) }; child(node.radicand) }
                is Paren -> { out.writeByte(6); child(node.body) }
                is Func -> { require(node.name.length in 1..64); out.writeByte(7); out.writeUTF(node.name); child(node.arg) }
            }
        }
    }
    private class Budget(var nodes: Int = 0)
    private fun readRow(input: DataInputStream, depth: Int, budget: Budget): Row {
        require(depth <= MAX_DEPTH) { "Saved expression is nested too deeply." }
        val count = input.readInt().also { require(it in 0..MAX_NODES) }
        budget.nodes += count
        require(budget.nodes <= MAX_NODES) { "Saved session contains too many expression nodes." }
        fun child() = readRow(input, depth + 1, budget)
        return Row(List(count) {
            when (input.readUnsignedByte()) {
                0 -> Atom(input.readChar())
                1 -> Ref(input.readInt().also { require(it >= 0) })
                2 -> Ans
                3 -> Fraction(child(), child())
                4 -> Power(child(), child())
                5 -> { val index = if (input.readBoolean()) child() else null; Root(index, child()) }
                6 -> Paren(child())
                7 -> { val name = input.readUTF().also { require(it.length in 1..64) }; Func(name, child()) }
                else -> throw IllegalArgumentException("Unknown expression node in saved session.")
            }
        })
    }
}
