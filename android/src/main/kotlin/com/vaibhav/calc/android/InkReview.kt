package com.vaibhav.calc.android

import kotlin.math.abs

/** Explicit user corrections only. Never choose a glyph because its answer calculates. */
internal object InkReview {
    val symbols: List<String> = (0..9).map { it.toString() } +
        listOf("+", "−", "×", "/", "÷", "=", ".", "(", ")", "^", "√", "π", "x", "y", "z", "%", "!")
    val lineSymbols: List<String> = symbols.filter { it != "√" }

    data class Edit(val tokens: List<MathToken>, val layout: MathLayoutResult)

    /** A failed layout still retains the COMPLETE corrected token set for the next edit. */
    fun replace(tokens: List<MathToken>, index: Int, symbol: String): Edit? {
        if (index !in tokens.indices || symbol !in symbols || tokens.size > MathLayout.MAX_TOKENS ||
            tokens.sumOf { it.text.length.toLong() } > MathLayout.MAX_CHARACTERS) return null
        if (tokens.any { token ->
                val b = token.bounds
                listOf(b.left, b.top, b.right, b.bottom).any { !it.isFinite() || abs(it) > 1_000_000f } ||
                    b.left > b.right || b.top > b.bottom || token.alternatives.size > 8 ||
                    token.alternatives.any { it.length > 64 } ||
                    token.matchDistance?.let { !it.isFinite() || it < 0f } == true
            }) return null
        val corrected = tokens.mapIndexed { position, token ->
            if (position == index) token.copy(text = symbol, uncertain = false, matchDistance = null,
                alternatives = token.alternatives.toList())
            else token.copy(alternatives = token.alternatives.toList())
        }
        return Edit(corrected, MathLayout.read(corrected))
    }
}
