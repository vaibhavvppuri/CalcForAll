package com.vaibhav.calc.api

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.constants.Constants
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.format.OutcomeFormatter
import com.vaibhav.calc.core.graph.*
import com.vaibhav.calc.core.matrix.Matrix
import com.vaibhav.calc.core.matrix.Eigen
import com.vaibhav.calc.core.name.Functions
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import com.vaibhav.calc.core.symbolic.Symbolic
import com.vaibhav.calc.core.symbolic.NumericCalculus
import com.vaibhav.calc.core.symbolic.IntegrationOptions
import com.vaibhav.calc.core.units.Units
import io.ktor.http.HttpStatusCode

internal data class ApiResponse(val body: Map<String, Any?>, val status: HttpStatusCode = HttpStatusCode.OK)

private fun numberUncertainty(n: Num): Map<String, Any?> = when (n) {
    is Dec -> n.absoluteError?.let { mapOf("kind" to "bounded", "absoluteError" to it.stripTrailingZeros().toString()) }
        ?: mapOf("kind" to "unknown")
    else -> mapOf("kind" to "exact")
}


/** A new service and calculator are created for each request. Nothing persists between callers. */
internal class ApiService(internal val request: ApiRequest) {
    internal val settings = request.settings()
    internal val calc = Calculator(settings)

    fun execute(operation: String): ApiResponse = when (operation) {
        "evaluate" -> evaluate()
        "convert" -> convert()
        "matrix" -> matrix()
        "statistics" -> statistics()
        "regression" -> regression()
        "base" -> base()
        "graph/sample" -> sample()
        "graph/roots" -> roots(false)
        "graph/intersections" -> roots(true)
        "graph/surface" -> surface()
        "symbolic" -> symbolic()
        "integrate" -> integrate()
        else -> if (operation in ApiTools.operations) ApiTools(this).execute(operation) else badRequest("unknown operation")
    }

    private fun evaluate(): ApiResponse {
        request.only("expr", "settings", "registers")
        loadRegisters()
        val input = checkedExpression(request.string("expr"))
        return when (val result = calc.route(input)) {
            is RouteResult.Ok -> success("expr" to input, "result" to outcome(result.outcome, input))
            is RouteResult.Err -> mathError(result.error)
            is RouteResult.Ambiguous -> ApiResponse(linkedMapOf(
                "ok" to false, "kind" to "AMBIGUOUS", "error" to "choose an explicit interpretation", "expr" to input,
                "candidates" to result.candidates.map { mapOf("reading" to it.reading, "result" to outcome(it.outcome, input)) },
            ), HttpStatusCode.Conflict)
        }
    }

    private fun convert(): ApiResponse {
        request.only("value", "from", "to", "settings")
        val from = request.string("from", max = 64)
        val to = request.string("to", max = 64)
        val result = calc.convert(number(request.value("value")), from, to).required()
        return success("from" to from, "to" to to, "result" to numberResult(result))
    }

    private fun matrix(): ApiResponse {
        request.only("operation", "a", "b", "scalar", "settings")
        val operation = request.string("operation", max = 32)
        if (operation !in setOf("add", "subtract", "multiply", "determinant", "inverse", "transpose", "rank", "scale", "eigenvalues", "characteristicPolynomial")) {
            badRequest("unknown matrix operation '$operation'")
        }
        val needsB = operation in setOf("add", "subtract", "multiply")
        if (!needsB && "b" in request.fields) badRequest("'$operation' does not take matrix 'b'")
        if (operation != "scale" && "scalar" in request.fields) badRequest("'$operation' does not take 'scalar'")
        val a = matrixInput(request.value("a"), "a")
        val b = if (needsB) matrixInput(request.value("b"), "b") else null
        val result: Any = when (operation) {
            "add" -> matrixResult(calc.matrixAdd(a, b!!).required())
            "subtract" -> matrixResult(calc.matrixSubtract(a, b!!).required())
            "multiply" -> matrixResult(calc.matrixMultiply(a, b!!).required())
            "determinant" -> numberResult(calc.matrixDeterminant(a).required())
            "inverse" -> matrixResult(calc.matrixInverse(a).required())
            "transpose" -> matrixResult(a.transpose())
            "rank" -> a.rank()
            "scale" -> matrixResult(a.scalarTimes(number(request.value("scalar"))))
            "eigenvalues" -> Eigen.eigenvalues(a, settings).let {
                mapOf("values" to it.values.map(::valueResult), "polynomial" to it.polynomial.map(::numberResult),
                    "coefficientOrder" to "ascending", "repeatedValues" to "listed once", "eigenvectors" to false)
            }
            "characteristicPolynomial" -> mapOf("coefficients" to Eigen.characteristicPolynomial(a).map(::numberResult),
                "coefficientOrder" to "ascending", "convention" to "det(lambda*I-A)")
            else -> error("operation was validated")
        }
        return success("operation" to operation, "result" to result)
    }

