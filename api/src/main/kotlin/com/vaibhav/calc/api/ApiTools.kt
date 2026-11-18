package com.vaibhav.calc.api

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.everyday.*
import com.vaibhav.calc.core.graph.*
import com.vaibhav.calc.core.matrix.Vectors
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.stats.*
import com.vaibhav.calc.core.symbolic.Limits
import com.vaibhav.calc.core.symbolic.Series
import java.math.BigInteger
import java.time.LocalDate

/** Typed adapters over shared core modules. No Android formulas, clocks or mutable cross-request state. */
internal class ApiTools(private val service: ApiService) {
    private val request get() = service.request
    private val settings get() = service.settings
    private val mc get() = settings.computeMc
    // These adapters have no interval-valued domain/classification contracts. Refuse uncertain
    // parameters rather than discard their uncertainty in a point-only tool.
    private fun point(value: Num): Num {
        if (value is Dec && value.absoluteError?.signum() != 0)
            throw ArithFault.Unsupported("this tool needs exact parameters or explicitly zero-error estimates; uncertain parameters are not supported")
        return value
    }
    private fun n(name: String): Num = point(service.number(request.value(name)))
    private fun pointValue(value: JsonValue): Num = point(service.number(value))
    private fun result(n: Num): Map<String, Any?> = service.numberResult(n)
    private fun nums(vararg fields: Pair<String, Num>): Map<String, Any?> =
        fields.associate { (name, value) -> name to result(value) }

    fun execute(endpoint: String): ApiResponse {
        val family = schemas[endpoint] ?: badRequest("unknown operation")
        val operation = if (family.containsKey("")) "" else request.string("operation", max = 40)
        val schema = family[operation] ?: badRequest("unknown $endpoint operation '$operation'")
        val common = if (operation.isEmpty()) listOf("settings") else listOf("operation", "settings")
        request.only(*(common + schema.required + schema.optional).toTypedArray())
        schema.required.forEach { request.value(it) }
        val value: Any = when (endpoint) {
            "finance" -> finance(operation)
            "dates" -> dates(operation)
            "duration" -> duration(operation)
            "bmi" -> BodyMass.index(n("weightKg"), n("heightCm")).let {
                mapOf("index" to result(it.index), "band" to it.band.name, "label" to it.band.label,
                    "scope" to "General adult screening bands; not a diagnosis or age/population-adjusted assessment.")
            }
            "words" -> words(operation)
            "vector" -> vector(operation)
            "number-theory" -> numberTheory(operation)
            "probability" -> probability(operation)
            "curve-fit" -> curveFit(operation)
            "quartiles" -> quartiles()
            "taylor" -> taylor()
            "limit" -> limitEstimate()
            "table" -> table()
            "series" -> series(operation)
            "bitwise" -> bitwise(operation)
            else -> badRequest("unknown operation")
        }
        return ApiResponse(linkedMapOf("ok" to true, "operation" to operation.ifEmpty { endpoint }, "result" to value))
    }

    private fun integer(name: String, low: Int, high: Int): Int {
        request.value(name)
        return request.int(name, low, low..high)
    }

    /** Exact whole values only: rounded imported estimates cannot certify discrete integer operations. */
    private fun whole(name: String): BigInteger {
        val value = service.number(request.value(name))
        if (value !is Rational || value.den != BigInteger.ONE) badRequest("'$name' must be an exact whole number")
        return value.num
    }

    private fun array(name: String, min: Int, max: Int): List<JsonValue> {
        val values = request.array(name)
        if (values.size < min) badRequest("'$name' needs at least $min values")
        if (values.size > max) limit("'$name' exceeds $max values")
        return values
    }

