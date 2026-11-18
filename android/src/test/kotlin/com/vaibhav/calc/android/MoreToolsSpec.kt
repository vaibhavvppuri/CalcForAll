package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.eval.GroupingMode
import com.vaibhav.calc.core.eval.NumeralSystem
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.Dec
import java.math.BigDecimal
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import java.math.MathContext
import java.time.LocalDate

/**
 * The 1.2 tool adapters, headless. The first test walks the whole catalogue, so an
 * operation that is listed but not wired — or whose own defaults fail — cannot ship.
 */
class MoreToolsSpec : StringSpec({
    val today = LocalDate.of(2026, 11, 12)
    fun tool(name: String, operation: String, vararg values: String, settings: Settings = Settings()) =
        MoreTools.calculate(name, operation, values.toList(), settings, today)
    /** What the calculator makes of [text] — used to read an inserted answer back. */
    fun number(text: String): Double =
        ((Calculator().evaluate(text) as CalcResult.Ok).value as RealValue).num.toBigDecimal(MathContext(20)).toDouble()

    /** Same cent-value oracle, with the estimate provenance retained on insertion. */
    fun estimatedCents(answer: ToolAnswer, expected: String) {
        answer.error shouldBe false
        val value = ((Calculator().evaluate(checkNotNull(answer.insert)) as CalcResult.Ok).value as RealValue).num
        (value is Dec) shouldBe true
        value.toBigDecimal(MathContext(64)).compareTo(BigDecimal(expected)) shouldBe 0
        (value as Dec).absoluteError shouldBe null
    }

    "every catalogued operation computes its own defaults" {
        val catalog = MoreTools.catalog(today)
        catalog.map { it.name }.distinct().size shouldBe catalog.size
        catalog.forEach { spec ->
            spec.operations.map { it.name }.distinct().size shouldBe spec.operations.size
            spec.operations.forEach { operation ->
                val answer = MoreTools.calculate(spec.name, operation.name, operation.fields.map { it.default }, Settings(), today)
                withClue("${spec.name} / ${operation.name}: ${answer.text}") {
                    answer.error shouldBe false
                    answer.text.isNotBlank() shouldBe true
                }
            }
        }
    }

    "an unknown tool, operation or field count is refused, not guessed" {
        tool("Money", "Lottery", "1").error shouldBe true
        tool("Astrology", "Solve", "1").error shouldBe true
        tool("Money", "Loan EMI", "500000", "8.5").error shouldBe true
    }

    "loan EMI shows money to two decimals and inserts a parser-ready number" {
        val answer = tool("Money", "Loan EMI", "1000000", "8.5", "240")
        answer.error shouldBe false
        answer.text.lines().first() shouldBe "Monthly payment: 8678.23"
        estimatedCents(answer, "8678.23")
        (Calculator().evaluate(answer.insert!!) is CalcResult.Ok) shouldBe true
    }

    "money follows the user's grouping and numerals while inserted text stays plain" {
        val grouped = tool("Money", "SIP", "5000", "12", "120", settings = Settings(grouping = GroupingMode.INDIAN))
        grouped.text shouldContain "Future value: 11,61,695.38"
        grouped.text shouldContain "Amount invested: 6,00,000.00"
        estimatedCents(grouped, "1161695.38")
        val devanagari = tool("Money", "Add GST", "1000", "18", settings = Settings(numerals = NumeralSystem.DEVANAGARI))
        devanagari.text shouldContain "Tax: १८०.००"
        devanagari.insert shouldBe "1180.00"
    }

    "GST is added and removed exactly, with the CGST + SGST split" {
        tool("Money", "Add GST", "1000", "18").text.lines() shouldBe
            listOf("Tax: 180.00", "Price including tax: 1180.00", "If split as CGST + SGST: 90.00 each")
        tool("Money", "Remove GST", "1180", "18").text.lines().first() shouldBe "Price before tax: 1000.00"
    }

    "percent change is signed" {
        tool("Money", "Percent change", "80", "100").text.lines().first() shouldBe "Change: +25%"
        val fall = tool("Money", "Percent change", "100", "80").text.lines().first()
        fall shouldContain "20%"
        fall shouldNotContain "+"
    }

    "impossible money inputs explain themselves" {
        tool("Money", "Loan EMI", "0", "8.5", "60").error shouldBe true
        val months = tool("Money", "Loan EMI", "500000", "8.5", "2.5")
        months.error shouldBe true
        months.text shouldContain "whole number"
        tool("Money", "Discount", "100", "150").error shouldBe true
        tool("Money", "Loan EMI", "500000", "", "60").error shouldBe true
    }

    "dates: days between, adding days, and age" {
        val between = tool("Dates", "Days between", "2026-01-01", "12/11/2026")
        between.text.lines().take(3) shouldBe listOf("315 days", "45 weeks 0 days", "0 years 10 months 11 days")
        between.insert shouldBe "315"
        tool("Dates", "Add days", "2026-11-12", "100").text.lines().first() shouldBe "2027-02-20 (Saturday)"
        val age = tool("Dates", "Age", "1990-11-12", "2026-11-12")
        age.text.lines().take(3) shouldBe listOf("36 years 0 months 0 days", "13149 days in total", "Birthday today")
        age.insert shouldBe "36"
        tool("Dates", "Days between", "31/02/2026", "2026-03-01").error shouldBe true
        tool("Dates", "Age", "2030-01-01", "2026-01-01").error shouldBe true
    }

    "the solver gives exact polynomial roots and points other equations at the numeric search" {
        tool("Equation solver", "Solve", "x^3-6x^2+11x-6").text.lines().first() shouldBe "x = 1, 2, 3"
        tool("Equation solver", "Solve", "x^3-2=0").text shouldStartWith "≈ x = 1.25992104989"
        val refused = tool("Equation solver", "Solve", "sin(x)=0.5")
        refused.error shouldBe true
        refused.text shouldContain "Numeric on a range"
    }

    "numeric solving checks its roots and states the angle mode" {
        val answer = tool("Equation solver", "Numeric on a range", "cos(x)=x", "0", "2", settings = Settings(angle = AngleMode.RAD))
        answer.error shouldBe false
        answer.text shouldContain "x ≈ 0.7390851332"
        answer.text shouldContain "Angle mode: RAD"
        tool("Equation solver", "Numeric on a range", "2^x+1", "-5", "5").text shouldStartWith "No solution found"
        tool("Equation solver", "Numeric on a range", "x=1=2", "0", "1").error shouldBe true
    }

    "tables show exact values and name the gaps" {
        tool("Table & series", "Table", "x^2", "-1", "1", "1/2").text.lines() shouldBe
            listOf("-1  →  1", "-0.5  →  0.25", "0  →  0", "0.5  →  0.25", "1  →  1")
        tool("Table & series", "Table", "1/x", "-1", "1", "1").text.lines()[1] shouldBe "0  →  undefined"
        tool("Table & series", "Table", "x", "0", "1000", "1").error shouldBe true
        tool("Table & series", "Table", "x", "0", "10", "0").error shouldBe true
    }

    "number theory: factors, primality, divisors, GCD and LCM" {
        tool("Number theory", "Prime factors", "360").text shouldBe "360 = 2^3 × 3^2 × 5"
        tool("Number theory", "Prime factors", "97").text shouldBe "97 is prime."
        tool("Number theory", "Is it prime?", "91").text shouldBe "91 is not prime: 91 = 7 × 13"
        tool("Number theory", "Is it prime?", "1").text shouldContain "primes start at 2"
        tool("Number theory", "Divisors", "28").text.lines() shouldBe listOf("6 divisors", "1, 2, 4, 7, 14, 28")
        tool("Number theory", "GCD and LCM", "84, 36 120").text.lines() shouldBe listOf("GCD: 12", "LCM: 2520")
        tool("Number theory", "Prime factors", "2.5").error shouldBe true
        tool("Number theory", "Prime factors", "10^15").error shouldBe true
    }

    "vectors: exact products, a surd length, and an angle in the calculator's mode" {
        tool("Vectors", "Dot product", "1, 2, 3", "4, 5, 6").insert shouldBe "32"
        tool("Vectors", "Cross product", "1, 2, 3", "4, 5, 6").text shouldBe "(-3, 6, -3)"
        tool("Vectors", "Length", "3, 4").insert shouldBe "5"
        tool("Vectors", "Length", "1, 1").text shouldBe "√2"
        tool("Vectors", "Angle between", "1, 0", "0, 1").text.lines().first() shouldBe "90 degrees"
        tool("Vectors", "Angle between", "1, 0", "1, 1").text.lines().first() shouldContain "45"
        tool("Vectors", "Projection", "3, 4", "1, 0").text shouldBe "(3, 0)"
        tool("Vectors", "Dot product", "1, 2", "1, 2, 3").error shouldBe true
    }

    "probability: exact binomial fractions, and normal table values" {
        val coin = tool("Probability", "Binomial", "10", "3", "1/2")
        coin.text.lines().first() shouldBe "P(X = 3): 15/128 (0.1171875)"
        coin.insert shouldBe "(15/128)"
        tool("Probability", "Normal", "100", "15", "130").text.lines().take(2) shouldBe
            listOf("z-score: 2", "P(X ≤ x): ≈ 0.977249868052")
        tool("Probability", "Poisson", "4", "2").text.lines().first() shouldContain "0.14652511111"
        tool("Probability", "Binomial", "10", "11", "1/2").error shouldBe true
        tool("Probability", "Normal", "0", "0", "1").error shouldBe true
    }

    "curve fitting recovers the curve the data came from" {
        tool("Curve fitting", "Quadratic", "0, 1, 2, 3, 4", "1, 3, 7, 13, 21").text.lines().take(2) shouldBe
            listOf("y = (1)x² + (1)x + (1)", "r² = 1")
        tool("Curve fitting", "Power", "1, 2, 3, 4", "3, 12, 27, 48").text shouldContain "x^(≈ 2)"
        tool("Curve fitting", "Quadratic", "0, 1", "1, 2").error shouldBe true
        tool("Curve fitting", "Exponential", "0, 1, 2", "1, 2").error shouldBe true
    }

    "Taylor polynomials keep exact coefficients and insert in radians" {
        val sine = tool("Series & limits", "Taylor polynomial", "sin(x)", "0", "5")
        sine.text.lines().first() shouldBe "x - x^3/6 + x^5/120"
        sine.insert shouldBe "(x - x^3/6 + x^5/120)"
        sine.insertAngle shouldBe AngleMode.RAD
        tool("Series & limits", "Taylor polynomial", "ln(x)", "0", "3").error shouldBe true
        tool("Series & limits", "Taylor polynomial", "sin(x)", "0", "11").error shouldBe true
    }

    "limits are labelled estimates and say what each side does" {
        val sinc = tool("Series & limits", "Limit", "sin(x)/x", "0")
        sinc.text.lines().first() shouldBe "≈ 1"
        sinc.text shouldContain "not a proof"
        tool("Series & limits", "Limit", "(1+1/x)^x", "inf").text.lines().first() shouldContain "2.71828"
        val jump = tool("Series & limits", "Limit", "abs(x)/x", "0").text
        jump shouldContain "From the left: ≈ -1"
        jump shouldContain "From the right: ≈ 1"
        jump shouldContain "does not appear to exist"
        tool("Series & limits", "Limit", "1/x^2", "0").text shouldContain "+∞"
        tool("Series & limits", "Limit", "sin(ln(abs(x)))", "0").text shouldContain "does not settle"
    }

    "review regressions: honest wording, tails inside the documented ranges, and rounded values marked ≈" {
        // A calculator limit is not a statement about the mathematics.
        val unevaluated = tool("Series & limits", "Limit", "exp(-x)", "inf").text
        unevaluated shouldContain "could not be evaluated"
        unevaluated shouldNotContain "does not settle"
        val hole = tool("Series & limits", "Limit", "sin(1/x)", "0").text
        hole shouldContain "could not be estimated"
        hole shouldNotContain "does not appear to exist"
        // Tails that used to be refused by the number tower's absorption guard.
        val poisson = tool("Probability", "Poisson", "4", "100")
        poisson.error shouldBe false
        poisson.text shouldContain "P(X ≤ 100): ≈ 1"
        val normal = tool("Probability", "Normal", "0", "1", "-15")
        normal.error shouldBe false
        normal.text.lines().last() shouldBe "P(X > x): ≈ 1"
        tool("Curve fitting", "Exponential", "1, 2, 3", "2, 4, 8").text shouldContain "0.69314718056"
        // A fraction too long to read is shown, and inserted, as its rounded decimal.
        val coins = tool("Probability", "Binomial", "2000", "1000", "1/2")
        coins.error shouldBe false
        (coins.text.lines().first().length < 60) shouldBe true
        coins.text.lines().first() shouldStartWith "P(X = 1000): ≈ 0.0178390111"
        coins.insert!! shouldStartWith "approx("
        // Rounded values carry the mark; exact ones do not.
        tool("Money", "Percent change", "3", "4").text.lines().first() shouldBe "Change: ≈ +33.3333333333%"
        tool("Everyday", "Fuel cost", "100", "3", "1").text.lines().first() shouldBe "Fuel needed: ≈ 33.3333333333"
        // 29 February: the age and the birthday notice agree.
        tool("Dates", "Age", "2000-02-29", "2023-02-28").text.lines().take(3) shouldBe
            listOf("22 years 11 months 27 days", "8400 days in total", "Next birthday tomorrow")
        tool("Dates", "Age", "2000-02-29", "2023-03-01").text.lines().take(3) shouldBe
            listOf("23 years 0 months 0 days", "8401 days in total", "Birthday today")
    }

    "programmer tool: bitwise results in four bases" {
        tool("Programmer", "AND", "1100", "1010", "2").text.lines() shouldBe
            listOf("Decimal: 8", "Hexadecimal: 8", "Binary: 1000", "Octal: 10")
        tool("Programmer", "NOT", "1011", "8", "2").text.lines() shouldBe
            listOf("Decimal: 244", "Hexadecimal: F4", "Binary: 11110100", "Octal: 364")
        tool("Programmer", "Shift", "FF", "4", "16").insert shouldBe "4080"
        tool("Programmer", "XOR", "12", "10", "2").error shouldBe true
        tool("Programmer", "NOT", "1011", "12", "2").error shouldBe true
    }

    "money inverses: the loan a payment affords and the instalment a goal needs" {
        tool("Money", "Loan you can afford", "8678.23", "8.5", "240").text.lines().first() shouldStartWith "Loan amount: 999999."
        tool("Money", "Savings goal", "1000000", "12", "120").text.lines().first() shouldStartWith "Invest each month: 4304.0"
    }

    "everyday: percent of, unit price, fuel, proportion and BMI" {
        tool("Everyday", "Percent of", "15", "2400").insert shouldBe "360"
        tool("Everyday", "Unit price", "120", "500", "200", "1000").text.lines() shouldBe
            listOf("A costs per unit: 0.24", "B costs per unit: 0.2", "B is cheaper by ≈ 16.6666666667% per unit.")
        tool("Everyday", "Unit price", "10", "2", "20", "4").text shouldContain "Both cost the same"
        tool("Everyday", "Fuel cost", "350", "17.5", "104.5").text.lines() shouldBe listOf("Fuel needed: 20", "Cost: 2090.00")
        tool("Everyday", "Proportion", "3", "450", "5").insert shouldBe "750"
        val bmi = tool("Everyday", "BMI", "70", "175")
        bmi.text.lines().take(2) shouldBe listOf("BMI: ≈ 22.86", "Normal weight")
        tool("Everyday", "BMI", "64", "160").text.lines().first() shouldBe "BMI: 25.00"
        bmi.text shouldContain "not a diagnosis"
        tool("Everyday", "BMI", "70", "0").error shouldBe true
    }

    "time: adding and subtracting durations, clock gaps, and seconds" {
        tool("Time", "Add durations", "1:45:30", "0:50:45").text.lines() shouldBe listOf("2:36:15", "2 h 36 min 15 s", "9375 seconds")
        val negative = tool("Time", "Subtract durations", "0:10:00", "0:20:00")
        negative.text.lines().first() shouldBe "−0:10:00"
        negative.insert shouldBe "(-600)"
        val overnight = tool("Time", "Between clock times", "22:00", "06:00")
        overnight.text.lines().first() shouldBe "8:00:00"
        overnight.text shouldContain "next day"
        tool("Time", "Seconds to d h m s", "100000").text.lines().first() shouldBe "1 d 3 h 46 min 40 s"
        tool("Time", "Add durations", "1:75", "0:10").error shouldBe true
        tool("Time", "Between clock times", "25:00", "06:00").error shouldBe true
    }

    "loan schedule: one line per year, ending at a zero balance" {
        val lines = tool("Money", "Loan schedule", "100000", "12", "12").text.lines()
        lines shouldBe listOf("Monthly payment: 8884.88", "Year 1: interest 6618.55, principal 100000.00, balance 0.00")
        tool("Money", "Loan schedule", "1000000", "8.5", "240").text.lines().size shouldBe 21
        tool("Money", "Loan schedule", "1000000", "8.5", "601").error shouldBe true
    }

    "matrices: a linear system solved exactly, and eigenvalues through the equation solver" {
        fun matrices(operation: String, a: String, b: String = "") = ToolCalculator.calculate("Matrices", operation, a, b, "", Settings())
        // 2x + y = 3, x + 3y = 5  →  x = 4/5, y = 7/5
        matrices("Solve system", "2, 1; 1, 3", "3; 5").text.lines() shouldBe listOf("[ 0.8 ]", "[ 1.4 ]")
        matrices("Solve system", "1, 1, 1; 0, 2, 5; 2, 5, -1", "6; -4; 27").text.lines() shouldBe listOf("[ 5 ]", "[ 3 ]", "[ -2 ]")
        matrices("Solve system", "1, 2; 2, 4", "1; 2").error shouldBe true
        matrices("Eigenvalues", "2, 1; 1, 2").text shouldStartWith "λ = 1, 3"
        matrices("Eigenvalues", "1, 0, 0; 0, 2, 0; 0, 0, 3").text shouldStartWith "λ = 1, 2, 3"
        matrices("Eigenvalues", "2, 1, 0, 0; 1, 2, 1, 0; 0, 1, 2, 1; 0, 0, 1, 2").text shouldStartWith "≈ λ = 0.38196601125"
        matrices("Eigenvalues", "1, 2, 3; 4, 5, 6").error shouldBe true
    }

    "1.3— geometry answers stay exact in π and square roots" {
        tool("Geometry", "Circle", "5").text.lines().first() shouldStartWith "Area: 25π"
        tool("Geometry", "Circle", "5").text.lines().first() shouldContain "78.5398163397"
        tool("Geometry", "Triangle from 3 sides", "3", "4", "5").text.lines().first() shouldBe "Area: 6"
        tool("Geometry", "Right triangle", "5", "12").text.lines().first() shouldBe "Longest side: 13"
        tool("Geometry", "Rectangle", "1", "1").text.lines()[2] shouldStartWith "Diagonal: √2"
        tool("Geometry", "Sphere", "3").text.lines().first() shouldStartWith "Volume: 36π"
        tool("Geometry", "Cone", "3", "4").text.lines()[1] shouldBe "Slant height: 5"
        tool("Geometry", "Triangle from 3 sides", "1", "2", "10").error shouldBe true
        tool("Geometry", "Circle", "-1").error shouldBe true
    }

    "1.3— sequences give any term and the sum exactly" {
        tool("Sequences", "Arithmetic", "3", "4", "10").text.lines().take(2) shouldBe listOf("Term 10: 39", "Sum of 10 terms: 210")
        tool("Sequences", "Geometric", "2", "3", "8").text.lines().take(2) shouldBe listOf("Term 8: 4374", "Sum of 8 terms: 6560")
        tool("Sequences", "Geometric", "1", "1/2", "10").text shouldContain "Sum to infinity: 2"
        tool("Sequences", "Geometric", "5", "1", "4").text.lines()[1] shouldBe "Sum of 4 terms: 20"
        tool("Sequences", "Arithmetic", "3", "4", "0").error shouldBe true
    }

    "1.3— business: margin, markup, break-even and growth rate" {
        tool("Business", "Margin and markup", "80", "100").text.lines() shouldBe
            listOf("Profit: 20.00", "Margin: 20% of the price", "Markup: 25% of the cost")
        tool("Business", "Price for a margin", "80", "20").text.lines().first() shouldBe "Selling price: 100.00"
        tool("Business", "Break-even", "50000", "250", "150").text.lines().take(2) shouldBe
            listOf("Units to break even: 500", "Whole units needed: 500")
        tool("Business", "Break-even", "1000", "30", "7").text.lines()[1] shouldBe "Whole units needed: 44"
        tool("Business", "Growth rate", "100000", "161051", "5").text.lines().first() shouldContain "10%"
        tool("Business", "Break-even", "1000", "10", "10").error shouldBe true
        tool("Business", "Price for a margin", "80", "100").error shouldBe true
    }

    "1.3 — a percent sign typed in a percent field is not applied a second time" {
        tool("Business", "Price for a margin", "80", "20%").text shouldBe tool("Business", "Price for a margin", "80", "20").text
        tool("Business", "Price for a margin", "80", " 20 % ").text.lines().first() shouldBe "Selling price: 100.00"
        tool("Money", "Discount", "2499", "20%").text shouldBe tool("Money", "Discount", "2499", "20").text
        tool("Money", "Add GST", "1000", "18%").text shouldBe tool("Money", "Add GST", "1000", "18").text
        tool("Everyday", "Percent of", "15%", "2400").text shouldBe tool("Everyday", "Percent of", "15", "2400").text
        tool("Money", "Add GST", "1000", "%").error shouldBe true
    }

    "1.3 — extras: numbers and amounts in words, a share in a ratio, inflation" {
        tool("Everyday", "Number in words", "1234567").text.lines() shouldBe listOf(
            "Indian: twelve lakh thirty-four thousand five hundred and sixty-seven",
            "International: one million two hundred and thirty-four thousand five hundred and sixty-seven")
        tool("Everyday", "Number in words", "2.5").error shouldBe true
        tool("Everyday", "Number in words", "10^21").error shouldBe true
        tool("Money", "Amount in words", "123456.50").text shouldBe "Rupees one lakh twenty-three thousand four hundred and fifty-six and paise fifty only"
        tool("Money", "Amount in words", "5000").text shouldBe "Rupees five thousand only"
        tool("Money", "Amount in words", "10.005").error shouldBe true // a third decimal place is refused, not rounded away
        tool("Money", "Amount in words", "-5").error shouldBe true
        val split = tool("Everyday", "Share in a ratio", "1200", "2:3:5")
        split.text.lines() shouldBe listOf("Part 1 (2): 240", "Part 2 (3): 360", "Part 3 (5): 600", "Simplest ratio: 2 : 3 : 5")
        tool("Everyday", "Share in a ratio", "1000", "1, 2").text.lines().first() shouldBe "Part 1 (1): 1000/3  (≈ 333.333333333)"
        tool("Everyday", "Share in a ratio", "100", "1.5 : 2.5").text.lines().last() shouldBe "Simplest ratio: 3 : 5"
        tool("Everyday", "Share in a ratio", "100", "5").error shouldBe true
        tool("Everyday", "Share in a ratio", "100", "2:0").error shouldBe true
        // A colon separates the parts, and then nothing else does (the seventh review pass): a grouping
        // or decimal comma inside a part is the engine's syntax refusal, not two more parts, and a
        // space inside a part belongs to that part's expression.
        tool("Everyday", "Share in a ratio", "4000", "1,500:2,500").error shouldBe true
        tool("Everyday", "Share in a ratio", "4000", "1,5:2,5").error shouldBe true
        tool("Everyday", "Share in a ratio", "100", "1 +1 : 2").text.lines() shouldBe
            listOf("Part 1 (2): 50", "Part 2 (2): 50", "Simplest ratio: 1 : 1")
        tool("Everyday", "Share in a ratio", "100", "2 3 5").error shouldBe true
        // The eighth pass: the same holds for a semicolon. One kind of separator at a time.
        tool("Everyday", "Share in a ratio", "4000", "1,500; 2,500").error shouldBe true
        tool("Everyday", "Share in a ratio", "100", "1,5; 2,5").error shouldBe true
        tool("Everyday", "Share in a ratio", "1200", "2;3;5").text.lines().last() shouldBe "Simplest ratio: 2 : 3 : 5"
        tool("Everyday", "Share in a ratio", "20", "nCr(5,2):10").text.lines().last() shouldBe "Simplest ratio: 1 : 1"
        val inflation = tool("Money", "Inflation", "1000", "6", "10")
        inflation.text.lines().take(2) shouldBe listOf("The same goods will cost: 1790.85", "The same money will buy what costs today: 558.39")
        estimatedCents(inflation, "1790.85")
        tool("Money", "Inflation", "1000", "6%", "10").text shouldBe inflation.text
    }

    "1.3 — a multiple of e goes into a tool and comes back as text the calculator reads" {
        tool("Geometry", "Circle", "2*e").error shouldBe false
        tool("Geometry", "Rectangle", "e", "2").insert shouldBe "(2*e)"
        val sum = tool("Sequences", "Arithmetic", "e", "e", "10")
        sum.insert shouldBe "(55*e)"
        // typing on keeps the value: 55·e + 3, never 55 × 10³
        number(sum.insert!! + "+3") shouldBe (55 * Math.E + 3 plusOrMinus 1e-9)
    }

    "1.3 — an inserted answer stays one value when the user types on" {
        number(tool("Time", "Subtract durations", "0:10:00", "0:20:00").insert!! + "^2") shouldBe (360000.0 plusOrMinus 1e-6)
        number(tool("Probability", "Binomial", "10", "3", "1/2").insert!! + "^2") shouldBe (Math.pow(15.0 / 128, 2.0) plusOrMinus 1e-12)
        number(tool("Geometry", "Circle", "5").insert!! + "^2") shouldBe (Math.pow(25 * Math.PI, 2.0) plusOrMinus 1e-6)
        asOneValue("8678.23") shouldBe "8678.23"
        asOneValue("approx(1.5)") shouldBe "approx(1.5)"
        asOneValue("sqrt(2)/2") shouldBe "(sqrt(2)/2)"
        asOneValue("(1/3)") shouldBe "(1/3)"
        asOneValue("(1/3)+(1/4)") shouldBe "((1/3)+(1/4))"
        asOneValue("-600") shouldBe "(-600)"
    }

    "1.3 — a geometric sequence with an irrational ratio is summed when its power dwarfs 1 or vanishes against it" {
        val growing = tool("Sequences", "Geometric", "1", "sqrt(2)", "333")
        withClue(growing.text) { growing.error shouldBe false }
        val expected = (Math.pow(2.0, 166.0) * Math.sqrt(2.0) - 1) / (Math.sqrt(2.0) - 1)
        (number(growing.insert!!) / expected) shouldBe (1.0 plusOrMinus 1e-12)
        val fading = tool("Sequences", "Geometric", "1", "sqrt(2)/2", "331")
        withClue(fading.text) { fading.error shouldBe false }
        number(fading.insert!!) shouldBe (2 + Math.sqrt(2.0) plusOrMinus 1e-9)
        fading.text shouldContain "Sum to infinity"
        // exact inputs keep the exact path and its exact answer
        tool("Sequences", "Geometric", "3", "2", "10").insert shouldBe "3069"
    }

    "1.3 — break-even: exact amounts are counted exactly; approximate ones only when the count is beyond doubt" {
        fun units(vararg values: String, settings: Settings = Settings()) =
            tool("Business", "Break-even", *values, settings = settings).text.lines()[1]
        fun refused(vararg values: String): String {
            val answer = tool("Business", "Break-even", *values)
            withClue("${values.toList()} → ${answer.text}") { answer.error shouldBe true }
            return answer.text
        }
        // Exact amounts: an exact quotient, an exact ceiling, however fine the excess.
        units("1000", "30", "7") shouldBe "Whole units needed: 44"
        units("50000", "250", "150") shouldBe "Whole units needed: 500"
        units("30.0000001", "10", "0") shouldBe "Whole units needed: 4"
        units("30.00000000000000000000000001", "10", "0") shouldBe "Whole units needed: 4"

        // An exact irrational quotient (a multiple of π, a surd) is exact too, and its ceiling is taken
        // from as many digits as it needs — fifty are not enough for π·10^50 (the eighth pass: one short).
        units("100*pi", "1", "0") shouldBe "Whole units needed: 315"
        units("pi*10^50", "1", "0") shouldBe "Whole units needed: 314159265358979323846264338327950288419716939937511"

        // Approximate amounts, with the answer nowhere near a whole number: counted.
        units("30.001", "10+sqrt(3)", "sqrt(3)") shouldBe "Whole units needed: 4"
        units("1000", "12.5*sqrt(2)", "5") shouldBe "Whole units needed: 79"            // 1000 / 12.6777 = 78.88
        units("5000+sqrt(2)", "10^19+1", "10^19") shouldBe "Whole units needed: 5002"
        units("5000+sqrt(2)", "1000", "991", settings = Settings(displayPrecision = 32, computePrecision = 44)) shouldBe
            "Whole units needed: 556"

        // A propagated enclosure crossing an integer cannot choose a count. An explicitly
        // positive excess that is larger than that enclosure now correctly establishes four.
        refused("30", "10+sqrt(3)", "sqrt(3)") shouldContain "uncertainty"
        refused("5*(sqrt(2)+2)", "sqrt(2)+2", "0") shouldContain "uncertainty"
        refused("30", "10+sqrt(7)*10^12", "sqrt(7)*10^12") shouldContain "uncertainty"
        units("30.00000000000000000000000001", "10+sqrt(7)*10^12", "sqrt(7)*10^12") shouldBe "Whole units needed: 4"
        units("30.00000000000001", "10+sqrt(7)*10^23", "sqrt(7)*10^23") shouldBe "Whole units needed: 4"
        units("30.0000000000000000001", "10+sqrt(7)*10^12", "sqrt(7)*10^12") shouldBe "Whole units needed: 4"
        refused("30+(ln(21)-ln(3)-ln(7))*10^5", "20", "10")
        refused("30+(sqrt(26)+sqrt(34)-sqrt(26)-sqrt(34))*10^16", "20", "10")
        // …too few digits left after the subtraction, or a count too long for the displayed digits.
        refused("30", "10+sqrt(7)*10^30", "sqrt(7)*10^30") shouldContain "uncertainty"
        units("30.00000000001", "10+sqrt(7)*10^26", "sqrt(7)*10^26") shouldBe "Whole units needed: 4"
        refused("30000000000040", "10+sqrt(7)*10^26", "sqrt(7)*10^26")
    }

    "1.3— quartiles by the median-of-halves method, and modes" {
        tool("Data", "Quartiles and mode", "1, 2, 3, 4, 5, 6, 7, 8").text.lines() shouldBe listOf(
            "Lower quartile Q1: 2.5", "Median: 4.5", "Upper quartile Q3: 6.5", "Interquartile range: 4", "Mode: no value repeats")
        tool("Data", "Quartiles and mode", "2 4 4 5 7 7").text.lines().last() shouldBe "Mode: 4, 7 (2 times each)"
        tool("Data", "Quartiles and mode", "5").error shouldBe true
    }

    "sums and products are exact, bounded, and refuse a term that has no value" {
        val squares = tool("Table & series", "Sum Σ", "x^2", "1", "10")
        squares.text.lines().last() shouldBe "= 385"
        squares.insert shouldBe "385"
        tool("Table & series", "Product Π", "x", "1", "10").insert shouldBe "3628800"
        tool("Table & series", "Sum Σ", "1/x", "1", "4").insert shouldBe "(25/12)"
        tool("Table & series", "Sum Σ", "1/x", "0", "4").error shouldBe true
        tool("Table & series", "Sum Σ", "x", "1", "100000").error shouldBe true
    }
})
