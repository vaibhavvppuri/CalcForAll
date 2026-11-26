package com.vaibhav.calc.android

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Independent coordinate fixtures on the device; not a canvas gesture or handwriting accuracy benchmark. */
@RunWith(AndroidJUnit4::class)
class InkStyleRecoveryDeviceTest {
    @Test fun narrowDigitsRetainTheirReviewRequirementAcrossRecreation() {
        fun path(vararg xy: Int) = xy.toList().chunked(2).map { InkPoint(it[0].toFloat(), it[1].toFloat()) }
        fun narrow(path: List<InkPoint>, dx: Float) = path.map { InkPoint(dx + it.x * .65f, 17f + it.y) }
        val ink = listOf(
            narrow(path(64,110,64,5,10,70,88,70), 23f),
            path(110,72,145,72), path(127,55,127,90),
            narrow(path(79,29,73,8,47,3,22,16,13,39,26,58,54,61,76,45,80,29,78,67,64,96,41,111), 173f)
        )
        // Match outside the Activity main thread. Every coordinate is independently specified.
        val reading = MathInkRecognizer.readDetailed(ink)
        val draft = reading.layout as? MathLayoutResult.Draft
        assertNotNull("Complete narrow-digit reading required: $reading", draft)
        assertEquals("4+9", draft!!.expression)
        assertTrue(reading.tokens.first().uncertain)
        assertTrue(reading.tokens.last().uncertain)
        val evaluated = Calculator().evaluate(draft.expression)
        val real = (evaluated as? CalcResult.Ok<*>)?.value as? RealValue
        assertNotNull("Recognized draft must evaluate to a real number: $evaluated", real)
        assertEquals(Num.of(13), real!!.num)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var workspace: InkWorkspace
        try {
            scenario.onActivity { activity ->
                workspace = ViewModelProvider(activity)[RecognitionWorkspaces::class.java].ink
                workspace.clear()
                workspace.mode = "Math layout"
                assertTrue(workspace.commitInk(ink))
                workspace.reviewTokens = reading.tokens
                workspace.mathDraft = draft
                workspace.expression = draft.expression
                workspace.recognizedStrokes = workspace.strokes
                workspace.recognizedMode = workspace.mode
                workspace.recognizedRequest = workspace.recognitionRequest
                assertTrue(workspace.needsExpressionReview)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val retained = ViewModelProvider(activity)[RecognitionWorkspaces::class.java].ink
                assertSame(workspace, retained)
                assertEquals("4+9", retained.expression)
                assertTrue(retained.needsExpressionReview)
                assertTrue(retained.acknowledgeExpression())
                assertFalse(retained.needsExpressionReview)
                assertTrue(retained.commitInk(ink + listOf(path(270,17,270,122))))
                assertFalse(retained.expressionWasReviewed)
                assertEquals("", retained.expression)
            }
        } finally { scenario.close() }
    }
}
