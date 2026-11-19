package com.vaibhav.calc.android

import java.io.IOException

/** Serializes all stores for one private file, including stores from a recreated activity. */
internal class SessionWriteCoordinator {
    private val stateLock = Any()
    private val diskLock = Any()
    private var owner = 0L
    private var version = 0L
    private var reserved: Long? = null
    private data class LifecycleCheckpoint(val token: Long, val write: () -> Unit)
    private var pendingLifecycle: LifecycleCheckpoint? = null

    fun openOwner(): Long = synchronized(stateLock) {
        owner++
        version++
        reserved = null
        // A queued lifecycle checkpoint belongs to the file, not the dead activity.
        // The replacement load must drain it before reading the older disk snapshot.
        owner
    }

    fun ticket(client: Long): Long = synchronized(stateLock) {
        requireOwner(client)
        // A stop/dispose flush must not supersede a recognition transaction.
        if (reserved != null) -1L else {
            pendingLifecycle = null
            ++version
        }
    }

    fun reserve(client: Long): Long = synchronized(stateLock) {
        requireOwner(client)
        if (reserved != null) throw IOException("A calculation is already being saved.")
        pendingLifecycle = null
        (++version).also { reserved = it }
    }

    /** Register before dispatch so an activity replacement can load the latest snapshot. */
    fun registerLifecycle(client: Long, action: () -> Unit): Long = synchronized(stateLock) {
        requireOwner(client)
        if (reserved != null) -1L else {
            val token = ++version
            pendingLifecycle = LifecycleCheckpoint(token, action)
            token
        }
    }

    /** An asynchronous worker may finish this checkpoint even after its owner changes. */
    fun drainLifecycle(token: Long): Boolean = synchronized(diskLock) { drainLifecycleLocked(token) }

    private fun drainLifecycleLocked(token: Long? = null): Boolean {
        val checkpoint = synchronized(stateLock) {
            pendingLifecycle?.takeIf { token == null || it.token == token }
        } ?: return false
        // Failure remains observable to a replacement load, which can safely retry.
        checkpoint.write()
        synchronized(stateLock) {
            if (pendingLifecycle === checkpoint) pendingLifecycle = null
        }
        return true
    }

    fun write(client: Long, ticket: Long, action: () -> Unit): Boolean = synchronized(diskLock) {
        val accepted = synchronized(stateLock) {
            client == owner && ticket == version && reserved == null
        }
        if (!accepted) return@synchronized false
        action()
        true
    }

    fun writeReserved(client: Long, ticket: Long, action: () -> Unit): Unit = synchronized(diskLock) {
        synchronized(stateLock) {
            requireOwner(client)
            if (ticket != version || reserved != ticket) throw IOException("This save was superseded by a newer session.")
        }
        // Admission is the operation's ordering point. A replacement owner can be
        // activated immediately, but its load/write waits for this disk operation.
        // Never abort an admitted durable write halfway through its recovery protocol.
        action()
        // Keep the reservation until the caller has published the matching UI snapshot.
    }

    fun release(client: Long, ticket: Long): Unit = synchronized(stateLock) {
        if (client == owner && reserved == ticket) reserved = null
    }

    fun <T> read(client: Long, action: () -> T): T = synchronized(diskLock) {
        synchronized(stateLock) { requireOwner(client) }
        drainLifecycleLocked()
        action()
    }

    fun delete(client: Long, action: () -> Unit): Unit = synchronized(diskLock) {
        synchronized(stateLock) {
            requireOwner(client)
            if (reserved != null) throw IOException("Finish saving the calculation before deleting local data.")
            version++
            pendingLifecycle = null
        }
        action()
    }

    private fun requireOwner(client: Long) {
        if (client != owner) throw IOException("This session was replaced by a recreated activity.")
    }
}
