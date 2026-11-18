package com.vaibhav.calc.core.solve

import com.vaibhav.calc.core.eval.ComplexValue
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.eval.Value
import com.vaibhav.calc.core.eval.toValue
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Complex
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.Rational
import com.vaibhav.calc.core.num.abs
import com.vaibhav.calc.core.num.bounds
import com.vaibhav.calc.core.num.DecimalBounds
import com.vaibhav.calc.core.num.establishedSign
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.num.unaryMinus
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Polynomial equations of degree 3..[MAX_DEGREE] in one unknown (CalcForAll 1.2).
 *
 * Exact first, in this order:
 *  1. rational roots by the rational-root theorem (exact, deflated out);
 *  2. a remaining linear/quadratic factor by the existing exact solvers (surds stay surds);
 *  3. anything left: real roots are COUNTED and isolated with Sturm's theorem in exact
 *     rational arithmetic, then refined by bisection; non-real roots are computed
 *     numerically (Durand–Kerner, Newton-polished on the original factor) and accepted
 *     only if every one passes a residual check and they pair up as conjugates.
 *
 * The list is therefore never silently incomplete: every distinct root is returned, or
 * the solve fails with a typed [ArithFault]. Repeated roots are listed once. Numeric
 * roots are [Dec] values, so the display marks them approximate.
 */
object PolynomialRoots {
    const val MAX_DEGREE = 8
    private const val MAX_COEFFICIENT_BITS = 512
    private const val MAX_RATIONAL_CANDIDATES = 20_000L
    private val MAX_TRIAL: BigInteger = BigInteger.valueOf(1_000_000_000_000L)
    private const val MAX_SPLIT_DEPTH = 600
    private const val MAX_BISECTIONS = 1_500
    private const val MAX_DK_ITERATIONS = 500

    data class Result(val roots: List<Value>, val steps: List<String>)

    private const val MAX_NORMALIZED_BITS = 2_048
    private const val MAX_MILLIS = 10_000L

    /**
     * A wall-clock budget, like the graph root finder's. The size caps below bound each
     * arithmetic step; this bounds how many of them one equation may take, because a
     * history entry is re-solved whenever the tape recomputes and the local API solves on
     * request. Ten seconds accommodates slower Android arithmetic without relaxing the
     * size, iteration or root-acceptance checks. Exceeding it is a typed refusal, never
     * a partial answer; an individual bounded arithmetic operation is not preempted.
     */
    private class Budget {
        private val deadline = System.nanoTime() + MAX_MILLIS * 1_000_000
        fun check() {
            if (System.nanoTime() > deadline) {
                throw ArithFault.Unsupported("this equation needs more work than the polynomial solver's time budget (${MAX_MILLIS / 1_000} seconds) allows; simplify its coefficients")
            }
        }
    }

    private val active = ThreadLocal<Budget>()
    private fun tick() { active.get()?.check() }

    /** Solve `p(x) = 0`. [p] holds coefficients lowest power first. */
    fun solve(p: Poly, settings: Settings, quadraticFactorDoubt: BigDecimal? = null): Result {
        active.set(Budget())
        try {
            return solveWithinBudget(p, settings, quadraticFactorDoubt)
        } finally {
            active.remove()
        }
    }

