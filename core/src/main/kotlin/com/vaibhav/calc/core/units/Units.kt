package com.vaibhav.calc.core.units

import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.times

/**
 * A unit: a name, a [Dimension], and an affine map to the base SI unit —
 * `base = value·factor + offset`. `offset` is zero for all but temperature.
 */
data class Unit(
    val name: String,
    val dimension: Dimension,
    val factor: Num,
    val offset: Num = Num.ZERO,
) {
    val isAffine: Boolean get() = !offset.isZero
}

/**
 * A value with a unit (spec 9.1). Conversion works across any two units of the
 * same dimension; add/sub/mul/div enforce dimensional analysis. Multiplying
 * pure-scale quantities combines dimensions (`m · m = m²`); affine units
 * (temperature) support conversion only, not arithmetic — that ambiguity is
 * rejected rather than guessed.
 */
data class Quantity(val value: Num, val unit: Unit) {

    fun toBase(): Num = value * unit.factor + unit.offset

    fun convertTo(target: Unit): Quantity {
        requireSameDimension(target.dimension)
        val base = toBase()
        return Quantity((base - target.offset) / target.factor, target)
    }

    operator fun plus(o: Quantity): Quantity {
        requireSameDimension(o.unit.dimension)
        requirePureScale("addition"); o.requirePureScale("addition")
        return Quantity(toBase() + o.toBase(), Units.baseUnit(unit.dimension)).convertTo(unit)
    }

    operator fun minus(o: Quantity): Quantity {
        requireSameDimension(o.unit.dimension)
        requirePureScale("subtraction"); o.requirePureScale("subtraction")
        return Quantity(toBase() - o.toBase(), Units.baseUnit(unit.dimension)).convertTo(unit)
    }

    operator fun times(o: Quantity): Quantity {
        requirePureScale("multiplication"); o.requirePureScale("multiplication")
        return Quantity(toBase() * o.toBase(), Units.baseUnit(unit.dimension * o.unit.dimension))
    }

    operator fun div(o: Quantity): Quantity {
        requirePureScale("division"); o.requirePureScale("division")
        return Quantity(toBase() / o.toBase(), Units.baseUnit(unit.dimension / o.unit.dimension))
    }

    private fun requireSameDimension(other: Dimension) {
        if (unit.dimension != other) {
            throw ArithFault.Domain("incompatible dimensions (${unit.dimension.siSymbol()} vs ${other.siSymbol()})", "units")
        }
    }

    private fun requirePureScale(op: String) {
        if (unit.isAffine) throw ArithFault.Domain("$op on an affine unit (${unit.name}); convert to an absolute scale first", "units")
    }

    override fun toString(): String = "${value} ${unit.name}"
}

/** Registry of predefined units across categories (spec 9.2), incl. Indian units (9.6). */
object Units {

    private val registry = LinkedHashMap<String, Unit>()

    fun get(name: String): Unit? = registry[name]

    fun baseUnit(dimension: Dimension): Unit =
        Unit(dimension.siSymbol().ifEmpty { "1" }, dimension, Num.ONE)

    fun names(): Set<String> = registry.keys.toSet()

    private fun reg(unit: Unit, vararg aliases: String) {
        registry[unit.name] = unit
        for (a in aliases) registry[a] = unit
    }

    // -----------------------------------------------------------------------
    // Shared exact roots. Where units are DEFINED in terms of one another, they
    // are DERIVED from one another here rather than each carrying its own decimal
    // literal — so the relation cannot drift the way two hand-typed constants can
    // (Article 12, applied to data).
    // -----------------------------------------------------------------------

    /** The international yard: 0.9144 m exactly. Squared, it defines acre and guntha. */
    private val SQUARE_YARD: Num = Num.ofDecimal("0.9144") * Num.ofDecimal("0.9144")

    /**
     * The traditional Indian **80-tola seer as tabulated by BIS: 0.93310 kg** exactly.
     * The maund is 40 of these (D-029, as corrected by **D-030**).
     *
     * **Not** the 1956 Act's metric seer, which sources give as exactly 1.25 kg and
     * which this build does not implement — D-029 cited the Act for this figure and
     * that attribution was wrong.
     *
     * The tabulated value is a rounded one: 80 tolas at the BIS tola of 11.6638038 g
     * is 0.933104304 kg, about **4.3 mg** more. The published seer is what is
     * registered, deliberately — deriving it from a tola would make it depend on
     * which tola figure is chosen.
     */
    private val SEER: Num = Num.ofDecimal("0.9331")

