package com.vaibhav.calc.core.graph

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.parse.Parser

/** The x/y domain of a real-valued z=f(x,y) surface. Validated by the sampler. */
data class SurfaceBounds(val xMin: Num, val xMax: Num, val yMin: Num, val yMax: Num) {
    companion object {
        val DEFAULT = SurfaceBounds(Num.of(-5), Num.of(5), Num.of(-5), Num.of(5))
    }
}

/** A mathematical gap is different from an evaluation that could not be completed. */
sealed interface SurfaceSample {
    val x: Num
    val y: Num
    data class Defined(override val x: Num, override val y: Num, val z: Num) : SurfaceSample
    data class Undefined(override val x: Num, override val y: Num, val error: EvalError) : SurfaceSample
    data class Unevaluated(override val x: Num, override val y: Num, val error: EvalError) : SurfaceSample
}

/** Row-major samples: y changes between rows; x changes between columns. */
data class SurfaceGrid(
    val bounds: SurfaceBounds,
    val columns: Int,
    val rows: Int,
    val samples: List<SurfaceSample>,
    val minZ: Num?,
    val maxZ: Num?,
    val angleMode: AngleMode,
    val warnings: List<String> = listOf("A sampled surface can miss features or poles between grid points. Its wireframe is not a proof of continuity."),
) {
    val definedCount: Int get() = samples.count { it is SurfaceSample.Defined }
    val undefinedCount: Int get() = samples.count { it is SurfaceSample.Undefined }
    val unevaluatedCount: Int get() = samples.count { it is SurfaceSample.Unevaluated }

    fun point(column: Int, row: Int): SurfaceSample? =
        if (column in 0 until columns && row in 0 until rows) samples.getOrNull(row * columns + column) else null
}

/** One shared evaluation path for surface sampling and arbitrary-point tracing. */
class PlottableSurface private constructor(
    val expr: Expr,
    val settings: Settings,
    private val environment: Environment,
) {
    fun at(x: Num, y: Num): SurfaceSample {
        if (x.abs() > SurfaceSampler.MAX_COORDINATE || y.abs() > SurfaceSampler.MAX_COORDINATE) {
            return SurfaceSample.Unevaluated(x, y,
                EvalError.Overflow("surface coordinates must have magnitude at most 1e6", expr.span))
        }
        return when (val result = Evaluator(settings, environment, mapOf("x" to x, "y" to y)).eval(expr)) {
            is CalcResult.Ok -> when (val value = result.value) {
                is RealValue -> if (value.num.abs() <= SurfaceSampler.MAX_HEIGHT) SurfaceSample.Defined(x, y, value.num)
                    else SurfaceSample.Unevaluated(x, y, EvalError.Overflow("surface height exceeds 1e100", expr.span))
                else -> SurfaceSample.Unevaluated(x, y,
                    EvalError.Unsupported("a surface needs a real scalar height, without units or an imaginary part", expr.span))
            }
            is CalcResult.Err -> when (result.error.kind) {
                EvalError.Kind.DOMAIN, EvalError.Kind.DIVIDE_BY_ZERO -> SurfaceSample.Undefined(x, y, result.error)
                else -> SurfaceSample.Unevaluated(x, y, result.error)
            }
        }
    }

    companion object {
        fun of(input: String, settings: Settings = Settings(), environment: Environment = Environment()): CalcResult<PlottableSurface> {
            if (input.length > SurfaceSampler.MAX_INPUT_CHARACTERS) return CalcResult.Err(
                EvalError.Unsupported("surface expression exceeds ${SurfaceSampler.MAX_INPUT_CHARACTERS} characters", null))
            val expr = when (val parsed = Parser.parse(input)) {
                is CalcResult.Err -> return parsed
                is CalcResult.Ok -> parsed.value
            }
            val pending = ArrayDeque<Pair<Expr, Int>>()
            pending.addLast(expr to 1)
            var nodes = 0
            while (pending.isNotEmpty()) {
                val (node, depth) = pending.removeLast()
                if (++nodes > SurfaceSampler.MAX_EXPRESSION_NODES || depth > SurfaceSampler.MAX_EXPRESSION_DEPTH) {
                    return CalcResult.Err(EvalError.Unsupported("surface expression is too large or deeply nested", node.span))
                }
                when (node) {
                    is Unary -> pending.addLast(node.operand to depth + 1)
                    is Binary -> { pending.addLast(node.left to depth + 1); pending.addLast(node.right to depth + 1) }
                    is Postfix -> pending.addLast(node.operand to depth + 1)
                    is Call -> node.args.forEach { pending.addLast(it to depth + 1) }
                    is UnitApply -> pending.addLast(node.value to depth + 1)
                    is NumberLit -> if (node.value is Rational &&
                        (node.value.num.abs().bitLength() > 4096 || node.value.den.bitLength() > 4096)) {
                        return CalcResult.Err(EvalError.Unsupported("surface number literal exceeds its 4096-bit budget", node.span))
                    }
                    is NameRef -> if (ReservedNames.isUnknown(node.name) &&
                        ReservedNames.canonicalUnknown(node.name) !in setOf("x","y")) {
                        return CalcResult.Err(EvalError.Unsupported("a height-field surface binds x and y; z is the resulting height", node.span))
                    }
                }
            }
            val snapshot = Environment()
            for (name in environment.names()) environment.get(name)?.let { snapshot.set(name,it) }
            return CalcResult.Ok(PlottableSurface(expr, settings, snapshot))
        }
    }
}

