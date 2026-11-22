package com.vaibhav.calc.android

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkReviewDeviceTest {
    @Test fun incompleteReviewRetainsEveryChoiceAcrossActivityRecreation() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var before: RecognitionWorkspaces
        try {
            scenario.onActivity { activity ->
                before = ViewModelProvider(activity)[RecognitionWorkspaces::class.java]
                before.ink.reviewTokens = listOf("?", "+", "?").mapIndexed { index, symbol ->
                    MathToken(symbol, MathBox(index * 40f, 10f, index * 40f + 20f, 70f),
                        uncertain = true, sourceIndex = index)
                }
                assertTrue(before.ink.replaceInkSymbol(0, "4"))
                assertEquals(listOf("4", "+", "?"), before.ink.reviewTokens.map { it.text })
                assertEquals("", before.ink.expression)
                assertNull(before.ink.mathDraft)
                before.ink.selectedGlyph = 2
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val after = ViewModelProvider(activity)[RecognitionWorkspaces::class.java]
                assertSame(before, after)
                assertEquals(2, after.ink.selectedGlyph)
                assertEquals(listOf("4", "+", "?"), after.ink.reviewTokens.map { it.text })
                assertTrue(after.ink.replaceInkSymbol(2, "2"))
                assertEquals("4+2", after.ink.expression)
                assertNotNull(after.ink.mathDraft)
                assertNull(after.ink.selectedGlyph)
                assertEquals(listOf(0, 1, 2), after.ink.reviewTokens.map { it.sourceIndex })
            }
        } finally { scenario.close() }
        assertTrue(before.ink.reviewTokens.isEmpty())
    }

    @Test fun drawingUndoAndClearInvalidateOldGlyphTargets() {
        val workspace = InkWorkspace()
        val stroke = listOf(InkPoint(10f, 10f), InkPoint(11f, 90f))
        workspace.commitInk(listOf(stroke))
        workspace.reviewTokens = listOf(MathToken("1", MathBox(10f, 10f, 11f, 90f)))
        workspace.selectedGlyph = 0
        assertTrue(workspace.commitInk(listOf(stroke, stroke.map { it.copy(x = it.x + 40f) })))
        assertTrue(workspace.reviewTokens.isEmpty())
        assertNull(workspace.selectedGlyph)
        assertFalse(workspace.replaceInkSymbol(0, "2"))
        workspace.reviewTokens = listOf(MathToken("?", MathBox(10f, 10f, 51f, 90f)))
        assertTrue(workspace.undoInk())
        assertTrue(workspace.reviewTokens.isEmpty())
        workspace.reviewTokens = listOf(MathToken("?", MathBox(10f, 10f, 11f, 90f)))
        workspace.clear()
        assertTrue(workspace.reviewTokens.isEmpty())
        assertEquals("", workspace.expression)
    }
}
