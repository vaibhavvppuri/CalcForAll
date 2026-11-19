package com.vaibhav.calc.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.IOException

private class MemorySnapshotFile(initial: String? = null) : SnapshotFile {
    var bytes: ByteArray? = initial?.toByteArray()
    var beforeWrite: (ByteArray) -> Unit = {}
    var afterWrite: (ByteArray) -> Unit = {}
    var beforeDelete: () -> Unit = {}
    override fun read(): ByteArray? = bytes?.copyOf()
    override fun write(data: ByteArray) { beforeWrite(data); bytes = data.copyOf(); afterWrite(data) }
    override fun delete() { beforeDelete(); bytes = null }
    fun text() = bytes?.decodeToString()
}

class VerifiedSessionStorageSpec : StringSpec({
    "a reviewed commit is verified and removes its rollback journal only after success" {
        val base = MemorySnapshotFile("old"); val journal = MemorySnapshotFile()
        var journalObserved = false
        base.beforeWrite = { journalObserved = journal.bytes != null }
        val storage = VerifiedSessionStorage(base, journal)
        storage.saveReviewed("new".toByteArray())
        journalObserved shouldBe true
        base.text() shouldBe "new"
        journal.bytes shouldBe null
        storage.load()!!.decodeToString() shouldBe "new"
    }

    "a failure after candidate bytes were written still restores the previous session" {
        val base = MemorySnapshotFile("old"); val journal = MemorySnapshotFile()
        base.afterWrite = { if (it.decodeToString() == "new") throw IOException("finish failed") }
        shouldThrow<IOException> { VerifiedSessionStorage(base, journal).saveReviewed("new".toByteArray()) }
        base.text() shouldBe "old"
        journal.bytes shouldBe null
    }

    "a silently wrong replacement is detected rather than acknowledged" {
        val base = MemorySnapshotFile("old"); val journal = MemorySnapshotFile()
        base.afterWrite = { if (it.decodeToString() == "new") base.bytes = "truncated".toByteArray() }
        shouldThrow<IOException> { VerifiedSessionStorage(base, journal).saveReviewed("new".toByteArray()) }
        base.text() shouldBe "old"
        journal.bytes shouldBe null
    }

    "rollback failure retains recovery data and blocks later writes until recovery succeeds" {
        val base = MemorySnapshotFile("old"); val journal = MemorySnapshotFile()
        var failRestoration = true
        base.afterWrite = { if (it.decodeToString() == "new") throw IOException("finish failed") }
        base.beforeWrite = { if (it.decodeToString() == "old" && failRestoration) throw IOException("disk unavailable") }
        val storage = VerifiedSessionStorage(base, journal)
        shouldThrow<IOException> { storage.saveReviewed("new".toByteArray()) }.message!! shouldContain "recovery journal was retained"
        (journal.bytes != null) shouldBe true
        shouldThrow<IOException> { storage.saveReviewed("retry".toByteArray()) }
        shouldThrow<IOException> { storage.saveOrdinary("unrelated".toByteArray()) }
        base.text() shouldBe "new"
        failRestoration = false
        // A recreated store first restores the last acknowledged snapshot, never the candidate.
        VerifiedSessionStorage(base, journal).load()!!.decodeToString() shouldBe "old"
        journal.bytes shouldBe null
    }

    "interruption before journal removal recovers the old snapshot or an originally absent file" {
        for (original in listOf<String?>("old", null)) {
            val base = MemorySnapshotFile(original); val journal = MemorySnapshotFile()
            base.afterWrite = { if (it.decodeToString() == "new") throw AssertionError("simulated process interruption") }
            shouldThrow<AssertionError> { VerifiedSessionStorage(base, journal).saveReviewed("new".toByteArray()) }
            (journal.bytes != null) shouldBe true
            base.afterWrite = {}
            VerifiedSessionStorage(base, journal).load()?.decodeToString() shouldBe original
            journal.bytes shouldBe null
        }
    }

    "failure to remove a recovery journal rolls back and cannot report a successful commit" {
        val base = MemorySnapshotFile("old"); val journal = MemorySnapshotFile()
        journal.beforeDelete = { throw IOException("journal locked") }
        shouldThrow<IOException> { VerifiedSessionStorage(base, journal).saveReviewed("new".toByteArray()) }
        base.text() shouldBe "old"
        (journal.bytes != null) shouldBe true
        journal.beforeDelete = {}
        VerifiedSessionStorage(base, journal).load()!!.decodeToString() shouldBe "old"
    }

    "damaged recovery data is preserved and cannot overwrite the stored session" {
        val base = MemorySnapshotFile("old"); val journal = MemorySnapshotFile("broken")
        val storage = VerifiedSessionStorage(base, journal)
        shouldThrow<IOException> { storage.load() }
        shouldThrow<IOException> { storage.saveReviewed("new".toByteArray()) }
        base.text() shouldBe "old"
        journal.text() shouldBe "broken"
        storage.delete()
        storage.load() shouldBe null
    }

    "failed deletion retains the journal needed to reject an unacknowledged candidate" {
        val base = MemorySnapshotFile("old"); val journal = MemorySnapshotFile()
        base.afterWrite = { if (it.decodeToString() == "new") throw AssertionError("interrupted save") }
        val storage = VerifiedSessionStorage(base, journal)
        shouldThrow<AssertionError> { storage.saveReviewed("new".toByteArray()) }
        base.beforeDelete = { throw IOException("session is locked") }
        shouldThrow<IOException> { storage.delete() }
        (journal.bytes != null) shouldBe true
        base.text() shouldBe "new"
        base.beforeDelete = {}; base.afterWrite = {}
        storage.load()!!.decodeToString() shouldBe "old"
        journal.bytes shouldBe null
    }
})