/** Bounded pure-Kotlin sampling. Projection, perspective and gestures belong in the UI. */
object SurfaceSampler {
    const val MAX_INPUT_CHARACTERS = 4096
    const val MAX_EXPRESSION_NODES = 512
    const val MAX_EXPRESSION_DEPTH = 64
    const val MAX_GRID_SIDE = 40
    val MAX_COORDINATE: Num = Num.of(1_000_000)
    val MAX_HEIGHT: Num = Num.ofDecimal("1e100")
    private val MIN_SPAN = Num.ofDecimal("1e-12")

    fun sample(
        input: String,
        bounds: SurfaceBounds = SurfaceBounds.DEFAULT,
        columns: Int = 21,
        rows: Int = 21,
        settings: Settings = Settings(),
        maxMillis: Long = 5000,
    ): CalcResult<SurfaceGrid> = PlottableSurface.of(input, settings).flatMap {
        sample(it, bounds, columns, rows, maxMillis)
    }

    fun sample(
        surface: PlottableSurface,
        bounds: SurfaceBounds = SurfaceBounds.DEFAULT,
        columns: Int = 21,
        rows: Int = 21,
        maxMillis: Long = 5000,
    ): CalcResult<SurfaceGrid> = guarded {
        if (columns !in 2..MAX_GRID_SIDE || rows !in 2..MAX_GRID_SIDE) {
            return@guarded CalcResult.Err(EvalError.Unsupported("surface grid sides must be from 2 to $MAX_GRID_SIDE", null))
        }
        if (maxMillis !in 100..15000) return@guarded CalcResult.Err(EvalError.Unsupported("surface time budget must be from 100 to 15000 ms", null))
        if (listOf(bounds.xMin,bounds.xMax,bounds.yMin,bounds.yMax).any { it.abs() > MAX_COORDINATE }) {
            return@guarded CalcResult.Err(EvalError.Unsupported("surface bounds must have magnitude at most 1e6", null))
        }
        if (bounds.xMin >= bounds.xMax || bounds.yMin >= bounds.yMax) {
            return@guarded CalcResult.Err(EvalError.Domain("surface minima must be smaller than maxima", null))
        }
        val width = bounds.xMax - bounds.xMin
        val height = bounds.yMax - bounds.yMin
        if (width < MIN_SPAN || height < MIN_SPAN) return@guarded CalcResult.Err(
            EvalError.Unsupported("surface domain spans must be at least 1e-12", null))
        val xSteps = Num.of(columns - 1)
        val ySteps = Num.of(rows - 1)
        val xs = (0 until columns).map { i -> when (i) {
            0 -> bounds.xMin
            columns - 1 -> bounds.xMax
            else -> bounds.xMin + width * Num.of(i) / xSteps
        } }
        val ys = (0 until rows).map { j -> when (j) {
            0 -> bounds.yMin
            rows - 1 -> bounds.yMax
            else -> bounds.yMin + height * Num.of(j) / ySteps
        } }
        val samples = ArrayList<SurfaceSample>(columns * rows)
        var minZ: Num? = null
        var maxZ: Num? = null
        val started = System.nanoTime()
        for (y in ys) for (x in xs) {
            if (Thread.currentThread().isInterrupted) return@guarded CalcResult.Err(EvalError.Unsupported("surface sampling was cancelled", null))
            if ((System.nanoTime() - started) / 1_000_000 > maxMillis) {
                return@guarded CalcResult.Err(EvalError.Unsupported("surface exceeded its time budget; use a simpler expression or smaller grid", null))
            }
            val point = surface.at(x,y)
            if ((System.nanoTime() - started) / 1_000_000 > maxMillis) {
                return@guarded CalcResult.Err(EvalError.Unsupported("surface exceeded its time budget; use a simpler expression or smaller grid", null))
            }
            samples += point
            if (point is SurfaceSample.Defined) {
                minZ = minZ?.let { minOf(it,point.z) } ?: point.z
                maxZ = maxZ?.let { maxOf(it,point.z) } ?: point.z
            }
        }
        CalcResult.Ok(SurfaceGrid(bounds,columns,rows,samples,minZ,maxZ,surface.settings.angle))
    }

    private inline fun <T> guarded(block: () -> CalcResult<T>): CalcResult<T> = try {
        block()
    } catch (fault: ArithFault) {
        CalcResult.Err(when (fault) {
            is ArithFault.DivideByZero -> EvalError.DivideByZero(null,fault.message ?: "division by zero")
            is ArithFault.Domain -> EvalError.Domain(fault.message ?: "invalid surface domain",null)
            is ArithFault.Overflow -> EvalError.Overflow(fault.message ?: "surface exceeded its numeric budget",null)
            is ArithFault.Underflow -> EvalError.Underflow(fault.message ?: "surface exceeded its numeric budget",null)
            is ArithFault.Unsupported -> EvalError.Unsupported(fault.message ?: "unsupported surface",null)
        })
    }
}
