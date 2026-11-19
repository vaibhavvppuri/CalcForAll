package com.vaibhav.calc.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Screen recreation must not create another lane for an unfinished calculation.
private val recognitionCalculationLock = Mutex()

/** A result is always keyed to the visible transcription, never left over from prior ink. */
@Composable
fun RecognitionAnswer(expression: String, settings: Settings, environment: Environment,
                      enabled: Boolean = true, saving: Boolean = false, onResolved: () -> Unit = {}, onSave: (String) -> Unit) {
    var preview by remember(expression, settings, environment, enabled) {
        mutableStateOf<RecognitionPreview>(if (expression.isBlank() || !enabled) RecognitionPreview.Empty else RecognitionPreview.Calculating)
    }
    val resolvedCallback by rememberUpdatedState(onResolved)
    LaunchedEffect(preview) {
        if (preview is RecognitionPreview.Answer || preview is RecognitionPreview.NeedsEdit) {
            withFrameNanos { } // Let the result enter the scroll layout before requesting visibility.
            resolvedCallback()
        }
    }
    LaunchedEffect(expression, settings, environment, enabled) {
        if (!enabled || expression.isBlank()) return@LaunchedEffect
        delay(450)
        preview = try {
            // A cancelled calculation finishes before another starts; rapid edits cannot
            // pile up simultaneous expensive computations on a small phone.
            recognitionCalculationLock.withLock { withContext(Dispatchers.Default) { RecognitionCalculator.preview(expression, settings, environment) } }
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { RecognitionPreview.NeedsEdit(e.message ?: "This expression could not be calculated.") }
    }
    when (val current = preview) {
        RecognitionPreview.Empty -> Unit
        RecognitionPreview.Calculating -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
        is RecognitionPreview.NeedsEdit -> Text(current.message, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 12.dp))
        is RecognitionPreview.Answer -> Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("From the expression above", style = MaterialTheme.typography.labelMedium)
                val visible = current.rendered.primary.take(1200)
                Text((if (current.rendered.approximate) "≈ " else "") + visible + if (current.rendered.primary.length > 1200) "…" else "",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 8.dp).semantics { contentDescription = "Recognized calculation result: ${current.rendered.primary}"; liveRegion = LiveRegionMode.Polite })
                if (current.rendered.primary.length > 1200) Text("Showing the first 1,200 characters. Save the reviewed calculation to copy the full result from the calculator.", fontSize = 12.sp, lineHeight = 18.sp)
                current.rendered.decimal?.takeIf { it != current.rendered.primary }?.let {
                    Text("Decimal: $it", fontSize = 13.sp, lineHeight = 19.sp)
                }
                if (current.rendered.steps.isNotEmpty()) {
                    var expanded by remember(expression) { mutableStateOf(false) }
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide working" else "Show working") }
                    if (expanded) current.rendered.steps.forEachIndexed { i, step -> Text("${i + 1}. $step", fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
                Text("Check every recognized symbol before saving.", fontSize = 12.sp, lineHeight = 18.sp)
                Button(onClick = { onSave(current.expression) }, enabled = enabled && !saving,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (saving) "Saving calculation…" else "Save reviewed calculation") }
            }
        }
    }
}