    private fun statistics(): ApiResponse {
        request.only("data", "settings")
        val data = request.array("data")
        checkCount(data.size, 1, "data")
        val s = calc.statsDescribe(data.map(::number)).required()
        return success("result" to linkedMapOf(
            "count" to s.count, "sum" to numberResult(s.sum), "mean" to numberResult(s.mean),
            "minimum" to numberResult(s.min), "maximum" to numberResult(s.max), "range" to numberResult(s.range),
            "median" to numberResult(s.median), "populationVariance" to numberResult(s.variancePopulation),
            "sampleVariance" to s.varianceSample?.let(::numberResult),
            "populationStandardDeviation" to numberResult(s.stdevPopulation),
            "sampleStandardDeviation" to s.stdevSample?.let(::numberResult),
        ))
    }

    private fun regression(): ApiResponse {
        request.only("points", "settings")
        val points = request.array("points")
        checkCount(points.size, 2, "points")
        val pairs = points.mapIndexed { i, p ->
            val values = (p as? JsonValue.Arr)?.values ?: badRequest("points[$i] must be [x, y]")
            if (values.size != 2) badRequest("points[$i] must contain exactly two values")
            number(values[0]) to number(values[1])
        }
        val r = calc.linearRegression(pairs).required()
        return success("result" to mapOf("slope" to numberResult(r.slope), "intercept" to numberResult(r.intercept),
            "r" to r.r?.let(::numberResult), "rSquared" to r.r2?.let(::numberResult)))
    }

    private fun base(): ApiResponse {
        request.only("value", "from", "to")
        val input = request.string("value", max = 512)
        val from = request.int("from", 10, 2..36)
        val to = request.int("to", 16, 2..36)
        val value = calc.fromBase(input, from).required()
        return success("from" to from, "to" to to, "result" to calc.toBase(value, to).required(),
            "decimal" to Formatter.exact(value, Settings()))
    }

    private fun sample(): ApiResponse {
        request.only("expr", "variable", "xMin", "xMax", "samples", "settings", "registers")
        loadRegisters()
        val input = checkedExpression(request.string("expr"))
        val variable = variable()
        val (lo, hi) = range()
        val count = request.int("samples", 201, 2..ApiLimits.GRAPH_SAMPLES)
        val samples = Sampler(calc.plot(input, variable).required()).samples(lo, hi, count)
        return success("variable" to variable, "xMin" to numberResult(lo), "xMax" to numberResult(hi),
            "samples" to samples.map(::sampleResult),
            "counts" to mapOf("defined" to samples.count { it is Sample.Defined },
                "undefined" to samples.count { it is Sample.Undefined }, "unevaluated" to samples.count { it is Sample.Unevaluated }))
    }

