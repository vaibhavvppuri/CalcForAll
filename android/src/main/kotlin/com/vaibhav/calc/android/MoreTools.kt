package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.everyday.BodyMass
import com.vaibhav.calc.core.everyday.Dates
import com.vaibhav.calc.core.everyday.Durations
import com.vaibhav.calc.core.everyday.Finance
import com.vaibhav.calc.core.everyday.NumberWords
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.format.OutcomeFormatter
import com.vaibhav.calc.core.graph.PlottableFunction
import com.vaibhav.calc.core.graph.RootFinder
import com.vaibhav.calc.core.graph.RootResult
import com.vaibhav.calc.core.graph.Sample
import com.vaibhav.calc.core.graph.VerifiedRoot
import com.vaibhav.calc.core.matrix.Vectors
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.route.RouteResult
import com.vaibhav.calc.core.stats.CurveFit
import com.vaibhav.calc.core.stats.Distributions
import com.vaibhav.calc.core.stats.Quartiles
import com.vaibhav.calc.core.symbolic.Limits
import com.vaibhav.calc.core.symbolic.Series
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

data class FieldSpec(val label: String, val default: String)
data class OperationSpec(val name: String, val fields: List<FieldSpec>, val help: String = "")
data class ToolSpec(val name: String, val summary: String, val operations: List<OperationSpec>)

/**
 * CalcForAll 1.2 tools, described as data: each tool is a list of operations, each
 * operation a list of labelled fields. One form renders them all, and a test can walk
 * the whole catalogue. Input adapters only — every calculation is the shared engine's.
 */
object MoreTools {
    const val MAX_TABLE_ROWS = 200
    const val MAX_SUM_TERMS = 5_000
    const val MAX_PRODUCT_TERMS = 500
    /** Exact terms can grow without limit (the sum of 1/x⁵ has a denominator thousands of digits long), so a count cap alone does not bound the work. */
    const val MAX_SERIES_MILLIS = 8_000L
    private const val MAX_FRACTION_CHARS = 40
    private val MONEY = MathContext(60, RoundingMode.HALF_EVEN)

    private fun op(name: String, help: String, vararg fields: Pair<String, String>) =
        OperationSpec(name, fields.map { FieldSpec(it.first, it.second) }, help)

