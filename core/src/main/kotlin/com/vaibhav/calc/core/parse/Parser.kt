package com.vaibhav.calc.core.parse

import com.vaibhav.calc.core.ast.Assignment
import com.vaibhav.calc.core.ast.Binary
import com.vaibhav.calc.core.ast.BinaryOp
import com.vaibhav.calc.core.ast.Call
import com.vaibhav.calc.core.ast.Equation
import com.vaibhav.calc.core.ast.Expr
import com.vaibhav.calc.core.ast.ExprStatement
import com.vaibhav.calc.core.ast.NameRef
import com.vaibhav.calc.core.ast.NumberLit
import com.vaibhav.calc.core.ast.Postfix
import com.vaibhav.calc.core.ast.PostfixOp
import com.vaibhav.calc.core.ast.Program
import com.vaibhav.calc.core.ast.Statement
import com.vaibhav.calc.core.ast.Unary
import com.vaibhav.calc.core.ast.UnaryOp
import com.vaibhav.calc.core.ast.UnitApply
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.error.SyntaxFault
import com.vaibhav.calc.core.lex.Token
import com.vaibhav.calc.core.lex.TokenType
import com.vaibhav.calc.core.lex.Tokenizer
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.source.SourceSpan

/**
 * A Pratt (precedence-climbing) parser producing an [Expr] tree — never a value.
 * Precedence is arranged so the classic traps come out right:
 *
 *  - `-2^2  == -(2^2) == -4`  (unary minus is looser than `^`)
 *  - `2^-2  == 2^(-2)`        (unary minus may sit in an exponent)
 *  - `2^3^2 == 2^(3^2)`       (`^` is right-associative)
 *  - `2x^2  == 2*(x^2)`       (implicit multiply is looser than `^`)
 *  - `50+20% == 60`           (contextual percent; handled in the evaluator)
 *
 * Above expressions sits a statement layer (D-016): `=` is an equation, `→` is a
 * store, and NEWLINE separates the equations of a system.
 */
class Parser private constructor(private val tokens: List<Token>) {

    private var pos = 0
    private var depth = 0

    private fun peek(): Token = tokens[pos]
    private fun next(): Token = tokens[pos++]
    private fun atEnd(): Boolean = peek().type == TokenType.EOF

    private object Bp {
        const val ADD = 10
        const val MUL = 20         // *, /, implicit multiply, `of`
        const val UNARY = 30       // prefix - / + / √
        const val POW = 40         // right-associative
        const val PERCENT = 45     // postfix %
        // `5 km`. Must stay BELOW POW's right binding power (39), or a unit binds
        // inside an exponent: `10^3 kg` would parse as `10^(3 kg)` and be rejected.
        const val UNIT = 35
        const val FACTORIAL = 50   // postfix !
    }

    // -----------------------------------------------------------------------
    // Statements (D-016)
    // -----------------------------------------------------------------------

    private fun skipNewlines() {
        while (peek().type == TokenType.NEWLINE) next()
    }

    private fun parseProgramNode(): Program {
        val statements = ArrayList<Statement>()
        skipNewlines()
        while (!atEnd()) {
            statements += parseStatement()
            if (peek().type == TokenType.NEWLINE) skipNewlines() else break
        }
        if (!atEnd()) throw SyntaxFault("unexpected '${peek().lexeme}'", peek().span)
        if (statements.isEmpty()) throw SyntaxFault("empty expression", SourceSpan.point(0))
        return Program(statements, span(statements.first().span, statements.last().span))
    }

    private fun parseStatement(): Statement {
        val first = parseExpr(0)
        val t = peek()
        return when {
            t.type == TokenType.EQUALS -> {
                next()
                val rhs = parseExpr(0)
                Equation(first, rhs, span(first.span, rhs.span))
            }
            t.type == TokenType.ARROW || (t.type == TokenType.IDENT && t.lexeme == "sto") -> {
                next()
                val target = peek()
                if (target.type != TokenType.IDENT) {
                    throw SyntaxFault("expected a register name after '→'", target.span)
                }
                next()
                Assignment(target.lexeme, first, span(first.span, target.span))
            }
            else -> ExprStatement(first, first.span)
        }
    }

    // -----------------------------------------------------------------------
    // Expressions
    // -----------------------------------------------------------------------

    private fun parseSingleExpr(): Expr {
        skipNewlines()
        val e = parseExpr(0)
        skipNewlines()
        if (!atEnd()) throw SyntaxFault("unexpected '${peek().lexeme}'", peek().span)
        return e
    }

    private fun parseExpr(minBp: Int): Expr {
        // Bound recursion so deeply-nested input fails as a typed SYNTAX error
        // rather than a StackOverflowError.
        if (++depth > MAX_DEPTH) throw SyntaxFault("expression nested too deeply", peek().span)
        try {
            var left = parsePrefix()

            loop@ while (true) {
                val t = peek()

                // A reserved unit name directly after a value: `5 km` (D-016.4).
                // Decided statically from the unit table — never from stored state.
                if (t.type == TokenType.IDENT && ReservedNames.isUnit(t.lexeme) && Bp.UNIT > minBp) {
                    next()
                    left = UnitApply(left, t.lexeme, span(left.span, t.span))
                    continue@loop
                }

                when (t.type) {
                    TokenType.BANG -> {
                        if (Bp.FACTORIAL <= minBp) break@loop
                        next()
                        left = Postfix(PostfixOp.FACTORIAL, left, span(left.span, t.span))
                        continue@loop
                    }
                    TokenType.PERCENT -> {
                        if (Bp.PERCENT <= minBp) break@loop
                        next()
                        left = Postfix(PostfixOp.PERCENT, left, span(left.span, t.span))
                        continue@loop
                    }
                    else -> {}
                }

                val infix = infixOp(t)
                if (infix != null) {
                    val (op, lbp, rbp) = infix
                    if (lbp <= minBp) break@loop
                    next()
                    val right = parseExpr(rbp)
                    left = Binary(op, left, right, span(left.span, right.span))
                    continue@loop
                }

                // Implicit multiplication: `2x`, `2(3)`, `2π`, `(a)(b)`.
                if (startsPrimary(t) && Bp.MUL > minBp) {
                    val right = parseExpr(Bp.MUL)
                    left = Binary(BinaryOp.MUL, left, right, span(left.span, right.span))
                    continue@loop
                }

                break@loop
            }
            return left
        } finally {
            depth--
        }
    }

