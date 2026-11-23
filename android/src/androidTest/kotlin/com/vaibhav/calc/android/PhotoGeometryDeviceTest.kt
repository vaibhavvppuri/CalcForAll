package com.vaibhav.calc.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CancellationException

/** Controlled geometry/pixel regressions, not a photographed-page accuracy benchmark. */
@RunWith(AndroidJUnit4::class)
class PhotoGeometryDeviceTest {
    @Test fun paddedStraighteningPreservesCornerMarksAndInputOwnership() {
        val original = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA)
        val centers = listOf(14f to 14f, 386f to 14f, 386f to 226f, 14f to 226f)
        Canvas(original).apply { centers.zip(colors).forEach { (point, color) ->
            drawCircle(point.first, point.second, 10f, Paint().apply { this.color = color })
        } }
        try {
            assertSame(original, PhotoImageGeometry.apply(original))
            for (angle in listOf(-17.0, 17.0)) {
                val adjustment = PhotoGeometryAdjustment(straightenDegrees = angle)
                val plan = PhotoGeometry.plan(original.width, original.height, adjustment)
                val output = PhotoImageGeometry.apply(original, adjustment)
                try {
                    assertEquals(Color.WHITE, output.getPixel(0, 0))
                    centers.zip(colors).forEach { (point, color) ->
                        val mapped = plan.map(point.first.toDouble(), point.second.toDouble())
                        assertEquals(color, output.getPixel(mapped.first.toInt(), mapped.second.toInt()))
                    }
                    assertTrue(output.width > original.width && output.height > original.height)
                } finally { output.recycle() }
            }
            assertFalse(original.isRecycled)
            centers.zip(colors).forEach { (point, color) -> assertEquals(color, original.getPixel(point.first.toInt(), point.second.toInt())) }
            val quality = PhotoImageQuality.assess(original)
            assertTrue(quality.sampleCount in 1..PhotoQualitySamples.MAX_SAMPLES)
            assertFalse(original.isRecycled)
            var checks = 0
            try {
                PhotoImageGeometry.apply(original, PhotoGeometryAdjustment(straightenDegrees = 5.0)) { ++checks >= 3 }
                fail("Cancellation after allocation was ignored")
            } catch (_: CancellationException) { }
            assertFalse(original.isRecycled)
        } finally { original.recycle() }
    }

    @Test fun manualPerspectiveRestoresTheDisplayedSyntheticPrintForActualOfflineOcr() = runBlocking {
        val upright = Bitmap.createBitmap(700, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(upright).drawText("12 + 34", 40f, 155f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 84f })
        val photographed = Bitmap.createBitmap(1000, 650, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(180, 180, 180)) }
        // Independent known camera quadrilateral; the operator selects these actual corners.
        val coordinates = floatArrayOf(140f, 90f, 850f, 150f, 780f, 510f, 200f, 470f)
        val camera = Matrix()
        assertTrue(camera.setPolyToPoly(floatArrayOf(0f, 0f, 700f, 0f, 700f, 240f, 0f, 240f), 0, coordinates, 0, 4))
        Canvas(photographed).drawBitmap(upright, camera, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        val quad = PhotoQuad(PhotoPoint(.14, 90.0 / 650), PhotoPoint(.85, 150.0 / 650),
            PhotoPoint(.78, 510.0 / 650), PhotoPoint(.2, 470.0 / 650))
        var corrected: Bitmap? = null
        var nativeFinished = false
        try {
            val displayed = PhotoImageGeometry.apply(photographed, PhotoGeometryAdjustment(quad))
            corrected = displayed
            assertFalse(photographed.isRecycled)
            assertTrue(displayed.width <= 2048 && displayed.height <= 2048)
            assertEquals(Color.WHITE, displayed.getPixel(displayed.width / 2, 8))
            val draft = PhotoMathReader.read(displayed, onNativeCompleted = { nativeFinished = true })
            val preview = RecognitionCalculator.preview(draft.expressionForReview, Settings(), Environment())
            assertTrue("Transcription=${draft.rawText}; layout=${draft.layout}; preview=$preview", preview is RecognitionPreview.Answer)
            assertEquals("46", (preview as RecognitionPreview.Answer).rendered.primary)
        } finally {
            // The native adapter owns displayed pixels until real SDK completion.
            if (nativeFinished) corrected?.recycle()
            photographed.recycle(); upright.recycle()
        }
    }
}
