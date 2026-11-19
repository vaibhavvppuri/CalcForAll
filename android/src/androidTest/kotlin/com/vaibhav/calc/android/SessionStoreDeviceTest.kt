package com.vaibhav.calc.android

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaibhav.calc.core.tape.SessionSnapshot
import com.vaibhav.calc.core.tape.SnapshotRead
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Exercises the platform AtomicFile implementation without accessing application session data. */
@RunWith(AndroidJUnit4::class)
class SessionStoreDeviceTest {
    @Test fun reviewedSnapshotRoundTripsThroughActualAtomicFile() = withStorage { context, directory ->
        val session = CalcSession()
        session.replaceText("7 -> A"); session.commit()
        session.replaceText("A*6"); session.commit()
        session.replaceText("9+1")
        val expected = snapshot(session)
        val store = SessionStore(context)
        writeReviewed(store, expected)
        assertEquals(expected, restored(SessionStore(context)))
        assertTrue(File(directory, "session.calc").isFile)
        assertFalse(File(directory, "session.calc.recognition-rollback").exists())
        assertFalse(File(directory, "session.calc.bak").exists())
        assertFalse(File(directory, "session.calc.new").exists())
    }

    @Test fun replacementStoreDrainsPendingLifecycleSnapshotBeforeLoading() = withStorage { context, _ ->
        val session = CalcSession()
        session.replaceText("1+1"); session.commit()
        val original = SessionStore(context)
        writeReviewed(original, snapshot(session))
        session.replaceText("12+34"); session.commit()
        session.replaceText("sqrt(16)")
        val latest = snapshot(session)
        lateinit var dispatched: () -> Unit
        // No sleep or scheduler race: the background callback has not run at handoff.
        original.enqueueLifecycleSave(latest) { dispatched = it }
        val replacement = SessionStore(context)
        assertEquals(latest, restored(replacement))
        session.replaceText("5+6"); session.commit()
        val newerReviewed = snapshot(session)
        writeReviewed(replacement, newerReviewed)
        dispatched() // A delayed old worker must not replace a newer reviewed save.
        assertEquals(newerReviewed, restored(replacement))
    }

    @Test fun storageObstructionRefusesSavePreservesSessionAndAllowsOneRetry() = withStorage { context, directory ->
        val session = CalcSession()
        session.replaceText("7 -> A"); session.commit()
        session.replaceText("3+4")
        val store = SessionStore(context)
        val before = snapshot(session)
        writeReviewed(store, before)
        val bytesBefore = File(directory, "session.calc").readBytes()
        val obstruction = File(directory, "session.calc.recognition-rollback")
        assertTrue(obstruction.mkdir())
        try {
            val ticket = store.beginDurableWrite()
            val result = try {
                session.saveRecognitionDurably("A*6", "DARK") { store.saveDurably(it, ticket) }
            } finally { store.endDurableWrite(ticket) }
            assertFalse("A directory cannot be accepted as a verified rollback journal", result.accepted)
            assertNotNull(result.error)
            assertEquals(before, snapshot(session))
            assertArrayEquals(bytesBefore, File(directory, "session.calc").readBytes())
        } finally {
            assertTrue("Remove only the empty obstruction created by this test", obstruction.delete())
        }
        assertEquals(before, restored(store))
        val ticket = store.beginDurableWrite()
        val retry = try {
            session.saveRecognitionDurably("A*6", "DARK") { store.saveDurably(it, ticket) }
        } finally { store.endDurableWrite(ticket) }
        assertTrue(retry.error, retry.accepted)
        assertEquals("42", retry.state.exact)
        assertEquals(listOf(1, 2), retry.state.entries.map { it.id.n })
        assertEquals(retry.snapshot, restored(SessionStore(context)))
        assertFalse(File(directory, "session.calc.recognition-rollback").exists())
    }

    private fun snapshot(session: CalcSession) =
        SessionSnapshot(session.settings, session.ui.editor, session.archive(), "DARK")

    private fun writeReviewed(store: SessionStore, snapshot: SessionSnapshot) {
        val ticket = store.beginDurableWrite()
        try { store.saveDurably(snapshot, ticket) } finally { store.endDurableWrite(ticket) }
    }

    private fun restored(store: SessionStore): SessionSnapshot {
        val loaded = store.load()
        assertTrue("Expected a restored snapshot, got $loaded", loaded is SnapshotRead.Restored)
        return (loaded as SnapshotRead.Restored).snapshot
    }

    private fun withStorage(block: (Context, File) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.cacheDir, "session-store-device-${UUID.randomUUID()}")
        assertTrue(directory.mkdir())
        val isolated = object : ContextWrapper(app) { override fun getFilesDir() = directory }
        try { block(isolated, directory) }
        finally {
            // The only recursively removed path is this newly created direct cache child.
            check(directory.canonicalFile.parentFile == app.cacheDir.canonicalFile)
            directory.walkBottomUp().forEach { child ->
                check(child.canonicalPath == directory.canonicalPath ||
                    child.canonicalPath.startsWith(directory.canonicalPath + File.separator))
                check(child.delete()) { "Could not clean test-owned path ${child.name}" }
            }
        }
    }
}
