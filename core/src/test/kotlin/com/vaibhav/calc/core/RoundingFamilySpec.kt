package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.NumMath
import com.vaibhav.calc.core.num.Rational
import com.vaibhav.calc.core.num.Surd
import com.vaibhav.calc.core.num.SymConst
import com.vaibhav.calc.core.num.toBigIntegerExactOrNull
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * The reference values. Nothing here comes from the engine: π is Machin's formula and e its
 * own series, both in plain `BigDecimal`, and square roots are the JDK's.
 */
internal object IndependentReference {
    val WIDE = MathContext(1400, RoundingMode.HALF_EVEN)
    private val FIFTY = MathContext(50, RoundingMode.HALF_EVEN)

    /** arctan(1/n) = 1/n − 1/(3n³) + 1/(5n⁵) − … */
    private fun arctanOfInverse(n: Long): BigDecimal {
        val square = BigDecimal(n * n)
        var power = BigDecimal.ONE.divide(BigDecimal(n), WIDE)
        var sum = power
        for (k in 1..1100) {
            power = power.divide(square, WIDE)
            val term = power.divide(BigDecimal(2L * k + 1), WIDE)
            sum = if (k % 2 == 1) sum.subtract(term) else sum.add(term)
        }
        return sum
    }

    val PI: BigDecimal = arctanOfInverse(5).multiply(BigDecimal(16)).subtract(arctanOfInverse(239).multiply(BigDecimal(4)))

    val E: BigDecimal = run {
        var term = BigDecimal.ONE
        var sum = BigDecimal.ONE
        for (k in 1..700) {
            term = term.divide(BigDecimal(k), WIDE)
            sum = sum.add(term)
        }
        sum
    }

    fun root(n: BigDecimal): BigDecimal = n.sqrt(WIDE)
    fun root(n: Long): BigDecimal = root(BigDecimal(n))
    fun tenTo(n: Int): BigDecimal = BigDecimal.TEN.pow(n)

    fun fifty(x: BigDecimal): BigDecimal = x.round(FIFTY)

    /** x − trunc(x): carries the sign of x. */
    fun fraction(x: BigDecimal): BigDecimal = x.subtract(BigDecimal(x.toBigInteger()))

    /** a − b·⌊a/b⌋: carries the sign of b. */
    fun remainder(a: BigDecimal, b: BigDecimal): BigDecimal =
        a.subtract(b.multiply(a.divide(b, WIDE).setScale(0, RoundingMode.FLOOR)))

    /** Nearest whole number, a tie going away from zero. */
    fun nearest(x: BigDecimal): BigInteger =
        x.abs().setScale(0, RoundingMode.HALF_UP).toBigIntegerExact().let { if (x.signum() < 0) it.negate() else it }
}

/**
 * `round`, `frac` and `mod` of values that are not plain fractions. The ninth review pass found
 * that all three were still taken through the tower's fifty-digit addition after `floor` and
 * `ceil` had been fixed: `round(√(10^50+10^25))` was one too high, `frac(π·10^50)` was 0, and
 * `mod(10^60, π)` was 10000000000.
 */
