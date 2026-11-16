package com.vaibhav.calc.core

import com.vaibhav.calc.core.ast.Expr
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Evaluator
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.eval.Value
import com.vaibhav.calc.core.constants.Constants
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.graph.PlottableFunction
import com.vaibhav.calc.core.graph.RootFinder
import com.vaibhav.calc.core.graph.RootResult
import com.vaibhav.calc.core.graph.RootSearchOptions
import com.vaibhav.calc.core.matrix.Matrix
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Bases
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.toBigIntegerExactOrNull
import com.vaibhav.calc.core.parse.Parser
import com.vaibhav.calc.core.route.ExpressionRouter
import com.vaibhav.calc.core.route.RouteResult
import com.vaibhav.calc.core.solve.Solvers
import com.vaibhav.calc.core.stats.Stats
import com.vaibhav.calc.core.units.Quantity
import com.vaibhav.calc.core.units.Units

/** A fully-rendered answer: the value (real or complex) plus both display forms. */
data class Answer(
    val value: Value,
    val exact: String,
    val decimal: String,
)

/**
 * The public entry point to the engine. Pure Kotlin, zero Android (Article 1).
 *
 * ```
 * val calc = Calculator()
 * calc.evaluateAnswer("√8")          // exact = "2√2", decimal = "2.82842712475"
 * calc.evaluateAnswer("0.1 + 0.2")   // exact = "3/10", decimal = "0.3"
 * calc.evaluateAnswer("(2+3i)*i")    // exact = "-3 + 2i"
 * ```
 *
 * Parsing and evaluation are separate: [parse] yields the AST (what a future CAS
 * rewrites), [evaluate] yields an exact [Value] (real or complex), and
 * [evaluateAnswer] adds the rendered strings. `ans` updates after every success.
 */
class Calculator(var settings: Settings = Settings()) {

    val environment: Environment = Environment()

    /**
     * The unified entry point (D-016): raw input → intent → dispatch. The user
     * types `x²-5x+6=0` and gets roots; `5 km + 300 m` and gets a quantity;
     * `5 → A` and it stores. No modes. Returns `Ok | Ambiguous | Err`.
     */
    fun route(input: String): RouteResult = ExpressionRouter(settings, environment).route(input)

    /** Parse to an AST without evaluating (spec M1.3: the parser never evaluates). */
    fun parse(input: String): CalcResult<Expr> = Parser.parse(input)

    /** Parse and evaluate to a [Value], updating `ans` on success. */
    fun evaluate(input: String): CalcResult<Value> = when (val parsed = Parser.parse(input)) {
        is CalcResult.Err -> parsed
        is CalcResult.Ok -> Evaluator(settings, environment).eval(parsed.value).also {
            if (it is CalcResult.Ok) environment.setAns(it.value)
        }
    }

    /**
     * Evaluate and render both the exact and decimal forms.
     *
     * The exact form goes through [Formatter.forInput], so the typed API renders a
     * result exactly as the app will (D-025). Formatting from the value alone would
     * have this API answer `2.5` to `10/4` while the screen said `5/2` — one rule,
     * two answers (Article 12).
     */
    fun evaluateAnswer(input: String): CalcResult<Answer> =
        evaluate(input).map {
            Answer(it, Formatter.forInput(it, input, settings), Formatter.decimal(it, settings))
        }

    fun exact(value: Value): String = Formatter.exact(value, settings)
    fun decimal(value: Value): String = Formatter.decimal(value, settings)

    fun setVariable(name: String, value: Value) = environment.set(name, value)
    fun setVariable(name: String, value: Num) = environment.set(name, RealValue(value))

    // --- base conversion, radix 2..36 (spec 2.1) ---
    /** Render an integer [value] in [radix] (2..36), e.g. `toBase(255, 16) = "FF"`. */
    fun toBase(value: Num, radix: Int): CalcResult<String> = guard {
        val i = value.toBigIntegerExactOrNull()
            ?: throw IllegalArgumentException("base conversion requires an integer value")
        Bases.format(i, radix)
    }

    /** Parse an integer written in [radix] (2..36), e.g. `fromBase("FF", 16) = 255`. */
    fun fromBase(text: String, radix: Int): CalcResult<Num> = guard { Num.of(Bases.parse(text, radix)) }

    // --- equation solvers (spec D5) ---
    fun solveLinear(a: Num, b: Num): CalcResult<Solvers.LinearResult> =
        guard { Solvers.linear(a, b, settings) }

    fun solveQuadratic(a: Num, b: Num, c: Num): CalcResult<Solvers.QuadraticResult> =
        guard { Solvers.quadratic(a, b, c, settings) }

