package com.vaibhav.calc.android

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Prepare without touching the displayed draft; publish only a completed current operation. */
internal object PhotoStaging {
    private val imageLane = Mutex()

    /**
     * One process-wide preparation lane, also used by orientation trials. Waiting is
     * cancellable. Structured blocking decode work must finish before this lock is
     * released, even when its caller has already requested cancellation.
     * Call once around the complete operation; helpers inside it must not re-enter.
     */
    suspend fun <T> imageOperation(operation: suspend () -> T): T = imageLane.withLock {
        currentCoroutineContext().ensureActive()
        val prepared = try {
            operation()
        } catch (_: OutOfMemoryError) {
            // Only image allocation/decode is translated. Do not catch publication,
            // native recognition, cancellation, or unrelated VM errors here.
            throw IllegalArgumentException("This image needs too much memory. Choose a smaller image or crop it first. Your previous draft is still available.")
        }
        currentCoroutineContext().ensureActive()
        prepared
    }

    /**
     * Called from the UI coroutine; [publish] must not suspend. Null means the operation
     * was superseded. Decode failures and cancellation propagate without publication.
     * A decoder swallowing CancellationException does not make its result publishable.
     */
    suspend fun <T : Any> prepare(
        ticket: Int,
        currentRevision: () -> Int,
        operation: suspend () -> T,
        publish: (T) -> Unit,
    ): T? {
        currentCoroutineContext().ensureActive()
        if (ticket != currentRevision()) return null
        val prepared = imageOperation {
            // The ticket may have changed while this operation waited for the lane.
            if (ticket == currentRevision()) operation() else null
        } ?: return null
        currentCoroutineContext().ensureActive()
        if (ticket != currentRevision()) return null
        publish(prepared)
        return prepared
    }
}