    private fun finance(op: String): Any {
        fun months(max: Int = Finance.MAX_PERIODS) = integer("months", 1, max)
        return when (op) {
            "loan" -> Finance.loan(n("principal"), n("annualRate"), months(), mc).let {
                nums("payment" to it.payment, "totalPaid" to it.totalPaid, "totalInterest" to it.totalInterest)
            }
            "compound" -> Finance.compound(n("principal"), n("annualRate"), n("years"), integer("compoundsPerYear", 1, 365), mc).let {
                nums("amount" to it.amount, "interest" to it.interest)
            }
            "simple" -> Finance.simple(n("principal"), n("annualRate"), n("years")).let {
                nums("amount" to it.amount, "interest" to it.interest)
            }
            "sip" -> Finance.monthlyInvestment(n("monthly"), n("annualRate"), months(), mc).let {
                nums("futureValue" to it.futureValue, "invested" to it.invested, "gain" to it.gain) +
                    ("paymentTiming" to "startOfMonth")
            }
            "schedule" -> Finance.schedule(n("principal"), n("annualRate"), months(Finance.MAX_SCHEDULE_MONTHS), mc).map {
                nums("interest" to it.interest, "principal" to it.principal, "balance" to it.balance) + ("year" to it.year)
            }
            "affordableLoan" -> Finance.affordableLoan(n("payment"), n("annualRate"), months(), mc).let {
                nums("principal" to it.principal, "totalPaid" to it.totalPaid, "totalInterest" to it.totalInterest)
            }
            "savingsGoal" -> Finance.savingsGoal(n("target"), n("annualRate"), months(), mc).let {
                nums("monthly" to it.monthly, "invested" to it.invested, "gain" to it.gain) + ("paymentTiming" to "startOfMonth")
            }
            "inflation" -> Finance.inflation(n("amount"), n("annualRate"), n("years"), mc).let {
                nums("futurePrice" to it.futurePrice, "buyingPower" to it.buyingPower)
            }
            "addTax", "removeTax" -> {
                val amount = n("amount"); val rate = n("rate")
                (if (op == "addTax") Finance.addTax(amount, rate) else Finance.removeTax(amount, rate)).let {
                    nums("net" to it.net, "tax" to it.tax, "gross" to it.gross)
                }
            }
            "discount" -> Finance.discount(n("price"), n("percent")).let {
                nums("saving" to it.saving, "finalPrice" to it.finalPrice)
            }
            "percentChange" -> Finance.percentChange(n("from"), n("to")).let {
                nums("difference" to it.difference, "percent" to it.percent)
            }
            "portion" -> result(Finance.portion(n("percent"), n("amount")))
            "percentOf" -> result(Finance.percentOf(n("part"), n("whole")))
            "split" -> Finance.split(n("bill"), n("tipPercent"), integer("people", 1, 10_000)).let {
                nums("tip" to it.tip, "total" to it.total, "perPerson" to it.perPerson)
            }
            "shares" -> {
                val ratio = array("ratio", 2, Finance.MAX_RATIO_PARTS)
                Finance.shareByRatio(n("amount"), ratio.map(::pointValue)).let {
                    mapOf("parts" to it.parts.map(::result), "simplestRatio" to it.simplest.map(BigInteger::toString))
                }
            }
            "unitPrices" -> Finance.compareUnitPrices(n("priceA"), n("quantityA"), n("priceB"), n("quantityB")).let {
                nums("unitPriceA" to it.unitPriceA, "unitPriceB" to it.unitPriceB, "savingPercent" to it.savingPercent)
            }
            "fuel" -> Finance.fuelCost(n("distance"), n("distancePerUnitFuel"), n("pricePerUnitFuel")).let {
                nums("fuel" to it.fuel, "cost" to it.cost)
            }
            "proportion" -> result(Finance.proportion(n("a"), n("b"), n("c")))
            else -> badRequest("unknown finance operation")
        }
    }

    private fun date(name: String): LocalDate = Dates.parse(request.string(name, max = 32))
    private fun span(value: Dates.Span): Map<String, Any?> = mapOf(
        "totalDays" to value.totalDays.toString(), "weeks" to value.weeks.toString(),
        "weekRemainder" to value.weekRemainder, "years" to value.years,
        "months" to value.months, "days" to value.days, "forward" to value.forward)
    private fun dates(op: String): Any = when (op) {
        "between" -> span(Dates.between(date("from"), date("to")))
        "shift" -> Dates.shift(date("date"), integer("days", -Dates.MAX_SHIFT_DAYS.toInt(), Dates.MAX_SHIFT_DAYS.toInt()).toLong()).let {
            mapOf("date" to it.toString(), "weekday" to Dates.weekday(it))
        }
        "age" -> {
            val birth = date("birth"); val asOf = date("asOf")
            span(Dates.age(birth, asOf)) + mapOf("daysToNextBirthday" to Dates.daysToNextBirthday(birth, asOf).toString(),
                "leapBirthdayConvention" to "March 1 in non-leap years")
        }
        "weekday" -> mapOf("weekday" to Dates.weekday(date("date")))
        else -> badRequest("unknown date operation")
    }

