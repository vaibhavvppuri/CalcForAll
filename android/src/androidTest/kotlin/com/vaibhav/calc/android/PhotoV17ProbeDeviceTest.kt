package com.vaibhav.calc.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Baseline-compatible probe: only the existing PhotoMathReader.read ABI is used.
 * The same new test APK can record this controlled fixture against the preserved old APK.
 * An old-APK failure is historical comparison evidence, never a passing release gate. */
@RunWith(AndroidJUnit4::class)
class PhotoV17ProbeDeviceTest {
    @Test fun visibleGrayFractionRulesKeepTheCompleteFiveSixthsExpression() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = instrumentation.context.assets.open("photo-corpus/07-stacked-fractions.png")
            .use { BitmapFactory.decodeStream(it) }!!
        val image = original.copy(Bitmap.Config.ARGB_8888, true)
        original.recycle()
        // Existing independently authored printed 1/2 + 1/3 fixture, changing only
        // its two five-pixel bars. Gray202 is plainly visible on the white page but
        // lies above the old fixed rule cutoff110; no digits/operators are invented.
        val canvas = Canvas(image)
        val paint = Paint().apply { color = Color.rgb(202, 202, 202); isAntiAlias = false }
        canvas.drawRect(291f, 278f, 490f, 283f, paint)
        canvas.drawRect(711f, 278f, 910f, 283f, paint)
        val fixture = File(context.filesDir, "photo-v17-faint-fraction.png")
        fixture.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val observation = JSONObject().put("synthetic", true).put("accuracyBenchmark", false)
            .put("fixture", fixture.name).put("fixtureSha256", MessageDigest.getInstance("SHA-256")
                .digest(fixture.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) })
            .put("width", image.width).put("height", image.height)
            .put("expectedExpressionMeaning", "1/2 + 1/3").put("expectedExact", "5/6")
            .put("versionName", packageInfo.versionName).put("versionCode", packageInfo.versionCode)
            .put("method", "One normal offline read; same printed source and gray-bar pixels on both APKs. No raw-text fallback or answer-based candidate selection.")
        var complete = false
        val started = System.nanoTime()
        try {
            val draft = PhotoMathReader.read(image)
            complete = true
            val expression = draft.expressionForReview
            val result = Calculator().evaluate(expression)
            val number = ((result as? CalcResult.Ok)?.value as? RealValue)?.num
            observation.put("rawText", draft.rawText).put("layout", draft.layout.toString())
                .put("transcription", expression).put("result", result.toString())
                .put("matchesIndependentOracle", number == Num.of(5, 6))
                .put("elapsedMillis", (System.nanoTime() - started) / 1_000_000)
            File(context.filesDir, "photo-v17-faint-fraction-observation.json").writeText(observation.toString(2))
            assertTrue("Complete visible gray rules must not disappear: $observation", expression.isNotBlank())
            assertEquals("Independent arithmetic oracle, after actual SDK recognition", Num.of(5, 6), number)
        } finally {
            // An asynchronously cancelled SDK owner can return before pixel use ends.
            if (complete) image.recycle()
        }
    }
}
