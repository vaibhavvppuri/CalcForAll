// =============================================================================
// THROWAWAY SPIKE (M0.4) — does ML Kit Digital Ink usefully read math?
//
// This is NOT part of the build and NOT a Gradle module (kept out of
// settings.gradle.kts on purpose — Article 2: spikes stay isolated). It is
// device-ready reference code. To run it, drop it into a fresh Android app:
//
//   // build.gradle.kts
//   implementation("com.google.mlkit:digital-ink-recognition:18.1.0")
//   // AndroidManifest.xml
//   <uses-permission android:name="android.permission.INTERNET"/>  // model download
//
// Then hand-write each test input, tap RECOGNIZE, and read Logcat (tag "InkSpike").
// See ../DECISIONS.md for the predicted verdict and how this confirms it.
// =============================================================================
package spike.mlkit

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.Ink

/**
 * Canvas → strokes → ML Kit Digital Ink Recognition → printed candidates.
 *
 * Note there is NO math/formula recognizer to request — `fromLanguageTag("en")`
 * is a *text* recognizer, and there is no `MATH`/`FORMULA` identifier in the
 * SDK. The whole point of the spike is to see what a text recognizer does with
 * `x²`, `√9`, `∫x dx`, a stacked `1/2`, etc. (Predicted: flat strings, no
 * structure — see DECISIONS.md.)
 */
class InkMathSpikeActivity : Activity() {

    private val strokeBuilders = mutableListOf<Ink.Stroke.Builder>()
    private var current: Ink.Stroke.Builder? = null
    private val inkBuilder = Ink.builder()

    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pad = DrawingView(this)
        output = TextView(this).apply { textSize = 16f; setPadding(24, 24, 24, 24) }

        val recognize = Button(this).apply {
            text = "RECOGNIZE"
            setOnClickListener { runRecognition() }
        }
        val clear = Button(this).apply {
            text = "CLEAR"
            setOnClickListener { recreate() }
        }

        root.addView(pad, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f))
        root.addView(recognize)
        root.addView(clear)
        root.addView(output)
        setContentView(root)
    }

    private fun addPoint(x: Float, y: Float, t: Long, down: Boolean, up: Boolean) {
        if (down) current = Ink.Stroke.builder()
        current?.addPoint(Ink.Point.create(x, y, t))
        if (up) current?.let { inkBuilder.addStroke(it.build()); strokeBuilders.add(it) }
    }

    private fun runRecognition() {
        val ink = inkBuilder.build()
        val modelId = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en") ?: run {
            output.text = "no recognizer for tag"; return
        }
        val model = DigitalInkRecognitionModel.builder(modelId).build()
        val manager = RemoteModelManager.getInstance()

        output.text = "downloading model…"
        manager.download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener {
                val recognizer = DigitalInkRecognition.getClient(
                    com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions.builder(model).build()
                )
                recognizer.recognize(ink)
                    .addOnSuccessListener { result ->
                        // NOTE: each candidate exposes ONLY .text (String) and .score.
                        // There is no field for superscripts, fraction bars, radicals
                        // or any 2D structure. That absence is the finding.
                        val lines = result.candidates.joinToString("\n") { c ->
                            "text=\"${c.text}\"  score=${c.score}"
                        }
                        Log.i("InkSpike", "candidates:\n$lines")
                        output.text = "candidates:\n$lines"
                    }
                    .addOnFailureListener { e -> output.text = "recognize failed: ${e.message}" }
            }
            .addOnFailureListener { e -> output.text = "download failed: ${e.message}" }
    }

    /** A trivial finger-paint surface that also feeds points into the Ink builder. */
    private inner class DrawingView(a: Activity) : View(a) {
        private val paint = Paint().apply {
            color = Color.BLACK; strokeWidth = 6f; style = Paint.Style.STROKE; isAntiAlias = true
        }
        private val path = android.graphics.Path()

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.WHITE)
            canvas.drawPath(path, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val t = event.eventTime
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    path.moveTo(event.x, event.y); addPoint(event.x, event.y, t, down = true, up = false)
                }
                MotionEvent.ACTION_MOVE -> {
                    path.lineTo(event.x, event.y); addPoint(event.x, event.y, t, down = false, up = false)
                }
                MotionEvent.ACTION_UP -> {
                    addPoint(event.x, event.y, t, down = false, up = true)
                }
            }
            invalidate()
            return true
        }
    }
}
