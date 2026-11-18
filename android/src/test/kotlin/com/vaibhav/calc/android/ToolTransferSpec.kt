package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.num.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class ToolTransferSpec : StringSpec({
    fun tool(name: String, operation: String, a: String, b: String = "", extra: String = "", settings: Settings = Settings()) =
        ToolCalculator.calculate(name, operation, a, b, extra, settings)
    fun value(input: String): Num = Calculator().evaluate(input).getOrThrow().shouldBeInstanceOf<RealValue>().num

    "definite integral bounds use radians even when the calculator is in degrees" {
        val answer = tool("Algebra & calculus", "Definite integral", "1", "x", "0;asin(1)", Settings(angle = AngleMode.DEG))
        answer.error shouldBe false
        val n = value(answer.insert!!).shouldBeInstanceOf<Dec>()
        (n.value.subtract(BigDecimal("1.5707963267948966192313216916398")).abs() < BigDecimal("1e-9")) shouldBe true
        answer.text shouldContain "Bound expressions also use radians."
    }
    "qualified symbolic results cannot lose their domain through automatic insertion" {
        val answer = tool("Algebra & calculus", "Differentiate", "ln(x)", "x")
        answer.error shouldBe false
        answer.insert shouldBe null
        answer.insertAngle shouldBe null
        answer.text shouldContain "x > 0"
        answer.text shouldContain "Automatic insertion is unavailable"
    }
    "unqualified symbolic formulas carry their radians context with the insertion" {
        val answer = tool("Algebra & calculus", "Differentiate", "sin(x)", "x", settings = Settings(angle = AngleMode.DEG))
        answer.error shouldBe false
        answer.insert shouldBe "cos(x)"
        answer.insertAngle shouldBe AngleMode.RAD
        answer.insertNotice!! shouldContain "radians"
    }
    "a reused numerical integral remains approximate through the real calculator session" {
        val answer = tool("Algebra & calculus", "Definite integral", "x^2", "x", "0;1")
        answer.error shouldBe false
        answer.insert!!.startsWith("approx(") shouldBe true
        value(answer.insert!!).shouldBeInstanceOf<Dec>()
        val session = CalcSession()
        session.replaceText(answer.insert!!)
        session.commit().approximate shouldBe true
    }
    "a zero width numerical estimate also retains its approximate marker" {
        val answer = tool("Algebra & calculus", "Definite integral", "x^2", "x", "1;1")
        answer.error shouldBe false
        val estimate = value(answer.insert!!).shouldBeInstanceOf<Dec>()
        estimate.value.compareTo(BigDecimal.ZERO) shouldBe 0
        estimate.absoluteError shouldBe null
        answer.text.startsWith("≈ ") shouldBe true
    }
    "exact unit results retain exact insertion while rounded display is labelled" {
        val answer = tool("Units", "Convert", "1/3", "m", "cm",
            Settings(displayPrecision = 6, exactForm = ExactForm.DECIMAL))
        answer.text shouldContain "≈ 33.3333"
        answer.insert shouldBe "((100/3) cm)"
        val q = Calculator().evaluate(answer.insert!!).getOrThrow().shouldBeInstanceOf<QuantityValue>()
        q.q.value shouldBe Num.of(100, 3)
    }
    "unit matrix and statistics adapters identify approximate values without discarding the marker" {
        val units = tool("Units", "Convert", "approx(1.25)", "m", "cm")
        units.text shouldContain "≈ "
        val q = Calculator().evaluate(units.insert!!).getOrThrow().shouldBeInstanceOf<QuantityValue>()
        q.q.value.shouldBeInstanceOf<Dec>()
        val matrix = tool("Matrices", "Determinant", "approx(1.25,0),0;0,1")
        matrix.text shouldContain "≈ "
        value(matrix.insert!!).shouldBeInstanceOf<Dec>()
        tool("Statistics", "Summary", "approx(1.25,0),2").text shouldContain "Mean: ≈ 1.625"
        // Unknown accuracy cannot establish a nonzero pivot or an ordered data sample.
        tool("Matrices", "Determinant", "approx(1.25),0;0,1").error shouldBe true
        tool("Statistics", "Summary", "approx(1.25),2").error shouldBe true
    }
    "antiderivative reuse explicitly chooses its C equals zero representative" {
        val answer = tool("Algebra & calculus", "Integrate", "2*x", "x")
        answer.insertAngle shouldBe AngleMode.RAD
        answer.insertNotice!! shouldContain "C = 0"
        answer.text shouldContain "+ C"
        answer.insert!!.contains("C") shouldBe false
    }
})