    fun solveSystem2(a1: Num, b1: Num, c1: Num, a2: Num, b2: Num, c2: Num): CalcResult<Solvers.SystemResult> =
        guard { Solvers.system2x2(a1, b1, c1, a2, b2, c2, settings) }

    fun solveSystem3(
        a1: Num, b1: Num, c1: Num, d1: Num,
        a2: Num, b2: Num, c2: Num, d2: Num,
        a3: Num, b3: Num, c3: Num, d3: Num,
    ): CalcResult<Solvers.SystemResult> =
        guard { Solvers.system3x3(a1, b1, c1, d1, a2, b2, c2, d2, a3, b3, c3, d3, settings) }

    // --- matrices (spec D7). Total ops (transpose/rank/scalar) are on Matrix itself. ---
    fun matrixAdd(a: Matrix, b: Matrix): CalcResult<Matrix> = guard { a + b }
    fun matrixSubtract(a: Matrix, b: Matrix): CalcResult<Matrix> = guard { a - b }
    fun matrixMultiply(a: Matrix, b: Matrix): CalcResult<Matrix> = guard { a * b }
    fun matrixDeterminant(m: Matrix): CalcResult<Num> = guard { m.determinant() }
    fun matrixInverse(m: Matrix): CalcResult<Matrix> = guard { m.inverse() }

    // --- statistics (spec D8) ---
    fun statsDescribe(data: List<Num>): CalcResult<Stats.Descriptive> =
        guard { Stats.describe(data, settings.computeMc) }

    fun linearRegression(points: List<Pair<Num, Num>>): CalcResult<Stats.Regression> =
        guard { Stats.linearRegression(points, settings.computeMc) }

    // --- units & dimensional analysis (spec D9) ---
    fun quantity(value: Num, unitName: String): CalcResult<Quantity> = guard { Quantity(value, unitOf(unitName)) }

    /** Convert [value] from one unit to another of the same dimension. */
    fun convert(value: Num, fromUnit: String, toUnit: String): CalcResult<Num> =
        guard { Quantity(value, unitOf(fromUnit)).convertTo(unitOf(toUnit)).value }

    fun convert(q: Quantity, toUnit: String): CalcResult<Quantity> = guard { q.convertTo(unitOf(toUnit)) }

    private fun unitOf(name: String): com.vaibhav.calc.core.units.Unit =
        Units.get(name) ?: throw ArithFault.Domain("unknown unit '$name'", "units")

    // --- graphing (spec D10) ---
    /** Parse `input` as a plottable function of [unknown] (default `x`). */
    fun plot(input: String, unknown: String = "x"): CalcResult<PlottableFunction> =
        PlottableFunction.of(input, settings, environment, unknown)

    /** Verified roots over [xMin, xMax]. Never returns a root it could not substitute back. */
    fun graphRoots(
        f: PlottableFunction,
        xMin: Num,
        xMax: Num,
        samples: Int = RootFinder.DEFAULT_SAMPLES,
        options: RootSearchOptions = RootSearchOptions(),
    ): RootResult = RootFinder.roots(f, xMin, xMax, settings, samples, options)

    /** Verified intersections of two functions — a root of their difference. */
    fun graphIntersections(
        f: PlottableFunction,
        g: PlottableFunction,
        xMin: Num,
        xMax: Num,
        samples: Int = RootFinder.DEFAULT_SAMPLES,
        options: RootSearchOptions = RootSearchOptions(),
    ): RootResult = RootFinder.intersections(f, g, xMin, xMax, settings, samples, options)

    // --- constants (spec 9.4 / 9.5) ---
    fun physicalConstant(symbol: String): Constants.Physical? = Constants.physical(symbol)
    fun mathConstant(name: String): Num? = Constants.math(name, settings.computeMc)

    private inline fun <T> guard(block: () -> T): CalcResult<T> = try {
        CalcResult.Ok(block())
    } catch (f: ArithFault) {
        CalcResult.Err(faultToError(f))
    } catch (e: IllegalArgumentException) {
        // e.g. Bases.parse / an invalid radix — surface as a typed error, never a throw.
        CalcResult.Err(EvalError.Domain(e.message ?: "invalid argument", null))
    }

    private fun faultToError(f: ArithFault): EvalError = when (f) {
        is ArithFault.DivideByZero -> EvalError.DivideByZero(null, f.message ?: "division by zero")
        is ArithFault.Domain -> EvalError.Domain(f.message ?: "domain error", null, f.function)
        is ArithFault.Overflow -> EvalError.Overflow(f.message ?: "result too large", null)
        is ArithFault.Underflow -> EvalError.Underflow(f.message ?: "result too small", null)
        is ArithFault.Unsupported -> EvalError.Unsupported(f.message ?: "unsupported", null)
    }
}
