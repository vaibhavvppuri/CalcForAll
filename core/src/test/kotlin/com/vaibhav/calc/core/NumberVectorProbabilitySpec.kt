package com.vaibhav.calc.core

import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.matrix.Vectors
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Dec
import java.math.BigDecimal
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.NumberTheory
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.stats.CurveFit
import com.vaibhav.calc.core.stats.Distributions
import com.vaibhav.calc.core.stats.Quartiles
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/** CalcForAll 1.2: number theory, vectors, distributions and curve fits against known answers. */
class NumberVectorProbabilitySpec : StringSpec({
    val mc = MathContext(40, RoundingMode.HALF_EVEN)
    fun big(n: Long): BigInteger = BigInteger.valueOf(n)
    fun v(vararg n: Int): List<Num> = n.map { Num.of(it) }
    fun Num.d(): Double = toBigDecimal(MathContext(25)).toDouble()

    // ---- number theory -----------------------------------------------------

    "prime factorisation, ascending with exponents" {
        NumberTheory.factorize(big(360)) shouldBe listOf(big(2) to 3, big(3) to 2, big(5) to 1)
        NumberTheory.factorize(big(97)) shouldBe listOf(big(97) to 1)
        NumberTheory.factorize(BigInteger.TEN.pow(14)) shouldBe listOf(big(2) to 14, big(5) to 14)
        NumberTheory.factorize(big(1_000_000_007L * 3)) shouldBe listOf(big(3) to 1, big(1_000_000_007) to 1)
    }

    "factorisation is bounded and says so" {
        shouldThrow<ArithFault.Domain> { NumberTheory.factorize(BigInteger.ONE) }
        shouldThrow<ArithFault.Domain> { NumberTheory.factorize(BigInteger.TEN.pow(14).add(BigInteger.ONE)) }
    }

    "primality is deterministic" {
        NumberTheory.isPrime(big(2)) shouldBe true
        NumberTheory.isPrime(big(1)) shouldBe false
        NumberTheory.isPrime(big(91)) shouldBe false
        NumberTheory.isPrime(big(1_000_000_007)) shouldBe true
    }

    "divisors are complete and ascending" {
        NumberTheory.divisors(big(28)) shouldBe listOf(1L, 2, 4, 7, 14, 28).map { big(it) }
        NumberTheory.divisors(big(360)).size shouldBe 24
        NumberTheory.divisors(BigInteger.ONE) shouldBe listOf(BigInteger.ONE)
    }

    "gcd and lcm of several numbers" {
        val values = listOf(big(84), big(36), big(120))
        NumberTheory.gcd(values) shouldBe big(12)
        NumberTheory.lcm(values) shouldBe big(2520)
        NumberTheory.gcd(listOf(big(0), big(5))) shouldBe big(5)
        NumberTheory.lcm(listOf(big(0), big(5))) shouldBe big(0)
        NumberTheory.lcm(listOf(big(-4), big(6))) shouldBe big(12)
        shouldThrow<ArithFault.Domain> { NumberTheory.gcd(listOf(big(5))) }
    }

    // ---- vectors -----------------------------------------------------------

    "dot, cross and projection are exact" {
        Vectors.dot(v(1, 2, 3), v(4, 5, 6)) shouldBe Num.of(32)
        Vectors.cross(v(1, 2, 3), v(4, 5, 6)) shouldBe v(-3, 6, -3)
        Vectors.projection(v(3, 4), v(1, 0)) shouldBe v(3, 0)
    }

    "length is an integer or a surd, not a rounded decimal" {
        Vectors.length(v(3, 4), mc) shouldBe Num.of(5)
        val root14 = Vectors.length(v(1, 2, 3), mc)
        (root14 * root14).compareTo(Num.of(14)) shouldBe 0
    }

    "the cosine of the angle between vectors" {
        Vectors.cosine(v(1, 0), v(0, 1), mc) shouldBe Num.of(0)
        Vectors.cosine(v(2, 0), v(5, 0), mc) shouldBe Num.of(1)
        Vectors.cosine(v(1, 0), v(1, 1), mc).d() shouldBe (0.7071067811865476 plusOrMinus 1e-15)
    }

    "vector shape errors are typed" {
        shouldThrow<ArithFault.Domain> { Vectors.dot(v(1, 2), v(1, 2, 3)) }
        shouldThrow<ArithFault.Domain> { Vectors.cross(v(1, 2), v(3, 4)) }
        shouldThrow<ArithFault.Domain> { Vectors.cosine(v(0, 0), v(1, 1), mc) }
        shouldThrow<ArithFault.Domain> { Vectors.projection(v(1, 1), v(0, 0)) }
    }

    // ---- distributions -----------------------------------------------------

    "binomial probabilities are exact fractions for a fair coin" {
        val t = Distributions.binomial(10, 3, Num.HALF, mc)
        t.exactly shouldBe Num.of(15, 128)
        t.atMost shouldBe Num.of(11, 64)
    }

    "binomial edge cases: certain failure, certain success, and a total of exactly 1" {
        Distributions.binomial(5, 0, Num.ZERO, mc).exactly shouldBe Num.ONE
        Distributions.binomial(5, 5, Num.ONE, mc).exactly shouldBe Num.ONE
        Distributions.binomial(20, 20, Num.of(1, 3), mc).atMost shouldBe Num.ONE
        shouldThrow<ArithFault.Domain> { Distributions.binomial(5, 6, Num.HALF, mc) }
        shouldThrow<ArithFault.Domain> { Distributions.binomial(5, 2, Num.of(3, 2), mc) }
    }

    "Poisson with average 4: P(2) = 8e⁻⁴, P(≤2) = 13e⁻⁴" {
        val t = Distributions.poisson(2, Num.of(4), mc)
        t.exactly.d() shouldBe (0.14652511110987343 plusOrMinus 1e-15)
        t.atMost.d() shouldBe (0.23810330555354436 plusOrMinus 1e-15)
        shouldThrow<ArithFault.Domain> { Distributions.poisson(2, Num.ZERO, mc) }
    }

    "the normal curve: table values, symmetry, and a deep tail that keeps its digits" {
        Distributions.normalCdf(Num.ZERO, mc) shouldBe Num.HALF
        Distributions.normalCdf(Num.of(1), mc).d() shouldBe (0.8413447460685429 plusOrMinus 1e-15)
        Distributions.normalCdf(Num.of(-1), mc).d() shouldBe (0.15865525393145705 plusOrMinus 1e-15)
        Distributions.normalCdf(Num.of(196, 100), mc).d() shouldBe (0.9750021048517795 plusOrMinus 1e-15)
        Distributions.normalCdf(Num.of(2), mc).d() shouldBe (0.9772498680518208 plusOrMinus 1e-15)
        val sum = Distributions.normalCdf(Num.of(3), mc) + Distributions.normalCdf(Num.of(-3), mc)
        sum.d() shouldBe (1.0 plusOrMinus 1e-30)
        val tail = Distributions.normalCdf(Num.of(-8), mc).d()
        (tail / 6.220960574271785e-16) shouldBe (1.0 plusOrMinus 1e-10)
        shouldThrow<ArithFault.Domain> { Distributions.normalCdf(Num.of(41), mc) }
    }

    "tails inside the documented ranges are computed, not refused (review regression)" {
        val far = Distributions.poisson(100, Num.of(4), mc)
        far.atMost.d() shouldBe (1.0 plusOrMinus 1e-15)
        (far.above.d() > 1e-103 && far.above.d() < 1e-99) shouldBe true
        (Distributions.poisson(0, Num.of(120), mc).exactly.d() / 7.667648073722e-53) shouldBe (1.0 plusOrMinus 1e-9)
        Distributions.poisson(2, Num.of(4), mc).above.d() shouldBe (0.7618966944464556 plusOrMinus 1e-14)
        (Distributions.normalCdf(Num.of(-15), mc).d() / 3.67096619931275e-51) shouldBe (1.0 plusOrMinus 1e-9)
        shouldThrow<ArithFault.Domain> { Distributions.poisson(2, Num.of(10_001), mc) }
    }

    "binomial at its documented limit: exact for a modest fraction, decimal for a fine one, quick either way" {
        val modest = Distributions.binomial(2000, 2000, Num.of(1234, 10_000), mc)
        modest.atMost shouldBe Num.ONE
        modest.above shouldBe Num.ZERO
        val fine = Distributions.binomial(2000, 2000, Num.of(123_456_789, 1_000_000_000), mc)
        fine.atMost.d() shouldBe (1.0 plusOrMinus 1e-15)
        val half = Distributions.binomial(2000, 1000, Num.HALF, mc)
        half.exactly.d() shouldBe (0.0178390111 plusOrMinus 1e-9)
        (half.atMost + half.above) shouldBe Num.ONE
    }

    "a probability just below 1 keeps its complement (second-review regression)" {
        // p = 1 − 10⁻⁷⁰ with n = 2000 is too fine for the exact path; rounding p first would make q = 0.
        val almost = Num.of(BigInteger.TEN.pow(70).subtract(BigInteger.ONE), BigInteger.TEN.pow(70))
        val decimal = Distributions.binomial(2000, 1999, almost, mc)
        (decimal.exactly.d() / 2e-67) shouldBe (1.0 plusOrMinus 1e-6)
        decimal.above.d() shouldBe (1.0 plusOrMinus 1e-15)
        // The same p with fewer trials fits the exact path, and the two paths agree.
        val exact = Distributions.binomial(200, 199, almost, mc)
        (exact.exactly.d() / 2e-68) shouldBe (1.0 plusOrMinus 1e-6)
        (exact.atMost + exact.above) shouldBe Num.ONE
    }

    "X values that coincide after the transform are refused with a reason, not a raw division error" {
        val close = Num.of(BigInteger.TEN.pow(70).add(BigInteger.ONE), BigInteger.TEN.pow(70))
        val points = listOf(Num.ONE as Num to Num.of(1) as Num, close as Num to Num.of(2) as Num)
        shouldThrow<ArithFault.Domain> { CurveFit.logarithmic(points, mc) }
    }

    "X values whose spread is only rounding are refused, not fitted to a fabricated slope (third-review regression)" {
        // 2.5, 2.5 + 1e-80 and 2.5 + 2e-80: three distinct fractions, one value at working precision.
        val scale = BigInteger.TEN.pow(80)
        fun x(k: Long): Num = Num.of(BigInteger.valueOf(25).multiply(scale).add(BigInteger.valueOf(10 * k)), BigInteger.TEN.multiply(scale))
        val points = listOf(x(0) to Num.of(1) as Num, x(1) to Num.of(2) as Num, x(2) to Num.of(4) as Num)
        shouldThrow<ArithFault.Domain> { CurveFit.logarithmic(points, mc) }.message shouldContain "too close together"
        shouldThrow<ArithFault.Domain> { CurveFit.power(points, mc) }
        shouldThrow<ArithFault.Domain> { CurveFit.exponential(points, mc) }
        // Independently computed with Python Decimal.ln at 120 digits, then held as decimal
        // observations. Their zero error describes those chosen data points, not exact ln values.
        val heldY = listOf(
            "30.6310211159285482082158974562123704912132178635452757123999348",
            "30.6310231159275482088825636228794371575465514825926447602412044",
            "30.6310251159245482135492227895585038032132544349098472547489364",
        ).map { Dec(BigDecimal(it), BigDecimal.ZERO) }
        val near = listOf(1_000_000, 1_000_001, 1_000_002).zip(heldY).map { (x, y) -> Num.of(x) as Num to y as Num }
        shouldThrow<ArithFault.Unsupported> {
            CurveFit.logarithmic(near.map { (x, y) -> x to Dec((y as Dec).value) }, mc)
        }
        val fit = CurveFit.logarithmic(near, mc)
        fit.a.d() shouldBe (3.0 plusOrMinus 1e-9)
        fit.b.d() shouldBe (2.0 plusOrMinus 1e-9)
    }

    "quartiles by the median-of-halves method, and modes (1.3)" {
        val even = Quartiles.of((1..8).map { Num.of(it) })
        Triple(even.q1, even.median, even.q3) shouldBe Triple(Num.of(5, 2), Num.of(9, 2), Num.of(13, 2))
        even.modes shouldBe emptyList()
        // odd count: the middle value belongs to neither half
        val odd = Quartiles.of(listOf(7, 1, 3, 9, 5).map { Num.of(it) })
        Triple(odd.q1, odd.median, odd.q3) shouldBe Triple(Num.of(2), Num.of(5), Num.of(8))
        val repeats = Quartiles.of(listOf(2, 4, 4, 5, 7, 7).map { Num.of(it) })
        repeats.modes shouldBe listOf(Num.of(4), Num.of(7))
        repeats.modeCount shouldBe 2
        Quartiles.of(listOf(Num.of(3), Num.of(9))).let { Triple(it.q1, it.median, it.q3) shouldBe Triple(Num.of(3), Num.of(6), Num.of(9)) }
        shouldThrow<ArithFault.Domain> { Quartiles.of(listOf(Num.of(1))) }
    }

    // ---- curve fitting -----------------------------------------------------

    "a transformed fit with a data point exactly at the mean is computed, not refused (review regression)" {
        fun points(vararg p: Pair<Int, Int>) = p.map { (x, y) -> Num.of(x) as Num to Num.of(y) as Num }
        val exponential = CurveFit.exponential(points(1 to 2, 2 to 4, 3 to 8), mc) // y = 2^x
        exponential.a.d() shouldBe (1.0 plusOrMinus 1e-12)
        exponential.b.d() shouldBe (0.6931471805599453 plusOrMinus 1e-12)
        exponential.r2!!.d() shouldBe (1.0 plusOrMinus 1e-12)
        CurveFit.power(points(1 to 3, 2 to 12, 4 to 48), mc).b.d() shouldBe (2.0 plusOrMinus 1e-12)
        CurveFit.logarithmic(points(1 to 0, 2 to 1, 4 to 2), mc).b.d() shouldBe (1.4426950408889634 plusOrMinus 1e-12)
        CurveFit.exponential(points(1 to 5, 2 to 5, 3 to 5), mc).r2 shouldBe null
        shouldThrow<ArithFault.Domain> { CurveFit.exponential(points(2 to 5, 2 to 6, 2 to 7), mc) }
    }

    "a quadratic through y = x² + x + 1 is recovered exactly" {
        val points = listOf(0 to 1, 1 to 3, 2 to 7, 3 to 13, 4 to 21).map { (x, y) -> Num.of(x) as Num to Num.of(y) as Num }
        val fit = CurveFit.quadratic(points, Settings())
        fit.a shouldBe Num.ONE
        fit.b shouldBe Num.ONE
        fit.c shouldBe Num.ONE
        fit.r2 shouldBe Num.ONE
    }

    "a quadratic fit needs three different X values" {
        val two = listOf(Num.of(0) as Num to Num.of(1) as Num, Num.of(1) as Num to Num.of(2) as Num)
        shouldThrow<ArithFault.Domain> { CurveFit.quadratic(two, Settings()) }
        val repeated = two + (Num.of(1) as Num to Num.of(5) as Num)
        shouldThrow<ArithFault.Domain> { CurveFit.quadratic(repeated, Settings()) }
    }

    "exponential, power and logarithmic fits recover their generating curves" {
        fun points(vararg p: Pair<Int, Int>) = p.map { (x, y) -> Num.of(x) as Num to Num.of(y) as Num }
        val exponential = CurveFit.exponential(points(0 to 2, 1 to 6, 2 to 18, 3 to 54), mc) // y = 2·3^x
        exponential.a.d() shouldBe (2.0 plusOrMinus 1e-12)
        exponential.b.d() shouldBe (1.0986122886681098 plusOrMinus 1e-12)
        val power = CurveFit.power(points(1 to 3, 2 to 12, 3 to 27, 4 to 48), mc) // y = 3x²
        power.a.d() shouldBe (3.0 plusOrMinus 1e-12)
        power.b.d() shouldBe (2.0 plusOrMinus 1e-12)
        val logarithmic = CurveFit.logarithmic(points(1 to 0, 2 to 1, 4 to 2, 8 to 3), mc) // y = log₂ x
        logarithmic.a.d() shouldBe (0.0 plusOrMinus 1e-12)
        logarithmic.b.d() shouldBe (1.4426950408889634 plusOrMinus 1e-12)
        shouldThrow<ArithFault.Domain> { CurveFit.exponential(points(0 to 2, 1 to -6, 2 to 18), mc) }
    }
})
