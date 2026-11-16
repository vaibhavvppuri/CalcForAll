package com.vaibhav.calc.android

import android.content.Context
import android.util.AtomicFile
import com.vaibhav.calc.core.tape.SessionCodec
import com.vaibhav.calc.core.tape.SessionSnapshot
import com.vaibhav.calc.core.tape.SnapshotRead
import java.io.File
import java.io.IOException
import java.io.FileNotFoundException

/** Private local storage. Atomic replacement preserves the last good session after a failed save. */
class SessionStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "session.calc"))
    private var writeVersion = 0L

    @Synchronized
    fun newWriteTicket(): Long = ++writeVersion

    @Synchronized
    fun load(): SnapshotRead? {
        return try {
            file.openRead().use { input ->
                if (input.channel.size() > SessionCodec.MAX_BYTES) SnapshotRead.Invalid("Saved session is too large.")
                else SessionCodec.decode(input.readBytes())
            }
        } catch (e: FileNotFoundException) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) null
            else SnapshotRead.Invalid("Could not open saved calculations: ${e.message}")
        } catch (e: IOException) {
            SnapshotRead.Invalid("Could not read saved calculations: ${e.message}")
        }
    }

    fun save(snapshot: SessionSnapshot, ticket: Long) {
        val data = SessionCodec.encode(snapshot)
        synchronized(this) {
            if (ticket != writeVersion) return
            val stream = file.startWrite()
            try {
                stream.write(data)
                file.finishWrite(stream)
            } catch (e: IOException) {
                file.failWrite(stream)
                throw e
            }
        }
    }

    @Synchronized
    fun delete() { writeVersion++; file.delete() }
}