    private fun roots(intersections: Boolean): ApiResponse {
        request.only("expr", "other", "variable", "xMin", "xMax", "samples", "settings", "registers")
        if (!intersections && "other" in request.fields) badRequest("use /v1/graph/intersections for two expressions")
        loadRegisters()
        val input = checkedExpression(request.string("expr"))
        val variable = variable()
        val (lo, hi) = range()
        val count = request.int("samples", RootFinder.DEFAULT_SAMPLES, 2..ApiLimits.ROOT_SAMPLES)
        val f = calc.plot(input, variable).required()
        val result = if (intersections) {
            val g = calc.plot(checkedExpression(request.string("other")), variable).required()
            calc.graphIntersections(f, g, lo, hi, count)
        } else calc.graphRoots(f, lo, hi, count)
        return when (result) {
            is RootResult.Found -> success("state" to "verified", "method" to result.method,
                "approximate" to (result.method.startsWith("numeric") || result.roots.any { rootIsApproximate(it) }),
                "roots" to result.roots.map { rootResult(it, result.method.startsWith("numeric")) },
                "scope" to "searched interval only; numeric scanning is not a completeness proof")
            is RootResult.Unverified -> ApiResponse(linkedMapOf(
                "ok" to false, "kind" to "UNVERIFIED", "state" to "unverified", "error" to result.reason,
                "approximate" to true, "verified" to result.verified.map { rootResult(it) },
                "candidates" to result.candidates.map(::estimatedNumberResult),
            ), HttpStatusCode.UnprocessableEntity)
            is RootResult.Failed -> mathError(result.error)
        }
    }

    private fun surface(): ApiResponse {
        request.only("expr", "xMin", "xMax", "yMin", "yMax", "columns", "rows", "settings", "registers")
        loadRegisters()
        val input = checkedExpression(request.string("expr"))
        fun bound(name: String, default: Num) = request.fields[name]?.let(::number) ?: default
        val defaults = SurfaceBounds.DEFAULT
        val bounds = SurfaceBounds(bound("xMin", defaults.xMin), bound("xMax", defaults.xMax),
            bound("yMin", defaults.yMin), bound("yMax", defaults.yMax))
        val columns = request.int("columns", 21, 2..ApiLimits.SURFACE_GRID_SIDE)
        val rows = request.int("rows", 21, 2..ApiLimits.SURFACE_GRID_SIDE)
        val surface = PlottableSurface.of(input, settings, calc.environment).required()
        val grid = SurfaceSampler.sample(surface, bounds, columns, rows, maxMillis = 4_000).required()
        return success("columns" to grid.columns, "rows" to grid.rows, "order" to "row-major",
            "angleMode" to grid.angleMode.name, "minZ" to grid.minZ?.let(::numberResult), "maxZ" to grid.maxZ?.let(::numberResult),
            "counts" to mapOf("defined" to grid.definedCount, "undefined" to grid.undefinedCount, "unevaluated" to grid.unevaluatedCount),
            "warnings" to grid.warnings, "samples" to grid.samples.map { point ->
                val coordinates = mapOf("x" to numberResult(point.x), "y" to numberResult(point.y))
                coordinates + when (point) {
                    is SurfaceSample.Defined -> mapOf("state" to "defined", "z" to numberResult(point.z))
                    is SurfaceSample.Undefined -> mapOf("state" to "undefined", "kind" to point.error.kind.name, "error" to point.error.detail)
                    is SurfaceSample.Unevaluated -> mapOf("state" to "unevaluated", "kind" to point.error.kind.name, "error" to point.error.detail)
                }
            })
    }

    private fun symbolic(): ApiResponse {
        request.only("operation", "expr", "variable")
        val operation = request.string("operation", max = 32)
        val input = checkedExpression(request.string("expr"))
        val variable = variable()
        val result = when (operation) {
            "simplify" -> Symbolic.simplify(input, variable)
            "expand" -> Symbolic.expand(input, variable)
            "factor" -> Symbolic.factor(input, variable)
            "differentiate" -> Symbolic.differentiate(input, variable)
            "integrate" -> Symbolic.integrate(input, variable)
            else -> badRequest("symbolic operation must be simplify, expand, factor, differentiate, or integrate")
        }.required()
        return success("result" to mapOf("expression" to result.expression, "display" to result.display,
            "operation" to result.operation, "variable" to result.variable, "steps" to result.steps,
            "conditions" to result.conditions, "angleMode" to result.angleMode,
            "constantOfIntegration" to result.constantOfIntegration, "domainRestricted" to result.domainRestricted))
    }

