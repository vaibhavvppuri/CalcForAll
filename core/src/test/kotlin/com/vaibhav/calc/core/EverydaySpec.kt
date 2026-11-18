package com.vaibhav.calc.core

import com.vaibhav.calc.core.everyday.BodyMass
import com.vaibhav.calc.core.everyday.Dates
import com.vaibhav.calc.core.everyday.Durations
import com.vaibhav.calc.core.everyday.Finance
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.abs
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.times
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/** CalcForAll 1.2: everyday money and date arithmetic, against independently known answers. */
class EverydaySpec : StringSpec({
    val mc = MathContext(40, RoundingMode.HALF_EVEN)
    fun money(n: Num): String = n.toBigDecimal(mc).setScale(2, RoundingMode.HALF_UP).toPlainString()

    // ---- loans -------------------------------------------------------------

    "EMI: 1,00,000 at 12% for 12 months is 8,884.88" {
        val loan = Finance.loan(Num.of(100_000), Num.of(12), 12, mc)
        money(loan.payment) shouldBe "8884.88"
        money(loan.totalInterest) shouldBe "6618.55"
    }

    "EMI: 10,00,000 at 8.5% for 240 months is 8,678.23" {
        money(Finance.loan(Num.of(1_000_000), Num.of(17, 2), 240, mc).payment) shouldBe "8678.23"
    }

    "the EMI formula is checked by simulation: paying it every month clears the loan" {
        val principal = Num.of(1_000_000)
        val rate = Num.of(17, 2)
        val loan = Finance.loan(principal, rate, 240, mc)
        val monthly = rate / Num.of(1200)
        var balance: Num = principal
        repeat(240) { balance = balance * (Num.ONE + monthly) - loan.payment }
        (balance.abs() < Num.of(1, 1_000_000)) shouldBe true
    }

    "a zero-interest loan divides evenly and stays exact" {
        val loan = Finance.loan(Num.of(1200), Num.ZERO, 12, mc)
        loan.payment shouldBe Num.of(100)
        loan.totalInterest shouldBe Num.ZERO
    }

    // ---- growth ------------------------------------------------------------

    "compound interest: 1,000 at 10% for 2 years is 1,210" {
        val g = Finance.compound(Num.of(1000), Num.of(10), Num.of(2), 1, mc)
        money(g.amount) shouldBe "1210.00"
        money(g.interest) shouldBe "210.00"
    }

    "compound interest, quarterly: 1,00,000 at 8% for 5 years is 1,48,594.74" {
        money(Finance.compound(Num.of(100_000), Num.of(8), Num.of(5), 4, mc).amount) shouldBe "148594.74"
    }

    "a fractional number of periods uses the real power: half a year at 10%" {
        money(Finance.compound(Num.of(1000), Num.of(10), Num.of(1, 2), 1, mc).amount) shouldBe "1048.81"
    }

    "simple interest is exact" {
        val g = Finance.simple(Num.of(5000), Num.of(15, 2), Num.of(3))
        g.interest shouldBe Num.of(1125)
        g.amount shouldBe Num.of(6125)
    }

    "SIP: 5,000 a month at 12% for 10 years grows to 11,61,695.38" {
        val sip = Finance.monthlyInvestment(Num.of(5000), Num.of(12), 120, mc)
        money(sip.futureValue) shouldBe "1161695.38"
        sip.invested shouldBe Num.of(600_000)
        money(sip.gain) shouldBe "561695.38"
    }

    // ---- tax, discount, percent, split: exact rationals -------------------

    "GST added: 1,000 + 18% is 180 tax, 1,180 total" {
        val t = Finance.addTax(Num.of(1000), Num.of(18))
        t.tax shouldBe Num.of(180)
        t.gross shouldBe Num.of(1180)
    }

    "GST removed is the exact inverse, and net + tax is always the price paid" {
        Finance.removeTax(Num.of(1180), Num.of(18)).net shouldBe Num.of(1000)
        val t = Finance.removeTax(Num.of(999), Num.of(18))
        t.net shouldBe Num.of(49_950, 59)
        (t.net + t.tax) shouldBe Num.of(999)
    }

    "discount: 20% off 2,499 saves 499.80" {
        val d = Finance.discount(Num.of(2499), Num.of(20))
        d.saving shouldBe Num.of(2499, 5)
        d.finalPrice shouldBe Num.of(9996, 5)
    }

    "percent change is signed and measured against the size of the starting value" {
        Finance.percentChange(Num.of(80), Num.of(100)).percent shouldBe Num.of(25)
        Finance.percentChange(Num.of(100), Num.of(80)).percent shouldBe Num.of(-20)
        Finance.percentChange(Num.of(-50), Num.of(-25)).percent shouldBe Num.of(50)
        Finance.percentOf(Num.of(45), Num.of(60)) shouldBe Num.of(75)
    }

    "splitting a bill with a tip" {
        val s = Finance.split(Num.of(1200), Num.of(10), 4)
        s.tip shouldBe Num.of(120)
        s.total shouldBe Num.of(1320)
        s.perPerson shouldBe Num.of(330)
    }

    "impossible money inputs are typed refusals" {
        shouldThrow<ArithFault.Domain> { Finance.loan(Num.ZERO, Num.of(10), 12, mc) }
        shouldThrow<ArithFault.Domain> { Finance.loan(Num.of(1000), Num.of(10), 0, mc) }
        shouldThrow<ArithFault.Domain> { Finance.loan(Num.of(1000), Num.of(-1), 12, mc) }
        shouldThrow<ArithFault.Domain> { Finance.discount(Num.of(100), Num.of(150)) }
        shouldThrow<ArithFault.Domain> { Finance.percentChange(Num.ZERO, Num.of(5)) }
        shouldThrow<ArithFault.Domain> { Finance.split(Num.of(100), Num.of(10), 0) }
    }

    // ---- second pack: inverses, shopping, body mass, durations -------------

    "the affordable loan recovers principal from an independently held payment point" {
        val loan = Finance.loan(Num.of(500_000), Num.of(17, 2), 60, mc)
        // Independent Decimal calculation at 120 digits, held here as a chosen decimal payment.
        // This is a point-input inverse check, not promotion of the engine's unknown estimate.
        val heldPayment = Num.ofDecimal("10258.265663525624736724659873354911772014409735338")
        (loan.payment.toBigDecimal(mc).subtract(heldPayment.toBigDecimal(mc)).abs() < BigDecimal("1e-25")) shouldBe true
        shouldThrow<ArithFault.Unsupported> { Finance.affordableLoan(loan.payment, Num.of(17, 2), 60, mc) }
        val back = Finance.affordableLoan(heldPayment, Num.of(17, 2), 60, mc)
        ((back.principal - Num.of(500_000)).abs() < Num.of(1, 1_000_000_000L)) shouldBe true
        Finance.affordableLoan(Num.of(100), Num.ZERO, 12, mc).principal shouldBe Num.of(1200)
    }

    "the savings goal recovers deposits from an independently held future-value point" {
        val sip = Finance.monthlyInvestment(Num.of(5000), Num.of(12), 120, mc)
        // Independent 120-digit Decimal annuity-due calculation, held as decimal target data.
        val heldTarget = Num.ofDecimal("1161695.3817597008491778669303124621775608623187479")
        (sip.futureValue.toBigDecimal(mc).subtract(heldTarget.toBigDecimal(mc)).abs() < BigDecimal("1e-25")) shouldBe true
        shouldThrow<ArithFault.Unsupported> { Finance.savingsGoal(sip.futureValue, Num.of(12), 120, mc) }
        val goal = Finance.savingsGoal(heldTarget, Num.of(12), 120, mc)
        ((goal.monthly - Num.of(5000)).abs() < Num.of(1, 1_000_000_000L)) shouldBe true
        Finance.savingsGoal(Num.of(1200), Num.ZERO, 12, mc).monthly shouldBe Num.of(100)
    }

    "percent of, unit prices, fuel and proportion are exact" {
        Finance.portion(Num.of(15), Num.of(2400)) shouldBe Num.of(360)
        val offers = Finance.compareUnitPrices(Num.of(120), Num.of(500), Num.of(200), Num.of(1000))
        offers.unitPriceA shouldBe Num.of(6, 25)
        offers.unitPriceB shouldBe Num.of(1, 5)
        offers.savingPercent shouldBe Num.of(50, 3)
        Finance.compareUnitPrices(Num.of(10), Num.of(2), Num.of(20), Num.of(4)).savingPercent shouldBe Num.ZERO
        val trip = Finance.fuelCost(Num.of(350), Num.of(35, 2), Num.of(209, 2))
        trip.fuel shouldBe Num.of(20)
        trip.cost shouldBe Num.of(2090)
        Finance.proportion(Num.of(3), Num.of(450), Num.of(5)) shouldBe Num.of(750)
        shouldThrow<ArithFault.Domain> { Finance.proportion(Num.ZERO, Num.of(1), Num.of(1)) }
        shouldThrow<ArithFault.Domain> { Finance.fuelCost(Num.of(100), Num.ZERO, Num.of(100)) }
        shouldThrow<ArithFault.Domain> { Finance.compareUnitPrices(Num.of(1), Num.ZERO, Num.of(1), Num.of(1)) }
    }

    "body-mass index is exact and banded at the WHO adult thresholds" {
        val typical = BodyMass.index(Num.of(70), Num.of(175))
        typical.index shouldBe Num.of(160, 7)
        typical.band shouldBe BodyMass.Band.NORMAL
        BodyMass.index(Num.of(50), Num.of(175)).band shouldBe BodyMass.Band.UNDERWEIGHT
        BodyMass.index(Num.of(80), Num.of(175)).band shouldBe BodyMass.Band.OVERWEIGHT
        BodyMass.index(Num.of(100), Num.of(175)).band shouldBe BodyMass.Band.OBESITY
        // the thresholds belong to the upper band: exactly 18.5 is normal, exactly 25 is overweight
        BodyMass.index(Num.of(4736, 100), Num.of(160)).band shouldBe BodyMass.Band.NORMAL
        BodyMass.index(Num.of(64), Num.of(160)).band shouldBe BodyMass.Band.OVERWEIGHT
        shouldThrow<ArithFault.Domain> { BodyMass.index(Num.ZERO, Num.of(170)) }
        shouldThrow<ArithFault.Domain> { BodyMass.index(Num.of(70), Num.of(5)) }
    }

    "durations parse, add and print" {
        Durations.parse("1:45:30") shouldBe 6330L
        Durations.parse("2:05") shouldBe 7500L
        Durations.format(Durations.parse("1:45:30") + Durations.parse("0:50:45")) shouldBe "2:36:15"
        Durations.format(-600) shouldBe "−0:10:00"
        Durations.breakdown(100_000) shouldBe "1 d 3 h 46 min 40 s"
        Durations.breakdown(3600) shouldBe "1 h 0 min 0 s"
        Durations.breakdown(59) shouldBe "59 s"
        Durations.breakdown(0) shouldBe "0 s"
        shouldThrow<ArithFault.Domain> { Durations.parse("1:60") }
        shouldThrow<ArithFault.Domain> { Durations.parse("90") }
        shouldThrow<ArithFault.Domain> { Durations.parse("1:30:75") }
    }

    "clock times: same day, overnight, and out of range" {
        Durations.between(Durations.parseClock("09:15"), Durations.parseClock("17:40")) shouldBe 30_300L
        Durations.between(Durations.parseClock("22:00"), Durations.parseClock("06:00")) shouldBe 28_800L
        Durations.parseClock("23:59:59") shouldBe 86_399L
        shouldThrow<ArithFault.Domain> { Durations.parseClock("24:00") }
    }

    // ---- dates -------------------------------------------------------------

    "dates parse as ISO or day-first, and nothing else" {
        val expected = LocalDate.of(2026, 11, 12)
        Dates.parse("2026-11-12") shouldBe expected
        Dates.parse("12/11/2026") shouldBe expected
        Dates.parse(" 12-11-2026 ") shouldBe expected
        Dates.parse("12.11.2026") shouldBe expected
        shouldThrow<ArithFault.Domain> { Dates.parse("31/02/2026") }
        shouldThrow<ArithFault.Domain> { Dates.parse("November 12") }
        shouldThrow<ArithFault.Domain> { Dates.parse("12/11/26") }
    }

    "days between two dates, as a count and as a calendar period" {
        val span = Dates.between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 12))
        span.totalDays shouldBe 315L
        span.weeks shouldBe 45L
        span.weekRemainder shouldBe 0
        Triple(span.years, span.months, span.days) shouldBe Triple(0, 10, 11)
        span.forward shouldBe true
        val back = Dates.between(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 1, 1))
        back.totalDays shouldBe 315L
        back.forward shouldBe false
    }

    "leap years are honoured" {
        Dates.between(LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1)).totalDays shouldBe 2L
        Dates.between(LocalDate.of(2023, 2, 28), LocalDate.of(2023, 3, 1)).totalDays shouldBe 1L
    }

    "adding days lands on the right date and weekday" {
        Dates.shift(LocalDate.of(2026, 11, 12), 100) shouldBe LocalDate.of(2027, 2, 20)
        Dates.shift(LocalDate.of(2026, 11, 12), -315) shouldBe LocalDate.of(2026, 1, 1)
        Dates.weekday(LocalDate.of(2026, 11, 12)) shouldBe "Thursday"
        Dates.weekday(LocalDate.of(2026, 1, 1)) shouldBe "Thursday"
        shouldThrow<ArithFault.Domain> { Dates.shift(LocalDate.of(2026, 1, 1), 5_000_000) }
    }

    "age and the next birthday, including a 29 February birthday" {
        val age = Dates.between(LocalDate.of(1990, 11, 12), LocalDate.of(2026, 11, 12))
        Triple(age.years, age.months, age.days) shouldBe Triple(36, 0, 0)
        Dates.daysToNextBirthday(LocalDate.of(1990, 11, 12), LocalDate.of(2026, 11, 12)) shouldBe 0L
        shouldThrow<ArithFault.Domain> { Dates.daysToNextBirthday(LocalDate.of(2030, 1, 1), LocalDate.of(2026, 1, 1)) }
    }

    "a 29 February birthday is observed on 1 March in common years, agreeing with the age shown (review regression)" {
        val birth = LocalDate.of(2000, 2, 29)
        // 28 February 2023: still 22, and the birthday is tomorrow — the two must not disagree.
        Dates.between(birth, LocalDate.of(2023, 2, 28)).years shouldBe 22
        Dates.daysToNextBirthday(birth, LocalDate.of(2023, 2, 28)) shouldBe 1L
        Dates.between(birth, LocalDate.of(2023, 3, 1)).years shouldBe 23
        Dates.daysToNextBirthday(birth, LocalDate.of(2023, 3, 1)) shouldBe 0L
        Dates.daysToNextBirthday(birth, LocalDate.of(2024, 2, 29)) shouldBe 0L
        Dates.daysToNextBirthday(birth, LocalDate.of(2026, 11, 12)) shouldBe 109L
        // The age line uses the same convention, so it reads a whole number of years on the day.
        val before = Dates.age(birth, LocalDate.of(2023, 2, 28))
        Triple(before.years, before.months, before.days) shouldBe Triple(22, 11, 27)
        val onTheDay = Dates.age(birth, LocalDate.of(2023, 3, 1))
        Triple(onTheDay.years, onTheDay.months, onTheDay.days) shouldBe Triple(23, 0, 0)
        val leapDay = Dates.age(birth, LocalDate.of(2024, 2, 29))
        Triple(leapDay.years, leapDay.months, leapDay.days) shouldBe Triple(24, 0, 0)
    }

    "for every other birth date the age is exactly the calendar period" {
        for ((born, asOf) in listOf(
            LocalDate.of(1990, 11, 12) to LocalDate.of(2026, 11, 12),
            LocalDate.of(1995, 1, 31) to LocalDate.of(2026, 3, 1),
            LocalDate.of(2001, 12, 31) to LocalDate.of(2026, 1, 1),
            LocalDate.of(2010, 6, 15) to LocalDate.of(2010, 6, 15),
        )) {
            val age = Dates.age(born, asOf)
            val period = Dates.between(born, asOf)
            Triple(age.years, age.months, age.days) shouldBe Triple(period.years, period.months, period.days)
            age.totalDays shouldBe period.totalDays
        }
        shouldThrow<ArithFault.Domain> { Dates.age(LocalDate.of(2030, 1, 1), LocalDate.of(2026, 1, 1)) }
    }
})