    /** [today] only seeds the date fields' defaults; the engine never reads a clock. */
    fun catalog(today: LocalDate): List<ToolSpec> = listOf(
        ToolSpec("Money", "EMI, interest, SIP, GST, discounts, inflation and amounts in words", listOf(
            op("Loan EMI", "Reducing-balance instalment paid every month. Amounts are plain numbers in any currency.",
                "Loan amount" to "500000", "Interest % per year" to "8.5", "Months" to "60"),
            op("Compound interest", "Interest is added to the balance the stated number of times each year.",
                "Principal" to "100000", "Interest % per year" to "8", "Years" to "5", "Times compounded per year" to "4"),
            op("Simple interest", "Interest on the original amount only.",
                "Principal" to "5000", "Interest % per year" to "7.5", "Years" to "3"),
            op("SIP", "The same amount invested at the start of every month. The return is an assumption you choose, not a prediction.",
                "Monthly amount" to "5000", "Expected return % per year" to "12", "Months" to "120"),
            op("Add GST", "Tax added on top of a price. Enter whichever tax rate applies.",
                "Price before tax" to "1000", "Tax %" to "18"),
            op("Remove GST", "Finds the pre-tax price inside a tax-inclusive amount.",
                "Price including tax" to "1180", "Tax %" to "18"),
            op("Discount", "", "Price" to "2499", "Discount %" to "20"),
            op("Percent change", "Change measured against the size of the starting value.", "From" to "80", "To" to "100"),
            op("Split bill", "", "Bill" to "1200", "Tip %" to "10", "People" to "4"),
            op("Loan schedule", "Year by year: how much of the instalments is interest, how much repays the loan, and what is still owed. Up to ${Finance.MAX_SCHEDULE_MONTHS} months.",
                "Loan amount" to "500000", "Interest % per year" to "8.5", "Months" to "60"),
            op("Loan you can afford", "The loan a monthly payment can service at this rate and term.",
                "Monthly payment" to "15000", "Interest % per year" to "8.5", "Months" to "60"),
            op("Savings goal", "The amount to invest at the start of every month to reach a target. The return is your assumption.",
                "Target amount" to "1000000", "Expected return % per year" to "12", "Months" to "120"),
            op("Inflation", "What today's price becomes, and what today's money will then buy. The rate is your assumption.",
                "Amount today" to "1000", "Inflation % per year" to "6", "Years" to "10"),
            op("Amount in words", "For cheques and invoices: rupees and paise in words, in lakh and crore.",
                "Amount" to "123456.50"),
        )),
        ToolSpec("Dates", "Days between dates, add days, age and weekday", listOf(
            op("Days between", "Write dates as YYYY-MM-DD or DD/MM/YYYY.",
                "From" to "${today.year}-01-01", "To" to today.toString()),
            op("Add days", "Write the date as YYYY-MM-DD or DD/MM/YYYY. A negative number counts backwards.",
                "Start date" to today.toString(), "Days to add" to "100"),
            op("Age", "Write dates as YYYY-MM-DD or DD/MM/YYYY.",
                "Date of birth" to "2000-01-01", "As of" to today.toString()),
        )),
        ToolSpec("Everyday", "Percent of, unit price, fuel cost, proportion, ratios, BMI and numbers in words", listOf(
            op("Percent of", "", "Percent" to "15", "Of" to "2400"),
            op("Unit price", "Compares two packs by their price per unit.",
                "Price of A" to "120", "Quantity in A" to "500", "Price of B" to "200", "Quantity in B" to "1000"),
            op("Fuel cost", "Any units work, as long as the mileage is distance per unit of fuel.",
                "Distance" to "350", "Distance per unit of fuel" to "17.5", "Fuel price per unit" to "104.5"),
            op("Proportion", "If the first amount gives the second, what does the third give?",
                "If this many" to "3", "gives" to "450", "then this many" to "5"),
            op("BMI", "Weight in kilograms and height in centimetres.", "Weight (kg)" to "70", "Height (cm)" to "175"),
            op("Share in a ratio", "Divides an amount in a ratio such as 2:3:5. Up to ${Finance.MAX_RATIO_PARTS} parts.",
                "Amount" to "1200", "Ratio" to "2:3:5"),
            op("Number in words", "A whole number in words, in lakh and crore and in millions and billions.",
                "Whole number" to "1234567"),
        )),
        ToolSpec("Time", "Add or subtract durations; time between two clock times", listOf(
            op("Add durations", "Write durations as H:MM or H:MM:SS.", "First" to "1:45:30", "Second" to "0:50:45"),
            op("Subtract durations", "Write durations as H:MM or H:MM:SS.", "From" to "2:00:00", "Take away" to "0:50:45"),
            op("Between clock times", "24-hour times as HH:MM. If the second time is earlier it is taken as the next day.",
                "From" to "09:15", "To" to "17:40"),
            op("Seconds to d h m s", "", "Seconds" to "100000"),
        )),
        ToolSpec("Equation solver", "Polynomials to degree 8 exactly; any equation numerically", listOf(
            op("Solve", "Linear, quadratic and polynomial equations in x up to degree 8. Rational roots and quadratic factors stay exact; other roots are marked ≈.",
                "Equation" to "x^3-6x^2+11x-6=0"),
            op("Numeric on a range", "Searches a range for solutions of any equation in x, then checks each one in the original equation. Trigonometry follows the calculator's angle mode.",
                "Equation" to "cos(x)=x", "From x =" to "0", "To x =" to "2"),
        )),
        ToolSpec("Table & series", "Table of values, sums (Σ) and products (Π) of f(x)", listOf(
            op("Table", "Up to $MAX_TABLE_ROWS rows.", "f(x)" to "x^2-3x+2", "Start" to "-2", "End" to "5", "Step" to "1"),
            op("Sum Σ", "Adds f(x) for every whole x in the range. Up to $MAX_SUM_TERMS terms.",
                "f(x)" to "x^2", "From x =" to "1", "To x =" to "10"),
            op("Product Π", "Multiplies f(x) for every whole x in the range. Up to $MAX_PRODUCT_TERMS terms.",
                "f(x)" to "x", "From x =" to "1", "To x =" to "10"),
        )),
        ToolSpec("Number theory", "Prime factors, GCD and LCM, divisors, primality", listOf(
            op("Prime factors", "Whole numbers from 2 to 10^14.", "Whole number" to "360"),
            op("GCD and LCM", "Separate whole numbers with commas or spaces.", "Whole numbers" to "84, 36, 120"),
            op("Divisors", "Whole numbers from 1 to 10^14.", "Whole number" to "360"),
            op("Is it prime?", "Checked by exact division, so the answer is certain. Up to 10^14.", "Whole number" to "97"),
        )),
        ToolSpec("Vectors", "Dot and cross product, length, angle and projection", listOf(
            op("Dot product", "Separate components with commas. Up to ${Vectors.MAX_DIMENSION} components.",
                "Vector A" to "1, 2, 3", "Vector B" to "4, 5, 6"),
            op("Cross product", "Three components each.", "Vector A" to "1, 2, 3", "Vector B" to "4, 5, 6"),
            op("Length", "", "Vector A" to "3, 4"),
            op("Angle between", "Given in the calculator's angle mode.", "Vector A" to "1, 0", "Vector B" to "1, 1"),
            op("Projection", "The part of A that lies along B.", "Vector A" to "3, 4", "Vector B" to "1, 0"),
        )),
        ToolSpec("Probability", "Binomial, Poisson and normal distributions", listOf(
            op("Binomial", "Chance of exactly k, and of at most k, successes in n independent trials. Exact when p is a fraction.",
                "Trials n" to "10", "Successes k" to "3", "Chance of success p" to "1/2"),
            op("Poisson", "Chance of exactly k, and of at most k, events when the average is λ.",
                "Average λ" to "4", "Events k" to "2"),
            op("Normal", "Chance that a normally distributed value is at or below x.",
                "Mean μ" to "100", "Standard deviation σ" to "15", "Value x" to "130"),
        )),
        ToolSpec("Curve fitting", "Quadratic, exponential, power and logarithmic fits", listOf(
            op("Quadratic", "Least-squares y = ax² + bx + c. Separate values with commas or spaces.",
                "X values" to "0, 1, 2, 3, 4", "Y values" to "1, 3, 7, 13, 21"),
            op("Exponential", "y = a·e^(bx), fitted as a straight line on ln y. Every Y must be above zero.",
                "X values" to "0, 1, 2, 3", "Y values" to "2, 6, 18, 54"),
            op("Power", "y = a·x^b, fitted as a straight line on ln x and ln y. Every X and Y must be above zero.",
                "X values" to "1, 2, 3, 4", "Y values" to "3, 12, 27, 48"),
            op("Logarithmic", "y = a + b·ln x. Every X must be above zero.",
                "X values" to "1, 2, 4, 8", "Y values" to "0, 1, 2, 3"),
        )),
        ToolSpec("Geometry", "Areas, volumes and lengths — exact in π and square roots", listOf(
            op("Circle", "", "Radius" to "5"),
            op("Rectangle", "", "Length" to "4", "Width" to "3"),
            op("Triangle from 3 sides", "Area by Heron's formula.", "Side a" to "3", "Side b" to "4", "Side c" to "5"),
            op("Right triangle", "From the two shorter sides.", "Side a" to "5", "Side b" to "12"),
            op("Sphere", "", "Radius" to "3"),
            op("Cylinder", "", "Radius" to "2", "Height" to "5"),
            op("Cone", "", "Radius" to "3", "Height" to "4"),
        )),
        ToolSpec("Sequences", "Arithmetic and geometric progressions: any term and the sum", listOf(
            op("Arithmetic", "Each term adds the same difference.", "First term" to "3", "Common difference" to "4", "Number of terms" to "10"),
            op("Geometric", "Each term is multiplied by the same ratio.", "First term" to "2", "Common ratio" to "3", "Number of terms" to "8"),
        )),
        ToolSpec("Business", "Margin, markup, break-even and growth rate", listOf(
            op("Margin and markup", "Margin is profit as a share of the price; markup is profit as a share of the cost.",
                "Cost" to "80", "Selling price" to "100"),
            op("Price for a margin", "The price at which the cost leaves the margin you want.", "Cost" to "80", "Margin %" to "20"),
            op("Break-even", "How many units cover the fixed costs.",
                "Fixed costs" to "50000", "Price per unit" to "250", "Variable cost per unit" to "150"),
            op("Growth rate", "The steady yearly rate that turns the start value into the end value (CAGR).",
                "Start value" to "100000", "End value" to "161051", "Years" to "5"),
        )),
        ToolSpec("Data", "Quartiles, interquartile range and mode", listOf(
            op("Quartiles and mode", "Separate values with commas or spaces. Q1 and Q3 are the medians of the lower and upper halves; with an odd count the middle value belongs to neither half.",
                "Data" to "1, 2, 3, 4, 5, 6, 7, 8"),
        )),
        ToolSpec("Series & limits", "Taylor polynomials and numerical limits", listOf(
            op("Taylor polynomial", "Approximates f(x) near a chosen point with a polynomial of order 0 to ${Series.MAX_ORDER}. Calculus uses radians.",
                "f(x)" to "sin(x)", "About x =" to "0", "Order" to "5"),
            op("Limit", "A numerical estimate from values ever closer to the point on each side — not a proof. Type inf or -inf for a limit at infinity. Uses radians.",
                "f(x)" to "sin(x)/x", "x approaches" to "0"),
        )),
        ToolSpec("Programmer", "Bitwise AND, OR, XOR, NOT and shifts in any base", listOf(
            op("AND", "Whole numbers zero or greater, written in the base you choose (2–36).",
                "A" to "1100", "B" to "1010", "Base of A and B" to "2"),
            op("OR", "Whole numbers zero or greater, written in the base you choose (2–36).",
                "A" to "1100", "B" to "1010", "Base of A and B" to "2"),
            op("XOR", "Whole numbers zero or greater, written in the base you choose (2–36).",
                "A" to "1100", "B" to "1010", "Base of A and B" to "2"),
            op("Shift", "A positive number of places shifts left; a negative number shifts right.",
                "A" to "1011", "Places" to "2", "Base of A" to "2"),
            op("NOT", "Flips every bit inside a word of 8, 16, 32 or 64 bits.",
                "A" to "1011", "Word size in bits" to "8", "Base of A" to "2"),
        )),
    )

