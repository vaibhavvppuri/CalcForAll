package com.vaibhav.calc.core.stats

import ch.obermuhlner.math.big.BigDecimalMath
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.Rational
import com.vaibhav.calc.core.num.abs
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.signum
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.num.requireKnownPoint
import com.vaibhav.calc.core.solve.Solvers
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Probability distributions (CalcForAll 1.2).
 *
 * Binomial probabilities are EXACT rationals when `p` is a fraction of workable size —
 * `P(X = 3)` for ten fair coin flips is `15/128`, not `0.1171875…`. Everything else is a
 * [Dec] approximation at the caller's precision.
 *
 * The approximate sums are accumulated in plain [BigDecimal], not in the number tower.
 * The tower refuses an approximate addition that would absorb one operand — the right
 * rule for a calculator expression, but a tail probability is exactly a sum of terms
 * far smaller than the running total. For the same reason every distribution reports
 * its upper tail directly instead of leaving the caller to form `1 − P(X ≤ k)`.
 */
object Distributions {
    const val MAX_TRIALS = 2_000
    const val MAX_EVENTS = 5_000
    private const val MAX_TAIL_TERMS = 2_000_000
    /**
     * Beyond this many bits in bⁿ the binomial answer is computed as a decimal instead of a
     * fraction. Kept under the number tower's own integer limit, and still well under a
     * second of whole-number arithmetic at n = 2000.
     */
    private const val MAX_EXACT_BITS = 120_000L
    private val MAX_LAMBDA = Num.of(10_000)
    private val MAX_Z = Num.of(40)

    /** `exactly` = P(X = k); `atMost` = P(X ≤ k); `above` = P(X > k). */
    data class Tail(val exactly: Num, val atMost: Num, val above: Num)

    fun binomial(n: Int, k: Int, p: Num, mc: MathContext): Tail {
        p.requireKnownPoint("binomial probability")
        if (n !in 1..MAX_TRIALS) throw ArithFault.Domain("trials must be from 1 to $MAX_TRIALS", "binomial")
        if (k !in 0..n) throw ArithFault.Domain("successes must be from 0 to the number of trials", "binomial")
        if (p.signum() < 0 || p > Num.ONE) throw ArithFault.Domain("a probability must be from 0 to 1", "binomial")
        if (p.isZero) return Tail(if (k == 0) Num.ONE else Num.ZERO, Num.ONE, Num.ZERO)
        if (p.compareTo(Num.ONE) == 0) {
            return if (k == n) Tail(Num.ONE, Num.ONE, Num.ZERO) else Tail(Num.ZERO, Num.ZERO, Num.ONE)
        }
        // Exact when p = a/b is rational and the common denominator bⁿ is of a workable size.
        // Every term is C(n,i)·aⁱ·(b−a)ⁿ⁻ⁱ over that ONE denominator, so the sum is whole-number
        // arithmetic reduced once at the end. Summing the terms as separate fractions instead
        // costs a gcd over ever-larger numbers per term — minutes for n = 2000, p = 0.123456789.
        if (p is Rational && p.den.bitLength().toLong() * n <= MAX_EXACT_BITS) {
            val a = p.num
            val c = p.den.subtract(a)
            var coefficient = BigInteger.ONE // C(n, i)
            var weight = c.pow(n) // aⁱ·cⁿ⁻ⁱ at i = 0
            var lower = BigInteger.ZERO
            var exactly = BigInteger.ZERO
            for (i in 0..k) {
                val term = coefficient.multiply(weight)
                lower = lower.add(term)
                if (i == k) exactly = term
                if (i < n) {
                    coefficient = coefficient.multiply(BigInteger.valueOf((n - i).toLong())).divide(BigInteger.valueOf((i + 1).toLong()))
                    weight = weight.multiply(a).divide(c)
                }
            }
            val denominator = p.den.pow(n)
            return Tail(Num.of(exactly, denominator), Num.of(lower, denominator), Num.of(denominator.subtract(lower), denominator))
        }
        val wp = MathContext(mc.precision + 20, RoundingMode.HALF_EVEN)
        val pd = p.toBigDecimal(wp)
        // For a rational p, round q = 1 − p from its exact value: rounding p first would turn
        // a p just below 1 into exactly 1 and report every outcome but "all succeed" as 0.
        val qd = if (p is Rational) (Num.ONE - p).toBigDecimal(wp) else BigDecimal.ONE.subtract(pd, wp)
        var coefficient = BigDecimal.ONE
        var lower = BigDecimal.ZERO
        var upper = BigDecimal.ZERO
        var exactly = BigDecimal.ZERO
        for (i in 0..n) {
            val term = coefficient.multiply(pd.pow(i, wp), wp).multiply(qd.pow(n - i, wp), wp)
            if (i <= k) lower = lower.add(term, wp) else upper = upper.add(term, wp)
            if (i == k) exactly = term
            coefficient = coefficient.multiply(BigDecimal(n - i), wp).divide(BigDecimal(i + 1), wp)
        }
        return Tail(Dec(exactly.round(mc)), Dec(lower.round(mc)), Dec(upper.round(mc)))
    }

