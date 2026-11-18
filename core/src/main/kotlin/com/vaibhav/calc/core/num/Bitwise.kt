package com.vaibhav.calc.core.num

import java.math.BigInteger

/**
 * Bitwise logic on non-negative whole numbers (CalcForAll 1.2). Restricting the
 * operands to non-negative values keeps every result unambiguous: there is no hidden
 * word size or sign extension except where [not] asks for one explicitly.
 */
object Bitwise {
    const val MAX_BITS = 4_096
    val WORD_SIZES: List<Int> = listOf(8, 16, 32, 64)

    private fun check(vararg values: BigInteger) {
        if (values.any { it.signum() < 0 }) throw ArithFault.Domain("use whole numbers that are zero or greater", "bitwise")
        if (values.any { it.bitLength() > MAX_BITS }) throw ArithFault.Overflow("a value is wider than $MAX_BITS bits")
    }

    fun and(a: BigInteger, b: BigInteger): BigInteger = check(a, b).let { a.and(b) }

    fun or(a: BigInteger, b: BigInteger): BigInteger = check(a, b).let { a.or(b) }

    fun xor(a: BigInteger, b: BigInteger): BigInteger = check(a, b).let { a.xor(b) }

    /** Positive [places] shift left (multiply by 2ⁿ); negative shift right (drop low bits). */
    fun shift(a: BigInteger, places: Int): BigInteger {
        check(a)
        if (places !in -MAX_BITS..MAX_BITS) throw ArithFault.Domain("shift by at most $MAX_BITS places", "bitwise")
        val out = if (places >= 0) a.shiftLeft(places) else a.shiftRight(-places)
        if (out.bitLength() > MAX_BITS) throw ArithFault.Overflow("the shifted value is wider than $MAX_BITS bits")
        return out
    }

    /** Every bit flipped inside an unsigned word of [bits] bits. */
    fun not(a: BigInteger, bits: Int): BigInteger {
        check(a)
        if (bits !in WORD_SIZES) throw ArithFault.Domain("the word size must be one of ${WORD_SIZES.joinToString(", ")} bits", "bitwise")
        if (a.bitLength() > bits) throw ArithFault.Domain("the value does not fit in $bits bits", "bitwise")
        return BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE).xor(a)
    }
}
