package com.vaibhav.calc.android

import java.io.IOException
import java.security.MessageDigest

internal interface SnapshotFile {
    fun read(): ByteArray?
    fun write(data: ByteArray)
    /** On failure the file must remain available for recovery. */
    fun delete()
}

/**
 * A reviewed save keeps a verified rollback journal until the new bytes are read back.
 * A process interruption before journal removal restores the previous session on load.
 * All entry points are called under SessionWriteCoordinator's per-file lock.
 */
internal class VerifiedSessionStorage(private val session: SnapshotFile, private val journal: SnapshotFile) {
    fun load(): ByteArray? { recover(); return session.read() }

    fun saveOrdinary(data: ByteArray) { recover(); session.write(data) }

    fun saveReviewed(data: ByteArray) {
        recover()
        val previous = session.read()
        val recovery = encodeJournal(previous)
        journal.write(recovery)
        if (!same(journal.read(), recovery)) throw IOException("Could not verify the save recovery journal.")
        try {
            session.write(data)
            if (!same(session.read(), data)) throw IOException("Could not verify the saved calculation.")
            journal.delete()
        } catch (problem: Exception) {
            try {
                restore(previous)
                journal.delete()
            } catch (rollback: Exception) {
                problem.addSuppressed(rollback)
                throw IOException("Storage could not restore the prior session; its recovery journal was retained. Retry when storage is available.", problem)
            }
            throw problem
        }
    }

    fun delete() {
        // Preserve recovery evidence until the possibly unacknowledged candidate
        // is gone. A failed reset may restore the prior session, never accept it as new.
        session.delete()
        journal.delete()
    }

    private fun recover() {
        val data = journal.read() ?: return
        val previous = decodeJournal(data)
        restore(previous)
        journal.delete()
    }

    private fun restore(previous: ByteArray?) {
        if (previous == null) session.delete() else session.write(previous)
        if (!same(session.read(), previous)) throw IOException("Could not verify restoration of the prior session.")
    }

    private fun encodeJournal(previous: ByteArray?): ByteArray {
        val payload = byteArrayOf(if (previous == null) 0 else 1) + (previous ?: ByteArray(0))
        return MessageDigest.getInstance("SHA-256").digest(payload) + payload
    }

    private fun decodeJournal(data: ByteArray): ByteArray? {
        if (data.size < 33) throw IOException("The session recovery journal is incomplete; it was preserved.")
        val payload = data.copyOfRange(32, data.size)
        if (!MessageDigest.isEqual(data.copyOfRange(0, 32), MessageDigest.getInstance("SHA-256").digest(payload)))
            throw IOException("The session recovery journal is damaged; it was preserved.")
        return when (payload[0].toInt()) {
            0 -> if (payload.size == 1) null else throw IOException("Invalid empty-session recovery journal.")
            1 -> payload.copyOfRange(1, payload.size)
            else -> throw IOException("Invalid session recovery journal.")
        }
    }

    private fun same(a: ByteArray?, b: ByteArray?): Boolean =
        if (a == null || b == null) a == null && b == null else a.contentEquals(b)
}
