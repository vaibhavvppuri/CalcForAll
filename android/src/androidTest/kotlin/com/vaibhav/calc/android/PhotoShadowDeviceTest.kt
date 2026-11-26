package com.vaibhav.calc.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CancellationException
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class PhotoShadowDeviceTest {
    /** Controlled transformed-input regression, not a claim that the original is unreadable. */
    @Test fun explicitShadowReductionKeepsPrintedArithmeticAndInputOwnership() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val context = instrumentation.targetContext
        val original = assets.open("photo-corpus/01-addition.png").use { BitmapFactory.decodeStream(it) }!!
        val shaded = original.copy(Bitmap.Config.ARGB_8888, true)
        original.recycle()
        val row = IntArray(shaded.width)
        for (y in 0 until shaded.height) {
            shaded.getPixels(row, 0, row.size, 0, y, row.size, 1)
            for (x in row.indices) {
                val light = 90 + 150 * x / maxOf(1, shaded.width - 1)
                val gray = PhotoToneCurve.gray(row[x], PhotoImageTreatment.ORIGINAL) * light / 255
                row[x] = 0xff000000.toInt() or (gray shl 16) or (gray shl 8) or gray
            }
            shaded.setPixels(row, 0, row.size, 0, y, row.size, 1)
        }
        val before = IntArray(shaded.width * shaded.height)
        shaded.getPixels(before, 0, shaded.width, 0, 0, shaded.width, shaded.height)
        fun save(bitmap: Bitmap, name: String): String {
            val file = File(context.filesDir, name)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        val inputHash = save(shaded, "photo-v17-shadow.png")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val observation = JSONObject().put("synthetic", true).put("accuracyBenchmark", false)
            .put("classification", "controlled-transformed-input-regression")
            .put("baselineOcrExecuted", false).put("expectedExact", "46")
            .put("versionName", packageInfo.versionName).put("versionCode", packageInfo.versionCode)
            .put("input", "photo-v17-shadow.png").put("inputSha256", inputHash)
            .put("width", shaded.width).put("height", shaded.height)
            .put("method", "Existing addition print multiplied by a deterministic horizontal lighting ramp90..240; explicit local shadow reduction, then one SDK read of those exact transformed pixels.")
        val receipt = File(context.filesDir, "photo-v17-shadow-observation.json")
        var treated: Bitmap? = null
        var readComplete = false
        try {
            val prepared = PhotoImageAssist.apply(shaded, PhotoImageTreatment.SHADOW_REDUCTION)
            treated = prepared
            assertNotSame(shaded, prepared)
            assertEquals(shaded.width, prepared.width)
            assertEquals(shaded.height, prepared.height)
            assertFalse(shaded.isRecycled)
            val after = IntArray(before.size)
            shaded.getPixels(after, 0, shaded.width, 0, 0, shaded.width, shaded.height)
            observation.put("inputPixelsUnchanged", before.contentEquals(after))
            assertArrayEquals(before, after)
            prepared.getPixels(after, 0, prepared.width, 0, 0, prepared.width, prepared.height)
            observation.put("transformedPixelsDiffer", !before.contentEquals(after))
                .put("transformed", "photo-v17-shadow-reduced.png")
                .put("transformedSha256", save(prepared, "photo-v17-shadow-reduced.png"))
            receipt.writeText(observation.toString(2))
            assertFalse("The explicit operation must actually change this shaded fixture", before.contentEquals(after))
            val started = System.nanoTime()
            val draft = PhotoMathReader.read(prepared)
            readComplete = true
            val result = Calculator().evaluate(draft.expressionForReview)
            val number = ((result as? CalcResult.Ok)?.value as? RealValue)?.num
            observation.put("rawText", draft.rawText).put("layout", draft.layout.toString())
                .put("transcription", draft.expressionForReview).put("result", result.toString())
                .put("matchesIndependentOracle", number == Num.of(46))
                .put("elapsedMillis", (System.nanoTime() - started) / 1_000_000)
            receipt.writeText(observation.toString(2))
            assertEquals("Same displayed transformed pixels must be read by the native SDK", Num.of(46), number)
            try {
                PhotoImageAssist.apply(shaded, PhotoImageTreatment.SHADOW_REDUCTION, shouldStop = { true })
                fail("Cancellation must refuse the operation")
            } catch (_: CancellationException) { assertFalse(shaded.isRecycled) }
        } finally {
            shaded.recycle()
            if (readComplete) treated?.recycle()
        }
    }
}
