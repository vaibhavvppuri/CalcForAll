package com.vaibhav.calc.android

/** List separators apply outside function calls, including reusable approx(value,error). */
object ToolInputLists {
    fun split(text: String, separators: Set<Char>, whitespace: Boolean = false, keepEmpty: Boolean = false): List<String> {
        require(text.length <= 8192) { "Input is too long." }
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        fun add(end: Int) {
            val part = text.substring(start, end).trim()
            if (keepEmpty || part.isNotEmpty()) parts += part
            require(parts.size <= 1000) { "Use at most 1,000 values." }
        }
        text.forEachIndexed { index, char ->
            if (char == '(') { depth++; require(depth <= 64) { "A value is nested too deeply." } }
            if (char == ')') { depth--; require(depth >= 0) { "Check the parentheses in each value." } }
            if (depth == 0 && (char in separators || (whitespace && char.isWhitespace()))) {
                add(index); start = index + 1
            }
        }
        require(depth == 0) { "Check the parentheses in each value." }
        add(text.length)
        return parts
    }
}
