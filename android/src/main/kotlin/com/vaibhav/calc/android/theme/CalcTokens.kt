package com.vaibhav.calc.android.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Design tokens extracted verbatim from "Calculator App.html" (2026-07-28). */
object CalcTokens {

    // ---- palette: three themes -------------------------------------------
    data class Palette(
        val paper: Color, val sheet: Color, val sheet2: Color, val sheetEdge: Color,
        val ink: Color, val ink2: Color, val ink3: Color,
        val grid: Color, val grid2: Color,
        val amber: Color, val amber2: Color, val amberSoft: Color,
        val blue: Color, val blue2: Color, val blueSoft: Color,
        val key: Color, val keyEdge: Color, val keyPress: Color, val keyInk: Color,
        /** RGB triplet for shadow; 0,0,0 means "no shadow" in the dark themes. */
        val shadowRgb: Triple<Int, Int, Int>,
    )

    val Light = Palette(
        paper = Color(0xFFEDF0EA), sheet = Color(0xFFF8FAF5), sheet2 = Color(0xFFFFFFFF),
        sheetEdge = Color(0xFFE0E6DB),
        ink = Color(0xFF182338), ink2 = Color(0xFF59617A), ink3 = Color(0xFF98A0B1),
        grid = Color(0xFFDBE5DE), grid2 = Color(0xFFC6D5CB),
        amber = Color(0xFFDA8618), amber2 = Color(0xFF9C5C05), amberSoft = Color(0xFFFBEFD8),
        blue = Color(0xFF2F6DB0), blue2 = Color(0xFF194E86), blueSoft = Color(0xFFE4EDF6),
        key = Color(0xFFFFFFFF), keyEdge = Color(0xFFE5EAE0), keyPress = Color(0xFFEDF1E9),
        keyInk = Color(0xFF1B2740),
        shadowRgb = Triple(30, 45, 40),
    )

    val Dark = Palette(
        paper = Color(0xFF0E141E), sheet = Color(0xFF161D2A), sheet2 = Color(0xFF1C2431),
        sheetEdge = Color(0xFF2A3341),
        ink = Color(0xFFE9EDEF), ink2 = Color(0xFF9AA4B4), ink3 = Color(0xFF616B7D),
        grid = Color(0xFF1B2330), grid2 = Color(0xFF2A3547),
        amber = Color(0xFFF0A63E), amber2 = Color(0xFFD98A1E), amberSoft = Color(0xFF2C2415),
        blue = Color(0xFF5BA1E1), blue2 = Color(0xFF8CC0F0), blueSoft = Color(0xFF16273A),
        key = Color(0xFF1C2431), keyEdge = Color(0xFF2B3546), keyPress = Color(0xFF242E3E),
        keyInk = Color(0xFFE9EDEF),
        shadowRgb = Triple(0, 0, 0),
    )

    val Amoled = Palette(
        paper = Color(0xFF000000), sheet = Color(0xFF080B10), sheet2 = Color(0xFF0D1119),
        sheetEdge = Color(0xFF1B222E),
        ink = Color(0xFFEEF1F3), ink2 = Color(0xFF8B95A5), ink3 = Color(0xFF525C6E),
        grid = Color(0xFF12181F), grid2 = Color(0xFF212B39),
        amber = Color(0xFFF2A93F), amber2 = Color(0xFFD98A1E), amberSoft = Color(0xFF211A0E),
        blue = Color(0xFF5BA1E1), blue2 = Color(0xFF8CC0F0), blueSoft = Color(0xFF0C1A29),
        key = Color(0xFF0D1119), keyEdge = Color(0xFF1D2531), keyPress = Color(0xFF161C27),
        keyInk = Color(0xFFEEF1F3),
        shadowRgb = Triple(0, 0, 0),
    )

    // ---- plot curves: Okabe-Ito, hue + dash --------------------------------
    data class CurveToken(val name: String, val color: Color, val dash: List<Float>)

    val Curves = listOf(
        CurveToken("primary",    Color(0xFF0072B2), emptyList()),
        CurveToken("secondary",  Color(0xFFE69F00), listOf(8f, 5f)),
        CurveToken("tertiary",   Color(0xFF009E73), listOf(2f, 4f)),
        CurveToken("quaternary", Color(0xFFCC79A7), listOf(10f, 4f, 2f, 4f)),
        CurveToken("quinary",    Color(0xFFD55E00), listOf(1f, 5f)),
    )

    // ---- spacing: 8px minor cell, 40px major ------------------------------
    object Space {
        val minor = 8.dp
        val major = 40.dp
        val keypadGap = 8.dp
        val chipGap = 6.dp
        val drawerGap = 7.dp
        val tapeRowGap = 11.dp
    }

    // ---- type: two families, tabular figures on all numerics --------------
    object Type {
        // family = 'IBM Plex Sans' (UI) / 'JetBrains Mono' (data)
        // NOTE: every numeric surface needs tnum + slashed-zero features.
        val displaySize = 34.sp;  val displayWeight = FontWeight.Bold
        val titleSize   = 26.sp
        val bodySize    = 15.sp
        val labelSize   = 13.sp
        val microSize   = 11.sp
        val numeralSize = 27.sp

        val entrySize   = 19.sp;  val entryWeight  = FontWeight.Medium
        val resultSize  = 33.sp;  val resultWeight = FontWeight.SemiBold
        val approxSize  = 13.sp
        val keyNumSize  = 22.sp;  val keyNumWeight = FontWeight.Medium
        val keyOpSize   = 23.sp;  val keyOpWeight  = FontWeight.SemiBold
        val keyUtilSize = 19.sp
        val keyFnSize   = 15.sp
        val keyEqSize   = 25.sp;  val keyEqWeight  = FontWeight.Bold
        val chipSize    = 12.sp
    }

    // ---- component geometry ------------------------------------------------
    object Dim {
        val keyHeight = 56.dp          // FIXED
        val keyRadius = 13.dp
        val keypadColumns = 4          // each 1fr -> width SCALES
        val chipHeight = 34.dp
        val drawerButtonHeight = 40.dp
        val tapeBadge = 20.dp
        val tapeBadgeRadius = 6.dp
        val entryMinHeight = 28.dp
        val resultMinHeight = 44.dp
        val flickThresholdPx = 14f     // upward dy that selects the flick action
        val pressTranslateY = 1.5f
        val backspaceHoldDelayMs = 380L
        val backspaceRepeatMs = 70L
        val revealDurationMs = 1800L
    }
}
