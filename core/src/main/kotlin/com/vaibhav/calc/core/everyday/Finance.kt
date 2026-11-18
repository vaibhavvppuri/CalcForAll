package com.vaibhav.calc.core.everyday

import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.NumMath
import com.vaibhav.calc.core.num.NumericLimits
import com.vaibhav.calc.core.num.Rational
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.signum
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.num.toBigIntegerExactOrNull
import java.math.BigInteger
import java.math.BigDecimal
import java.math.MathContext

/**
 * Everyday money arithmetic (CalcForAll 1.2). Currency-neutral: amounts are plain
 * numbers, and nothing here fetches a rate or knows a tax table — every rate is the
 * user's input, so the app stays offline.
 *
 * Percent/tax/discount results are exact rationals. Growth factors `(1+i)^n` are
 * computed at the caller's [MathContext] and returned as [Dec]: an exact rational
 * with a thousand-digit denominator is not a more honest instalment, only a less
 * readable one. Rounding to money is the presentation layer's job.
 */
object Finance {
    const val MAX_PERIODS = 12_000
    const val MAX_RATIO_PARTS = 12
    private val HUNDRED = Num.of(100)
    private val TWELVE = Num.of(12)

    data class Loan(val payment: Num, val totalPaid: Num, val totalInterest: Num)
    data class Growth(val amount: Num, val interest: Num)
    data class Investment(val futureValue: Num, val invested: Num, val gain: Num)
    data class Tax(val net: Num, val tax: Num, val gross: Num)
    data class Discount(val saving: Num, val finalPrice: Num)
    data class Change(val difference: Num, val percent: Num)
    data class Split(val tip: Num, val total: Num, val perPerson: Num)

    /**
     * Equated monthly instalment on a reducing balance:
     * `EMI = P·i·(1+i)^n / ((1+i)^n − 1)` with `i = annual% / 1200`.
     */
    fun loan(principal: Num, annualRatePercent: Num, months: Int, mc: MathContext): Loan {
        positive(principal, "loan amount")
        nonNegative(annualRatePercent, "interest rate")
        periods(months)
        val n = Num.of(months)
        if (annualRatePercent.isZero) return Loan(principal / n, principal, Num.ZERO)
        val i = annualRatePercent / HUNDRED / TWELVE
        val terms = GrowthTerms(i, months, mc)
        val p = principal.toBigDecimal(terms.mc)
        val payment = terms.multiply(p, terms.factor).divide(terms.annuity, terms.mc)
        val interest = terms.multiply(p, terms.interestNumerator).divide(terms.annuity, terms.mc)
        return Loan(Dec(payment.round(mc)), Dec(p.add(interest, terms.mc).round(mc)), Dec(interest.round(mc)))
    }

    /** `A = P·(1 + r/k)^(k·t)`; a fractional number of periods uses the real power. */
    fun compound(principal: Num, annualRatePercent: Num, years: Num, compoundsPerYear: Int, mc: MathContext): Growth {
        positive(principal, "principal")
        nonNegative(annualRatePercent, "interest rate")
        nonNegative(years, "time")
        if (compoundsPerYear !in 1..365) throw ArithFault.Domain("compounding must be from 1 to 365 times a year", "finance")
        val count = years * Num.of(compoundsPerYear)
        if (count > Num.of(MAX_PERIODS)) throw ArithFault.Domain("use at most $MAX_PERIODS compounding periods", "finance")
        if (count.isZero || annualRatePercent.isZero) return Growth(principal, Num.ZERO)
        val rate = annualRatePercent / HUNDRED / Num.of(compoundsPerYear)
        val whole = count.toBigIntegerExactOrNull()
        if (whole != null) {
            val terms = GrowthTerms(rate, whole.toInt(), mc)
            val p = principal.toBigDecimal(terms.mc)
            val interest = terms.multiply(p, terms.gain)
            return Growth(Dec(p.add(interest, terms.mc).round(mc)), Dec(interest.round(mc)))
        }
        val base = Dec((Num.ONE + rate).toBigDecimal(mc))
        if (base.value.compareTo(BigDecimal.ONE) == 0)
            throw ArithFault.Unsupported("the fractional-period growth is too small to resolve at this precision")
        val factor = NumMath.pow(base, Dec(count.toBigDecimal(mc)), mc)
        val amount = principal * factor
        return Growth(amount, amount - principal)
    }

    /** `I = P·r·t / 100` — exact. */
    fun simple(principal: Num, annualRatePercent: Num, years: Num): Growth {
        positive(principal, "principal")
        nonNegative(annualRatePercent, "interest rate")
        nonNegative(years, "time")
        val interest = principal * annualRatePercent * years / HUNDRED
        return Growth(principal + interest, interest)
    }

