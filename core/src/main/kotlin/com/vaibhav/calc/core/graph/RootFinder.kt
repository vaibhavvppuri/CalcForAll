package com.vaibhav.calc.core.graph

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.solve.Extraction
import com.vaibhav.calc.core.solve.Poly
import com.vaibhav.calc.core.solve.Solvers
import com.vaibhav.calc.core.name.Functions
import java.util.IdentityHashMap

/** A tolerance-based candidate checked against the original function, not a proof of continuity. */
data class VerifiedRoot(val x: Num, val residual: Num)

sealed interface RootResult {
    /** Candidates passed the stated checks; numeric ones assume continuity and do not prove completeness. */
    data class Found(val roots: List<VerifiedRoot>, val method: String) : RootResult
    data class Unverified(val verified: List<VerifiedRoot>, val candidates: List<Num>, val reason: String) : RootResult
    data class Failed(val error: EvalError) : RootResult
}

/**
 * One shared budget for extraction, scanning, bisection and verification.
 * [shouldStop] is a cheap, non-blocking callback: true requests a typed failure.
 * Cancellation/deadlines are checked around operations; an individual bounded
 * evaluator/extractor invocation is not preempted halfway through arithmetic.
 */
data class RootSearchOptions(
    val maxEvaluations: Int = 10_000,
    val maxMillis: Long = 5_000,
    val shouldStop: (() -> Boolean)? = null,
)

/** Exact low-degree solving, then bounded numeric scanning with original-function residual checks. */
object RootFinder {
    val VERIFY_TOLERANCE: Num = Num.of(1, 1_000_000L)
    private val RELATIVE_RESIDUAL_FACTOR: Num = Num.of(1, 1_000_000L)
    private val BRACKET_TOLERANCE: Num = Num.of(1, 1_000_000_000_000L)
    private const val MAX_BISECTIONS = 120
    const val DEFAULT_SAMPLES = 200
    const val MAX_SAMPLES = 4096
    const val MAX_EVALUATIONS = 20_000
    const val MAX_MILLIS = 15_000L
    const val MAX_EXPRESSION_NODES = 512
    const val MAX_EXPRESSION_DEPTH = 64
    private data class Candidate(val x: Num, val residualLimit: Num = VERIFY_TOLERANCE)

    fun roots(
        f: PlottableFunction,
        xMin: Num,
        xMax: Num,
        settings: Settings,
        sampleCount: Int = DEFAULT_SAMPLES,
        options: RootSearchOptions = RootSearchOptions(),
    ): RootResult = guarded {
        if (sampleCount !in 2..MAX_SAMPLES) return@guarded unsupported("root sample count must be from 2 to $MAX_SAMPLES")
        if (options.maxEvaluations !in 1..MAX_EVALUATIONS) return@guarded unsupported("root evaluation budget must be from 1 to $MAX_EVALUATIONS")
        if (options.maxMillis !in 1..MAX_MILLIS) return@guarded unsupported("root time budget must be from 1 to $MAX_MILLIS ms")
        xMin.requireKnownPoint("root search bounds")
        xMax.requireKnownPoint("root search bounds")
        if (xMin >= xMax) return@guarded RootResult.Failed(EvalError.Domain("root search requires xMin < xMax", null))
        val budget = SearchBudget(options)
        if (!budget.check()) return@guarded budget.failed()
        // Extraction is recursive. Refuse oversized trees before entering it;
        // a long left-associated sum can be deep despite having no parentheses.
        val pending = ArrayDeque<Pair<Expr, Int>>()
        pending.addLast(f.expr to 1)
        var nodes = 0
        while (pending.isNotEmpty()) {
            if (!budget.check()) return@guarded budget.failed()
            val (node, depth) = pending.removeLast()
            if (++nodes > MAX_EXPRESSION_NODES || depth > MAX_EXPRESSION_DEPTH) {
                return@guarded unsupported("root analysis expression exceeds its size or depth budget")
            }
            when (node) {
                is Unary -> pending.addLast(node.operand to depth + 1)
                is Binary -> { pending.addLast(node.left to depth + 1); pending.addLast(node.right to depth + 1) }
                is Postfix -> pending.addLast(node.operand to depth + 1)
                is Call -> node.args.forEach { pending.addLast(it to depth + 1) }
                is UnitApply -> pending.addLast(node.value to depth + 1)
                is NumberLit, is NameRef -> Unit
            }
        }
        val structure = f.polynomial()
        if (!budget.check()) return@guarded budget.failed()
        val result = when (structure) {
            is Extraction.Failed -> RootResult.Failed(structure.error)
            is Extraction.NotPolynomial -> numericRoots(f, xMin, xMax, sampleCount, budget)
            is Extraction.Extracted -> exactRoots(f, structure.value, xMin, xMax, settings, sampleCount, budget)
        }
        if (budget.check()) result else budget.failed()
    }

