package com.vaibhav.calc.android.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Pins every token to DESIGN-HANDOFF-M7-2.md. The expected values below are
 * transcribed from the handoff's §1.1 palette TABLE (not its §1.5 Kotlin block,
 * which [CalcTokens] itself was copied from) — so a transcription slip in either
 * place breaks the agreement and fails here.
 */
class CalcTokensSpec : StringSpec({

    fun CalcTokens.Palette.rows() = listOf(
        paper, sheet, sheet2, sheetEdge, ink, ink2, ink3, grid, grid2,
        amber, amber2, amberSoft, blue, blue2, blueSoft, key, keyEdge, keyPress, keyInk,
    )

    "light palette matches §1.1" {
        CalcTokens.Light.rows() shouldBe listOf(
            Color(0xFFEDF0EA), Color(0xFFF8FAF5), Color(0xFFFFFFFF), Color(0xFFE0E6DB),
            Color(0xFF182338), Color(0xFF59617A), Color(0xFF98A0B1),
            Color(0xFFDBE5DE), Color(0xFFC6D5CB),
            Color(0xFFDA8618), Color(0xFF9C5C05), Color(0xFFFBEFD8),
            Color(0xFF2F6DB0), Color(0xFF194E86), Color(0xFFE4EDF6),
            Color(0xFFFFFFFF), Color(0xFFE5EAE0), Color(0xFFEDF1E9), Color(0xFF1B2740),
        )
        CalcTokens.Light.shadowRgb shouldBe Triple(30, 45, 40)
    }

    "dark palette matches §1.1" {
        CalcTokens.Dark.rows() shouldBe listOf(
            Color(0xFF0E141E), Color(0xFF161D2A), Color(0xFF1C2431), Color(0xFF2A3341),
            Color(0xFFE9EDEF), Color(0xFF9AA4B4), Color(0xFF616B7D),
            Color(0xFF1B2330), Color(0xFF2A3547),
            Color(0xFFF0A63E), Color(0xFFD98A1E), Color(0xFF2C2415),
            Color(0xFF5BA1E1), Color(0xFF8CC0F0), Color(0xFF16273A),
            Color(0xFF1C2431), Color(0xFF2B3546), Color(0xFF242E3E), Color(0xFFE9EDEF),
        )
        CalcTokens.Dark.shadowRgb shouldBe Triple(0, 0, 0)
    }

    "amoled palette matches §1.1 — true black paper, brightened amber, dark's blues" {
        CalcTokens.Amoled.rows() shouldBe listOf(
            Color(0xFF000000), Color(0xFF080B10), Color(0xFF0D1119), Color(0xFF1B222E),
            Color(0xFFEEF1F3), Color(0xFF8B95A5), Color(0xFF525C6E),
            Color(0xFF12181F), Color(0xFF212B39),
            Color(0xFFF2A93F), Color(0xFFD98A1E), Color(0xFF211A0E),
            Color(0xFF5BA1E1), Color(0xFF8CC0F0), Color(0xFF0C1A29),
            Color(0xFF0D1119), Color(0xFF1D2531), Color(0xFF161C27), Color(0xFFEEF1F3),
        )
        CalcTokens.Amoled.shadowRgb shouldBe Triple(0, 0, 0)
        // §3.1: AMOLED keeps Dark's blues verbatim
        CalcTokens.Amoled.blue shouldBe CalcTokens.Dark.blue
        CalcTokens.Amoled.blue2 shouldBe CalcTokens.Dark.blue2
    }

    "component geometry matches §1.5/§2.1 — key height fixed, width the only scaler" {
        CalcTokens.Dim.keyHeight shouldBe 56.dp
        CalcTokens.Dim.keyRadius shouldBe 13.dp
        CalcTokens.Dim.keypadColumns shouldBe 4
        CalcTokens.Dim.chipHeight shouldBe 34.dp
        CalcTokens.Dim.drawerButtonHeight shouldBe 40.dp
        CalcTokens.Dim.tapeBadge shouldBe 20.dp
        CalcTokens.Dim.tapeBadgeRadius shouldBe 6.dp
        CalcTokens.Dim.entryMinHeight shouldBe 28.dp
        CalcTokens.Dim.resultMinHeight shouldBe 44.dp
        CalcTokens.Dim.flickThresholdPx shouldBe 14f
        CalcTokens.Dim.pressTranslateY shouldBe 1.5f
        CalcTokens.Dim.backspaceHoldDelayMs shouldBe 380L
        CalcTokens.Dim.backspaceRepeatMs shouldBe 70L
        CalcTokens.Dim.revealDurationMs shouldBe 1800L
    }

    "spacing sits on the 8dp minor / 40dp major grid (§1.4)" {
        CalcTokens.Space.minor shouldBe 8.dp
        CalcTokens.Space.major shouldBe 40.dp
        CalcTokens.Space.keypadGap shouldBe 8.dp
        CalcTokens.Space.chipGap shouldBe 6.dp
        CalcTokens.Space.drawerGap shouldBe 7.dp
        CalcTokens.Space.tapeRowGap shouldBe 11.dp
    }

    "type scale matches §1.3 — sizes and the weights the handoff names" {
        CalcTokens.Type.displaySize shouldBe 34.sp
        CalcTokens.Type.displayWeight shouldBe FontWeight.Bold
        CalcTokens.Type.titleSize shouldBe 26.sp
        CalcTokens.Type.bodySize shouldBe 15.sp
        CalcTokens.Type.labelSize shouldBe 13.sp
        CalcTokens.Type.microSize shouldBe 11.sp
        CalcTokens.Type.numeralSize shouldBe 27.sp
        CalcTokens.Type.entrySize shouldBe 19.sp
        CalcTokens.Type.entryWeight shouldBe FontWeight.Medium
        CalcTokens.Type.resultSize shouldBe 33.sp
        CalcTokens.Type.resultWeight shouldBe FontWeight.SemiBold
        CalcTokens.Type.approxSize shouldBe 13.sp
        CalcTokens.Type.keyNumSize shouldBe 22.sp
        CalcTokens.Type.keyNumWeight shouldBe FontWeight.Medium
        CalcTokens.Type.keyOpSize shouldBe 23.sp
        CalcTokens.Type.keyOpWeight shouldBe FontWeight.SemiBold
        CalcTokens.Type.keyUtilSize shouldBe 19.sp
        CalcTokens.Type.keyFnSize shouldBe 15.sp
        CalcTokens.Type.keyEqSize shouldBe 25.sp
        CalcTokens.Type.keyEqWeight shouldBe FontWeight.Bold
        CalcTokens.Type.chipSize shouldBe 12.sp
    }

    "the numeric font features are the handoff's exact pair — tnum + slashed zero" {
        NUMERIC_FONT_FEATURES shouldBe "'tnum' 1, 'zero' 1"
    }

    "plot-curve tokens are the Okabe-Ito five with the table's dashes (§1.2)" {
        CalcTokens.Curves.map { it.name } shouldBe
            listOf("primary", "secondary", "tertiary", "quaternary", "quinary")
        CalcTokens.Curves.map { it.color } shouldBe listOf(
            Color(0xFF0072B2), Color(0xFFE69F00), Color(0xFF009E73),
            Color(0xFFCC79A7), Color(0xFFD55E00),
        )
        // The token table is authoritative: secondary is 8 5, not the demo's 7 5.
        CalcTokens.Curves.map { it.dash } shouldBe listOf(
            emptyList(), listOf(8f, 5f), listOf(2f, 4f), listOf(10f, 4f, 2f, 4f), listOf(1f, 5f),
        )
    }
})