    /** Returns (operator, leftBp, rightBp) or null. Handles the `of` keyword. */
    private fun infixOp(t: Token): Triple<BinaryOp, Int, Int>? = when (t.type) {
        TokenType.PLUS -> Triple(BinaryOp.ADD, Bp.ADD, Bp.ADD)
        TokenType.MINUS -> Triple(BinaryOp.SUB, Bp.ADD, Bp.ADD)
        TokenType.STAR -> Triple(BinaryOp.MUL, Bp.MUL, Bp.MUL)
        TokenType.SLASH -> Triple(BinaryOp.DIV, Bp.MUL, Bp.MUL)
        TokenType.CARET -> Triple(BinaryOp.POW, Bp.POW, Bp.POW - 1) // right-assoc
        TokenType.IDENT -> if (t.lexeme == "of") Triple(BinaryOp.MUL, Bp.MUL, Bp.MUL) else null
        else -> null
    }

    private fun parsePrefix(): Expr {
        val t = peek()
        return when (t.type) {
            TokenType.MINUS -> {
                next()
                val operand = parseExpr(Bp.UNARY)
                Unary(UnaryOp.NEG, operand, span(t.span, operand.span))
            }
            TokenType.PLUS -> {
                next()
                val operand = parseExpr(Bp.UNARY)
                Unary(UnaryOp.POS, operand, span(t.span, operand.span))
            }
            TokenType.SQRT -> {
                next()
                val operand = parseExpr(Bp.UNARY + 5) // √ binds a touch tighter than unary -
                Call("sqrt", listOf(operand), span(t.span, operand.span))
            }
            else -> parsePrimary()
        }
    }

    private fun parsePrimary(): Expr {
        val t = next()
        return when (t.type) {
            TokenType.NUMBER -> NumberLit(t.value!!, t.span)
            TokenType.LPAREN -> {
                val e = parseExpr(0)
                expect(TokenType.RPAREN, "')'")
                e
            }
            TokenType.IDENT -> {
                if (peek().type == TokenType.LPAREN) {
                    next() // consume '('
                    val args = parseArgs()
                    val close = expect(TokenType.RPAREN, "')'")
                    Call(t.lexeme, args, span(t.span, close.span))
                } else if (ReservedNames.isUnit(t.lexeme)) {
                    // A bare unit is one of it, so `m/s` and `km/hr` read naturally.
                    UnitApply(NumberLit(Num.ONE, t.span), t.lexeme, t.span)
                } else {
                    NameRef(t.lexeme, t.span)
                }
            }
            TokenType.EOF -> throw SyntaxFault("unexpected end of input", t.span)
            else -> throw SyntaxFault("unexpected '${t.lexeme}'", t.span)
        }
    }

    private fun parseArgs(): List<Expr> {
        val args = ArrayList<Expr>()
        if (peek().type == TokenType.RPAREN) return args
        args += parseExpr(0)
        while (peek().type == TokenType.COMMA) {
            next()
            args += parseExpr(0)
        }
        return args
    }

    private fun startsPrimary(t: Token): Boolean = when (t.type) {
        TokenType.NUMBER, TokenType.LPAREN, TokenType.SQRT -> true
        // `of` and `sto` are keywords, not implicit-multiply operands.
        TokenType.IDENT -> t.lexeme != "of" && t.lexeme != "sto"
        else -> false
    }

    private fun expect(type: TokenType, what: String): Token {
        val t = peek()
        if (t.type != type) throw SyntaxFault("expected $what", t.span)
        return next()
    }

    private fun span(a: SourceSpan, b: SourceSpan): SourceSpan =
        SourceSpan(minOf(a.start, b.start), maxOf(a.end, b.end))

    companion object {
        private const val MAX_DEPTH = 512

        /** Parse a single expression (no statements). Used for AST inspection. */
        fun parse(source: String): CalcResult<Expr> = try {
            val tokens = Tokenizer(source).tokenize()
            if (tokens.size == 1) {
                CalcResult.Err(EvalError.Syntax("empty expression", SourceSpan.point(0)))
            } else {
                CalcResult.Ok(Parser(tokens).parseSingleExpr())
            }
        } catch (f: SyntaxFault) {
            CalcResult.Err(EvalError.Syntax(f.message ?: "syntax error", f.span))
        }

        /** Parse a full program: expressions, equations, assignments, systems. */
        fun parseProgram(source: String): CalcResult<Program> = try {
            val tokens = Tokenizer(source).tokenize()
            if (tokens.size == 1) {
                CalcResult.Err(EvalError.Syntax("empty expression", SourceSpan.point(0)))
            } else {
                CalcResult.Ok(Parser(tokens).parseProgramNode())
            }
        } catch (f: SyntaxFault) {
            CalcResult.Err(EvalError.Syntax(f.message ?: "syntax error", f.span))
        }
    }
}
