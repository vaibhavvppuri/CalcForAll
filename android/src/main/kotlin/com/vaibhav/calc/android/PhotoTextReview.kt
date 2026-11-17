package com.vaibhav.calc.android

/** Literal OCR lines for explicit user selection. Never guesses or changes mathematical symbols. */
object PhotoTextReview {
    const val MAX_LINES = 24
    fun lines(recognized: String): List<String> = recognized.take(CalcSession.MAX_ENTRY_CHARS)
        .replace("\r\n", "\n").replace('\r', '\n')
        .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
        .take(MAX_LINES).toList()
}
