package com.vaibhav.calc.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises bundled OCR and image/capture paths on the oldest Android runtime. */
@RunWith(AndroidJUnit4::class)
class RecognitionDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun printed(text: String): Bitmap = Bitmap.createBitmap(700, 220, Bitmap.Config.ARGB_8888).also {
        Canvas(it).apply { drawColor(Color.WHITE); drawText(text, 40f, 135f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 84f }) }
    }

    @Test fun bundledOcrFeedsAutomaticCalculation() = runBlocking {
        val image = printed("12 + 34")
        try {
            val recognized = PhotoMathReader.read(image)
            assertTrue("raw=${recognized.rawText}; layout=${recognized.layout}", recognized.layout is MathLayoutResult.Draft)
            val expression = (recognized.layout as MathLayoutResult.Draft).expression
            val answer = RecognitionCalculator.preview(expression, Settings(), Environment())
            assertTrue("expression=$expression; answer=$answer", answer is RecognitionPreview.Answer)
            assertEquals("46", (answer as RecognitionPreview.Answer).rendered.primary)
        } finally { image.recycle() }
    }

    @Test fun emptyImageCannotProduceAnAnswer() = runBlocking {
        val image = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val recognized = PhotoMathReader.read(image)
            assertTrue(recognized.rawText.isBlank())
            assertTrue(recognized.layout is MathLayoutResult.Rejected)
        } finally { image.recycle() }
    }

    @Test fun fractionRuleRecoveryIgnoresTransparentBackground() {
        val image = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        val tokens = listOf(MathToken("1", MathBox(90f, 30f, 120f, 80f)), MathToken("2", MathBox(90f, 130f, 120f, 180f)))
        try {
            // Transparent black pixels are blank paper, not a giant dark rectangle.
            assertTrue(PhotoMathReader.missingRules(image, tokens).isEmpty())
            Canvas(image).drawRect(65f, 100f, 155f, 104f, Paint().apply { color = Color.BLACK })
            val rules = PhotoMathReader.missingRules(image, tokens)
            assertEquals(1, rules.size)
            assertTrue(rules.single().uncertain)
            val draft = MathLayout.read(tokens + rules) as MathLayoutResult.Draft
            val result = RecognitionCalculator.preview(draft.expression, Settings(), Environment()) as RecognitionPreview.Answer
            assertEquals("1/2", result.rendered.primary)
        } finally { image.recycle() }
    }

    @Test fun missingRadicalOverbarCanRestoreItsCompoundScope() {
        val image = Bitmap.createBitmap(240, 150, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val tokens = listOf(MathToken("√", MathBox(10f, 20f, 40f, 100f)), MathToken("1", MathBox(50f, 40f, 70f, 95f)),
            MathToken("+", MathBox(85f, 50f, 110f, 90f)), MathToken("3", MathBox(125f, 40f, 155f, 95f)))
        try {
            Canvas(image).drawRect(40f, 20f, 165f, 24f, Paint().apply { color = Color.BLACK })
            val rules = PhotoMathReader.missingRules(image, tokens)
            assertEquals(1, rules.size)
            val draft = MathLayout.read(tokens + rules) as MathLayoutResult.Draft
            val result = RecognitionCalculator.preview(draft.expression, Settings(), Environment()) as RecognitionPreview.Answer
            assertEquals("2", result.rendered.primary)
        } finally { image.recycle() }
    }

    @Test fun privateCameraUriCanBeDecodedAndDeleted() {
        val (path, uri) = PhotoInput.createCapture(context)
        val image = printed("6 * 7")
        try {
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.capture", uri.authority)
            context.contentResolver.openOutputStream(uri)!!.use { assertTrue(image.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            val decoded = PhotoInput.decode(context, uri)
            try { assertEquals(image.width, decoded.width); assertEquals(image.height, decoded.height) }
            finally { decoded.recycle() }
        } finally { image.recycle(); PhotoInput.discardCapture(context, path) }
        assertFalse(File(path).exists())
    }

    @Test fun captureCleanupCannotDeleteOutsideItsDirectory() {
        val other = File(context.cacheDir, "recognition-keep.txt").apply { writeText("keep") }
        try {
            try { PhotoInput.discardCapture(context, other.absolutePath); fail("outside path was accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals("keep", other.readText())
        } finally { other.delete() }
    }

    @Test fun oversizedImagesDecodeWithinThePixelBudget() {
        val file = File.createTempFile("recognition-test-", ".png", context.cacheDir)
        val image = Bitmap.createBitmap(4096, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val decoded = PhotoInput.decode(context, android.net.Uri.fromFile(file))
            try { assertTrue(decoded.width <= 2048); assertTrue(decoded.height > 0) }
            finally { decoded.recycle() }
        } finally { image.recycle(); file.delete() }
    }
}
