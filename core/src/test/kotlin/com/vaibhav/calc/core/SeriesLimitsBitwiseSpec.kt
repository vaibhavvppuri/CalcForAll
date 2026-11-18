package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Bitwise
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.symbolic.Limits
import com.vaibhav.calc.core.symbolic.Series
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigInteger
import java.math.MathContext

/** CalcForAll 1.2: Taylor polynomials, numerical limits and bitwise logic. */
class SeriesLimitsBitwiseSpec : StringSpec({
    val settings = Settings()
    fun Num.d(): Double = toBigDecimal(MathContext(25)).toDouble()
    fun taylor(f: String, center: Num, order: Int): Series.Taylor =
        when (val r = Series.taylor(f, "x", center, order, settings)) {
            is CalcResult.Ok -> r.value
            is CalcResult.Err -> throw AssertionError("expected a Taylor polynomial for '$f' but got: ${r.error.message}")
        }
    fun limit(f: String, point: Num): Limits.Estimate = when (val r = Limits.at(f, "x", point, settings)) {
        is CalcResult.Ok -> r.value
        is CalcResult.Err -> throw AssertionError("expected an estimate for '$f' but got: ${r.error.message}")
    }
    fun atInfinity(f: String, positive: Boolean = true): Limits.Side = when (val r = Limits.atInfinity(f, "x", positive, settings)) {
        is CalcResult.Ok -> r.value.right
        is CalcResult.Err -> throw AssertionError("expected an estimate for '$f' but got: ${r.error.message}")
    }
    fun settled(side: Limits.Side): Double = (side as Limits.Side.Settles).value.d()

    // ---- Taylor ------------------------------------------------------------

    "sin x about 0 has exact rational coefficients" {
        val t = taylor("sin(x)", Num.ZERO, 5)
        t.expression shouldBe "x - x^3/6 + x^5/120"
        t.coefficients.map { it.d() } shouldBe listOf(0.0, 1.0, 0.0, -1.0 / 6, 0.0, 1.0 / 120)
        t.exact shouldBe true
    }

    "cos x about 0" {
        taylor("cos(x)", Num.ZERO, 4).expression shouldBe "1 - x^2/2 + x^4/24"
    }

    "a polynomial is reproduced exactly about any centre" {
        taylor("x^3", Num.of(1), 3).expression shouldBe "1 + 3*(x-1) + 3*(x-1)^2 + (x-1)^3"
        taylor("x^2", Num.of(-2), 2).expression shouldBe "4 - 4*(x+2) + (x+2)^2"
        taylor("x^2", Num.of(5), 0).expression shouldBe "25"
    }

    "ln x about 1 is the alternating series in (x−1)" {
        taylor("ln(x)", Num.of(1), 3).expression shouldBe "(x-1) - (x-1)^2/2 + (x-1)^3/3"
    }

    "an undefined centre, an oversized order and a non-variable are typed refusals" {
        Series.taylor("ln(x)", "x", Num.ZERO, 2, settings).shouldBeInstanceOf<CalcResult.Err>()
        Series.taylor("sin(x)", "x", Num.ZERO, Series.MAX_ORDER + 1, settings).shouldBeInstanceOf<CalcResult.Err>()
        Series.taylor("sin(t)", "t", Num.ZERO, 2, settings).shouldBeInstanceOf<CalcResult.Err>()
    }

    // ---- limits ------------------------------------------------------------

    "classic removable limits settle on their values from both sides" {
        limit("sin(x)/x", Num.ZERO).twoSided!!.value.d() shouldBe (1.0 plusOrMinus 1e-9)
        limit("(x^2-1)/(x-1)", Num.of(1)).twoSided!!.value.d() shouldBe (2.0 plusOrMinus 1e-9)
        limit("(1-cos(x))/x^2", Num.ZERO).twoSided!!.value.d() shouldBe (0.5 plusOrMinus 1e-9)
        limit("(1+x)^(1/x)", Num.ZERO).twoSided!!.value.d() shouldBe (2.718281828 plusOrMinus 1e-8)
    }

    "a continuous zero limit remains an estimate with an unknown error bound" {
        val value = limit("x*cos(x)", Num.ZERO).twoSided!!.value as Dec
        value.value.signum() shouldBe 0
        value.absoluteError shouldBe null
    }

    "x·sin(1/x) is honestly unsettled: the engine refuses trigonometry of arguments beyond a million" {
        limit("x*sin(1/x)", Num.ZERO).twoSided.shouldBeNull()
    }

    "a jump has two one-sided values and no two-sided limit" {
        val jump = limit("abs(x)/x", Num.ZERO)
        settled(jump.left) shouldBe (-1.0 plusOrMinus 1e-9)
        settled(jump.right) shouldBe (1.0 plusOrMinus 1e-9)
        jump.twoSided.shouldBeNull()
    }

    "poles are unbounded, with a sign on each side" {
        val even = limit("1/x^2", Num.ZERO)
        even.left shouldBe Limits.Side.Unbounded(true)
        even.right shouldBe Limits.Side.Unbounded(true)
        val odd = limit("1/x", Num.ZERO)
        odd.left shouldBe Limits.Side.Unbounded(false)
        odd.right shouldBe Limits.Side.Unbounded(true)
        odd.twoSided.shouldBeNull()
    }

    "an oscillation the engine can evaluate is reported as not settling" {
        val wild = limit("sin(ln(abs(x)))", Num.ZERO)
        wild.left shouldBe Limits.Side.Unsettled
        wild.right shouldBe Limits.Side.Unsettled
    }

    "a hole in our data is not a statement about the limit (review regression)" {
        // sin(1/x): the argument passes the engine's trigonometry limit near 0.
        limit("sin(1/x)", Num.ZERO).left shouldBe Limits.Side.Unevaluated
        // exp(-x): the exponent passes the engine's limit long before x is "large".
        atInfinity("exp(-x)") shouldBe Limits.Side.Unevaluated
        limit("exp(-x)", Num.ZERO).twoSided!!.value.d() shouldBe (1.0 plusOrMinus 1e-9)
    }

    "a side outside the function's domain is reported as having no value" {
        limit("sqrt(x)", Num.ZERO).left shouldBe Limits.Side.Undefined
    }

    "lost precision close to the point is not reported as a gap in the function (second-review regression)" {
        // 1 − cos(x³) loses every digit for small x; the function is defined, we just cannot see it.
        val cancelled = limit("x^6/(1-cos(x^3))", Num.ZERO)
        cancelled.left shouldBe Limits.Side.Unevaluated
        cancelled.right shouldBe Limits.Side.Unevaluated
    }

    "limits at infinity" {
        settled(atInfinity("(1+1/x)^x")) shouldBe (2.718281828 plusOrMinus 1e-8)
        settled(atInfinity("(2x+1)/(x-3)")) shouldBe (2.0 plusOrMinus 1e-8)
        val value = (atInfinity("1/x") as Limits.Side.Settles).value as Dec
        value.value.signum() shouldBe 0
        value.absoluteError shouldBe null
        atInfinity("x^2") shouldBe Limits.Side.Unbounded(true)
        atInfinity("x^3", positive = false) shouldBe Limits.Side.Unbounded(false)
    }

    // ---- bitwise -----------------------------------------------------------

    "bitwise logic on non-negative whole numbers" {
        fun b(n: Long) = BigInteger.valueOf(n)
        Bitwise.and(b(12), b(10)) shouldBe b(8)
        Bitwise.or(b(12), b(10)) shouldBe b(14)
        Bitwise.xor(b(12), b(10)) shouldBe b(6)
        Bitwise.shift(b(11), 2) shouldBe b(44)
        Bitwise.shift(b(11), -2) shouldBe b(2)
        Bitwise.not(b(11), 8) shouldBe b(244)
        Bitwise.not(b(0), 64) shouldBe BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
        shouldThrow<ArithFault.Domain> { Bitwise.and(b(-1), b(1)) }
        shouldThrow<ArithFault.Domain> { Bitwise.not(b(300), 8) }
        shouldThrow<ArithFault.Domain> { Bitwise.not(b(1), 12) }
        shouldThrow<ArithFault.Domain> { Bitwise.shift(b(1), 5000) }
    }
})