    /**
     * A fixed amount invested at the START of every month (the SIP convention):
     * `FV = P · ((1+i)^n − 1)/i · (1+i)` with `i = annual% / 1200`.
     */
    fun monthlyInvestment(monthly: Num, annualReturnPercent: Num, months: Int, mc: MathContext): Investment {
        positive(monthly, "monthly amount")
        nonNegative(annualReturnPercent, "return rate")
        periods(months)
        val invested = monthly * Num.of(months)
        if (annualReturnPercent.isZero) return Investment(invested, invested, Num.ZERO)
        val i = annualReturnPercent / HUNDRED / TWELVE
        val terms = GrowthTerms(i, months, mc)
        val gain = terms.multiply(monthly.toBigDecimal(terms.mc), terms.startOfPeriodExcess)
        val future = invested.toBigDecimal(terms.mc).add(gain, terms.mc)
        return Investment(Dec(future.round(mc)), invested, Dec(gain.round(mc)))
    }

    const val MAX_SCHEDULE_MONTHS = 600

    /** One year of a repayment schedule: what that year's instalments paid, and what is still owed. */
    data class YearRow(val year: Int, val interest: Num, val principal: Num, val balance: Num)

    /**
     * Year-by-year split of the instalments into interest and principal. Each month's
     * interest is charged on the balance still owed; the final instalment clears the
     * remainder exactly, so the principal column always adds up to the loan.
     */
    fun schedule(principal: Num, annualRatePercent: Num, months: Int, mc: MathContext): List<YearRow> {
        if (months > MAX_SCHEDULE_MONTHS) throw ArithFault.Domain("a schedule covers at most $MAX_SCHEDULE_MONTHS months", "finance")
        val payment = loan(principal, annualRatePercent, months, mc).payment
        val i = annualRatePercent / HUNDRED / TWELVE
        val rows = ArrayList<YearRow>()
        var balance: Num = principal
        var yearInterest: Num = Num.ZERO
        var yearPrincipal: Num = Num.ZERO
        for (month in 1..months) {
            val interest = balance * i
            val repaid = if (month == months) balance else payment - interest
            balance = if (month == months) Num.ZERO else balance - repaid
            yearInterest += interest
            yearPrincipal += repaid
            if (month % 12 == 0 || month == months) {
                rows += YearRow((month + 11) / 12, yearInterest, yearPrincipal, balance)
                yearInterest = Num.ZERO
                yearPrincipal = Num.ZERO
            }
        }
        return rows
    }

    data class Affordable(val principal: Num, val totalPaid: Num, val totalInterest: Num)
    data class Goal(val monthly: Num, val invested: Num, val gain: Num)
    data class Offers(val unitPriceA: Num, val unitPriceB: Num, val savingPercent: Num)
    data class Trip(val fuel: Num, val cost: Num)

    /** The loan a monthly [payment] can service — the EMI formula solved for the principal. */
    fun affordableLoan(payment: Num, annualRatePercent: Num, months: Int, mc: MathContext): Affordable {
        positive(payment, "monthly payment")
        nonNegative(annualRatePercent, "interest rate")
        periods(months)
        val total = payment * Num.of(months)
        if (annualRatePercent.isZero) return Affordable(total, total, Num.ZERO)
        val i = annualRatePercent / HUNDRED / TWELVE
        val terms = GrowthTerms(i, months, mc)
        val p = payment.toBigDecimal(terms.mc)
        val principal = terms.multiply(p, terms.annuity).divide(terms.factor, terms.mc)
        val interest = terms.multiply(p, terms.interestNumerator).divide(terms.factor, terms.mc)
        return Affordable(Dec(principal.round(mc)), total, Dec(interest.round(mc)))
    }

    /** The start-of-month amount that grows to [target] — the SIP formula solved for the instalment. */
    fun savingsGoal(target: Num, annualReturnPercent: Num, months: Int, mc: MathContext): Goal {
        positive(target, "target amount")
        nonNegative(annualReturnPercent, "return rate")
        periods(months)
        val n = Num.of(months)
        if (annualReturnPercent.isZero) return Goal(target / n, target, Num.ZERO)
        val i = annualReturnPercent / HUNDRED / TWELVE
        val terms = GrowthTerms(i, months, mc)
        val t = target.toBigDecimal(terms.mc)
        val divisor = BigDecimal(months).add(terms.startOfPeriodExcess, terms.mc)
        val monthly = t.divide(divisor, terms.mc)
        val gain = terms.multiply(t, terms.startOfPeriodExcess).divide(divisor, terms.mc)
        return Goal(Dec(monthly.round(mc)), Dec(terms.multiply(monthly, BigDecimal(months)).round(mc)), Dec(gain.round(mc)))
    }

