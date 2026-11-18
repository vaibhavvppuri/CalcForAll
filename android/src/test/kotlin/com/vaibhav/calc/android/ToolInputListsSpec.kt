package com.vaibhav.calc.android

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.Dec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class ToolInputListsSpec : StringSpec({
    "function commas and spaces do not split a reusable bounded estimate" {
        ToolInputLists.split("approx(2, 0.1), root(16,2); 3", setOf(',', ';'), whitespace = true) shouldBe
            listOf("approx(2, 0.1)", "root(16,2)", "3")
        ToolInputLists.split("1,,2", setOf(','), keepEmpty = true) shouldBe listOf("1", "", "2")
    }
    "matrix result reuse preserves its numerical uncertainty" {
        val answer = ToolCalculator.calculate("Matrices", "Determinant", "approx(2,0.1),0;0,1", "", "", Settings())
        answer.error shouldBe false
        val value = Calculator().evaluate(answer.insert!!).getOrThrow().shouldBeInstanceOf<RealValue>().num.shouldBeInstanceOf<Dec>()
        value.value.compareTo(BigDecimal(2)) shouldBe 0
        value.absoluteError!!.compareTo(BigDecimal("0.1")) shouldBe 0
    }
    "unbalanced or excessive list input is refused rather than partially parsed" {
        shouldThrow<IllegalArgumentException> { ToolInputLists.split("1,approx(2,0.1", setOf(',')) }
        shouldThrow<IllegalArgumentException> { ToolInputLists.split("1),2", setOf(',')) }
        shouldThrow<IllegalArgumentException> { ToolInputLists.split((1..1001).joinToString(","), setOf(',')) }
        shouldThrow<IllegalArgumentException> { ToolInputLists.split("1".repeat(8193), setOf(',')) }
    }
})
