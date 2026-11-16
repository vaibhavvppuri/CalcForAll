package com.vaibhav.calc.core.graph

import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.times

/**
 * Samples a function across a range (spec 10.1) and answers trace queries
 * (spec 10.3). Every point comes from [PlottableFunction.at] — the sampler owns no
 * evaluation logic of its own, so it can never disagree with the root finder about
 * what the curve does (Article 12).
 */
class Sampler(private val f: PlottableFunction) {

    /** One sample per screen column across the viewport's x-range. */
    fun sample(viewport: Viewport, columns: Int): List<Sample> =
        samples(viewport.xMin, viewport.xMax, columns)

    fun samples(xMin: Num, xMax: Num, count: Int): List<Sample> {
        require(count >= 2) { "need at least 2 samples (got $count)" }
        require(xMin < xMax) { "need xMin < xMax" }
        val span = xMax - xMin
        val divisor = Num.of((count - 1).toLong())
        return (0 until count).map { i ->
            val x = xMin + span * Num.of(i.toLong()) / divisor
            f.at(x)
        }
    }

    /** Trace at a world x. */
    fun trace(x: Num): Sample = f.at(x)

    /** Trace at a screen column. */
    fun traceAtScreen(viewport: Viewport, screenX: Num, pixelWidth: Int): Sample =
        f.at(viewport.toWorldX(screenX, pixelWidth))
}