    /**
     * Keep g=(1+i)^n−1 and e=sum(k=0..n−1)((1+i)^k−1) directly. Forming
     * (1+i)^n and then subtracting one erases small nonzero rates. Concatenating
     * blocks gives g=a+b+ab and e=eA+eB+a*(lengthB+eB), with no cancellation
     * for nonnegative rates. Binary composition takes O(log n) bounded steps.
     */
    private class GrowthTerms(rate: Num, count: Int, requested: MathContext) {
        val mc = MathContext(minOf(requested.precision + 16, NumericLimits.MAX_COMPUTE_PRECISION), requested.roundingMode)
        val gain: BigDecimal
        val excess: BigDecimal
        private val rateDecimal = rate.toBigDecimal(mc)
        val annuity get() = BigDecimal(count).add(excess, mc)
        val factor get() = BigDecimal.ONE.add(gain, mc)
        val interestNumerator get() = multiply(BigDecimal(count), gain).subtract(excess, mc)
        val startOfPeriodExcess get() = excess.add(multiply(rateDecimal, annuity), mc)
        private val count = count

        fun multiply(a: BigDecimal, b: BigDecimal): BigDecimal {
            NumericLimits.decimalProduct(a, b)
            return NumericLimits.decimal(a.multiply(b, mc))
        }

        init {
            NumericLimits.precision(requested)
            var remaining = count
            var length = 1
            var blockGain = rateDecimal
            var blockExcess = BigDecimal.ZERO
            var accumulatedGain = BigDecimal.ZERO
            var accumulatedExcess = BigDecimal.ZERO
            while (remaining > 0) {
                if (remaining and 1 != 0) {
                    accumulatedExcess = NumericLimits.decimal(accumulatedExcess.add(blockExcess, mc)
                        .add(multiply(accumulatedGain, BigDecimal(length).add(blockExcess, mc)), mc))
                    accumulatedGain = NumericLimits.decimal(accumulatedGain.add(blockGain, mc)
                        .add(multiply(accumulatedGain, blockGain), mc))
                }
                remaining = remaining ushr 1
                if (remaining > 0) {
                    blockExcess = NumericLimits.decimal(blockExcess.add(blockExcess, mc)
                        .add(multiply(blockGain, BigDecimal(length).add(blockExcess, mc)), mc))
                    blockGain = NumericLimits.decimal(blockGain.add(blockGain, mc).add(multiply(blockGain, blockGain), mc))
                    length *= 2
                }
            }
            gain = accumulatedGain
            excess = accumulatedExcess
        }
    }

    /** `percent`% of `amount`. Exact. */
    fun portion(percent: Num, amount: Num): Num = amount * percent / HUNDRED

    /**
     * Price per unit of two offers. [Offers.savingPercent] is how much less the cheaper
     * one costs per unit than the dearer one; zero when they are equal. Exact.
     */
    fun compareUnitPrices(priceA: Num, quantityA: Num, priceB: Num, quantityB: Num): Offers {
        nonNegative(priceA, "price")
        nonNegative(priceB, "price")
        positive(quantityA, "quantity")
        positive(quantityB, "quantity")
        val a = priceA / quantityA
        val b = priceB / quantityB
        val dearer = if (a >= b) a else b
        val cheaper = if (a >= b) b else a
        return Offers(a, b, if (dearer.isZero) Num.ZERO else (dearer - cheaper) / dearer * HUNDRED)
    }

    /** Fuel and cost for a trip, from the distance covered per unit of fuel. Exact. */
    fun fuelCost(distance: Num, distancePerUnitFuel: Num, pricePerUnitFuel: Num): Trip {
        nonNegative(distance, "distance")
        positive(distancePerUnitFuel, "mileage")
        nonNegative(pricePerUnitFuel, "fuel price")
        val fuel = distance / distancePerUnitFuel
        return Trip(fuel, fuel * pricePerUnitFuel)
    }

    /** Rule of three: `a` is to `b` as `c` is to the result. Exact. */
    fun proportion(a: Num, b: Num, c: Num): Num {
        if (a.isZero) throw ArithFault.Domain("the first value of a proportion cannot be zero", "finance")
        return b * c / a
    }

