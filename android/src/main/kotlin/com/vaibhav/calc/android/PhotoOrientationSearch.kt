package com.vaibhav.calc.android

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Fixed work budget and geometric ordering, independent of the calculator's answers. */
internal object PhotoOrientationSearch {
    suspend fun collect(onTurnStarted: (Int) -> Unit = {},
                        onOptionsReady: (List<PhotoOrientationOption>) -> Unit = {},
                        readTurn: suspend (Int) -> PhotoMathDraft): List<PhotoOrientationOption> {
        val options = ArrayList<PhotoOrientationOption>(3)
        for (turns in 1..3) {
            currentCoroutineContext().ensureActive()
            onTurnStarted(turns)
            currentCoroutineContext().ensureActive()
            val draft = readTurn(turns)
            currentCoroutineContext().ensureActive()
            if (!draft.truncated && !draft.incomplete && draft.rawText.isNotBlank()) {
                options += PhotoOrientationOption(turns, draft)
                onOptionsReady(options.toList())
                currentCoroutineContext().ensureActive()
            }
        }
        return options
    }
}
