package com.vaibhav.calc.api

/** Small, strict JSON codec. Request numbers keep their original decimal text, never Double. */
internal sealed interface JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue
    data class Arr(val values: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Number(val text: String) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

internal class JsonInputException(message: String) : IllegalArgumentException(message)

internal object Json {
    fun parse(text: String): JsonValue = Reader(text).read()

    fun encode(value: Any?): String = buildString { appendValue(value) }

    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> appendString(value)
            is Boolean -> append(if (value) "true" else "false")
            is Int, is Long -> append(value)
            is Map<*, *> -> {
                append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    require(k is String) { "JSON object keys must be strings" }
                    if (i > 0) append(',')
                    appendString(k); append(':'); appendValue(v)
                }
                append('}')
            }
            is Iterable<*> -> {
                append('[')
                value.forEachIndexed { i, v -> if (i > 0) append(','); appendValue(v) }
                append(']')
            }
            else -> error("Unsupported JSON output type ${value::class.java.simpleName}; numeric values must be formatted strings")
        }
    }

    private fun StringBuilder.appendString(s: String) {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ' || c.isSurrogate()) {
                append("\\u"); append(c.code.toString(16).padStart(4, '0'))
            } else append(c)
        }
        append('"')
    }

    private class Reader(private val text: String) {
        private var at = 0
        private var values = 0

        fun read(): JsonValue {
            val value = value(0)
            whitespace()
            if (at != text.length) fail("trailing content")
            return value
        }

        private fun value(depth: Int): JsonValue {
            if (depth > 32) fail("JSON nesting exceeds 32 levels")
            if (++values > 10_000) fail("JSON contains too many values")
            whitespace()
            return when (peek()) {
                '{' -> obj(depth)
                '[' -> array(depth)
                '"' -> JsonValue.Str(string())
                't' -> literal("true", JsonValue.Bool(true))
                'f' -> literal("false", JsonValue.Bool(false))
                'n' -> literal("null", JsonValue.Null)
                '-', in '0'..'9' -> number()
                else -> fail("expected a JSON value")
            }
        }

        private fun obj(depth: Int): JsonValue.Obj {
            at++
            val out = linkedMapOf<String, JsonValue>()
            whitespace()
            if (consume('}')) return JsonValue.Obj(out)
            while (true) {
                whitespace()
                if (peek() != '"') fail("expected an object key")
                val key = string()
                if (key in out) fail("duplicate object key '$key'")
                whitespace(); expect(':')
                out[key] = value(depth + 1)
                whitespace()
                if (consume('}')) return JsonValue.Obj(out)
                expect(',')
            }
        }

        private fun array(depth: Int): JsonValue.Arr {
            at++
            val out = ArrayList<JsonValue>()
            whitespace()
            if (consume(']')) return JsonValue.Arr(out)
            while (true) {
                out += value(depth + 1)
                whitespace()
                if (consume(']')) return JsonValue.Arr(out)
                expect(',')
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (at < text.length) {
                val c = text[at++]
                when {
                    c == '"' -> {
                        val result = out.toString()
                        var i = 0
                        while (i < result.length) {
                            val ch = result[i++]
                            if (ch.isHighSurrogate()) {
                                if (i == result.length || !result[i++].isLowSurrogate()) fail("unpaired Unicode surrogate")
                            } else if (ch.isLowSurrogate()) fail("unpaired Unicode surrogate")
                        }
                        return result
                    }
                    c == '\\' -> {
                        if (at == text.length) fail("unfinished string escape")
                        when (val esc = text[at++]) {
                            '"', '\\', '/' -> out.append(esc)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (at + 4 > text.length) fail("unfinished Unicode escape")
                                val digits = text.substring(at, at + 4)
                                if (digits.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) fail("invalid Unicode escape")
                                val n = digits.toInt(16)
                                out.append(n.toChar()); at += 4
                            }
                            else -> fail("invalid string escape")
                        }
                    }
                    c < ' ' -> fail("unescaped control character")
                    else -> out.append(c)
                }
            }
            fail("unfinished string")
        }

        private fun number(): JsonValue.Number {
            val start = at
            consume('-')
            if (consume('0')) {
                if (peek() in '0'..'9') fail("leading zero in number")
            } else {
                if (peek() !in '1'..'9') fail("invalid number")
                while (peek() in '0'..'9') at++
            }
            if (consume('.')) {
                if (peek() !in '0'..'9') fail("fraction requires digits")
                while (peek() in '0'..'9') at++
            }
            if (peek() == 'e' || peek() == 'E') {
                at++
                if (peek() == '+' || peek() == '-') at++
                if (peek() !in '0'..'9') fail("exponent requires digits")
                while (peek() in '0'..'9') at++
            }
            if (at - start > 256) fail("numeric literal exceeds 256 characters; use a bounded expression string")
            return JsonValue.Number(text.substring(start, at))
        }

        private fun <T : JsonValue> literal(word: String, result: T): T {
            if (!text.startsWith(word, at)) fail("invalid literal")
            at += word.length
            return result
        }

        private fun whitespace() { while (peek() in listOf(' ', '\t', '\n', '\r')) at++ }
        private fun peek(): Char = text.getOrNull(at) ?: '\u0000'
        private fun consume(c: Char): Boolean = if (peek() == c) { at++; true } else false
        private fun expect(c: Char) { if (!consume(c)) fail("expected '$c'") }
        private fun fail(message: String): Nothing = throw JsonInputException("$message at JSON offset $at")
    }
}
