package com.vaibhav.calc.core.everyday

import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.signum
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.num.requireKnownPoint
import com.vaibhav.calc.core.num.establishedSign
import com.vaibhav.calc.core.num.minus

/**
 * Body-mass index (CalcForAll 1.2): `kg / m²` with the World Health Organization's
 * general adult bands. It is a screening number, not a diagnosis, and the bands are
 * not adjusted for age, build or population — the caller should say so.
 */
object BodyMass {
    enum class Band(val label: String) {
        UNDERWEIGHT("Underweight"), NORMAL("Normal weight"), OVERWEIGHT("Overweight"), OBESITY("Obesity")
    }

    data class Result(val index: Num, val band: Band)

    private val HUNDRED = Num.of(100)
    private val UNDER = Num.of(37, 2) // 18.5
    private val OVER = Num.of(25)
    private val OBESE = Num.of(30)

    fun index(weightKg: Num, heightCm: Num): Result {
        weightKg.requireKnownPoint("BMI classification")
        heightCm.requireKnownPoint("BMI classification")
        if (weightKg.signum() <= 0 || weightKg > Num.of(1000)) throw ArithFault.Domain("weight must be above 0 and at most 1000 kg", "bmi")
        if (heightCm < Num.of(30) || heightCm > Num.of(300)) throw ArithFault.Domain("height must be from 30 to 300 cm", "bmi")
        val metres = heightCm / HUNDRED
        val index = weightKg / (metres * metres)
        val band = when {
            (index - UNDER).establishedSign("BMI band") < 0 -> Band.UNDERWEIGHT
            (index - OVER).establishedSign("BMI band") < 0 -> Band.NORMAL
            (index - OBESE).establishedSign("BMI band") < 0 -> Band.OVERWEIGHT
            else -> Band.OBESITY
        }
        return Result(index, band)
    }
}

/** Durations and clock times in whole seconds (CalcForAll 1.2). */
object Durations {
    const val MAX_HOURS = 1_000_000_000L
    private val CLOCK = Regex("""^(\d{1,10}):([0-5]?\d)(?::([0-5]?\d))?$""")

    /** `H:MM` or `H:MM:SS` → seconds. Minutes and seconds must be 0–59. */
    fun parse(text: String): Long {
        val match = CLOCK.matchEntire(text.trim())
            ?: throw ArithFault.Domain("write a duration as H:MM or H:MM:SS, with minutes and seconds from 0 to 59", "time")
        val (h, m, s) = match.destructured
        val hours = h.toLong()
        if (hours > MAX_HOURS) throw ArithFault.Domain("use at most $MAX_HOURS hours", "time")
        return hours * 3600 + m.toLong() * 60 + (if (s.isEmpty()) 0 else s.toLong())
    }

    /** A time of day, `HH:MM` or `HH:MM:SS` with hours 0–23 → seconds after midnight. */
    fun parseClock(text: String): Long {
        val seconds = parse(text)
        if (seconds >= 86_400) throw ArithFault.Domain("a time of day runs from 00:00 to 23:59:59", "time")
        return seconds
    }

    /** Seconds from [from] to [to] on a 24-hour clock; a [to] earlier than [from] is taken as the next day. */
    fun between(from: Long, to: Long): Long = if (to >= from) to - from else to + 86_400 - from

    /** `H:MM:SS`, with a leading minus sign for negative durations. */
    fun format(seconds: Long): String {
        val magnitude = Math.abs(seconds)
        val text = "%d:%02d:%02d".format(magnitude / 3600, magnitude % 3600 / 60, magnitude % 60)
        return if (seconds < 0) "−$text" else text
    }

    /** `1 d 3 h 46 min 40 s`, omitting leading zero units. */
    fun breakdown(seconds: Long): String {
        val magnitude = Math.abs(seconds)
        val parts = listOf(magnitude / 86_400 to "d", magnitude % 86_400 / 3600 to "h", magnitude % 3600 / 60 to "min", magnitude % 60 to "s")
        val shown = parts.dropWhile { it.first == 0L }.ifEmpty { listOf(0L to "s") }
        val text = shown.joinToString(" ") { "${it.first} ${it.second}" }
        return if (seconds < 0) "−$text" else text
    }
}