class RoundingFamilySpec : StringSpec({
    val c = Calculator()
    fun num(text: String): Num = (c.evaluate(text).getOrThrow() as RealValue).num
    fun whole(text: String): BigInteger = num(text).toBigIntegerExactOrNull() ?: throw AssertionError("'$text' is not a whole number: ${num(text)}")
    fun decimal(text: String): BigDecimal = (num(text) as? Dec)?.value ?: throw AssertionError("'$text' is not a decimal: ${num(text)}")
    fun refusal(text: String): EvalError =
        (c.evaluate(text) as? CalcResult.Err)?.error ?: throw AssertionError("'$text' was answered: ${num(text)}")
    fun same(actual: BigDecimal, expected: BigDecimal) = withClue("got $actual, wanted $expected") { actual.compareTo(expected) shouldBe 0 }

    "the reference constants are the published ones" {
        IndependentReference.PI.toPlainString() shouldStartWith "3.14159265358979323846264338327950288419716939937510582097494459230781640628620899862803482534211706798214808651"
        IndependentReference.E.toPlainString() shouldStartWith "2.71828182845904523536028747135266249775724709369995957496696762772407663035354759457138217852516642742746"
    }

    "round of an exact irrational is settled, not read off fifty digits" {
        val tenTo25 = BigInteger.TEN.pow(25)
        // √(10^50+10^25) = 10^25 + 0.4999999999999999999999999875…: fifty digits showed .5 and went up
        whole("round(sqrt(10^50+10^25))") shouldBe tenTo25
        whole("round(sqrt(10^50+10^25+1))") shouldBe tenTo25.add(BigInteger.ONE) // 10^25 + 0.5000000000000000000000000375…
        whole("round(-sqrt(10^50+10^25))") shouldBe tenTo25.negate()
        // π·10^49 = …93751.058…: fifty digits gave a tie at …751.5, which went to the even …752
        val piE49 = BigInteger("31415926535897932384626433832795028841971693993751")
        whole("round(pi*10^49)") shouldBe piE49
        whole("round(-pi*10^49)") shouldBe piE49.negate()
        whole("round(pi*10^1000)") shouldBe IndependentReference.nearest(IndependentReference.PI.multiply(IndependentReference.tenTo(1000)))
        mapOf("round(pi)" to 3, "round(-pi)" to -3, "round(e)" to 3, "round(5*sqrt(2))" to 7, "round(-5*sqrt(2))" to -7,
            "round(sqrt(2)/2)" to 1, "round(pi/10)" to 0, "round(-pi/10)" to 0, "round(pi/10^60)" to 0, "round(sqrt(2)/4)" to 0,
        ).forEach { (text, expected) -> withClue(text) { whole(text) shouldBe BigInteger.valueOf(expected.toLong()) } }
    }

    "round agrees with the reference on six hundred multiples of surds, π and e" {
        val rootTwo = IndependentReference.root(2)
        val rootThree = IndependentReference.root(3)
        for (k in 1..150) {
            val times = BigDecimal(k)
            withClue("k = $k") {
                whole("round($k*sqrt(2))") shouldBe IndependentReference.nearest(rootTwo.multiply(times))
                whole("round(-$k*pi)") shouldBe IndependentReference.nearest(IndependentReference.PI.multiply(times).negate())
                whole("round($k*e/7)") shouldBe IndependentReference.nearest(IndependentReference.E.multiply(times).divide(BigDecimal(7), IndependentReference.WIDE))
                whole("round($k*sqrt(3)/2)") shouldBe IndependentReference.nearest(rootThree.multiply(times).divide(BigDecimal(2), IndependentReference.WIDE))
            }
        }
    }

    "round of a known-point decimal uses every held digit" {
        // A fifty-digit odd whole number: adding the half in the tower made a tie and sent it up by one.
        val odd = "31415926535897932384626433832795028841971693993751"
        NumMath.round(Dec(BigDecimal(odd), BigDecimal.ZERO)) shouldBe Num.of(BigInteger(odd))
        NumMath.round(Dec(BigDecimal("$odd.5"), BigDecimal.ZERO)) shouldBe Num.of(BigInteger(odd).add(BigInteger.ONE))
        NumMath.round(Dec(BigDecimal("-$odd.5"), BigDecimal.ZERO)) shouldBe Num.of(BigInteger(odd).add(BigInteger.ONE).negate())
        NumMath.round(Dec(BigDecimal("$odd.4999"), BigDecimal.ZERO)) shouldBe Num.of(BigInteger(odd))
        NumMath.round(Dec(BigDecimal("1E+60"), BigDecimal.ZERO)) shouldBe Num.of(BigInteger.TEN.pow(60))
        mapOf("2.5" to 3, "-2.5" to -3, "2.4999999999999999999999999999999999999999999999999" to 2, "0.4" to 0, "-0.4" to 0, "0.5" to 1, "0" to 0)
            .forEach { (text, expected) -> withClue(text) { NumMath.round(Dec(BigDecimal(text), BigDecimal.ZERO)) shouldBe Num.of(expected) } }
        // These decimal fixtures are explicitly known points, including half-way ties.
        whole("round(approx(2.5,0))") shouldBe BigInteger.valueOf(3)
        whole("round(approx(-2.5,0))") shouldBe BigInteger.valueOf(-3)
        val unknown = refusal("round(approx(2.5))")
        (unknown is EvalError.Unsupported) shouldBe true
        unknown.detail shouldContain "no established error bound"
    }

    "exact fractions round, split and divide as they always did" {
        mapOf("round(2.5)" to "3", "round(-2.5)" to "-3", "round(1/2)" to "1", "round(-1/2)" to "-1", "round(3/2)" to "2", "round(0)" to "0",
            "round(2.4)" to "2", "frac(7/2)" to "1/2", "frac(-7/2)" to "-1/2", "frac(5)" to "0", "frac(1/3)" to "1/3",
            "mod(7, 3)" to "1", "mod(-1, 3)" to "2", "mod(7, -3)" to "-2", "mod(-7, -3)" to "-1", "mod(7.5, 2)" to "3/2",
            "mod(-7.5, 2)" to "1/2", "mod(1/3, 1/7)" to "1/21", "mod(6, 3)" to "0", "mod(0, 7)" to "0",
        ).forEach { (text, expected) -> withClue(text) { (num(text) as Rational).toString() shouldBe expected } }
    }

    "the fractional part of an exact irrational has fifty good digits however large the value" {
        val pi = IndependentReference.PI
        mapOf(
            "frac(pi*10^50)" to pi.multiply(IndependentReference.tenTo(50)),       // was 0
            "frac(pi*10^49)" to pi.multiply(IndependentReference.tenTo(49)),       // was 0
            "frac(-pi*10^50)" to pi.multiply(IndependentReference.tenTo(50)).negate(),
            "frac(sqrt(2)*10^49)" to IndependentReference.root(2).multiply(IndependentReference.tenTo(49)), // was 0
            "frac(pi*10^45)" to pi.multiply(IndependentReference.tenTo(45)),       // was 0.3751, four digits
            "frac(pi*10^20)" to pi.multiply(IndependentReference.tenTo(20)),
            "frac(pi*10^1000)" to pi.multiply(IndependentReference.tenTo(1000)),
            "frac(e*10^30)" to IndependentReference.E.multiply(IndependentReference.tenTo(30)),
            "frac(pi)" to pi,
            "frac(-pi)" to pi.negate(),
            "frac(100*sqrt(3))" to IndependentReference.root(3).multiply(BigDecimal(100)),
            "frac(7*e)" to IndependentReference.E.multiply(BigDecimal(7)),
            // a fraction far smaller than the value: 10^25 + 5·10^-26 − …
            "frac(sqrt(10^50+1))" to IndependentReference.root(IndependentReference.tenTo(50).add(BigDecimal.ONE)),
            "frac(-sqrt(10^50+1))" to IndependentReference.root(IndependentReference.tenTo(50).add(BigDecimal.ONE)).negate(), // was −0.000…
            "frac(sqrt(10^50+10^25))" to IndependentReference.root(IndependentReference.tenTo(50).add(IndependentReference.tenTo(25))),
        ).forEach { (text, value) -> withClue(text) { same(decimal(text), IndependentReference.fifty(IndependentReference.fraction(value))) } }
    }

    "a value below one in size is its own fractional part and stays exact" {
        num("frac(sqrt(2)/2)") shouldBe num("sqrt(2)/2")
        (num("frac(sqrt(2)/2)") is Surd) shouldBe true
        num("frac(-pi/4)") shouldBe num("-pi/4")
        (num("frac(-pi/4)") is SymConst) shouldBe true
    }

    "the fractional part of a known-point decimal uses every held digit" {
        // fifty-two digits: the tower's subtraction rounded the value to fifty first
        NumMath.frac(Dec(BigDecimal("12345678901234567890123456789012345678901234567890.25"), BigDecimal.ZERO)) shouldBe Dec(BigDecimal("0.25"), BigDecimal.ZERO)
        NumMath.frac(Dec(BigDecimal("-2.5"), BigDecimal.ZERO)) shouldBe Dec(BigDecimal("-0.5"), BigDecimal.ZERO)
        NumMath.frac(Dec(BigDecimal("0.25"), BigDecimal.ZERO)) shouldBe Dec(BigDecimal("0.25"), BigDecimal.ZERO)
        NumMath.frac(Dec(BigDecimal("1E+60"), BigDecimal.ZERO)) shouldBe Dec(BigDecimal.ZERO, BigDecimal.ZERO)
        same(decimal("frac(approx(-2.5,0))"), BigDecimal("-0.5"))
        same(decimal("frac(approx(7.25,0))"), BigDecimal("0.25"))
    }

    "a remainder against an irrational is taken from enough digits" {
        val pi = IndependentReference.PI
        val e = IndependentReference.E
        val rootTwo = IndependentReference.root(2)
        listOf(
            Triple("mod(pi*10^50, 1)", pi.multiply(IndependentReference.tenTo(50)), BigDecimal.ONE),            // was 0
            Triple("mod(10^60, pi)", IndependentReference.tenTo(60), pi),                                         // was 10000000000
            Triple("mod(sqrt(2)*10^49, 3)", rootTwo.multiply(IndependentReference.tenTo(49)), BigDecimal(3)),     // was 0
            Triple("mod(10^49, sqrt(2))", IndependentReference.tenTo(49), rootTwo),                               // was 1.0
            Triple("mod(pi*10^30, e)", pi.multiply(IndependentReference.tenTo(30)), e),                           // had twenty digits
            Triple("mod(e*10^1000, pi)", e.multiply(IndependentReference.tenTo(1000)), pi),
            Triple("mod(10, pi)", BigDecimal.TEN, pi),
            Triple("mod(pi, sqrt(2))", pi, rootTwo),
            Triple("mod(-sqrt(2), 5)", rootTwo.negate(), BigDecimal(5)),
            Triple("mod(pi, -2)", pi, BigDecimal(-2)),
            Triple("mod(-pi, -2)", pi.negate(), BigDecimal(-2)),
            Triple("mod(-10^40, pi)", IndependentReference.tenTo(40).negate(), pi),
            Triple("mod(10^40, -e)", IndependentReference.tenTo(40), e.negate()),
            // a decimal against an irrational: the decimal is taken at the digits it holds
            Triple("mod(approx(10,0), pi)", BigDecimal.TEN, pi),
            Triple("mod(pi*10^20, approx(7,0))", pi.multiply(IndependentReference.tenTo(20)), BigDecimal(7)),
            Triple("mod(approx(10^20,0), pi)", IndependentReference.tenTo(20), pi),
        ).forEach { (text, a, b) ->
            withClue(text) {
                val answer = decimal(text)
                same(answer, IndependentReference.fifty(IndependentReference.remainder(a, b)))
                answer.signum() shouldBe b.signum()                  // the sign of the divisor
                (answer.abs() < b.abs()) shouldBe true               // and smaller than it
            }
        }
    }

    "remainders agree with the reference as the quotient grows from one digit to forty" {
        val rootThree = IndependentReference.root(3)
        val rootFive = IndependentReference.root(5)
        for (k in 1..40) {
            val scale = IndependentReference.tenTo(k).multiply(BigDecimal(k))
            withClue("k = $k") {
                same(decimal("mod($k*pi*10^$k, sqrt(3))"), IndependentReference.fifty(IndependentReference.remainder(IndependentReference.PI.multiply(scale), rootThree)))
                same(decimal("mod(-$k*sqrt(5)*10^$k, e)"), IndependentReference.fifty(IndependentReference.remainder(rootFive.multiply(scale).negate(), IndependentReference.E)))
            }
        }
    }

    "a quotient that is an exact fraction leaves an exact remainder" {
        mapOf(
            "mod(3pi, 2pi)" to "pi",
            "mod(-pi, 2pi)" to "pi",
            "mod(5*e, 2*e)" to "e",
            "mod(7*sqrt(2), 2*sqrt(2))" to "sqrt(2)",
            // 33603602 = 2·4099², past the reach of trial division, so the surd is stored unreduced
            "mod(sqrt(33603602), 3*sqrt(2))" to "sqrt(2)",
            // a quotient between 0 and 1: the dividend is its own remainder, kept as it is
            "mod(sqrt(2), 5)" to "sqrt(2)",
            "mod(pi, 10^60)" to "pi",
        ).forEach { (text, expected) -> withClue(text) { num(text) shouldBe num(expected) } }
        listOf("mod(2pi, pi)", "mod(sqrt(8), sqrt(2))", "mod(sqrt(33603602), sqrt(2))", "mod(6*sqrt(3), -2*sqrt(3))", "mod(0, pi)", "mod(0, sqrt(2))")
            .forEach { text -> withClue(text) { num(text) shouldBe Num.ZERO } }
    }

    "a remainder of a known-point decimal is taken from its held digits" {
        same(decimal("mod(approx(10^20,0), 7)"), BigDecimal(BigInteger.TEN.pow(20).mod(BigInteger.valueOf(7))))
        same(decimal("mod(approx(15*10^19,0), 7)"), BigDecimal(BigInteger("15").multiply(BigInteger.TEN.pow(19)).mod(BigInteger.valueOf(7))))
        // the engine's own form takes any decimal at the digits it holds, however large
        NumMath.mod(Dec(BigDecimal("15E+59"), BigDecimal.ZERO), Num.of(7)) shouldBe Dec(BigDecimal(BigInteger("15").multiply(BigInteger.TEN.pow(59)).mod(BigInteger.valueOf(7))), BigDecimal.ZERO)
        NumMath.mod(Dec(BigDecimal("1E+30000"), BigDecimal.ZERO), Num.of(7)) shouldBe Dec(BigDecimal.ONE, BigDecimal.ZERO)
        same(decimal("mod(approx(7.5,0), 2)"), BigDecimal("1.5"))
        same(decimal("mod(approx(-7.5,0), 2)"), BigDecimal("0.5"))
        same(decimal("mod(approx(7.5,0), -2)"), BigDecimal("-0.5"))
        same(decimal("mod(7.5, approx(2,0))"), BigDecimal("1.5"))
        same(decimal("mod(approx(6,0), 3)"), BigDecimal.ZERO)
        same(decimal("mod(approx(0.3,0), approx(0.1,0))"), BigDecimal.ZERO)
        same(decimal("mod(approx(1/10^60,0), 1)"), BigDecimal("1E-60"))
        same(decimal("mod(approx(0,0), 7)"), BigDecimal.ZERO)
        NumMath.mod(Dec(BigDecimal("1E+60"), BigDecimal.ZERO), Num.of(7)) shouldBe Dec(BigDecimal.ONE, BigDecimal.ZERO)
        NumMath.mod(Dec(BigDecimal("12345678901234567890123456789012345678901234567890.25"), BigDecimal.ZERO), Num.ONE) shouldBe Dec(BigDecimal("0.25"), BigDecimal.ZERO)
    }

    "what fifty digits cannot hold is refused, not rounded onto the edge of the range" {
        mapOf(
            // 1 − 5·10^-61: the fractional part would be written as 1
            "frac(sqrt(10^120-1))" to "too close to 1",
            // the remainder would be written as the divisor itself
            "mod(-pi/10^60, 1)" to "too close to the divisor",
            "mod(-pi, 10^60)" to "too close to the divisor",
            "mod(approx(-1/10^60), 1)" to "no established error bound",
            // past the widest expansion
            "frac(pi*10^1100)" to "too large for its fractional part",
            "mod(pi*10^1100, 1)" to "too large for the remainder",
            "mod(pi*10^2000, 1)" to "quotient is too large",
            "floor(pi*10^2000)" to "for its whole part",
            "ceil(-sqrt(2)*10^2000)" to "for its whole part",
            "round(pi*10^2000)" to "for its whole part",
            "int(e*10^2000)" to "for its whole part",
        ).forEach { (text, reason) ->
            withClue(text) {
                val error = refusal(text)
                (error is EvalError.Unsupported) shouldBe true
                error.detail shouldContain reason
            }
        }
        (refusal("mod(approx(10^30000,0), approx(1/10^30000,0))") is EvalError.Overflow) shouldBe true
        (refusal("mod(pi, 0)") is EvalError.DivideByZero) shouldBe true
        (refusal("mod(5, approx(0,0))") is EvalError.DivideByZero) shouldBe true
        (refusal("mod(5, approx(0))") is EvalError.Unsupported) shouldBe true
    }
})
