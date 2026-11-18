package com.vaibhav.calc.core.everyday

import com.vaibhav.calc.core.num.ArithFault
import java.math.BigInteger

/**
 * Whole numbers in English words (CalcForAll 1.3), in the Indian system — thousand, lakh,
 * crore, and crore again for larger numbers ("one lakh crore") — and in the international
 * one (thousand, million, billion …).
 *
 * The wording is British and Indian usage: "one hundred and five", "one thousand and five",
 * "twenty-one". The joining "and" stands before the last part below a hundred whenever
 * something comes before it, and nowhere else.
 */
object NumberWords {
    /** The largest magnitude put into words: one less than 10²¹, where the international names used here end. */
    val MAX: BigInteger = BigInteger.TEN.pow(21).subtract(BigInteger.ONE)

    private val SMALL = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private val INTERNATIONAL = listOf("", "thousand", "million", "billion", "trillion", "quadrillion", "quintillion")

    private val THOUSAND = BigInteger.valueOf(1_000)
    private val CRORE = BigInteger.valueOf(10_000_000)

    /** `1234567` → "twelve lakh thirty-four thousand five hundred and sixty-seven". */
    fun indian(n: BigInteger): String = signed(n) { indianWords(it) }

    /** `1234567` → "one million two hundred and thirty-four thousand five hundred and sixty-seven". */
    fun international(n: BigInteger): String = signed(n) { magnitude ->
        val parts = ArrayList<String>()
        var rest = magnitude
        val groups = ArrayList<Int>()
        while (rest.signum() > 0) {
            val split = rest.divideAndRemainder(THOUSAND)
            groups += split[1].toInt()
            rest = split[0]
        }
        for (i in groups.indices.reversed()) {
            val group = groups[i]
            if (group == 0) continue
            if (i == 0) parts += last(group, parts.isNotEmpty()) else parts += below1000(group) + " " + INTERNATIONAL[i]
        }
        parts.joinToString(" ")
    }

    /**
     * Cheque wording in the Indian system: "Rupees one lakh twenty-three thousand four hundred
     * and fifty-six and paise fifty only". [paise] is 0..99.
     */
    fun rupees(rupees: BigInteger, paise: Int): String {
        if (rupees.signum() < 0) throw ArithFault.Domain("an amount in words cannot be negative", "words")
        if (paise !in 0..99) throw ArithFault.Domain("paise must be from 0 to 99", "words")
        val whole = "Rupees " + indian(rupees)
        return if (paise == 0) "$whole only" else "$whole and paise ${below100(paise)} only"
    }

    private fun signed(n: BigInteger, words: (BigInteger) -> String): String {
        val magnitude = n.abs()
        if (magnitude > MAX) throw ArithFault.Domain("numbers are put into words up to 10^21", "words")
        if (magnitude.signum() == 0) return SMALL[0]
        return (if (n.signum() < 0) "minus " else "") + words(magnitude)
    }

    /** [n] is positive. Everything from a crore upwards is read as so many crore, recursively. */
    private fun indianWords(n: BigInteger): String {
        val split = n.divideAndRemainder(CRORE)
        val parts = ArrayList<String>()
        if (split[0].signum() > 0) parts += indianWords(split[0]) + " crore"
        val rest = split[1].toInt() // below ten million
        val lakhs = rest / 100_000
        val thousands = rest / 1_000 % 100
        val units = rest % 1_000
        if (lakhs > 0) parts += below100(lakhs) + " lakh"
        if (thousands > 0) parts += below100(thousands) + " thousand"
        if (units > 0) parts += last(units, parts.isNotEmpty())
        return parts.joinToString(" ")
    }

    /** The final group, 1..999, with the joining "and" when it is below a hundred and something precedes it. */
    private fun last(group: Int, preceded: Boolean): String =
        if (preceded && group < 100) "and " + below100(group) else below1000(group)

    private fun below1000(n: Int): String {
        val hundreds = n / 100
        val rest = n % 100
        return when {
            hundreds == 0 -> below100(rest)
            rest == 0 -> SMALL[hundreds] + " hundred"
            else -> SMALL[hundreds] + " hundred and " + below100(rest)
        }
    }

    private fun below100(n: Int): String = when {
        n < 20 -> SMALL[n]
        n % 10 == 0 -> TENS[n / 10]
        else -> TENS[n / 10] + "-" + SMALL[n % 10]
    }
}