    fun poisson(k: Int, lambda: Num, mc: MathContext): Tail {
        lambda.requireKnownPoint("Poisson probability")
        if (lambda.signum() <= 0 || lambda > MAX_LAMBDA) throw ArithFault.Domain("the average must be above 0 and at most 10,000", "poisson")
        if (k !in 0..MAX_EVENTS) throw ArithFault.Domain("events must be from 0 to $MAX_EVENTS", "poisson")
        val wp = MathContext(mc.precision + 20, RoundingMode.HALF_EVEN)
        val l = lambda.toBigDecimal(wp)
        var term = BigDecimalMath.exp(l.negate(), wp) // P(X = 0)
        var lower = term
        for (i in 1..k) {
            term = term.multiply(l, wp).divide(BigDecimal(i), wp)
            lower = lower.add(term, wp)
        }
        val exactly = term
        // The upper tail, summed term by term. Past i > λ the terms only shrink, so the sum
        // is complete once a term no longer registers against it.
        var upper = BigDecimal.ZERO
        var i = k
        while (true) {
            i++
            term = term.multiply(l, wp).divide(BigDecimal(i), wp)
            upper = upper.add(term, wp)
            if (BigDecimal(i) > l && term <= upper.movePointLeft(wp.precision)) break
            if (i - k > MAX_TAIL_TERMS) throw ArithFault.Unsupported("the Poisson tail did not converge")
        }
        return Tail(Dec(exactly.round(mc)), Dec(lower.round(mc)), Dec(upper.round(mc)))
    }

    /**
     * Φ(z), the standard normal cumulative probability, from the Maclaurin series of
     * erf. The series alternates and the lower tail subtracts two near-equal numbers,
     * so the working precision grows with z² to keep the requested digits honest.
     * For an upper tail call this with `−z`: `1 − Φ(z)` would cancel.
     */
    fun normalCdf(z: Num, mc: MathContext): Num {
        z.requireKnownPoint("normal probability")
        if (z.abs() > MAX_Z) throw ArithFault.Domain("the z-score must be between −40 and 40", "normal")
        if (z.isZero) return Num.HALF
        val rough = z.toBigDecimal(MathContext(20))
        val guard = rough.multiply(rough).multiply(BigDecimal("0.45")).setScale(0, RoundingMode.CEILING).intValueExact()
        val wp = MathContext(mc.precision + 20 + guard, RoundingMode.HALF_EVEN)
        val x = z.toBigDecimal(wp).divide(BigDecimalMath.sqrt(BigDecimal(2), wp), wp)
        val x2 = x.multiply(x, wp)
        val stop = BigDecimal.ONE.movePointLeft(wp.precision)
        var power = x // (−1)^n · x^(2n+1) / n!
        var sum = x
        var n = 1
        while (true) {
            power = power.multiply(x2, wp).divide(BigDecimal(n), wp).negate()
            val term = power.divide(BigDecimal(2 * n + 1), wp)
            sum = sum.add(term, wp)
            if (term.abs() < stop && BigDecimal(n) > x2) break
            if (++n > 200_000) throw ArithFault.Unsupported("the normal probability did not converge")
        }
        val erf = sum.multiply(BigDecimal(2), wp).divide(BigDecimalMath.sqrt(BigDecimalMath.pi(wp), wp), wp)
        return Dec(BigDecimal.ONE.add(erf, wp).divide(BigDecimal(2), wp).round(mc))
    }
}

