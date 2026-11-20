package com.vaibhav.calc.android

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

/** One lazy batch owner plus at most one admitted native Task. SDK work is gated separately. */
internal class NativeResourceSession<T : Any>(private val create: () -> T, private val dispose: (T) -> Unit) {
    private val lock = Any()
    private var resource: T? = null
    private var active = false
    private var ownerClosed = false

    class Lease<T : Any> internal constructor(val resource: T, private val finish: () -> Unit) {
        private val released = AtomicBoolean(false)
        fun release() { if (released.compareAndSet(false, true)) finish() }
    }

    /** Reserve before creating: cancellation during creation cannot dispose an unfinished client. */
    fun acquire(): Lease<T> {
        val existing = synchronized(lock) {
            check(!ownerClosed) { "The native resource session is closed." }
            check(!active) { "A native task is already using this resource." }
            active = true
            resource
        }
        val client = try { existing ?: create() } catch (problem: Throwable) {
            synchronized(lock) { active = false }
            throw problem
        }
        val cancelled = synchronized(lock) {
            resource = client
            ownerClosed
        }
        if (cancelled) {
            val failure = CancellationException("The native resource owner closed during preparation.")
            runCatching { releaseTask() }.onFailure { failure.addSuppressed(it) }
            throw failure
        }
        return try { Lease(client, ::releaseTask) } catch (problem: Throwable) {
            runCatching { releaseTask() }.onFailure { problem.addSuppressed(it) }
            throw problem
        }
    }

    /** Never waits for a live Task. Its final lease will perform deferred cleanup. */
    fun closeOwner() {
        val client = synchronized(lock) {
            ownerClosed = true
            detachIfUnused()
        }
        if (client != null) dispose(client)
    }

    private fun releaseTask() {
        val client = synchronized(lock) {
            check(active) { "There is no active native task." }
            active = false
            detachIfUnused()
        }
        if (client != null) dispose(client)
    }

    private fun detachIfUnused(): T? = if (ownerClosed && !active) resource.also { resource = null } else null
}
