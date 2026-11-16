package com.vaibhav.calc.core.num

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** Shared allocation/work budgets; exceeding one is an ordinary typed arithmetic fault. */
object NumericLimits {
    const val MAX_INPUT_CHARACTERS = 65_536
    const val MAX_LITERAL_DIGITS = 10_000
    const val MAX_LITERAL_SCALE = 20_000
    const val MAX_INTEGER_BITS = 131_072
    // A rational addition/comparison may need two bounded operands before cancellation.
    internal const val MAX_SCRATCH_BITS = 2 * MAX_INTEGER_BITS + 1
    const val MAX_COMPUTE_PRECISION = 1_024
    /** Session/API arithmetic shares the number tower's 50-digit fallback. */
    const val MAX_SESSION_PRECISION = 50
    internal const val MAX_WORKING_PRECISION = MAX_COMPUTE_PRECISION + 128
    const val MAX_DECIMAL_EXPONENT = 32_768
    const val MAX_FACTORIAL_ARGUMENT = 10_000
    const val MAX_COMBINATORIC_TERMS = 10_000
    const val MAX_POWER_EXPONENT = 1_000_000
    const val MAX_TRANSCENDENTAL_EXPONENT = 10_000
    const val MAX_EXP_ARGUMENT = 10_000
    const val MAX_TRIG_ARGUMENT = 1_000_000
    internal const val MAX_TRIAL_DIVISOR = 4_096

    internal fun bits(n: BigInteger): Int = n.abs().bitLength()

    internal fun integer(n: BigInteger, limit: Int = MAX_INTEGER_BITS): BigInteger {
        if (bits(n) > limit) throw ArithFault.Overflow("integer exceeds the $limit-bit budget")
        return n
    }

    /** Bound the allocation before multiplication; check the one-bit border afterward. */
    internal fun multiply(a: BigInteger, b: BigInteger, limit: Int = MAX_INTEGER_BITS): BigInteger {
        if (a.signum() == 0 || b.signum() == 0) return BigInteger.ZERO
        if (bits(a).toLong() + bits(b) - 1 > limit) {
            throw ArithFault.Overflow("integer product exceeds the $limit-bit budget")
        }
        return integer(a.multiply(b), limit)
    }

    internal fun add(a: BigInteger, b: BigInteger, limit: Int = MAX_INTEGER_BITS): BigInteger {
        integer(a, limit)
        integer(b, limit)
        // Inputs are bounded; the temporary sum can grow by at most one bit.
        return integer(a.add(b), limit)
    }

    internal fun precision(mc: MathContext) {
        if (mc.precision !in 1..MAX_WORKING_PRECISION) {
            throw ArithFault.Overflow("working precision must be in 1..$MAX_WORKING_PRECISION digits")
        }
    }

    internal fun guard(mc: MathContext): MathContext {
        precision(mc)
        val guarded = MathContext(mc.precision + 8, mc.roundingMode)
        precision(guarded)
        return guarded
    }

    internal fun adjustedExponent(value: BigDecimal): Long =
        value.precision().toLong() - value.scale().toLong() - 1L

    internal fun decimal(value: BigDecimal): BigDecimal {
        integer(value.unscaledValue())
        if (value.scale().toLong() !in -65_536L..65_536L ||
            (value.signum() != 0 && adjustedExponent(value) !in -MAX_DECIMAL_EXPONENT.toLong()..MAX_DECIMAL_EXPONENT.toLong())) {
            throw ArithFault.Overflow("decimal magnitude exceeds the supported budget")
        }
        return value
    }

    internal fun decimalProduct(a: BigDecimal, b: BigDecimal) {
        if (a.signum() == 0 || b.signum() == 0) return
        val exponent = adjustedExponent(a) + adjustedExponent(b)
        if (exponent !in -MAX_DECIMAL_EXPONENT.toLong() - 1L..MAX_DECIMAL_EXPONENT.toLong()) {
            throw ArithFault.Overflow("decimal product exceeds the magnitude budget")
        }
    }

    internal fun literal(value: BigDecimal) {
        if (value.precision() > MAX_LITERAL_DIGITS ||
            value.scale().toLong() !in -MAX_LITERAL_SCALE.toLong()..MAX_LITERAL_SCALE.toLong()) {
            throw ArithFault.Overflow("number literal exceeds $MAX_LITERAL_DIGITS digits or scale $MAX_LITERAL_SCALE")
        }
    }

    internal fun literalText(text: String) {
        // Bound BigDecimal's parser allocation, including leading zeroes and exponent text.
        if (text.length > MAX_LITERAL_DIGITS + 16 || text.count { it.isDigit() } > MAX_LITERAL_DIGITS) {
            throw ArithFault.Overflow("number literal exceeds $MAX_LITERAL_DIGITS digits")
        }
    }

    internal fun transcendental(value: BigDecimal): BigDecimal {
        decimal(value)
        if (value.signum() != 0 &&
            adjustedExponent(value) !in -MAX_TRANSCENDENTAL_EXPONENT.toLong()..MAX_TRANSCENDENTAL_EXPONENT.toLong()) {
            throw ArithFault.Overflow("transcendental input magnitude exceeds the supported budget")
        }
        return value
    }

    internal fun exponential(value: BigDecimal): BigDecimal {
        transcendental(value)
        if (value.abs() > BigDecimal.valueOf(MAX_EXP_ARGUMENT.toLong())) {
            throw ArithFault.Overflow("exponential argument magnitude must be at most $MAX_EXP_ARGUMENT")
        }
        return value
    }

    internal fun trigonometric(value: BigDecimal): BigDecimal {
        transcendental(value)
        if (value.abs() > BigDecimal.valueOf(MAX_TRIG_ARGUMENT.toLong())) {
            throw ArithFault.Overflow("non-symbolic trigonometric argument magnitude must be at most $MAX_TRIG_ARGUMENT radians")
        }
        return value
    }

    internal fun powerExponent(exponent: BigInteger) {
        if (exponent.abs() > BigInteger.valueOf(MAX_POWER_EXPONENT.toLong())) {
            throw ArithFault.Overflow("exponent magnitude must be at most $MAX_POWER_EXPONENT")
        }
    }
}
