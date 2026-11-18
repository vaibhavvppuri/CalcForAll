package com.vaibhav.calc.android

import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.tape.EntryId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class RecognitionPreviewSpec : StringSpec({
    fun answer(text: String, settings: Settings = Settings(), env: Environment = Environment()) =
        RecognitionCalculator.preview(text, settings, env).shouldBeInstanceOf<RecognitionPreview.Answer>()

    "recognized arithmetic automatically produces an exact preview" {
        answer("(1)/(2)+2^3").rendered.primary shouldBe "17/2"
        answer("sqrt(8)").rendered.primary shouldBe "2√2"
    }
    "equations route to roots rather than treating equals as a store" {
        val result = answer("x^2-5x+6=0")
        result.rendered.primary shouldBe "x = 2, 3"
    }
    "previews use the chosen angle mode" {
        answer("sin(30)").rendered.primary shouldBe "0.5"
        answer("sin(pi/2)", Settings(angle = AngleMode.RAD)).rendered.primary shouldBe "1"
    }
    "preview never changes caller Ans or registers" {
        val env = Environment().apply { setAns(RealValue(Num.of(7))); set("A", RealValue(Num.of(2))) }
        answer("ans+A", env = env).rendered.primary shouldBe "9"
        env.ans() shouldBe RealValue(Num.of(7))
        env.get("A") shouldBe RealValue(Num.of(2))
        RecognitionCalculator.preview("9 -> A", Settings(), env).shouldBeInstanceOf<RecognitionPreview.NeedsEdit>()
        env.get("A") shouldBe RealValue(Num.of(2))
    }
    "empty invalid and oversized input cannot produce stale answers" {
        RecognitionCalculator.preview("", Settings(), Environment()) shouldBe RecognitionPreview.Empty
        listOf("1/0", "1+", "1".repeat(2049), "(".repeat(25) + "1" + ")".repeat(25), (1..140).joinToString("+") { "1" }).forEach {
            RecognitionCalculator.preview(it, Settings(), Environment()).shouldBeInstanceOf<RecognitionPreview.NeedsEdit>()
        }
    }
    "saving reviewed input appends a calculation even while an old row is being edited" {
        val s = CalcSession()
        s.replaceText("2+2"); s.commit(); s.beginEdit(EntryId(1))
        s.saveRecognition("6*7").accepted shouldBe true
        s.ui.entries.size shouldBe 2
        s.ui.exact shouldBe "42"
        s.ui.editingId shouldBe null
        s.reuse(EntryId(1)).entryText shouldBe "2+2"
    }
    "failed review preserves editor history and current register values" {
        val s = CalcSession()
        s.replaceText("7 -> A"); s.commit(); s.replaceText("3+4")
        val entries = s.ui.entries
        val env = s.snapshotEnvironment()
        s.saveRecognition("1/0").accepted shouldBe false
        s.ui.entries shouldBe entries
        s.ui.entryText shouldBe "3+4"
        s.snapshotEnvironment().get("A") shouldBe env.get("A")
        s.saveRecognition("9 -> A").accepted shouldBe false
        s.ui.entries shouldBe entries
    }
})
