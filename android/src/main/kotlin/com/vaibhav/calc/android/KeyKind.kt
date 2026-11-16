package com.vaibhav.calc.android

import com.vaibhav.calc.core.keypad.Key
import com.vaibhav.calc.core.keypad.KeyAction

/**
 * The design's six per-kind key treatments (DESIGN-HANDOFF-M7-2.md §2.1). Pure
 * Kotlin, no Compose: the kind is derived from what the key DOES, never from
 * where it sits, so the view keeps rendering whatever [com.vaibhav.calc.core.keypad.Keypad]
 * defines and cannot fossilise a layout of its own.
 */
enum class KeyKind { NUM, OP, UTIL, FN, DEL, EQ }

fun keyKind(key: Key): KeyKind = when (val tap = key.tap) {
    KeyAction.Equals -> KeyKind.EQ
    KeyAction.OpenPalette -> KeyKind.FN
    KeyAction.Backspace, KeyAction.BackspaceRepeat -> KeyKind.DEL
    KeyAction.OpenParen, KeyAction.CloseParen, KeyAction.Ans -> KeyKind.UTIL
    is KeyAction.Insert -> if (tap.text in OPERATOR_TEXTS) KeyKind.OP else KeyKind.NUM
    // Structure/function/reference/cursor actions are flick or badge affordances,
    // not tap actions on any shipped pad; NUM is the neutral fallback if one ever is.
    KeyAction.Fraction, KeyAction.Power, KeyAction.Root,
    is KeyAction.Func, is KeyAction.Ref,
    KeyAction.Left, KeyAction.Right,
    -> KeyKind.NUM
}

/** The four operator inserts the design styles as `op` (÷ × − +). */
private val OPERATOR_TEXTS = setOf("+", "-", "*", "/")