    private fun duration(op: String): Any {
        fun parse(name: String) = Durations.parse(request.string(name, max = 32))
        val seconds = when (op) {
            "add" -> Math.addExact(parse("a"), parse("b"))
            "subtract" -> Math.subtractExact(parse("a"), parse("b"))
            "between" -> Durations.between(Durations.parseClock(request.string("from", max = 32)),
                Durations.parseClock(request.string("to", max = 32)))
            "format" -> {
                val n = whole("seconds")
                if (n.abs() > BigInteger.valueOf(MAX_DURATION_SECONDS)) limit("duration exceeds $MAX_DURATION_SECONDS seconds")
                n.toLong()
            }
            else -> badRequest("unknown duration operation")
        }
        return mapOf("seconds" to seconds.toString(), "clock" to Durations.format(seconds), "breakdown" to Durations.breakdown(seconds))
    }

    private fun words(op: String): Any = mapOf("text" to when (op) {
        "indian" -> NumberWords.indian(whole("value"))
        "international" -> NumberWords.international(whole("value"))
        "rupees" -> NumberWords.rupees(whole("rupees"), integer("paise", 0, 99))
        else -> badRequest("unknown words operation")
    })

    private fun vector(op: String): Any {
        val aRaw = array("a", 1, Vectors.MAX_DIMENSION)
        val bRaw = if (op == "length") null else array("b", 1, Vectors.MAX_DIMENSION)
        if (bRaw != null && aRaw.size != bRaw.size) badRequest("vectors must have equal dimensions")
        if (op == "cross" && aRaw.size != 3) badRequest("cross product needs three components")
        val a = aRaw.map(::pointValue); val b = bRaw?.map(::pointValue)
        return when (op) {
            "dot" -> result(Vectors.dot(a, b!!))
            "cross" -> Vectors.cross(a, b!!).map(::result)
            "length" -> result(Vectors.length(a, mc))
            "projection" -> Vectors.projection(a, b!!).map(::result)
            "angle" -> {
                val cosine = Vectors.cosine(a, b!!, mc)
                val angleCalc = Calculator(settings).apply { setVariable("A", cosine) }
                val angle = (angleCalc.evaluate("acos(A)").required() as RealValue).num
                result(angle) + ("angleMode" to settings.angle.name)
            }
            else -> badRequest("unknown vector operation")
        }
    }

    private fun numberTheory(op: String): Any = when (op) {
        "factorize" -> NumberTheory.factorize(whole("value")).map { (p, e) -> mapOf("prime" to p.toString(), "exponent" to e) }
        "isPrime" -> mapOf("prime" to NumberTheory.isPrime(whole("value")), "method" to "deterministic trial division")
        "divisors" -> NumberTheory.divisors(whole("value")).map(BigInteger::toString)
        "gcd", "lcm" -> {
            val input = array("values", 2, NumberTheory.MAX_VALUES).map {
                val value = service.number(it)
                if (value !is Rational || value.den != BigInteger.ONE) badRequest("values must be exact whole numbers")
                value.num
            }
            mapOf("value" to (if (op == "gcd") NumberTheory.gcd(input) else NumberTheory.lcm(input)).toString())
        }
        else -> badRequest("unknown number-theory operation")
    }

