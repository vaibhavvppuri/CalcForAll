package com.vaibhav.calc.core.everyday

import com.vaibhav.calc.core.num.ArithFault
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Calendar arithmetic on the proleptic ISO (Gregorian) calendar (CalcForAll 1.2).
 * "Today" is always an argument — the engine never reads a clock, so results are
 * reproducible and testable.
 */
object Dates {
    const val MAX_SHIFT_DAYS = 3_660_000L

    /** [totalDays] is the absolute distance; [years]/[months]/[days] is the same span as a calendar period. */
    data class Span(val totalDays: Long, val years: Int, val months: Int, val days: Int, val forward: Boolean) {
        val weeks: Long get() = totalDays / 7
        val weekRemainder: Int get() = (totalDays % 7).toInt()
    }

    private val ISO = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
    private val DAY_FIRST = Regex("""^(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4})$""")

    /** Accepts `2026-11-12` (ISO) or day-first `12/11/2026`, `12-11-2026`, `12.11.2026`. */
    fun parse(text: String): LocalDate {
        val t = text.trim()
        val (y, m, d) = ISO.matchEntire(t)?.destructured?.let { (y, m, d) -> Triple(y, m, d) }
            ?: DAY_FIRST.matchEntire(t)?.destructured?.let { (d, m, y) -> Triple(y, m, d) }
            ?: throw ArithFault.Domain("write dates as YYYY-MM-DD or DD/MM/YYYY", "date")
        return try {
            LocalDate.of(y.toInt(), m.toInt(), d.toInt()).also {
                if (it.year < 1) throw ArithFault.Domain("years start at 0001", "date")
            }
        } catch (e: DateTimeException) {
            throw ArithFault.Domain("'$t' is not a real calendar date", "date")
        }
    }

    fun between(a: LocalDate, b: LocalDate): Span {
        val forward = !b.isBefore(a)
        val start = if (forward) a else b
        val end = if (forward) b else a
        val period = Period.between(start, end)
        return Span(ChronoUnit.DAYS.between(start, end), period.years, period.months, period.days, forward)
    }

    fun shift(date: LocalDate, days: Long): LocalDate {
        if (days !in -MAX_SHIFT_DAYS..MAX_SHIFT_DAYS) throw ArithFault.Domain("shift by at most $MAX_SHIFT_DAYS days", "date")
        val out = try {
            date.plusDays(days)
        } catch (e: DateTimeException) {
            throw ArithFault.Domain("the resulting date is out of range", "date")
        }
        if (out.year !in 1..9999) throw ArithFault.Domain("the resulting date is out of range", "date")
        return out
    }

    fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    /**
     * Days from [today] to the next anniversary of [birth]; 0 on the day itself.
     *
     * A 29 February birthday is observed on **1 March** in common years. That is the
     * convention the age itself follows: [between] counts a 29 February birth as one year
     * older on 1 March, not on 28 February, so "birthday today" and the age shown can
     * never disagree.
     */
    fun daysToNextBirthday(birth: LocalDate, today: LocalDate): Long {
        if (birth.isAfter(today)) throw ArithFault.Domain("the birth date is after the 'as of' date", "date")
        var next = anniversary(birth, today.year)
        if (next.isBefore(today)) next = anniversary(birth, today.year + 1)
        return ChronoUnit.DAYS.between(today, next)
    }

    /**
     * Age as completed years plus the months and days since the last birthday, on the same
     * 1 March convention as [daysToNextBirthday]. For every birth date except 29 February
     * this is exactly [between]; for that one date the calendar's own period arithmetic
     * never lands on a whole number of years in a common year, so the age is counted from
     * the observed birthday instead.
     */
    fun age(birth: LocalDate, today: LocalDate): Span {
        if (birth.isAfter(today)) throw ArithFault.Domain("the birth date is after the 'as of' date", "date")
        var years = today.year - birth.year
        var last = anniversary(birth, today.year)
        if (last.isAfter(today)) {
            years--
            last = anniversary(birth, today.year - 1)
        }
        val rest = Period.between(last, today)
        return Span(ChronoUnit.DAYS.between(birth, today), years, rest.months, rest.days, true)
    }

    private fun anniversary(birth: LocalDate, year: Int): LocalDate =
        if (birth.monthValue == 2 && birth.dayOfMonth == 29 && !LocalDate.of(year, 1, 1).isLeapYear) LocalDate.of(year, 3, 1)
        else birth.withYear(year)

    fun describe(date: LocalDate): String = "$date (${weekday(date)})"
}
