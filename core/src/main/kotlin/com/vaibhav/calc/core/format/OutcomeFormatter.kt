package com.vaibhav.calc.core.format

import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.route.Outcome

data class RenderedOutcome(val primary: String, val decimal: String? = null,
    val value: Value? = null, val steps: List<String> = emptyList(), val approximate: Boolean = false)

/** One display contract for the app, history and public API. */
object OutcomeFormatter {
    fun render(outcome: Outcome, input: String, settings: Settings = Settings()): RenderedOutcome = when (outcome) {
        is Outcome.Scalar -> scalar(outcome.value, input, settings)
        is Outcome.Stored -> scalar(outcome.value, input, settings).let {
            it.copy(primary = "${outcome.register} = ${it.primary}")
        }
        is Outcome.Verdict -> RenderedOutcome(if (outcome.holds) "True" else "False")
        is Outcome.Identity -> RenderedOutcome("All ${outcome.unknown}", steps = listOf("The equation is an identity wherever both sides are defined."))
        is Outcome.NoSolution -> RenderedOutcome("No solution")
        is Outcome.Roots -> RenderedOutcome(
            "${outcome.unknown} = ${outcome.roots.joinToString(", ") { Formatter.exact(it, settings) }}",
            steps = outcome.steps, approximate = outcome.roots.any { isApproximate(it, settings) })
        is Outcome.SystemSolution -> RenderedOutcome(
            outcome.unknowns.zip(outcome.values).joinToString("; ") { (u, v) -> "$u = ${Formatter.exact(v, settings)}" },
            steps = outcome.steps, approximate = outcome.values.any { isApproximate(RealValue(it), settings) })
    }
    private fun scalar(v: Value, input: String, settings: Settings) =
        RenderedOutcome(Formatter.forInput(v, input, settings), Formatter.decimal(v, settings), v,
            approximate = isApproximate(v, settings))

    /** Exact storage and a rounded decimal display are separate facts. */
    fun isApproximate(v: Value, settings: Settings): Boolean {
        fun approximate(n: Num): Boolean = when(n) {
            is Dec -> true
            is Rational -> if(n.isInteger || settings.exactForm != ExactForm.DECIMAL) false else {
                val exact = n.toExactBigDecimalOrNull()
                exact == null || exact.round(settings.displayMc).compareTo(exact) != 0
            }
            is Surd, is SymConst -> false
        }
        return when(v) {
            is RealValue -> approximate(v.num)
            is ComplexValue -> approximate(v.cx.re) || approximate(v.cx.im)
            is QuantityValue -> approximate(v.q.value)
        }
    }
}