    fun calculate(tool: String, operation: String, values: List<String>, settings: Settings, today: LocalDate): ToolAnswer = try {
        require(values.all { it.length <= 8192 }) { "Input is too long." }
        val expected = catalog(today).firstOrNull { it.name == tool }?.operations?.firstOrNull { it.name == operation }
            ?: throw IllegalArgumentException("Unknown tool.")
        require(values.size == expected.fields.size) { "Fill every field." }
        // A field labelled as a percentage already means percent. The calculator reads "20%" as 0.2,
        // so a sign typed there would divide by a hundred twice and still print a plausible answer.
        val cleaned = values.mapIndexed { i, text ->
            val label = expected.fields[i].label
            if ('%' in label || label.contains("percent", ignoreCase = true)) text.trim().removeSuffix("%").trimEnd() else text
        }
        Run(settings).run(tool, operation, cleaned).let { it.copy(insert = it.insert?.let(::asOneValue)) }
    } catch (e: ToolFailure) { ToolAnswer(e.message.orEmpty(), error = true) }
      catch (e: ArithFault) { ToolAnswer(e.message.orEmpty(), error = true) }
      catch (e: IllegalArgumentException) { ToolAnswer(e.message.orEmpty(), error = true) }
      catch (e: ArithmeticException) { ToolAnswer(e.message ?: "Arithmetic could not be completed.", error = true) }

    private class ToolFailure(message: String) : RuntimeException(message)

    private class Run(val settings: Settings) {
        val calc = Calculator(settings)
        val mc: MathContext = settings.computeMc

        fun <T> CalcResult<T>.unwrap(): T = when (this) {
            is CalcResult.Ok -> value
            is CalcResult.Err -> throw ToolFailure(error.message)
        }

        fun num(text: String): Num {
            require(text.isNotBlank()) { "Fill every field." }
            return when (val value = calc.evaluate(text.trim()).unwrap()) {
                is RealValue -> value.num
                else -> throw IllegalArgumentException("Use real, dimensionless numbers in this tool.")
            }
        }

        fun whole(text: String, what: String): BigInteger =
            num(text).toBigIntegerExactOrNull() ?: throw IllegalArgumentException("$what must be a whole number.")

        fun int(text: String, what: String, range: IntRange): Int {
            val value = whole(text, what)
            require(value.bitLength() < 31 && value.toInt() in range) { "$what must be from ${range.first} to ${range.last}." }
            return value.toInt()
        }

        fun show(value: Num): String =
            (if (OutcomeFormatter.isApproximate(RealValue(value), settings)) "≈ " else "") + Formatter.exact(RealValue(value), settings)

        fun plain(value: Num): String = Formatter.reusableInput(value)

        /** Reuse the displayed cents without turning rounding or input uncertainty into exactness. */
        fun cashPlain(value: Num): String {
            val rounded = value.toBigDecimal(MONEY).setScale(2, RoundingMode.HALF_UP)
            val error = when (value) {
                is Dec -> value.absoluteError?.add(value.value.subtract(rounded).abs())
                is Rational -> {
                    val difference = (value - Num.ofDecimal(rounded.toPlainString())).abs()
                    if (difference.isZero) return rounded.toPlainString()
                    difference.toBigDecimal(MathContext(66, RoundingMode.CEILING))
                }
                else -> null // No certified conversion bound available through the public tool contract.
            }
            return plain(Dec(rounded, error))
        }

        /** Two decimals, half-up, in the user's grouping and numerals (the Formatter's rule, not a second one). */
        fun cash(value: Num): String {
            val rounded = value.toBigDecimal(MONEY).setScale(2, RoundingMode.HALF_UP)
            val magnitude = rounded.abs()
            val units = magnitude.toBigInteger()
            val cents = magnitude.subtract(BigDecimal(units)).movePointRight(2).intValueExact()
            val unitsText = Formatter.exact(RealValue(Num.of(units)), settings)
            val centsText = Formatter.exact(RealValue(Num.of(100 + cents)), settings.copy(grouping = GroupingMode.NONE)).takeLast(2)
            return (if (rounded.signum() < 0) "−" else "") + unitsText + "." + centsText
        }

        fun rows(vararg rows: Pair<String, String>): String = rows.joinToString("\n") { "${it.first}: ${it.second}" }

        /** A decimal rendering that is marked ≈ unless the digits shown are the value itself. */
        fun rounded(value: Num, sign: String = ""): String {
            val exact = value is Rational &&
                value.toExactBigDecimalOrNull()?.let { it.round(settings.displayMc).compareTo(it) == 0 } == true
            return (if (exact) "" else "≈ ") + sign + Formatter.decimal(RealValue(value), settings)
        }

        fun function(text: String): PlottableFunction {
            require(text.isNotBlank()) { "Enter an expression in x." }
            return PlottableFunction.of(text, settings, calc.environment, "x").unwrap()
        }

        fun run(tool: String, operation: String, v: List<String>): ToolAnswer = when (tool) {
            "Money" -> moneyTool(operation, v)
            "Dates" -> datesTool(operation, v)
            "Equation solver" -> solverTool(operation, v)
            "Table & series" -> seriesTool(operation, v)
            "Number theory" -> numberTool(operation, v)
            "Vectors" -> vectorTool(operation, v)
            "Probability" -> probabilityTool(operation, v)
            "Curve fitting" -> fitTool(operation, v)
            "Series & limits" -> calculusTool(operation, v)
            "Programmer" -> programmerTool(operation, v)
            "Everyday" -> everydayTool(operation, v)
            "Time" -> timeTool(operation, v)
            "Geometry" -> geometryTool(operation, v)
            "Sequences" -> sequenceTool(operation, v)
            "Business" -> businessTool(operation, v)
            "Data" -> dataTool(v)
            else -> throw IllegalArgumentException("Unknown tool.")
        }

        // ---- 1.3 tools ---------------------------------------------------------------------
        // The formulas are written as calculator expressions and evaluated by the engine itself,
        // so π stays π and a square root stays a surd exactly as it would on the main screen.

        private fun positive(text: String, what: String): Num {
            val value = num(text)
            requirePositive(value, what)
            return value
        }

        /** A physical/domain restriction must hold throughout a supplied error interval. */
        private fun requirePositive(value: Num, what: String, allowZero: Boolean = false) {
            val relation = if (allowZero) "non-negative" else "greater than zero"
            val sign = if (value is Dec) {
                val error = value.absoluteError
                    ?: throw ArithFault.Unsupported("$what needs a known error bound to establish that it is $relation.")
                val lowerSign = value.value.subtract(error).signum()
                if (error.signum() > 0 && (lowerSign < 0 || (!allowZero && lowerSign == 0)))
                    throw ArithFault.Unsupported("$what is not guaranteed to be $relation throughout its uncertainty.")
                lowerSign
            } else value.signum()
            require(sign > 0 || (allowZero && sign == 0)) { "$what must be $relation." }
        }

        private fun provesConvergentRatio(ratio: Num): Boolean = if (ratio is Dec) {
            ratio.absoluteError?.let {
                ratio.value.subtract(it) > BigDecimal.ONE.negate() && ratio.value.add(it) < BigDecimal.ONE
            } == true
        } else ratio.abs() < Num.ONE

        /** Evaluate [formula] with each `{i}` replaced by the i-th value, parenthesised and parser-ready. */
        private fun formula(formula: String, vararg values: Num): Num {
            var text = formula
            values.forEachIndexed { i, value -> text = text.replace("{$i}", "(${plain(value)})") }
            return num(text)
        }

        /** The exact form, with its decimal beside it when the exact form is not already a plain number. */
        private fun both(value: Num): String {
            val exact = show(value)
            if (value is Dec) return exact
            val decimal = Formatter.decimal(RealValue(value), settings)
            return if (decimal == exact) exact else "$exact  (${rounded(value)})"
        }