    fun intersections(
        f: PlottableFunction,
        g: PlottableFunction,
        xMin: Num,
        xMax: Num,
        settings: Settings,
        sampleCount: Int = DEFAULT_SAMPLES,
        options: RootSearchOptions = RootSearchOptions(),
    ): RootResult {
        if (f.unknown != g.unknown) return unsupported("intersected functions must share an independent variable")
        if (!f.sharesEvaluationContext(g)) return unsupported("intersected functions must share equal settings and memory bindings")
        return roots(PlottableFunction.difference(f, g), xMin, xMax, settings, sampleCount, options)
    }

    private fun exactRoots(
        f: PlottableFunction,
        p: Poly,
        xMin: Num,
        xMax: Num,
        settings: Settings,
        sampleCount: Int,
        budget: SearchBudget,
    ): RootResult = when (p.degree) {
        -1 -> RootResult.Unverified(emptyList(), emptyList(),
            "the function is identically zero — every x in range is a root, so there is no finite root set")
        0 -> {
            p.coeff(0).establishedSign("the constant function's nonzeroness")
            RootResult.Found(emptyList(), "exact (non-zero constant has no roots)")
        }
        1 -> {
            val root = Solvers.linear(p.coeff(1), p.coeff(0), settings).root
            if (!budget.check()) budget.failed()
            else verifyAll(f, listOf(Candidate(root)), "exact (linear)", xMin, xMax, budget)
        }
        2 -> {
            val roots = Solvers.quadratic(p.coeff(2), p.coeff(1), p.coeff(0), settings).roots
            if (!budget.check()) budget.failed()
            else verifyAll(f, roots.mapNotNull { (it as? RealValue)?.num?.let { n -> Candidate(n) } }, "exact (quadratic)", xMin, xMax, budget)
        }
        else -> numericRoots(f, xMin, xMax, sampleCount, budget)
    }

    private fun numericRoots(
        f: PlottableFunction, xMin: Num, xMax: Num, sampleCount: Int, budget: SearchBudget,
    ): RootResult {
        if (!allowsNumericAnalysis(f,budget)) return budget.failed()
        val span = xMax - xMin
        val divisor = Num.of(sampleCount - 1)
        val candidates = LinkedHashMap<Num, Candidate>()
        var previous: Sample.Defined? = null
        var defined = 0
        for (i in 0 until sampleCount) {
            val x = when (i) { 0 -> xMin; sampleCount - 1 -> xMax; else -> xMin + span * Num.of(i) / divisor }
            val sample = budget.sample(f, x) ?: return budget.failed()
            when (sample) {
                is Sample.Unevaluated -> return unsupported("root scan could not evaluate the whole requested range: ${sample.error.message}")
                is Sample.Undefined -> { previous = null; continue }
                is Sample.Defined -> {
                    defined++
                    val sign = sample.y.establishedSign("root scan sample sign")
                    if (sign == 0) candidates[sample.x] = Candidate(sample.x, Num.ZERO)
                    val before = previous
                    val beforeSign = before?.y?.establishedSign("root scan sample sign")
                    if (before != null && beforeSign != 0 && sign != 0 && beforeSign != sign) {
                        val root = bisect(f, before, sample, budget) ?: return budget.failed()
                        candidates[root.x] = root
                    }
                    previous = sample
                }
            }
        }
        if (defined == 0) return unsupported("root scan found no real-defined samples in the requested range")
        return verifyAll(f, candidates.values.toList(),
            "numeric (bisection; absolute and relative residual checks; candidates assume continuity, not a completeness proof)",
            xMin, xMax, budget)
    }

