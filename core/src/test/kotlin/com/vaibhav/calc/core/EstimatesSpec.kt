package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.eval.ComplexValue
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.graph.RootResult
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.NumMath
import com.vaibhav.calc.core.num.NumericLimits
import com.vaibhav.calc.core.num.bounds
import com.vaibhav.calc.core.num.toBigIntegerExactOrNull
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import com.vaibhav.calc.core.symbolic.Limits
import io.kotest.assertions.withClue
import io.kotest.assertions.throwables.shouldThrow
import com.vaibhav.calc.core.num.ArithFault
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/** Historical cancellation cases, retaining their mathematical oracles under established bounds.
 * Unsupported identities must be refused rather than inferred from agreement between precisions.
 */
class EstimatesSpec : StringSpec({
    val c = Calculator()
    fun num(text: String): Num = (c.evaluate(text).getOrThrow() as RealValue).num
    fun decimal(text: String): BigDecimal = num(text).toBigDecimal(MathContext(50))
    fun whole(text: String): BigInteger = num(text).toBigIntegerExactOrNull() ?: throw AssertionError("'$text' is not a whole number: ${num(text)}")
    fun refusal(text: String): EvalError =
        (c.evaluate(text) as? CalcResult.Err)?.error ?: throw AssertionError("'$text' was answered: ${num(text)}")
    fun same(actual: BigDecimal, expected: BigDecimal) = withClue("got $actual, wanted $expected") { actual.compareTo(expected) shouldBe 0 }
    fun wholes(cases: Map<String, Int>) =
        cases.forEach { (text, expected) -> withClue(text) { whole(text) shouldBe BigInteger.valueOf(expected.toLong()) } }
    // These identities are mathematically known, but are outside the current structural rules.
    // Preserve the numerical oracle if supported; an uncertainty refusal is preferable to a wrong integer.
    fun unprovedWholes(cases: Map<String, Int>) = cases.forEach { (text, expected) -> withClue(text) {
        when (val result = c.evaluate(text)) {
            is CalcResult.Ok -> (result.value as RealValue).num.toBigIntegerExactOrNull() shouldBe BigInteger.valueOf(expected.toLong())
            is CalcResult.Err -> (result.error is EvalError.Unsupported) shouldBe true
        }
    } }
    fun routeRefused(text: String, calculator: Calculator = c) = withClue(text) {
        (((calculator.route(text) as? RouteResult.Err)?.error) is EvalError.Unsupported) shouldBe true
    }
    // Independent plain-decimal Taylor oracle, without calculator kernels or their bounds.
    fun trigReferenceDegrees(angle: BigDecimal, cosine: Boolean): BigDecimal {
        val mc = MathContext(180)
        val x = angle.multiply(IndependentReference.PI, mc).divide(BigDecimal(180), mc)
        val negativeSquare = x.multiply(x, mc).negate()
        var term = if (cosine) BigDecimal.ONE else x
        var sum = term
        for (k in 1..100) {
            val denominator = if (cosine) (2L * k - 1) * (2L * k) else (2L * k) * (2L * k + 1)
            term = term.multiply(negativeSquare, mc).divide(BigDecimal(denominator), mc)
            sum = sum.add(term, mc)
        }
        return sum
    }
    val nearPoleAngle = "89.99999999999999999999999999"
    val nearPoleGap = BigDecimal(90).subtract(BigDecimal(nearPoleAngle))
    val nearPoleTangent = trigReferenceDegrees(nearPoleGap, true).divide(trigReferenceDegrees(nearPoleGap, false), MathContext(150))

    "a whole multiple of an estimate leaves nothing over" {
        // 8·(1+√2) is rounded to fifty digits and lands 2·10⁻⁴⁹ below eight times 1+√2. Read digit
        // for digit, the quotient was 7 and the "remainder" was the divisor to every digit shown.
        for (k in 2..30) withClue("k = $k") { same(decimal("mod($k*(1+sqrt(2)), 1+sqrt(2))"), BigDecimal.ZERO) }
        listOf(
            "mod(4*(sqrt(2)+sqrt(3)), sqrt(2)+sqrt(3))", "mod(11*(pi+1), pi+1)", "mod(11*(1+sqrt(5)), 1+sqrt(5))", "mod(6*(pi+e), pi+e)",
            // an estimate of k·π against π itself
            "mod(2*pi+1-1, pi)", "mod(9*pi+1-1, pi)", "mod(11*pi+1-1, pi)", "mod(12*pi+1-1, pi)",
            "mod(3*sqrt(2)+1-1, sqrt(2))", "mod(12*sqrt(2)+1-1, sqrt(2))",
            // a sum of estimates that is 1
            "mod(sin(1)^2+cos(1)^2, 1)", "mod(2*sin(1), sin(1))", "mod(approx(6,0), 3)",
        ).forEach { withClue(it) { same(decimal(it), BigDecimal.ZERO) } }
        // a quotient nowhere near a whole number is simply answered
        same(decimal("mod(approx(123.75,0), 10)"), BigDecimal("3.75"))
        same(decimal("mod(approx(-123.75,0), 10)"), BigDecimal("6.25"))
    }

    "an estimate that reads as a whole number is that whole number" {
        wholes(mapOf(
            // 8·(1+√2)/(1+√2) is 8 or a hair under it; its floor was 7
            "floor(8*(1+sqrt(2))/(1+sqrt(2)))" to 8, "ceil(8*(1+sqrt(2))/(1+sqrt(2)))" to 8,
            "int(8*(1+sqrt(2))/(1+sqrt(2)))" to 8, "round(8*(1+sqrt(2))/(1+sqrt(2)))" to 8,
            "floor(sin(20)^2+cos(20)^2)" to 1, "ceil(sin(20)^2+cos(20)^2)" to 1, "round(sin(20)^2+cos(20)^2)" to 1,
            // What people type: change-of-base logarithms, angles, inverse pairs. Read digit for
            // digit these came out one too low or one too high by the luck of a last digit: 1.2
            // answered floor(ln(125)/ln(5)) = 2, floor(e^(ln(10))) = 9 and floor(cbrt(2)^3) = 1.
            "floor(ln(8)/ln(2))" to 3, "floor(ln(81)/ln(3))" to 4, "floor(ln(125)/ln(5))" to 3, "ceil(ln(125)/ln(5))" to 3,
            "floor(atan2(1,1))" to 45, "floor(atan2(1,0))" to 90, "ceil(atan2(0,-1))" to 180,
            "floor(e^(ln(10)))" to 10, "ceil(e^(ln(10)))" to 10, "floor(exp(ln(5)))" to 5,
            "floor(cbrt(2)^3)" to 2, "ceil(cbrt(2)^3)" to 2, "int(cbrt(3)^3)" to 3,
            "floor(asin(0.3)+acos(0.3))" to 90, "floor(cosh(1)^2-sinh(1)^2)" to 1,
            // next to a half an estimate reads as that half, and a half rounds away from zero
            "round(5*(1+sqrt(2))/(1+sqrt(2))/2)" to 3, "round(7*(pi+1)/(pi+1)/2)" to 4,
            // Round trips through a function that magnifies its argument's rounding (tan near 90°,
            // acos near 1, atanh near 1). Their fifty-digit values are off by more than two digits
            // in fifty, and with a fixed allowance these read 24, 0, 3, 999, 6 and 6 (twelfth review).
            "floor(tan(atan(25)))" to 25, "int(tan(atan(25)))" to 25, "floor(acos(cos(1)))" to 1,
            "ceil(100*cos(acos(0.02)))" to 2, "floor(tan(atan(1000)))" to 1000, "floor(tan(atan(33)))" to 33,
            "ceil(atanh(tanh(5)))" to 5, "floor(atanh(tanh(7)))" to 7, "floor(atanh(tanh(20)))" to 20,
            "round(tan(atan(16.5)))" to 17, "round(tan(atan(45.5)))" to 46,
        ))
        unprovedWholes(mapOf("floor(tan(20)*tan(70))" to 1, "round(ln(sqrt(e)))" to 1,
            "round(ln(e^2.5))" to 3, "round(-ln(e^2.5))" to -3, "round(2*sin(15)*cos(15))" to 1))
        for (n in 2..130) withClue("tan(atan($n))") {
            whole("floor(tan(atan($n)))") shouldBe BigInteger.valueOf(n.toLong())
            whole("ceil(tan(atan($n)))") shouldBe BigInteger.valueOf(n.toLong())
        }
        for (k in 1..89) withClue("round(tan(atan($k.5)))") { whole("round(tan(atan($k.5)))") shouldBe BigInteger.valueOf(k + 1L) }
        same(num("frac(tan(atan(25)))").toBigDecimal(MathContext(50)), BigDecimal.ZERO)
        same(decimal("mod(tan(atan(25)), 5)"), BigDecimal.ZERO)
        same(num("frac(8*(1+sqrt(2))/(1+sqrt(2)))").toBigDecimal(MathContext(50)), BigDecimal.ZERO)
        // Old precision probes are not established bounds. Their agreement cannot justify snapping.
        val none = BigDecimal.ZERO
        val under = Dec(BigDecimal("2.9999999999999999999999999999999999999999999999999"))
        val over = Dec(BigDecimal("3.0000000000000000000000000000000000000000000000001"))
        shouldThrow<ArithFault.Unsupported> { NumMath.floor(under, none) }
        shouldThrow<ArithFault.Unsupported> { NumMath.ceil(over, none) }
        NumMath.floor(Dec(under.value, BigDecimal.ZERO)) shouldBe Num.of(2)
        NumMath.ceil(Dec(under.value, BigDecimal.ZERO)) shouldBe Num.of(3)
        NumMath.floor(Dec(over.value, BigDecimal.ZERO)) shouldBe Num.of(3)
        NumMath.ceil(Dec(over.value, BigDecimal.ZERO)) shouldBe Num.of(4)
        NumMath.round(Dec(BigDecimal("2.4999999999999999999999999999999999999999999999999"), BigDecimal.ZERO)) shouldBe Num.of(2)
        val fifty = Dec(BigDecimal("24.999999999999999999999999999999999999999999999919"))
        val fortyFive = Dec(BigDecimal("25.00000000000000000000000000000000000000000031"))
        val forty = Dec(BigDecimal("25.00000000000000000000000000000000000027"))
        val measured = NumMath.doubt(fifty, fortyFive, forty)
        shouldThrow<ArithFault.Unsupported> { NumMath.floor(fifty, measured) }
        NumMath.floor(Dec(fifty.value, BigDecimal.ZERO)) shouldBe Num.of(24)
        // estimates that are nowhere near a whole number are rounded as they always were
        wholes(mapOf("floor(approx(123.75,0))" to 123, "ceil(approx(123.75,0))" to 124, "floor(approx(-2.5,0))" to -3, "ceil(approx(-2.5,0))" to -2,
            "int(approx(7.9,0))" to 7, "int(approx(-7.9,0))" to -7, "round(approx(2.5,0))" to 3, "round(approx(-2.5,0))" to -3,
            "round(approx(2.4,0))" to 2, "floor(approx(5,0))" to 5, "ceil(approx(5,0))" to 5, "floor(sin(30)*7)" to 3,
            "floor(sin(20)*100)" to 34, "round(cos(20)*1000)" to 940, "ceil(ln(2)*10)" to 7, "int(e^2)" to 7))
        same(decimal("frac(approx(7.25,0))"), BigDecimal("0.25"))
    }

    "an estimate that is truly just short of a whole number keeps its own whole part" {
        // Decided at the engine's fifty digits, where the gap is plain. At the session's forty,
        // tanh(50) and log(10^38−1) are exactly 1 and 38, and their floors read 1 and 38.
        wholes(mapOf(
            "floor(tanh(50))" to 0, "ceil(tanh(-50))" to 0, "int(tanh(47))" to 0, "floor(log(10^38-1))" to 37,
            "floor(3-e^(-80))" to 2, "floor(3-e^(-90))" to 2, "ceil(3+e^(-90))" to 4, "round(2.5-e^(-90))" to 2,
        ))
        // the remainder is what is left above 2: one less 8.19…×10⁻⁴⁰
        val short = BigDecimal.ONE.subtract(decimal("mod(3-e^(-90), 1)"))
        withClue("1 − remainder = $short") { (short > BigDecimal("8.19E-40") && short < BigDecimal("8.20E-40")) shouldBe true }
    }

    "an extreme tangent is answered only with an established whole part" {
        // cot(delta) independently resolves the units even though old precision probes disagreed.
        whole("floor(tan($nearPoleAngle))") shouldBe nearPoleTangent.toBigInteger()
        whole("round(tan($nearPoleAngle))") shouldBe IndependentReference.nearest(nearPoleTangent)
        val remainder = num("mod(tan($nearPoleAngle),7)").bounds(120)!!
        val expected = IndependentReference.remainder(nearPoleTangent, BigDecimal(7))
        (expected >= remainder.lower && expected <= remainder.upper) shouldBe true
        // an ordinary large tangent is known to the units and is answered
        whole("floor(tan(89.9999))") shouldBe decimal("tan(89.9999)").toBigInteger()
    }

    "an estimate too large for its digits to reach the units has no whole part to give" {
        // sin(1)·10^60 is known to fifty digits; the other ten of its whole part were printed as zeros
        listOf("floor(sin(1)*10^60)", "ceil(sin(1)*10^60)", "int(sin(1)*10^60)", "round(sin(1)*10^60)", "frac(sin(1)*10^60)",
            "floor(approx(10^60))", "round(approx(10^60))", "frac(approx(10^60))",
            "mod(approx(10^60), 7)", "mod(approx(15*10^59), 7)", "mod(sin(1)*10^60, 7)", "mod(approx(10^30000), 7)",
            "mod(approx(10^60), pi)", "mod(pi*10^60, approx(1))",
        ).forEach { text -> withClue(text) { (refusal(text) is EvalError.Unsupported || refusal(text) is EvalError.Overflow) shouldBe true } }
        // the same size of number, exact, is answered: nothing here is about size alone
        whole("floor(pi*10^60)") shouldBe IndependentReference.PI.multiply(IndependentReference.tenTo(60)).toBigInteger()
        whole("mod(10^60, 7)") shouldBe BigInteger.TEN.pow(60).mod(BigInteger.valueOf(7))
        // and estimates whose digits do reach the units keep their whole parts: 30!/e is the
        // number of derangements of thirty things, and e^70 has thirty-one digits
        var factorial = BigInteger.ONE
        for (k in 2..30) factorial = factorial.multiply(BigInteger.valueOf(k.toLong()))
        whole("round(30!/e)") shouldBe IndependentReference.nearest(BigDecimal(factorial).divide(IndependentReference.E, IndependentReference.WIDE))
        whole("floor(e^70)") shouldBe IndependentReference.E.pow(70, IndependentReference.WIDE).toBigInteger()
        whole("floor(sin(1)*10^20)") shouldBe decimal("sin(1)*10^20").toBigInteger()
    }

    "a whole part is decided at the engine's full precision, whatever the session computes with" {
        Settings().computePrecision shouldBe 40
        Settings().trustedDigits shouldBe 36
        Settings(computePrecision = 16, displayPrecision = 12).trustedDigits shouldBe 12
        NumericLimits.MAX_SESSION_PRECISION shouldBe 50
        // sessions that compute with sixteen digits, or with all fifty, get the same answers
        listOf(Settings(computePrecision = 16, displayPrecision = 12), Settings(computePrecision = 50),
            Settings(computePrecision = 50, displayPrecision = 46), Settings(computePrecision = 45),
        ).forEach { settings ->
            val session = Calculator(settings)
            fun sessionWhole(text: String) = (session.evaluate(text).getOrThrow() as RealValue).num.toBigIntegerExactOrNull()
            withClue("computing with ${settings.computePrecision} digits") {
                sessionWhole("floor(ln(125)/ln(5))") shouldBe BigInteger.valueOf(3)
                sessionWhole("floor(tan(atan(25)))") shouldBe BigInteger.valueOf(25)
                sessionWhole("floor(tanh(50))") shouldBe BigInteger.ZERO
                sessionWhole("floor(3-e^(-80))") shouldBe BigInteger.valueOf(2)
            }
        }
        // a rounding function inside another is recomputed in its turn, and deep nesting stays quick
        whole("floor(floor(ln(125)/ln(5))+0.5)") shouldBe BigInteger.valueOf(3)
        val started = System.nanoTime()
        whole("floor(" .repeat(40) + "sin(20)*100" + ")".repeat(40)) shouldBe BigInteger.valueOf(34)
        withClue("forty nested floors") { ((System.nanoTime() - started) / 1e9 < 20.0) shouldBe true }
    }

    "an equation between estimates is not called false when they agree to every trusted digit" {
        fun verdictOf(calculator: Calculator, text: String): Boolean? = when (val r = calculator.route(text)) {
            is RouteResult.Ok -> (r.outcome as Outcome.Verdict).holds
            is RouteResult.Err -> {
                withClue("$text → ${r.error}") {
                    (r.error is EvalError.Unsupported) shouldBe true
                    r.error.detail shouldContain "cannot be said whether they are equal"
                }
                null
            }
            else -> throw AssertionError("'$text' did not evaluate: $r")
        }
        fun verdict(text: String): Boolean? = verdictOf(c, text)
        // True identities whose two sides are computed estimates: answered False until 1.3.
        (listOf(20, 40, 50, 7, 13, 77, 89).map { "sin($it)^2+cos($it)^2 = 1" } + listOf(
            "(2*e)^2 = 4*e^2",
            "sin(20)^2+cos(20)^2-1 = 0",        // the same statement with the subtraction already done
            "0 = 1-sin(20)^2-cos(20)^2",
            "-(sin(20)^2+cos(20)^2-1) = 0",
            "sin(20)^2 m + cos(20)^2 m = 1 m",   // with a unit
            "sin(20)^2 m + cos(20)^2 m - 1 m = 0 m",
            "(sin(20)^2 + cos(20)^2 - 1) m = 0 m",
            "10 m*sin(20)^2 + 10 m*cos(20)^2 - 10 m = 0 m",
            "approx(pi) = pi",                   // an estimate against the value it estimates
                // a difference below what a forty-digit estimate can show
            // identities that lose a few digits to cancellation on the way (twelfth review)
            "(sqrt(2)-1)^4 = 17-12*sqrt(2)", "(2-sqrt(3))^3 = 26-15*sqrt(3)", "(sqrt(10)-3)^2 = 19-6*sqrt(10)",
            "1/(sqrt(101)-10) = sqrt(101)+10", "(sqrt(101)+10)*(sqrt(101)-10) = 1",
            "cosh(3)^2 - sinh(3)^2 = 1", "cosh(6)^2 - sinh(6)^2 = 1", "1 - cos(1)^2 = sin(1)^2", "acos(cos(1)) = 1",
        )).forEach { withClue(it) { (verdict(it) != false) shouldBe true } }
        // Plain disagreements are still False, and equal values still True.
        mapOf(
            "ln(2) = 0.693" to false,
            "sin(20) = 0.3420201433" to false,
            "sin(20)^2+cos(20)^2 = 1.0000001" to false,
            "sin(20)^2+cos(20)^2 = 1+10^-25" to false,   // twenty-five digits is well within what the estimates can tell apart
            "sin(20)^2+cos(20)^2-1 = 5" to false,
            "sin(20)^2+cos(20)^2-2 = 0" to false,
            "sin(20) = cos(20)" to false,
            "5 m - 3 m = 0 m" to false,
            "sin(20) m = 0.34 m" to false,
            "sin(30) = 1/2" to true,
            "sin(20) = sin(20)" to true,
            "sin(20)-sin(20) = 0" to true,
            "1 m - 1 m = 0 m" to true,
            "sqrt(2)*sqrt(2) = 2" to true,
            "5 lakh = 500000" to true,
            // exact against exact is never undecided, however close
            "pi = 3.14159265358979323846264338327950288419716939937510" to false,
            "sqrt(2) = 1.41421356237309504880168872420969807856967187537694" to false,
        ).forEach { (text, expected) -> withClue(text) { verdict(text) shouldBe expected } }
        // A value kept in Ans or a register was computed at the session's precision, and so is the
        // other side. The eleventh pass computed statements at fifty digits and these came out False,
        // with the equations "solved" to about 10^41 (twelfth review).
        val session = Calculator()
        session.route("sin(20)")
        verdictOf(session, "Ans = sin(20)") shouldBe null
        verdictOf(session, "Ans - sin(20) = 0") shouldBe null
        routeRefused("Ans*x + 1 = sin(20)*x + 2", session)
        session.route("ln(2) → A")
        verdictOf(session, "A = ln(2)") shouldBe null
        routeRefused("A*x + 1 = ln(2)*x + 3", session)
        // Complex values are compared as points in the plane. De Moivre's identities leave rounding
        // in a part that should be zero; they were called False without any comparison at all.
        val complex = Calculator(Settings(complexEnabled = true))
        listOf("(cos(20)+i*sin(20))^9 = -1", "(cos(20)+i*sin(20))^18 = 1", "(cos(10)+i*sin(10))^9 = i")
            .forEach { withClue(it) { (verdictOf(complex, it) != false) shouldBe true } }
        mapOf(
            "(cos(20)+i*sin(20))^2 = cos(40)+i*sin(41)" to false,   // one part plainly differs
            "e^(ln(5))+2i = 5+3i" to false,                           // the real parts agree, the imaginary ones do not
            "(1+i)^2 = 2i" to true,
            "(2+3i)*(1-i) = 5+i" to true,
        ).forEach { (text, expected) -> withClue(text) { verdictOf(complex, text) shouldBe expected } }
    }

    "a quadratic with a repeated irrational root has one root, not a complex pair" {
        fun roots(text: String): Outcome.Roots = ((c.route(text) as RouteResult.Ok).outcome as Outcome.Roots)
        // (x − e)² = 0: D came out as −1E-48, the last digit of two fifty-digit products
        mapOf(
            "(x-e)^2 = 0" to "e",
            "(x-2*pi)^2 = 0" to "2*pi",
            "(x-pi/2)^2 = 0" to "pi/2",
            "(x-3*pi)^2 = 0" to "3*pi",        // D came out positive: two decimal roots a hair apart
            "(x-pi)^2 = 0" to "pi",
            "(x+7*e)^2 = 0" to "-7*e",
        ).forEach { (text, expected) ->
            withClue(text) {
                val found = roots(text)
                found.roots.size shouldBe 1
                (found.roots.single() as RealValue).num shouldBe num(expected)
            }
        }
        roots("(x-e)^2 = 0").steps.any { "reported once" in it } shouldBe true
        routeRefused("x^2-2*e*x+e^2 = 0")
        // Coefficients that come from a function carry the session's forty digits, not the tower's
        // fifty, and their rounding is that much larger: these still split in two (eleventh review).
        listOf("x^2-2*sqrt(e)*x+e = 0", "x^2-2*sin(20)*x+1-cos(20)^2 = 0", "x^2-2*2^(1/3)*x+4^(1/3) = 0",
            "x^2-2*sqrt(pi)*x+pi = 0", "x^2+e = 2*sqrt(e)*x", "-x^2+2*sqrt(e)*x-e = 0", "x^2-2*sqrt(sqrt(2))*x+sqrt(2) = 0",
        ).forEach { routeRefused(it) }
        // a genuine gap is kept, however the coefficients were obtained
        roots("x^2-2*e*x+e^2-10^-20 = 0").roots.size shouldBe 2       // e ± 10⁻¹⁰
        roots("x^2-2*sqrt(e)*x+e-10^-32 = 0").roots.size shouldBe 2   // √e ± 10⁻¹⁶
        roots("(x-1)^2+10^-26 = 0").roots.size shouldBe 2             // exact coefficients: a true complex pair
        roots("x^2-2*sqrt(2)*x+2 = 0").roots.size shouldBe 1          // D is exactly zero
    }

    "an uncertain determinant is refused without declaring the system singular" {
        fun refused(text: String): String = (c.route(text) as? RouteResult.Err)?.error?.detail
            ?: throw AssertionError("'$text' was answered: ${c.route(text)}")
        // one row a multiple of the other: answered with x = 0, y = 10^49 when the coefficients
        // were computed at fifty digits (twelfth review)
        listOf(
            "x*sin(25) + y*cos(25) = 10\n2*x*sin(25) + 2*y*cos(25) = 20",
            "sin(20)*x + cos(20)*y = 1\n2*sin(20)*x + 2*cos(20)*y = 3",
            "3*x + 7*y = 1\n3*tan(20)*x + 7*tan(20)*y = 2",
            "sin(10)*x + sin(20)*y + sin(40)*z = 1\n2*sin(10)*x + 2*sin(20)*y + 2*sin(40)*z = 2\nx + y + z = 0",
            // a multiple that is not exact in the tower: the determinant is two rounded products apart
            "sin(20)*x + cos(20)*y = 1\nsin(20)^2*x + sin(20)*cos(20)*y = 3",
            "ln(2)*x + ln(3)*y = 1\nln(2)*ln(5)*x + ln(3)*ln(5)*y = 2",
        ).forEach { routeRefused(it) }
        // a system that is not singular is solved as before
        val solved = (c.route("sin(20)*x + cos(20)*y = 1\ncos(20)*x - sin(20)*y = 0") as RouteResult.Ok).outcome as Outcome.SystemSolution
        solved.values.size shouldBe 2
        ((c.route("x + y = 3\nx - y = 1") as RouteResult.Ok).outcome as Outcome.SystemSolution).values shouldBe listOf(Num.of(2), Num.of(1))
    }

    "the engine's own sums of unequal exact values are not stopped" {
        // The tenth pass refused a sum that kept fewer than twelve digits of its smaller operand.
        // That reached the engine's own arithmetic, and the eleventh pass found it stopping these.
        val graph = c.graphRoots(c.plot("x^4 - pi*x").getOrThrow(), Num.of(-3), Num.of(7))
        withClue(graph.toString()) { ((graph as? RootResult.Found)?.roots?.size) shouldBe 2 }
        val limit = Limits.atInfinity("(pi*x^4 + 1)/(x^4 + 1)", "x", true, Settings())
        withClue(limit.toString()) {
            val settled = (limit as CalcResult.Ok).value.right as Limits.Side.Settles
            (settled.value.toBigDecimal(MathContext(20)).subtract(BigDecimal("3.141592654")).abs() < BigDecimal("0.000000001")) shouldBe true
        }
        fun rootCount(text: String): Int = ((c.route(text) as RouteResult.Ok).outcome as Outcome.Roots).roots.size
        rootCount("x^2 + x - 10^78 = 0") shouldBe 2
        rootCount("x^2 + 10^-20 x + sqrt(2) = 0") shouldBe 2
        rootCount("pi x^2 + x - 10^39 = 0") shouldBe 2
        ((c.route("x + y = sqrt(2)*10^40\nx - y = 1") as RouteResult.Ok).outcome is Outcome.SystemSolution) shouldBe true
        (c.evaluate("10^45 + pi") is CalcResult.Ok) shouldBe true
        // a sum that keeps nothing at all of an operand is still refused, as it always was
        refusal("10^80 + sqrt(2)").detail shouldContain "to preserve both operands"
    }

    // Nominal digits alone are insufficient: UncertaintySpec checks the enclosing error.
    "cancellation retains its nominal digits together with the established loss of accuracy" {
        same(decimal("10^49+sqrt(2)-10^49"), BigDecimal.ONE)
        same(decimal("10^45+pi-10^45"), BigDecimal("3.1416"))
        // the same expression in an order that keeps the digits is right
        same(num("10^49-10^49+sqrt(2)").toBigDecimal(MathContext(30, RoundingMode.HALF_EVEN)),
            IndependentReference.root(2).round(MathContext(30, RoundingMode.HALF_EVEN)))
    }

    "a square root of a radicand thousands of digits long is found at once" {
        // big-math halves its way down from x/2: one fifty-digit root of 10^20001+2 took over half a minute
        val started = System.nanoTime()
        refusal("round(sqrt(10^20001+2))").detail shouldContain "for its whole part"
        refusal("mod(sqrt(10^20001+2), 7)").detail shouldContain "quotient is too large"
        val seconds = (System.nanoTime() - started) / 1e9
        withClue("took $seconds s") { (seconds < 30.0) shouldBe true }
        // and it is the same root
        val wide = MathContext(80, RoundingMode.HALF_EVEN)
        val sixty = MathContext(60, RoundingMode.HALF_EVEN)
        same(num("sqrt(10^5001+2)").toBigDecimal(sixty), BigDecimal.TEN.pow(5001).add(BigDecimal(2)).sqrt(wide).round(sixty))
        same(num("sqrt(3*10^-4001)").toBigDecimal(sixty), BigDecimal("3E-4001").sqrt(wide).round(sixty))
        same(decimal("sqrt(approx(1/10^5001))"), BigDecimal("1E-5001").sqrt(wide).round(MathContext(40, RoundingMode.HALF_EVEN)))
        whole("floor(sqrt(10^2001+2))") shouldBe BigDecimal.TEN.pow(2001).add(BigDecimal(2)).sqrt(IndependentReference.WIDE).toBigInteger()
    }

    "unproved radical identities retain their oracle without rounding onto an integer" {
        // (√101 + 10)(√101 − 10) is 1. √101 − 10 had lost three digits and the product stood
        // 7.6·10⁻⁴⁸ under 1; nothing in it depends on the session's precision, so nothing moved,
        // no doubt was measured, and its floor read 0 (thirteenth review).
        unprovedWholes(mapOf(
            "floor((sqrt(101)+10)*(sqrt(101)-10))" to 1, "int((sqrt(101)+10)*(sqrt(101)-10))" to 1,
            "ceil((10+sqrt(101))*(10-sqrt(101)))" to -1, "floor((sqrt(101)-10)*(sqrt(101)+10)*100)" to 100,
            "round((sqrt(101)+10)*(sqrt(101)-10)/2)" to 1,
            // a measured part elsewhere in the argument did not help: its doubt was tiny beside the loss
            "floor((sqrt(101)+10)*(sqrt(101)-10)*exp(1)/exp(1))" to 1,
            // Binet's formula for the fortieth Fibonacci number
            "ceil((((1+sqrt(5))/2)^40-((1-sqrt(5))/2)^40)/sqrt(5))" to 102334155,
            "floor((sqrt(3)+sqrt(2))*(sqrt(3)-sqrt(2)))" to 1, "ceil((sqrt(7)+sqrt(5))*(sqrt(7)-sqrt(5)))" to 2,
            "floor(1/(sqrt(2)-1)-sqrt(2))" to 1, "ceil(1/(sqrt(2)-1)-sqrt(2))" to 1,
        ))
        (refusal("frac((sqrt(101)+10)*(sqrt(101)-10))") is EvalError.Unsupported) shouldBe true
        (refusal("mod((sqrt(101)+10)*(sqrt(101)-10)*6, 3)") is EvalError.Unsupported) shouldBe true
        // (1+√2)^65 = a + b√2 by the integer recurrence; it lies 1.3·10⁻²⁵ above the whole number 2a
        var a = BigInteger.ONE
        var b = BigInteger.ONE
        repeat(64) { val next = a + b.shiftLeft(1); b = a + b; a = next }
        when (val result = c.evaluate("floor((1+sqrt(2))^65)")) {
            is CalcResult.Ok -> (result.value as RealValue).num.toBigIntegerExactOrNull() shouldBe a.shiftLeft(1)
            is CalcResult.Err -> (result.error is EvalError.Unsupported) shouldBe true
        }
        // √(10^8+1) − 10^4 loses nine digits, inside the ten a sum of exact values may lose
        // before it is expanded further: the product is 4·10⁻⁴² over 1, and that is measured
        unprovedWholes(mapOf(
            "floor((sqrt(10^8+1)+10^4)*(sqrt(10^8+1)-10^4))" to 1, "ceil((sqrt(10^8+1)+10^4)*(sqrt(10^8+1)-10^4))" to 1,
            "floor((sqrt(10^8+1)-10^4)*(sqrt(10^8+1)+10^4)*1000)" to 1000,
        ))
        (refusal("frac((sqrt(10^8+1)+10^4)*(sqrt(10^8+1)-10^4))") is EvalError.Unsupported) shouldBe true
    }

    "a large power of π or e keeps the tower's fifty digits" {
        // Squared and multiplied at fifty digits, e^108 was wrong in its last two: 0.17 of a unit.
        val e108 = IndependentReference.fifty(IndependentReference.E.pow(108, IndependentReference.WIDE))
        val e100 = IndependentReference.fifty(IndependentReference.E.pow(100, IndependentReference.WIDE))
        same(decimal("e^108"), e108)
        same(decimal("e^100"), e100)
        same(decimal("pi^80"), IndependentReference.fifty(IndependentReference.PI.pow(80, IndependentReference.WIDE)))
        same(decimal("e^-70"), IndependentReference.fifty(BigDecimal.ONE.divide(IndependentReference.E.pow(70, IndependentReference.WIDE), IndependentReference.WIDE)))
        same(decimal("frac(e^108)"), IndependentReference.fraction(e108))   // 0.230; it read 0.398
        same(decimal("frac(e^100)"), IndependentReference.fraction(e100))   // 0.773742; it read 0.773794
        same(decimal("mod(e^108, 7)"), IndependentReference.remainder(e108, BigDecimal(7)))
        same(decimal("frac(e^108)"), decimal("frac(exp(108))"))
        // 4·e^108 has forty-eight digits before the point: its units are not settled, and it says so
        whole("floor(4*e^108)") shouldBe IndependentReference.E.pow(108, IndependentReference.WIDE).multiply(BigDecimal(4)).toBigInteger()
        // a small power is still the products a person would write, digit for digit
        same(decimal("e^2"), decimal("e*e"))
        same(decimal("pi^3"), decimal("pi*pi*pi"))
        same(decimal("e^5"), decimal("e*((e*e)*(e*e))"))
        whole("floor(e^30)") shouldBe IndependentReference.E.pow(30, IndependentReference.WIDE).toBigInteger()
        whole("floor(pi^20)") shouldBe IndependentReference.PI.pow(20, IndependentReference.WIDE).toBigInteger()
    }

    "an estimate that keeps too few digits to be worked out again has no whole part to give" {
        // 10^49 + √2 holds one digit of √2, and at forty-five digits none: the coarser value is
        // not a rounder version of the finer one. Its floor read 1 and its fractional part 0.
        listOf("floor(10^49+sqrt(2)-10^49)", "frac(10^49+sqrt(2)-10^49)",
            // exactly whole as held, with the digits that would tell it from 1 swallowed
            "floor(10^44+sqrt(1.0000002)-10^44)", "frac(10^44+sqrt(1.0000002)-10^44)",
        ).forEach { text -> withClue(text) { refusal(text).detail shouldContain "uncertainty" } }
        whole("floor(10^46+sqrt(2)-10^46)") shouldBe IndependentReference.root(2).toBigInteger()
        whole("round(10^46+pi-10^46)") shouldBe IndependentReference.nearest(IndependentReference.PI)
        // the same sum with digits to spare is answered
        whole("floor(10^30+sqrt(2)-10^30)") shouldBe BigInteger.ONE
        same(decimal("frac(10^30+sqrt(2)-10^30)"), BigDecimal("0.4142135623730950488"))
        // the forty-five-digit value has no room for the fraction of 10^44 + √2: that is its size, not a doubt
        whole("floor(10^43+sqrt(2))") shouldBe BigInteger.TEN.pow(43) + BigInteger.ONE
        whole("floor(10^44+sqrt(2))") shouldBe BigInteger.TEN.pow(44) + BigInteger.ONE
        // Rounding seen through an inverse sine at the edge of its domain shrinks slowly, and the
        // two coarser values show it: the value is 90 less the root of a rounding, not 89 and a fraction.
        wholes(mapOf("floor(asin(sin(20)^2+cos(20)^2))" to 90, "ceil(asin(sin(20)^2+cos(20)^2))" to 90, "int(asin(sin(20)^2+cos(20)^2))" to 90))
        // values truly short of a whole number by less than forty-five digits show are still told apart
        wholes(mapOf("floor(tanh(50))" to 0, "floor(tanh(52))" to 0, "floor(tanh(53))" to 0, "floor(tanh(54))" to 0, "ceil(tanh(53))" to 1))
        // At forty digits tanh(50) − 1 is a decimal zero, and a function of it came back exact:
        // the session saw an exact argument and nothing was recomputed. Each of these read one too high.
        wholes(mapOf(
            "floor(exp(tanh(50)-1))" to 0, "floor(e^(tanh(50)-1))" to 0, "floor(10^(tanh(50)-1))" to 0,
            "floor(2^(tanh(50)+2))" to 7, "ceil(exp(1-tanh(50)))" to 2, "int(exp(tanh(50)-1)*5)" to 4,
        ))
        refusal("floor(sqrt(tanh(50)-1))").detail shouldContain "negative"
        // A value 10⁻¹⁰ short of a whole number is not that whole number. Once the tower's lost
        // digits were measured, a sum against 10^38 left an allowance of 4·10⁻¹⁰ (the forty-digit
        // sum has no room for 0.74), and 0.9999999999 was read as 1 (fourteenth review). The
        // forty-five-digit value is the fifty-digit one, digit for digit: that is not rounding.
        wholes(mapOf(
            "floor(10^38 + exp(ln(0.74)) - 10^38 + 0.2599999999)" to 0, "round(10^38 + exp(ln(0.74)) - 10^38 + 0.7599999999)" to 1,
            "ceil(10^38 + exp(ln(0.74)) - 10^38 + 0.2600000001)" to 2, "floor(10^38 + exp(ln(0.74)) - 10^38 + 7.2599999999)" to 7,
            "floor(10^38 + exp(ln(0.74)) - 10^38 + 0.25999999999)" to 0, "floor(10^38 + approx(0.74,0) - 10^38 + 2.2599999999)" to 2,
            "floor(10^28 + exp(ln(0.745432154321)) - 10^28 + 0.25456784567899999999)" to 0,
            "floor(10^20 + exp(ln(0.74543215432154321543)) - 10^20 + 0.2545678456784567845699999999)" to 0,
        ))
        same(decimal("frac(10^38 + exp(ln(0.74)) - 10^38 + 0.2599999999)"), BigDecimal("0.9999999999"))
        same(decimal("mod(10^38 + approx(0.74,0) - 10^38 + 0.2599999999, 1)"), BigDecimal("0.9999999999"))
        // Where the forty-five-digit value differs as well, and the doubt is wider than 10⁻²⁰, the
        // whole part is declined: sin(20) + 0.65797985667 is 4·10⁻¹² short of 1, and the sum
        // against 10^38 holds it as exactly 1.
        listOf("floor(10^38 + sin(20) - 10^38 + 0.65797985667)", "frac(10^38 + sin(20) - 10^38 + 0.65797985667)",
            // a divisor that vanishes more coarsely, and a quotient exactly whole as held but unsettled
            "mod(7.5, 10^49 + sqrt(2) - 10^49)", "mod(10^44 + sqrt(0.9999998) - 10^44, 1)",
        ).forEach { text -> withClue(text) { refusal(text).detail shouldContain "uncertainty" } }
        whole("floor(10^38 + sin(20) - 10^38 + 0.6579798566)") shouldBe
            trigReferenceDegrees(BigDecimal(20), false).add(BigDecimal("0.6579798566")).toBigInteger()
        // True identities whose rounding is larger than the last two digits are still read as whole:
        // cosh(9)² − sinh(9)² is exactly 1 at forty-five digits and at forty, and 10⁻⁴² short at fifty.
        wholes(mapOf("floor(cosh(9)^2-sinh(9)^2)" to 1, "floor(cosh(14)^2-sinh(14)^2)" to 1, "ceil(cosh(15)^2-sinh(15)^2)" to 1,
            "ceil(cosh(26)^2-sinh(26)^2)" to 1))
        unprovedWholes(mapOf("floor(1/sin(5/1000)^2-1/tan(5/1000)^2)" to 1, "floor(1/cos(75)^2-tan(75)^2)" to 1))
        // A large estimate that is whole as held is not unsettled because forty digits cannot hold it.
        whole("floor(approx(2,0)^140)") shouldBe BigInteger.TWO.pow(140)
        whole("floor(approx(12,0)^40)") shouldBe BigInteger.valueOf(12).pow(40)
        var factorial = BigInteger.ONE
        for (k in 2..39) factorial = factorial.multiply(BigInteger.valueOf(k.toLong()))
        whole("round(fact(39)/e)") shouldBe IndependentReference.nearest(BigDecimal(factorial).divide(IndependentReference.E, IndependentReference.WIDE))
        val thirtiethQuotient = (2..30).fold(BigInteger.ONE) { acc, k -> acc.multiply(BigInteger.valueOf(k.toLong())) } / BigInteger.valueOf(7)
        whole("floor(fact(30)/7)") shouldBe thirtiethQuotient
        // Decimal reciprocal arithmetic encloses this exact integer but does not retain
        // the rational dependency that would prove its floor. It must not snap the interval.
        val divided = num("approx(fact(30),0)/7").bounds(100)!!
        (divided.lower <= BigDecimal(thirtiethQuotient) && divided.upper >= BigDecimal(thirtiethQuotient)) shouldBe true
        (refusal("floor(approx(fact(30),0)/7)") is EvalError.Unsupported) shouldBe true
        // 40!/e has forty-eight digits, and dividing at forty-five digits moves it by thousands:
        // its whole part is answered or declined, never wrong
        (c.evaluate("floor(fact(40)/e+1/2)") as? CalcResult.Ok)?.let {
            (it.value as RealValue).num.toBigIntegerExactOrNull() shouldBe BigInteger("300158458444475693321518926221316715906770469041")
        }
        // an estimate that was typed, or kept, is exactly whole as held and is taken as it stands
        whole("floor(approx(10^45,0))") shouldBe BigInteger.TEN.pow(45)
        whole("floor(10^40*approx(1.5,0))") shouldBe BigInteger.valueOf(15) * BigInteger.TEN.pow(39)
        whole("floor(approx(7,0))") shouldBe BigInteger.valueOf(7)
    }

    "a rounding function inside another is worked out once, and a refusal is not papered over" {
        // The inner floor was not recomputed two levels down and read 2 or 3 by the luck of a digit;
        // scaled by 10^10 that became a refusal in the finest pass, which was dropped in favour of
        // the session's forty-digit value with no doubt at all: 2, 2, 5 and 6 (thirteenth review).
        wholes(mapOf(
            "floor(e^(ln(3))+frac(floor(ln(125)/ln(5))*10^10))" to 3,
            "floor(ln(125)/ln(5)+mod(floor(ln(125)/ln(5))*10^10,3))" to 3,
            "floor(e^(ln(3))+floor(floor(ln(125)/ln(5))*10^10)/10^10)" to 6,
            "floor(mod(floor(ln(125)/ln(5))*10^9,7)+ln(125)/ln(5))" to 7,
            "floor(e^(ln(3))+frac(floor(ln(125)/ln(5))*10^7))" to 3,
            "floor(floor(floor(ln(125)/ln(5))))" to 3,
        ))
        // A fractional part or a remainder still carries its argument's rounding. Kept as a
        // finished value it never moved in the outer function's passes, no doubt was measured,
        // and 123.75 degrees had 44 minutes (fourteenth review; 1.2 said the same).
        wholes(mapOf(
            "floor(frac(123.75*cos(10)/cos(10))*60)" to 45, "floor(mod(123.75*cos(10)/cos(10), 1)*60)" to 45,
            "floor(frac(98765.4*ln(3)/ln(3))*10)" to 4, "floor(frac(cbrt(1234.5)^3)*10)" to 5,
            "floor(frac(asin(sin(30.5)))*60)" to 30,
            "floor(frac(frac(123.7625*cos(10)/cos(10))*60)*60)" to 45,
            // and where nothing is close to a whole number the answers are what they were
            "floor(frac(sin(20)*10)*100)" to 42, "floor(frac(ln(125)/ln(5))*10+0.5)" to 0, "floor(mod(sin(20)*100, 7)*10)" to 62,
        ))
        // A supported inner integer remains correct when nested; actual inner refusals propagate.
        whole("floor(1+floor(tan($nearPoleAngle)))") shouldBe nearPoleTangent.toBigInteger() + BigInteger.ONE
        refusal("round(2*frac(sin(1)*10^60))").detail shouldContain "whole"
        // an argument that fails when it is worked out more coarsely has an unmeasured doubt
        refusal("floor(10^9/(sqrt(2)+sqrt(3)-3.14626436994197234232913506571557044551))").detail shouldContain "uncertainty"
        // Nesting costs what the expression is long, not its cube: 300 deep took 36 seconds.
        val started = System.nanoTime()
        same(decimal("frac(".repeat(300) + "sin(1)+0.5" + ")".repeat(300)), decimal("frac(sin(1)+0.5)"))
        var nested = "sin(1)"
        repeat(40) { nested = "frac(3*$nested+ln(2))" }
        (c.evaluate(nested) is CalcResult.Ok) shouldBe true
        // Each turn of x → frac(3x + ln 2) triples the rounding, and after about a hundred turns
        // none of the fifty digits is left. The fractional part used to be printed all the same.
        repeat(80) { nested = "frac(3*$nested+ln(2))" }
        refusal(nested).detail shouldContain "uncertainty"
        var remainders = "sin(1)"
        repeat(40) { remainders = "mod(3*$remainders+ln(2),1)" }
        (c.evaluate(remainders) is CalcResult.Ok) shouldBe true
        for (k in 1..200) {
            val expression = "frac(sin($k/10)*frac(cos($k/10)*frac(tan($k/10)*frac(e^($k/10)))))"
            withClue(expression) { (c.evaluate(expression) is CalcResult.Ok) shouldBe true }
        }
        val seconds = (System.nanoTime() - started) / 1e9
        withClue("took $seconds s") { (seconds < 20.0) shouldBe true }
    }

    "a discriminant or a determinant that is small beside exact terms is not rounding" {
        fun roots(text: String, calculator: Calculator = c) =
            ((calculator.route(text) as? RouteResult.Ok)?.outcome as? Outcome.Roots)?.roots ?: throw AssertionError("'$text' → ${calculator.route(text)}")
        // (x − 10^18)² = sin(20): D is 4·sin(20), known to fourteen digits. Beside two exact terms of
        // 4·10^36 it was called rounding, and the answer was the single root 10^18, shown as exact.
        listOf("(x-10^18)^2 = sin(20)", "(x-10^18)^2 = ln(2)", "(x-10^19)^2 = pi", "(x-1)^2 = e^-85", "x^2-2*10^18*x+10^36-sin(20) = 0").forEach { text ->
            withClue(text) {
                val found = roots(text)
                found.size shouldBe 2
                found.all { it is RealValue } shouldBe true
            }
        }
        val pair = roots("(x-10^18)^2 = sin(20)").map { (it as RealValue).num.toBigDecimal(Num.MC) }
        val width = BigDecimal(2).multiply(decimal("sin(20)").sqrt(Num.MC))
        withClue("the roots are ${pair[1].subtract(pair[0])} apart, wanted $width") {
            (pair[1].subtract(pair[0]).subtract(width).abs() < BigDecimal("1E-10")) shouldBe true
        }
        roots("(x-1)^2 = -e^-90").let { found -> found.size shouldBe 2; found.all { it is ComplexValue } shouldBe true }
        // the same for a determinant: this one is sin(20)², beside two terms of 10^36
        val solved = (c.route("10^18*x+(10^18+sin(20))*y=1\n(10^18-sin(20))*x+10^18*y=2") as RouteResult.Ok).outcome as Outcome.SystemSolution
        withClue(solved.values.toString()) {
            (solved.values[0].toBigDecimal(Num.MC).divide(BigDecimal("-8.54863217042E18"), Num.MC).subtract(BigDecimal.ONE).abs() < BigDecimal("1E-9")) shouldBe true
        }
        // a session that computes with sixteen digits
        val sixteen = Calculator(Settings(computePrecision = 16, displayPrecision = 12))
        listOf("(x-10^6)^2 = sin(20)", "(x-100)^2 = e^-25", "(x-sin(20))*(x-sin(20.00001)) = 0").forEach { withClue(it) { roots(it, sixteen).size shouldBe 2 } }
        roots("(x-e)^2 = 0", sixteen).size shouldBe 1
        listOf("x^2-2*sqrt(e)*x+e = 0", "x^2-2*sin(20)*x+1-cos(20)^2 = 0").forEach { routeRefused(it, sixteen) }
        // a session with fewer than ten digits has none to take away, and is measured against five more
        listOf(5, 8).forEach { digits ->
            val few = Calculator(Settings(computePrecision = digits, displayPrecision = 1))
            withClue("computing with $digits digits") {
                roots("(x-100)^2 = sin(20)", few).size shouldBe 2
                routeRefused("x^2-2*sqrt(e)*x+e = 0", few)
                roots("(x-e)^2 = 0", few).size shouldBe 1
            }
        }
        // and one that computes with all fifty
        val fifty = Calculator(Settings(computePrecision = 50, displayPrecision = 46))
        roots("(x-10^18)^2 = sin(20)", fifty).size shouldBe 2
        routeRefused("x^2-2*sqrt(e)*x+e = 0", fifty)
        // a quadratic left when a power of the unknown is divided out is measured the same way: the
        // close pair is not merged into one root (it is refused, as it was before the twelfth pass)
        roots("x*((x-10^18)^2-sin(20)) = 0").size shouldBe 3
        routeRefused("x*(x^2-2*sqrt(e)*x+e) = 0")     // 0, and √e once
        routeRefused("x^2*(x^2-2*sin(20)*x+sin(20)^2) = 0")
        // Coefficients from a register cannot be worked out again, so there is no measure and the
        // session's digits are assumed, as before the thirteenth pass. Measured, the rounding of a
        // stored √e went unseen: the quadratic was split in two and the singular system "solved".
        val session = Calculator()
        session.route("sqrt(e) → A")
        routeRefused("x^2-2*A*x+e = 0", session)
        routeRefused("x+A*y=1\nA*x+e*y=2", session)
        routeRefused("x*(x^2-2*A*x+e) = 0", session)
        session.route("sqrt(e)")
        routeRefused("x^2-2*Ans*x+e = 0", session)
        // KNOWN LIMITATION of that assumption: with the estimate in a register, the close pair
        // of (x − 10^18)² = A is one root again, as it was in 1.3 before the thirteenth pass.
        session.route("sin(20) → B")
        roots("(x-10^18)^2 = B", session).size shouldBe 2
    }

    "expanded irrational repeated roots are either proved algebraically or explicitly undecided" {
        fun roots(text: String, calculator: Calculator = c) =
            ((calculator.route(text) as? RouteResult.Ok)?.outcome as? Outcome.Roots)?.roots ?: throw AssertionError("'$text' → ${calculator.route(text)}")
        fun repeated(text: String, calculator: Calculator = c, count: Int = 1) = withClue(text) {
            when (val result = calculator.route(text)) {
                is RouteResult.Ok -> (result.outcome as Outcome.Roots).roots.size shouldBe count
                is RouteResult.Err -> (result.error is EvalError.Unsupported) shouldBe true
                else -> throw AssertionError("not evaluated: $result")
            }
        }
        fun singular(text: String, calculator: Calculator = c) = withClue(text) {
            val error = (calculator.route(text) as? RouteResult.Err)?.error
            (error is EvalError.Unsupported || error is EvalError.Domain) shouldBe true
        }
        // log 9 − 2·log 3 is 10⁻⁴⁰ at forty digits and exactly 0 at thirty-five. Scaled down, a
        // movement from zero is always smaller than the value: the measure called rounding a real
        // gap, split the root in two and solved a singular system to 10^40 (fourteenth review).
        listOf("x^2-log(9)*x+log(3)^2 = 0", "x^2-ln(196)*x+ln(14)^2 = 0", "x^2-22*x+exp(ln(121)) = 0", "x^2-2*exp(ln(12))*x+144 = 0",
            "x^2-2*ln(40)*x+(ln(5)+ln(8))^2 = 0", "x^2-2*exp(52)*x+exp(104) = 0",
        ).forEach { withClue(it) { repeated(it) } }
        singular("x+log(3)*y=1\n2*x+log(9)*y=5")
        singular("x+y+z=1\nln(14)*x+y-z=2\nln(196)*x+2*y-2*z=7")
        singular("exp(ln(12))*x+3*y=1\n4*x+y=2")
        singular("x+exp(52)*y=1\nexp(52)*x+exp(104)*y=3")
        repeated("x^3-log(9)*x^2+log(3)^2*x = 0", count = 2)     // 0, and log 3 once
        // not by the luck of one value: every k, at the default precision and at sixteen and twelve digits
        val sessions = listOf(c, Calculator(Settings(computePrecision = 16, displayPrecision = 12)), Calculator(Settings(computePrecision = 12, displayPrecision = 8)))
        for (session in sessions) for (k in 2..60) {
            withClue("k = $k, ${session.settings.computePrecision} digits") {
                repeated("x^2-log($k^2)*x+log($k)^2 = 0", session)
                repeated("x^2-ln($k^2)*x+ln($k)^2 = 0", session)
                singular("x+ln($k)*y=1\n2*x+ln($k^2)*y=5", session)
            }
        }
        // and a gap that is there is still kept
        roots("x^2-2*ln(7)*x+ln(7)^2-10^-20 = 0").size shouldBe 2
        roots("(x-10^12)^2 = sin(20)").size shouldBe 2
    }

    "a statement is not called false while its sides differ by no more than their own rounding" {
        fun verdictOf(calculator: Calculator, text: String): Boolean? = when (val r = calculator.route(text)) {
            is RouteResult.Ok -> (r.outcome as Outcome.Verdict).holds
            is RouteResult.Err -> {
                withClue("$text → ${r.error}") { (r.error is EvalError.Unsupported) shouldBe true }
                null
            }
            else -> throw AssertionError("'$text' did not evaluate: $r")
        }
        // cosh(13)² is 4.9·10^10, so forty digits leave the difference twenty-nine, and the sides
        // disagree in the thirtieth: False, while cosh(12)² − sinh(12)² = 1 was refused.
        (listOf(13, 14, 15, 20, 30).map { "cosh($it)^2-sinh($it)^2 = 1" } + listOf(
            "cosh(13)^2-sinh(13)^2-1 = 0",
            "(cosh(13)-sinh(13))*(cosh(13)+sinh(13)) = 1",   // the cancellation inside a factor
            "1-cos(0.0001) = 2*sin(0.00005)^2", "sin(0.0001)^2 = 1-cos(0.0001)^2", "1/sin(0.001)^2-1/tan(0.001)^2 = 1",
            "cosh(40)-sinh(40) = e^-40", "ln(10^12+1)-ln(10^12) = ln(1+10^-12)", "log(10^15+1)-15 = log(1+10^-15)",
        )).forEach { withClue(it) { (verdictOf(c, it) != false) shouldBe true } }
        val radians = Calculator(Settings(angle = AngleMode.RAD))
        (verdictOf(radians, "1-cos(10^-6) = 2*sin(10^-6/2)^2") != false) shouldBe true
        // values kept in registers cannot be worked out again; the size of their terms still speaks
        val session = Calculator()
        session.route("cosh(13) → B")
        session.route("sinh(13) → C")
        (verdictOf(session, "B^2-C^2 = 1") != false) shouldBe true
        // With complex numbers on, `… − 1 = 0` has a left side that is rounding with nothing to
        // agree with; every one of these was False while the same identity written `… = 1` was refused.
        val complex = Calculator(Settings(complexEnabled = true))
        listOf("(cos(72)+i*sin(72))^5-1 = 0", "(cos(20)+i*sin(20))^9+1 = 0", "(cos(36)+i*sin(36))^5+1 = 0", "(cos(15)+i*sin(15))^6-i = 0",
            "(cos(40)+i*sin(40))^3-cos(120)-i*sin(120) = 0", "0 = (cos(72)+i*sin(72))^5-1",
            "(cos(72)+i*sin(72))^4+(cos(72)+i*sin(72))^3+(cos(72)+i*sin(72))^2+(cos(72)+i*sin(72))+1 = 0",
        ).forEach { withClue(it) { (verdictOf(complex, it) != false) shouldBe true } }
        mapOf(
            "(cos(72)+i*sin(72))^5-2 = 0" to false,
            "(cos(72)+i*sin(72))^5-1 = 10^-30" to false,
            "(cos(72)+i*sin(72))^5 = 1+10^-20*i" to false,
            "(1+i)^2-2i = 0" to true,
        ).forEach { (text, expected) -> withClue(text) { verdictOf(complex, text) shouldBe expected } }
        // The fourteenth review found the measure itself fooled. A sum of a few function values
        // carries zero or one unit of rounding, so at thirty-five and at thirty digits these come
        // out as exactly 0, and the movement that was scaled down was the difference itself.
        listOf("2*(ln(40)-ln(5)-ln(8)) = 0", "abs(ln(5)+ln(8)-ln(40)) = 0", "(ln(5)+ln(8)-ln(40))/2 = 0", "(exp(ln(12))-12)*2 = 0",
            "(ln(13*3)-ln(13)-ln(3))*2 = 0",
            // rounding seen through a root or an inverse function at the edge of its domain shrinks
            // by 10^2.5 for five digits, not 10^5, and in some sessions it cannot be worked out at all
            "asin(sin(20)^2+cos(20)^2) = 90", "acos(sin(20)^2+cos(20)^2) = 0", "sqrt(sin(50)^2+cos(50)^2-1) = 0",
            "acosh(cosh(2)^2-sinh(2)^2) = 0", "sqrt(abs(sin(20)^2+cos(20)^2-1)) = 0", "cbrt(tan(20)*cos(20)-sin(20)) = 0",
            "acos((ln(2)+ln(3))/ln(6)) = 0",
        ).forEach { withClue(it) { (verdictOf(c, it) != false) shouldBe true } }
        val fiftyDigits = Calculator(Settings(computePrecision = 50, displayPrecision = 12))
        (verdictOf(fiftyDigits, "(sin(20)^2+cos(20)^2-1)*2 = 0") != false) shouldBe true
        (verdictOf(Calculator(Settings(computePrecision = 50, complexEnabled = true)), "(cos(72)+i*sin(72))^5-1 = 0") != false) shouldBe true
        // a value whose imaginary part is exactly zero in one session and rounding in another
        listOf("exp(ln(3))+i*exp(ln(4))-3-4*i = 0", "1/(ln(2)+i)-(ln(2)-i)/(ln(2)^2+1) = 0", "i*ln(40)-i*ln(5)-i*ln(8) = 0")
            .forEach { withClue(it) { (verdictOf(complex, it) != false) shouldBe true } }
        // a session with fewer than ten digits has none to take away, and is measured against five more
        val eight = Calculator(Settings(computePrecision = 8, displayPrecision = 4))
        listOf("2*(ln(6)-ln(2)-ln(3)) = 0", "(sin(20)^2+cos(20)^2-1)*2 = 0").forEach { withClue(it) { (verdictOf(eight, it) != false) shouldBe true } }
        verdictOf(eight, "2*(ln(6)-ln(2)-ln(3)) = 0.01") shouldBe false
        // What plainly differs is still False, cancellation or not.
        mapOf(
            "2*(ln(40)-ln(5)-ln(8)) = 10^-30" to false,
            "2*(ln(40)-ln(5)-ln(9)) = 0" to false,
            "asin(sin(20)^2+cos(20)^2) = 89.999999" to false,
            "sqrt(sin(50)^2+cos(50)^2) = 1.0000001" to false,
            "cosh(13)^2-sinh(13)^2 = 1.0000001" to false,
            "cosh(13)^2-sinh(13)^2 = 1+10^-15" to false,
            "cosh(13)^2-sinh(13)^2 = 2" to false,
            "sin(20) = cos(70.0000001)" to false,
            "ln(2)+ln(5) = ln(11)" to false,
            "tan(89.999) = 57295.78" to false,
            "e^10 = 22026.4657948" to false,
            "sqrt(2)+sqrt(3) = 3.146" to false,
            "sqrt(10^100+1) = 10^50" to false,
            "sin(20) km = 342.0201433 m" to false,
            // an exact term has no session rounding: these are not thirty digits of 10^30 short of decidable
            "10^30 + sqrt(2) - 10^30 = 1.4" to false,
            "sin(20) mm + 1 ly - 1 ly = 0.3 mm" to false,
            "10^6 + sin(20) - 10^6 = 0.3" to false,
        ).forEach { (text, expected) -> withClue(text) { verdictOf(c, text) shouldBe expected } }
        listOf("e^2=e*e", "pi^3=pi*pi*pi", "e^(2+3)=e^2*e^3", "(2*pi)^2=4*pi^2",
            "e^33=e^32*e", "e^64=e^32*e^32", "pi^40=pi^32*pi^8", "sin(20) km=1000*sin(20) m")
            .forEach { text -> withClue(text) { (verdictOf(c, text) != false) shouldBe true } }
    }
})
