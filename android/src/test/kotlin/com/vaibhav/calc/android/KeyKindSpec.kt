package com.vaibhav.calc.android

import com.vaibhav.calc.android.theme.CalcTokens
import com.vaibhav.calc.core.keypad.Keypad
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The per-kind styling is DERIVED from the model's actions, never from grid
 * position — so the one thing to prove is that the derivation, applied to core's
 * real layout, reproduces the design's kind grid (DESIGN-HANDOFF-M7-2.md §2.1).
 */
class KeyKindSpec : StringSpec({

    "core's 20 keys classify to the design's kind grid (§2.1)" {
        val expected = listOf(
            listOf(KeyKind.FN, KeyKind.UTIL, KeyKind.UTIL, KeyKind.OP),
            listOf(KeyKind.NUM, KeyKind.NUM, KeyKind.NUM, KeyKind.OP),
            listOf(KeyKind.NUM, KeyKind.NUM, KeyKind.NUM, KeyKind.OP),
            listOf(KeyKind.NUM, KeyKind.NUM, KeyKind.NUM, KeyKind.OP),
            listOf(KeyKind.NUM, KeyKind.NUM, KeyKind.DEL, KeyKind.EQ),
        )
        Keypad.basic().rows.map { row -> row.map { keyKind(it) } } shouldBe expected
    }

    "every row is 4 columns — the grid the handoff fixes and core defines" {
        Keypad.basic().rows.forEach { row ->
            row.size shouldBe CalcTokens.Dim.keypadColumns
        }
    }
})
