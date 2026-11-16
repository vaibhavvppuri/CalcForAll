package com.vaibhav.calc.battery

import java.math.BigDecimal

/**
 * A minimal, dependency-free JSON reader/writer.
 *
 * Hand-written on purpose. A JSON library would mean fetching a new dependency,
 * and this run's safety floor forbids network calls — so the parser is the one
 * thing the harness brings itself.
 *
 * **It is verified non-vacuously by its caller**, not trusted: [Battery.load]
 * asserts the parsed case count equals the file's own `case_count`, and that the
 * per-class and per-axis histograms equal the file's own `coverage` block. A
 * parser that silently dropped or mangled cases fails there rather than quietly
 * shrinking the battery — which would be Article 11's exact shape, a clean-looking
 * count over work that never happened.
 */
internal object MiniJson {

    // 0x0C, written as a code point so no raw control byte sits in this source.
    private val FORM_FEED: Char = 12.toChar()

    fun parse(text: String): Any? = Reader(text).let { r ->
        val v = r.value()
        r.skipWs()
        v
    }

    @Suppress("UNCHECKED_CAST")
    fun obj(v: Any?): Map<String, Any?> = v as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun arr(v: Any?): List<Any?> = v as List<Any?>

    fun str(v: Any?): String? = v as? String

    fun int(v: Any?): Int? = (v as? BigDecimal)?.toInt()

    private class Reader(private val s: String) {
        private var i = 0

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skipWs()
            require(i < s.length) { "unexpected end of JSON at $i" }
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { lit("true"); true }
                'f' -> { lit("false"); false }
                'n' -> { lit("null"); null }
                else -> num()
            }
        }

        private fun lit(w: String) {
            require(s.startsWith(w, i)) { "bad literal at $i" }
            i += w.length
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++ // '{'
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                skipWs()
                val k = str()
                skipWs()
                require(s[i] == ':') { "expected ':' at $i" }
                i++
                m[k] = value()
                skipWs()
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> error("expected ',' or '}' at $i")
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++ // '['
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                l.add(value())
                skipWs()
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> error("expected ',' or ']' at $i")
                }
            }
        }

        private fun str(): String {
            require(s[i] == '"') { "expected a string at $i" }
            i++
            val sb = StringBuilder()
            while (true) {
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = s[i++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append(FORM_FEED)
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        // The battery carries \uXXXX escapes. Dropping these would
                        // silently alter the expressions under test.
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> error("bad escape at $i")
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): BigDecimal {
            val start = i
            if (s[i] == '-' || s[i] == '+') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' ||
                    ((s[i] == '+' || s[i] == '-') && (s[i - 1] == 'e' || s[i - 1] == 'E')))
            ) i++
            return BigDecimal(s.substring(start, i))
        }
    }

    // --- writing (results are emitted as JSON so exprs carrying tabs/newlines
    // --- survive the round trip; a TSV would corrupt them silently) ----------

    fun escape(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                FORM_FEED -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    fun field(name: String, value: String?): String =
        if (value == null) "\"$name\":null" else "\"$name\":\"${escape(value)}\""

    fun rawField(name: String, value: String): String = "\"$name\":$value"
}
