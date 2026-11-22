package com.vaibhav.calc.android

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.name.Functions
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.parse.Parser
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A bounded, explicit review candidate, not automatic multiline recognition or a solver. */
internal object MathEquationSystems {
    data class Candidate(val expression: String, val rows: List<MathLayoutResult.Draft>)

    /** Equality signs anchor 2–3 vertically separated rows. Every input token must belong
     * wholly to one row, whose entire geometry must be accepted by the single-row reader. */
    fun suggest(tokens: List<MathToken>): Candidate? {
        if (tokens.isEmpty() || tokens.size > MathLayout.MAX_TOKENS ||
            tokens.sumOf { it.text.length.toLong() } > MathLayout.MAX_CHARACTERS) return null
        if (tokens.any { token ->
                val b = token.bounds
                token.text.isBlank() || token.text.any { it == '\n' || it == '\r' } ||
                    listOf(b.left, b.top, b.right, b.bottom).any { !it.isFinite() || abs(it) > 1_000_000f } ||
                    b.width <= 0f || b.height <= 0f
            }) return null
        val anchors = tokens.filter { it.text.trim() in setOf("=", "＝") }.sortedBy { it.bounds.centerY }
        if (anchors.size !in 2..3 || tokens.sumOf { token -> token.text.count { it == '=' || it == '＝' } } != anchors.size)
            return null
        val heights = tokens.filter { it.text.any(Char::isLetterOrDigit) }.map { it.bounds.height }.sorted()
        val nominal = heights.getOrNull(heights.size / 2) ?: return null
        if (anchors.zipWithNext().any { (a, b) -> b.bounds.centerY - a.bounds.centerY < nominal * 1.5f }) return null
        val boundaries = anchors.zipWithNext().map { (a, b) -> (a.bounds.centerY + b.bounds.centerY) / 2f }
        val groups = List(anchors.size) { mutableListOf<MathToken>() }
        for ((index, token) in tokens.withIndex()) {
            val b = token.bounds
            // No token is split, dropped, or assigned across an uncertain boundary.
            if (boundaries.any { b.top <= it && b.bottom >= it }) return null
            val row = boundaries.count { b.centerY > it }
            if (abs(b.centerY - anchors[row].bounds.centerY) > nominal * 3f) return null
            groups[row] += token.copy(sourceIndex = index)
        }
        if (groups.any { row -> row.count { it.text.trim() in setOf("=", "＝") } != 1 }) return null
        val rowBoxes = groups.map { row -> MathBox(row.minOf { it.bounds.left }, row.minOf { it.bounds.top },
            row.maxOf { it.bounds.right }, row.maxOf { it.bounds.bottom }) }
        if (rowBoxes.zipWithNext().any { (a, b) ->
                // A visible gutter and shared horizontal span distinguish stacked rows
                // from a fraction crossing rows or independent side-by-side columns.
                b.top - a.bottom < nominal * .2f ||
                    min(a.right, b.right) - max(a.left, b.left) < min(a.width, b.width) * .35f
            }) return null
        val rows = ArrayList<MathLayoutResult.Draft>(groups.size)
        val unknowns = mutableSetOf<String>()
        for (group in groups) {
            val row = MathLayout.read(group) as? MathLayoutResult.Draft ?: return null
            val names = equationUnknowns(row.expression) ?: return null
            if (names.isEmpty()) return null
            unknowns += names
            rows += row
        }
        // The supported system router expects the same number of x/y/z unknowns
        // and equations. This is a syntactic check, never an answer-validity test.
        if (unknowns.size != rows.size) return null
        val expression = rows.joinToString("\n") { it.expression }
        if (expression.length > MathLayout.MAX_CHARACTERS) return null
        return Candidate(expression, rows.toList())
    }

    private fun equationUnknowns(expression: String): Set<String>? {
        // Bound parser recursion even when an OCR element contains many characters.
        var depth = 0
        var operators = 0
        for (c in expression) {
            if (c == '(' && ++depth > 32) return null
            if (c == ')' && --depth < 0) return null
            if (c in "+-*/^!%" && ++operators > 128) return null
        }
        if (depth != 0) return null
        val program = Parser.parseProgram(expression).getOrNull() ?: return null
        val equation = program.statements.singleOrNull() as? Equation ?: return null
        val unknowns = mutableSetOf<String>()
        val pending = ArrayDeque<Pair<Expr, Int>>()
        pending.add(equation.lhs to 0); pending.add(equation.rhs to 0)
        var count = 0
        while (pending.isNotEmpty()) {
            val (node, level) = pending.removeLast()
            if (++count > 256 || level > 32) return null
            fun add(child: Expr) { pending.add(child to level + 1) }
            when (node) {
                is NumberLit -> Unit
                is NameRef -> when {
                    ReservedNames.isUnknown(node.name) -> unknowns += ReservedNames.canonicalUnknown(node.name)
                    ReservedNames.isConstant(node.name) -> Unit
                    else -> return null // Labels, prose, and state-dependent registers are not inferred.
                }
                is Unary -> add(node.operand)
                is Binary -> { add(node.left); add(node.right) }
                is Postfix -> add(node.operand)
                is Call -> {
                    val function = Functions.get(node.name) ?: return null
                    if (node.args.size !in function.arities) return null
                    node.args.forEach(::add)
                }
                is UnitApply -> return null
            }
        }
        return unknowns
    }
}
