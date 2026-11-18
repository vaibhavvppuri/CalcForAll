package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Rational
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.math.BigDecimal
import java.time.LocalDate

class MoreToolsUncertaintySpec : StringSpec({
    fun tool(name: String, operation: String, vararg values: String) =
        MoreTools.calculate(name, operation, values.toList(), Settings(), LocalDate.of(2026, 11, 13))
    fun inserted(answer: ToolAnswer) = ((Calculator().evaluate(checkNotNull(answer.insert)) as CalcResult.Ok).value as RealValue).num

    "rounded finance estimates stay approximate when inserted" {
        val answer = tool("Money", "Loan EMI", "1000000", "8.5", "240")
        answer.error shouldBe false
        val value = inserted(answer) as Dec
        value.value.compareTo(BigDecimal("8678.23")) shouldBe 0
        value.absoluteError shouldBe null
    }

    "rounded exact fractions carry a bound covering the original amount" {
        val answer = tool("Money", "Split bill", "1", "0", "3")
        answer.error shouldBe false
        val value = inserted(answer) as Dec
        value.value.compareTo(BigDecimal("0.33")) shouldBe 0
        val error = checkNotNull(value.absoluteError)
        // Avoid an approximate 1/3 reference: the upper endpoint multiplied by 3 must reach 1.
        (value.value.add(error).multiply(BigDecimal(3)) >= BigDecimal.ONE) shouldBe true
        (value.value.subtract(error).multiply(BigDecimal(3)) <= BigDecimal.ONE) shouldBe true
        (error < BigDecimal("0.003334")) shouldBe true
    }

    "bounded business amounts retain input error and cent rounding" {
        val answer = tool("Business", "Margin and markup", "1", "approx(3.004,0.1)")
        answer.error shouldBe false
        val value = inserted(answer) as Dec
        value.value.compareTo(BigDecimal("2.00")) shouldBe 0
        val error = checkNotNull(value.absoluteError)
        (error >= BigDecimal("0.104")) shouldBe true
        (error < BigDecimal("0.104001")) shouldBe true
    }

    "break-even refuses uncertainty crossing a whole unit instead of agreeing rounded probes" {
        tool("Business", "Break-even", "approx(305,100)", "10", "0").error shouldBe true
        val determinate = tool("Business", "Break-even", "approx(305,1)", "10", "0")
        determinate.error shouldBe false
        (inserted(determinate) as Rational).num.toString() shouldBe "31"
    }

    "uncertain geometric ratio one cannot use the exact constant-sequence shortcut" {
        tool("Sequences", "Geometric", "2", "approx(1,0.1)", "3").error shouldBe true
        val known = tool("Sequences", "Geometric", "2", "1", "3")
        known.error shouldBe false
        (inserted(known) as Rational).num.toString() shouldBe "6"
    }

    "exact cent amounts remain exact after transfer" {
        val answer = tool("Money", "Add GST", "1000", "18")
        answer.error shouldBe false
        (inserted(answer) as Rational).num.toString() shouldBe "1180"
    }
    "ratio lists keep a reusable estimate's comma inside its function call" {
        val answer = tool("Everyday", "Share in a ratio", "9", "approx(1,0),2")
        answer.error shouldBe false
        answer.text.contains("Part 1") shouldBe true
        answer.text.contains("Part 2") shouldBe true
    }

    "geometry proves positive dimensions while retaining bounded valid measurements" {
        tool("Geometry", "Circle", "approx(1,2)").error shouldBe true
        tool("Geometry", "Rectangle", "approx(2)", "3").error shouldBe true
        val rectangle = tool("Geometry", "Rectangle", "approx(2,0.1)", "3")
        rectangle.error shouldBe false
        val area = inserted(rectangle) as Dec
        area.value.compareTo(BigDecimal("6")) shouldBe 0
        val error = checkNotNull(area.absoluteError)
        (area.value.subtract(error) <= BigDecimal("5.7")) shouldBe true
        (area.value.add(error) >= BigDecimal("6.3")) shouldBe true
        (error < BigDecimal("0.300001")) shouldBe true
    }

    "triangle inequalities must hold throughout uncertain side lengths" {
        tool("Geometry", "Triangle from 3 sides", "3", "4", "approx(5,3)").error shouldBe true
        val triangle = tool("Geometry", "Triangle from 3 sides", "3", "4", "approx(5,0.1)")
        triangle.error shouldBe false
        val area = inserted(triangle) as Dec
        area.value.compareTo(BigDecimal("6")) shouldBe 0
        (checkNotNull(area.absoluteError) > BigDecimal.ZERO) shouldBe true
    }

    "business domain guards reject intervals crossing cost and margin boundaries" {
        tool("Business", "Price for a margin", "80", "approx(90,20)").error shouldBe true
        tool("Business", "Break-even", "approx(0,0.1)", "10", "0").error shouldBe true
        tool("Business", "Break-even", "100", "approx(11,2)", "10").error shouldBe true
        val price = tool("Business", "Price for a margin", "80", "approx(20,1)")
        price.error shouldBe false
        val amount = inserted(price) as Dec
        amount.value.compareTo(BigDecimal("100")) shouldBe 0
        (checkNotNull(amount.absoluteError) > BigDecimal.ZERO) shouldBe true
    }

    "an infinite geometric sum requires the entire ratio interval inside minus one and one" {
        tool("Sequences", "Geometric", "1", "approx(0.5,1)", "3").error shouldBe true
        val unknown = tool("Sequences", "Geometric", "1", "approx(0.5)", "3")
        unknown.error shouldBe false // The finite nominal sum is usable with unknown accuracy.
        unknown.text shouldNotContain "Sum to infinity:"
        unknown.text shouldContain "convergence is not established"
        val unknownSum = inserted(unknown) as Dec
        unknownSum.value.compareTo(BigDecimal("1.75")) shouldBe 0
        unknownSum.absoluteError shouldBe null
        val convergent = tool("Sequences", "Geometric", "1", "approx(0.5,0.1)", "3")
        convergent.error shouldBe false
        convergent.text shouldContain "Sum to infinity:"
        val finiteSum = inserted(convergent) as Dec
        finiteSum.value.compareTo(BigDecimal("1.75")) shouldBe 0
        (checkNotNull(finiteSum.absoluteError) > BigDecimal.ZERO) shouldBe true
    }

})
