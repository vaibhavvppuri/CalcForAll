package com.vaibhav.calc.core.num

import java.math.BigInteger

/**
 * Whole-number tools (CalcForAll 1.2): prime factorisation, primality, divisors,
 * GCD and LCM. Factorisation is deterministic trial division, so it is bounded to
 * [MAX_FACTOR_INPUT] — a refusal beyond that, never a probabilistic "probably prime".
 */
object NumberTheory {
    val MAX_FACTOR_INPUT: BigInteger = BigInteger.TEN.pow(14)
    const val MAX_LISTED_DIVISORS = 2_000
    const val MAX_VALUES = 100

    // Not BigInteger.TWO: that constant only exists from Android 13 (API 33), and this
    // engine ships in an app whose minimum is Android 6. Lint does not scan this module.
    private val TWO: BigInteger = BigInteger.valueOf(2)

    /** Prime factors of [n] as (prime, exponent), ascending. Requires 2 ≤ n ≤ [MAX_FACTOR_INPUT]. */
    fun factorize(n: BigInteger): List<Pair<BigInteger, Int>> {
        if (n < TWO || n > MAX_FACTOR_INPUT) {
            throw ArithFault.Domain("use a whole number from 2 to 10^14", "factor")
        }
        var rest = n.toLong()
        val out = ArrayList<Pair<BigInteger, Int>>()
        var p = 2L
        while (p * p <= rest) {
            if (rest % p == 0L) {
                var exponent = 0
                while (rest % p == 0L) {
                    rest /= p
                    exponent++
                }
                out += BigInteger.valueOf(p) to exponent
            }
            p += if (p == 2L) 1 else 2
        }
        if (rest > 1) out += BigInteger.valueOf(rest) to 1
        return out
    }

    /** Deterministic within the factorisation bound. Numbers below 2 are not prime. */
    fun isPrime(n: BigInteger): Boolean {
        if (n < TWO) return false
        val factors = factorize(n)
        return factors.size == 1 && factors[0].second == 1
    }

    fun divisorCount(factors: List<Pair<BigInteger, Int>>): Long =
        factors.fold(1L) { acc, (_, exponent) -> acc * (exponent + 1) }

    /** Every positive divisor of [n], ascending; refused when there are more than [MAX_LISTED_DIVISORS]. */
    fun divisors(n: BigInteger): List<BigInteger> {
        if (n == BigInteger.ONE) return listOf(BigInteger.ONE)
        val factors = factorize(n)
        if (divisorCount(factors) > MAX_LISTED_DIVISORS) {
            throw ArithFault.Unsupported("this number has ${divisorCount(factors)} divisors; at most $MAX_LISTED_DIVISORS are listed")
        }
        var out = listOf(BigInteger.ONE)
        for ((prime, exponent) in factors) {
            val next = ArrayList<BigInteger>(out.size * (exponent + 1))
            var power = BigInteger.ONE
            repeat(exponent + 1) {
                for (d in out) next += d.multiply(power)
                power = power.multiply(prime)
            }
            out = next
        }
        return out.sorted()
    }

    fun gcd(values: List<BigInteger>): BigInteger {
        check(values)
        return values.fold(BigInteger.ZERO) { acc, v -> acc.gcd(v) }
    }

    /** Least common multiple of the magnitudes; zero if any value is zero. */
    fun lcm(values: List<BigInteger>): BigInteger {
        check(values)
        var out = BigInteger.ONE
        for (v in values) {
            if (v.signum() == 0) return BigInteger.ZERO
            val magnitude = v.abs()
            out = out.multiply(magnitude).divide(out.gcd(magnitude))
            if (out.bitLength() > NumericLimits.MAX_INTEGER_BITS) throw ArithFault.Overflow("the least common multiple is too large")
        }
        return out
    }

    private fun check(values: List<BigInteger>) {
        if (values.size !in 2..MAX_VALUES) throw ArithFault.Domain("use 2 to $MAX_VALUES whole numbers", "gcd")
        if (values.any { it.bitLength() > NumericLimits.MAX_INTEGER_BITS }) throw ArithFault.Overflow("a value is too large")
    }
}