        private fun geometryTool(operation: String, v: List<String>): ToolAnswer = when (operation) {
            "Circle" -> {
                val r = positive(v[0], "The radius")
                val area = formula("π*{0}^2", r)
                ToolAnswer(rows("Area" to both(area), "Circumference" to both(formula("2*π*{0}", r)), "Diameter" to both(formula("2*{0}", r))), plain(area))
            }
            "Rectangle" -> {
                val l = positive(v[0], "The length")
                val w = positive(v[1], "The width")
                val area = formula("{0}*{1}", l, w)
                ToolAnswer(rows("Area" to both(area), "Perimeter" to both(formula("2*({0}+{1})", l, w)),
                    "Diagonal" to both(formula("sqrt({0}^2+{1}^2)", l, w))), plain(area))
            }
            "Triangle from 3 sides" -> {
                val a = positive(v[0], "Side a")
                val b = positive(v[1], "Side b")
                val c = positive(v[2], "Side c")
                requirePositive(a + b - c, "The sum of sides a and b minus side c")
                requirePositive(a + c - b, "The sum of sides a and c minus side b")
                requirePositive(b + c - a, "The sum of sides b and c minus side a")
                val area = formula("sqrt((({0}+{1}+{2})/2)*(({0}+{1}+{2})/2-{0})*(({0}+{1}+{2})/2-{1})*(({0}+{1}+{2})/2-{2}))", a, b, c)
                ToolAnswer(rows("Area" to both(area), "Perimeter" to both(formula("{0}+{1}+{2}", a, b, c))), plain(area))
            }
            "Right triangle" -> {
                val a = positive(v[0], "Side a")
                val b = positive(v[1], "Side b")
                val hypotenuse = formula("sqrt({0}^2+{1}^2)", a, b)
                ToolAnswer(rows("Longest side" to both(hypotenuse), "Area" to both(formula("{0}*{1}/2", a, b)),
                    "Perimeter" to both(formula("{0}+{1}+sqrt({0}^2+{1}^2)", a, b))), plain(hypotenuse))
            }
            "Sphere" -> {
                val r = positive(v[0], "The radius")
                val volume = formula("4/3*π*{0}^3", r)
                ToolAnswer(rows("Volume" to both(volume), "Surface area" to both(formula("4*π*{0}^2", r))), plain(volume))
            }
            "Cylinder" -> {
                val r = positive(v[0], "The radius")
                val h = positive(v[1], "The height")
                val volume = formula("π*{0}^2*{1}", r, h)
                ToolAnswer(rows("Volume" to both(volume), "Curved surface" to both(formula("2*π*{0}*{1}", r, h)),
                    "Total surface" to both(formula("2*π*{0}*({0}+{1})", r, h))), plain(volume))
            }
            "Cone" -> {
                val r = positive(v[0], "The radius")
                val h = positive(v[1], "The height")
                val volume = formula("π*{0}^2*{1}/3", r, h)
                ToolAnswer(rows("Volume" to both(volume), "Slant height" to both(formula("sqrt({0}^2+{1}^2)", r, h)),
                    "Total surface" to both(formula("π*{0}*({0}+sqrt({0}^2+{1}^2))", r, h))), plain(volume))
            }
            else -> throw IllegalArgumentException("Unknown calculation.")
        }

        private fun sequenceTool(operation: String, v: List<String>): ToolAnswer {
            val first = num(v[0])
            val step = num(v[1])
            val n = int(v[2], "The number of terms", 1..10_000)
            val count = Num.of(n)
            val shown = minOf(n, 6)
            return when (operation) {
                "Arithmetic" -> {
                    val last = formula("{0}+({2}-1)*{1}", first, step, count)
                    val sum = formula("{2}/2*(2*{0}+({2}-1)*{1})", first, step, count)
                    val terms = (0 until shown).joinToString(", ") { show(first + step * Num.of(it)) } + if (n > shown) ", …" else ""
                    ToolAnswer(rows("Term $n" to both(last), "Sum of $n terms" to both(sum), "Starts" to terms), plain(sum))
                }
                "Geometric" -> {
                    val last = formula("{0}*{1}^({2}-1)", first, step, count)
                    val sum = if ((step !is Dec || step.absoluteError?.signum() == 0) && step.compareTo(Num.ONE) == 0) formula("{0}*{2}", first, step, count)
                    else orInDecimals(first, step, n) { formula("{0}*({1}^{2}-1)/({1}-1)", first, step, count) }
                    val forever = if (provesConvergentRatio(step)) {
                        "\nSum to infinity: ${both(orInDecimals(first, step, null) { formula("{0}/(1-{1})", first, step) })}"
                    } else if (step is Dec && step.absoluteError?.signum() != 0) {
                        "\nInfinite sum: convergence is not established for this uncertain ratio."
                    } else ""
                    ToolAnswer(rows("Term $n" to both(last), "Sum of $n terms" to both(sum)) + forever, plain(sum))
                }
                else -> throw IllegalArgumentException("Unknown calculation.")
            }
        }

        /**
         * The geometric sum by the engine's own formula ([exact]), or in plain decimals when the
         * engine declines. With an irrational ratio, ratioⁿ is an approximate number, and once it is fifty
         * orders of magnitude from 1 the number tower refuses to subtract the 1 because it would
         * vanish (√2 with 333 terms; √2/2 with 331). The sum is approximate there in any case, so
         * a·(rⁿ − 1)/(r − 1) is worked out in decimals with guard digits. [terms] is null for the
         * sum to infinity, a/(1 − r). Exact inputs never come here: their refusals stand.
         */
        private fun orInDecimals(first: Num, ratio: Num, terms: Int?, exact: () -> Num): Num = try {
            exact()
        } catch (refusal: ToolFailure) {
            if (first is Rational && ratio is Rational) throw refusal
            // A nominal fallback may approximate exact irrational inputs, but cannot erase an
            // uncertain input's domain refusal or present a divergent family as convergent.
            if ((first is Dec && first.absoluteError?.signum() != 0) ||
                (ratio is Dec && ratio.absoluteError?.signum() != 0)) throw refusal
            val wp = MathContext(mc.precision + 20, RoundingMode.HALF_EVEN)
            val r = ratio.toBigDecimal(wp)
            val gap = r.subtract(BigDecimal.ONE, wp)
            // A ratio this close to 1 would spend the guard digits on the division.
            if (gap.abs() < BigDecimal.ONE.movePointLeft(10)) throw refusal
            val top = if (terms == null) BigDecimal.ONE.negate() else r.pow(terms, wp).subtract(BigDecimal.ONE, wp)
            Dec(first.toBigDecimal(wp).multiply(top, wp).divide(gap, wp).round(mc))
        }

        /** Count only when the shared engine's enclosure proves one ceiling; otherwise refuse. */
        private fun wholeUnits(units: Num): Num = NumMath.ceil(units)

        private fun businessTool(operation: String, v: List<String>): ToolAnswer = when (operation) {
            "Margin and markup" -> {
                val cost = positive(v[0], "The cost")
                val price = positive(v[1], "The selling price")
                ToolAnswer(rows("Profit" to cash(price - cost), "Margin" to "${rounded(formula("({1}-{0})/{1}*100", cost, price))}% of the price",
                    "Markup" to "${rounded(formula("({1}-{0})/{0}*100", cost, price))}% of the cost"), cashPlain(price - cost))
            }
            "Price for a margin" -> {
                val cost = positive(v[0], "The cost")
                val margin = num(v[1])
                requirePositive(margin, "The margin", allowZero = true)
                requirePositive(Num.of(100) - margin, "100 percent minus the margin")
                val price = formula("{0}/(1-{1}/100)", cost, margin)
                ToolAnswer(rows("Selling price" to cash(price), "Profit" to cash(price - cost)), cashPlain(price))
            }
            "Break-even" -> {
                val fixed = num(v[0])
                val price = positive(v[1], "The price per unit")
                val variable = num(v[2])
                requirePositive(fixed, "The fixed cost", allowZero = true)
                requirePositive(variable, "The variable cost", allowZero = true)
                requirePositive(price - variable, "The price minus the variable cost")
                val units = formula("{0}/({1}-{2})", fixed, price, variable)
                val whole = wholeUnits(units)
                ToolAnswer(rows("Units to break even" to rounded(units), "Whole units needed" to show(whole),
                    "Sales at break-even" to cash(units * price)), plain(whole))
            }
            "Growth rate" -> {
                val start = positive(v[0], "The start value")
                val end = positive(v[1], "The end value")
                val years = positive(v[2], "The number of years")
                val rate = formula("(({1}/{0})^(1/{2})-1)*100", start, end, years)
                ToolAnswer(rows("Growth per year" to "${rounded(rate)}%", "Total growth" to "${rounded(formula("({1}/{0}-1)*100", start, end))}%"), plain(rate))
            }
            else -> throw IllegalArgumentException("Unknown calculation.")
        }

