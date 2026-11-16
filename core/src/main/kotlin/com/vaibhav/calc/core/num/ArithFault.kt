package com.vaibhav.calc.core.num

/**
 * Internal, position-free arithmetic faults thrown *within* the number tower.
 * They never escape the engine: the evaluator catches them at the AST boundary
 * and converts each into a typed [com.vaibhav.calc.core.error.EvalError] with a source
 * span attached. This keeps `num/` free of any dependency on source positions
 * while still honouring "errors report where" at the public boundary.
 */
sealed class ArithFault(message: String) : RuntimeException(message) {
    class DivideByZero(message: String = "division by zero") : ArithFault(message)
    class Domain(message: String, val function: String? = null) : ArithFault(message)
    class Overflow(message: String = "result too large") : ArithFault(message)
    class Underflow(message: String = "result too small") : ArithFault(message)
    class Unsupported(message: String) : ArithFault(message)
}