    /**
     * The legacy [quadraticFactorDoubt] parameter cannot establish a discriminant bound.
     * Irrational coefficients must cancel exactly or remain in a supported low-degree factor.
     */
    private fun solveWithinBudget(p: Poly, settings: Settings, quadraticFactorDoubt: BigDecimal?): Result {
        val n = p.degree
        if (n !in 3..MAX_DEGREE) {
            throw ArithFault.Unsupported("degree $n equations are not supported (polynomial equations up to degree $MAX_DEGREE)")
        }
        // Preserve exact powers of x times a low-degree factor before any coefficient conversion.
        val zeroPower = p.coeffs.indexOfFirst { !it.isZero }
        if (zeroPower > 0 && n - zeroPower <= 2) {
            val factor = p.coeffs.drop(zeroPower)
            val reduced = when (factor.size - 1) {
                0 -> { factor[0].establishedSign("the polynomial's nonzero factor"); emptyList() }
                1 -> listOf(RealValue(Solvers.linear(factor[1], factor[0], settings).root))
                else -> Solvers.quadratic(factor[2], factor[1], factor[0], settings).roots
            }
            val allRoots = listOf(RealValue(Num.ZERO)) + reduced
            val realRoots = allRoots.filterIsInstance<RealValue>().sortedWith { a, b -> a.num.compareTo(b.num) }
            return Result(realRoots + allRoots.filter { it !is RealValue },
                listOf("An exact power of the unknown gives root 0; the remaining factor is solved separately"))
        }
        fun knownRational(value: Num): Rational? = when (value) {
            is Rational -> value
            is Dec -> if (value.absoluteError?.signum() == 0) rationalOf(value.value) else null
            else -> null
        }
        // An exact common factor (for example pi*x^3-pi) may cancel without approximating it.
        val direct = p.coeffs.map(::knownRational)
        val normalized = if (direct.all { it != null }) direct else {
            p.coeffs.last().establishedSign("the polynomial's leading coefficient")
            p.coeffs.map { knownRational(it / p.coeffs.last()) }
        }
        if (normalized.any { it == null }) throw ArithFault.Unsupported(
            "higher-degree solving requires exact rational coefficients after exact common-factor cancellation; " +
                "uncertain or irrational coefficients cannot be rounded into a different polynomial")
        val exact = true
        val digits = maxOf(settings.displayPrecision + 8, 30)
        val wmc = MathContext(digits + 30, RoundingMode.HALF_EVEN)
        var q: List<Rational> = normalized.map { it!! }
        if (q.any { it.num.bitLength() > MAX_COEFFICIENT_BITS || it.den.bitLength() > MAX_COEFFICIENT_BITS }) {
            throw ArithFault.Unsupported("the polynomial's coefficients are too large to solve")
        }
        // Clear the denominators once (a positive scale leaves the roots alone). The bound is
        // on the INTEGER polynomial: eight modest fractions with unrelated denominators can
        // have a common denominator thousands of bits wide, and every later step pays for it.
        val common = q.fold(BigInteger.ONE) { acc, c -> acc.multiply(c.den).divide(acc.gcd(c.den)) }
        if (q.maxOf { it.num.bitLength() } + common.bitLength() > MAX_NORMALIZED_BITS) {
            throw ArithFault.Unsupported("the polynomial's coefficients are too large to solve")
        }
        q = q.map { rr(it * Num.of(common)) }
        val steps = mutableListOf("Polynomial equation of degree $n" + if (exact) "" else " (irrational coefficients approximated)")
        val real = mutableListOf<Num>()
        val nonReal = mutableListOf<Value>()

        if (q[0].isZero) {
            real += Num.ZERO
            q = q.dropWhile { it.isZero }
        }
        if (exact) {
            val found = rationalRoots(q)
            if (found == null) {
                steps += "Rational-root test skipped: the end coefficients have too many divisors"
            } else {
                for (r in found) {
                    real += r
                    while (q.size > 1 && evalAt(q, r).isZero) q = deflate(q, r)
                }
            }
            // "No rational roots" is a claim only the test itself can make — not a skipped one.
            if (real.isNotEmpty()) {
                steps += "Exact rational roots: " + real.sortedWith { a, b -> a.compareTo(b) }.joinToString(", ") { Formatter.exact(it, settings) }
            } else if (found != null) {
                steps += "No rational roots"
            }
        }

        // Work on the squarefree part: same distinct roots, every one simple.
        if (q.size - 1 >= 3) {
            val s = squarefree(q)
            if (s.size < q.size) steps += "Repeated roots are listed once"
            q = s
        }
        fun coefficient(c: Rational): Num = if (exact) c else Dec(c.toBigDecimal(wmc))
        when (val m = q.size - 1) {
            0 -> Unit
            1 -> real += coefficient(rr(-q[0] / q[1]))
            2 -> {
                // Monic first. Clearing denominators can leave a common factor in what remains
                // (a denominator of 10007 leaves 10007·(x² − 2)), and the quadratic formula would
                // then return the same root as an unsimplified surd.
                val lead = q[2]
                val res = Solvers.quadratic(
                    Num.ONE, coefficient(rr(q[1] / lead)), coefficient(rr(q[0] / lead)), settings,
                    if (exact) null else quadraticFactorDoubt,
                )
                res.roots.forEach { if (it is RealValue) real += it.num else nonReal += it }
                steps += "Remaining quadratic factor solved with the quadratic formula"
            }
            else -> {
                val chain = sturmChain(q)
                val bound = rr(cauchyBound(q) + Num.ONE)
                val lo = rr(-bound)
                val intervals = mutableListOf<Pair<Rational, Rational>>()
                isolate(q, chain, lo, bound, variations(chain, lo), variations(chain, bound), intervals, 0)
                val numeric = intervals.map { (a, b) -> refine(q, a, b, digits) }
                real += numeric
                steps += "${numeric.size} further real root${if (numeric.size == 1) "" else "s"} counted with Sturm's theorem and refined by bisection"
                val remaining = m - numeric.size
                if (remaining > 0) {
                    nonReal += complexRoots(q, numeric, remaining, digits, wmc, approximate = !exact)
                    steps += "$remaining non-real roots computed numerically and checked against the equation"
                }
            }
        }
        var ordered = real.distinct().sortedWith { a, b -> a.compareTo(b) }
        var others: List<Value> = nonReal
        return Result(ordered.map { RealValue(it) } + others, steps)
    }