        private fun dataTool(v: List<String>): ToolAnswer {
            val q = Quartiles.of(list(v[0]))
            val modes = if (q.modes.isEmpty()) "no value repeats" else q.modes.joinToString(", ") { show(it) } + " (${q.modeCount} times each)"
            return ToolAnswer(rows("Lower quartile Q1" to show(q.q1), "Median" to show(q.median), "Upper quartile Q3" to show(q.q3),
                "Interquartile range" to show(q.q3 - q.q1), "Mode" to modes), plain(q.median))
        }

        private fun everydayTool(operation: String, v: List<String>): ToolAnswer = when (operation) {
            "Percent of" -> Finance.portion(num(v[0]), num(v[1])).let { ToolAnswer(show(it), plain(it)) }
            "Unit price" -> Finance.compareUnitPrices(num(v[0]), num(v[1]), num(v[2]), num(v[3])).let {
                val verdict = when {
                    it.savingPercent.isZero -> "Both cost the same per unit."
                    it.unitPriceA < it.unitPriceB -> "A is cheaper by ${rounded(it.savingPercent)}% per unit."
                    else -> "B is cheaper by ${rounded(it.savingPercent)}% per unit."
                }
                ToolAnswer(rows("A costs per unit" to rounded(it.unitPriceA), "B costs per unit" to rounded(it.unitPriceB)) + "\n$verdict")
            }
            "Fuel cost" -> Finance.fuelCost(num(v[0]), num(v[1]), num(v[2])).let {
                ToolAnswer(rows("Fuel needed" to rounded(it.fuel), "Cost" to cash(it.cost)), cashPlain(it.cost))
            }
            "Proportion" -> Finance.proportion(num(v[0]), num(v[1]), num(v[2])).let { ToolAnswer(show(it), plain(it)) }
            "BMI" -> BodyMass.index(num(v[0]), num(v[1])).let {
                val exact = (it.index * Num.of(100)).isIntegerValue()
                ToolAnswer("BMI: ${if (exact) "" else "≈ "}${cash(it.index)}\n${it.band.label}\n\nWHO adult bands: under 18.5 underweight, 18.5 to under 25 normal, " +
                    "25 to under 30 overweight, 30 and over obesity. A screening number for adults, not a diagnosis.", cashPlain(it.index))
            }
            "Share in a ratio" -> {
                // One kind of separator at a time: a colon if there is one, else a semicolon if there
                // is one, else a comma. "1,500:2,500" and "1,500; 2,500" must not become four parts,
                // nor "1 +1 : 2" three. Each part goes whole to the engine, which refuses a grouping
                // comma and ignores spaces.
                val pieces = when {
                    ':' in v[1] -> ToolInputLists.split(v[1], setOf(':'))
                    ';' in v[1] -> ToolInputLists.split(v[1], setOf(';'))
                    else -> ToolInputLists.split(v[1], setOf(','))
                }
                val ratio = pieces.filter { it.isNotBlank() }.map { num(it) }
                val shares = Finance.shareByRatio(num(v[0]), ratio)
                val parts = shares.parts.mapIndexed { i, part -> "Part ${i + 1} (${show(ratio[i])})" to both(part) }
                ToolAnswer(rows(*parts.toTypedArray()) +
                    if (shares.simplest.isEmpty()) "" else "\nSimplest ratio: ${shares.simplest.joinToString(" : ")}")
            }
            "Number in words" -> whole(v[0], "The number").let {
                ToolAnswer(rows("Indian" to NumberWords.indian(it), "International" to NumberWords.international(it)))
            }
            else -> throw IllegalArgumentException("Unknown calculation.")
        }

        private fun timeTool(operation: String, v: List<String>): ToolAnswer = when (operation) {
            "Add durations", "Subtract durations" -> {
                val first = Durations.parse(v[0])
                val second = Durations.parse(v[1])
                val total = if (operation == "Add durations") first + second else first - second
                ToolAnswer("${Durations.format(total)}\n${Durations.breakdown(total)}\n$total seconds", total.toString())
            }
            "Between clock times" -> {
                val from = Durations.parseClock(v[0])
                val to = Durations.parseClock(v[1])
                val gap = Durations.between(from, to)
                ToolAnswer("${Durations.format(gap)}\n${Durations.breakdown(gap)}" +
                    if (to < from) "\n\nThe second time is earlier, so it was taken as the next day." else "", gap.toString())
            }
            "Seconds to d h m s" -> {
                val seconds = whole(v[0], "Seconds")
                require(seconds.signum() >= 0 && seconds.bitLength() < 50) { "Use a whole number of seconds from 0 up." }
                ToolAnswer("${Durations.breakdown(seconds.toLong())}\n${Durations.format(seconds.toLong())}")
            }
            else -> throw IllegalArgumentException("Unknown calculation.")
        }

        /** Calculus works in radians, so its point/centre fields are read in radians too. */
        private fun radianNumber(text: String): Num {
            require(text.isNotBlank()) { "Fill every field." }
            return when (val value = Calculator(settings.copy(angle = AngleMode.RAD)).evaluate(text.trim()).unwrap()) {
                is RealValue -> value.num
                else -> throw IllegalArgumentException("Use a real, dimensionless number.")
            }
        }

