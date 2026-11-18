package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.graph.Sample
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ToolCalculatorSpec : StringSpec({
    fun tool(name: String, operation: String, a: String, b: String = "", extra: String = "", settings: Settings = Settings()) =
        ToolCalculator.calculate(name, operation, a, b, extra, settings)

    "unit insertion preserves the exact value despite rounded localised display settings" {
        val result = tool("Units", "Convert", "1/3", "m", "cm",
            Settings(displayPrecision = 6, exactForm = ExactForm.DECIMAL, grouping = GroupingMode.INDIAN, numerals = NumeralSystem.DEVANAGARI))
        result.error shouldBe false
        result.insert shouldBe "((100/3) cm)"
        val value = (Calculator().evaluate(result.insert!!) as CalcResult.Ok).value as QuantityValue
        value.q.value shouldBe Num.of(100, 3)
        value.q.unit.name shouldBe "cm"
    }

    "a converted quantity is inserted as one value, so a typed power raises all of it" {
        val inserted = tool("Units", "Convert", "10", "km", "m").insert!!
        inserted shouldBe "((10000) m)"
        val squared = (Calculator().evaluate("$inserted^2") as CalcResult.Ok).value as QuantityValue
        squared.q.toBase() shouldBe Num.of(100_000_000)
    }

    "temperature conversion and dimensional mismatch surface usable outcomes" {
        tool("Units", "Convert", "32", "degF", "degC").insert shouldBe "((0) degC)"
        val wrong = tool("Units", "Convert", "1", "m", "kg")
        wrong.error shouldBe true
        wrong.insert shouldBe null
        wrong.text.isNotBlank() shouldBe true
    }

    "matrix determinant preserves fractions and a rectangular product has the expected rows" {
        val determinant = tool("Matrices", "Determinant", "1/3, 0; 0, 2")
        determinant.error shouldBe false
        determinant.insert shouldBe "(2/3)"
        val product = tool("Matrices", "Multiply", "1,2,3;4,5,6", "1;2;3")
        product.error shouldBe false
        product.text.lines() shouldBe listOf("[ 14 ]", "[ 32 ]")
    }

    "matrix errors are shown without an insertion payload" {
        for (result in listOf(
            tool("Matrices", "Inverse", "1,2;2,4"),
            tool("Matrices", "Transpose", "1,2;3"),
            tool("Matrices", "Transpose", "1;2;3;4;5;6;7"),
            tool("Matrices", "Add", "1,2", "1;2"),
        )) {
            result.error shouldBe true
            result.insert shouldBe null
            result.text.isNotBlank() shouldBe true
        }
    }

    "statistics distinguish unavailable sample variance from zero population variance" {
        val result = tool("Statistics", "Summary", "5")
        result.error shouldBe false
        result.text shouldContain "Population variance: 0"
        result.text shouldContain "Sample variance: undefined (n = 1)"
        val multiple = tool("Statistics", "Summary", "1, 2\n3")
        multiple.text shouldContain "Mean: 2"
        multiple.text shouldContain "Population variance: 2/3"
        multiple.text shouldContain "Sample variance: 1"
    }

    "regression uses paired datasets and refuses a mismatched pair count" {
        val result = tool("Statistics", "Linear regression", "0 1 2", "1 3 5")
        result.error shouldBe false
        result.text shouldContain "y = (2)x + (1)"
        result.text shouldContain "r² = 1"
        tool("Statistics", "Linear regression", "0 1 2", "1 3").error shouldBe true
        val constant = tool("Statistics", "Linear regression", "0 1 2", "5 5 5")
        constant.error shouldBe false
        constant.text shouldContain "r = undefined (constant Y)"
        constant.text shouldContain "r² = undefined (constant Y)"
    }

    "base conversion does not round integers beyond the IEEE double range" {
        val result = tool("Number bases", "Convert", "9007199254740993", "10", "16")
        result.error shouldBe false
        result.text shouldContain "Base 16: 20000000000001"
        result.insert shouldBe "9007199254740993"
        tool("Number bases", "Convert", "102", "2", "10").error shouldBe true
        tool("Number bases", "Convert", "12", "1", "10").error shouldBe true
    }

    "algebra insertion is parseable and calculus qualifications remain visible" {
        val derivative = tool("Algebra & calculus", "Differentiate", "x^3", "x")
        derivative.error shouldBe false
        derivative.text shouldContain "Calculus uses radians."
        val function = (Calculator().plot(derivative.insert!!) as CalcResult.Ok).value
        (function.at(Num.of(2)) as Sample.Defined).y shouldBe Num.of(12)
        val integral = tool("Algebra & calculus", "Integrate", "2*x", "x")
        integral.error shouldBe false
        integral.text shouldContain "+ C"
        integral.insert!!.contains("+ C") shouldBe false
    }

    "unsupported inputs return an explicit error rather than a plausible answer" {
        for (result in listOf(
            tool("unknown", "Convert", "1"),
            tool("Statistics", "Summary", ""),
            tool("Units", "Convert", "sqrt(-1)", "m", "cm"),
            tool("Algebra & calculus", "Differentiate", "x^2", "q"),
            tool("Statistics", "Summary", List(1001) { "1" }.joinToString(",")),
            tool("Units", "Convert", "1".repeat(8193), "m", "cm"),
        )) {
            result.error shouldBe true
            result.insert shouldBe null
            result.text.isNotBlank() shouldBe true
        }
    }

    "definite integration remains approximate and uses radians independently of scientific angle mode" {
        val result = tool("Algebra & calculus", "Definite integral", "sin(x)", "x", "0;pi",
            Settings(angle = AngleMode.DEG))
        result.error shouldBe false
        result.text shouldContain "≈"
        result.text shouldContain "Estimated error:"
        result.text shouldContain "Calculus uses radians."
        val value = (Calculator().evaluate(result.insert!!) as CalcResult.Ok).value as RealValue
        ((value.num.toBigDecimal(Num.MC) - java.math.BigDecimal("2")).abs() < java.math.BigDecimal("1e-8")) shouldBe true
    }

    "definite integration rejects singular domains and missing limits without an insertion payload" {
        for (extra in listOf("-1;1", "0", "0;1;2")) {
            val result = tool("Algebra & calculus", "Definite integral", "1/x", "x", extra)
            result.error shouldBe true
            result.insert shouldBe null
            result.text.isNotBlank() shouldBe true
        }
    }
})
