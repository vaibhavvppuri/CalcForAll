package com.vaibhav.calc.android

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.route.ExpressionRouter
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigInteger

/** Exploratory recognition observations, not eight passing accuracy assertions. The two basic
 * arithmetic fixtures are regression gates; every other actual result is recorded for review. */
@RunWith(AndroidJUnit4::class)
class PhotoCorpusDeviceTest {
    private fun reference(text: String): Num {
        val parts = text.split('/')
        return if (parts.size == 2) Num.of(BigInteger(parts[0]), BigInteger(parts[1])) else Num.ofDecimal(text)
    }
    private fun matches(expected: JSONObject, outcome: Outcome): Boolean = when (expected.getString("expectedKind")) {
        "value" -> (outcome as? Outcome.Scalar)?.value.let { it is RealValue && it.num == reference(expected.getString("expectedExact")) }
        "roots" -> {
            val solved = outcome as? Outcome.Roots
            val roots = solved?.roots?.map { (it as? RealValue)?.num }
            val wanted = expected.getJSONArray("expectedRoots").let { list -> (0 until list.length()).map { reference(list.getString(it)) } }
            val variables = expected.getJSONArray("expectedVariables")
            solved != null && roots != null && variables.length() == 1 && solved.unknown == variables.getString(0) && roots.size == wanted.size && roots.toSet() == wanted.toSet()
        }
        "system" -> {
            val solved = outcome as? Outcome.SystemSolution
            val wanted = expected.getJSONObject("expectedSolution")
            solved != null && solved.unknowns.size == wanted.length() && solved.values.size == solved.unknowns.size && solved.unknowns.zip(solved.values).all { (name, value) -> wanted.has(name) && value == reference(wanted.getString(name)) }
        }
        else -> false
    }

    @Test fun syntheticPhotoCorpusRecordsActualTranscriptions() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val manifest = assets.open("photo-corpus/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        val cases = manifest.getJSONArray("cases")
        assertEquals(8, cases.length())
        val records = JSONArray()
        val mandatoryFailures = mutableListOf<String>()
        for (index in 0 until cases.length()) {
            val fixture = cases.getJSONObject(index)
            val started = System.nanoTime()
            val correctionRecords = JSONArray()
            val record = JSONObject().put("id", fixture.getString("id")).put("classification", "PROBE")
                .put("automaticMatch", false).put("assistedMatch", false).put("corrections", correctionRecords)
            var image: android.graphics.Bitmap? = null
            var correct = false
            try {
                image = assets.open("photo-corpus/${fixture.getString("image")}").use { BitmapFactory.decodeStream(it) }
                checkNotNull(image) { "Fixture did not decode" }
                val recognized = PhotoMathReader.read(image!!)
                val transcription = recognized.expressionForReview
                val preview = RecognitionCalculator.preview(transcription, Settings(), Environment())
                val result = if (transcription.isBlank()) null else ExpressionRouter(Settings(), Environment()).route(transcription)
                correct = preview is RecognitionPreview.Answer && result is RouteResult.Ok && matches(fixture, result.outcome)
                record.put("rawText", recognized.rawText).put("layout", recognized.layout.toString())
                    .put("preview", preview.toString())
                    .put("transcription", transcription).put("outcome", result?.toString() ?: "No expression")
                    .put("matchesIndependentOracle", correct)
                    .put("automaticMatch", correct)
                    .put("status", if (correct) "matched" else "needs correction or unsupported")
                // Evaluate only alternatives actually offered by production recognition. The
                // manifest is an independent oracle, never replacement recognition input.
                for (correction in recognized.corrections) {
                    val option = JSONObject().put("expression", correction.expression)
                        .put("explanation", correction.explanation).put("requiresExplicitSelection", true)
                    try {
                        val optionPreview = RecognitionCalculator.preview(correction.expression, Settings(), Environment())
                        val optionResult = ExpressionRouter(Settings(), Environment()).route(correction.expression)
                        val optionMatches = optionPreview is RecognitionPreview.Answer &&
                            optionResult is RouteResult.Ok && matches(fixture, optionResult.outcome)
                        option.put("preview", optionPreview.toString()).put("outcome", optionResult.toString())
                            .put("matchesIndependentOracle", optionMatches)
                            .put("status", if (optionMatches) "matched after offered correction" else "needs correction or unsupported")
                        if (optionMatches) record.put("assistedMatch", true)
                    } catch (problem: Exception) {
                        option.put("status", "error").put("error", problem.toString()).put("matchesIndependentOracle", false)
                    }
                    correctionRecords.put(option)
                }
            } catch (problem: Exception) {
                record.put("status", "error").put("error", problem.toString()).put("matchesIndependentOracle", false)
            } finally { image?.recycle() }
            if (fixture.getString("id") in listOf("addition", "parentheses") && !correct)
                mandatoryFailures += "${fixture.getString("id")}: $record"
            record.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000)
            records.put(record)
        }
        val report = JSONObject().put("synthetic", true).put("accuracyBenchmark", false)
            .put("apiLevel", android.os.Build.VERSION.SDK_INT).put("probes", records)
            .put("automaticMatches", (0 until records.length()).count { records.getJSONObject(it).optBoolean("automaticMatch") })
            .put("assistedOnlyMatches", (0 until records.length()).count {
                val record = records.getJSONObject(it)
                !record.optBoolean("automaticMatch") && record.optBoolean("assistedMatch")
            })
        val output = File(instrumentation.targetContext.cacheDir, "photo-corpus-result.json")
        output.writeText(report.toString(2))
        android.util.Log.i("CalcPhotoCorpus", report.toString())
        assertTrue(mandatoryFailures.joinToString("\n"), mandatoryFailures.isEmpty())
    }
}
