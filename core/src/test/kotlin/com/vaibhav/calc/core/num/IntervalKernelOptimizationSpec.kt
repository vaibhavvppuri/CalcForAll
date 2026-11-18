package com.vaibhav.calc.core.num

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class IntervalKernelOptimizationSpec : StringSpec({
    val mixed = DecimalBounds(BigDecimal(-7), BigDecimal(5))

    "sign-aware multiplication encloses exact corner extrema in every sign quadrant" {
        val intervals = listOf(
            "1.2345" to "5.6789", "-9.8765" to "-0.1234", "-4.1234" to "7.4321",
            "0" to "2.3456", "-2.3456" to "0", "0" to "0.00",
            "1.2300" to "1.23", "-1.2300" to "-1.23",
            "0.00000012345" to "0.00000056789",
        ).map { (low, high) -> DecimalBounds(BigDecimal(low), BigDecimal(high)) }
        for (a in intervals) for (b in intervals) for (precision in listOf(1, 6, 17)) {
            // Independent oracle: first form all four exact products, then round only
            // the mathematical extrema. This does not use production sign selection.
            val corners = listOf(a.lower * b.lower, a.lower * b.upper, a.upper * b.lower, a.upper * b.upper)
            val exactLow = corners.minOrNull()!!
            val exactHigh = corners.maxOrNull()!!
            val result = a.multiply(b, precision)
            withClue("$a * $b at precision $precision") {
                result.lower.compareTo(exactLow.round(MathContext(precision, RoundingMode.FLOOR))) shouldBe 0
                result.upper.compareTo(exactHigh.round(MathContext(precision, RoundingMode.CEILING))) shouldBe 0
                (result.lower <= exactLow && result.upper >= exactHigh) shouldBe true
            }
        }
    }

    "multiplication retains magnitude preflight for selected and unselected corners" {
        shouldThrow<ArithFault.Overflow> {
            DecimalBounds.point(BigDecimal("1e20000")).multiply(DecimalBounds.point(BigDecimal("1e20000")), 66)
        }
        shouldThrow<ArithFault.Overflow> {
            DecimalBounds.point(BigDecimal("1e-20000")).multiply(DecimalBounds.point(BigDecimal("1e-20000")), 66)
        }
        // Both true extrema fit, but an unused corner is below the old magnitude
        // budget. Selecting fewer products must not silently remove that guard.
        val tiny = BigDecimal("1e-20000")
        val crossing = DecimalBounds(tiny.negate(), BigDecimal.ONE)
        val other = DecimalBounds(BigDecimal.ONE.negate(), tiny)
        shouldThrow<ArithFault.Overflow> { crossing.multiply(other, 66) }
        shouldThrow<ArithFault.Overflow> { other.multiply(crossing, 66) }
        val zero = DecimalBounds.point(BigDecimal.ZERO)
        zero.multiply(DecimalBounds.point(BigDecimal("1e32768")), 66).containsZero shouldBe true
    }

    "division by a positive point rounds the two endpoints outward directly" {
        // Equal values with different scales are still one exact denominator.
        val result = mixed.divide(DecimalBounds(BigDecimal("3.0"), BigDecimal("3.00")), 6)
        result.lower.compareTo(BigDecimal("-2.33334")) shouldBe 0
        result.upper.compareTo(BigDecimal("1.66667")) shouldBe 0
        (Num.ofDecimal(result.lower) <= Num.of(-7, 3)) shouldBe true
        (Num.ofDecimal(result.upper) >= Num.of(5, 3)) shouldBe true
    }

    "division by a negative point reverses endpoint order before directed rounding" {
        val result = mixed.divide(DecimalBounds.point(BigDecimal(-3)), 6)
        result.lower.compareTo(BigDecimal("-1.66667")) shouldBe 0
        result.upper.compareTo(BigDecimal("2.33334")) shouldBe 0
        (Num.ofDecimal(result.lower) <= Num.of(-5, 3)) shouldBe true
        (Num.ofDecimal(result.upper) >= Num.of(7, 3)) shouldBe true
    }

    "point division retains zero and magnitude guards and general interval division" {
        shouldThrow<ArithFault.Unsupported> { mixed.divide(DecimalBounds.point(BigDecimal.ZERO), 6) }
        shouldThrow<ArithFault.Unsupported> { mixed.divide(DecimalBounds(BigDecimal(-1), BigDecimal.ONE), 6) }
        shouldThrow<ArithFault.Overflow> {
            DecimalBounds.point(BigDecimal("1e32768")).divide(DecimalBounds.point(BigDecimal("0.01")), 66)
        }
        shouldThrow<ArithFault.Overflow> {
            DecimalBounds.point(BigDecimal("1e-32768")).divide(DecimalBounds.point(BigDecimal(100)), 66)
        }
        val result = mixed.divide(DecimalBounds(BigDecimal(2), BigDecimal(3)), 12)
        result.lower.compareTo(BigDecimal("-3.5")) shouldBe 0
        result.upper.compareTo(BigDecimal("2.5")) shouldBe 0
    }

    "reusing a Taylor remainder term preserves independently checked trig enclosures" {
        // These prefixes were computed from 180 exact rational Taylor terms, independently
        // of both BigDecimalMath and CertifiedMath. The omitted alternating term bounds
        // their truncation; the reference intervals below include decimal-prefix truncation.
        val cases = listOf(
            Triple("1", "0.841470984807896506652502321630298999622563060798371065672751709991910404391239668948639743543052695854349037907920674293", "0.540302305868139717400936607442976603732310420617922227670097255381100394774471764517951856087183089343571731160030089097"),
            Triple("7", "0.656986598718789090396999091593635177936870010497490074657854334189292837131227031509935121601055212681293170521097576396", "0.753902254343304638141197521719182012218313391460126839543613880813876026720717405625428391089302482541417434794653362444"),
            Triple("0.01", "0.009999833334166664682542438269099729038964385360169151033879112479409734509063915965942636792961498990152518256893760673", "0.999950000416665277780257933752206673212470583980277111122275768646460088129643491635125543761297372656230561941513041834"),
        )
        val error = BigDecimal("1e-115")
        fun contains(interval: DecimalBounds, expected: BigDecimal) {
            withClue("$interval should enclose $expected ± $error") {
                (interval.lower <= expected.subtract(error) && interval.upper >= expected.add(error)) shouldBe true
            }
        }
        for ((argument, sine, cosine) in cases) {
            val x = BigDecimal(argument)
            contains(CertifiedMath.sin(DecimalBounds.point(x), 60), BigDecimal(sine))
            contains(CertifiedMath.sin(DecimalBounds.point(x.negate()), 60), BigDecimal(sine).negate())
            contains(CertifiedMath.cos(DecimalBounds.point(x), 60), BigDecimal(cosine))
            contains(CertifiedMath.cos(DecimalBounds.point(x.negate()), 60), BigDecimal(cosine))
        }
        val zero = DecimalBounds.point(BigDecimal.ZERO)
        CertifiedMath.sin(zero, 60).lower.signum() shouldBe 0
        CertifiedMath.sin(zero, 60).upper.signum() shouldBe 0
        CertifiedMath.cos(zero, 60).lower.compareTo(BigDecimal.ONE) shouldBe 0
        CertifiedMath.cos(zero, 60).upper.compareTo(BigDecimal.ONE) shouldBe 0
    }
})
