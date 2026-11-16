package com.vaibhav.calc.core.lex

import com.vaibhav.calc.core.error.SyntaxFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.NumericLimits
import com.vaibhav.calc.core.source.SourceSpan
import com.vaibhav.calc.core.units.Units
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Turns source text into a flat list of [Token]s, tracking a [SourceSpan] on
 * every one. Understands:
 *  - decimal, scientific (`1.5e-3`) and plain integer literals,
 *  - ASCII identifiers (function names, variables, and the `of` keyword),
 *  - the constant `π` (normalised to the name `pi`),
 *  - ASCII and common Unicode operators (`×÷−·√`).
 *
 * Implicit multiplication (`2x`, `2(3)`, `2π`) is NOT resolved here — that's the
 * parser's job — the tokenizer only reports adjacency faithfully.
 */
class Tokenizer(private val src: String) {

    private var i = 0

    fun tokenize(): List<Token> {
        if (src.length > NumericLimits.MAX_INPUT_CHARACTERS) {
            throw SyntaxFault("expression exceeds ${NumericLimits.MAX_INPUT_CHARACTERS} characters", SourceSpan.point(0))
        }
        val out = ArrayList<Token>()
        while (i < src.length) {
            val c = src[i]
            when {
                // Newline is significant: it separates the equations of a system
                // (D-016.3). `;` is a tolerated alias. Other whitespace is skipped.
                c == '\n' -> { out += Token(TokenType.NEWLINE, "\\n", SourceSpan(i, i + 1)); i++ }
                c == ';' -> { out += Token(TokenType.NEWLINE, ";", SourceSpan(i, i + 1)); i++ }
                c == '\r' -> i++
                c.isWhitespace() -> i++
                // `->` must be matched before `-` would lex as MINUS.
                c == '-' && i + 1 < src.length && src[i + 1] == '>' -> {
                    out += Token(TokenType.ARROW, "->", SourceSpan(i, i + 2)); i += 2
                }
                // Superscript powers: `x²` is `x^2`, `x⁻¹` is `x^-1`. The natural
                // form on a calculator (and what a 2D editor emits).
                // `#3` — a tape entry reference. Lexed as an identifier so the
                // evaluator resolves it through the ordinary environment: no new AST
                // node, no evaluator change, and it cannot be assigned (D-016.4).
                c == '#' -> out += entryReference()
                c in SUPERSCRIPT -> out += superscriptPower(out)
                c.isDigit() || (c == '.' && hasDigitAt(i + 1)) -> out += number()
                c == 'π' -> {
                    out += Token(TokenType.IDENT, "pi", SourceSpan(i, i + 1)); i++
                }
                isIdentStart(c) -> out += identifier()
                else -> out += symbol()
            }
        }
        out += Token(TokenType.EOF, "", SourceSpan.point(i))
        return out
    }

