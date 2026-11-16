package com.vaibhav.calc.core.num

import java.math.BigInteger

/**
 * Exact values of sin/cos/tan — and their inverses — at the standard angles.
 * **A fast path, not a second trigonometry.**
 *
 * Same shape as the perfect-square path in `√`: recognise the cases that have an exact
 * answer, return it, and fall through to the numeric kernel for everything else. There
 * is exactly one `sin` (in [NumMath]); this only supplies the answer it cannot reach.
 *
 * **Why this is not an optimisation.** Without it the engine answered
 * `sin(45) = √2/2` → **FALSE**. Angle conversion is exact — 45° becomes
 * `SymConst(1/4, π)` — and then the BigDecimal kernel returns a truncated [Dec].
 * Compared against the exact `Surd` √2/2, the truncation really is unequal, so the
 * engine denied a true statement with total confidence. `sin(30) = 1/2` passed only
 * **by luck**: ½ is exactly representable in decimal, so the truncation happened to
 * land on it. A suite green by accident of representation is not evidence.
 *
 * **One table, four functions.** [SIN] is the only hand-written data here. cos reads
 * it at θ+90°; tan divides two of its entries; and the three inverse tables are
 * *derived by inverting it* rather than typed a second time. A hand-written `asin`
 * list would have been one rule in two places — and worse, the two could disagree
 * about the same angle (Article 12).
 *
 * **The Article 12 guard is what makes this legitimate.** `ExactTrigSpec`
 * property-tests every entry against the kernel to 40 digits; that test — not this
 * file — is what says the table is true. Verified load-bearing: corrupting one entry
 * (`√2/2` → `√3/2`) turns four tests red.
 *
 * The table is keyed in **degrees**, because that is how the standard angles are
 * named, but it is reached from an exact rational multiple of π, so it fires in RAD
 * (`sin(π/6)`) and GRAD (50 grad) exactly as it does in DEG.
 */
internal object ExactTrig {

    /**
     * sin, in degrees, at every standard angle — the multiples of 30° and 45°.
     *
     * These are the angles a student meets: the unit circle's named points. Nothing
     * else has an exact closed form in this number tower, so nothing else belongs here.
     *
     * **Declared first on purpose**: every other table below is derived from it, and
     * Kotlin initialises properties top-to-bottom. (This exact ordering trap already
     * bit `Tokenizer.SUPERSCRIPT` once.)
     */
    private val SIN: Map<Int, Num> = buildMap {
        fun half(radicand: Long) = Surd.of(Num.HALF, BigInteger.valueOf(radicand)) // √r / 2

        put(0, Num.ZERO)
        put(30, Num.HALF)
        put(45, half(2)) // √2/2
        put(60, half(3)) // √3/2
        put(90, Num.ONE)
        put(120, half(3))
        put(135, half(2))
        put(150, Num.HALF)
        put(180, Num.ZERO)
        put(210, -Num.HALF)
        put(225, -half(2))
        put(240, -half(3))
        put(270, -Num.ONE)
        put(300, -half(3))
        put(315, -half(2))
        put(330, -Num.HALF)
    }

    private fun sinAt(d: Int): Num? = SIN[((d % 360) + 360) % 360]
    private fun cosAt(d: Int): Num? = sinAt(d + 90)
    private fun tanAt(d: Int): Num? {
        val s = sinAt(d) ?: return null
        val c = cosAt(d) ?: return null
        return if (c.isZero) null else s / c
    }

    // --- the inverse tables, DERIVED by inverting SIN -------------------------
    // Each maps an exact value back to the one angle in the function's principal
    // range that produces it. Inverting the forward table is what keeps forward and
    // inverse from ever disagreeing about the same angle.

    /** asin: principal range [−90°, 90°]. */
    private val ASIN: Map<Num, Int> =
        listOf(-90, -60, -45, -30, 0, 30, 45, 60, 90).mapNotNull { d -> sinAt(d)?.let { it to d } }.toMap()

    /** acos: principal range [0°, 180°]. */
    private val ACOS: Map<Num, Int> =
        listOf(0, 30, 45, 60, 90, 120, 135, 150, 180).mapNotNull { d -> cosAt(d)?.let { it to d } }.toMap()

    /** atan: principal range (−90°, 90°) — open, so the poles are absent by construction. */
    private val ATAN: Map<Num, Int> =
        listOf(-60, -45, -30, 0, 30, 45, 60).mapNotNull { d -> tanAt(d)?.let { it to d } }.toMap()

    init {
        // Inverting a table is only sound if the forward map is injective over the
        // principal range. If two angles shared a value, one would be silently
        // unreachable and the inverse would answer the wrong angle with confidence.
        require(ASIN.size == 9) { "asin table lost an angle to a duplicate value: ${ASIN.size}" }
        require(ACOS.size == 9) { "acos table lost an angle to a duplicate value: ${ACOS.size}" }
        require(ATAN.size == 7) { "atan table lost an angle to a duplicate value: ${ATAN.size}" }
    }

    // --- forward -------------------------------------------------------------

    /**
     * [x] as an exact rational multiple of π, or null if it is not one.
     *
     * Zero is a multiple of π (0·π), and it is the only [Rational] that is: no other
     * rational is a rational multiple of π, because π is irrational.
     */
    private fun piMultiple(x: Num): Rational? = when {
        x.isZero -> Num.ZERO
        x is SymConst && x.konst == Konst.PI -> x.coeff
        else -> null
    }

    /** The angle in degrees when it is a whole number of them, else null. */
    private fun degrees(x: Num): Int? {
        val r = piMultiple(x) ?: return null
        val whole = (r * Num.of(180)).toBigIntegerExactOrNull() ?: return null
        return whole.mod(BigInteger.valueOf(360)).toInt() // normalise to [0, 360)
    }

    /** sin at an exact standard angle, or null to fall through to the kernel. */
    fun sin(x: Num): Num? = degrees(x)?.let { SIN[it] }

    /** cos at an exact standard angle, or null. */
    fun cos(x: Num): Num? = degrees(x)?.let { cosAt(it) }

    /**
     * tan at an exact standard angle, or null.
     *
     * At 90° and 270° cos is exactly zero, so tan is **undefined** — and this is the
     * one place the fast path must *not* fall through. The kernel answers `tan(90°)`
     * with `-4.08648721119e47`: a finite, negative, enormous number for a value that
     * does not exist. That is a failure wearing a result's clothes (Article 11), and
     * it is the most obvious pole on the unit circle. A Casio says Math ERROR.
     */
    fun tan(x: Num): Num? {
        val d = degrees(x) ?: return null
        val c = cosAt(d) ?: return null
        if (c.isZero) throw ArithFault.Domain("tan is undefined at ${d}° (cos is zero there)", "tan")
        return tanAt(d)
    }

    // --- inverse (answers in RADIANS; the evaluator converts to the angle mode) --

    private fun radiansOf(d: Int): Num = SymConst.of(Num.of(d.toLong(), 180L), Konst.PI)

    fun asin(x: Num): Num? = ASIN[x]?.let { radiansOf(it) }
    fun acos(x: Num): Num? = ACOS[x]?.let { radiansOf(it) }
    fun atan(x: Num): Num? = ATAN[x]?.let { radiansOf(it) }
}