    private fun integrate(): ApiResponse {
        request.only("expr", "lower", "upper", "variable", "absoluteTolerance", "relativeTolerance", "maxEvaluations", "precision", "settings")
        val input = checkedExpression(request.string("expr"))
        val variable = variable()
        val lower = number(request.value("lower"))
        val upper = number(request.value("upper"))
        fun tolerance(name: String): Num {
            val value = request.fields[name]?.let(::number) ?: Num.ofDecimal("1e-10")
            if (value < Num.ofDecimal("1e-20") || value > Num.ofDecimal("0.01")) {
                badRequest("'$name' must be from 1e-20 to 0.01")
            }
            return value
        }
        val options = IntegrationOptions(
            absoluteTolerance = tolerance("absoluteTolerance"),
            relativeTolerance = tolerance("relativeTolerance"),
            maxEvaluations = request.int("maxEvaluations", ApiLimits.INTEGRATION_EVALUATIONS, 128..ApiLimits.INTEGRATION_EVALUATIONS),
            computePrecision = request.int("precision", 48, 32..50),
            maxDepth = 20,
            maxMillis = 4_000,
        )
        val estimate = NumericCalculus.integrate(input, lower, upper, variable, options).required()
        return success("variable" to variable, "result" to mapOf(
            "value" to estimatedNumberResult(estimate.value), "estimatedError" to estimatedNumberResult(estimate.estimatedError),
            "evaluations" to estimate.evaluations, "method" to estimate.method,
            "approximate" to estimate.approximate, "warnings" to estimate.warnings, "angleMode" to estimate.angleMode,
        ))
    }

    private fun variable(): String {
        val variable = request.string("variable", "x", 16)
        if (!ReservedNames.isUnknown(variable)) badRequest("variable must be x, y, or z")
        return ReservedNames.canonicalUnknown(variable)
    }

    private fun range(): Pair<Num, Num> {
        val lo = request.fields["xMin"]?.let(::number) ?: Num.of(-66, 10)
        val hi = request.fields["xMax"]?.let(::number) ?: Num.of(66, 10)
        if (lo >= hi) badRequest("xMin must be less than xMax")
        if (lo.abs() > Num.of(1_000_000_000_000L) || hi.abs() > Num.of(1_000_000_000_000L)) {
            limit("graph bounds must have magnitude at most 1e12")
        }
        if (hi - lo < Num.of(1, 1_000_000_000_000L)) limit("graph range width must be at least 1e-12")
        return lo to hi
    }

    internal fun loadRegisters() {
        val raw = request.fields["registers"] ?: return
        val registers = (raw as? JsonValue.Obj)?.fields ?: badRequest("registers must be an object")
        for ((name, value) in registers) {
            if (!ReservedNames.isRegister(name)) badRequest("register names must be A, B, C, D, E, F, or M")
            calc.setVariable(name, number(value))
        }
    }

    internal fun number(value: JsonValue): Num {
        val text = when (value) {
            is JsonValue.Str -> value.value
            is JsonValue.Number -> value.text
            else -> badRequest("numeric values must be JSON numbers or real-expression strings")
        }
        if (text.length > 256) limit("a numeric value exceeds 256 characters")
        // Independent calculators mean cells, register definitions and data cannot accidentally consume one another's ans.
        val evaluated = Calculator(settings).evaluate(checkedExpression(text)).required()
        return (evaluated as? RealValue)?.num ?: badRequest("a dimensionless real number is required")
    }

    private fun matrixInput(value: JsonValue, name: String): Matrix {
        val rows = (value as? JsonValue.Arr)?.values ?: badRequest("'$name' must be an array of rows")
        if (rows.isEmpty() || rows.size > ApiLimits.MATRIX_DIMENSION) badRequest("'$name' must have 1..${ApiLimits.MATRIX_DIMENSION} rows")
        val data = rows.mapIndexed { i, row ->
            (row as? JsonValue.Arr)?.values ?: badRequest("$name[$i] must be an array")
        }
        val columns = data.first().size
        if (columns !in 1..ApiLimits.MATRIX_DIMENSION || data.any { it.size != columns }) {
            badRequest("'$name' must be rectangular, with 1..${ApiLimits.MATRIX_DIMENSION} columns")
        }
        return Matrix.of(data.map { it.map(::number) })
    }

