package com.vaibhav.calc.core.ast

import com.vaibhav.calc.core.source.SourceSpan

/**
 * A top-level statement (D-016). The parser produces these instead of a bare
 * [Expr] so that `=` (equation) and `→` (store) are grammar, not heuristics.
 */
sealed interface Statement {
    val span: SourceSpan
}

/** A plain expression: `2+2`, `5 km + 300 m`, `sin(30)`. */
data class ExprStatement(val expr: Expr, override val span: SourceSpan) : Statement

/** `lhs = rhs`. Always an equation — never an assignment (D-016.1). */
data class Equation(val lhs: Expr, val rhs: Expr, override val span: SourceSpan) : Statement

/** `value → target`. The store gesture; target must be a register (D-016.4). */
data class Assignment(val target: String, val value: Expr, override val span: SourceSpan) : Statement

/**
 * One or more statements. Multiple equations (separated by NEWLINE, or the `;`
 * alias) form a system — that is how a human writes a system on paper (D-016.3).
 */
data class Program(val statements: List<Statement>, val span: SourceSpan)
