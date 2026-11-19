package com.vaibhav.calc.android

import kotlin.math.abs

internal data class PhotoOcrSymbol(val text: String, val bounds: MathBox?)
internal data class PhotoOcrElement(val text: String, val bounds: MathBox?, val symbols: List<PhotoOcrSymbol>)
internal data class PhotoOcrTokens(
    val tokens: List<MathToken>, val missingGeometry: Boolean = false,
    val usedElementFallback: Boolean = false, val budgetExceeded: Boolean = false,
)

/** A partial SDK symbol list must never silently replace the complete OCR element text. */
internal object PhotoOcrTokenAdapter {
    fun collect(elements: Sequence<PhotoOcrElement>): PhotoOcrTokens {
        val tokens = mutableListOf<MathToken>()
        var missing = false; var fallback = false; var characters = 0; var count = 0
        fun usable(box: MathBox?) = box != null &&
            listOf(box.left, box.top, box.right, box.bottom).all { it.isFinite() && abs(it) <= 1_000_000f } &&
            box.left < box.right && box.top < box.bottom
        fun compact(text: String) = text.filterNot { it.isWhitespace() }
        for (element in elements) {
            if (++count > MathLayout.MAX_TOKENS * 4 ||
                element.text.length > MathLayout.MAX_CHARACTERS - characters)
                return PhotoOcrTokens(emptyList(), budgetExceeded = true)
            if (element.text.isBlank()) continue
            characters += element.text.length
            val symbols = element.symbols
            val complete = symbols.isNotEmpty() && symbols.size <= MathLayout.MAX_TOKENS &&
                symbols.all { it.text.isNotBlank() && usable(it.bounds) } &&
                symbols.sumOf { it.text.length.toLong() } <= MathLayout.MAX_CHARACTERS &&
                compact(symbols.joinToString("") { it.text }) == compact(element.text)
            val selected = when {
                complete -> symbols.map { MathToken(it.text, checkNotNull(it.bounds)) }
                usable(element.bounds) -> {
                    fallback = true
                    listOf(MathToken(element.text, checkNotNull(element.bounds), uncertain = true))
                }
                else -> { missing = true; emptyList() }
            }
            if (tokens.size + selected.size > MathLayout.MAX_TOKENS)
                return PhotoOcrTokens(emptyList(), budgetExceeded = true)
            tokens += selected
        }
        return PhotoOcrTokens(tokens, missing, fallback)
    }
}
