package com.vaibhav.calc.core.solve

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Evaluator
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.name.ReservedNames

/**
 * The single construction of a [StructureExtractor] (Article 12).
 *
 * Both the expression router (solving a typed equation) and the graph layer
 * (deciding whether a plotted function is polynomial) need the same rule for
 * folding a constant sub-expression, and the same three-way answer. They call
 * this — not "the same logic", the same function — so they cannot drift.
 */
fun structureExtractor(settings: Settings, env: Environment): StructureExtractor =
    StructureExtractor(ReservedNames.UNKNOWNS) { e ->
        when (val r = Evaluator(settings, env).eval(e)) {
            is CalcResult.Ok -> when (val v = r.value) {
                is RealValue -> Extraction.Extracted(v.num)
                else -> Extraction.NotPolynomial("a non-real value cannot appear in a polynomial equation")
            }
            is CalcResult.Err -> Extraction.Failed(r.error)
        }
    }
