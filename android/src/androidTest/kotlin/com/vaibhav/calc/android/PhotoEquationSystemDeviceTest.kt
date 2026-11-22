package com.vaibhav.calc.android

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** A synthetic fixture regression through the actual SDK, not a recognition accuracy claim. */
@RunWith(AndroidJUnit4::class)
class PhotoEquationSystemDeviceTest {
    @Test fun completeSeparatedEquationsOfferAReviewedSystemWithoutAutomaticRawFallback() = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bitmap = checkNotNull(assets.open("photo-corpus/08-equation-system.png").use { BitmapFactory.decodeStream(it) })
        var finished = false
        try {
            val draft = PhotoMathReader.read(bitmap)
            finished = true
            assertFalse(draft.truncated)
            assertFalse(draft.incomplete)
            assertTrue(draft.completeRawText.isNotBlank())
            assertEquals("A multiline candidate must remain an explicit choice", "", draft.expressionForReview)
            assertTrue(draft.corrections.size <= PhotoMathCorrections.MAX_CORRECTIONS)
            val candidate = draft.corrections.singleOrNull { it.expression.count { c -> c == '\n' } == 1 }
            assertNotNull("No two-row option was offered from actual SDK tokens: $draft", candidate)
            assertTrue(candidate!!.explanation.contains("2 separate rows"))
            val outcome = Calculator().route(candidate.expression)
            assertTrue("The production candidate did not solve: $outcome", outcome is RouteResult.Ok)
            val solved = (outcome as RouteResult.Ok).outcome
            assertTrue(solved is Outcome.SystemSolution)
            solved as Outcome.SystemSolution
            assertEquals(mapOf("x" to Num.of(3), "y" to Num.of(2)), solved.unknowns.zip(solved.values).toMap())
            assertEquals("Choosing is still required after inspecting the candidate", "", draft.expressionForReview)
        } finally {
            // Do not recycle the input if cancellation returned while native work still owns it.
            if (finished) bitmap.recycle()
        }
    }
}
