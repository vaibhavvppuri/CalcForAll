package com.vaibhav.calc.core.eval

import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Complex
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.NumMath
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.num.toBigIntegerExactOrNull
import com.vaibhav.calc.core.num.unaryMinus
import com.vaibhav.calc.core.units.Dimension
import com.vaibhav.calc.core.units.Quantity
import com.vaibhav.calc.core.units.Units
import java.math.BigInteger
import java.math.MathContext

/**
 * A runtime value: an exact real, a complex, or a [Quantity] (a value with a
 * unit — D-016 puts units inline). The real case is the common one and delegates
 * straight to the exact [Num] tower. Complex results whose imaginary part is zero
 * demote to real (`i*i → -1`); dimensionless quantities in base units demote to
 * real (`3 + 5 lakh → 500003`).
 *
 * Matrices and statistics deliberately stay outside this type — a grid is the
 * right affordance for them, not text (D-013/D-016).
 */
sealed interface Value {
    val isReal: Boolean
}

data class RealValue(val num: Num) : Value {
    override val isReal: Boolean get() = true
}

data class ComplexValue(val cx: Complex) : Value {
    override val isReal: Boolean get() = false
}

data class QuantityValue(val q: Quantity) : Value {
    override val isReal: Boolean get() = false
}

fun Num.toValue(): Value = RealValue(this)

/** A complex collapses to a real when its imaginary part is exactly zero. */
fun Complex.toValue(): Value = if (im.isZero) RealValue(re) else ComplexValue(this)

/** A plain dimensionless quantity (factor 1, no offset) is just a number. */
fun Quantity.toValue(): Value =
    if (unit.dimension.isDimensionless && !unit.isAffine && unit.factor.compareTo(Num.ONE) == 0) {
        RealValue(value)
    } else {
        QuantityValue(this)
    }

internal fun Value.toComplex(): Complex = when (this) {
    is RealValue -> Complex.real(num)
    is ComplexValue -> cx
    is QuantityValue -> throw ArithFault.Unsupported("a quantity with units cannot be used as a complex number")
}

/**
 * Promote a bare number to a dimensionless quantity so dimensions can be checked.
 * `internal` on purpose: the router compares through this SAME rule, so the
 * comparison and the arithmetic can never drift apart.
 */
internal fun Value.asQuantity(): Quantity = when (this) {
    is QuantityValue -> q
    is RealValue -> Quantity(num, Units.baseUnit(Dimension.NONE))
    is ComplexValue -> throw ArithFault.Unsupported("a complex number cannot carry units")
}

/** Scale a quantity by a plain number, preserving its unit (`3 · 5 km = 15 km`). */
private fun scale(q: Quantity, k: Num): Value {
    if (q.unit.isAffine) {
        throw ArithFault.Domain("scaling an affine unit (${q.unit.name}) is not defined; convert to an absolute scale first", "units")
    }
    return Quantity(q.value * k, q.unit).toValue()
}

/** Arithmetic over [Value], promoting only when it must. */
object ValueOps {

    fun add(a: Value, b: Value): Value = when {
        a is RealValue && b is RealValue -> RealValue(a.num + b.num)
        a is QuantityValue || b is QuantityValue -> (a.asQuantity() + b.asQuantity()).toValue()
        else -> (a.toComplex() + b.toComplex()).toValue()
    }

    fun sub(a: Value, b: Value): Value = when {
        a is RealValue && b is RealValue -> RealValue(a.num - b.num)
        a is QuantityValue || b is QuantityValue -> (a.asQuantity() - b.asQuantity()).toValue()
        else -> (a.toComplex() - b.toComplex()).toValue()
    }

    fun mul(a: Value, b: Value): Value = when {
        a is RealValue && b is RealValue -> RealValue(a.num * b.num)
        a is RealValue && b is QuantityValue -> scale(b.q, a.num)
        a is QuantityValue && b is RealValue -> scale(a.q, b.num)
        a is QuantityValue && b is QuantityValue -> (a.q * b.q).toValue()
        else -> (a.toComplex() * b.toComplex()).toValue()
    }

    fun div(a: Value, b: Value): Value = when {
        a is RealValue && b is RealValue -> RealValue(a.num / b.num)
        a is QuantityValue && b is RealValue -> {
            if (b.num.isZero) throw ArithFault.DivideByZero()
            scale(a.q, Num.ONE / b.num)
        }
        a is QuantityValue || b is QuantityValue -> (a.asQuantity() / b.asQuantity()).toValue()
        else -> (a.toComplex() / b.toComplex()).toValue()
    }

    fun neg(a: Value): Value = when (a) {
        is RealValue -> RealValue(-a.num)
        is ComplexValue -> (-a.cx).toValue()
        is QuantityValue -> scale(a.q, -Num.ONE)
    }

    fun pow(a: Value, b: Value, mc: MathContext): Value = when {
        a is RealValue && b is RealValue -> RealValue(NumMath.pow(a.num, b.num, mc))
        a is ComplexValue && b is RealValue -> {
            val k = b.num.toBigIntegerExactOrNull()
                ?: throw ArithFault.Unsupported("only integer exponents are supported for a complex base")
            a.cx.pow(k).toValue()
        }
        // A quantity may be raised to an INTEGER power — the dimension scales with
        // it (`s²`, `m³`). This is also what lets the engine re-read its own
        // derived-unit output like `kg·m/s²` (Article 12: render → parse → same).
        a is QuantityValue && b is RealValue -> {
            val k = b.num.toBigIntegerExactOrNull()
                ?: throw ArithFault.Unsupported("a quantity can only be raised to an integer power")
            if (a.q.unit.isAffine) {
                throw ArithFault.Domain(
                    "raising an affine unit (${a.q.unit.name}) to a power is not defined; convert to an absolute scale first",
                    "units",
                )
            }
            if (k.abs() > MAX_UNIT_POWER) throw ArithFault.Overflow("unit power too large")
            val n = k.toInt()
            Quantity(
                NumMath.pow(a.q.toBase(), Num.of(n.toLong()), mc),
                Units.baseUnit(a.q.unit.dimension.pow(n)),
            ).toValue()
        }
        b is QuantityValue -> throw ArithFault.Unsupported("an exponent cannot carry units")
        else -> throw ArithFault.Unsupported("a complex exponent is not supported")
    }

    private val MAX_UNIT_POWER: BigInteger = BigInteger.valueOf(32)
}
