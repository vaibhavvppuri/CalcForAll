package com.vaibhav.calc.android

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PhotoMathDraft(val rawText: String, val layout: MathLayoutResult, val truncated: Boolean = false,
                          val corrections: List<PhotoMathCorrection> = emptyList()) {
    /** Incomplete page prefixes must not be offered again through raw-text/line buttons. */
    val completeRawText: String get() = if (truncated) "" else rawText
    /** The same reviewable transcription is used by the screen and device recognition checks. */
    val expressionForReview: String get() = if (truncated) "" else when (layout) {
        is MathLayoutResult.Draft -> layout.expression
        is MathLayoutResult.Rejected -> rawText.takeIf { it.length <= RecognitionCalculator.MAX_CHARS } ?: ""
    }
}

/** Uses the bundled on-device recognizer, then keeps the geometry for mathematical layout. */
object PhotoMathReader {
    suspend fun read(image: Bitmap): PhotoMathDraft {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = suspendCancellableCoroutine<Text> { continuation ->
            // Close after the underlying task finishes, even if its screen was cancelled.
            try {
                recognizer.process(InputImage.fromBitmap(image, 0))
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                    .addOnCompleteListener { recognizer.close() }
            } catch (problem: Exception) { recognizer.close(); if (continuation.isActive) continuation.resumeWithException(problem) }
        }
        return withContext(Dispatchers.Default) {
            var missingGeometry = false
            val tokens = result.textBlocks.flatMap { it.lines }.flatMap { it.elements }.flatMap { element ->
                val symbols = element.symbols.takeIf { all -> all.isNotEmpty() && all.all { it.boundingBox != null } }?.map { symbol -> symbol.boundingBox!!.let { b ->
                    MathToken(symbol.text, MathBox(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()))
                } }
                if (symbols != null) symbols else listOfNotNull(element.boundingBox?.let { b ->
                    MathToken(element.text, MathBox(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()))
                }.also { if (it == null && element.text.isNotBlank()) missingGeometry = true })
            }
            val full = if (tokens.size <= MathLayout.MAX_TOKENS) tokens + missingRules(image, tokens) else tokens
            val layout = if (missingGeometry) MathLayoutResult.Rejected("Some symbol positions are missing. Review the raw text or crop the expression.") else MathLayout.read(full)
            val truncated = result.text.length > CalcSession.MAX_ENTRY_CHARS
            val corrections = if (truncated || missingGeometry) emptyList() else suggestCorrections(image, full)
            PhotoMathDraft(result.text.take(CalcSession.MAX_ENTRY_CHARS), layout, truncated, corrections)
        }
    }

    /** These candidates remain separate from the default transcription until the user selects one. */
    internal fun suggestCorrections(image: Bitmap, tokens: List<MathToken>): List<PhotoMathCorrection> {
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
        if (tokens.isEmpty() || tokens.size > MathLayout.MAX_TOKENS) return emptyList()
        val heights = tokens.map { it.bounds.height }.filter { it >= 4f }.sorted()
        val height = heights.getOrNull(heights.size / 2) ?: return emptyList()
        val minRun = max(8, (height * .65f).toInt())
        val row = IntArray(image.width)
        val rules = mutableListOf<MathBox>()
        val top = max(0, (tokens.minOf { it.bounds.top }).toInt())
        val bottom = min(image.height - 1, (tokens.maxOf { it.bounds.bottom }).toInt())
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
                    q.left < b.right && q.right > b.left && q.top <= b.bottom && q.bottom >= b.top } &&
                tokens.any { t -> val q = t.bounds
                    q.centerX in b.left..b.right && q.top > b.bottom && q.top - b.bottom < height * 2.5f }
            val above = tokens.any { t -> val q = t.bounds
                q.centerX in b.left..b.right && q.bottom < b.top && b.top - q.bottom < height * 2.5f }
            b.height <= max(2f, height * .18f) && clear && (root != null || above)
        }.take(MathLayout.MAX_TOKENS - tokens.size + 1).map { MathToken("-", it, uncertain = true) }
    }
}