    // ---- exact rational polynomial helpers (coefficients lowest power first) ----------

    private fun rr(n: Num): Rational = n as Rational

    private fun rationalOf(bd: BigDecimal): Rational {
        val scale = bd.scale()
        return if (scale >= 0) Num.of(bd.unscaledValue(), BigInteger.TEN.pow(scale))
        else Num.of(bd.unscaledValue().multiply(BigInteger.TEN.pow(-scale)))
    }

    private fun trim(p: List<Rational>): List<Rational> = p.dropLastWhile { it.isZero }

    private fun evalAt(p: List<Rational>, x: Rational): Rational {
        tick()
        var acc: Num = Num.ZERO
        for (i in p.indices.reversed()) acc = acc * x + p[i]
        return rr(acc)
    }

    private fun derivative(p: List<Rational>): List<Rational> =
        trim((1 until p.size).map { rr(p[it] * Num.of(it)) })

    /** Exact division by `(x − root)`; the caller has checked the remainder is zero. */
    private fun deflate(p: List<Rational>, root: Rational): List<Rational> {
        val n = p.size - 1
        val out = MutableList(n) { Num.ZERO }
        var carry = p[n]
        for (i in n - 1 downTo 0) {
            out[i] = carry
            carry = rr(p[i] + carry * root)
        }
        return out
    }

    private fun divRem(a: List<Rational>, b: List<Rational>): Pair<List<Rational>, List<Rational>> {
        val db = b.size - 1
        val lead = b[db]
        var r = a.toMutableList()
        val quotient = MutableList(maxOf(a.size - db, 0)) { Num.ZERO }
        while (r.isNotEmpty() && r.size - 1 >= db) {
            tick()
            val shift = r.size - 1 - db
            val f = rr(r.last() / lead)
            quotient[shift] = f
            for (i in 0..db) r[i + shift] = rr(r[i + shift] - f * b[i])
            r = trim(r).toMutableList()
        }
        return quotient to r
    }

    /** Positive scaling only, so signs — all Sturm's theorem reads — are preserved. */
    private fun normalize(p: List<Rational>): List<Rational> {
        if (p.isEmpty()) return p
        val lead = p.last()
        val scale = if (lead.num.signum() < 0) rr(-lead) else lead
        return p.map { rr(it / scale) }
    }

