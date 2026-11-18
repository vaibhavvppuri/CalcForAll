package com.vaibhav.calc.android

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.num.Num
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoCorrectionDeviceTest {
    @Test fun realPrintedRadicalPixelsOfferAnExplicitCorrectionForTheObservedVReading() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val cases = listOf(
            Triple("03-root-integer.png", listOf(MathToken("V", MathBox(500f, 204f, 580f, 324f)),
                MathToken("1", MathBox(584f, 204f, 632f, 324f)), MathToken("6", MathBox(652f, 204f, 708f, 324f))), Num.of(4)),
            Triple("04-root-decimal.png", listOf(MathToken("V", MathBox(443f, 199f, 529f, 326f)),
                MathToken("1", MathBox(531f, 202f, 587f, 328f)), MathToken(".", MathBox(598f, 204f, 616f, 329f)),
                MathToken("4", MathBox(622f, 205f, 688f, 332f)), MathToken("4", MathBox(694f, 208f, 758f, 335f))), Num.of(6, 5)))
        for ((name, tokens, expected) in cases) {
            val image = assets.open("photo-corpus/$name").use { BitmapFactory.decodeStream(it) }!!
            try {
                val correction = PhotoMathReader.suggestCorrections(image, tokens).single()
                val preview = RecognitionCalculator.preview(correction.expression, Settings(), Environment()) as RecognitionPreview.Answer
                assertEquals(expected, (preview.rendered.value as RealValue).num)
                assertEquals("V", tokens.first().text)
            } finally { image.recycle() }
        }
    }
}
