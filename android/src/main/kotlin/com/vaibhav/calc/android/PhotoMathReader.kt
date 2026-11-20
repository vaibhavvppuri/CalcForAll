package com.vaibhav.calc.android

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PhotoMathDraft(val rawText: String, val layout: MathLayoutResult, val truncated: Boolean = false,
                          val corrections: List<PhotoMathCorrection> = emptyList(),
                          val incomplete: Boolean = false) {
    /** Incomplete page prefixes must not be offered again through raw-text/line buttons. */
    val completeRawText: String get() = if (truncated || incomplete) "" else rawText
    /** The same reviewable transcription is used by the screen and device recognition checks. */
    val expressionForReview: String get() = if (truncated || incomplete) "" else when (layout) {
        is MathLayoutResult.Draft -> layout.expression
        // Flattened raw OCR can calculate while reversing the meaning of a rejected
        // fraction or multiline layout. Only an explicit review action may use it.
        is MathLayoutResult.Rejected -> ""
    }
}

/** Relative clockwise rotation of the exact bitmap passed to orientationAlternatives. */
data class PhotoOrientationOption(val turns: Int, val draft: PhotoMathDraft)

/** Internal observation of the same production batch, used for native ownership regressions. */
internal data class PhotoBatchObserver(
    val onNativeStarted: (Int) -> Unit = {},
    /** The Task completed and released its lease; the batch may still retain the client. */
    val onNativeCompleted: (Int) -> Unit = {},
    val onClientCreated: () -> Unit = {},
    /** The recognizer's actual close call returned successfully. */
    val onClientClosed: () -> Unit = {},
)

/** Uses the bundled on-device recognizer, then keeps the geometry for mathematical layout. */
object PhotoMathReader {
    private val nativeGate = NativeRecognitionGate()
    private val completionExecutor = Executor { it.run() }

    /** Three bounded trials, in geometric order. Never evaluate or select a numerical winner. */
    suspend fun orientationAlternatives(image: Bitmap, onTurnStarted: (Int) -> Unit = {},
                                       onOptionsReady: (List<PhotoOrientationOption>) -> Unit = {}): List<PhotoOrientationOption> =
        orientationBatch(image, onTurnStarted, onOptionsReady, null)

    internal suspend fun orientationAlternativesObserved(image: Bitmap, observer: PhotoBatchObserver): List<PhotoOrientationOption> =
        orientationBatch(image, {}, {}, observer)