    private fun gcd(a: List<Rational>, b: List<Rational>): List<Rational> {
        var x = normalize(a)
        var y = normalize(b)
        while (y.isNotEmpty()) {
            val r = normalize(divRem(x, y).second)
            x = y
            y = r
        }
        return x
    }

    private fun squarefree(p: List<Rational>): List<Rational> {
        val g = gcd(p, derivative(p))
        return if (g.size <= 1) p else trim(divRem(p, g).first)
    }

    private fun sturmChain(p: List<Rational>): List<List<Rational>> {
        val chain = mutableListOf(normalize(p), normalize(derivative(p)))
        while (chain.last().size > 1) {
            val r = divRem(chain[chain.size - 2], chain.last()).second
            if (r.isEmpty()) break
            chain += normalize(r.map { rr(-it) })
        }
        return chain
    }

    private fun variations(chain: List<List<Rational>>, x: Rational): Int {
        var changes = 0
        var previous = 0
        for (poly in chain) {
            val sign = evalAt(poly, x).num.signum()
            if (sign == 0) continue
            if (previous != 0 && sign != previous) changes++
            previous = sign
        }
        return changes
    }

    private fun cauchyBound(p: List<Rational>): Rational {
        val lead = p.last()
        var worst: Rational = Num.ZERO
        for (i in 0 until p.size - 1) {
            val ratio = rr(p[i] / lead)
            val magnitude = if (ratio.num.signum() < 0) rr(-ratio) else ratio
            if (magnitude > worst) worst = magnitude
        }
        return rr(worst + Num.ONE)
    }

    private val SPLITS: List<Rational> = listOf(
        Num.of(1, 2), Num.of(1, 3), Num.of(2, 3), Num.of(2, 5), Num.of(3, 5),
        Num.of(3, 7), Num.of(4, 7), Num.of(4, 9), Num.of(5, 9), Num.of(5, 11),
    )

    /** An interior point that is not itself a root (at most `degree` of the candidates can be). */
    private fun splitPoint(p: List<Rational>, a: Rational, b: Rational): Rational {
        val width = rr(b - a)
        for (t in SPLITS) {
            val m = rr(a + width * t)
            if (!evalAt(p, m).isZero) return m
        }
        throw ArithFault.Unsupported("could not separate the roots of this polynomial")
    }

    private fun isolate(
        p: List<Rational>, chain: List<List<Rational>>, a: Rational, b: Rational,
        va: Int, vb: Int, out: MutableList<Pair<Rational, Rational>>, depth: Int,
    ) {
        val count = va - vb
        if (count <= 0) return
        if (count == 1) {
            out += a to b
            return
        }
        if (depth > MAX_SPLIT_DEPTH) throw ArithFault.Unsupported("could not separate very close roots of this polynomial")
        val m = splitPoint(p, a, b)
        val vm = variations(chain, m)
        isolate(p, chain, a, m, va, vm, out, depth + 1)
        isolate(p, chain, m, b, vm, vb, out, depth + 1)
    }

