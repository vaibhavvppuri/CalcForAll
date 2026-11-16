package com.vaibhav.calc.api

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.parse.Parser
import io.ktor.http.HttpStatusCode
import java.math.RoundingMode

internal object ApiLimits {
    const val BODY_BYTES = 65_536
    const val EXPRESSION_CHARS = 4_096
    const val EXPRESSION_NODES = 512
    const val EXPRESSION_DEPTH = 64
    const val MATRIX_DIMENSION = 6
    const val DATA_POINTS = 1_000
    const val GRAPH_SAMPLES = 1_000
    const val ROOT_SAMPLES = 256
    const val INTEGRATION_EVALUATIONS = 4_096
    const val SURFACE_GRID_SIDE = 32
    const val CONCURRENT_CALCULATIONS = 2
}

internal class ApiException(
    val status: HttpStatusCode,
    val kind: String,
    override val message: String,
    val position: Int? = null,
    val end: Int? = null,
    val recoverable: Boolean = true,
) : RuntimeException(message) {
    fun json(): Map<String, Any?> = linkedMapOf(
        "ok" to false, "kind" to kind, "error" to message,
        "position" to position, "end" to end, "recoverable" to recoverable,
    )
}

internal fun badRequest(message: String): Nothing =
    throw ApiException(HttpStatusCode.BadRequest, "INVALID_REQUEST", message)

internal fun limit(message: String): Nothing =
    throw ApiException(HttpStatusCode.UnprocessableEntity, "RESOURCE_LIMIT", message)

internal fun mathError(error: EvalError, legacy: Boolean = false): Nothing = throw ApiException(
    if (legacy) HttpStatusCode.BadRequest else HttpStatusCode.UnprocessableEntity,
    error.kind.name, error.detail, error.span?.start, error.span?.end, error.recoverable,
)

internal fun <T> CalcResult<T>.required(): T = when (this) {
    is CalcResult.Ok -> value
    is CalcResult.Err -> mathError(error)
}

internal class ApiRequest(val fields: Map<String, JsonValue>) {
    constructor(value: JsonValue) : this((value as? JsonValue.Obj)?.fields ?: badRequest("request body must be a JSON object"))

    fun only(vararg allowed: String) {
        val extras = fields.keys - allowed.toSet()
        if (extras.isNotEmpty()) badRequest("unknown field(s): ${extras.sorted().joinToString(", ")}")
    }

    fun value(name: String): JsonValue = fields[name] ?: badRequest("missing field '$name'")

    fun string(name: String, default: String? = null, max: Int = ApiLimits.EXPRESSION_CHARS): String {
        val v = fields[name] ?: return default ?: badRequest("missing field '$name'")
        val s = (v as? JsonValue.Str)?.value ?: badRequest("'$name' must be a string")
        if (s.isBlank()) badRequest("'$name' must not be blank")
        if (s.length > max) limit("'$name' exceeds $max characters")
        return s
    }

    fun int(name: String, default: Int, range: IntRange): Int {
        val v = fields[name] ?: return default
        val n = (v as? JsonValue.Number)?.text?.toIntOrNull()
            ?: badRequest("'$name' must be an integer JSON number")
        if (n !in range) badRequest("'$name' must be in ${range.first}..${range.last}")
        return n
    }

    fun bool(name: String, default: Boolean = false): Boolean {
        val v = fields[name] ?: return default
        return (v as? JsonValue.Bool)?.value ?: badRequest("'$name' must be true or false")
    }

    fun array(name: String): List<JsonValue> =
        (value(name) as? JsonValue.Arr)?.values ?: badRequest("'$name' must be an array")

    fun settings(): Settings {
        val value = fields["settings"] ?: return Settings()
        val s = ApiRequest(value)
        s.only("angle", "computePrecision", "displayPrecision", "rounding", "notation", "exactForm", "grouping", "numerals", "complexEnabled")
        val display = s.int("displayPrecision", 12, 1..32)
        val compute = s.int("computePrecision", maxOf(40, display + 8), 16..50)
        if (compute < display + 4) badRequest("computePrecision must be at least displayPrecision + 4")
        val rounding = s.enum("rounding", RoundingMode.HALF_EVEN)
        if (rounding == RoundingMode.UNNECESSARY) badRequest("UNNECESSARY rounding is not supported")
        return Settings(
            angle = s.enum("angle", AngleMode.DEG), computePrecision = compute, displayPrecision = display,
            rounding = rounding, notation = s.enum("notation", NotationMode.AUTO),
            exactForm = s.enum("exactForm", ExactForm.AUTO), grouping = s.enum("grouping", GroupingMode.NONE),
            numerals = s.enum("numerals", NumeralSystem.WESTERN), complexEnabled = s.bool("complexEnabled"),
        )
    }

    private inline fun <reified T : Enum<T>> enum(name: String, default: T): T {
        val word = string(name, default.name, 32)
        return enumValues<T>().firstOrNull { it.name.equals(word, ignoreCase = true) }
            ?: badRequest("'$name' must be one of ${enumValues<T>().joinToString { it.name }}")
    }
}

/** Validate structure before recursive engine paths, with a stack rather than recursive checks. */
internal fun checkedExpression(input: String, legacy: Boolean = false): String {
    if (input.isBlank()) badRequest("expression must not be blank")
    if (input.length > ApiLimits.EXPRESSION_CHARS) limit("expression exceeds ${ApiLimits.EXPRESSION_CHARS} characters")
    // The parser's own recursive nesting guard is more generous than the server's.
    // Check delimiters before invoking it, without attempting to validate its grammar twice.
    var delimiters = 0
    for (c in input) {
        if (c == '(' && ++delimiters > ApiLimits.EXPRESSION_DEPTH) limit("expression nesting exceeds ${ApiLimits.EXPRESSION_DEPTH}")
        if (c == ')') delimiters--
    }
    val program = when (val p = Parser.parseProgram(input)) {
        is CalcResult.Ok -> p.value
        is CalcResult.Err -> mathError(p.error, legacy)
    }
    val pending = ArrayDeque<Pair<Expr, Int>>()
    program.statements.forEach { st ->
        when (st) {
            is ExprStatement -> pending += st.expr to 1
            is Assignment -> pending += st.value to 1
            is Equation -> { pending += st.lhs to 1; pending += st.rhs to 1 }
        }
    }
    var count = 0
    while (pending.isNotEmpty()) {
        val (node, depth) = pending.removeLast()
        if (++count > ApiLimits.EXPRESSION_NODES || depth > ApiLimits.EXPRESSION_DEPTH) {
            limit("expression exceeds ${ApiLimits.EXPRESSION_NODES} nodes or ${ApiLimits.EXPRESSION_DEPTH} levels")
        }
        when (node) {
            is Binary -> { pending += node.left to depth + 1; pending += node.right to depth + 1 }
            is Unary -> pending += node.operand to depth + 1
            is Postfix -> pending += node.operand to depth + 1
            is Call -> node.args.forEach { pending += it to depth + 1 }
            is UnitApply -> pending += node.value to depth + 1
            is NameRef, is NumberLit -> Unit
        }
    }
    return input
}
