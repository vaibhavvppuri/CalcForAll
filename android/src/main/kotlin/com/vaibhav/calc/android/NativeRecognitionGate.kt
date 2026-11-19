package com.vaibhav.calc.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

/** Serializes non-cancellable native work without making its caller wait through cancellation. */
internal class NativeRecognitionGate {
    private val slot = Mutex()

    /** [start] must invoke its callback only after the native work and resource cleanup finish. */
    suspend fun <T> awaitCompletion(start: (finish: (Result<T>) -> Unit) -> Unit): T {
        slot.lock() // Waiting callers can cancel without ever starting native work.
        return suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            val finish: (Result<T>) -> Unit = { result ->
                if (finished.compareAndSet(false, true)) {
                    slot.unlock()
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            }
            if (!continuation.isActive) {
                finish(Result.failure(CancellationException("Recognition was cancelled before it started.")))
            } else try {
                start(finish)
            } catch (problem: Throwable) {
                // Startup failed synchronously; there is no native completion to wait for.
                finish(Result.failure(problem))
            }
        }
    }
}