    init {
        // length (base m)
        reg(Unit("m", Dimension.LENGTH, Num.ONE), "meter", "metre")
        reg(Unit("km", Dimension.LENGTH, Num.of(1000)))
        reg(Unit("cm", Dimension.LENGTH, Num.ofDecimal("0.01")))
        reg(Unit("mm", Dimension.LENGTH, Num.ofDecimal("0.001")))
        reg(Unit("mi", Dimension.LENGTH, Num.ofDecimal("1609.344")), "mile")
        reg(Unit("yd", Dimension.LENGTH, Num.ofDecimal("0.9144")), "yard")
        reg(Unit("ft", Dimension.LENGTH, Num.ofDecimal("0.3048")), "foot")
        reg(Unit("in", Dimension.LENGTH, Num.ofDecimal("0.0254")), "inch")
        // The international nautical mile — 1852 m exactly, by definition.
        reg(Unit("nmi", Dimension.LENGTH, Num.of(1852)))
        // The IAU light-year: c · 86400 s · 365.25 days, all three exact, so the
        // product is the exact integer 9 460 730 472 580 800 m.
        reg(Unit("ly", Dimension.LENGTH, Num.of(299_792_458) * Num.of(86_400) * Num.ofDecimal("365.25")), "lightyear")

        // mass (base kg)
        reg(Unit("kg", Dimension.MASS, Num.ONE))
        reg(Unit("g", Dimension.MASS, Num.ofDecimal("0.001")), "gram")
        reg(Unit("mg", Dimension.MASS, Num.ofDecimal("0.000001")))
        reg(Unit("t", Dimension.MASS, Num.of(1000)), "tonne")
        reg(Unit("lb", Dimension.MASS, Num.ofDecimal("0.45359237")), "pound")
        reg(Unit("oz", Dimension.MASS, Num.ofDecimal("0.028349523125")), "ounce")
        reg(Unit("tola", Dimension.MASS, Num.ofDecimal("0.0116638"))) // Indian
        // Traditional Indian masses (D-030). The maund is EXACTLY 40 seer — a plain
        // definition every source agrees on, written as that product rather than as
        // its own literal so the 40:1 relation cannot drift. (D-029 presented this
        // as resolving a 0.2 g conflict with the 1956 Act; no source supports that
        // figure, and D-030 withdraws the claim. The values never changed.)
        reg(Unit("seer", Dimension.MASS, SEER))
        reg(Unit("maund", Dimension.MASS, Num.of(40) * SEER))

        // time (base s)
        reg(Unit("s", Dimension.TIME, Num.ONE), "sec", "second")
        reg(Unit("min", Dimension.TIME, Num.of(60)))
        reg(Unit("hr", Dimension.TIME, Num.of(3600)), "hour", "h") // `h` is the SI symbol
        reg(Unit("day", Dimension.TIME, Num.of(86400)))

        // temperature (base K, affine)
        reg(Unit("K", Dimension.TEMPERATURE, Num.ONE))
        reg(Unit("degC", Dimension.TEMPERATURE, Num.ONE, Num.ofDecimal("273.15")), "celsius")
        reg(Unit("degF", Dimension.TEMPERATURE, Num.of(5, 9), Num.ofDecimal("273.15") - Num.of(160, 9)), "fahrenheit")

        // area (base m²)
        reg(Unit("m2", Dimension.AREA, Num.ONE))
        reg(Unit("km2", Dimension.AREA, Num.of(1_000_000)))
        reg(Unit("hectare", Dimension.AREA, Num.of(10_000)), "ha")
        // An acre is 4840 square yards and a guntha is 121 — both derived from the
        // SAME square yard, so `40 guntha == 1 acre` holds EXACTLY by construction
        // rather than by two decimal literals happening to agree.
        // acre  = 4840 yd² = 316160658/78125 m² = 4046.8564224 m² exactly
        // guntha = 121 yd² = 158080329/1562500 m² = 101.17141056 m² exactly = 1/40 acre
        reg(Unit("acre", Dimension.AREA, Num.of(4840) * SQUARE_YARD), "ac")
        reg(Unit("guntha", Dimension.AREA, Num.of(121) * SQUARE_YARD))
        reg(Unit("bigha", Dimension.AREA, Num.ofDecimal("2529.2851"))) // Indian (pucca; region-dependent)

        // volume (base m³)
        reg(Unit("m3", Dimension.VOLUME, Num.ONE))
        reg(Unit("L", Dimension.VOLUME, Num.ofDecimal("0.001")), "litre", "liter")
        reg(Unit("mL", Dimension.VOLUME, Num.ofDecimal("0.000001")))
        reg(Unit("gal", Dimension.VOLUME, Num.ofDecimal("0.003785411784")), "gallon")

        // speed (base m/s)
        reg(Unit("mps", Dimension.SPEED, Num.ONE))
        reg(Unit("kmph", Dimension.SPEED, Num.of(5, 18)), "kph")
        reg(Unit("mph", Dimension.SPEED, Num.ofDecimal("0.44704")))

        // dimensionless + Indian number scales (spec 9.6)
        reg(Unit("one", Dimension.NONE, Num.ONE))
        reg(Unit("dozen", Dimension.NONE, Num.of(12)))
        reg(Unit("lakh", Dimension.NONE, Num.of(100_000)))       // Indian: 10⁵
        reg(Unit("crore", Dimension.NONE, Num.of(10_000_000)))   // Indian: 10⁷
    }
}
