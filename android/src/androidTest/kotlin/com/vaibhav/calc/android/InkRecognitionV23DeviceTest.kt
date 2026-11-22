package com.vaibhav.calc.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.num.Num
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Actual Android geometry execution with synthetic ink; not a physical-stylus accuracy claim. */
@RunWith(AndroidJUnit4::class)
class InkRecognitionV23DeviceTest {
    private fun path(vararg xy: Int) = xy.toList().chunked(2).map { InkPoint(it[0].toFloat(), it[1].toFloat()) }
    private fun value(expression: String) = ((Calculator().evaluate(expression) as CalcResult.Ok).value as RealValue).num

    @Test fun separateRootAndDottedDivisionKeepTheirGeometryAndIncompleteRootRemainsReviewOnly() {
        val root = listOf(path(0, 52, 13, 69, 20, 75, 43, 2), path(47, 3, 73, 2, 122, 4))
        val loop = (0..28).map { i -> InkPoint((84 + 14 * cos(2 * PI * i / 28)).toFloat(),
            (37 + 17 * sin(2 * PI * i / 28)).toFloat()) }
        val nine = listOf(loop, path(98, 35, 96, 60, 90, 75, 72, 76))
        val ink = root + nine + listOf(path(142, 45, 188, 46), path(165, 24, 164, 70), path(213, 18, 214, 78))
        val reading = MathInkRecognizer.readDetailed(ink.reversed().map { it.reversed() })
        assertTrue(reading.layout.toString(), reading.layout is MathLayoutResult.Draft)
        val expression = (reading.layout as MathLayoutResult.Draft).expression
        assertEquals("sqrt(9)+1", expression)
        assertEquals(Num.of(4), value(expression))
        assertEquals(4, reading.tokens.size)
        assertEquals("√", reading.tokens.first().text)
        assertTrue(reading.tokens.first().uncertain)

        val divide = listOf(path(31, 40, 61, 41, 92, 39), path(61, 17, 62, 18), path(60, 63))
        assertEquals("÷", InkRecognizer.recognize(divide).first().symbol)
        val line = InkLineRecognizer.recognize(listOf(path(0, 0, 1, 80)) + divide + listOf(path(122, 0, 123, 80)))
        assertTrue(line.toString(), line is InkLineResult.Draft)
        assertEquals("1÷1", (line as InkLineResult.Draft).text)
        assertEquals(Num.ONE, value(line.text))

        val incomplete = MathInkRecognizer.readDetailed(root)
        assertTrue(incomplete.layout.toString(), incomplete.layout is MathLayoutResult.Rejected)
        assertEquals(listOf("√"), incomplete.tokens.map { it.text })
        val malformed = MathInkRecognizer.readDetailed(ink + listOf(listOf(InkPoint(Float.NaN, 1f))))
        assertTrue(malformed.layout is MathLayoutResult.Rejected)
        assertTrue(malformed.tokens.isEmpty())
    }
}