        private fun calculusTool(operation: String, v: List<String>): ToolAnswer = when (operation) {
            "Taylor polynomial" -> {
                val center = radianNumber(v[1])
                val order = int(v[2], "Order", 0..Series.MAX_ORDER)
                val taylor = Series.taylor(v[0], "x", center, order, settings).unwrap()
                ToolAnswer(taylor.expression +
                    "\n\nTaylor polynomial of order $order about x = ${show(center)}. It approximates the function near that point; it is not the function." +
                    "\nCalculus uses radians." + if (taylor.exact) "" else "\nSome coefficients are rounded, so automatic insertion is unavailable.",
                    if (taylor.exact) taylor.expression else null,
                    insertAngle = if (taylor.exact) AngleMode.RAD else null,
                    insertNotice = if (taylor.exact) "Using radians for this algebra/calculus expression." else null)
            }
            "Limit" -> {
                val target = v[1].trim().lowercase()
                val upward = target in setOf("inf", "+inf", "∞", "+∞", "infinity", "+infinity")
                val downward = target in setOf("-inf", "-∞", "−∞", "-infinity", "−inf")
                val estimate = when {
                    upward -> Limits.atInfinity(v[0], "x", true, settings)
                    downward -> Limits.atInfinity(v[0], "x", false, settings)
                    else -> Limits.at(v[0], "x", radianNumber(v[1]), settings)
                }.unwrap()
                fun side(s: Limits.Side): String = when (s) {
                    is Limits.Side.Settles -> "≈ ${Formatter.decimal(RealValue(s.value), settings)}"
                    is Limits.Side.Unbounded -> if (s.positive) "grows without bound (+∞)" else "falls without bound (−∞)"
                    Limits.Side.Unsettled -> "does not settle on a value"
                    Limits.Side.Undefined -> "has no value on this side"
                    Limits.Side.Unevaluated -> "could not be evaluated near enough to tell (the calculator's limits or precision were reached, or the function stops having a value there; this says nothing about the limit itself)"
                }
                fun determinate(s: Limits.Side) = s is Limits.Side.Settles || s is Limits.Side.Unbounded
                val note = "\n\nA numerical estimate from sampled values, not a proof. Uses radians."
                val both = estimate.twoSided
                when {
                    upward || downward -> ToolAnswer("As x → ${if (upward) "+∞" else "−∞"}: ${side(estimate.right)}$note",
                        (estimate.right as? Limits.Side.Settles)?.let { plain(it.value) })
                    both != null -> ToolAnswer("${side(both)}\nBoth sides agree to about ${both.digits} significant digits.$note", plain(both.value))
                    estimate.left == estimate.right && estimate.left is Limits.Side.Unbounded ->
                        ToolAnswer("From both sides the function ${side(estimate.left)}.$note")
                    else -> ToolAnswer("From the left: ${side(estimate.left)}\nFrom the right: ${side(estimate.right)}\n" +
                        // "Does not exist" is a claim; make it only when both sides were actually determined.
                        (if (determinate(estimate.left) && determinate(estimate.right)) "The two sides disagree, so the two-sided limit does not appear to exist."
                        else "The two-sided limit could not be estimated.") + note)
                }
            }
            else -> throw IllegalArgumentException("Unknown calculation.")
        }

        private fun programmerTool(operation: String, v: List<String>): ToolAnswer {
            val base = int(v[2], "The base", 2..36)
            fun parse(text: String): BigInteger = calc.fromBase(text.trim(), base).unwrap().toBigIntegerExactOrNull()
                ?: throw IllegalArgumentException("Use whole numbers.")
            val a = parse(v[0])
            val result = when (operation) {
                "AND" -> Bitwise.and(a, parse(v[1]))
                "OR" -> Bitwise.or(a, parse(v[1]))
                "XOR" -> Bitwise.xor(a, parse(v[1]))
                "Shift" -> Bitwise.shift(a, int(v[1], "Places", -Bitwise.MAX_BITS..Bitwise.MAX_BITS))
                "NOT" -> Bitwise.not(a, int(v[1], "The word size", 8..64))
                else -> throw IllegalArgumentException("Unknown calculation.")
            }
            val n = Num.of(result)
            return ToolAnswer(rows("Decimal" to result.toString(), "Hexadecimal" to calc.toBase(n, 16).unwrap(),
                "Binary" to calc.toBase(n, 2).unwrap(), "Octal" to calc.toBase(n, 8).unwrap()), result.toString())
        }

        fun list(text: String): List<Num> {
            val parts = ToolInputLists.split(text, setOf(',', ';'), whitespace = true)
            require(parts.isNotEmpty()) { "Fill every field." }
            require(parts.size <= 1000) { "Use at most 1,000 values." }
            return parts.map { num(it) }
        }

        fun vec(values: List<Num>): String = values.joinToString(", ", "(", ")") { show(it) }

        private fun factorText(factors: List<Pair<BigInteger, Int>>): String =
            factors.joinToString(" × ") { (prime, exponent) -> if (exponent == 1) "$prime" else "$prime^$exponent" }

        private fun numberTool(operation: String, v: List<String>): ToolAnswer = when (operation) {
            "Prime factors", "Is it prime?" -> {
                val n = whole(v[0], "The number")
                if (operation == "Is it prime?" && n < BigInteger.valueOf(2)) ToolAnswer("$n is not prime: primes start at 2.")
                else {
                    val factors = NumberTheory.factorize(n)
                    val prime = factors.size == 1 && factors[0].second == 1
                    ToolAnswer(when {
                        prime -> "$n is prime."
                        operation == "Is it prime?" -> "$n is not prime: $n = ${factorText(factors)}"
                        else -> "$n = ${factorText(factors)}"
                    })
                }
            }
            "GCD and LCM" -> {
                val values = list(v[0]).map { it.toBigIntegerExactOrNull() ?: throw IllegalArgumentException("Use whole numbers only.") }
                val gcd = NumberTheory.gcd(values)
                ToolAnswer(rows("GCD" to gcd.toString(), "LCM" to NumberTheory.lcm(values).toString()), gcd.toString())
            }
            "Divisors" -> {
                val n = whole(v[0], "The number")
                require(n.signum() > 0) { "Use a whole number from 1 to 10^14." }
                val divisors = NumberTheory.divisors(n)
                ToolAnswer("${divisors.size} divisor${if (divisors.size == 1) "" else "s"}\n${divisors.joinToString(", ")}", divisors.size.toString())
            }
            else -> throw IllegalArgumentException("Unknown calculation.")
        }

        private fun vectorTool(operation: String, v: List<String>): ToolAnswer = when (operation) {
            "Dot product" -> Vectors.dot(list(v[0]), list(v[1])).let { ToolAnswer(show(it), plain(it)) }
            "Cross product" -> ToolAnswer(vec(Vectors.cross(list(v[0]), list(v[1]))))
            "Length" -> Vectors.length(list(v[0]), mc).let { ToolAnswer(show(it), plain(it)) }
            "Angle between" -> {
                val cos = Vectors.cosine(list(v[0]), list(v[1]), mc)
                // The inverse cosine is the engine's own, so the answer is in the user's angle mode.
                val angle = num("acos(${plain(cos)})")
                val unit = when (settings.angle) { AngleMode.DEG -> "degrees"; AngleMode.RAD -> "radians"; AngleMode.GRAD -> "gradians" }
                ToolAnswer("${show(angle)} $unit\ncos θ = ${show(cos)}", plain(angle))
            }
            "Projection" -> ToolAnswer(vec(Vectors.projection(list(v[0]), list(v[1]))))
            else -> throw IllegalArgumentException("Unknown calculation.")
        }

        /** A fraction short enough to read; a 600-digit one helps nobody and cannot be inserted. */
        private fun shortFraction(p: Num): String? {
            if (p !is Rational || p.isInteger) return null
            return Formatter.exact(RealValue(p), settings.copy(exactForm = ExactForm.FRACTION)).takeIf { it.length <= MAX_FRACTION_CHARS }
        }

        /** An exact probability reads best as its fraction with the decimal beside it. */
        private fun chance(p: Num): String {
            val fraction = shortFraction(p)
            return when {
                fraction != null -> "$fraction (${Formatter.decimal(RealValue(p), settings)})"
                p is Rational && !p.isInteger -> rounded(p)
                else -> show(p)
            }
        }

        private fun tail(k: Int, t: Distributions.Tail): ToolAnswer = ToolAnswer(
            // The upper tail is the engine's own: 1 − P(X ≤ k) would cancel once the tail is small.
            rows("P(X = $k)" to chance(t.exactly), "P(X ≤ $k)" to chance(t.atMost), "P(X > $k)" to chance(t.above)),
            // An exact fraction is inserted only when it is short; otherwise its rounded decimal, marked approximate.
            if (t.exactly is Dec || shortFraction(t.exactly) != null || (t.exactly as? Rational)?.isInteger == true) plain(t.exactly)
            else plain(Dec(t.exactly.toBigDecimal(settings.displayMc))),
        )

