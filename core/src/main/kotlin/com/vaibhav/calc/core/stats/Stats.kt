package com.vaibhav.calc.core.stats

import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.NumMath
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.times
import java.math.MathContext

/**
 * Descriptive statistics and least-squares regression (spec D8). Sums, means,
 * variances and regression coefficients stay **exact rationals**; only standard
 * deviation and the correlation coefficient (genuine square roots) decimalise.
 */
object Stats {

    data class Descriptive(
        val count: Int,
        val sum: Num,
        val mean: Num,
        val min: Num,
        val max: Num,
        val range: Num,
        val median: Num,
        val variancePopulation: Num,
        /** Undefined (null) for a single data point — division by n−1 = 0. */
        val varianceSample: Num?,
        val stdevPopulation: Num,
        val stdevSample: Num?,
    )

    fun describe(data: List<Num>, mc: MathContext): Descriptive {
        if (data.isEmpty()) throw ArithFault.Domain("no data to summarise", "stats")
        val n = data.size
        var sum: Num = Num.ZERO
        for (x in data) sum = sum + x
        val mean = sum / Num.of(n.toLong())

        val sorted = data.sorted()
        val min = sorted.first()
        val max = sorted.last()
        val range = max - min
        val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / Num.TWO

        var ssd: Num = Num.ZERO // Σ(x − mean)²
        for (x in data) {
            val d = x - mean
            ssd = ssd + d * d
        }
        val varPop = ssd / Num.of(n.toLong())
        val sdPop = NumMath.sqrt(varPop, mc)
        // Sample variance/stdev are undefined for n = 1 (÷ (n−1)); report null,
        // not a misleading 0. Population variance/stdev at n = 1 legitimately are 0.
        val varSample = if (n > 1) ssd / Num.of((n - 1).toLong()) else null
        val sdSample = varSample?.let { NumMath.sqrt(it, mc) }

        return Descriptive(n, sum, mean, min, max, range, median, varPop, varSample, sdPop, sdSample)
    }

    data class Regression(
        val slope: Num,
        val intercept: Num,
        /** Pearson correlation; undefined (null) when all y values are equal. */
        val r: Num?,
        /** Coefficient of determination; undefined for a constant response. */
        val r2: Num?,
    )

    fun linearRegression(points: List<Pair<Num, Num>>, mc: MathContext): Regression {
        val n = points.size
        if (n < 2) throw ArithFault.Domain("need at least two points for regression", "regression")
        val bigN = Num.of(n.toLong())
        var sx: Num = Num.ZERO
        var sy: Num = Num.ZERO
        for ((x, y) in points) {
            sx = sx + x; sy = sy + y
        }
        val meanX = sx / bigN
        val meanY = sy / bigN
        // Center before multiplying: n*sum(x²)-sum(x)² loses the small
        // variation of large-offset decimal data through cancellation.
        var denomX: Num = Num.ZERO
        var denomY: Num = Num.ZERO
        var cov: Num = Num.ZERO
        for ((x, y) in points) {
            val dx = x - meanX
            val dy = y - meanY
            denomX += dx * dx
            denomY += dy * dy
            cov += dx * dy
        }
        if (denomX.isZero) throw ArithFault.Domain("regression is undefined (all x are equal)", "regression")
        val slope = cov / denomX
        val intercept = meanY - slope * meanX

        val rDenomSq = denomX * denomY
        val r = if (rDenomSq.isZero) null else cov / NumMath.sqrt(rDenomSq, mc)
        val r2 = if (rDenomSq.isZero) null else (cov * cov) / rDenomSq // exact for rational data

        return Regression(slope, intercept, r, r2)
    }
}
