package com.vaibhav.calc.android

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.format.OutcomeFormatter
import com.vaibhav.calc.core.format.RenderedOutcome
import com.vaibhav.calc.core.parse.Parser
import com.vaibhav.calc.core.route.ExpressionRouter
import com.vaibhav.calc.core.route.RouteResult

sealed interface RecognitionPreview {
    data object Empty : RecognitionPreview
    data object Calculating : RecognitionPreview
    data class Answer(val expression: String, val rendered: RenderedOutcome) : RecognitionPreview
    data class NeedsEdit(val message: String) : RecognitionPreview
}

/** Evaluates a proposed transcription in a private context; never writes Ans or history. */
object RecognitionCalculator {
    const val MAX_CHARS = 2048
    private const val MAX_NODES = 256
    private const val MAX_DEPTH = 24

    fun preview(input: String, settings: Settings, environment: Environment): RecognitionPreview {
        val text = input.trim()
        if (text.isEmpty()) return RecognitionPreview.Empty
        if (text.length > MAX_CHARS) return RecognitionPreview.NeedsEdit("Use a smaller expression (maximum $MAX_CHARS characters).")
        var nesting = 0
        for (char in text) {
            if (char == '(' && ++nesting > MAX_DEPTH) return RecognitionPreview.NeedsEdit("The expression is nested too deeply for automatic calculation.")
            if (char == ')') nesting = (nesting - 1).coerceAtLeast(0)
        }
        val program = when (val parsed = Parser.parseProgram(text)) {
            is CalcResult.Ok -> parsed.value
            is CalcResult.Err -> return RecognitionPreview.NeedsEdit(parsed.error.message)
        }
        if (program.statements.size > 6) return RecognitionPreview.NeedsEdit("Use at most six equations at a time.")
        val queue = ArrayDeque<Pair<Expr, Int>>()
        program.statements.forEach { statement -> when (statement) {
            is Assignment -> return RecognitionPreview.NeedsEdit("Register assignments need the calculator editor. Enter an expression or equation here.")
            is Equation -> { queue.addLast(statement.lhs to 0); queue.addLast(statement.rhs to 0) }
            is ExprStatement -> queue.addLast(statement.expr to 0)
        } }
        var nodes = 0
        while (queue.isNotEmpty()) {
            val (expr, depth) = queue.removeLast()
            if (++nodes > MAX_NODES || depth > MAX_DEPTH) return RecognitionPreview.NeedsEdit("This expression is too complex for automatic calculation. Split it into smaller parts.")
            val children = when (expr) {
                is Binary -> listOf(expr.left, expr.right)
                is Unary -> listOf(expr.operand)
                is Postfix -> listOf(expr.operand)
                is Call -> expr.args
                is UnitApply -> listOf(expr.value)
                is NumberLit, is NameRef -> emptyList()
            }
            children.forEach { queue.addLast(it to depth + 1) }
        }
        val isolated = Environment()
        environment.names().forEach { name -> environment.get(name)?.let { isolated.set(name, it) } }
        return when (val result = ExpressionRouter(settings, isolated).route(text)) {
            is RouteResult.Ok -> RecognitionPreview.Answer(text, OutcomeFormatter.render(result.outcome, text, settings))
            is RouteResult.Err -> RecognitionPreview.NeedsEdit(result.error.message)
            is RouteResult.Ambiguous -> RecognitionPreview.NeedsEdit("This expression has more than one reading. Add parentheses to make it explicit.")
        }
    }
}
