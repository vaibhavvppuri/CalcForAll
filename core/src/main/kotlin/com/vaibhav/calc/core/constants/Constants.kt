package com.vaibhav.calc.core.constants

import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Value
import com.vaibhav.calc.core.eval.toValue
import com.vaibhav.calc.core.num.Konst
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.NumMath
import com.vaibhav.calc.core.num.SymConst
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.units.Dimension
import com.vaibhav.calc.core.units.Quantity
import com.vaibhav.calc.core.units.Units
import java.math.MathContext

/**
 * Physical constants with CODATA values + uncertainty (spec 9.4) and math
 * constants (spec 9.5). Post-2019-SI defined constants are exact (uncertainty 0);
 * measured ones (G, mₑ) carry their standard uncertainty. Values are stored as
 * exact [Num]s.
 */
object Constants {

    /**
     * @param dimension what the constant *is*, dimensionally. This — not [unit] — is
     *   what the expression language uses, so `c · 2 s` is a length and not a silently
     *   dimensionless number. A constant exposed as a bare number would make every
     *   downstream dimensional check a lie.
     * @param unit the conventional CODATA label, for the reference browser. Display
     *   only; never computed with.
     */
    data class Physical(
        val name: String,
        val symbol: String,
        val value: Num,
        /** Standard uncertainty; zero for exactly-defined constants. */
        val uncertainty: Num,
        val unit: String,
        val dimension: Dimension,
    ) {
        val isExact: Boolean get() = uncertainty.isZero

        /** The constant as the engine sees it: a quantity in base SI units. */
        fun toValue(): Value =
            if (dimension.isDimensionless) RealValue(value)
            else Quantity(value, Units.baseUnit(dimension)).toValue()
    }

    private val JOULE = Dimension.MASS * Dimension.LENGTH.pow(2) / Dimension.TIME.pow(2)

    /**
     * **`h` is not here — it is the hour.**
     *
     * Planck's constant is `h` in every physics textbook, and `h` is also the SI
     * symbol for the hour, which this engine needs because `2 h` must parse and
     * because the formatter has to re-read its own output (Article 12's round-trip).
     * One name, one meaning: the unit wins, and Planck takes a suffixed spelling — the
     * convention `e_charge` already set for exactly this collision with Euler's `e`.
     *
     * The alternative was the design's: teach `h = 6.626×10⁻³⁴ J·s` in the browser and
     * have the engine answer `1 hr`. That is a wrong meaning delivered silently, which
     * is worse than either name being unavailable.
     */
    private val physical: List<Physical> = listOf(
        Physical("speed of light in vacuum", "c", Num.of(299_792_458L), Num.ZERO, "m/s", Dimension.LENGTH / Dimension.TIME),
        Physical("Planck constant", "h_planck", Num.ofDecimal("6.62607015e-34"), Num.ZERO, "J·s", JOULE * Dimension.TIME),
        Physical("elementary charge", "e_charge", Num.ofDecimal("1.602176634e-19"), Num.ZERO, "C", Dimension.CURRENT * Dimension.TIME),
        Physical("Boltzmann constant", "k_B", Num.ofDecimal("1.380649e-23"), Num.ZERO, "J/K", JOULE / Dimension.TEMPERATURE),
        Physical("Avogadro constant", "NA", Num.ofDecimal("6.02214076e23"), Num.ZERO, "1/mol", Dimension.NONE / Dimension.AMOUNT),
        Physical("molar gas constant", "R", Num.ofDecimal("8.31446261815324"), Num.ZERO, "J/(mol·K)", JOULE / Dimension.AMOUNT / Dimension.TEMPERATURE),
        Physical("standard gravity", "g0", Num.ofDecimal("9.80665"), Num.ZERO, "m/s²", Dimension.LENGTH / Dimension.TIME.pow(2)),
        Physical("Newtonian gravitation", "G", Num.ofDecimal("6.67430e-11"), Num.ofDecimal("0.00015e-11"), "m³/(kg·s²)", Dimension.LENGTH.pow(3) / Dimension.MASS / Dimension.TIME.pow(2)),
        Physical("electron mass", "me", Num.ofDecimal("9.1093837139e-31"), Num.ofDecimal("0.0000000028e-31"), "kg", Dimension.MASS),
        Physical("proton mass", "mp", Num.ofDecimal("1.67262192595e-27"), Num.ofDecimal("0.00000000052e-27"), "kg", Dimension.MASS),
    )

    private val byKey: Map<String, Physical> = physical.associateBy { it.symbol.lowercase() }

    fun physical(symbol: String): Physical? = byKey[symbol.lowercase()]

    fun allPhysical(): List<Physical> = physical

    /** Math constants (spec 9.5). π and e stay exact-symbolic; others decimalise. */
    fun math(name: String, mc: MathContext): Num? = when (name.lowercase()) {
        "pi", "π" -> SymConst.of(Num.ONE, Konst.PI)
        "e" -> SymConst.of(Num.ONE, Konst.E)
        "tau" -> SymConst.of(Num.TWO, Konst.PI)
        "phi" -> (Num.ONE + NumMath.sqrt(Num.of(5), mc)) / Num.TWO // golden ratio
        "gamma", "euler" -> Num.ofDecimal("0.57721566490153286060") // Euler–Mascheroni
        "sqrt2" -> NumMath.sqrt(Num.TWO, mc)
        else -> null
    }

    // -----------------------------------------------------------------------
    // The expression language's view. ONE TABLE (Article 12).
    // -----------------------------------------------------------------------

    /** Math constants reachable by typing, in their exact spellings. */
    private val MATH_SYMBOLS: List<String> = listOf("pi", "π", "tau", "e", "phi", "gamma", "euler", "sqrt2")

    /**
     * Every symbol the expression language resolves to a constant — **case-sensitive**.
     *
     * Case matters and is load-bearing (D-016.4): register `E` must never resolve to
     * Euler's `e`, `C` must stay a register while `c` is the speed of light, and `G`
     * (gravitation) is not `g` (gram).
     */
    val SYMBOLS: Set<String> = (physical.map { it.symbol } + MATH_SYMBOLS).toSet()

    init {
        // A constant that shadows — or is shadowed by — a unit is the D-014 failure
        // mode: a wrong meaning, silently. `h` taught as Planck while the engine reads
        // hours is exactly the bug the design's browser exposed. Fail at construction,
        // loudly, rather than in a student's hand.
        val collisions = SYMBOLS.filter { Units.get(it) != null }
        require(collisions.isEmpty()) {
            "constant symbols collide with unit names: $collisions — one name, one meaning (D-026)"
        }
        // A hand-written unit label that disagrees with the dimension would make the
        // browser teach one thing and the engine compute another. Checked where the
        // label names a registered unit; composites are covered by ConstantsSpec.
        for (p in physical) {
            val u = Units.get(p.unit) ?: continue
            require(u.dimension == p.dimension) {
                "constant '${p.symbol}' is labelled ${p.unit} but its dimension says otherwise"
            }
        }
    }

    /**
     * Resolve [name] to a constant's value, or null. **The single entry point the
     * evaluator uses**, so what the browser lists and what the router accepts cannot
     * be two vocabularies.
     */
    fun resolve(name: String, mc: MathContext): Value? {
        if (name !in SYMBOLS) return null // case-sensitive: the gate
        physical.firstOrNull { it.symbol == name }?.let { return it.toValue() }
        return math(name, mc)?.let { RealValue(it) }
    }
}
