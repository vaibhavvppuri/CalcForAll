package com.vaibhav.calc.core.lex

import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.source.SourceSpan

enum class TokenType {
    NUMBER, IDENT,
    PLUS, MINUS, STAR, SLASH, CARET,
    LPAREN, RPAREN, COMMA,
    BANG, PERCENT, SQRT,

    /** `=` — equation only. Assignment is [ARROW] (D-016.1). */
    EQUALS,

    /** `→` / `->` / `sto` — the store gesture. */
    ARROW,

    /** Statement separator: a real newline (canonical) or `;` (tolerated alias). */
    NEWLINE,

    EOF,
}

/**
 * A lexical token. Numbers arrive already parsed into an exact [Num] so the
 * parser never re-interprets a literal. Every token carries its [span].
 */
data class Token(
    val type: TokenType,
    val lexeme: String,
    val span: SourceSpan,
    val value: Num? = null,
)