/**
 * Least-squares curve fits beyond the straight line (CalcForAll 1.2).
 *
 * The quadratic fit solves its normal equations exactly. The exponential, power and
 * logarithmic fits are straight-line fits on transformed data — the standard
 * calculator method — so their r² describes the TRANSFORMED fit, and the caller
 * should say so. The transformed fits are computed in plain [BigDecimal]: logarithms
 * are approximate, and a data point that sits at the mean would otherwise contribute
 * a deviation too small for the number tower's absorption guard to accept.
 */
object CurveFit {
    data class Quadratic(val a: Num, val b: Num, val c: Num, val r2: Num?)
    data class TwoParameter(val a: Num, val b: Num, val r2: Num?)

    /** `y = a·x² + b·x + c`. */
    fun quadratic(points: List<Pair<Num, Num>>, settings: Settings): Quadratic {
        points.forEach { (x, y) -> x.requireKnownPoint("curve fitting"); y.requireKnownPoint("curve fitting") }
        if (points.size < 3) throw ArithFault.Domain("a quadratic fit needs at least 3 points", "fit")
        val n: Num = Num.of(points.size)
        var s1: Num = Num.ZERO
        var s2: Num = Num.ZERO
        var s3: Num = Num.ZERO
        var s4: Num = Num.ZERO
        var t0: Num = Num.ZERO
        var t1: Num = Num.ZERO
        var t2: Num = Num.ZERO
        for ((x, y) in points) {
            val xx = x * x
            s1 += x; s2 += xx; s3 += xx * x; s4 += xx * xx
            t0 += y; t1 += x * y; t2 += xx * y
        }
        val (a, b, c) = try {
            Solvers.system3x3(s4, s3, s2, t2, s3, s2, s1, t1, s2, s1, n, t0, settings).solution
        } catch (e: ArithFault.Domain) {
            throw ArithFault.Domain("a quadratic fit needs at least 3 different X values", "fit")
        }
        val mean = t0 / n
        var residual: Num = Num.ZERO
        var total: Num = Num.ZERO
        for ((x, y) in points) {
            val miss = y - (a * x * x + b * x + c)
            residual += miss * miss
            total += (y - mean) * (y - mean)
        }
        return Quadratic(a, b, c, if (total.isZero) null else Num.ONE - residual / total)
    }

    /** `y = a·e^(b·x)`, fitted as `ln y = ln a + b·x`. */
    fun exponential(points: List<Pair<Num, Num>>, mc: MathContext): TwoParameter {
        val wp = working(mc)
        val line = line(points, wp, BigDecimal.ZERO, { it.toBigDecimal(wp) }, { ln(positive(it, "Y"), wp) })
        return TwoParameter(Dec(BigDecimalMath.exp(line.intercept, wp).round(mc)), Dec(line.slope.round(mc)), line.r2?.let { Dec(it.round(mc)) })
    }

    /** `y = a·x^b`, fitted as `ln y = ln a + b·ln x`. */
    fun power(points: List<Pair<Num, Num>>, mc: MathContext): TwoParameter {
        val wp = working(mc)
        val line = line(points, wp, BigDecimal.ONE, { ln(positive(it, "X"), wp) }, { ln(positive(it, "Y"), wp) })
        return TwoParameter(Dec(BigDecimalMath.exp(line.intercept, wp).round(mc)), Dec(line.slope.round(mc)), line.r2?.let { Dec(it.round(mc)) })
    }

    /** `y = a + b·ln x`. */
    fun logarithmic(points: List<Pair<Num, Num>>, mc: MathContext): TwoParameter {
        val wp = working(mc)
        val line = line(points, wp, BigDecimal.ONE, { ln(positive(it, "X"), wp) }, { it.toBigDecimal(wp) })
        return TwoParameter(Dec(line.intercept.round(mc)), Dec(line.slope.round(mc)), line.r2?.let { Dec(it.round(mc)) })
    }

