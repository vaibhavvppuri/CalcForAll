package com.vaibhav.calc.core.stats

import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.requireKnownPoint

/**
 * Quartiles and modes (CalcForAll 1.3).
 *
 * Quartiles have several textbook definitions that give different numbers, so the one
 * used is stated: **median of halves**. The data is sorted and split into a lower and
 * an upper half; with an odd count the middle value belongs to neither. Q1 and Q3 are
 * the medians of those halves. This is the method taught in most school courses and
 * used by common graphing calculators; spreadsheet `QUARTILE` functions interpolate
 * and can differ.
 */
object Quartiles {
    const val MAX_VALUES = 1_000

    /** [modes] is empty when no value repeats; otherwise every value that occurs [modeCount] times. */
    data class Result(val q1: Num, val median: Num, val q3: Num, val modes: List<Num>, val modeCount: Int)

    fun of(data: List<Num>): Result {
        if (data.size < 2) throw ArithFault.Domain("quartiles need at least 2 values", "quartiles")
        if (data.size > MAX_VALUES) throw ArithFault.Domain("use at most $MAX_VALUES values", "quartiles")
        data.forEach { it.requireKnownPoint("quartiles and modes") }
        val sorted = data.sortedWith { a, b -> a.compareTo(b) }
        val half = sorted.size / 2
        val runs = ArrayList<Pair<Num, Int>>()
        for (value in sorted) {
            val last = runs.lastOrNull()
            if (last != null && last.first.compareTo(value) == 0) runs[runs.size - 1] = last.first to last.second + 1
            else runs += value to 1
        }
        val most = runs.maxOf { it.second }
        val modes = if (most < 2) emptyList() else runs.filter { it.second == most }.map { it.first }
        return Result(
            q1 = median(sorted.subList(0, half)),
            median = median(sorted),
            q3 = median(sorted.subList(sorted.size - half, sorted.size)),
            modes = modes,
            modeCount = if (most < 2) 0 else most,
        )
    }

    private fun median(sorted: List<Num>): Num {
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / Num.TWO
    }
}
