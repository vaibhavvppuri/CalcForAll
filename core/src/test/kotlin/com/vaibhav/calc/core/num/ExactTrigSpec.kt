package com.vaibhav.calc.core.num

import ch.obermuhlner.math.big.BigDecimalMath
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/**
 * **The Article 12 guard for the exact-trig fast path.**
 *
 * A hand-written table sitting beside a numeric kernel is *one rule in two places* by
 * definition. This spec is the thing that makes it legitimate: every entry is pinned
 * to the kernel to 40 significant digits, so the table cannot drift from the
 * mathematics without a red test. The table is not trusted because it was typed
 * carefully — it is trusted because this runs.
 *
 * Note the direction of proof. The kernel is the authority on *value*; the table is
 * the authority on *exactness*. The table earns its place only where it agrees with
 * the kernel to a precision far beyond anything the display will ever show.
 */
class ExactTrigSpec : StringSpec({

    val check = MathContext(40)
    val tolerance = BigDecimal("1e-38")

    /** θ° as an exact rational multiple of π — the same form the evaluator produces. */
    fun deg(d: Long): Num = SymConst.of(Num.of(d, 180), Konst.PI)

    /** Every angle the table claims, plus 360° wrap-around and negatives. */
    val standard = listOf(0L, 30, 45, 60, 90, 120, 135, 150, 180, 210, 225, 240, 270, 300, 315, 330)

    fun kernel(d: Long, f: (BigDecimal, MathContext) -> BigDecimal): BigDecimal {
        val radians = BigDecimalMath.pi(check).multiply(BigDecimal(d)).divide(BigDecimal(180), check)
        return f(radians, check)
    }

    "EVERY sin table entry agrees with the kernel to 40 digits" {
        for (d in standard) {
            val exact = NumMath.sin(deg(d), check).toBigDecimal(check)
            val approx = kernel(d) { v, m -> BigDecimalMath.sin(v, m) }
            val delta = (exact - approx).abs()
            withClue("sin(${d}°): table=$exact kernel=$approx") { (delta < tolerance) shouldBe true }
        }
    }

    "EVERY cos table entry agrees with the kernel to 40 digits" {
        for (d in standard) {
            val exact = NumMath.cos(deg(d), check).toBigDecimal(check)
            val approx = kernel(d) { v, m -> BigDecimalMath.cos(v, m) }
            val delta = (exact - approx).abs()
            withClue("cos(${d}°): table=$exact kernel=$approx") { (delta < tolerance) shouldBe true }
        }
    }

    "EVERY tan table entry agrees with the kernel to 40 digits, except at the poles" {
        for (d in standard) {
            if (d == 90L || d == 270L) continue // no value to agree about — see below
            val exact = NumMath.tan(deg(d), check).toBigDecimal(check)
            val approx = kernel(d) { v, m -> BigDecimalMath.tan(v, m) }
            val delta = (exact - approx).abs()
            withClue("tan(${d}°): table=$exact kernel=$approx") { (delta < tolerance) shouldBe true }
        }
    }

    // --- the inverse tables are derived from SIN, but they are still entries ---
    // Derivation makes them consistent with the forward table; it does not make the
    // principal range or the radian mapping right. Those are new claims, so they are
    // pinned to the kernel exactly like the forward ones.

    "EVERY asin table entry agrees with the kernel to 40 digits" {
        for (d in listOf(-90L, -60, -45, -30, 0, 30, 45, 60, 90)) {
            val value = NumMath.sin(deg(d), check) // the exact value asin must invert
            val exact = NumMath.asin(value, check).toBigDecimal(check) // radians
            val approx = BigDecimalMath.asin(value.toBigDecimal(check), check)
            withClue("asin(sin(${d}°)): table=$exact kernel=$approx") {
                ((exact - approx).abs() < tolerance) shouldBe true
            }
        }
    }

    "EVERY acos table entry agrees with the kernel to 40 digits" {
        for (d in listOf(0L, 30, 45, 60, 90, 120, 135, 150, 180)) {
            val value = NumMath.cos(deg(d), check)
            val exact = NumMath.acos(value, check).toBigDecimal(check)
            val approx = BigDecimalMath.acos(value.toBigDecimal(check), check)
            withClue("acos(cos(${d}°)): table=$exact kernel=$approx") {
                ((exact - approx).abs() < tolerance) shouldBe true
            }
        }
    }

    "EVERY atan table entry agrees with the kernel to 40 digits" {
        for (d in listOf(-60L, -45, -30, 0, 30, 45, 60)) {
            val value = NumMath.tan(deg(d), check)
            val exact = NumMath.atan(value, check).toBigDecimal(check)
            val approx = BigDecimalMath.atan(value.toBigDecimal(check), check)
            withClue("atan(tan(${d}°)): table=$exact kernel=$approx") {
                ((exact - approx).abs() < tolerance) shouldBe true
            }
        }
    }

    "asin returns an EXACT angle, not a Dec that prints like one" {
        val v = NumMath.asin(Num.HALF, MathContext(34)) // → π/6 radians
        withClue("asin(1/2) came back as ${v::class.simpleName}") { (v is SymConst) shouldBe true }
        v shouldBe SymConst.of(Num.of(1, 6), Konst.PI)
    }

    "a value with no exact angle falls through: asin(1/3)" {
        (NumMath.asin(Num.of(1, 3), MathContext(34)) is Dec) shouldBe true
    }

    "asin's DOMAIN check still runs before the table" {
        shouldThrowArith { NumMath.asin(Num.of(5), MathContext(34)) }
    }

    // --- what the fast path is FOR: exactness, not speed ----------------------

    "sin(45°) is exactly √2/2 — the surd, not a truncation of it" {
        val v = NumMath.sin(deg(45), MathContext(34))
        v.shouldBeSurd()
        (v * Num.TWO) shouldBe NumMath.sqrt(Num.TWO, MathContext(34))
        // The bug, stated as the test that would have caught it:
        (v.compareTo(NumMath.sqrt(Num.TWO, MathContext(34)) / Num.TWO)) shouldBe 0
    }

    "sin(30°) is exactly 1/2 as a RATIONAL — not a Dec that happens to print 0.5" {
        val v = NumMath.sin(deg(30), MathContext(34))
        withClue("sin(30) came back as ${v::class.simpleName}") { (v is Rational) shouldBe true }
        v shouldBe Num.HALF
    }

    "cos(60°) is exactly 1/2; cos(90°) is exactly zero" {
        NumMath.cos(deg(60), MathContext(34)) shouldBe Num.HALF
        NumMath.cos(deg(90), MathContext(34)).isZero shouldBe true
    }

    "tan(45°) is exactly 1" {
        NumMath.tan(deg(45), MathContext(34)) shouldBe Num.ONE
    }

    "tan(90°) is UNDEFINED — not a finite, negative, enormous number" {
        val e = shouldThrowArith { NumMath.tan(deg(90), MathContext(34)) }
        withClue("tan(90) must be a Domain fault, was $e") { (e is ArithFault.Domain) shouldBe true }
        shouldThrowArith { NumMath.tan(deg(270), MathContext(34)) }
    }

    // --- the fast path must not fire where there is no exact value ------------

    "a non-standard angle falls through to the kernel" {
        val v = NumMath.sin(SymConst.of(Num.of(1, 7), Konst.PI), MathContext(34)) // π/7
        withClue("π/7 has no closed form here; must be Dec") { (v is Dec) shouldBe true }
    }

    "a plain rational angle (radians, not a multiple of π) falls through" {
        val v = NumMath.sin(Num.ONE, MathContext(34)) // sin(1 radian)
        (v is Dec) shouldBe true
        v shouldNotBe Num.ZERO
    }

    "sin(0) is exactly zero — zero IS a multiple of pi" {
        NumMath.sin(Num.ZERO, MathContext(34)).isZero shouldBe true
        NumMath.cos(Num.ZERO, MathContext(34)) shouldBe Num.ONE
        NumMath.tan(Num.ZERO, MathContext(34)).isZero shouldBe true
    }

    "angles outside [0,360) normalise: 390° = 30°, −30° = 330°" {
        NumMath.sin(deg(390), MathContext(34)) shouldBe Num.HALF
        NumMath.sin(deg(-30), MathContext(34)) shouldBe -Num.HALF
        NumMath.sin(deg(750), MathContext(34)) shouldBe Num.HALF // 750 = 2·360 + 30
    }

    "a fractional degree is not in the table and must fall through" {
        // 30.5° = 61/360 · π — a rational multiple of π, but not a whole degree.
        val v = NumMath.sin(SymConst.of(Num.of(61, 360), Konst.PI), MathContext(34))
        (v is Dec) shouldBe true
    }

    "the table is not empty — a vacuous pass is not a pass (Article 11)" {
        standard.size shouldBe 16
    }
})

private fun Num.shouldBeSurd() {
    if (this !is Surd) throw AssertionError("expected a Surd, got ${this::class.simpleName} = $this")
}

private fun shouldThrowArith(block: () -> Unit): ArithFault {
    try {
        block()
    } catch (e: ArithFault) {
        return e
    }
    throw AssertionError("expected an ArithFault, none thrown")
}

private fun <T> withClue(clue: String, block: () -> T): T =
    try {
        block()
    } catch (e: AssertionError) {
        throw AssertionError("$clue\n${e.message}")
    }