    private fun number(): Token {
        val start = i
        // Base-prefixed integer literals: 0x… (hex), 0b… (binary), 0o… (octal).
        if (src[i] == '0' && i + 1 < src.length) {
            val radix = when (src[i + 1]) {
                'x', 'X' -> 16
                'b', 'B' -> 2
                'o', 'O' -> 8
                else -> 0
            }
            if (radix != 0) {
                // Don't commit `i` until the literal is confirmed: `0x` with no hex
                // digits is 0·x, not a malformed literal. Backtrack and let `0`
                // lex as a number and `x` as an identifier.
                val digitsStart = i + 2
                var j = digitsStart
                while (j < src.length && Character.digit(src[j], radix) != -1) j++
                if (j > digitsStart) {
                    i = j
                    if (j - digitsStart > NumericLimits.MAX_LITERAL_DIGITS) {
                        throw SyntaxFault("base literal exceeds ${NumericLimits.MAX_LITERAL_DIGITS} digits", SourceSpan(start, j))
                    }
                    val value = Num.of(BigInteger(src.substring(digitsStart, j), radix))
                    return Token(TokenType.NUMBER, src.substring(start, j), SourceSpan(start, j), value)
                }
            }
        }
        while (i < src.length && src[i].isDigit()) i++
        if (i < src.length && src[i] == '.') {
            i++
            while (i < src.length && src[i].isDigit()) i++
        }
        // A SECOND decimal point is a malformed literal, and it must be REFUSED
        // rather than reinterpreted. `1..2` used to lex as `1.` and `.2`, which
        // implicit multiplication then silently folded into 0.2 — a confident
        // wrong value from a double-tap on the decimal key, which is D-014's named
        // failure mode and the worst outcome this engine allows.
        //
        // Reaching here with a `.` in hand means one was already consumed above, so
        // this cannot fire on a well-formed literal: `1./2` and `.5+.5` still lex
        // exactly as before (both pinned in B1RegressionSpec).
        if (i < src.length && src[i] == '.') {
            throw SyntaxFault(
                "malformed number '${src.substring(start, i + 1)}' — more than one decimal point",
                SourceSpan(start, i + 1),
            )
        }
        // Exponent — only consume `e`/`E` if a valid exponent actually follows.
        if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
            var j = i + 1
            if (j < src.length && (src[j] == '+' || src[j] == '-')) j++
            if (j < src.length && src[j].isDigit()) {
                j++
                while (j < src.length && src[j].isDigit()) j++
                i = j
            } else if (src[i] == 'e' && (i + 1 >= src.length || !isIdentPart(src[i + 1]))) {
                // A LOWERCASE `e` in numeric position with no exponent digits is an
                // INCOMPLETE EXPONENT — not a quiet fallback to Euler's constant.
                // D-016.6 fixes numeric-position `e` as always an exponent, and
                // `1e` used to escape that and re-read as `1 × e`: the same
                // keystrokes meaning a different operation, which is the one thing
                // that rule exists to prevent.
                //
                // Deliberately LOWERCASE ONLY. Case is load-bearing here (D-016.4):
                // `E` is the REGISTER, not Euler's constant, so `2E` is `2 × E` and
                // is pinned by NamesUnitsSpec. The ambiguity D-016.6 resolves is
                // between the exponent marker and Euler's `e` — both spelled with a
                // lowercase e — and uppercase `E` is simply not part of it. `1E3`
                // still lexes as scientific notation via the branch above.
                throw SyntaxFault(
                    "incomplete exponent in '${src.substring(start, j)}'",
                    SourceSpan(start, j),
                )
            }
            // Otherwise the `e` merely STARTS a longer name (`2exp(3)`): that is
            // implicit multiplication, not an exponent marker, so it is left for
            // the identifier lexer. A blanket "reject digit-adjacent e" would have
            // broken it.
        }
        val span = SourceSpan(start, i)
        var text = src.substring(start, i)
        if (text.endsWith(".")) text = text.dropLast(1)
        if (text.startsWith(".")) text = "0$text"
        if (text.length > NumericLimits.MAX_LITERAL_DIGITS + 16 || text.count { it.isDigit() } > NumericLimits.MAX_LITERAL_DIGITS) {
            throw SyntaxFault("number literal exceeds ${NumericLimits.MAX_LITERAL_DIGITS} digits", span)
        }
        val bd = try {
            BigDecimal(text)
        } catch (e: NumberFormatException) {
            throw SyntaxFault("malformed number '${src.substring(start, i)}'", span)
        }
        // Guard absurd magnitudes: a literal like "1e2000000000" would otherwise
        // make Num.ofDecimal compute BigInteger.TEN.pow(huge) and throw. Surface a
        // typed syntax error instead — the engine never crashes on valid syntax.
        if (bd.scale().toLong() !in -NumericLimits.MAX_LITERAL_SCALE.toLong()..NumericLimits.MAX_LITERAL_SCALE.toLong() || bd.precision() > NumericLimits.MAX_LITERAL_DIGITS) {
            throw SyntaxFault("number literal out of range", span)
        }
        return Token(TokenType.NUMBER, src.substring(start, i), span, Num.ofDecimal(bd))
    }

    private fun identifier(): Token {
        val start = i
        while (i < src.length && isIdentPart(src[i])) i++
        var name = src.substring(start, i)
        // `m²` IS the unit `m2` when that compound is a known unit. Without this,
        // `²` expands to `^2` and tries to raise a *quantity* to a power — which
        // also made the engine's own rendered output (`15 m²`) un-re-readable.
        // Decided from the static unit table, so `x²` is untouched (`x2` is no unit).
        if (i < src.length && SUPERSCRIPT_DIGITS.containsKey(src[i])) {
            val sb = StringBuilder(name)
            var j = i
            while (j < src.length && SUPERSCRIPT_DIGITS.containsKey(src[j])) {
                sb.append(SUPERSCRIPT_DIGITS[src[j]])
                j++
            }
            val candidate = sb.toString()
            if (Units.get(candidate) != null) {
                name = candidate
                i = j
            }
        }
        return Token(TokenType.IDENT, name, SourceSpan(start, i))
    }

    private fun symbol(): Token {
        val start = i
        val c = src[i]
        val type = SYMBOLS[c] ?: throw SyntaxFault("unexpected character '$c'", SourceSpan(start, start + 1))
        i++
        return Token(type, c.toString(), SourceSpan(start, start + 1))
    }

    /**
     * Expands a run of superscript characters into `^ <exponent>`, appending the
     * CARET directly so the parser sees ordinary power syntax.
     */
    private fun superscriptPower(out: MutableList<Token>): Token {
        val start = i
        val sb = StringBuilder()
        var negative = false
        if (src[i] == '⁻') { negative = true; i++ }
        while (i < src.length && SUPERSCRIPT_DIGITS.containsKey(src[i])) {
            sb.append(SUPERSCRIPT_DIGITS[src[i]]); i++
        }
        val span = SourceSpan(start, i)
        if (sb.isEmpty()) throw SyntaxFault("superscript with no digits", span)
        if (sb.length > NumericLimits.MAX_LITERAL_DIGITS) throw SyntaxFault("superscript exponent is too long", span)
        out += Token(TokenType.CARET, "^", span)
        if (negative) out += Token(TokenType.MINUS, "-", span)
        return Token(TokenType.NUMBER, sb.toString(), span, Num.ofDecimal(sb.toString()))
    }

    private fun entryReference(): Token {
        val start = i
        i++ // '#'
        val digitsStart = i
        while (i < src.length && src[i].isDigit()) i++
        if (i == digitsStart) throw SyntaxFault("expected a tape entry number after '#'", SourceSpan(start, i))
        return Token(TokenType.IDENT, src.substring(start, i), SourceSpan(start, i))
    }

    private fun hasDigitAt(idx: Int): Boolean = idx < src.length && src[idx].isDigit()
    private fun isIdentStart(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c == '_'
    private fun isIdentPart(c: Char): Boolean = isIdentStart(c) || c.isDigit()

    companion object {
        /** Operator characters the tokenizer recognises, and their token types. */
        private val SYMBOLS: Map<Char, TokenType> = mapOf(
            '+' to TokenType.PLUS,
            '-' to TokenType.MINUS, '−' to TokenType.MINUS,          // ASCII + Unicode minus
            '*' to TokenType.STAR, '×' to TokenType.STAR, '·' to TokenType.STAR,
            '/' to TokenType.SLASH, '÷' to TokenType.SLASH,
            '^' to TokenType.CARET,
            '(' to TokenType.LPAREN, '[' to TokenType.LPAREN, '{' to TokenType.LPAREN,
            ')' to TokenType.RPAREN, ']' to TokenType.RPAREN, '}' to TokenType.RPAREN,
            ',' to TokenType.COMMA,
            '!' to TokenType.BANG,
            '%' to TokenType.PERCENT,
            '√' to TokenType.SQRT,
            '=' to TokenType.EQUALS,
            '→' to TokenType.ARROW,
        )

        /**
         * Every character the tokenizer can consume — digits, identifier
         * characters, the π constant, the decimal point, all operator symbols
         * (drawn from [SYMBOLS], the SAME map [symbol] dispatches on), and space.
         * A fuzzer built on this set can never hand-exclude a character
         * (Constitution Article 4, amendment A3): if the tokenizer accepts it,
         * the fuzzer emits it.
         */
        internal val SUPERSCRIPT_DIGITS: Map<Char, Char> = mapOf(
            '⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4',
            '⁵' to '5', '⁶' to '6', '⁷' to '7', '⁸' to '8', '⁹' to '9',
        )

        internal val SUPERSCRIPT: Set<Char> = SUPERSCRIPT_DIGITS.keys + '⁻'

        val ACCEPTED_CHARACTERS: List<Char> = buildList {
            addAll('0'..'9')
            addAll('a'..'z')
            addAll('A'..'Z')
            add('_')
            add('π')
            add('.')
            addAll(SYMBOLS.keys)
            addAll(SUPERSCRIPT)
            add(' ')
            add('\n') // statement separator — the fuzzer must exercise it
            add(';')
            add('#') // tape entry references
        }
    }
}
