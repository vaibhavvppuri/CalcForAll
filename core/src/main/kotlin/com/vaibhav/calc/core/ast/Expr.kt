package com.vaibhav.calc.core.ast

import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.source.SourceSpan

/**
 * The abstract syntax tree. The parser produces this — **a tree, never a
 * value** (Constitution / spec M1.3). Keeping evaluation out of the parser is
 * exactly what makes a v2 CAS possible without a rewrite: a symbolic engine
 * rewrites this tree; it never has to re-parse.
 */
sealed interface Expr {
    val span: SourceSpan
}

/** A literal number, already exact. */
data class NumberLit(val value: Num, override val span: SourceSpan) : Expr

/** A bare name — resolved at evaluation time to a constant (π, e) or a variable. */
data class NameRef(val name: String, override val span: SourceSpan) : Expr

data class Unary(val op: UnaryOp, val operand: Expr, override val span: SourceSpan) : Expr

data class Binary(val op: BinaryOp, val left: Expr, val right: Expr, override val span: SourceSpan) : Expr

/** A postfix operator: `n!`, `n%`. */
data class Postfix(val op: PostfixOp, val operand: Expr, override val span: SourceSpan) : Expr

/** A function application: `sin(x)`, `nCr(5, 2)`, `log(2, 8)`. */
data class Call(val name: String, val args: List<Expr>, override val span: SourceSpan) : Expr

/**
 * A unit applied to a value: `5 km` (D-016). Distinct from implicit multiplication
 * because the suffix is a *reserved* unit name (D-016.4), decided statically.
 */
data class UnitApply(val value: Expr, val unit: String, override val span: SourceSpan) : Expr

enum class UnaryOp { NEG, POS }
enum class BinaryOp { ADD, SUB, MUL, DIV, POW }
enum class PostfixOp { FACTORIAL, PERCENT }