    private suspend fun orientationBatch(image: Bitmap, onTurnStarted: (Int) -> Unit,
                                         onOptionsReady: (List<PhotoOrientationOption>) -> Unit,
                                         observer: PhotoBatchObserver?): List<PhotoOrientationOption> {
        requireBoundedImage(image)
        val batchTiming = PhotoReadTiming(image.width, image.height)
        val session = NativeResourceSession(
            create = {
                batchTiming.stage("batch-client-create")
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also {
                    observe(observer?.onClientCreated, batchTiming)
                }
            },
            dispose = { recognizer: TextRecognizer ->
                batchTiming.stage("batch-close-start")
                try {
                    recognizer.close()
                    batchTiming.stage("batch-close-complete")
                    observe(observer?.onClientClosed, batchTiming)
                } catch (problem: Throwable) {
                    batchTiming.stage("batch-close-failed")
                    throw problem
                }
            },
        )
        var failure: Throwable? = null
        try {
            return PhotoOrientationSearch.collect(onTurnStarted, onOptionsReady) { turns ->
                val timing = PhotoReadTiming(image.width, image.height, turns)
                timing.stage("prepare-wait")
                val rotated = PhotoStaging.imageOperation {
                    timing.stage("prepare-start")
                    withContext(Dispatchers.Default) {
                        PhotoImageAssist.apply(image, quarterTurns = turns)
                    }
                }
                timing.stage("prepare-complete")
                var readFinished = false
                try {
                    val draft = readInternal(rotated, observer?.let { { it.onNativeStarted(turns) } },
                        observer?.let { { it.onNativeCompleted(turns) } }, session)
                    readFinished = true
                    timing.stage("orientation-read-complete")
                    draft
                } finally {
                    // Cancellation returns before the non-cancellable SDK job completes.
                    // Let it retain/release its input; only completed reads may be recycled.
                    if (readFinished && rotated !== image) {
                        timing.stage("recycle-start")
                        rotated.recycle()
                        timing.stage("recycle-complete")
                    }
                }
            }
        } catch (problem: Throwable) {
            failure = problem
            throw problem
        } finally {
            // If cancellation returned before SDK completion, the live Task lease
            // keeps the client alive and performs this close later in onComplete.
            try { session.closeOwner() } catch (cleanup: Throwable) {
                val original = failure
                if (original != null) original.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    /** Native observers run on worker threads and cannot change completion ownership.
     * Started is delivered after SDK launch and before installing completion delivery. */
    suspend fun read(image: Bitmap, onNativeStarted: (() -> Unit)? = null,
                     onNativeCompleted: (() -> Unit)? = null): PhotoMathDraft =
        readInternal(image, onNativeStarted, onNativeCompleted, null)

    private fun observe(callback: (() -> Unit)?, timing: PhotoReadTiming) {
        // Observers cannot change native ownership or turn cancellation into cleanup.
        runCatching { callback?.invoke() }.onFailure { timing.stage("observer-failed") }
    }

    private suspend fun readInternal(image: Bitmap, onNativeStarted: (() -> Unit)?,
                                     onNativeCompleted: (() -> Unit)?,
                                     session: NativeResourceSession<TextRecognizer>?): PhotoMathDraft {
        requireBoundedImage(image)
        val timing = PhotoReadTiming(image.width, image.height)
        try {
            timing.stage("gate-wait")
            val result = withContext(Dispatchers.Default) {
                nativeGate.awaitCompletion<Text> { complete ->
                    timing.stage(if (session == null) "native-create" else "batch-client-acquire")
                    val closed = AtomicBoolean(false)
                    val lease = session?.acquire()
                    val recognizer = lease?.resource ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    fun finish(outcome: Result<Text>, nativeCompleted: Boolean = false) {
                        if (!closed.compareAndSet(false, true)) {
                            return
                        }
                        timing.stage(if (lease == null) "close-start" else "lease-release-start")
                        val closeFailure = runCatching {
                            if (lease == null) recognizer.close() else lease.release()
                        }.exceptionOrNull()
                        timing.stage(when {
                            closeFailure != null -> if (lease == null) "close-failed" else "lease-release-failed"
                            lease == null -> "close-complete"
                            else -> "lease-release-complete"
                        })
                        if (nativeCompleted) observe(onNativeCompleted, timing)
                        complete(if (closeFailure != null) Result.failure(closeFailure) else outcome)
                    }
                    try {
                        val task = recognizer.process(InputImage.fromBitmap(image, 0))
                        timing.stage("native-start")
                        // No suspension or cancellation check may separate this
                        // observational callback from attaching the task listener.
                        // Even a cancelled owner still needs its native cleanup.
                        observe(onNativeStarted, timing)
                        task.addOnCompleteListener(completionExecutor) { completed ->
                            timing.stage("native-complete")
                            finish(runCatching {
                                if (!completed.isSuccessful) throw completed.exception ?: IllegalStateException("Recognition did not complete.")
                                completed.result
                            }, nativeCompleted = true)
                        }
                    } catch (problem: Throwable) { finish(Result.failure(problem)) }
                }
            }
            timing.stage("gate-return")
            val draft = withContext(Dispatchers.Default) {
                timing.stage("layout-start")
                try {
                    if (result.text.length > CalcSession.MAX_ENTRY_CHARS) return@withContext PhotoMathDraft(
                        result.text.take(CalcSession.MAX_ENTRY_CHARS),
                        MathLayoutResult.Rejected("This page contains too much text. Crop one expression."), truncated = true)
                    fun box(b: android.graphics.Rect?) = b?.let {
                        MathBox(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
                    }
                    val adapted = PhotoOcrTokenAdapter.collect(result.textBlocks.asSequence().flatMap { it.lines.asSequence() }
                        .flatMap { it.elements.asSequence() }.map { element ->
                            PhotoOcrElement(element.text, box(element.boundingBox),
                                element.symbols.asSequence().take(MathLayout.MAX_TOKENS + 1)
                                    .map { PhotoOcrSymbol(it.text, box(it.boundingBox)) }.toList())
                        }, expectedText = result.text)
                    if (adapted.budgetExceeded) return@withContext PhotoMathDraft(result.text,
                        MathLayoutResult.Rejected("This image has too many symbols. Crop one expression before interpreting its layout."),
                        incomplete = true)
                    if (adapted.incompleteText) return@withContext PhotoMathDraft(result.text,
                        MathLayoutResult.Rejected("Some recognized text has no matching symbols. Crop a clearer expression or enter it manually."),
                        incomplete = true)
                    val tokens = adapted.tokens
                    val full = if (adapted.missingGeometry) tokens else tokens + missingRules(image, tokens)
                    var layout = if (adapted.missingGeometry)
                        MathLayoutResult.Rejected("Some symbol positions are missing. Review the raw text or crop the expression.")
                        else MathLayout.read(full)
                    if (adapted.usedElementFallback && layout is MathLayoutResult.Draft) layout = layout.copy(
                        warnings = layout.warnings + "Some OCR text was kept as a whole block because individual symbol positions were incomplete. Check its layout.",
                        ambiguous = true)
                    val corrections = if (adapted.missingGeometry) emptyList() else suggestCorrections(image, full)
                    PhotoMathDraft(result.text, layout, corrections = corrections)
                } finally { timing.stage("layout-complete") }
            }
            timing.stage("read-return")
            return draft
        } catch (cancelled: CancellationException) {
            timing.stage("read-cancelled")
            throw cancelled
        } catch (problem: Exception) {
            timing.stage("read-failed")
            throw problem
        }
    }

    /** These candidates remain separate from the default transcription until the user selects one. */
    internal fun suggestCorrections(image: Bitmap, tokens: List<MathToken>): List<PhotoMathCorrection> {
        requireBoundedImage(image)
        if (tokens.size > MathLayout.MAX_TOKENS) return emptyList()
        val roots = mutableSetOf<Int>()
        for ((index, token) in tokens.withIndex().filter { it.value.text in setOf("V", "v") }.take(8)) {
            val b = token.bounds
            if (listOf(b.left, b.top, b.right, b.bottom).any { !it.isFinite() }) continue
            val left = b.left.toInt().coerceIn(0, image.width)
            val top = b.top.toInt().coerceIn(0, image.height)
            val right = b.right.toInt().coerceIn(0, image.width)
            val bottom = b.bottom.toInt().coerceIn(0, image.height)
            val width = right - left; val height = bottom - top
            if (width !in 8..512 || height !in 12..512) continue
            val pixels = IntArray(width * height)
            image.getPixels(pixels, 0, width, left, top, width, height)
            if (PhotoMathCorrections.hasRadicalProfile(width, height, pixels)) roots += index
        }
        return PhotoMathCorrections.suggest(tokens, roots)
    }

    /** Heuristic recovery of a printed rule omitted by OCR. Glyphs on both sides narrow
     * the candidates, but page rules can still be mistaken for fraction bars: mark uncertain. */
    internal fun missingRules(image: Bitmap, tokens: List<MathToken>): List<MathToken> {
        requireBoundedImage(image)
        if (tokens.isEmpty() || tokens.size > MathLayout.MAX_TOKENS) return emptyList()
        val heights = tokens.map { it.bounds.height }.filter { it >= 4f }.sorted()
        val height = heights.getOrNull(heights.size / 2) ?: return emptyList()
        val minRun = max(8, (height * .65f).toInt())
        val row = IntArray(image.width)
        val rules = mutableListOf<MathBox>()
        // An omitted rule just outside a lone numerator/denominator must still be
        // retained: ignoring it could turn an incomplete fraction into a number.
        val margin = height * .75f
        val top = max(0, (tokens.minOf { it.bounds.top } - margin).toInt())
        val bottom = min(image.height - 1, (tokens.maxOf { it.bounds.bottom } + margin).toInt())
        for (y in top..bottom) {
            image.getPixels(row, 0, image.width, 0, y, image.width, 1)
            var start = -1
            for (x in 0..image.width) {
                val color = if (x < image.width) row[x] else -1
                val alpha = (color ushr 24) and 255
                val gray = ((color shr 16) and 255) * 3 + ((color shr 8) and 255) * 6 + (color and 255)
                val dark = x < image.width && (gray * alpha + 2550 * (255 - alpha)) / 255 < 1100
                if (dark && start < 0) start = x
                if (!dark && start >= 0) {
                    if (x - start >= minRun) {
                        val existing = rules.indexOfLast { it.bottom >= y - 1 && abs(it.left - start) <= 2 && abs(it.right - x) <= 2 }
                        if (existing >= 0) rules[existing] = rules[existing].copy(bottom = y + 1f)
                        else {
                            require(rules.size < 512) { "This image has too many lines or marks. Crop one expression and try again." }
                            rules += MathBox(start.toFloat(), y.toFloat(), x.toFloat(), y + 1f)
                        }
                    }
                    start = -1
                }
            }
        }
        return rules.filter { b ->
            val root = tokens.firstOrNull { t -> val q = t.bounds
                t.text == "√" && abs(b.top - q.top) <= height * .2f && abs(b.left - q.right) <= height * .25f &&
                    b.right > q.right + height * .3f }
            val clear = tokens.none { t -> val q = t.bounds
                t !== root &&
                    q.left < b.right && q.right > b.left && q.top <= b.bottom && q.bottom >= b.top }
            val below = tokens.any { t -> val q = t.bounds
                    q.centerX in b.left..b.right && q.top > b.bottom && q.top - b.bottom < height * 2.5f }
            val above = tokens.any { t -> val q = t.bounds
                q.centerX in b.left..b.right && q.bottom < b.top && b.top - q.bottom < height * 2.5f }
            b.height <= max(2f, height * .18f) && clear &&
                (if (root != null) below else above || below)
        }.take(MathLayout.MAX_TOKENS - tokens.size + 1).map { MathToken("-", it, uncertain = true) }
    }

    private fun requireBoundedImage(image: Bitmap) {
        require(!image.isRecycled && image.width in 1..PhotoCropGeometry.MAX_EDGE && image.height in 1..PhotoCropGeometry.MAX_EDGE) {
            "Read a decoded image or crop with edges no larger than ${PhotoCropGeometry.MAX_EDGE} pixels."
        }
    }
}