    private fun probability(op: String): Any {
        fun tail(value: Distributions.Tail) = nums("exactly" to value.exactly, "atMost" to value.atMost, "above" to value.above)
        return when (op) {
            "binomial" -> tail(Distributions.binomial(integer("n", 1, Distributions.MAX_TRIALS),
                integer("k", 0, Distributions.MAX_TRIALS), n("p"), mc))
            "poisson" -> tail(Distributions.poisson(integer("k", 0, Distributions.MAX_EVENTS), n("lambda"), mc))
            "normal" -> {
                val mean = n("mean"); val sigma = n("sigma"); val x = n("x")
                if (sigma.signum() <= 0) throw ArithFault.Domain("standard deviation must be positive", "normal")
                val z = (x - mean) / sigma
                // Evaluate the reflected tail directly: 1-CDF(z) loses tiny upper tails.
                nums("atMost" to Distributions.normalCdf(z, mc), "above" to Distributions.normalCdf(-z, mc))
            }
            else -> badRequest("unknown probability operation")
        }
    }

    private fun curveFit(op: String): Any {
        val raw = array("points", if (op == "quadratic") 3 else 2, ApiLimits.DATA_POINTS)
        val rows = raw.mapIndexed { i, item ->
            val row = (item as? JsonValue.Arr)?.values ?: badRequest("points[$i] must be [x, y]")
            if (row.size != 2) badRequest("points[$i] must contain exactly two values")
            row
        }
        val points = rows.map { pointValue(it[0]) to pointValue(it[1]) }
        if (op == "quadratic") return CurveFit.quadratic(points, settings).let {
            nums("a" to it.a, "b" to it.b, "c" to it.c) + mapOf("rSquared" to it.r2?.let(::result), "fitSpace" to "original")
        }
        val fit = when (op) {
            "exponential" -> CurveFit.exponential(points, mc)
            "power" -> CurveFit.power(points, mc)
            "logarithmic" -> CurveFit.logarithmic(points, mc)
            else -> badRequest("unknown curve-fit operation")
        }
        return nums("a" to fit.a, "b" to fit.b) + mapOf("rSquared" to fit.r2?.let(::result),
            "fitSpace" to when (op) { "logarithmic" -> "x transformed with ln"; "power" -> "x and y transformed with ln"; else -> "y transformed with ln" },
            "model" to when (op) { "exponential" -> "a*exp(b*x)"; "power" -> "a*x^b"; else -> "a+b*ln(x)" })
    }

    private fun quartiles(): Any = Quartiles.of(array("data", 2, Quartiles.MAX_VALUES).map(::pointValue)).let {
        nums("q1" to it.q1, "median" to it.median, "q3" to it.q3, "interquartileRange" to it.q3 - it.q1) +
            mapOf("modes" to it.modes.map(::result), "modeCount" to it.modeCount,
                "method" to "median of halves; exclude the middle value from both halves for odd counts")
    }

    private fun variable(): String = request.string("variable", "x", 16).let {
        if (!ReservedNames.isUnknown(it)) badRequest("variable must be x, y, or z")
        ReservedNames.canonicalUnknown(it)
    }
    private fun expression(): String = checkedExpression(request.string("expr"))
    // Bound expressions in calculus use radians too, independently of the caller's angle mode.
    private fun radiansNumber(name: String): Num {
        val supplied = (request.fields["settings"] as? JsonValue.Obj)?.fields.orEmpty()
        val radians = JsonValue.Obj(supplied + ("angle" to JsonValue.Str("RAD")))
        return point(ApiService(ApiRequest(mapOf("settings" to radians))).number(request.value(name)))
    }

    private fun taylor(): Any {
        val input = expression(); val variable = variable()
        val value = Series.taylor(input, variable, radiansNumber("center"),
            integer("order", 0, Series.MAX_ORDER), settings).required()
        return mapOf("expression" to value.expression, "coefficients" to value.coefficients.map(::result),
            "coefficientOrder" to "ascending powers of (variable-center)", "variable" to variable,
            "exactCoefficients" to value.exact, "approximateFunction" to true, "angleMode" to "RAD",
            "warning" to "A finite Taylor polynomial approximates the function locally; no remainder bound is supplied.")
    }

