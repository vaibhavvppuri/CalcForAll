package com.vaibhav.calc.android

import com.vaibhav.calc.core.eval.AngleMode
import com.vaibhav.calc.core.eval.Settings
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ToolReplacementSpec : StringSpec({
    "oversize tool insertion retains the old expression result and angle with its rejection reason" {
        val session = CalcSession()
        session.replaceText("7+8")
        val before = session.commit()
        val result = session.replaceToolText("1".repeat(CalcSession.MAX_ENTRY_CHARS + 1),
            AngleMode.RAD, "Using radians.")
        result.accepted shouldBe false
        result.state.editor shouldBe before.editor
        result.state.exact shouldBe before.exact
        result.state.entries shouldBe before.entries
        result.state.angle shouldBe AngleMode.DEG
        result.state.notice!! shouldContain "too long"
    }
    "a real symbolic answer beyond the entry nesting limit cannot become a false success" {
        val source = "sin(".repeat(65) + "x" + ")".repeat(65)
        val tool = ToolCalculator.calculate("Algebra & calculus", "Simplify", source, "x", "", Settings())
        tool.error shouldBe false
        val session = CalcSession()
        session.replaceText("5")
        val result = session.replaceToolText(tool.insert!!, tool.insertAngle, tool.insertNotice)
        result.accepted shouldBe false
        result.state.entryText shouldBe "5"
        result.state.angle shouldBe AngleMode.DEG
        result.state.notice!! shouldContain "nested too deeply"
    }
    "accepted tool insertion applies radians and its success notice together" {
        val session = CalcSession()
        session.replaceText("5")
        val result = session.replaceToolText("cos(x)", AngleMode.RAD, "Using radians.")
        result.accepted shouldBe true
        result.state.entryText shouldBe "cos(x)"
        result.state.angle shouldBe AngleMode.RAD
        result.state.notice shouldBe "Using radians."
    }
    "replacing a valid expression with the same text is still an accepted edit" {
        val session = CalcSession()
        session.replaceText("1+2")
        session.commit()
        val result = session.replaceToolText("1+2", notice = "Expression reused.")
        result.accepted shouldBe true
        result.state.entryText shouldBe "1+2"
        result.state.committed shouldBe false
        result.state.exact shouldBe null
        result.state.notice shouldBe "Expression reused."
    }
})