    /** Tax added on top of a net price (GST/VAT exclusive → inclusive). Exact. */
    fun addTax(net: Num, ratePercent: Num): Tax {
        nonNegative(net, "amount")
        nonNegative(ratePercent, "tax rate")
        val tax = net * ratePercent / HUNDRED
        return Tax(net, tax, net + tax)
    }

    /** Tax taken out of a price that already includes it (inclusive → exclusive). Exact. */
    fun removeTax(gross: Num, ratePercent: Num): Tax {
        nonNegative(gross, "amount")
        nonNegative(ratePercent, "tax rate")
        val net = gross * HUNDRED / (HUNDRED + ratePercent)
        return Tax(net, gross - net, gross)
    }

    fun discount(price: Num, percentOff: Num): Discount {
        nonNegative(price, "price")
        if (percentOff.signum() < 0 || percentOff > HUNDRED) throw ArithFault.Domain("discount must be from 0 to 100 percent", "finance")
        val saving = price * percentOff / HUNDRED
        return Discount(saving, price - saving)
    }

    fun percentChange(old: Num, new: Num): Change {
        if (old.isZero) throw ArithFault.Domain("percent change from zero is undefined", "finance")
        val difference = new - old
        val magnitude = if (old.signum() < 0) Num.ZERO - old else old
        return Change(difference, difference / magnitude * HUNDRED)
    }

    /** `part` as a percentage of `whole`. Exact. */
    fun percentOf(part: Num, whole: Num): Num {
        if (whole.isZero) throw ArithFault.Domain("a percentage of zero is undefined", "finance")
        return part / whole * HUNDRED
    }

    fun split(bill: Num, tipPercent: Num, people: Int): Split {
        nonNegative(bill, "bill")
        nonNegative(tipPercent, "tip")
        if (people !in 1..10_000) throw ArithFault.Domain("split between 1 and 10,000 people", "finance")
        val tip = bill * tipPercent / HUNDRED
        val total = bill + tip
        return Split(tip, total, total / Num.of(people))
    }

    data class Shares(val parts: List<Num>, val simplest: List<BigInteger>)

    /**
     * [amount] divided in the ratio [ratio]: part i is `amount·rᵢ / Σr`, exactly, so the parts
     * add back to the amount. [Shares.simplest] is the ratio in lowest whole-number terms
     * (1.5 : 2.5 is 3 : 5); it is empty when a part of the ratio is not a rational number.
     */
    fun shareByRatio(amount: Num, ratio: List<Num>): Shares {
        if (ratio.size !in 2..MAX_RATIO_PARTS) throw ArithFault.Domain("a ratio needs 2 to $MAX_RATIO_PARTS parts", "finance")
        nonNegative(amount, "amount")
        ratio.forEach { positive(it, "each part of a ratio") }
        val total = ratio.reduce { a, b -> a + b }
        val parts = ratio.map { amount * it / total }
        val rationals = ratio.filterIsInstance<Rational>()
        if (rationals.size != ratio.size) return Shares(parts, emptyList())
        val denominator = rationals.fold(BigInteger.ONE) { acc, r -> acc.multiply(r.den).divide(acc.gcd(r.den)) }
        val whole = rationals.map { it.num.multiply(denominator.divide(it.den)) }
        val common = whole.reduce { a, b -> a.gcd(b) }
        return Shares(parts, whole.map { it.divide(common) })
    }

    data class Inflation(val futurePrice: Num, val buyingPower: Num)

    /**
     * Steady inflation of [ratePercent] a year for [years]: what goods costing [amount] today
     * will cost then, and what [amount] of money then buys in today's prices. The rate is the
     * caller's assumption; nothing is looked up.
     */
    fun inflation(amount: Num, ratePercent: Num, years: Num, mc: MathContext): Inflation {
        nonNegative(amount, "amount")
        val factor = compound(Num.ONE, ratePercent, years, 1, mc).amount
        return Inflation(amount * factor, amount / factor)
    }

    private fun positive(n: Num, what: String) {
        knownPoint(n, what)
        if (n.signum() <= 0) throw ArithFault.Domain("$what must be greater than zero", "finance")
    }

    private fun nonNegative(n: Num, what: String) {
        knownPoint(n, what)
        if (n.signum() < 0) throw ArithFault.Domain("$what cannot be negative", "finance")
    }

    private fun knownPoint(n: Num, what: String) {
        if (n is Dec && n.absoluteError?.signum() != 0)
            throw ArithFault.Unsupported("$what needs a known value; this finance calculation does not yet propagate input uncertainty")
    }

    private fun periods(months: Int) {
        if (months !in 1..MAX_PERIODS) throw ArithFault.Domain("use 1 to $MAX_PERIODS months", "finance")
    }
}