    private fun limitEstimate(): Any {
        val input = expression(); val variable = variable()
        val token = (request.value("point") as? JsonValue.Str)?.value?.trim()
        val estimate = when (token) {
            "inf", "+inf" -> Limits.atInfinity(input, variable, true, settings)
            "-inf" -> Limits.atInfinity(input, variable, false, settings)
            else -> Limits.at(input, variable, radiansNumber("point"), settings)
        }.required()
        fun side(s: Limits.Side): Map<String, Any?> = when (s) {
            is Limits.Side.Settles -> mapOf("state" to "settles", "value" to service.estimatedNumberResult(s.value), "agreeingDigits" to s.digits)
            is Limits.Side.Unbounded -> mapOf("state" to "unbounded", "positive" to s.positive)
            Limits.Side.Unsettled -> mapOf("state" to "unsettled")
            Limits.Side.Undefined -> mapOf("state" to "undefined")
            Limits.Side.Unevaluated -> mapOf("state" to "unevaluated")
        }
        return mapOf("left" to side(estimate.left), "right" to side(estimate.right),
            "twoSided" to estimate.twoSided?.let(::side), "atInfinity" to (token in setOf("inf", "+inf", "-inf")),
            "approximate" to true, "angleMode" to "RAD", "warning" to "Finite sampling is an estimate, not a proof of a limit.")
    }

    private fun function(): PlottableFunction {
        val expr = expression(); val variable = variable()
        service.loadRegisters()
        return service.calc.plot(expr, variable).required()
    }
    private class Budget {
        private val deadline = System.nanoTime() + SERIES_MILLIS * 1_000_000
        fun check() {
            if (Thread.currentThread().isInterrupted || System.nanoTime() > deadline) limit("table/series calculation budget exceeded; shorten the range or simplify the expression")
        }
    }
    private fun table(): Any {
        val f = function(); val start = n("start"); val end = n("end"); val step = n("step")
        if (start > end || step.signum() <= 0) badRequest("table requires start <= end and step > 0")
        if (start.abs() > Num.of(1_000_000_000_000L) || end.abs() > Num.of(1_000_000_000_000L)) limit("table bounds have magnitude at most 1e12")
        val last = NumMath.floor((end - start) / step).toBigIntegerExactOrNull()
            ?: badRequest("table row count is not a whole number")
        if (last >= BigInteger.valueOf(TABLE_ROWS.toLong())) limit("table exceeds $TABLE_ROWS rows")
        val budget = Budget()
        val rows = (0..last.toInt()).map { i ->
            budget.check()
            val point = start + step * Num.of(i)
            val sample = f.at(point)
            budget.check()
            service.sampleResult(sample)
        }
        return mapOf("rows" to rows, "angleMode" to settings.angle.name)
    }
    private fun series(op: String): Any {
        val from = whole("from"); val to = whole("to")
        if (from > to) badRequest("from must be <= to")
        val max = if (op == "sum") SUM_TERMS else PRODUCT_TERMS
        if (to - from >= BigInteger.valueOf(max.toLong())) limit("series exceeds $max terms")
        if (from.abs() > BigInteger.valueOf(1_000_000_000L) || to.abs() > BigInteger.valueOf(1_000_000_000L)) limit("series indices have magnitude at most 1e9")
        val f = function(); val budget = Budget()
        var total: Num = if (op == "sum") Num.ZERO else Num.ONE
        var index = from
        while (index <= to) {
            budget.check()
            val term = when (val s = f.at(Num.of(index))) {
                is Sample.Defined -> s.y
                is Sample.Undefined -> mathError(s.cause)
                is Sample.Unevaluated -> mathError(s.error)
            }
            total = if (op == "sum") total + term else total * term
            budget.check()
            index += BigInteger.ONE
        }
        return mapOf("value" to result(total), "terms" to (to - from + BigInteger.ONE).toInt(), "angleMode" to settings.angle.name)
    }

    private fun bitwise(op: String): Any {
        val base = request.int("base", 10, 2..36)
        fun parse(name: String): BigInteger {
            val value = Bases.parse(request.string(name, max = 512), base)
            if (value.bitLength() > Bitwise.MAX_BITS) limit("bitwise input exceeds 4096 bits")
            return value
        }
        val a = parse("a")
        val value = when (op) {
            "and" -> Bitwise.and(a, parse("b"))
            "or" -> Bitwise.or(a, parse("b"))
            "xor" -> Bitwise.xor(a, parse("b"))
            "shift" -> Bitwise.shift(a, integer("places", -Bitwise.MAX_BITS, Bitwise.MAX_BITS))
            "not" -> Bitwise.not(a, integer("bits", 8, 64))
            else -> badRequest("unknown bitwise operation")
        }
        return mapOf("decimal" to value.toString(), "binary" to Bases.format(value, 2),
            "octal" to Bases.format(value, 8), "hexadecimal" to Bases.format(value, 16))
    }

