package com.vaibhav.calc.android

import kotlin.math.max
import kotlin.math.min

/** An explicit review option, never an automatic replacement of OCR text. */
data class PhotoMathCorrection(val expression: String, val explanation: String)

internal object PhotoMathCorrections {
    const val MAX_CORRECTIONS = 4

    fun suggest(tokens: List<MathToken>, radicalEvidence: Set<Int>): List<PhotoMathCorrection> {
        if (tokens.isEmpty() || tokens.size > MathLayout.MAX_TOKENS ||
            tokens.sumOf { it.text.length.toLong() } > MathLayout.MAX_CHARACTERS) return emptyList()
        val suggestions = mutableListOf<PhotoMathCorrection>()
        fun offer(changed: List<MathToken>, explanation: String) {
            if (suggestions.size >= MAX_CORRECTIONS) return
            val draft = MathLayout.read(changed) as? MathLayoutResult.Draft ?: return
            if (draft.expression.length <= MathLayout.MAX_CHARACTERS && suggestions.none { it.expression == draft.expression })
                suggestions += PhotoMathCorrection(draft.expression, explanation)
        }
        for ((index, token) in tokens.withIndex()) {
            if (index in radicalEvidence && token.text in setOf("V", "v")) {
                val replacement = token.copy(text = "√", uncertain = true)
                offer(tokens.toMutableList().also { it[index] = replacement },
                    "OCR read '${token.text}' at symbol ${index + 1}, but its strokes may be a square root. Choose this only if the image shows √, and check its scope.")
            }
        }
        for ((index, token) in tokens.withIndex()) {
            // A short, separate OCR line can duplicate the top of a printed digit. Do not
            // erase it: offer a clearly described alternative while retaining the raw reading.
            if (token.text.length != 1 || !token.text.single().isLetter() ||
                token.text.lowercase() in setOf("x", "y", "z", "e", "i", "π", "a", "b", "c", "d")) continue
            val b = token.bounds
            val other = tokens.withIndex().firstOrNull { (otherIndex, candidate) ->
                val q = candidate.bounds
                otherIndex != index && candidate.text.matches(Regex("[0-9]")) &&
                    b.width > 0f && b.height > 0f && q.width > 0f && q.height > 0f &&
                    b.height <= q.height * .35f && b.width <= q.width * 1.3f &&
                    b.top <= q.top && b.bottom <= q.top + q.height * .35f &&
                    min(b.right, q.right) - max(b.left, q.left) >= min(b.width, q.width) * .8f &&
                    min(b.bottom, q.bottom) - max(b.top, q.top) >= b.height * .35f
            } ?: continue
            offer(tokens.filterIndexed { i, _ -> i != index },
                "OCR read a small '${token.text}' at symbol ${index + 1} overlapping '${other.value.text}'. Omit it only if it is part of the same printed glyph, not a separate symbol.")
        }
        return suggestions
    }

    /** Coarse pixel evidence only. A symmetric letter V must not become a root. */
    fun hasRadicalProfile(width: Int, height: Int, pixels: IntArray): Boolean {
        if (width !in 8..512 || height !in 12..512 || pixels.size != width * height) return false
        fun dark(pixel: Int): Boolean {
            val alpha = (pixel ushr 24) and 255
            val gray = ((pixel shr 16) and 255) * 3 + ((pixel shr 8) and 255) * 6 + (pixel and 255)
            return (gray * alpha + 2550 * (255 - alpha)) / 255 < 1100
        }
        var left = width; var right = -1; var top = height; var bottom = -1; var count = 0
        for (y in 0 until height) for (x in 0 until width) if (dark(pixels[y * width + x])) {
            left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y); count++
        }
        val w = right - left + 1; val h = bottom - top + 1
        if (count < 20 || w < 8 || h < 12 || w < h * .25f || w > h * 1.2f) return false
        var upperLeft = width; var upperRight = -1; var middleLeft = width; var lowerRight = -1
        for (y in top..bottom) for (x in left..right) if (dark(pixels[y * width + x])) {
            val relative = (y - top).toFloat() / h
            if (relative < .25f) { upperLeft = min(upperLeft, x); upperRight = max(upperRight, x) }
            if (relative in .35f.. .75f) middleLeft = min(middleLeft, x)
            if (relative >= .75f) lowerRight = max(lowerRight, x)
        }
        return upperLeft >= left + w * .55f && upperRight >= left + w * .8f &&
            middleLeft <= left + w * .25f && lowerRight in left..(left + w * .8f).toInt()
    }
}
