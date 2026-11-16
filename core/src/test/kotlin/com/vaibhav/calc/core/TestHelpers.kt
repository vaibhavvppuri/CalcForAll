package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.ComplexValue
import com.vaibhav.calc.core.eval.QuantityValue
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Complex
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.units.Quantity

/** Evaluate, asserting success + a real result, and return the exact [Num]. */
fun Calculator.num(input: String): Num = when (val r = evaluate(input)) {
    is CalcResult.Ok -> when (val v = r.value) {
        is RealValue -> v.num
        else -> throw AssertionError("expected a real result for '$input' but got: $v")
    }
    is CalcResult.Err -> throw AssertionError("expected Ok for '$input' but got: ${r.error.message}")
}

/** Evaluate, asserting success + a complex result, and return the [Complex]. */
fun Calculator.complexOf(input: String): Complex = when (val r = evaluate(input)) {
    is CalcResult.Ok -> when (val v = r.value) {
        is ComplexValue -> v.cx
        else -> throw AssertionError("expected a complex result for '$input' but got: $v")
    }
    is CalcResult.Err -> throw AssertionError("expected Ok for '$input' but got: ${r.error.message}")
}

/** Evaluate, asserting success + a quantity result, and return the [Quantity]. */
fun Calculator.quantityOf(input: String): Quantity = when (val r = evaluate(input)) {
    is CalcResult.Ok -> when (val v = r.value) {
        is QuantityValue -> v.q
        else -> throw AssertionError("expected a quantity result for '$input' but got: $v")
    }
    is CalcResult.Err -> throw AssertionError("expected Ok for '$input' but got: ${r.error.message}")
}

/** Evaluate, asserting success, and return the rendered [Answer]. */
fun Calculator.ansOf(input: String): Answer = when (val r = evaluateAnswer(input)) {
    is CalcResult.Ok -> r.value
    is CalcResult.Err -> throw AssertionError("expected Ok for '$input' but got: ${r.error.message}")
}

/** Evaluate, asserting failure, and return the [EvalError]. */
fun Calculator.err(input: String): EvalError = when (val r = evaluate(input)) {
    is CalcResult.Err -> r.error
    is CalcResult.Ok -> throw AssertionError("expected Err for '$input' but got Ok: ${r.value}")
}