    /** Bisection on a bracket that holds exactly one simple root. */
    private fun refine(p: List<Rational>, a: Rational, b: Rational, digits: Int): Num {
        var lo = a
        var hi = b
        val signLo = evalAt(p, lo).num.signum()
        val eps = Num.of(BigInteger.ONE, BigInteger.TEN.pow(digits))
        repeat(MAX_BISECTIONS) {
            val mid = rr((lo + hi) / Num.TWO)
            val sign = evalAt(p, mid).num.signum()
            if (sign == 0) return mid
            if (sign == signLo) lo = mid else hi = mid
            // The tolerance is relative to the root itself, so a root near 1e-25 keeps its
            // digits. Zero is never a root here (it is divided out first), so the bracket
            // stops straddling zero, and from then on its nearer end bounds the root's size.
            if (lo.num.signum() != hi.num.signum()) return@repeat
            val a0 = if (lo.num.signum() < 0) rr(-lo) else lo
            val b0 = if (hi.num.signum() < 0) rr(-hi) else hi
            val nearer = if (a0 <= b0) a0 else b0
            if (rr(hi - lo) <= rr(eps * nearer)) {
                val middle = rr((lo + hi) / Num.TWO)
                // A whole-number root that the bounded rational-root test never tried
                // (it skips very large end coefficients) is still exact: check it.
                val whole = Num.of(middle.toBigDecimal(MathContext(digits + 5)).setScale(0, RoundingMode.HALF_EVEN).toBigInteger())
                if (whole >= lo && whole <= hi && evalAt(p, whole).isZero) return whole
                val value = middle.toBigDecimal(MathContext(digits, RoundingMode.HALF_EVEN))
                return DecimalBounds(lo.bounds(digits + 16)!!.lower, hi.bounds(digits + 16)!!.upper).around(value)
            }
        }
        throw ArithFault.Unsupported("root refinement did not converge")
    }

    // ---- non-real roots: BigDecimal complex arithmetic, verified before acceptance ----

    private class C(val re: BigDecimal, val im: BigDecimal)

    private fun sub(a: C, b: C) = C(a.re.subtract(b.re), a.im.subtract(b.im))

    private fun mul(a: C, b: C, mc: MathContext) = C(
        a.re.multiply(b.re, mc).subtract(a.im.multiply(b.im, mc), mc),
        a.re.multiply(b.im, mc).add(a.im.multiply(b.re, mc), mc),
    )

    private fun div(a: C, b: C, mc: MathContext): C {
        val den = b.re.multiply(b.re, mc).add(b.im.multiply(b.im, mc), mc)
        if (den.signum() == 0) throw ArithFault.Unsupported("could not determine the complex roots of this polynomial")
        return C(
            a.re.multiply(b.re, mc).add(a.im.multiply(b.im, mc), mc).divide(den, mc),
            a.im.multiply(b.re, mc).subtract(a.re.multiply(b.im, mc), mc).divide(den, mc),
        )
    }

    private fun size(c: C): BigDecimal = c.re.abs().add(c.im.abs())

    /** Horner over coefficients lowest power first. */
    private fun horner(coeffs: List<BigDecimal>, z: C, mc: MathContext): C {
        var acc = C(coeffs.last(), BigDecimal.ZERO)
        for (i in coeffs.size - 2 downTo 0) {
            val t = mul(acc, z, mc)
            acc = C(t.re.add(coeffs[i], mc), t.im)
        }
        return acc
    }