    /**
     * Deliberately small continuous subset, not a symbolic domain prover. A
     * removable hole can pass every finite residual test, so restricted-domain
     * or discontinuous forms must be refused before numeric root searching.
     * Plotting and point tracing remain available for these expressions.
     */
    private fun allowsNumericAnalysis(f: PlottableFunction, budget: SearchBudget): Boolean {
        fun children(e: Expr): List<Expr> = when (e) {
            is Unary -> listOf(e.operand)
            is Binary -> listOf(e.left,e.right)
            is Postfix -> listOf(e.operand)
            is Call -> e.args
            is UnitApply -> listOf(e.value)
            is NumberLit, is NameRef -> emptyList()
        }
        val dependent = IdentityHashMap<Expr,Boolean>()
        val nodes = ArrayList<Expr>()
        val pending = ArrayDeque<Pair<Expr,Boolean>>()
        pending.addLast(f.expr to false)
        while (pending.isNotEmpty()) {
            if (!budget.check()) return false
            val (node, visited) = pending.removeLast()
            if (!visited) {
                pending.addLast(node to true)
                children(node).forEach { pending.addLast(it to false) }
            } else {
                dependent[node] = (node is NameRef && node.name.equals(f.unknown,ignoreCase = true)) || children(node).any { dependent[it] == true }
                nodes += node
            }
        }
        fun refuse(detail: String): Boolean {
            budget.stop("numeric root analysis cannot establish continuity for $detail; curve plotting and point tracing remain available")
            return false
        }
        for (node in nodes) {
            if (!budget.check()) return false
            if (dependent[node] != true) continue
            when (node) {
                is Binary -> when (node.op) {
                    BinaryOp.DIV -> {
                        if (dependent[node.right] == true) return refuse("a variable-dependent denominator")
                        val denominator = budget.constant(f,node.right) ?: return false
                        if (denominator.establishedSign("root continuity denominator") == 0) return refuse("a zero denominator")
                    }
                    BinaryOp.POW -> {
                        if (dependent[node.left] != true) {
                            val base = budget.constant(f,node.left) ?: return false
                            if (base.establishedSign("root continuity exponential base") <= 0) return refuse("a non-positive base with a variable exponent")
                        } else {
                            if (dependent[node.right] == true) return refuse("a variable base and exponent")
                            val exponent = budget.constant(f,node.right) ?: return false
                            val integer = exponent.toBigIntegerExactOrNull()
                            if (integer == null || integer.signum() < 0) return refuse("a variable base with a negative or non-integer power")
                        }
                    }
                    BinaryOp.ADD, BinaryOp.SUB, BinaryOp.MUL -> Unit
                }
                is Call -> {
                    val name = Functions.get(node.name)?.name ?: node.name.lowercase()
                    if (name !in CONTINUOUS_FUNCTIONS) return refuse("the function '${node.name}'")
                }
                is Postfix -> if (node.op != PostfixOp.PERCENT) return refuse("a variable-dependent factorial")
                is UnitApply -> return refuse("a quantity with units")
                is NumberLit, is NameRef, is Unary -> Unit
            }
        }
        return true
    }

    private val CONTINUOUS_FUNCTIONS = setOf(
        "sin", "cos", "atan", "abs", "cbrt", "exp", "sinh", "cosh", "tanh", "asinh", "hypot", "min", "max",
    )

    /** Original endpoint samples are reused; all new samples count against the same budget. */
    private fun bisect(f: PlottableFunction, a: Sample.Defined, b: Sample.Defined, budget: SearchBudget): Candidate? {
        var lo = a.x
        var hi = b.x
        var loSign = a.y.establishedSign("root bracket endpoint sign")
        var loValue = a.y
        var hiValue = b.y
        // Absolute tolerance alone accepts a tiny-scaled pole or step. Demand
        // a substantial reduction from BOTH original endpoint residuals too.
        // The smaller endpoint sets the scale; the larger can be arbitrarily
        // large merely because one initial sample was very close to a pole.
        val loLimit = magnitudeLower(a.y) * RELATIVE_RESIDUAL_FACTOR
        val hiLimit = magnitudeLower(b.y) * RELATIVE_RESIDUAL_FACTOR
        val relativeLimit = minOf(loLimit, hiLimit)
        val residualLimit = minOf(VERIFY_TOLERANCE, relativeLimit)
        fun convergedCandidate(): Candidate? {
            if (magnitudeUpper(hi - lo) > BRACKET_TOLERANCE || magnitudeUpper(loValue) > loLimit || magnitudeUpper(hiValue) > hiLimit) return null
            val best = if (magnitudeUpper(loValue) <= magnitudeUpper(hiValue)) Sample.Defined(lo,loValue) else Sample.Defined(hi,hiValue)
            return if (magnitudeUpper(best.y) <= residualLimit) Candidate(best.x,residualLimit) else null
        }
        repeat(MAX_BISECTIONS) {
            if (!budget.check()) return null
            val mid = (lo + hi) / Num.TWO
            convergedCandidate()?.let { return it }
            if (mid <= lo || mid >= hi) {
                budget.stop("root bisection reached its numeric precision limit before resolving a bracket")
                return null
            }
            when (val sample = budget.sample(f, mid) ?: return null) {
                is Sample.Unevaluated -> {
                    budget.stop("root bisection could not evaluate its bracket: ${sample.error.message}")
                    return null
                }
                is Sample.Undefined -> {
                    budget.stop("root bisection encountered an undefined point; the sign change is not a verified root")
                    return null
                }
                is Sample.Defined -> {
                    if (sample.y.isZero) return Candidate(mid, Num.ZERO)
                    // A narrow interval enclosing zero can pass the same residual/width checks,
                    // but its nominal sign must never choose one half of the bracket.
                    if (magnitudeUpper(hi - lo) <= BRACKET_TOLERANCE && magnitudeUpper(sample.y) <= residualLimit)
                        return Candidate(mid, residualLimit)
                    val sign = sample.y.establishedSign("root bisection sample sign")
                    if (sign == loSign) { lo = mid; loValue = sample.y; loSign = sign }
                    else { hi = mid; hiValue = sample.y }
                }
            }
        }
        convergedCandidate()?.let { return it }
        budget.stop("root bisection exhausted its iteration budget before its width and relative residual checks converged; a pole or discontinuity may be present")
        return null
    }

