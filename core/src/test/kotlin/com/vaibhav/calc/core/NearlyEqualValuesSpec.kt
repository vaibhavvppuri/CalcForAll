package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.Rational
import com.vaibhav.calc.core.num.unaryMinus
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Two exact values that agree to more digits than the tower's fifty. Subtracted, they read 0;
 * compared, a pair that agreed to 320 digits was called equal, so `√(10^700+1) = 10^350` held.
 * Found while fixing the ninth review pass's findings, which were the same flaw in `round`,
 * `frac` and `mod`. The reference values are [IndependentReference]'s, not the engine's.
 */
class NearlyEqualValuesSpec : StringSpec({
    val c = Calculator()
    val ref = IndependentReference
    fun num(text: String): Num = (c.evaluate(text).getOrThrow() as RealValue).num
    fun decimal(text: String): BigDecimal = (num(text) as? Dec)?.value ?: throw AssertionError("'$text' is not a decimal: ${num(text)}")
    fun refusal(text: String): EvalError =
        (c.evaluate(text) as? CalcResult.Err)?.error ?: throw AssertionError("'$text' was answered: ${num(text)}")
    fun holds(equation: String): Boolean = when (val r = c.route(equation)) {
        is RouteResult.Ok -> (r.outcome as? Outcome.Verdict)?.holds ?: throw AssertionError("'$equation' did not compare: ${r.outcome}")
        else -> throw AssertionError("'$equation' did not evaluate: $r")
    }
    fun same(actual: BigDecimal, expected: BigDecimal) = withClue("got $actual, wanted $expected") { actual.compareTo(expected) shouldBe 0 }

    "a difference of two exact values keeps fifty good digits however closely they agree" {
        val piToFifty = BigDecimal("3.14159265358979323846264338327950288419716939937510")
        val rootTwoToFifty = BigDecimal("1.4142135623730950488016887242096980785696718753769")
        mapOf(
            "sqrt(10^100+1) - 10^50" to ref.root(ref.tenTo(100).add(BigDecimal.ONE)).subtract(ref.tenTo(50)),               // was 0
            "10^50 - sqrt(10^100+1)" to ref.tenTo(50).subtract(ref.root(ref.tenTo(100).add(BigDecimal.ONE))),               // was 0
            "sqrt(10^700+1) - 10^350" to ref.root(ref.tenTo(700).add(BigDecimal.ONE)).subtract(ref.tenTo(350)),             // was 0
            "sqrt(10^40+1) - 10^20" to ref.root(ref.tenTo(40).add(BigDecimal.ONE)).subtract(ref.tenTo(20)),                 // had nine digits
            "pi*10^60 - floor(pi*10^60)" to ref.fraction(ref.PI.multiply(ref.tenTo(60))),                                     // was 0
            "pi - 3.14159265358979323846264338327950288419716939937510" to ref.PI.subtract(piToFifty),                       // was 0
            "sqrt(2) - 1.4142135623730950488016887242096980785696718753769" to ref.root(2).subtract(rootTwoToFifty),         // was 0
            "sqrt(10^60+1) - sqrt(10^60+2)" to ref.root(ref.tenTo(60).add(BigDecimal.ONE)).subtract(ref.root(ref.tenTo(60).add(BigDecimal(2)))),
            "e*10^30 - floor(e*10^30)" to ref.fraction(ref.E.multiply(ref.tenTo(30))),
        ).forEach { (text, value) -> withClue(text) { same(decimal(text), ref.fifty(value)) } }
    }

    "ordinary sums are the fifty-digit sums they always were" {
        // digit for digit what 1.2 returned: little or nothing cancels, so nothing is recomputed
        mapOf(
            "sqrt(2) - 1" to "0.4142135623730950488016887242096980785696718753769",
            "pi - 3" to "0.1415926535897932384626433832795028841971693993751",
            "sqrt(3) - sqrt(2)" to "0.3178372451957822447257576172961742883731333784335",
            "pi - e" to "0.4233108251307480031023559119268403864399223056751",
            "pi + e" to "5.8598744820488384738229308546321653819544164930751",
            "sqrt(2) + sqrt(3)" to "3.1462643699419723423291350657155704455124771291873",
            "sqrt(2)*10^30 + pi" to "1414213562373095048801688724212.8396712232616686154",
        ).forEach { (text, expected) -> withClue(text) { decimal(text).toPlainString() shouldBe expected } }
        // a few digits cancel: at least forty good ones remain
        val close = decimal("pi - 355/113")
        val wanted = ref.PI.subtract(BigDecimal(355).divide(BigDecimal(113), ref.WIDE))
        (close.subtract(wanted).abs() < wanted.abs().scaleByPowerOfTen(-40)) shouldBe true
    }

    "two surds that are the same number cancel to an exact zero" {
        // 33603602 = 2·4099²: trial division stops at 4096, so the surd is stored unreduced
        num("sqrt(33603602) - 4099*sqrt(2)") shouldBe Num.ZERO
        num("4099*sqrt(2) - sqrt(33603602)") shouldBe Num.ZERO
        holds("sqrt(33603602) = 4099*sqrt(2)") shouldBe true
        holds("sqrt(33603602) = 4098*sqrt(2)") shouldBe false
    }

    "what cannot be held in fifty digits is still refused" {
        mapOf(
            "10^80 + sqrt(2)" to "to preserve both operands",
            "1 - sqrt(2)/10^60" to "to preserve both operands",
            // the two agree to 2,400 digits, past the widest expansion
            "sqrt(10^2400+1) - 10^1200" to "to tell the two values apart",
        ).forEach { (text, reason) ->
            withClue(text) {
                val error = refusal(text)
                (error is EvalError.Unsupported) shouldBe true
                error.detail shouldContain reason
            }
        }
    }

    "values that agree to hundreds of digits are not called equal" {
        holds("sqrt(10^700+1) = 10^350") shouldBe false            // held until 1.3
        holds("sqrt(10^700+1) = sqrt(10^700+2)") shouldBe false    // held until 1.3
        holds("pi*10^400 = floor(pi*10^400)") shouldBe false       // held until 1.3
        holds("pi = floor(pi*10^400)/10^400") shouldBe false
        holds("sqrt(10^100+1) = 10^50") shouldBe false
        holds("sqrt(2)^2 = 2") shouldBe true
        holds("sqrt(8) = 2*sqrt(2)") shouldBe true
        holds("pi = 3.14159265358979323846264338327950288419716939937510") shouldBe false
    }

    "the larger and the smaller of two close values are the right ones" {
        val surd = num("sqrt(10^700+1)")
        val whole = Num.of(BigInteger.TEN.pow(350))
        num("max(sqrt(10^700+1), 10^350)") shouldBe surd
        num("min(sqrt(10^700+1), 10^350)") shouldBe whole            // was the surd: the two compared as equal
        num("max(-sqrt(10^700+1), -10^350)") shouldBe num("-10^350")
        num("min(-sqrt(10^700+1), -10^350)") shouldBe num("-sqrt(10^700+1)")
        num("max(sqrt(10^700+2), sqrt(10^700+1))") shouldBe num("sqrt(10^700+2)")
        num("min(pi, floor(pi*10^400)/10^400)") shouldBe num("floor(pi*10^400)/10^400")
        num("max(pi, floor(pi*10^400)/10^400)") shouldBe num("pi")
    }

    "comparison across kinds is exact where it can be" {
        val half = Num.HALF
        Dec(BigDecimal("0.5")).compareTo(half) shouldBe 0
        half.compareTo(Dec(BigDecimal("0.50000"))) shouldBe 0
        // a fraction that differs from the decimal at its 400th place: compared as equal until 1.3
        val nudged: Rational = Num.of(BigInteger.TEN.pow(400).add(BigInteger.valueOf(2)), BigInteger.TEN.pow(400).multiply(BigInteger.valueOf(2)))
        (nudged.compareTo(Dec(BigDecimal("0.5"))) > 0) shouldBe true
        (Dec(BigDecimal("0.5")).compareTo(nudged) < 0) shouldBe true
        (Dec(BigDecimal("-0.5")).compareTo(-nudged) > 0) shouldBe true
        // a third against its fifty-digit decimal
        val third = Num.of(1, 3)
        (Dec(BigDecimal("0." + "3".repeat(50))).compareTo(third) < 0) shouldBe true
        // a surd against its own fifty-digit decimal, either side of it
        val rootTwo = num("sqrt(2)")
        val below = Dec(IndependentReference.root(2).round(MathContext(50, RoundingMode.DOWN)))
        val above = Dec(IndependentReference.root(2).round(MathContext(50, RoundingMode.UP)))
        (below.compareTo(rootTwo) < 0) shouldBe true
        (above.compareTo(rootTwo) > 0) shouldBe true
        (rootTwo.compareTo(below) > 0) shouldBe true
        ((-rootTwo).compareTo(Dec(below.value.negate())) < 0) shouldBe true
        // opposite signs and zero
        (num("-sqrt(2)").compareTo(Dec(BigDecimal("1.4"))) < 0) shouldBe true
        Dec(BigDecimal.ZERO).compareTo(Num.ZERO) shouldBe 0
        (num("pi").compareTo(Dec(BigDecimal.ZERO)) > 0) shouldBe true
    }

    "a multiple of π that cannot be told from a fraction within the widest expansion is refused, not called equal" {
        val pi = num("pi")
        val twelveHundredPlaces: Rational = Num.ofDecimal(IndependentReference.PI.setScale(1200, RoundingMode.DOWN))
        val fault = shouldThrow<ArithFault.Unsupported> { pi.compareTo(twelveHundredPlaces) }
        fault.message!! shouldContain "cannot be told apart"
        // 1,100 places are within reach, and π is the larger
        val elevenHundredPlaces: Rational = Num.ofDecimal(IndependentReference.PI.setScale(1100, RoundingMode.DOWN))
        (pi.compareTo(elevenHundredPlaces) > 0) shouldBe true
        (elevenHundredPlaces.compareTo(pi) < 0) shouldBe true
    }
})