    private fun checkCount(size: Int, minimum: Int, name: String) {
        if (size < minimum) badRequest("'$name' needs at least $minimum values")
        if (size > ApiLimits.DATA_POINTS) limit("'$name' exceeds ${ApiLimits.DATA_POINTS} values")
    }

    internal fun numberResult(n: Num): Map<String, Any?> = mapOf(
        "exact" to Formatter.input(n), "decimal" to Formatter.decimal(n, settings), "approximate" to (n is Dec),
        "input" to Formatter.reusableInput(n), "uncertainty" to numberUncertainty(n))

    /** A sampling estimate's coordinate enclosure is not a bound on the mathematical root/limit. */
    internal fun estimatedNumberResult(n: Num): Map<String, Any?> = numberResult(n) + mapOf(
        "approximate" to true, "input" to "approx(${Formatter.input(n)})", "uncertainty" to mapOf("kind" to "unknown"))

    private fun valueResult(value: Value): Map<String, Any?> {
        if (value is RealValue) return numberResult(value.num) + ("kind" to "real")
        val fields = mapOf(
            "kind" to when (value) { is ComplexValue -> "complex"; is QuantityValue -> "quantity"; else -> error("real handled above") },
            "exact" to Formatter.input(value), "input" to Formatter.reusableInput(value),
            "decimal" to Formatter.decimal(value, settings),
            "approximate" to when (value) {
                is ComplexValue -> value.cx.re is Dec || value.cx.im is Dec
                is QuantityValue -> value.q.value is Dec
                else -> false
            })
        return fields + when (value) {
            is ComplexValue -> mapOf("real" to numberResult(value.cx.re), "imaginary" to numberResult(value.cx.im))
            is QuantityValue -> mapOf("coefficient" to numberResult(value.q.value))
            else -> error("real handled above")
        }
    }

    private fun matrixResult(m: Matrix): Map<String, Any?> = mapOf("rows" to m.rows, "columns" to m.cols,
        "cells" to (0 until m.rows).map { r -> (0 until m.cols).map { c -> numberResult(m.get(r, c)) } })

    private fun rootIsApproximate(r: VerifiedRoot): Boolean = r.x is Dec || !r.residual.isZero

    private fun rootResult(r: VerifiedRoot, numericMethod: Boolean = false): Map<String, Any?> {
        // A bisection coordinate may be stored as an exact Rational while only approximating the root.
        val approximate = numericMethod || rootIsApproximate(r)
        return mapOf("x" to if (approximate) estimatedNumberResult(r.x) else numberResult(r.x),
            "residual" to numberResult(r.residual), "approximate" to approximate)
    }

    internal fun sampleResult(s: Sample): Map<String, Any?> = when (s) {
        is Sample.Defined -> mapOf("state" to "defined", "x" to numberResult(s.x), "y" to numberResult(s.y))
        is Sample.Undefined -> mapOf("state" to "undefined", "x" to numberResult(s.x), "kind" to s.cause.kind.name, "error" to s.cause.detail)
        is Sample.Unevaluated -> mapOf("state" to "unevaluated", "x" to numberResult(s.x), "kind" to s.error.kind.name, "error" to s.error.detail)
    }

    private fun outcome(value: Outcome, input: String): Map<String, Any?> {
        val fields: Map<String, Any?> = when (value) {
            is Outcome.Scalar -> mapOf("kind" to "scalar", "value" to valueResult(value.value))
            is Outcome.Stored -> mapOf("kind" to "stored", "register" to value.register, "value" to valueResult(value.value))
            is Outcome.Verdict -> mapOf("kind" to "verdict", "holds" to value.holds)
            is Outcome.Roots -> mapOf("kind" to "roots", "variable" to value.unknown, "roots" to value.roots.map { valueResult(it) })
            is Outcome.SystemSolution -> mapOf("kind" to "system", "values" to value.unknowns.zip(value.values).associate { (name, n) -> name to numberResult(n) })
            is Outcome.Identity -> mapOf("kind" to "identity", "variable" to value.unknown)
            is Outcome.NoSolution -> mapOf("kind" to "noSolution", "variable" to value.unknown)
        }
        val rendered = OutcomeFormatter.render(value, input, settings)
        return fields + mapOf("display" to rendered.primary, "decimal" to rendered.decimal,
            "approximate" to rendered.approximate, "steps" to rendered.steps)
    }