        private fun probabilityTool(operation: String, v: List<String>): ToolAnswer = when (operation) {
            "Binomial" -> {
                val n = int(v[0], "Trials", 1..Distributions.MAX_TRIALS)
                val k = int(v[1], "Successes", 0..n)
                tail(k, Distributions.binomial(n, k, num(v[2]), mc))
            }
            "Poisson" -> {
                val k = int(v[1], "Events", 0..Distributions.MAX_EVENTS)
                tail(k, Distributions.poisson(k, num(v[0]), mc))
            }
            "Normal" -> {
                val mean = num(v[0])
                val deviation = num(v[1])
                require(deviation.signum() > 0) { "The standard deviation must be greater than zero." }
                val z = (num(v[2]) - mean) / deviation
                val below = Distributions.normalCdf(z, mc)
                val above = Distributions.normalCdf(-z, mc) // by symmetry; 1 − below would cancel in the tail
                ToolAnswer(rows("z-score" to show(z), "P(X ≤ x)" to show(below), "P(X > x)" to show(above)), plain(below))
            }
            else -> throw IllegalArgumentException("Unknown calculation.")
        }

        private fun fitTool(operation: String, v: List<String>): ToolAnswer {
            val xs = list(v[0])
            val ys = list(v[1])
            require(xs.size == ys.size) { "X and Y must contain the same number of values." }
            val points = xs.zip(ys)
            fun quality(r2: Num?): String = "r² = ${r2?.let { show(it) } ?: "undefined (constant Y)"}"
            val transformed = "\n\nFitted as a straight line on transformed data; r² describes that line. ${points.size} points."
            return when (operation) {
                "Quadratic" -> CurveFit.quadratic(points, settings).let {
                    ToolAnswer("y = (${show(it.a)})x² + (${show(it.b)})x + (${show(it.c)})\n${quality(it.r2)}\n\nLeast-squares fit; ${points.size} points.")
                }
                "Exponential" -> CurveFit.exponential(points, mc).let {
                    ToolAnswer("y = (${show(it.a)})·e^((${show(it.b)})x)\n${quality(it.r2)}$transformed")
                }
                "Power" -> CurveFit.power(points, mc).let {
                    ToolAnswer("y = (${show(it.a)})·x^(${show(it.b)})\n${quality(it.r2)}$transformed")
                }
                "Logarithmic" -> CurveFit.logarithmic(points, mc).let {
                    ToolAnswer("y = (${show(it.a)}) + (${show(it.b)})·ln x\n${quality(it.r2)}$transformed")
                }
                else -> throw IllegalArgumentException("Unknown calculation.")
            }
        }

        private fun moneyTool(operation: String, v: List<String>): ToolAnswer = when (operation) {
            "Loan EMI" -> Finance.loan(num(v[0]), num(v[1]), int(v[2], "Months", 1..Finance.MAX_PERIODS), mc).let {
                ToolAnswer(rows("Monthly payment" to cash(it.payment), "Total paid" to cash(it.totalPaid),
                    "Total interest" to cash(it.totalInterest)), cashPlain(it.payment))
            }
            "Compound interest" -> Finance.compound(num(v[0]), num(v[1]), num(v[2]), int(v[3], "Times compounded per year", 1..365), mc).let {
                ToolAnswer(rows("Final amount" to cash(it.amount), "Interest earned" to cash(it.interest)), cashPlain(it.amount))
            }
            "Simple interest" -> Finance.simple(num(v[0]), num(v[1]), num(v[2])).let {
                ToolAnswer(rows("Final amount" to cash(it.amount), "Interest earned" to cash(it.interest)), cashPlain(it.amount))
            }
            "SIP" -> Finance.monthlyInvestment(num(v[0]), num(v[1]), int(v[2], "Months", 1..Finance.MAX_PERIODS), mc).let {
                ToolAnswer(rows("Future value" to cash(it.futureValue), "Amount invested" to cash(it.invested), "Gain" to cash(it.gain)) +
                    "\n\nAssumes the return you entered every month; real returns vary.", cashPlain(it.futureValue))
            }
            "Add GST" -> Finance.addTax(num(v[0]), num(v[1])).let {
                ToolAnswer(rows("Tax" to cash(it.tax), "Price including tax" to cash(it.gross),
                    "If split as CGST + SGST" to "${cash(it.tax / Num.TWO)} each"), cashPlain(it.gross))
            }
            "Remove GST" -> Finance.removeTax(num(v[0]), num(v[1])).let {
                ToolAnswer(rows("Price before tax" to cash(it.net), "Tax inside the price" to cash(it.tax),
                    "If split as CGST + SGST" to "${cash(it.tax / Num.TWO)} each"), cashPlain(it.net))
            }
            "Discount" -> Finance.discount(num(v[0]), num(v[1])).let {
                ToolAnswer(rows("You save" to cash(it.saving), "Final price" to cash(it.finalPrice)), cashPlain(it.finalPrice))
            }
            "Percent change" -> Finance.percentChange(num(v[0]), num(v[1])).let {
                val sign = if (it.percent.signum() > 0) "+" else ""
                ToolAnswer(rows("Change" to "${rounded(it.percent, sign)}%",
                    "Difference" to show(it.difference)), plain(it.percent))
            }
            "Split bill" -> Finance.split(num(v[0]), num(v[1]), int(v[2], "People", 1..10_000)).let {
                ToolAnswer(rows("Tip" to cash(it.tip), "Total" to cash(it.total), "Each person pays" to cash(it.perPerson)),
                    cashPlain(it.perPerson))
            }
            "Loan schedule" -> {
                val principal = num(v[0])
                val rate = num(v[1])
                val months = int(v[2], "Months", 1..Finance.MAX_SCHEDULE_MONTHS)
                val payment = Finance.loan(principal, rate, months, mc).payment
                ToolAnswer("Monthly payment: ${cash(payment)}\n" + Finance.schedule(principal, rate, months, mc).joinToString("\n") {
                    "Year ${it.year}: interest ${cash(it.interest)}, principal ${cash(it.principal)}, balance ${cash(it.balance)}"
                })
            }
            "Loan you can afford" -> Finance.affordableLoan(num(v[0]), num(v[1]), int(v[2], "Months", 1..Finance.MAX_PERIODS), mc).let {
                ToolAnswer(rows("Loan amount" to cash(it.principal), "Total paid" to cash(it.totalPaid),
                    "Total interest" to cash(it.totalInterest)), cashPlain(it.principal))
            }
            "Savings goal" -> Finance.savingsGoal(num(v[0]), num(v[1]), int(v[2], "Months", 1..Finance.MAX_PERIODS), mc).let {
                ToolAnswer(rows("Invest each month" to cash(it.monthly), "Total invested" to cash(it.invested), "Gain" to cash(it.gain)) +
                    "\n\nAssumes the return you entered every month; real returns vary.", cashPlain(it.monthly))
            }
            "Inflation" -> Finance.inflation(num(v[0]), num(v[1]), num(v[2]), mc).let {
                ToolAnswer(rows("The same goods will cost" to cash(it.futurePrice), "The same money will buy what costs today" to cash(it.buyingPower)) +
                    "\n\nAssumes the same inflation every year; real inflation varies.", cashPlain(it.futurePrice))
            }
            "Amount in words" -> {
                val amount = num(v[0])
                require(amount.signum() >= 0) { "The amount cannot be negative." }
                // A cheque amount is exact: a third decimal place is refused, not rounded away.
                val paise = (amount * Num.of(100)).toBigIntegerExactOrNull()
                    ?: throw IllegalArgumentException("Write the amount with at most two decimal places.")
                val split = paise.divideAndRemainder(BigInteger.valueOf(100))
                ToolAnswer(NumberWords.rupees(split[0], split[1].toInt()))
            }
            else -> throw IllegalArgumentException("Unknown calculation.")
        }

