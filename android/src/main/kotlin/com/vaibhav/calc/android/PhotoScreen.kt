package com.vaibhav.calc.android

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.calc.android.theme.LocalCalcPalette
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Settings
import kotlinx.coroutines.*
import kotlin.math.min

@Composable
fun PhotoScreen(settings: Settings, environment: Environment, saving: Boolean = false, onClose: () -> Unit, onUse: (String) -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val p = LocalCalcPalette.current
    val scope = rememberCoroutineScope()
    val pageScroll = rememberScrollState()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var recognizedText by remember { mutableStateOf("") }
    val recognizedLines = remember(recognizedText) { PhotoTextReview.lines(recognizedText) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var warnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var corrections by remember { mutableStateOf<List<PhotoMathCorrection>>(emptyList()) }
    var editRejected by remember { mutableStateOf(false) }
    var scrollAfterRead by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var crop by remember { mutableStateOf(false) }
    var cropX by remember { mutableStateOf(0f..1f) }
    var cropY by remember { mutableStateOf(0f..1f) }

    fun read(image: Bitmap) {
        job?.cancel()
        val ticket = ++revision
        busy = true; error = null; text = ""; recognizedText = ""; warnings = emptyList(); editRejected = false
        corrections = emptyList()
        scrollAfterRead = true
        job = scope.launch {
            try {
                val draft = PhotoMathReader.read(image)
                if (ticket == revision) {
                    recognizedText = draft.completeRawText
                    text = draft.expressionForReview
                    corrections = draft.corrections
                    when (val layout = draft.layout) {
                        is MathLayoutResult.Draft -> { warnings = layout.warnings }
                        is MathLayoutResult.Rejected -> {
                            warnings = listOf(layout.reason)
                        }
                    }
                    if (draft.rawText.isBlank()) error = "No text was found. Use a closer, sharper image with good lighting."
                    if (draft.truncated) { text = ""; error = "This page contains too much text. Crop one expression before calculating." }
                }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { if (ticket == revision) error = "Could not read this image: ${e.message ?: "recognition unavailable"}" }
            finally { if (ticket == revision) busy = false }
        }
    }
    fun load(uri: Uri, capturePath: String? = null) {
        job?.cancel()
        val ticket = ++revision
        busy = true; error = null; text = ""; recognizedText = ""; warnings = emptyList(); crop = false
        corrections = emptyList()
        job = scope.launch {
            try {
                val image = withContext(Dispatchers.IO) { PhotoInput.decode(context, uri) }
                if (ticket == revision) { bitmap = image; original = image; read(image) }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { if (ticket == revision) { error = e.message ?: "Could not open this image."; busy = false } }
            finally { capturePath?.let { runCatching { PhotoInput.discardCapture(context, it) } } }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) load(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = pendingCapture
        pendingCapture = null
        if (path != null) {
            if (saved) {
                try { load(PhotoInput.captureUri(context, path), path) }
                catch (e: Exception) { error = e.message ?: "Could not open the captured image."; runCatching { PhotoInput.discardCapture(context, path) } }
            } else { runCatching { PhotoInput.discardCapture(context, path) }; error = "No photo was taken." }
        }
    }
    Surface(Modifier.fillMaxSize(), color = p.paper) {
        Column(Modifier.fillMaxSize().verticalScroll(pageScroll).imePadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Photo calculator", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close") }
            }
            Text("Photograph one expression to calculate offline.",
                color = p.blue, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 10.dp))
            Text("Use clear printed math. Check signs, powers and fractions before saving.", fontSize = 12.sp, lineHeight = 18.sp)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && pendingCapture == null, modifier = Modifier.weight(1f), onClick = {
                    try {
                        val (path, uri) = PhotoInput.createCapture(context)
                        pendingCapture = path
                        camera.launch(uri)
                    } catch (e: Exception) {
                        pendingCapture?.let { runCatching { PhotoInput.discardCapture(context, it) } }; pendingCapture = null
                        error = "Camera unavailable. Choose an existing image instead."
                    }
                }) { Text("Take photo", fontSize = 14.sp) }
                OutlinedButton(onClick = {
                    try { picker.launch("image/*") } catch (_: Exception) { error = "No image picker is available on this device." }
                }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Choose image", fontSize = 14.sp) }
            }
            bitmap?.let { image ->
                Box(Modifier.fillMaxWidth().height(230.dp).background(p.sheet)) {
                    Image(image.asImageBitmap(), contentDescription = "Selected expression image", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    if (crop) Canvas(Modifier.fillMaxSize()) {
                        val scale = min(size.width / image.width, size.height / image.height)
                        val width = image.width * scale; val height = image.height * scale
                        val x = (size.width - width) / 2; val y = (size.height - height) / 2
                        drawRect(p.blue, Offset(x + width * cropX.start, y + height * cropY.start),
                            Size(width * (cropX.endInclusive - cropX.start), height * (cropY.endInclusive - cropY.start)), style = Stroke(2.dp.toPx()))
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(enabled = !busy, onClick = {
                        val rotated = Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply { postRotate(90f) }, true)
                        bitmap = rotated; crop = false; read(rotated)
                    }) { Text("Rotate 90°") }
                    TextButton(enabled = !busy, onClick = { crop = !crop; cropX = 0f..1f; cropY = 0f..1f }) { Text(if (crop) "Cancel crop" else "Crop expression") }
                    TextButton(enabled = !busy, onClick = { read(image) }) { Text("Read again") }
                }
                if (crop) {
                    Text("Adjust the frame to include the whole expression, including fraction bars and powers.", fontSize = 12.sp, lineHeight = 18.sp)
                    Text("Left / right edges", fontSize = 12.sp, lineHeight = 18.sp)
                    RangeSlider(value = cropX, onValueChange = { if (it.endInclusive - it.start >= .03f) cropX = it }, modifier = Modifier.semantics { contentDescription = "Crop left and right edges" })
                    Text("Top / bottom edges", fontSize = 12.sp, lineHeight = 18.sp)
                    RangeSlider(value = cropY, onValueChange = { if (it.endInclusive - it.start >= .03f) cropY = it }, modifier = Modifier.semantics { contentDescription = "Crop top and bottom edges" })
                    Button(onClick = {
                        val x = (cropX.start * image.width).toInt().coerceIn(0, image.width - 1)
                        val y = (cropY.start * image.height).toInt().coerceIn(0, image.height - 1)
                        val width = ((cropX.endInclusive - cropX.start) * image.width).toInt().coerceIn(1, image.width - x)
                        val height = ((cropY.endInclusive - cropY.start) * image.height).toInt().coerceIn(1, image.height - y)
                        val selected = Bitmap.createBitmap(image, x, y, width, height)
                        bitmap = selected; crop = false; read(selected)
                    }) { Text("Read cropped expression") }
                }
                if (image !== original) TextButton(enabled = !busy, onClick = { original?.let { bitmap = it; crop = false; read(it) } }) { Text("Restore original image") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
            warnings.forEach { Text(it, color = p.amber2, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp)) }
            if (corrections.isNotEmpty()) {
                Text("Possible corrections · check the image first", fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp))
                corrections.forEach { correction ->
                    OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        text = correction.expression; editRejected = false; scrollAfterRead = true
                        warnings = listOf("You selected a suggested correction. Check every symbol against the image before saving.")
                    }) { Column(Modifier.fillMaxWidth()) {
                        Text(correction.expression, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(correction.explanation, fontSize = 12.sp, lineHeight = 18.sp)
                    } }
                }
            }
            if (recognizedLines.size > 1) {
                var linesExpanded by remember(recognizedText) { mutableStateOf(false) }
                TextButton(onClick = { linesExpanded = !linesExpanded }) { Text(if (linesExpanded) "Hide raw text lines" else "Choose a raw text line") }
                if (linesExpanded) recognizedLines.forEachIndexed { index, line ->
                    OutlinedButton(onClick = { text = line; editRejected = false; scope.launch { pageScroll.animateScrollTo(pageScroll.maxValue) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("${index + 1}. $line", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (recognizedText.isNotEmpty() && text != recognizedText) TextButton(onClick = { text = recognizedText; editRejected = false }, enabled = !busy) { Text("Use raw recognized text") }
            OutlinedTextField(text, { if (it.length <= CalcSession.MAX_ENTRY_CHARS) { text = it; editRejected = false } else { error = "The pasted text is too long and was not inserted."; editRejected = true } }, keyboardOptions = MathKeyboard,
                label = { Text("Expression · edit to correct") }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), maxLines = 6)
            Text("Angle mode: ${settings.angle.name}", fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
            RecognitionAnswer(text, settings, environment, enabled = !busy && !crop && !editRejected, saving = saving,
                onResolved = { if (scrollAfterRead) { scrollAfterRead = false; scope.launch { pageScroll.animateScrollTo(pageScroll.maxValue) } } }, onSave = onUse)
            Text("Word problems and complex or handwritten layouts may need manual entry. Only the recognized expression is calculated.", fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 12.dp))
            Text("Only the reviewed expression is saved in calculation history. The app deletes its temporary camera file after reading it.",
                fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}
