package com.vaibhav.calc.android.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Pins the role→style wiring in [CalcType] to the handoff's §1.3 table and §2.2
 * chip specs. [androidx.compose.ui.text.TextStyle] is plain data, so this runs
 * headless — the token spec pins the numbers, this pins that the ROLES actually
 * carry them, including the "mandatory on every numeric surface" font features.
 */
class CalcTypeSpec : StringSpec({

    val numericSurfaces = mapOf(
        "Entry" to CalcType.Entry,
        "Result" to CalcType.Result,
        "Approx" to CalcType.Approx,
        "KeyNum" to CalcType.KeyNum,
        "KeyOp" to CalcType.KeyOp,
        "KeyUtil" to CalcType.KeyUtil,
        "KeyEq" to CalcType.KeyEq,
        "UtilityChip" to CalcType.UtilityChip,
        "SdChip" to CalcType.SdChip,
    )

    "every numeric surface is JetBrains Mono with tnum + slashed zero (§1.3)" {
        numericSurfaces.forEach { (name, style: TextStyle) ->
            withClue(name) {
                style.fontFamily shouldBe CalcFonts.JetBrainsMono
                style.fontFeatureSettings shouldBe NUMERIC_FONT_FEATURES
            }
        }
    }

    "the two Plex Sans keys — fn and ⌫ — are deliberately not mono (§1.3)" {
        CalcType.KeyFn.fontFamily shouldBe CalcFonts.PlexSans
        CalcType.KeyFn.fontWeight shouldBe FontWeight.SemiBold
        CalcType.KeyFn.fontSize shouldBe CalcTokens.Type.keyFnSize
        CalcType.KeyDel.fontFamily shouldBe CalcFonts.PlexSans
        CalcType.KeyDel.fontWeight shouldBe FontWeight.SemiBold
        CalcType.KeyDel.fontSize shouldBe CalcTokens.Type.keyUtilSize // ⌫ is 19px
    }

    "entry: 19/500, line-height 1.2 (§1.3 / §2.2)" {
        CalcType.Entry.fontSize shouldBe CalcTokens.Type.entrySize
        CalcType.Entry.fontWeight shouldBe FontWeight.Medium
        CalcType.Entry.lineHeight shouldBe 1.2.em
    }

    "result: 33/600, line-height 1.08, ls -.01em (§1.3 / §2.2)" {
        CalcType.Result.fontSize shouldBe CalcTokens.Type.resultSize
        CalcType.Result.fontWeight shouldBe FontWeight.SemiBold
        CalcType.Result.lineHeight shouldBe 1.08.em
        CalcType.Result.letterSpacing shouldBe (-0.01).em
    }

    "approx: 13/400 (§2.2)" {
        CalcType.Approx.fontSize shouldBe CalcTokens.Type.approxSize
        CalcType.Approx.fontWeight shouldBe FontWeight.Normal
    }

    "key faces carry the per-kind table (§2.1)" {
        CalcType.KeyNum.fontSize shouldBe CalcTokens.Type.keyNumSize
        CalcType.KeyNum.fontWeight shouldBe FontWeight.Medium
        CalcType.KeyOp.fontSize shouldBe CalcTokens.Type.keyOpSize
        CalcType.KeyOp.fontWeight shouldBe FontWeight.SemiBold
        CalcType.KeyUtil.fontSize shouldBe CalcTokens.Type.keyUtilSize
        CalcType.KeyUtil.fontWeight shouldBe FontWeight.SemiBold
        CalcType.KeyEq.fontSize shouldBe CalcTokens.Type.keyEqSize
        CalcType.KeyEq.fontWeight shouldBe FontWeight.Bold
    }

    "utility chip: 600 12 JBM; S⇔D: 600 10 JBM ls .06em (§2.2)" {
        CalcType.UtilityChip.fontSize shouldBe CalcTokens.Type.chipSize
        CalcType.UtilityChip.fontWeight shouldBe FontWeight.SemiBold
        CalcType.SdChip.fontSize shouldBe 10.sp
        CalcType.SdChip.fontWeight shouldBe FontWeight.SemiBold
        CalcType.SdChip.letterSpacing shouldBe 0.06.em
    }
})
