package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.num.*
import com.vaibhav.calc.core.ast.NameRef
import com.vaibhav.calc.core.source.SourceSpan
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import com.vaibhav.calc.core.parse.Parser
import com.vaibhav.calc.core.route.RouteResult
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.error.EvalError

class ExactIdentitySafetySpec : StringSpec({
    fun number(text: String, angle: AngleMode = AngleMode.DEG): Num =
        (Calculator(Settings(angle = angle)).evaluate(text).getOrThrow() as RealValue).num

    "a repeated written quantity can cancel while independent approximate constructors cannot" {
        number("sin(1)-sin(1)") shouldBe Num.ZERO
        (number("approx(1,0.1)-approx(1,0.1)") as Dec).isZero shouldBe false
        number("8*(1+sqrt(2))/(1+sqrt(2))") shouldBe Num.of(8)
        number("mod(8*(1+sqrt(2)),1+sqrt(2))") shouldBe Num.ZERO
        (Calculator().evaluate("0/0") is CalcResult.Err) shouldBe true
        (Calculator().evaluate("(2*0)/0") is CalcResult.Err) shouldBe true
        ExactIdentities.same(NameRef("C", SourceSpan(0, 1)), NameRef("c", SourceSpan(2, 3))) shouldBe false
        val c = Calculator()
        c.route("approx(1) → A")
        (c.evaluate("A-A").getOrThrow() as RealValue).num shouldBe Num.ZERO
        val unknownDomain = (c.evaluate("ln(A)-ln(A)").getOrThrow() as RealValue).num as Dec
        unknownDomain.absoluteError shouldBe null
        ((c.route("A=A") as RouteResult.Ok).outcome as Outcome.Verdict).holds shouldBe true
        ((c.route("ln(A)=ln(A)") as RouteResult.Err).error is EvalError.Unsupported) shouldBe true
        ((c.route("A=exp(ln(A))") as RouteResult.Err).error is EvalError.Unsupported) shouldBe true
    }

    "inverse pairs retain exact input in every angle mode without rounding guesses" {
        for (angle in AngleMode.entries) {
            number("tan(atan(25))", angle) shouldBe Num.of(25)
            number("sin(asin(0.3))", angle) shouldBe Num.of(3, 10)
            number("cos(acos(0.02))", angle) shouldBe Num.of(1, 50)
        }
        number("exp(ln(5))") shouldBe Num.of(5)
        number("e^ln(10)") shouldBe Num.TEN
        number("atanh(tanh(60))") shouldBe Num.of(60)
        number("cbrt(2)^3") shouldBe Num.TWO
        number("ln(125)/ln(5)") shouldBe Num.of(3)
    }

    "principal inverse ranges and original domains remain checked" {
        number("asin(sin(150))") shouldBe Num.of(30)
        (number("acos(cos(-1))").signum() > 0) shouldBe true
        (Calculator().evaluate("exp(ln(0))") is CalcResult.Err) shouldBe true
        (Calculator().evaluate("sin(asin(2))") is CalcResult.Err) shouldBe true
        (Calculator().evaluate("sin(asin(approx(1,0.1)))") is CalcResult.Err) shouldBe true
    }

    "standard atan2 angles and written trigonometric identities are exact" {
        number("atan2(1,1)") shouldBe Num.of(45)
        number("atan2(1,0)") shouldBe Num.of(90)
        number("atan2(0,-1)") shouldBe Num.of(180)
        number("sin(20)^2+cos(20)^2") shouldBe Num.ONE
        number("cosh(15)^2-sinh(15)^2") shouldBe Num.ONE
        number("asin(0.3)+acos(0.3)") shouldBe Num.of(90)
        val session = Calculator()
        session.route("approx(0) → A")
        val unknown = (session.evaluate("asin(A)+acos(A)").getOrThrow() as RealValue).num as Dec
        unknown.absoluteError shouldBe null
        session.route("approx(0,0.1) → A")
        (session.evaluate("asin(A)+acos(A)").getOrThrow() as RealValue).num shouldBe Num.of(90)
    }

    "rounding uses one full-precision evaluation but does not invent stored accuracy" {
        number("floor(tanh(50))") shouldBe Num.ZERO
        (Calculator().evaluate("floor(tanh(60))") is CalcResult.Err) shouldBe true
        val c = Calculator()
        c.route("tanh(50) → A")
        (c.evaluate("floor(A)") is CalcResult.Err) shouldBe true
    }

    "nested cancellation reuses completed nodes and an evaluator refreshes its bindings" {
        var expression = "1"
        repeat(25) { expression = "($expression+sqrt(2))-sqrt(2)" }
        val started = System.nanoTime()
        number(expression) shouldBe Num.ONE
        ((System.nanoTime() - started) < 5_000_000_000L) shouldBe true
        val bindings = mutableMapOf("x" to Num.ONE)
        val evaluator = Evaluator(Settings(), Environment(), bindings)
        val parsed = Parser.parse("x+1").getOrThrow()
        (evaluator.eval(parsed).getOrThrow() as RealValue).num shouldBe Num.TWO
        bindings["x"] = Num.of(5)
        (evaluator.eval(parsed).getOrThrow() as RealValue).num shouldBe Num.of(6)
    }
})
