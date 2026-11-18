package com.vaibhav.calc.android

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * Every text field in this app holds mathematics, a unit, a function name or a date — never
 * prose. Left at its defaults, a phone keyboard treats it as prose: on an Android 14 emulator
 * the typed `5 lakh` reached the calculator as `5 leaky`, and the same autocorrect turns
 * `sqrt` into `sort`. Auto-capitalisation turns `pi` into `Pi`, which is not a name the engine
 * knows. So autocorrect and capitalisation are off wherever the user types, and a test
 * (`TypedFieldsSpec`) fails if a text field is added without these options.
 */
internal val MathKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrect = false,
    keyboardType = KeyboardType.Text,
)
