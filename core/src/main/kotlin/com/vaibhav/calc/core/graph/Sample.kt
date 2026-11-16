package com.vaibhav.calc.core.graph

import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.num.Num

/**
 * The value of a plotted function at one x.
 *
 * The three cases are **structurally distinct** (Article 11), because a renderer
 * must treat them differently and a single `null`/`NaN` would collapse them:
 *
 *  - [Defined] — f(x) is this real number. Draw it.
 *  - [Undefined] — **a GAP in the curve.** The mathematics has no value here
 *    (`1/x` at 0, `ln(x)` at −1, an asymptote). The function genuinely does not
 *    exist at this x, and the line must break.
 *  - [Unevaluated] — **a HOLE in the data.** *We* could not compute it (unsupported
 *    form, overflow, a bound we hit). The function may well have a value; we do not
 *    know it. This must never be drawn as a confident gap — that would assert
 *    something about the mathematics that we never established.
 *
 * A gap in a curve and a hole in the data are not the same thing.
 */
sealed interface Sample {
    val x: Num

    data class Defined(override val x: Num, val y: Num) : Sample

    /** The function has no value here. A gap. */
    data class Undefined(override val x: Num, val cause: EvalError) : Sample

    /** We failed to determine the value here. A hole. */
    data class Unevaluated(override val x: Num, val error: EvalError) : Sample
}
