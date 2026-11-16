package com.vaibhav.calc.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * M7-1: the Compose shell. The activity is deliberately empty of logic — state
 * lives in [CalcSession] (pure Kotlin, headless-testable) and the UI in
 * [CalcApp]. The engine underneath is `core`, which knows nothing about Android.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CalcApp() }
    }
}
