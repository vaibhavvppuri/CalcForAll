package com.vaibhav.calc.core.num

import java.math.BigInteger
import java.math.MathContext

/**
 * A complex number with **exact real components** (each a [Num]). Arithmetic
 * stays exact where the reals do; only modulus/argument/sqrt (genuinely
 * irrational) decimalise, and even then via `BigDecimal`, never `Double`.
 *
 * This is not a CAS — it is the Casio-parity complex type (spec 2.5): rectangular
 * storage, with polar in/out. The evaluator demotes a complex whose imaginary
 * part is zero back to a real (see the Value layer), so `i*i` is the real `-1`.
 */
data class Complex(val re: Num, val im: Num) {

    val isReal: Boolean get() = im.isZero

    operator fun plus(o: Complex): Complex = Complex(re + o.re, im + o.im)
    operator fun minus(o: Complex): Complex = Complex(re - o.re, im - o.im)
    operator fun times(o: Complex): Complex =
        Complex(re * o.re - im * o.im, re * o.im + im * o.re)

    operator fun unaryMinus(): Complex = Complex(-re, -im)

    fun conjugate(): Complex = Complex(re, -im)

    /** |z|² = re² + im², kept exact. */
    fun normSquared(): Num = re * re + im * im

    operator fun div(o: Complex): Complex {
        val d = o.normSquared()
        if (d.isZero) throw ArithFault.DivideByZero("complex division by zero")
        val n = this * o.conjugate()
        return Complex(n.re / d, n.im / d)
    }

    fun reciprocal(): Complex {
        val d = normSquared()
        if (d.isZero) throw ArithFault.DivideByZero("reciprocal of zero")
        return Complex(re / d, -im / d)
    }

    /** |z| — exact when it can be (`|3+4i| = 5`), decimal otherwise. */
    fun modulus(mc: MathContext): Num = NumMath.sqrt(normSquared(), mc)

    /** arg(z) in radians (the evaluator applies the angle mode). */
    fun argument(mc: MathContext): Num =
        if (re.isZero && im.isZero) Num.ZERO else NumMath.atan2(im, re, mc)

    /** Integer power by squaring — exact for a Gaussian-integer base. */
    fun pow(n: BigInteger): Complex {
        NumericLimits.powerExponent(n)
        if (n.signum() == 0) return ONE
        if (n.signum() < 0) return pow(n.negate()).reciprocal()
        var result = ONE
        var base = this
        var e = n
        while (e.signum() > 0) {
            if (e.testBit(0)) result *= base
            e = e.shiftRight(1)
            if (e.signum() > 0) base *= base
        }
        return result
    }

    /** Principal square root. */
    fun sqrt(mc: MathContext): Complex {
        if (re.isZero && im.isZero) return ZERO
        // Pure real: keep it exact and correctly signed. `√(-x)` is `i·√x`.
        if (im.isZero) {
            return if (re.signum() >= 0) Complex(NumMath.sqrt(re, mc), Num.ZERO)
            else Complex(Num.ZERO, NumMath.sqrt(-re, mc))
        }
        val r = modulus(mc)
        // (r ± re)/2 are ≥ 0 mathematically; clamp against rounding of an inexact
        // modulus (computed at a different precision than the exact components).
        val hiRad = ((r + re) / Num.TWO).let { if (it.signum() < 0) Num.ZERO else it }
        val loRad = ((r - re) / Num.TWO).let { if (it.signum() < 0) Num.ZERO else it }
        val rePart = NumMath.sqrt(hiRad, mc)
        var imPart = NumMath.sqrt(loRad, mc)
        if (im.signum() < 0) imPart = -imPart
        return Complex(rePart, imPart)
    }

    companion object {
        val ZERO = Complex(Num.ZERO, Num.ZERO)
        val ONE = Complex(Num.ONE, Num.ZERO)
        val I = Complex(Num.ZERO, Num.ONE)

        fun real(n: Num): Complex = Complex(n, Num.ZERO)

        /** r·(cos θ + i·sin θ), θ in radians. */
        fun fromPolar(r: Num, theta: Num, mc: MathContext): Complex =
            Complex(r * NumMath.cos(theta, mc), r * NumMath.sin(theta, mc))
    }
}