    private fun verifyAll(
        f: PlottableFunction, candidates: List<Candidate>, method: String,
        xMin: Num, xMax: Num, budget: SearchBudget,
    ): RootResult {
        val verified = ArrayList<VerifiedRoot>()
        val rejected = ArrayList<Num>()
        for (trial in candidates) {
            if (!budget.check()) return budget.failed()
            val candidate = trial.x
            if (candidate < xMin || candidate > xMax) continue
            when (val sample = budget.sample(f, candidate) ?: return budget.failed()) {
                is Sample.Unevaluated -> return unsupported("root verification could not evaluate a candidate: ${sample.error.message}")
                is Sample.Undefined -> rejected += candidate
                is Sample.Defined -> {
                    val residual = sample.y.abs()
                    if (magnitudeUpper(sample.y) <= trial.residualLimit) verified += VerifiedRoot(candidate, residual) else rejected += candidate
                }
            }
        }
        return if (rejected.isEmpty()) RootResult.Found(verified, method)
        else RootResult.Unverified(verified, rejected,
            "substituting the candidate back into the function did not confirm a root (it may sit on an asymptote)")
    }

    /** Bounds are evidence for a residual check; a displayed point alone supplies no evidence. */
    private fun magnitudeUpper(value: Num): Num {
        val interval = value.bounds() ?: throw ArithFault.Unsupported("root residual has no established error bound")
        return Num.ofDecimal(maxOf(interval.lower.abs(), interval.upper.abs()))
    }

    private fun magnitudeLower(value: Num): Num {
        val interval = value.bounds() ?: throw ArithFault.Unsupported("root endpoint has no established error bound")
        return if (interval.containsZero) Num.ZERO else Num.ofDecimal(minOf(interval.lower.abs(), interval.upper.abs()))
    }

    private class SearchBudget(private val options: RootSearchOptions) {
        private val started = System.nanoTime()
        private var evaluations = 0
        private var failure: EvalError.Unsupported? = null
        fun stop(message: String) { if (failure == null) failure = EvalError.Unsupported(message, null) }
        fun failed(): RootResult.Failed = RootResult.Failed(checkNotNull(failure))
        fun check(): Boolean {
            if (failure != null) return false
            when {
                Thread.currentThread().isInterrupted || options.shouldStop?.invoke() == true -> stop("root search was cancelled before completion")
                (System.nanoTime() - started) / 1_000_000 >= options.maxMillis -> stop(
                    "root search exceeded its ${options.maxMillis} ms time budget before completion after $evaluations attempted evaluations")
            }
            return failure == null
        }
        fun sample(f: PlottableFunction, x: Num): Sample? {
            if (!reserveEvaluation()) return null
            val sample = f.at(x)
            return if (check()) sample else null
        }
        fun constant(f: PlottableFunction, expression: Expr): Num? {
            if (!reserveEvaluation()) return null
            val result = f.evaluateClosed(expression)
            if (!check()) return null
            val value = ((result as? CalcResult.Ok)?.value as? RealValue)?.num
            if (value == null) {
                val reason = (result as? CalcResult.Err)?.error?.message ?: "a real scalar constant is required"
                stop("root continuity check could not evaluate a constant: $reason")
            }
            return value
        }
        private fun reserveEvaluation(): Boolean {
            if (!check()) return false
            if (evaluations >= options.maxEvaluations) {
                stop("root search exhausted its evaluation budget before completion")
                return false
            }
            evaluations++
            return true
        }
    }

    private fun unsupported(message: String): RootResult.Failed = RootResult.Failed(EvalError.Unsupported(message, null))
    private inline fun guarded(block: () -> RootResult): RootResult = try { block() } catch (fault: ArithFault) {
        RootResult.Failed(when (fault) {
            is ArithFault.DivideByZero -> EvalError.DivideByZero(null, fault.message ?: "division by zero")
            is ArithFault.Domain -> EvalError.Domain(fault.message ?: "invalid root search domain", null)
            is ArithFault.Overflow -> EvalError.Overflow(fault.message ?: "root search exceeded its numeric budget", null)
            is ArithFault.Underflow -> EvalError.Underflow(fault.message ?: "root search exceeded its numeric budget", null)
            is ArithFault.Unsupported -> EvalError.Unsupported(fault.message ?: "unsupported root search", null)
        })
    }
}
