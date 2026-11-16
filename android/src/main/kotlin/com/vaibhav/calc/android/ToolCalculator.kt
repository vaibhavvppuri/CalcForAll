package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.format.OutcomeFormatter
import com.vaibhav.calc.core.matrix.Matrix
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.symbolic.Symbolic
import com.vaibhav.calc.core.symbolic.NumericCalculus
import com.vaibhav.calc.core.symbolic.IntegrationOptions

data class ToolAnswer(
    val text: String,
    val insert: String? = null,
    val error: Boolean = false,
    val insertAngle: AngleMode? = null,
    val insertNotice: String? = null,
)

/** Input adapters only. Every mathematical operation is delegated to the shared engine. */
object ToolCalculator {
    fun calculate(tool: String, operation: String, a: String, b: String, extra: String, settings: Settings): ToolAnswer = try {
        require(listOf(a, b, extra).all { it.length <= 8192 }) { "Input is too long." }
        val calc = Calculator(settings)
        fun num(text: String, engine: Calculator = calc): Num = when (val value = engine.evaluate(text.trim()).unwrap()) {
            is RealValue -> value.num
            else -> throw IllegalArgumentException("Use real, dimensionless numbers in this tool.")
        }
        fun show(value: Num): String = (if (OutcomeFormatter.isApproximate(RealValue(value), settings)) "≈ " else "") +
            Formatter.exact(RealValue(value), settings)
        fun plain(value: Num): String = Formatter.input(value).let { if (value is Dec) "approx($it)" else it }
        fun matrix(text: String): Matrix {
            val rows = text.trim().split(Regex("[;\\n]+"))
            require(rows.size in 1..6) { "Use 1 to 6 matrix rows." }
            return Matrix.of(rows.map { row ->
                val cells = if (row.contains(',')) row.split(',').map { it.trim() } else row.trim().split(Regex("\\s+"))
                require(cells.size in 1..6) { "Use 1 to 6 matrix columns." }
                require(cells.none { it.isBlank() }) { "Fill every matrix cell." }
                cells.map { num(it) }
            })
        }
        fun data(text: String): List<Num> {
            val parts = text.trim().split(Regex("[,;\\s]+"))
            require(parts.size <= 1000) { "Use at most 1,000 values." }
            return parts.map { num(it) }
        }
        when (tool) {
            "Units" -> {
                val value = calc.convert(num(a), b.trim(), extra.trim()).unwrap()
                ToolAnswer("${show(value)} ${extra.trim()}", "(${plain(value)}) ${extra.trim()}")
            }
            "Matrices" -> {
                val first = matrix(a)
                when (operation) {
                    "Determinant" -> first.determinant().let { ToolAnswer(show(it), plain(it)) }
                    "Rank" -> ToolAnswer(first.rank().toString(), first.rank().toString())
                    else -> {
                        val result = when (operation) {
                            "Inverse" -> calc.matrixInverse(first).unwrap()
                            "Transpose" -> first.transpose()
                            "Add" -> calc.matrixAdd(first, matrix(b)).unwrap()
                            "Subtract" -> calc.matrixSubtract(first, matrix(b)).unwrap()
                            "Multiply" -> calc.matrixMultiply(first, matrix(b)).unwrap()
                            "Scale" -> first.scalarTimes(num(b))
                            else -> throw IllegalArgumentException("Unknown matrix operation.")
                        }
                        ToolAnswer(result.data.joinToString("\n") { row -> row.joinToString("    ", prefix = "[ ", postfix = " ]") { show(it) } })
                    }
                }
            }
            "Statistics" -> {
                if (operation == "Linear regression") {
                    val xs = data(a); val ys = data(b)
                    require(xs.size == ys.size) { "X and Y must contain the same number of values." }
                    val r = calc.linearRegression(xs.zip(ys)).unwrap()
                    ToolAnswer("y = (${show(r.slope)})x + (${show(r.intercept)})\nr = ${r.r?.let(::show) ?: "undefined (constant Y)"}\nr² = ${r.r2?.let(::show) ?: "undefined (constant Y)"}\n\nLeast-squares linear fit; ${xs.size} pairs.")
                } else {
                    val d = calc.statsDescribe(data(a)).unwrap()
                    ToolAnswer(listOf("Count" to d.count.toString(), "Sum" to show(d.sum), "Mean" to show(d.mean),
                        "Median" to show(d.median), "Minimum" to show(d.min), "Maximum" to show(d.max), "Range" to show(d.range),
                        "Population variance" to show(d.variancePopulation), "Population standard deviation" to show(d.stdevPopulation),
                        "Sample variance" to (d.varianceSample?.let(::show) ?: "undefined (n = 1)"),
                        "Sample standard deviation" to (d.stdevSample?.let(::show) ?: "undefined (n = 1)"))
                        .joinToString("\n") { "${it.first}: ${it.second}" })
                }
            }
            "Number bases" -> {
                val from = b.toIntOrNull(); val to = extra.toIntOrNull()
                require(from in 2..36 && to in 2..36) { "Bases must be whole numbers from 2 to 36." }
                val value = calc.fromBase(a.trim(), from!!).unwrap()
                ToolAnswer("Base $to: ${calc.toBase(value, to!!).unwrap()}\nDecimal: ${plain(value)}", plain(value))
            }
            "Algebra & calculus" -> {
                if(operation == "Definite integral") {
                    val bounds = extra.split(';')
                    require(bounds.size == 2) { "Supply a lower and upper integration bound." }
                    val integrationPrecision = settings.computePrecision.coerceIn(32, 50)
                    val radSettings = settings.copy(angle = AngleMode.RAD, computePrecision = integrationPrecision)
                    val boundsCalculator = Calculator(radSettings)
                    val result = NumericCalculus.integrate(a, num(bounds[0], boundsCalculator), num(bounds[1], boundsCalculator), b.trim(),
                        IntegrationOptions(computePrecision = integrationPrecision)).unwrap()
                    val estimated = result.value as? Dec ?: Dec(result.value.toBigDecimal(radSettings.computeMc))
                    ToolAnswer("${show(estimated)}\nEstimated error: ${show(result.estimatedError)}\n${result.evaluations} evaluations · ${result.method}\n\n${result.warnings.joinToString("\n")}\nCalculus uses radians. Bound expressions also use radians.", plain(estimated),
                        insertNotice = "Inserted an approximate integral estimate.")
                } else {
                val result = when (operation) {
                    "Simplify" -> Symbolic.simplify(a, b.trim())
                    "Expand" -> Symbolic.expand(a, b.trim())
                    "Factor" -> Symbolic.factor(a, b.trim())
                    "Differentiate" -> Symbolic.differentiate(a, b.trim())
                    "Integrate" -> Symbolic.integrate(a, b.trim())
                    else -> throw IllegalArgumentException("Unknown algebra operation.")
                }.unwrap()
                ToolAnswer(buildString {
                    append(result.display)
                    append("\n\n"); append(result.steps.joinToString("\n"))
                    if (result.conditions.isNotEmpty()) { append("\n\nConditions\n"); append(result.conditions.joinToString("\n")) }
                    if (operation in listOf("Differentiate", "Integrate")) append("\n\nCalculus uses radians.")
                    if (result.domainRestricted) append("\n\nAutomatic insertion is unavailable so the domain conditions remain attached to this result.")
                }, if (result.domainRestricted) null else result.expression,
                    insertAngle = if (result.domainRestricted) null else AngleMode.RAD,
                    insertNotice = if (result.domainRestricted) null else if (result.constantOfIntegration)
                        "Using radians and one antiderivative with C = 0."
                    else "Using radians for this algebra/calculus expression.")
                }
            }
            else -> throw IllegalArgumentException("Unknown tool.")
        }
    } catch (e: ToolInputError) { ToolAnswer(e.message.orEmpty(), error = true) }
      catch (e: ArithFault) { ToolAnswer(e.message.orEmpty(), error = true) }
      catch (e: IllegalArgumentException) { ToolAnswer(e.message.orEmpty(), error = true) }
      catch (e: ArithmeticException) { ToolAnswer(e.message ?: "Arithmetic could not be completed.", error = true) }

    private class ToolInputError(message: String) : RuntimeException(message)
    private fun <T> CalcResult<T>.unwrap(): T = when (this) {
        is CalcResult.Ok -> value
        is CalcResult.Err -> throw ToolInputError(error.message)
    }
}
