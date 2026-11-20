package com.vaibhav.calc.android

import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecognitionWorkspaceDeviceTest {
    @Test fun activityRecreationRetainsReviewableDraftsAndSaveState() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var before: RecognitionWorkspaces
        val picture = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        try {
            scenario.onActivity { activity ->
                before = ViewModelProvider(activity)[RecognitionWorkspaces::class.java]
                before.ink.commitInk(listOf(listOf(InkPoint(20f, 10f), InkPoint(20f, 150f))))
                before.ink.paperWidth = 300f; before.ink.paperHeight = 200f
                before.ink.expression = "1+2" // Manual correction must remain a correction.
                before.photo.bitmap = picture
                before.photo.text = "sqrt(16)"
                before.photo.recognizedText = "V16"
                before.photo.region = PhotoRegion(.1, .2, .8, .9)
                before.photo.treatment = PhotoImageTreatment.CONTRAST
                before.photo.pendingRender = PhotoRenderRequest(PhotoRegion(.2, .3, .7, .8), 1, PhotoImageTreatment.INVERT_CONTRAST)
                before.saving = true
                before.saveError = "A retained test notice"
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val after = ViewModelProvider(activity)[RecognitionWorkspaces::class.java]
                assertSame(before, after)
                assertEquals("1+2", after.ink.expression)
                assertEquals(InkPoint(20f, 150f), after.ink.strokes.single().last())
                assertTrue(after.ink.canUndoInk)
                assertTrue(after.ink.undoInk())
                assertTrue(after.ink.strokes.isEmpty())
                assertTrue(after.ink.redoInk())
                assertEquals(InkPoint(20f, 150f), after.ink.strokes.single().last())
                assertEquals(300f, after.ink.paperWidth, 0f)
                assertSame(picture, after.photo.bitmap)
                assertEquals("sqrt(16)", after.photo.text)
                assertEquals("V16", after.photo.recognizedText)
                assertEquals(PhotoRegion(.1, .2, .8, .9), after.photo.region)
                assertEquals(PhotoImageTreatment.CONTRAST, after.photo.treatment)
                assertEquals(PhotoRenderRequest(PhotoRegion(.2, .3, .7, .8), 1, PhotoImageTreatment.INVERT_CONTRAST), after.photo.pendingRender)
                assertTrue(after.saving)
                assertEquals("A retained test notice", after.saveError)
                after.saving = false
            }
        } finally { scenario.close() }
        assertNull(before.photo.bitmap)
        assertNull(before.photo.pendingRender)
        assertTrue(before.ink.strokes.isEmpty())
        assertFalse(before.ink.canUndoInk)
        assertFalse(before.ink.canRedoInk)
        // The application releases references instead of recycling potentially in-flight OCR input.
        assertFalse(picture.isRecycled)
        picture.recycle()
    }
}