        private fun datesTool(operation: String, v: List<String>): ToolAnswer = when (operation) {
            "Days between" -> {
                val a = Dates.parse(v[0])
                val b = Dates.parse(v[1])
                val s = Dates.between(a, b)
                ToolAnswer(listOf(
                    "${s.totalDays} days" + if (s.forward) "" else " (the second date is earlier)",
                    "${s.weeks} weeks ${s.weekRemainder} days",
                    "${s.years} years ${s.months} months ${s.days} days",
                    "", "From ${Dates.describe(a)}", "To ${Dates.describe(b)}",
                ).joinToString("\n"), s.totalDays.toString())
            }
            "Add days" -> {
                val start = Dates.parse(v[0])
                val days = whole(v[1], "Days to add")
                require(days.bitLength() < 63) { "Shift by at most ${Dates.MAX_SHIFT_DAYS} days." }
                ToolAnswer(Dates.describe(Dates.shift(start, days.toLong())) + "\n\nCounted from ${Dates.describe(start)}")
            }
            "Age" -> {
                val birth = Dates.parse(v[0])
                val asOf = Dates.parse(v[1])
                val next = Dates.daysToNextBirthday(birth, asOf) // also refuses a birth date after "as of"
                val s = Dates.age(birth, asOf) // same birthday convention as the countdown above
                ToolAnswer(listOf(
                    "${s.years} years ${s.months} months ${s.days} days",
                    "${s.totalDays} days in total",
                    when (next) {
                        0L -> "Birthday today"
                        1L -> "Next birthday tomorrow"
                        else -> "Next birthday in $next days"
                    },
                    "", "Born ${Dates.describe(birth)}",
                ).joinToString("\n"), s.years.toString())
            }
            else -> throw IllegalArgumentException("Unknown calculation.")
        }

        /**
         * A searched root is exact only when it satisfies the equation with a residual of
         * exactly zero. A bisection midpoint is a rational number too, but printing it as
         * `x = 1263389897495/1709396983808` would claim an exactness nobody established.
         */
        private fun exactRoot(r: VerifiedRoot): Boolean = r.residual.isZero && r.x !is Dec

        private fun root(r: VerifiedRoot): String =
            if (exactRoot(r)) "x = ${Formatter.exact(RealValue(r.x), settings)}"
            else "x ≈ ${Formatter.decimal(RealValue(r.x), settings)}"

        private fun rootInsertion(r: VerifiedRoot): String =
            if (exactRoot(r)) plain(r.x) else plain(Dec(r.x.toBigDecimal(settings.displayMc)))

        private fun solverTool(operation: String, v: List<String>): ToolAnswer = when (operation) {
            "Solve" -> {
                val text = v[0].trim()
                require(text.isNotEmpty()) { "Enter an equation in x." }
                val input = if ('=' in text) text else "$text=0"
                when (val r = calc.route(input)) {
                    is RouteResult.Ok -> {
                        val rendered = OutcomeFormatter.render(r.outcome, input, settings)
                        ToolAnswer(buildString {
                            if (rendered.approximate) append("≈ ")
                            append(rendered.primary)
                            if (rendered.steps.isNotEmpty()) {
                                append("\n\n")
                                append(rendered.steps.joinToString("\n"))
                            }
                        })
                    }
                    is RouteResult.Err -> ToolAnswer(r.error.message +
                        if ("cannot solve" in r.error.message) "\n\nThis is not a polynomial equation. Try “Numeric on a range”." else "",
                        error = true)
                    is RouteResult.Ambiguous -> ToolAnswer("This input has more than one reading.", error = true)
                }
            }
            "Numeric on a range" -> {
                val parts = v[0].split('=')
                require(parts.size <= 2) { "Use a single equals sign." }
                require(parts.none { it.isBlank() }) { "Enter an equation in x." }
                val f = function(if (parts.size == 2) "(${parts[0]})-(${parts[1]})" else parts[0])
                val note = "\n\nA numerical search samples the range. It can miss solutions that lie very close together " +
                    "or only touch zero. Angle mode: ${settings.angle}."
                when (val r = RootFinder.roots(f, num(v[1]), num(v[2]), settings)) {
                    is RootResult.Found ->
                        if (r.roots.isEmpty()) ToolAnswer("No solution found in this range.$note")
                        else ToolAnswer(r.roots.joinToString("\n") { root(it) } +
                            "\n\nEach value was checked in the original equation.$note",
                            r.roots.singleOrNull()?.let { rootInsertion(it) })
                    is RootResult.Unverified -> ToolAnswer(buildString {
                        if (r.verified.isNotEmpty()) {
                            append(r.verified.joinToString("\n") { root(it) })
                            append("\n\n")
                        }
                        append("Not confirmed: ")
                        append(r.candidates.joinToString(", ") { show(it) })
                        append("\n")
                        append(r.reason)
                        append(note)
                    })
                    is RootResult.Failed -> ToolAnswer(r.error.message, error = true)
                }
            }
            else -> throw IllegalArgumentException("Unknown calculation.")
        }

        private fun seriesTool(operation: String, v: List<String>): ToolAnswer {
            val f = function(v[0])
            val deadline = System.nanoTime() + MAX_SERIES_MILLIS * 1_000_000
            fun withinBudget() {
                if (Thread.currentThread().isInterrupted || System.nanoTime() > deadline) {
                    throw IllegalArgumentException("This range takes too long to work out exactly. Use a shorter range or a simpler f(x).")
                }
            }
            return when (operation) {
                "Table" -> {
                    val start = num(v[1])
                    val end = num(v[2])
                    val step = num(v[3])
                    require(step.signum() > 0) { "Step must be greater than zero." }
                    require(start <= end) { "Start must not be after End." }
                    val last = NumMath.floor((end - start) / step).toBigIntegerExactOrNull()
                    require(last != null && last < BigInteger.valueOf(MAX_TABLE_ROWS.toLong())) {
                        "Use at most $MAX_TABLE_ROWS rows: increase the step or shorten the range."
                    }
                    ToolAnswer((0..last.toInt()).joinToString("\n") { i ->
                        withinBudget()
                        val x = start + step * Num.of(i)
                        when (val s = f.at(x)) {
                            is Sample.Defined -> "${show(x)}  →  ${show(s.y)}"
                            is Sample.Undefined -> "${show(x)}  →  undefined"
                            is Sample.Unevaluated -> "${show(x)}  →  not evaluated"
                        }
                    })
                }
                "Sum Σ", "Product Π" -> {
                    val sum = operation == "Sum Σ"
                    val from = whole(v[1], "From")
                    val to = whole(v[2], "To")
                    require(from <= to) { "From must not be greater than To." }
                    val limit = if (sum) MAX_SUM_TERMS else MAX_PRODUCT_TERMS
                    require(to - from < BigInteger.valueOf(limit.toLong())) { "Use at most $limit terms." }
                    var total: Num = if (sum) Num.ZERO else Num.ONE
                    var k = from
                    while (k <= to) {
                        withinBudget()
                        val term = when (val s = f.at(Num.of(k))) {
                            is Sample.Defined -> s.y
                            else -> throw IllegalArgumentException("f(x) has no value at x = $k.")
                        }
                        total = if (sum) total + term else total * term
                        k += BigInteger.ONE
                    }
                    ToolAnswer("${if (sum) "Σ" else "Π"} f(x) for x = $from … $to\n= ${show(total)}", plain(total))
                }
                else -> throw IllegalArgumentException("Unknown calculation.")
            }
        }
    }
}