    private class Line(val slope: BigDecimal, val intercept: BigDecimal, val r2: BigDecimal?)

    /** Digits carried beyond the requested precision; the X spread must be resolved to at least this many. */
    private const val GUARD_DIGITS = 20

    private fun working(mc: MathContext) = MathContext(mc.precision + GUARD_DIGITS, RoundingMode.HALF_EVEN)

    private fun ln(value: Num, wp: MathContext): BigDecimal = BigDecimalMath.log(value.toBigDecimal(wp), wp)

    /**
     * Ordinary least squares on transformed coordinates. Degeneracy is judged on the ORIGINAL
     * values, exactly, and then on the transformed ones against rounding noise. A logarithm of
     * X carries an absolute error of about one unit in the last working digit however small the
     * logarithm is, so a log-transformed X passes a [noiseFloor] of 1; plain X values, which
     * are only ever rounded relative to their own size, pass zero.
     */
    private fun line(
        points: List<Pair<Num, Num>>, wp: MathContext, noiseFloor: BigDecimal,
        transformX: (Num) -> BigDecimal, transformY: (Num) -> BigDecimal,
    ): Line {
        points.forEach { (x, y) -> x.requireKnownPoint("curve fitting"); y.requireKnownPoint("curve fitting") }
        if (points.size < 2) throw ArithFault.Domain("a fit needs at least 2 points", "fit")
        if (points.all { it.first.compareTo(points[0].first) == 0 }) {
            throw ArithFault.Domain("a fit needs at least 2 different X values", "fit")
        }
        val constantY = points.all { it.second.compareTo(points[0].second) == 0 }
        val xs = points.map { transformX(it.first) }
        val ys = points.map { transformY(it.second) }
        val n = BigDecimal(points.size)
        val meanX = xs.fold(BigDecimal.ZERO) { acc, v -> acc.add(v, wp) }.divide(n, wp)
        val meanY = ys.fold(BigDecimal.ZERO) { acc, v -> acc.add(v, wp) }.divide(n, wp)
        var sxx = BigDecimal.ZERO
        var sxy = BigDecimal.ZERO
        var syy = BigDecimal.ZERO
        for (i in xs.indices) {
            val dx = xs[i].subtract(meanX, wp)
            val dy = ys[i].subtract(meanY, wp)
            sxx = sxx.add(dx.multiply(dx, wp), wp)
            sxy = sxy.add(dx.multiply(dy, wp), wp)
            syy = syy.add(dy.multiply(dy, wp), wp)
        }
        // Distinct X values can coincide once transformed and rounded (1 and 1 + 10⁻⁷⁰ have the same
        // logarithm here), or sit so close that their spread is mostly rounding. Then the mean,
        // rounded as well, differs from every one of them in its last digit, sxx is a tiny number
        // that is not zero, and the slope would be read off noise. The spread has to stand clear of
        // the guard digits; otherwise this is a refusal with a reason, not a fabricated slope.
        val low = xs.reduce { a, b -> a.min(b) }
        val high = xs.reduce { a, b -> a.max(b) }
        val noise = low.abs().max(high.abs()).max(noiseFloor).multiply(BigDecimal.ONE.movePointLeft(GUARD_DIGITS), wp)
        if (sxx.signum() == 0 || high.subtract(low, wp) <= noise) {
            throw ArithFault.Domain("the X values are too close together to fit a curve through", "fit")
        }
        val slope = sxy.divide(sxx, wp)
        val intercept = meanY.subtract(slope.multiply(meanX, wp), wp)
        val r2 = if (constantY || syy.signum() == 0) null
        else sxy.multiply(sxy, wp).divide(sxx.multiply(syy, wp), wp).min(BigDecimal.ONE)
        return Line(slope, intercept, r2)
    }

    private fun positive(value: Num, what: String): Num {
        if (value.signum() <= 0) throw ArithFault.Domain("this fit needs every $what value above zero", "fit")
        return value
    }
}
