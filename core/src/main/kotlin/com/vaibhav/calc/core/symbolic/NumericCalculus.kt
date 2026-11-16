package com.vaibhav.calc.core.symbolic

import com.vaibhav.calc.core.ast.*
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.num.*
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.PriorityQueue

/** Finite, proper real integrals. Tolerances are requested estimates, not proofs. */
data class IntegrationOptions(
    val absoluteTolerance: Num = Num.ofDecimal("1e-10"),
    val relativeTolerance: Num = Num.ofDecimal("1e-10"),
    val maxEvaluations: Int = 4096,
    val maxDepth: Int = 20,
    val computePrecision: Int = 48,
    val maxMillis: Long = 4000,
)

data class IntegralEstimate(
    val value: Num,
    val estimatedError: Num,
    val evaluations: Int,
    val method: String = "adaptive Gauss-Kronrod 7/15",
    val approximate: Boolean = true,
    val warnings: List<String> = listOf("Numerical estimate, not a rigorous error bound. Very narrow features can evade sampling; check by changing the bounds or tolerance."),
    val angleMode: String = "RAD",
)

object NumericCalculus {
    fun integrate(
        input: String, lower: Num, upper: Num, variable: String = "x",
        options: IntegrationOptions = IntegrationOptions(),
    ): CalcResult<IntegralEstimate> = symbolicResult {
        if (options.computePrecision !in 32..50 || options.maxEvaluations !in 128..20000 ||
            options.maxDepth !in 1..24 || options.maxMillis !in 100..15000) {
            unsupported("integration limits: precision 32..50, evaluations 128..20000, depth 1..24, time 100..15000 ms")
        }
        val mc = MathContext(options.computePrecision, RoundingMode.HALF_EVEN)
        val absTol = options.absoluteTolerance.toBigDecimal(mc)
        val relTol = options.relativeTolerance.toBigDecimal(mc)
        if (absTol < BigDecimal("1e-20") || absTol > BigDecimal("0.01") ||
            relTol < BigDecimal("1e-20") || relTol > BigDecimal("0.01")) {
            unsupported("absolute and relative tolerances must be from 1e-20 to 0.01")
        }
        val name = variable.lowercase()
        // Exact polynomial collection prevents avoidable catastrophic cancellation
        // such as (1e80+x)-1e80 before a finite-precision sampling boundary.
        val source = Symbolic.canonical(Expressions.parse(input, name))
        val exactPolynomial = RationalPolynomial.from(source)?.coefficients(name) != null
        val a0 = lower.toBigDecimal(mc)
        val b0 = upper.toBigDecimal(mc)
        if (a0.abs() > BigDecimal("1e12") || b0.abs() > BigDecimal("1e12")) unsupported("integration bounds must have magnitude at most 1e12")
        val a = minOf(a0, b0)
        val b = maxOf(a0, b0)
        if (lower.compareTo(upper) != 0 && a.compareTo(b) == 0) unsupported("distinct bounds collapse at the requested precision; increase precision or translate the variable")
        if (a.compareTo(b) != 0 && b.subtract(a).abs() < BigDecimal("1e-24")) unsupported("integration interval is too narrow for the numerical budget")
        if (a.compareTo(b) != 0) {
            val requestedDigits = maxOf(absTol.stripTrailingZeros().scale(), relTol.stripTrailingZeros().scale())
            val resolution = maxOf(a.abs(), b.abs()).multiply(BigDecimal.ONE.scaleByPowerOfTen(-options.computePrecision + requestedDigits + 4))
            if (b.subtract(a) < resolution) unsupported("the bounds are too close relative to their magnitude for the requested precision; translate the variable")
        }
        val started = System.nanoTime()
        fun checkpoint() {
            if (Thread.currentThread().isInterrupted) unsupported("integration was cancelled")
            if ((System.nanoTime() - started) / 1_000_000 > options.maxMillis) unsupported("integration exceeded its time budget; simplify the expression or split the interval")
        }
        checkpoint()
        val settings = Settings(angle = AngleMode.RAD, computePrecision = options.computePrecision, displayPrecision = 20)
        val environment = Environment()
        val bindings = mutableMapOf<String, Num>()
        val evaluator = Evaluator(settings, environment, bindings)
        var evaluations = 0
        var intermediateMagnitude = BigDecimal.ONE
        val requiredDigits = maxOf(absTol.stripTrailingZeros().scale(), relTol.stripTrailingZeros().scale())
        val conditioningLimit = BigDecimal.ONE.scaleByPowerOfTen(minOf(32, options.computePrecision) - requiredDigits - 6)
        fun at(x: BigDecimal): BigDecimal {
            checkpoint()
            if (++evaluations > options.maxEvaluations) unsupported("integration did not converge within ${options.maxEvaluations} evaluations")
            bindings[name] = Num.ofDecimal(x)
            val result = when (val value = evaluator.eval(source)) {
                is CalcResult.Err -> throw SymbolicFault(value.error)
                is CalcResult.Ok -> (value.value as? RealValue)?.num
                    ?: domain("numerical integration needs real scalar values")
            }.toBigDecimal(mc)
            if (result.abs() > MAX_VALUE) unsupported("integrand magnitude exceeds 1e100")
            // The absolute floor keeps ordinary zeros and odd functions valid.
            // A huge intermediate divided by a tiny final value can conceal
            // cancellation in finite-precision function arithmetic; refuse it.
            // Exact rational polynomials have no such intermediate rounding.
            if (!exactPolynomial && intermediateMagnitude > maxOf(BigDecimal.ONE, result.abs()).multiply(conditioningLimit)) {
                unsupported("intermediate values are too large relative to the result for a reliable estimate; simplify or rescale the expression")
            }
            return result
        }
        // Reject known or unproven internal poles before sampling: symmetric
        // quadrature must never report a principal value as a proper integral.
        // A rational polynomial is continuous everywhere and its intermediates
        // evaluate exactly. Applying decimal range-addition guards to it would
        // needlessly reject expressions such as 1e80+x.
        if (!exactPolynomial) intermediateMagnitude = ContinuousRange(settings, name, a, b, ::checkpoint).check(source)
        at(a)
        if (a.compareTo(b) == 0) return@symbolicResult IntegralEstimate(Num.ZERO, Num.ZERO, evaluations)
        at(b)

        data class Panel(val a: BigDecimal, val b: BigDecimal, val value: BigDecimal, val error: BigDecimal, val depth: Int)
        fun panel(lo: BigDecimal, hi: BigDecimal, depth: Int): Panel {
            val half = hi.subtract(lo, mc).divide(TWO, mc)
            val center = lo.add(half, mc)
            val midpoint = at(center)
            var kronrod = midpoint.multiply(KRONROD[0], mc)
            var gauss = midpoint.multiply(GAUSS[0], mc)
            var magnitude = midpoint.abs().multiply(KRONROD[0], mc)
            for (i in 1..7) {
                val offset = half.multiply(NODES[i], mc)
                val left = at(center.subtract(offset, mc))
                val right = at(center.add(offset, mc))
                val pair = left.add(right, mc)
                kronrod = kronrod.add(pair.multiply(KRONROD[i], mc), mc)
                magnitude = magnitude.add(left.abs().add(right.abs(), mc).multiply(KRONROD[i], mc), mc)
                if (i % 2 == 0) gauss = gauss.add(pair.multiply(GAUSS[i / 2], mc), mc)
            }
            val value = kronrod.multiply(half, mc)
            // Include a floor for the tabulated 34-digit nodes and arithmetic.
            val error = maxOf(kronrod.subtract(gauss, mc).abs().multiply(half, mc),
                magnitude.multiply(half, mc).multiply(BigDecimal("1e-30"), mc))
            return Panel(lo, hi, value, error, depth)
        }
        val panels = PriorityQueue<Panel>(11, compareByDescending { it.error })
        var total = BigDecimal.ZERO
        var error = BigDecimal.ZERO
        // Several initial panels reduce simple symmetry/aliasing accidents.
        for (i in 0 until 4) {
            val lo = a.add(b.subtract(a, mc).multiply(BigDecimal(i), mc).divide(FOUR, mc), mc)
            val hi = a.add(b.subtract(a, mc).multiply(BigDecimal(i + 1), mc).divide(FOUR, mc), mc)
            val p = panel(lo, hi, 0)
            panels.add(p)
            total = total.add(p.value, mc)
            error = error.add(p.error, mc)
        }
        while (error > maxOf(absTol, total.abs().multiply(relTol, mc))) {
            checkpoint()
            val old = panels.remove()
            if (old.depth >= options.maxDepth) unsupported("integration did not converge before the subdivision limit; split the interval")
            if (evaluations + 30 > options.maxEvaluations) unsupported("integration did not converge within ${options.maxEvaluations} evaluations")
            val middle = old.a.add(old.b, mc).divide(TWO, mc)
            if (middle.compareTo(old.a) == 0 || middle.compareTo(old.b) == 0) unsupported("integration subdivision reached the working precision")
            val left = panel(old.a, middle, old.depth + 1)
            val right = panel(middle, old.b, old.depth + 1)
            total = total.subtract(old.value, mc).add(left.value, mc).add(right.value, mc)
            error = maxOf(BigDecimal.ZERO, error.subtract(old.error, mc).add(left.error, mc).add(right.error, mc))
            panels.add(left)
            panels.add(right)
        }
        IntegralEstimate(Dec(if (a0 > b0) total.negate() else total), Dec(error), evaluations)
    }

    private val TWO = BigDecimal(2)
    private val FOUR = BigDecimal(4)
    private val MAX_VALUE = BigDecimal("1e100")
    // Standard mathematical GK15 / G7 quadrature constants, checked against
    // Boost.Math's published tables. Algorithm here is independently written.
    private val NODES = listOf("0", "0.207784955007898467600689403773244913", "0.405845151377397166906606412076961463",
        "0.586087235467691130294144838258729598", "0.741531185599394439863864773280788407",
        "0.864864423359769072789712788640926201", "0.949107912342758524526189684047851262", "0.991455371120812639206854697526328517").map(::BigDecimal)
    private val KRONROD = listOf("0.209482141084727828012999174891714264", "0.204432940075298892414161999234649085",
        "0.190350578064785409913256402421013683", "0.169004726639267902826583426598550284",
        "0.140653259715525918745189590510237920", "0.104790010322250183839876322541518017",
        "0.063092092629978553290700663189204287", "0.022935322010529224963732008058969592").map(::BigDecimal)
    private val GAUSS = listOf("0.417959183673469387755102040816326531", "0.381830050505118944950369775488975134",
        "0.279705391489276667901467771423779582", "0.129484966168869693270611432679082018").map(::BigDecimal)
}
