package com.vaibhav.calc.android

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/** Opt-in local timing only: never records OCR text, pixels, paths, URIs, or exception messages. */
internal class PhotoReadTiming(private val width: Int, private val height: Int, private val turns: Int = 0) {
    private val enabled = Log.isLoggable(TAG, Log.DEBUG)
    private val run = nextRun.incrementAndGet()
    private val started = System.nanoTime()
    private var previous = started

    @Synchronized fun stage(name: String) {
        if (!enabled) return
        val now = System.nanoTime()
        Log.d(TAG, "run=$run width=$width height=$height turns=$turns stage=$name elapsedMs=${(now - started) / 1_000_000} phaseMs=${(now - previous) / 1_000_000}")
        previous = now
    }

    companion object {
        private const val TAG = "CalcPhoto"
        private val nextRun = AtomicLong()
    }
}
