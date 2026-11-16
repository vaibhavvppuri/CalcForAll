package com.vaibhav.calc.android

import com.vaibhav.calc.core.eval.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ClipboardProvenanceSpec : StringSpec({
    "copying an approximate scalar complex component or quantity retains its marker on reuse" {
        for(expression in listOf("sin(1)", "sin(1)+2i", "sin(1) m")) {
            val first = CalcSession()
            first.updateSettings(first.settings.copy(angle = AngleMode.RAD, computePrecision = 50))
            first.replaceText(expression)
            first.commit().approximate shouldBe true
            val copied = first.copyResult()
            copied shouldContain "approx("
            val second = CalcSession()
            second.updateSettings(second.settings.copy(computePrecision = 50))
            second.replaceText(copied)
            second.commit().error shouldBe null
            second.ui.approximate shouldBe true
            second.copyResult() shouldBe copied
        }
    }
})
