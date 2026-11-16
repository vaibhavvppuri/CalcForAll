package com.vaibhav.calc.core.source

/**
 * A half-open span `[start, end)` into the original input text (0-based).
 * Every token and every error carries one, so the engine can always report
 * *where* something went wrong — see Constitution, Article 3 / spec 1.8.
 */
data class SourceSpan(val start: Int, val end: Int) {
    init {
        require(start >= 0 && end >= start) { "invalid span [$start, $end)" }
    }

    val length: Int get() = end - start

    /** Caret line under the offending region, e.g. for `"1+*2"` → `"  ^"`. */
    fun caret(): String = " ".repeat(start) + "^".repeat(maxOf(1, length))

    companion object {
        fun point(at: Int): SourceSpan = SourceSpan(at, at)
        val NONE: SourceSpan = SourceSpan(0, 0)
    }
}