    private fun complexRoots(
        s: List<Rational>, realRoots: List<Num>, count: Int, digits: Int, mc: MathContext, approximate: Boolean,
    ): List<Value> {
        val fail = { throw ArithFault.Unsupported("could not determine the complex roots of this polynomial reliably") }
        if (count % 2 != 0) fail()
        val full = s.map { it.toBigDecimal(mc) }
        // Deflate the real roots numerically; what remains has only the non-real roots.
        var reduced = full
        for (root in realRoots) {
            val r = root.toBigDecimal(mc)
            val out = MutableList(reduced.size - 1) { BigDecimal.ZERO }
            var carry = reduced.last()
            for (i in reduced.size - 2 downTo 0) {
                out[i] = carry
                carry = reduced[i].add(carry.multiply(r, mc), mc)
            }
            reduced = out
        }
        val lead = reduced.last()
        val monic = reduced.map { it.divide(lead, mc) }
        val radius = monic.dropLast(1).fold(BigDecimal.ONE) { acc, c -> acc.max(c.abs()) }.add(BigDecimal.ONE)
        val seed = C(BigDecimal("0.4"), BigDecimal("0.9"))
        var power = C(radius, BigDecimal.ZERO)
        val z = MutableList(count) { power.also { power = mul(power, seed, mc) } }
        val tolerance = BigDecimal.ONE.movePointLeft(digits + 5)
        var converged = false
        for (iteration in 0 until MAX_DK_ITERATIONS) {
            tick()
            var worst = BigDecimal.ZERO
            for (i in 0 until count) {
                var den = C(BigDecimal.ONE, BigDecimal.ZERO)
                for (j in 0 until count) if (j != i) den = mul(den, sub(z[i], z[j]), mc)
                val delta = div(horner(monic, z[i], mc), den, mc)
                z[i] = sub(z[i], delta)
                // Relative to the root, so a root of size 1e-40 is not "converged" at once.
                val scale = size(z[i])
                worst = worst.max(if (scale.signum() == 0) size(delta) else size(delta).divide(scale, mc))
            }
            if (worst <= tolerance) {
                converged = true
                break
            }
        }
        if (!converged) fail()
        // Newton-polish on the ORIGINAL squarefree factor to shed the deflation error.
        val slope = (1 until full.size).map { full[it].multiply(BigDecimal(it), mc) }
        for (i in 0 until count) repeat(3) {
            z[i] = sub(z[i], div(horner(full, z[i], mc), horner(slope, z[i], mc), mc))
        }
        val tiny = BigDecimal.ONE.movePointLeft(digits - 4)
        val upper = ArrayList<C>()
        for (root in z) {
            // Residual relative to the size of the terms that were summed.
            var scale = BigDecimal.ZERO
            var term = BigDecimal.ONE
            val magnitude = size(root)
            for (c in full) {
                scale = scale.add(c.abs().multiply(term, mc), mc)
                term = term.multiply(magnitude, mc)
            }
            if (size(horner(full, root, mc)) > scale.multiply(tiny, mc)) fail()
            // Sturm counted these as non-real, so for exact coefficients a vanishing imaginary
            // part is a contradiction. With approximated coefficients it is a repeated root
            // that split, which the caller collapses. Thresholds are relative to the root.
            if (!approximate && root.im.abs() <= magnitude.multiply(tiny, mc)) fail()
            if (root.im.signum() > 0) upper += root
        }
        if (upper.size != count / 2) fail()
        val out = MathContext(digits, RoundingMode.HALF_EVEN)
        return upper.sortedWith(compareBy<C> { it.re }.thenBy { it.im }).flatMap { root ->
            val re: Num = Dec(root.re.round(out))
            val im = root.im.round(out)
            listOf(Complex(re, Dec(im)).toValue(), Complex(re, Dec(im.negate())).toValue())
        }
    }

    /** Distinct rational roots, or null when the divisor search would be too large. */
    private fun rationalRoots(q: List<Rational>): List<Rational>? {
        val lcm = q.fold(BigInteger.ONE) { acc, c -> acc.multiply(c.den).divide(acc.gcd(c.den)) }
        val constant = q.first().num.multiply(lcm.divide(q.first().den)).abs()
        val leading = q.last().num.multiply(lcm.divide(q.last().den)).abs()
        if (constant > MAX_TRIAL || leading > MAX_TRIAL) return null
        val ps = divisors(constant.toLong())
        val ds = divisors(leading.toLong())
        if (ps.size.toLong() * ds.size * 2 > MAX_RATIONAL_CANDIDATES) return null
        val found = LinkedHashSet<Rational>()
        for (p in ps) for (d in ds) {
            if (BigInteger.valueOf(p).gcd(BigInteger.valueOf(d)) != BigInteger.ONE) continue
            for (candidate in listOf(Num.of(p, d), Num.of(-p, d))) {
                if (evalAt(q, candidate).isZero) found += candidate
            }
        }
        return found.sortedWith { a, b -> a.compareTo(b) }
    }

    private fun divisors(n: Long): List<Long> {
        val out = ArrayList<Long>()
        var i = 1L
        while (i * i <= n) {
            if (n % i == 0L) {
                out += i
                if (i != n / i) out += n / i
            }
            i++
        }
        return out
    }
}
