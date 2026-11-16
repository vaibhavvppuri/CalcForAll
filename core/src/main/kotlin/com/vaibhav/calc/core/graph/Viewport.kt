package com.vaibhav.calc.core.graph

import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.signum
import com.vaibhav.calc.core.num.times

/**
 * The visible world rectangle. All arithmetic is exact, so a viewport can be
 * panned, zoomed and interpolated without accumulating drift.
 *
 * [lerp] produces any intermediate frame exactly — scrubbable, reversible, pausable,
 * with no drift however many times it is dragged.
 *
 * **It is NOT what powers the grid→axes reveal, contrary to what this comment used to
 * say.** Reading the design's implementation settled it: the reveal's frame is fixed
 * and its grid never moves. The effect is opacity choreography over staggered
 * smoothstep intervals — axes fade in, tick labels fade in, the keypad drops away, the
 * curve draws left-to-right — which is precisely *why* the idea lands: the paper was
 * always graph paper, so nothing needs to travel.
 *
 * What [lerp] is genuinely for: pinch-zoom and pan (§05), and any reveal whose frame
 * differs from [DEFAULT] and must therefore land on it rather than snap to it.
 */
data class Viewport(val xMin: Num, val xMax: Num, val yMin: Num, val yMax: Num) {

    init {
        require(xMin < xMax) { "viewport needs xMin < xMax (got $xMin, $xMax)" }
        require(yMin < yMax) { "viewport needs yMin < yMax (got $yMin, $yMax)" }
    }

    val width: Num get() = xMax - xMin
    val height: Num get() = yMax - yMin
    val centerX: Num get() = (xMin + xMax) / Num.TWO
    val centerY: Num get() = (yMin + yMax) / Num.TWO

    fun pan(dx: Num, dy: Num): Viewport = Viewport(xMin + dx, xMax + dx, yMin + dy, yMax + dy)

    /**
     * Scale the window about a fixed point. `factor < 1` zooms IN (a smaller
     * window), `factor > 1` zooms OUT. The point ([aboutX], [aboutY]) stays put —
     * which is what pinch-to-zoom about a fingertip needs.
     */
    fun zoom(factor: Num, aboutX: Num = centerX, aboutY: Num = centerY): Viewport {
        require(factor.signum() > 0) { "zoom factor must be positive (got $factor)" }
        return Viewport(
            aboutX - (aboutX - xMin) * factor,
            aboutX + (xMax - aboutX) * factor,
            aboutY - (aboutY - yMin) * factor,
            aboutY + (yMax - aboutY) * factor,
        )
    }

    /**
     * Exact linear interpolation toward [target]; `t = 0` is this viewport, `t = 1`
     * is [target]. `t` outside [0, 1] is permitted so an easing curve may overshoot
     * — but the result must still be a valid rectangle, which [init] enforces.
     */
    fun lerp(target: Viewport, t: Num): Viewport = Viewport(
        xMin + (target.xMin - xMin) * t,
        xMax + (target.xMax - xMax) * t,
        yMin + (target.yMin - yMin) * t,
        yMax + (target.yMax - yMax) * t,
    )

    // --- world ↔ screen. Screen y grows downward. ---------------------------
    fun toScreenX(x: Num, pixelWidth: Int): Num = (x - xMin) / width * Num.of(pixelWidth.toLong())
    fun toWorldX(px: Num, pixelWidth: Int): Num = xMin + px / Num.of(pixelWidth.toLong()) * width
    fun toScreenY(y: Num, pixelHeight: Int): Num = (yMax - y) / height * Num.of(pixelHeight.toLong())
    fun toWorldY(py: Num, pixelHeight: Int): Num = yMax - py / Num.of(pixelHeight.toLong()) * height

    companion object {
        /**
         * **THE opening frame. One constant, shared — the reveal must END here.**
         *
         * There were **three** of these, all different, and none of them knew about
         * the others: this engine's `[-10,10]×[-10,10]`, the design's graph default
         * `[-6.6,6.6]×[-4.2,4.2]`, and the design's grid→axes reveal `[-5,5]×[-3.4,6.6]`.
         * In the design's demo the disagreement is invisible, because the reveal and
         * the graph are separate canvases. In the app they are **the same surface**,
         * so the curve jumps the instant the reveal ends and the live viewport takes
         * over. Nobody had noticed because the frames had never had to be one value.
         *
         * This is the design's graph default, in exact rationals: it is the considered
         * choice for a phone's aspect (≈1.57:1), where `[-10,10]×[-10,10]` is square
         * and was only ever an engine placeholder.
         *
         * **On the reveal.** It is not a viewport transition — its grid never moves;
         * the frame is fixed and the effect is opacity choreography (axes in, labels
         * in, keypad out, curve drawn). So it does not need [lerp] at all *provided it
         * adopts this frame*. If the design keeps a different reveal frame for
         * composition, then the endpoint must be interpolated to this one — which is
         * exactly what [lerp] is for. Either way there is one landing point, and it is
         * here.
         */
        val DEFAULT = Viewport(
            xMin = Num.of(-33, 5), xMax = Num.of(33, 5), // ∓6.6, exact
            yMin = Num.of(-21, 5), yMax = Num.of(21, 5), // ∓4.2, exact
        )
    }
}