    companion object {
        const val TABLE_ROWS = 200
        const val SUM_TERMS = 1_000
        const val PRODUCT_TERMS = 200
        const val SERIES_MILLIS = 3_000L
        const val MAX_DURATION_SECONDS = 7_200_000_007_198L
        private data class Schema(val required: List<String>, val optional: List<String>)
        private fun spec(required: String, optional: String = "") = Schema(required.split(' ').filter(String::isNotBlank), optional.split(' ').filter(String::isNotBlank))
        private val schemas = linkedMapOf(
            "finance" to linkedMapOf(
                "loan" to spec("principal annualRate months"), "compound" to spec("principal annualRate years compoundsPerYear"),
                "simple" to spec("principal annualRate years"), "sip" to spec("monthly annualRate months"),
                "schedule" to spec("principal annualRate months"), "affordableLoan" to spec("payment annualRate months"),
                "savingsGoal" to spec("target annualRate months"), "inflation" to spec("amount annualRate years"),
                "addTax" to spec("amount rate"), "removeTax" to spec("amount rate"), "discount" to spec("price percent"),
                "percentChange" to spec("from to"), "portion" to spec("percent amount"), "percentOf" to spec("part whole"),
                "split" to spec("bill tipPercent people"), "shares" to spec("amount ratio"),
                "unitPrices" to spec("priceA quantityA priceB quantityB"), "fuel" to spec("distance distancePerUnitFuel pricePerUnitFuel"),
                "proportion" to spec("a b c")),
            "dates" to linkedMapOf("between" to spec("from to"), "shift" to spec("date days"), "age" to spec("birth asOf"), "weekday" to spec("date")),
            "duration" to linkedMapOf("add" to spec("a b"), "subtract" to spec("a b"), "between" to spec("from to"), "format" to spec("seconds")),
            "bmi" to linkedMapOf("" to spec("weightKg heightCm")),
            "words" to linkedMapOf("indian" to spec("value"), "international" to spec("value"), "rupees" to spec("rupees paise")),
            "vector" to linkedMapOf("dot" to spec("a b"), "cross" to spec("a b"), "length" to spec("a"), "projection" to spec("a b"), "angle" to spec("a b")),
            "number-theory" to linkedMapOf("factorize" to spec("value"), "isPrime" to spec("value"), "divisors" to spec("value"), "gcd" to spec("values"), "lcm" to spec("values")),
            "probability" to linkedMapOf("binomial" to spec("n k p"), "poisson" to spec("k lambda"), "normal" to spec("mean sigma x")),
            "curve-fit" to linkedMapOf("quadratic" to spec("points"), "exponential" to spec("points"), "power" to spec("points"), "logarithmic" to spec("points")),
            "quartiles" to linkedMapOf("" to spec("data")),
            "taylor" to linkedMapOf("" to spec("expr center order", "variable")),
            "limit" to linkedMapOf("" to spec("expr point", "variable")),
            "table" to linkedMapOf("" to spec("expr start end step", "variable registers")),
            "series" to linkedMapOf("sum" to spec("expr from to", "variable registers"), "product" to spec("expr from to", "variable registers")),
            "bitwise" to linkedMapOf("and" to spec("a b", "base"), "or" to spec("a b", "base"), "xor" to spec("a b", "base"),
                "shift" to spec("a places", "base"), "not" to spec("a bits", "base")),
        )
        val operations: List<String> get() = schemas.keys.toList()
        fun catalog(): Map<String, Any?> = mapOf("ok" to true, "tools" to schemas.map { (endpoint, operations) ->
            mapOf("path" to "/v1/$endpoint", "operations" to operations.map { (operation, schema) ->
                mapOf("operation" to operation.takeIf { it.isNotEmpty() }, "required" to schema.required,
                    "optional" to schema.optional + "settings")
            })
        })
    }
}