    private fun success(vararg fields: Pair<String, Any?>) = ApiResponse(linkedMapOf("ok" to true, *fields))

    companion object {
        val operations = listOf("evaluate", "convert", "matrix", "statistics", "regression", "base", "graph/sample", "graph/roots", "graph/intersections", "graph/surface", "symbolic", "integrate") + ApiTools.operations

        fun capabilities(): Map<String, Any?> = mapOf("ok" to true, "apiVersion" to 1, "releaseVersion" to "2.0", "stateful" to false,
            "requiresNetwork" to false, "numberEncoding" to "strings preserve exact and arbitrary-precision values",
            "operations" to operations, "limits" to mapOf("bodyBytes" to ApiLimits.BODY_BYTES,
                "expressionCharacters" to ApiLimits.EXPRESSION_CHARS, "expressionNodes" to ApiLimits.EXPRESSION_NODES,
                "expressionDepth" to ApiLimits.EXPRESSION_DEPTH, "matrixDimension" to ApiLimits.MATRIX_DIMENSION,
                "dataPoints" to ApiLimits.DATA_POINTS, "graphSamples" to ApiLimits.GRAPH_SAMPLES,
                "rootSamples" to ApiLimits.ROOT_SAMPLES, "computePrecision" to 50, "displayPrecision" to 32,
                "rootEvaluations" to RootSearchOptions().maxEvaluations, "rootMillis" to RootSearchOptions().maxMillis,
                "concurrentCalculations" to ApiLimits.CONCURRENT_CALCULATIONS,
                "integrationEvaluations" to ApiLimits.INTEGRATION_EVALUATIONS, "surfaceGridSide" to ApiLimits.SURFACE_GRID_SIDE,
                "tableRows" to ApiTools.TABLE_ROWS, "sumTerms" to ApiTools.SUM_TERMS,
                "productTerms" to ApiTools.PRODUCT_TERMS, "seriesMillis" to ApiTools.SERIES_MILLIS))

        fun functions(): Map<String, Any?> = mapOf("ok" to true, "functions" to Functions.ALL.map {
            mapOf("name" to it.name, "displayName" to Functions.displayName(it.name), "arities" to it.arities.sorted(),
                "kind" to it.kind.name, "aliases" to it.aliases, "summary" to it.summary)
        })

        fun constants(): Map<String, Any?> {
            val settings = Settings(displayPrecision = 24)
            val physical = Constants.allPhysical()
            return mapOf("ok" to true, "physical" to physical.map { p -> mapOf(
                "name" to p.name, "symbol" to p.symbol, "value" to Formatter.exact(p.value, settings),
                "uncertainty" to Formatter.exact(p.uncertainty, settings), "unit" to p.unit,
                "dimensions" to p.dimension.exponents, "exact" to p.isExact,
                "approximate" to (p.value is Dec), "input" to Formatter.reusableInput(p.toValue()),
                "evaluationUncertainty" to numberUncertainty(p.value),
                "uncertaintyMeaning" to if (p.isExact) "exact definition" else "standard statistical uncertainty, not a hard error bound") },
                "mathematical" to Constants.SYMBOLS.filter { Constants.physical(it) == null }.sorted().map { name ->
                    val n = Constants.math(name, settings.computeMc) ?: error("constant registry mismatch")
                    mapOf("name" to name, "exact" to Formatter.exact(n, settings), "decimal" to Formatter.decimal(n, settings),
                        "approximate" to (n is Dec), "input" to Formatter.reusableInput(n), "uncertainty" to numberUncertainty(n))
                })
        }

        fun units(): Map<String, Any?> = mapOf("ok" to true, "units" to Units.names().sorted().map { name ->
            val u = checkNotNull(Units.get(name))
            mapOf("name" to name, "canonical" to u.name, "dimension" to u.dimension.siSymbol(),
                "dimensions" to u.dimension.exponents, "factor" to Formatter.exact(u.factor),
                "offset" to Formatter.exact(u.offset), "affine" to u.isAffine)
        })
    }
}
