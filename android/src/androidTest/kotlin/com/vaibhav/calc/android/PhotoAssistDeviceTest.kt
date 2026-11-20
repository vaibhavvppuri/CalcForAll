package com.vaibhav.calc.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Settings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic recovery regressions, not a real-photo recognition accuracy benchmark. */
@RunWith(AndroidJUnit4::class)
class PhotoAssistDeviceTest {
    private fun printed(foreground: Int, background: Int) = Bitmap.createBitmap(700, 220, Bitmap.Config.ARGB_8888).also {
        Canvas(it).apply {
            drawColor(background)
            drawText("12 + 34", 40f, 135f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foreground; textSize = 84f })
        }
    }
    private fun answer(draft: PhotoMathDraft): String {
        val preview = RecognitionCalculator.preview(draft.expressionForReview, Settings(), Environment())
        assertTrue("raw=${draft.rawText}; layout=${draft.layout}; preview=$preview", preview is RecognitionPreview.Answer)
        return (preview as RecognitionPreview.Answer).rendered.primary
    }

    @Test fun quarterTurnsPreserveExactPixelsAndInputOwnership() {
        val pixels = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA)
        val original = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        original.setPixels(pixels, 0, 3, 0, 0, 3, 2)
        try {
            assertSame(original, PhotoImageAssist.apply(original))
            val expected = listOf(intArrayOf(3, 0, 4, 1, 5, 2), intArrayOf(5, 4, 3, 2, 1, 0), intArrayOf(2, 5, 1, 4, 0, 3))
            for (turn in 1..3) {
                val rotated = PhotoImageAssist.apply(original, quarterTurns = turn)
                try {
                    assertEquals(if (turn % 2 == 0) 3 else 2, rotated.width)
                    assertEquals(if (turn % 2 == 0) 2 else 3, rotated.height)
                    val actual = IntArray(6)
                    rotated.getPixels(actual, 0, rotated.width, 0, 0, rotated.width, rotated.height)
                    assertArrayEquals(expected[turn - 1].map { pixels[it] }.toIntArray(), actual)
                } finally { if (rotated !== original) rotated.recycle() }
            }
            assertFalse(original.isRecycled)
            val unchanged = IntArray(6)
            original.getPixels(unchanged, 0, 3, 0, 0, 3, 2)
            assertArrayEquals(pixels, unchanged)
        } finally { original.recycle() }
        val oversized = Bitmap.createBitmap(2049, 1, Bitmap.Config.ARGB_8888)
        try {
            try { PhotoImageAssist.apply(oversized); fail("Oversized image accepted") }
            catch (_: IllegalArgumentException) { }
        } finally { oversized.recycle() }
    }

    @Test fun explicitContrastAndPolarityTreatmentsFeedTheDisplayedPixelsToOcr() = runBlocking {
        val cases = listOf(Triple(Color.rgb(212, 212, 212), Color.rgb(236, 236, 236), PhotoImageTreatment.CONTRAST),
            Triple(Color.rgb(235, 235, 235), Color.rgb(30, 30, 30), PhotoImageTreatment.INVERT_CONTRAST))
        for ((foreground, background, treatment) in cases) {
            val original = printed(foreground, background)
            val prepared = PhotoImageAssist.apply(original, treatment)
            try {
                assertNotSame(original, prepared)
                assertEquals(Color.WHITE, prepared.getPixel(0, 0))
                assertEquals(background, original.getPixel(0, 0))
                assertEquals("46", answer(PhotoMathReader.read(prepared)))
            } finally { prepared.recycle(); original.recycle() }
        }
    }

    @Test fun orientationSearchOffersTheActualUprightImageWithoutPickingANumericalWinner() = runBlocking {
        val upright = printed(Color.BLACK, Color.WHITE)
        try {
            for (inputTurn in listOf(1, 2)) {
                val input = PhotoImageAssist.apply(upright, quarterTurns = inputTurn)
                try {
                    var created = 0; var closed = 0
                    val options = PhotoMathReader.orientationAlternativesObserved(input, PhotoBatchObserver(
                        onClientCreated = { created++ }, onClientClosed = { closed++ }))
                    assertEquals("A three-turn batch creates one recognizer", 1, created)
                    assertEquals("A completed batch closes its recognizer once", 1, closed)
                    assertTrue(options.size <= 3)
                    assertEquals(options.map { it.turns }.sorted(), options.map { it.turns })
                    val needed = 4 - inputTurn
                    val correctOrientation = options.singleOrNull { it.turns == needed }
                    assertNotNull("The upright orientation was not offered: $options", correctOrientation)
                    assertEquals("46", answer(correctOrientation!!.draft))
                    val reviewed = PhotoImageAssist.apply(input, quarterTurns = needed)
                    try { assertTrue("Selected preview must match the pixels analyzed", upright.sameAs(reviewed)) }
                    finally { if (reviewed !== input) reviewed.recycle() }
                    assertFalse(input.isRecycled)
                } finally { if (input !== upright) input.recycle() }
            }
        } finally { upright.recycle() }
    }

    @Test fun anOmittedOneSidedRuleCannotBecomeJustItsNumeratorOrDenominator() {
        for (box in listOf(MathBox(90f, 30f, 120f, 80f), MathBox(90f, 130f, 120f, 180f))) {
            val image = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            try {
                Canvas(image).drawRect(65f, 100f, 155f, 104f, Paint().apply { color = Color.BLACK })
                val tokens = listOf(MathToken("12", box))
                val rules = PhotoMathReader.missingRules(image, tokens)
                assertEquals(1, rules.size)
                assertTrue(rules.single().uncertain)
                val layout = MathLayout.read(tokens + rules)
                assertTrue("A one-sided bar must require correction: $layout", layout is MathLayoutResult.Rejected)
                assertEquals("", PhotoMathDraft("12", layout).expressionForReview)
            } finally { image.recycle() }
        }
    }

    @Test fun sdkLaunchThenOwnerCancellationRetainsCleanupBeforeRetry() = runBlocking {
        val cancelledInput = printed(Color.BLACK, Color.WHITE)
        val retryInput = printed(Color.BLACK, Color.WHITE)
        val started = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        var retryFinished = false
        try {
            withTimeout(45_000) { supervisorScope {
                var completedBeforeCancellation = true
                lateinit var owner: Deferred<PhotoMathDraft>
                owner = async(start = CoroutineStart.LAZY) {
                    PhotoMathReader.read(cancelledInput, onNativeStarted = {
                        // Cancel synchronously after SDK launch, before completion
                        // delivery is installed. Pixel computation itself may already
                        // be finished on a fast device; no timing claim is inferred.
                        completedBeforeCancellation = completed.isCompleted
                        owner.cancel()
                        started.complete(Unit)
                        // Observers are informational; an exception must not release
                        // the native gate while the SDK is still using this bitmap.
                        throw IllegalStateException("synthetic observer failure")
                    }, onNativeCompleted = { completed.complete(Unit) })
                }
                owner.start()
                started.await()
                assertFalse("Completion cleanup must not precede the cancellation request", completedBeforeCancellation)
                owner.join()
                assertTrue("The read owner must actually have been cancelled", owner.isCancelled)
                assertFalse(cancelledInput.isRecycled)
                var overlapped = false
                val retry = PhotoMathReader.read(retryInput, onNativeStarted = { overlapped = !completed.isCompleted })
                retryFinished = true
                completed.await()
                assertFalse("Retry entered before the previous SDK task completed", overlapped)
                assertEquals("46", answer(retry))
            } }
        } finally {
            // Never recycle an input still owned by non-cancellable SDK work,
            // including when this regression fails or reaches its deadline.
            if (completed.isCompleted) cancelledInput.recycle()
            if (retryFinished) retryInput.recycle()
        }
    }

    @Test fun cancelledOrientationBatchClosesAfterNativeCompletionBeforeOrdinaryRetry() = runBlocking {
        val original = printed(Color.BLACK, Color.WHITE)
        val retryInput = printed(Color.BLACK, Color.WHITE)
        val started = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        var retryFinished = false
        try {
            withTimeout(45_000) { supervisorScope {
                var completedAtCancellation = true; var closedAtCancellation = true
                lateinit var owner: Deferred<List<PhotoOrientationOption>>
                owner = async(start = CoroutineStart.LAZY) {
                    PhotoMathReader.orientationAlternativesObserved(original, PhotoBatchObserver(
                        onNativeStarted = {
                            completedAtCancellation = completed.isCompleted
                            closedAtCancellation = closed.isCompleted
                            owner.cancel(); started.complete(Unit)
                            throw IllegalStateException("synthetic batch observer failure")
                        },
                        onNativeCompleted = { completed.complete(Unit) },
                        onClientClosed = { closed.complete(Unit) },
                    ))
                }
                owner.start(); started.await(); owner.join()
                assertTrue(owner.isCancelled)
                assertFalse(completedAtCancellation)
                assertFalse(closedAtCancellation)
                var enteredBeforeCleanup = false
                val retry = PhotoMathReader.read(retryInput, onNativeStarted = {
                    enteredBeforeCleanup = !completed.isCompleted || !closed.isCompleted
                })
                retryFinished = true
                completed.await(); closed.await()
                assertFalse("Ordinary retry must wait for the cancelled batch's actual cleanup", enteredBeforeCleanup)
                assertEquals("46", answer(retry))
            } }
        } finally {
            if (closed.isCompleted) original.recycle()
            if (retryFinished) retryInput.recycle()
        }
    }
}
