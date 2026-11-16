package com.vaibhav.calc.core.num

import java.math.BigInteger

/**
 * Integer base conversion for radixes 2–36 (spec 2.1). Rational/decimal values
 * have no single canonical base representation, so conversion is defined on
 * integers only.
 */
object Bases {

    fun format(value: BigInteger, radix: Int): String {
        require(radix in 2..36) { "radix must be in 2..36, was $radix" }
        NumericLimits.integer(value)
        return value.toString(radix).uppercase()
    }

    fun parse(text: String, radix: Int): BigInteger {
        require(radix in 2..36) { "radix must be in 2..36, was $radix" }
        val cleaned = text.trim()
        require(cleaned.isNotEmpty()) { "empty base-$radix literal" }
        if (cleaned.length > NumericLimits.MAX_LITERAL_DIGITS + 1) {
            throw ArithFault.Overflow("base literal exceeds ${NumericLimits.MAX_LITERAL_DIGITS} digits")
        }
        return try {
            NumericLimits.integer(BigInteger(cleaned, radix))
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("'$cleaned' is not a valid base-$radix integer")
        }
    }
}
