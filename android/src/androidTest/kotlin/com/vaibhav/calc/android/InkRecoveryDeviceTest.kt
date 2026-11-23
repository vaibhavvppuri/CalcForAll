package com.vaibhav.calc.android

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual local recognizer output plus workspace lifecycle checks; not automated canvas UI QA. */
@RunWith(AndroidJUnit4::class)
class InkRecoveryDeviceTest {
    @Test fun explicitReviewAndPauseSurviveRecreationButNewInkRequiresANewReading() {
        fun path(vararg xy: Int) = xy.toList().chunked(2).map { InkPoint(it[0].toFloat(), it[1].toFloat()) }
        val ink = listOf(path(0, 0, 0, 80), path(31, 40, 61, 41, 92, 39),
            path(61, 17, 62, 18), path(60, 63), path(122, 0, 123, 80))
        // Recognition runs on the instrumentation thread, not the Activity main thread.
        val reading = MathInkRecognizer.readDetailed(ink)
        val draft = reading.layout as? MathLayoutResult.Draft
        assertNotNull("Actual local division fixture must remain a complete draft: $reading", draft)
        assertEquals("1/1", draft!!.expression)
        assertTrue(reading.tokens[1].uncertain)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var workspace: InkWorkspace
        try {
            scenario.onActivity { activity ->
                workspace = ViewModelProvider(activity)[RecognitionWorkspaces::class.java].ink
                workspace.clear(); workspace.mode = "Math layout"
                assertTrue(workspace.commitInk(ink))
                workspace.reviewTokens = reading.tokens; workspace.mathDraft = draft; workspace.expression = draft.expression
                workspace.recognizedStrokes = workspace.strokes; workspace.recognizedMode = workspace.mode
                workspace.recognizedRequest = workspace.recognitionRequest
                assertTrue(workspace.needsExpressionReview)
                assertTrue(workspace.acknowledgeExpression())
                workspace.pauseRecognition()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val retained = ViewModelProvider(activity)[RecognitionWorkspaces::class.java].ink
                assertSame(workspace, retained)
                assertTrue(retained.recognitionPaused)
                assertFalse(retained.needsExpressionReview)
                assertTrue(retained.expressionWasReviewed)
                retained.retryRecognition()
                assertEquals("1/1", retained.expression)
                assertTrue(retained.expressionWasReviewed)
                assertTrue(retained.commitInk(ink + listOf(path(170, 0, 170, 80))))
                assertFalse(retained.expressionWasReviewed)
                assertFalse(retained.recognitionPaused)
                assertEquals("", retained.expression)
                assertTrue(retained.reviewTokens.isEmpty())
            }
        } finally { scenario.close() }
        assertEquals("", workspace.expression)
        assertFalse(workspace.expressionWasReviewed)
    }
}
