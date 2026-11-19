package com.vaibhav.calc.android

import android.content.Context
import android.util.AtomicFile
import com.vaibhav.calc.core.tape.SessionCodec
import com.vaibhav.calc.core.tape.SessionSnapshot
import com.vaibhav.calc.core.tape.SnapshotRead
import java.io.File
import java.io.IOException
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Private local storage. Atomic replacement preserves the last good session after a failed save. */
class SessionStore(context: Context) {
    private val path = File(context.filesDir, "session.calc")
    private val storage = VerifiedSessionStorage(
        AtomicSnapshotFile(path, SessionCodec.MAX_BYTES),
        AtomicSnapshotFile(File(context.filesDir, "session.calc.recognition-rollback"), SessionCodec.MAX_BYTES + 33),
    )
    private val coordinator = synchronized(coordinators) {
        coordinators.getOrPut(path.canonicalPath) { SessionWriteCoordinator() }
    }
    private val owner = coordinator.openOwner()

    fun newWriteTicket(): Long = coordinator.ticket(owner)
    fun beginDurableWrite(): Long = coordinator.reserve(owner)
    fun endDurableWrite(ticket: Long) = coordinator.release(owner, ticket)

    /** Capture the ticket with the UI snapshot; disk work survives activity disposal. */
    fun enqueueLifecycleSave(snapshot: SessionSnapshot) =
        enqueueLifecycleSave(snapshot) { task -> lifecycleWrites.launch { task() }; Unit }

    /** An internal dispatch seam makes owner handoff testable without racing IO threads. */
    internal fun enqueueLifecycleSave(snapshot: SessionSnapshot, dispatch: (() -> Unit) -> Unit) {
        val ticket = coordinator.registerLifecycle(owner) {
            // Handoff is verified too: a silently failed atomic rename must not
            // make the replacement activity accept an older session as current.
            storage.saveReviewed(SessionCodec.encode(snapshot))
        }
        if (ticket < 0) return
        dispatch {
            try { coordinator.drainLifecycle(ticket) }
            catch (_: IOException) { /* Preserve the prior atomic snapshot. */ }
            catch (_: IllegalArgumentException) { /* Invalid data cannot replace it. */ }
        }
    }

    fun load(): SnapshotRead? {
        return try {
            coordinator.read(owner) { storage.load()?.let(SessionCodec::decode) }
        } catch (e: IOException) {
            SnapshotRead.Invalid("Could not read saved calculations: ${e.message}")
        } catch (e: IllegalArgumentException) {
            SnapshotRead.Invalid("Could not preserve the latest calculations: ${e.message}")
        }
    }

    /** False explicitly means superseded; this must not acknowledge a reviewed save. */
    fun save(snapshot: SessionSnapshot, ticket: Long): Boolean {
        val data = SessionCodec.encode(snapshot)
        return coordinator.write(owner, ticket) { storage.saveOrdinary(data) }
    }

    /** Requires a reservation that remains held until the matching UI snapshot is published. */
    fun saveDurably(snapshot: SessionSnapshot, ticket: Long) {
        val data = SessionCodec.encode(snapshot)
        coordinator.writeReserved(owner, ticket) { storage.saveReviewed(data) }
    }

    fun delete() = coordinator.delete(owner) { storage.delete() }

    companion object {
        private val coordinators = mutableMapOf<String, SessionWriteCoordinator>()
        // Process-scoped, because a composition-scoped flush is cancelled by disposal.
        private val lifecycleWrites = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** Android's atomic replacement plus explicit sync and observable deletion failure. */
private class AtomicSnapshotFile(private val path: File, private val maxBytes: Int) : SnapshotFile {
    private val file = AtomicFile(path)

    override fun read(): ByteArray? = try {
        file.openRead().use { input ->
            if (input.channel.size() > maxBytes) throw IOException("Saved session is too large.")
            input.readBytes()
        }
    } catch (problem: FileNotFoundException) {
        if (!path.exists() && !File(path.path + ".bak").exists() && !File(path.path + ".new").exists()) null
        else throw problem
    }

    override fun write(data: ByteArray) {
        if (data.size > maxBytes) throw IOException("Saved session is too large.")
        val stream = file.startWrite()
        try {
            stream.write(data)
            // AtomicFile logs some sync failures; request an explicit throwing sync first.
            stream.fd.sync()
        } catch (problem: Exception) {
            file.failWrite(stream)
            throw problem
        }
        file.finishWrite(stream)
        // Reviewed writes are read back by VerifiedSessionStorage before acknowledgement.
    }

    override fun delete() {
        // Delete auxiliary files first. On a failed deletion, keep the committed base
        // available for recovery rather than claiming that deletion succeeded.
        for (target in listOf(File(path.path + ".bak"), File(path.path + ".new"), path)) {
            if (target.exists() && !target.delete()) throw IOException("Could not remove ${path.name}.")
        }
    }
}
