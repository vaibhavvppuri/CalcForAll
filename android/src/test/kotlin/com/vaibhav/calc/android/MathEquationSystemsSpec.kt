package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class MathEquationSystemsSpec : StringSpec({
    fun token(text: String, x: Int, y: Int = 20, w: Int = 20, h: Int = 40) =
        MathToken(text, MathBox(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()))
    fun row(text: String, y: Int, x: Int = 0) = text.mapIndexed { index, c -> token(c.toString(), x + 30 * index, y) }
    fun system(tokens: List<MathToken>) = checkNotNull(MathEquationSystems.suggest(tokens))
    fun solution(text: String): Map<String, Num> {
        val solved = Calculator().route(text).shouldBeInstanceOf<RouteResult.Ok>().outcome.shouldBeInstanceOf<Outcome.SystemSolution>()
        return solved.unknowns.zip(solved.values).toMap()
    }

    "two separate equations retain all symbols and remain an explicit alternative to rejected layout" {
        val tokens = row("X+y=5", 20) + row("X-y=1", 160)
        val candidate = system(tokens)
        candidate.expression shouldBe "X+y=5\nX-y=1"
        candidate.rows.flatMap { it.sourceTokens }.map { it.sourceIndex }.sorted() shouldBe tokens.indices.toList()
        solution(candidate.expression) shouldBe mapOf("x" to Num.of(3), "y" to Num.of(2))
        val original = MathLayout.read(tokens).shouldBeInstanceOf<MathLayoutResult.Rejected>()
        val draft = PhotoMathDraft("X+y=5\nX-y=1", original,
            corrections = listOf(PhotoMathCorrection(candidate.expression, "Review both rows")))
        draft.expressionForReview shouldBe ""
        draft.completeRawText shouldBe "X+y=5\nX-y=1"
    }

    "three equations are ordered geometrically and solve their independent reference system" {
        val tokens = row("x+y+z=6", 20) + row("2x-y+z=3", 160) + row("x+y-z=0", 300)
        val candidate = system(tokens.reversed())
        candidate.rows.size shouldBe 3
        candidate.expression shouldBe "x+y+z=6\n2*x-y+z=3\nx+y-z=0"
        solution(candidate.expression) shouldBe mapOf("x" to Num.ONE, "y" to Num.of(2), "z" to Num.of(3))
        candidate.rows.sumOf { it.sourceTokens.size } shouldBe tokens.size
    }

    "raised terms are reconstructed within their own equation rather than flattened from OCR lines" {
        val first = listOf(token("x", 0), token("+", 30), token("2", 60), token("2", 83, 0, 12, 20),
            token("y", 110), token("=", 140), token("11", 170, w = 35))
        val candidate = system(first + row("x-y=1", 180))
        candidate.expression shouldBe "x+2^(2)*y=11\nx-y=1"
        solution(candidate.expression) shouldBe mapOf("x" to Num.of(3), "y" to Num.of(2))
        candidate.rows.first().warnings.any { "power" in it } shouldBe true
    }

    "a whole stacked fraction stays in its equation and every numerator denominator and bar is retained" {
        val first = listOf(token("x", 20, 0, 20, 30), token("-", 10, 40, 40, 2), token("2", 20, 50, 20, 30),
            token("+", 65, 30, 20, 20), token("y", 95), token("=", 125), token("4", 155))
        val candidate = system(first + row("x-y=2", 180))
        candidate.expression shouldBe "((x)/(2))+y=4\nx-y=2"
        solution(candidate.expression) shouldBe mapOf("x" to Num.of(4), "y" to Num.of(2))
        candidate.rows.first().sourceTokens.map { it.text } shouldBe first.map { it.text }
    }

    "translation scaling and SDK enumeration order do not change the suggested system" {
        val original = row("x+y=5", 20) + row("x-y=1", 160)
        val transformed = original.reversed().map { t ->
            val b = t.bounds
            t.copy(bounds = MathBox(b.left * 2 + 300, b.top * 2 - 100, b.right * 2 + 300, b.bottom * 2 - 100))
        }
        system(transformed).expression shouldBe system(original).expression
    }

    "a single equation four rows or hidden extra equality signs are not proposed as a supported system" {
        MathEquationSystems.suggest(row("x=1", 20)) shouldBe null
        MathEquationSystems.suggest((0..3).flatMap { row("x+y=2", it * 140) }) shouldBe null
        MathEquationSystems.suggest(row("x+y=5", 20) + listOf(token("x-y=1", 0, 160, 150))) shouldBe null
        MathEquationSystems.suggest(row("x=y=5", 20) + row("x-y=1", 160)) shouldBe null
    }

    "a token straddling the row boundary cannot be dropped or assigned to a convenient row" {
        val tokens = row("x+y=5", 20) + row("x-y=1", 160)
        MathEquationSystems.suggest(tokens + token("-", 20, 90, 60, 40)) shouldBe null
        MathEquationSystems.suggest(tokens + token("1", 25, 100, 20, 20)) shouldBe null
    }

    "equations on one baseline or separated columns are not mistaken for a vertical system" {
        MathEquationSystems.suggest(row("x+y=5", 20) + row("x-y=1", 20, 250)) shouldBe null
        MathEquationSystems.suggest(row("x+y=5", 20) + row("x-y=1", 160, 250)) shouldBe null
        MathEquationSystems.suggest(row("x+y=5", 20) + row("x-y=1", 70)) shouldBe null
    }

    "extra labels prose and state dependent names prevent a candidate rather than becoming discarded text" {
        val tokens = row("x+y=5", 20) + row("x-y=1", 160)
        MathEquationSystems.suggest(tokens + token("Solve", -110, 20, 100)) shouldBe null
        MathEquationSystems.suggest(tokens + token("note", 0, 300, 80)) shouldBe null
        MathEquationSystems.suggest(row("A+x=5", 20) + row("x-y=1", 160)) shouldBe null
        MathEquationSystems.suggest(row("m+x=5", 20) + row("x-y=1", 160)) shouldBe null
    }

    "each row must be a complete equation even when both sides look geometrically aligned" {
        MathEquationSystems.suggest(row("x+y=", 20) + row("x-y=1", 160)) shouldBe null
        MathEquationSystems.suggest(row("x+=5", 20) + row("x-y=1", 160)) shouldBe null
        MathEquationSystems.suggest(row("(x+y=5", 20) + row("x-y=1", 160)) shouldBe null
        MathEquationSystems.suggest(row("x+y=5", 20) + row("=1", 160)) shouldBe null
    }

    "an incomplete fraction cannot be rescued by the presence of a second equation" {
        val first = listOf(token("x", 20, 0, 20, 30), token("-", 10, 40, 40, 2),
            token("+", 65, 30, 20, 20), token("y", 95), token("=", 125), token("4", 155))
        MathEquationSystems.suggest(first + row("x-y=2", 180)) shouldBe null
    }

    "unknown counts must match rows and constant truth statements are not silently added to a system" {
        MathEquationSystems.suggest(row("x=1", 20) + row("x=2", 160)) shouldBe null
        MathEquationSystems.suggest(row("x+y+z=6", 20) + row("x-y=1", 160)) shouldBe null
        MathEquationSystems.suggest(row("2+3=5", 20) + row("x+y=1", 160)) shouldBe null
    }

    "recognized function syntax keeps its scope but unknown words and malformed calls are refused" {
        val first = listOf(token("sqrt(4)*x+y", 0, 20, 220), token("=", 240), token("8", 270))
        val candidate = system(first + row("x-y=1", 160))
        solution(candidate.expression) shouldBe mapOf("x" to Num.of(3), "y" to Num.of(2))
        MathEquationSystems.suggest(first.map { if (it.text.startsWith("sqrt")) it.copy(text = "solve(4)*x+y") else it } + row("x-y=1", 160)) shouldBe null
        MathEquationSystems.suggest(first.map { if (it.text.startsWith("sqrt")) it.copy(text = "sqrt(4,9)*x+y") else it } + row("x-y=1", 160)) shouldBe null
    }

    "invalid coordinates token budgets and deeply nested element text produce no partial candidate" {
        val tokens = row("x+y=5", 20) + row("x-y=1", 160)
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, 1_000_001f)) {
            MathEquationSystems.suggest(tokens.mapIndexed { i, t -> if (i == 0) t.copy(bounds = t.bounds.copy(left = bad)) else t }) shouldBe null
        }
        MathEquationSystems.suggest(tokens + List(55) { token("1", it * 30, 300) }) shouldBe null
        MathEquationSystems.suggest(tokens.mapIndexed { i, t -> if (i == 0) t.copy(text = "x".repeat(2049)) else t }) shouldBe null
        val nested = "(".repeat(33) + "x" + ")".repeat(33)
        MathEquationSystems.suggest(listOf(token(nested, 0, 20, 80)) + row("+y=5", 20, 100) + row("x-y=1", 160)) shouldBe null
        MathEquationSystems.suggest(tokens.mapIndexed { i, t -> if (i == 0) t.copy(text = "x\ny") else t }) shouldBe null
    }
})
