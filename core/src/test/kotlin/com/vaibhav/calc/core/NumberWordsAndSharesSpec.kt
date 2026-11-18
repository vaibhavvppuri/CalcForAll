package com.vaibhav.calc.core

import com.vaibhav.calc.core.everyday.Finance
import com.vaibhav.calc.core.everyday.NumberWords
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.plus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/** CalcForAll 1.3 extras: numbers in words, an amount shared in a ratio, and inflation. */
class NumberWordsAndSharesSpec : StringSpec({
    fun big(text: String) = BigInteger(text)
    fun Num.d(): Double = toBigDecimal(MathContext(25)).toDouble()

    /**
     * Reads the words back into a number — written independently of [NumberWords], as the
     * way a person reads them: small numbers add up, "hundred" multiplies what stands before
     * it, a scale word closes a group, and "crore" multiplies everything read so far.
     */
    fun read(words: String): BigInteger {
        val small = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
            "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
        val tens = listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
        val scales = mapOf("thousand" to 3, "lakh" to 5, "million" to 6, "billion" to 9, "trillion" to 12, "quadrillion" to 15, "quintillion" to 18)
        var negative = false
        var high = BigInteger.ZERO
        var total = BigInteger.ZERO
        var group = 0
        for (token in words.replace("-", " ").split(" ")) when {
            token == "minus" -> negative = true
            token == "and" -> {}
            token in small -> group += small.indexOf(token)
            token in tens -> group += (tens.indexOf(token) + 2) * 10
            token == "hundred" -> group *= 100
            token == "crore" -> {
                high = high.add(total).add(BigInteger.valueOf(group.toLong())).multiply(BigInteger.TEN.pow(7))
                total = BigInteger.ZERO
                group = 0
            }
            token in scales -> {
                total = total.add(BigInteger.valueOf(group.toLong()).multiply(BigInteger.TEN.pow(scales.getValue(token))))
                group = 0
            }
            else -> throw AssertionError("unexpected word '$token' in '$words'")
        }
        val value = high.add(total).add(BigInteger.valueOf(group.toLong()))
        return if (negative) value.negate() else value
    }

    "the Indian system reads lakh and crore, with the joining 'and' of British and Indian usage" {
        mapOf(
            "0" to "zero", "7" to "seven", "13" to "thirteen", "21" to "twenty-one", "40" to "forty", "99" to "ninety-nine",
            "100" to "one hundred", "105" to "one hundred and five", "110" to "one hundred and ten",
            "999" to "nine hundred and ninety-nine", "1000" to "one thousand", "1005" to "one thousand and five",
            "1234" to "one thousand two hundred and thirty-four", "100000" to "one lakh", "100005" to "one lakh and five",
            "100500" to "one lakh five hundred",
            "1234567" to "twelve lakh thirty-four thousand five hundred and sixty-seven", "10000000" to "one crore",
            "10000005" to "one crore and five",
            "123456789" to "twelve crore thirty-four lakh fifty-six thousand seven hundred and eighty-nine",
            "1000000000000" to "one lakh crore", "-45" to "minus forty-five",
        ).forEach { (number, words) -> withClue(number) { NumberWords.indian(big(number)) shouldBe words } }
    }

    "the international system reads thousands, millions and billions" {
        mapOf(
            "0" to "zero", "100000" to "one hundred thousand", "1000000" to "one million", "1000005" to "one million and five",
            "1234567" to "one million two hundred and thirty-four thousand five hundred and sixty-seven",
            "1234567890" to "one billion two hundred and thirty-four million five hundred and sixty-seven thousand eight hundred and ninety",
            "1000000000000000000" to "one quintillion", "-1001" to "minus one thousand and one",
        ).forEach { (number, words) -> withClue(number) { NumberWords.international(big(number)) shouldBe words } }
    }

    "words read back to the number they came from, in both systems (property)" {
        checkAll(400, Arb.list(Arb.int(0..9), 1..21)) { digits ->
            val n = BigInteger(digits.joinToString(""))
            withClue("$n: '${NumberWords.indian(n)}' / '${NumberWords.international(n)}'") {
                read(NumberWords.indian(n)) shouldBe n
                read(NumberWords.international(n)) shouldBe n
                read(NumberWords.indian(n.negate())) shouldBe n.negate()
            }
        }
    }

    "cheque wording, and the limits" {
        NumberWords.rupees(big("123456"), 50) shouldBe "Rupees one lakh twenty-three thousand four hundred and fifty-six and paise fifty only"
        NumberWords.rupees(big("5000"), 0) shouldBe "Rupees five thousand only"
        NumberWords.rupees(BigInteger.ZERO, 5) shouldBe "Rupees zero and paise five only"
        shouldThrow<ArithFault.Domain> { NumberWords.rupees(big("-1"), 0) }
        shouldThrow<ArithFault.Domain> { NumberWords.rupees(big("1"), 100) }
        shouldThrow<ArithFault.Domain> { NumberWords.indian(BigInteger.TEN.pow(21)) }
        shouldThrow<ArithFault.Domain> { NumberWords.international(BigInteger.TEN.pow(21).negate()) }
        read(NumberWords.indian(NumberWords.MAX)) shouldBe NumberWords.MAX
        read(NumberWords.international(NumberWords.MAX)) shouldBe NumberWords.MAX
    }

    "an amount shared in a ratio: exact parts that add back, and the ratio in lowest terms" {
        fun whole(vararg n: Int) = n.map { BigInteger.valueOf(it.toLong()) }
        val shares = Finance.shareByRatio(Num.of(1200), listOf(Num.of(2), Num.of(3), Num.of(5)))
        shares.parts shouldBe listOf(Num.of(240), Num.of(360), Num.of(600))
        shares.simplest shouldBe whole(2, 3, 5)
        val thirds = Finance.shareByRatio(Num.of(1000), listOf(Num.of(1), Num.of(2)))
        thirds.parts shouldBe listOf(Num.of(1000, 3), Num.of(2000, 3))
        (thirds.parts[0] + thirds.parts[1]) shouldBe Num.of(1000)
        Finance.shareByRatio(Num.of(80), listOf(Num.of(3, 2), Num.of(5, 2))).simplest shouldBe whole(3, 5)
        Finance.shareByRatio(Num.of(80), listOf(Num.of(4), Num.of(6))).simplest shouldBe whole(2, 3)
        Finance.shareByRatio(Num.ZERO, listOf(Num.of(1), Num.of(1))).parts shouldBe listOf(Num.ZERO, Num.ZERO)
        shouldThrow<ArithFault.Domain> { Finance.shareByRatio(Num.of(10), listOf(Num.of(1))) }
        shouldThrow<ArithFault.Domain> { Finance.shareByRatio(Num.of(10), listOf(Num.of(1), Num.ZERO)) }
        shouldThrow<ArithFault.Domain> { Finance.shareByRatio(Num.of(10), listOf(Num.of(1), Num.of(-2))) }
        shouldThrow<ArithFault.Domain> { Finance.shareByRatio(Num.of(-10), listOf(Num.of(1), Num.of(2))) }
        shouldThrow<ArithFault.Domain> { Finance.shareByRatio(Num.of(10), List(13) { Num.of(1) }) }
    }

    "inflation: the future price and the buying power mirror each other" {
        val mc = MathContext(40, RoundingMode.HALF_EVEN)
        val ten = Finance.inflation(Num.of(1000), Num.of(6), Num.of(10), mc)
        ten.futurePrice.d() shouldBe (1790.8476965428546 plusOrMinus 1e-9) // 1000 × 1.06^10
        ten.buyingPower.d() shouldBe (558.3947769151178 plusOrMinus 1e-9)  // 1000 ÷ 1.06^10
        (ten.futurePrice.d() * ten.buyingPower.d()) shouldBe (1_000_000.0 plusOrMinus 1e-6)
        Finance.inflation(Num.of(1000), Num.ZERO, Num.of(10), mc).futurePrice.d() shouldBe 1000.0
        Finance.inflation(Num.of(1000), Num.of(6), Num.ZERO, mc).buyingPower.d() shouldBe 1000.0
        shouldThrow<ArithFault.Domain> { Finance.inflation(Num.of(1000), Num.of(-2), Num.of(10), mc) }
        shouldThrow<ArithFault.Domain> { Finance.inflation(Num.of(-1), Num.of(6), Num.of(10), mc) }
    }
})
