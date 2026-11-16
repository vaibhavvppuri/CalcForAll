package com.vaibhav.calc.android.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vaibhav.calc.android.R

/**
 * M7-2 Stage A: the design system's composition layer over [CalcTokens].
 *
 * Every value here traces to DESIGN-HANDOFF-M7-2.md — §1.1 palette, §1.3 type,
 * §1.4 spacing/paper grid. Where a style needs a colour, the colour is NOT baked
 * into the [TextStyle]: it comes from [LocalCalcPalette] at the call site, so one
 * set of styles serves all three themes.
 */

/** The design's three themes. Selection UI is a later stage; the palettes are not. */
enum class CalcThemeMode { LIGHT, DARK, AMOLED }

val LocalCalcPalette = staticCompositionLocalOf { CalcTokens.Light }

/**
 * All three families are bundled resources — none is reliably a system face on
 * Android (handoff §1.3). Devanagari is the same Plex superfamily, chosen so Hindi
 * carries "without a second type voice"; it is bundled now, consumed when the
 * numeral setting surfaces (D-031.5, Stage B+).
 */
object CalcFonts {
    val PlexSans = FontFamily(
        Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
        Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
    )
    val PlexSansDevanagari = FontFamily(
        Font(R.font.ibm_plex_sans_devanagari_regular, FontWeight.Normal),
    )
    val JetBrainsMono = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )
}

/** Tabular figures + slashed zero — "mandatory on every numeric surface" (§1.3). */
const val NUMERIC_FONT_FEATURES = "'tnum' 1, 'zero' 1"

/**
 * The type roles as the design uses them (§1.3 table). Sizes and weights come from
 * [CalcTokens.Type] where the handoff's own Kotlin block names them; weights the
 * block omits (util/fn/del/chip = 600) are the §1.3 table's literals.
 */
object CalcType {
    val Entry = TextStyle(
        fontFamily = CalcFonts.JetBrainsMono,
        fontWeight = CalcTokens.Type.entryWeight,
        fontSize = CalcTokens.Type.entrySize,
        lineHeight = 1.2.em,
        fontFeatureSettings = NUMERIC_FONT_FEATURES,
    )
    val Result = TextStyle(
        fontFamily = CalcFonts.JetBrainsMono,
        fontWeight = CalcTokens.Type.resultWeight,
        fontSize = CalcTokens.Type.resultSize,
        lineHeight = 1.08.em,
        letterSpacing = (-0.01).em,
        fontFeatureSettings = NUMERIC_FONT_FEATURES,
    )
    val Approx = TextStyle(
        fontFamily = CalcFonts.JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = CalcTokens.Type.approxSize,
        fontFeatureSettings = NUMERIC_FONT_FEATURES,
    )
    val KeyNum = TextStyle(
        fontFamily = CalcFonts.JetBrainsMono,
        fontWeight = CalcTokens.Type.keyNumWeight,
        fontSize = CalcTokens.Type.keyNumSize,
        lineHeight = 1.em,
        fontFeatureSettings = NUMERIC_FONT_FEATURES,
    )
    val KeyOp = TextStyle(
        fontFamily = CalcFonts.JetBrainsMono,
        fontWeight = CalcTokens.Type.keyOpWeight,
        fontSize = CalcTokens.Type.keyOpSize,
        lineHeight = 1.em,
        fontFeatureSettings = NUMERIC_FONT_FEATURES,
    )
    val KeyUtil = TextStyle(
        fontFamily = CalcFonts.JetBrainsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = CalcTokens.Type.keyUtilSize,
        lineHeight = 1.em,
        fontFeatureSettings = NUMERIC_FONT_FEATURES,
    )
    val KeyFn = TextStyle(
        fontFamily = CalcFonts.PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = CalcTokens.Type.keyFnSize,
        lineHeight = 1.em,
    )
    val KeyDel = TextStyle(
        fontFamily = CalcFonts.PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = CalcTokens.Type.keyUtilSize,
        lineHeight = 1.em,
    )
    val KeyEq = TextStyle(
        fontFamily = CalcFonts.JetBrainsMono,
        fontWeight = CalcTokens.Type.keyEqWeight,
        fontSize = CalcTokens.Type.keyEqSize,
        lineHeight = 1.em,
        fontFeatureSettings = NUMERIC_FONT_FEATURES,
    )
    val UtilityChip = TextStyle(
        fontFamily = CalcFonts.JetBrainsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = CalcTokens.Type.chipSize,
        fontFeatureSettings = NUMERIC_FONT_FEATURES,
    )

    /** `S⇔D`: 600 10px JetBrains Mono, ls .06em (§2.2). 10px has no named token. */
    val SdChip = TextStyle(
        fontFamily = CalcFonts.JetBrainsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 0.06.em,
        fontFeatureSettings = NUMERIC_FONT_FEATURES,
    )
}

/**
 * The paper substrate (§1.4): a real background on the root container — minor
 * gridline every 8dp in `--grid`, major every 40dp in `--grid-2`, on `--paper`.
 * "The layout literally sits on the paper."
 */
fun Modifier.paperGrid(palette: CalcTokens.Palette): Modifier = drawBehind {
    drawRect(palette.paper)
    val stroke = 1.dp.toPx()
    val minor = CalcTokens.Space.minor.toPx()
    val major = CalcTokens.Space.major.toPx()
    var x = minor
    while (x < size.width) {
        drawLine(palette.grid, Offset(x, 0f), Offset(x, size.height), stroke)
        x += minor
    }
    var y = minor
    while (y < size.height) {
        drawLine(palette.grid, Offset(0f, y), Offset(size.width, y), stroke)
        y += minor
    }
    x = major
    while (x < size.width) {
        drawLine(palette.grid2, Offset(x, 0f), Offset(x, size.height), stroke)
        x += major
    }
    y = major
    while (y < size.height) {
        drawLine(palette.grid2, Offset(0f, y), Offset(size.width, y), stroke)
        y += major
    }
}

@Composable
fun CalcTheme(
    mode: CalcThemeMode = if (isSystemInDarkTheme()) CalcThemeMode.DARK else CalcThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val palette = when (mode) {
        CalcThemeMode.LIGHT -> CalcTokens.Light
        CalcThemeMode.DARK -> CalcTokens.Dark
        CalcThemeMode.AMOLED -> CalcTokens.Amoled
    }
    // Material slots mapped from the tokens — plumbing for the Material pieces the
    // shell still uses (the fn drawer, restyled in a later stage), not a second
    // colour system.
    val scheme = if (mode == CalcThemeMode.LIGHT) {
        lightColorScheme(
            primary = palette.blue, onPrimary = Color.White,
            background = palette.paper, onBackground = palette.ink,
            surface = palette.sheet, onSurface = palette.ink,
            surfaceVariant = palette.sheet2, onSurfaceVariant = palette.ink2,
            outline = palette.sheetEdge,
        )
    } else {
        darkColorScheme(
            primary = palette.blue, onPrimary = Color.White,
            background = palette.paper, onBackground = palette.ink,
            surface = palette.sheet, onSurface = palette.ink,
            surfaceVariant = palette.sheet2, onSurfaceVariant = palette.ink2,
            outline = palette.sheetEdge,
        )
    }
    CompositionLocalProvider(LocalCalcPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
