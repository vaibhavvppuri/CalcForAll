package com.vaibhav.calc.android

import android.graphics.Bitmap
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
fun PhotoScreen(settings: Settings, environment: Environment, workspace: PhotoWorkspace, saving: Boolean = false, onClose: () -> Unit, onUse: (String) -> Unit) {
    BackHandler { if (!saving) onClose() }
    val context = LocalContext.current
    val p = LocalCalcPalette.current
    val scope = rememberCoroutineScope()
    val pageScroll = rememberScrollState()
    var bitmap by workspace::bitmap
    var text by workspace::text
    var recognizedText by workspace::recognizedText
    val recognizedLines = remember(recognizedText) { PhotoTextReview.lines(recognizedText) }
    var busy by remember { mutableStateOf(false) }
    var error by workspace::error
    var warnings by workspace::warnings
    var corrections by workspace::corrections
    var editRejected by workspace::editRejected
    var scrollAfterRead by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var revision by workspace::revision
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var crop by workspace::crop
    var cropX by workspace::cropX
    var cropY by workspace::cropY

    fun start(capturePath: String? = null, imageOperation: suspend () -> Bitmap) {
        job?.cancel()
        val ticket = ++revision
        busy = true; error = null; text = ""; recognizedText = ""; warnings = emptyList(); editRejected = false
        corrections = emptyList(); workspace.needsRead = true
        scrollAfterRead = true
        job = scope.launch {
            try {
                val image = imageOperation()
                ensureActive()
                if (ticket != revision) return@launch
                bitmap = image
                val draft = withTimeout(45_000) { PhotoMathReader.read(image) }
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
                    workspace.needsRead = false
                }
            } catch (_: TimeoutCancellationException) {
                if (ticket == revision) { error = "Reading took too long. Crop a smaller expression or enter it below."; workspace.needsRead = false }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { if (ticket == revision) { error = "Could not read this image: ${e.message ?: "recognition unavailable"}"; workspace.needsRead = false; workspace.pendingRender = null } }
            finally {
                if (ticket == revision) busy = false
                capturePath?.let { path -> withContext(NonCancellable + Dispatchers.IO) { runCatching { PhotoInput.discardCapture(context, path) } } }
            }
        }
    }
    fun read(image: Bitmap) { workspace.pendingRender = null; start { image } }
    fun render(region: PhotoRegion, turns: Int) {
        val source = workspace.source ?: return
        workspace.pendingRender = PhotoRenderRequest(region, turns)
        start {
            val image = withContext(Dispatchers.IO) { source.crop(region, turns) }
            workspace.region = region; workspace.quarterTurns = PhotoCropGeometry.turns(turns); crop = false; workspace.pendingRender = null
            image
        }
    }
    fun load(uri: Uri, capturePath: String? = null) {
        job?.cancel()
        workspace.clear()
        start(capturePath) {
            val source = withContext(Dispatchers.IO) { PhotoInput.open(context, uri) }
            workspace.source = source
            withContext(Dispatchers.IO) { source.preview() }
        }
    }
    fun cancelReading() {
        revision++; job?.cancel(); busy = false; workspace.needsRead = false; workspace.pendingRender = null
        error = "Reading cancelled. Crop the image, read it again, or enter the expression below."
    }
    LaunchedEffect(workspace) {
        if (workspace.needsRead) {
            val pending = workspace.pendingRender
            if (pending != null) render(pending.region, pending.quarterTurns)
            else bitmap?.let(::read) ?: workspace.source?.let { render(workspace.region, workspace.quarterTurns) }
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
                TextButton(enabled = !saving, onClick = onClose) { Text("Close") }
            }
            Text("Photograph one expression to calculate offline.",
                color = p.blue, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 10.dp))
            Text("Use clear printed math. Check signs, powers and fractions before saving.", fontSize = 12.sp, lineHeight = 18.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Your draft stays here until cleared, saved, or the app restarts.", modifier = Modifier.weight(1f), fontSize = 12.sp, lineHeight = 18.sp)
                TextButton(enabled = !saving && pendingCapture == null, onClick = { job?.cancel(); workspace.clear(); busy = false }) { Text("Clear photo") }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !saving && !busy && pendingCapture == null, modifier = Modifier.weight(1f), onClick = {
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
                }, enabled = !busy && !saving, modifier = Modifier.weight(1f)) { Text("Choose image", fontSize = 14.sp) }
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
                    TextButton(enabled = !busy && !saving, onClick = {
                        render(workspace.region.rotateClockwise(), workspace.quarterTurns + 1)
                    }) { Text("Rotate 90°") }
                    TextButton(enabled = !busy && !saving, onClick = { crop = !crop; cropX = 0f..1f; cropY = 0f..1f }) { Text(if (crop) "Cancel crop" else "Crop expression") }
                    TextButton(enabled = !busy && !saving, onClick = { read(image) }) { Text("Read again") }
                }
                if (crop) {
                    Text("Crop from the original-resolution photo. Include the whole expression, including fraction bars and powers.", fontSize = 12.sp, lineHeight = 18.sp)
                    Text("Left / right edges", fontSize = 12.sp, lineHeight = 18.sp)
                    RangeSlider(value = cropX, enabled = !busy && !saving, onValueChange = { if (it.endInclusive - it.start >= .03f) cropX = it }, modifier = Modifier.semantics { contentDescription = "Crop left and right edges" })
                    Text("Top / bottom edges", fontSize = 12.sp, lineHeight = 18.sp)
                    RangeSlider(value = cropY, enabled = !busy && !saving, onValueChange = { if (it.endInclusive - it.start >= .03f) cropY = it }, modifier = Modifier.semantics { contentDescription = "Crop top and bottom edges" })
                    Button(enabled = !busy && !saving, onClick = {
                        val selected = PhotoRegion(cropX.start.toDouble(), cropY.start.toDouble(), cropX.endInclusive.toDouble(), cropY.endInclusive.toDouble())
                        render(workspace.region.subregion(selected), workspace.quarterTurns)
                    }) { Text("Read cropped expression") }
                }
                if (workspace.region != PhotoRegion.FULL || workspace.quarterTurns != 0)
                    TextButton(enabled = !busy && !saving, onClick = { render(PhotoRegion.FULL, 0) }) { Text("Restore original image") }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(enabled = !saving, onClick = ::cancelReading) { Text("Cancel reading") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
            warnings.forEach { Text(it, color = p.amber2, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp)) }
            if (corrections.isNotEmpty()) {
                Text("Possible corrections · check the image first", fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp))
                corrections.forEach { correction ->
                    OutlinedButton(enabled = !busy && !saving, modifier = Modifier.fillMaxWidth(), onClick = {
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
                TextButton(enabled = !saving, onClick = { linesExpanded = !linesExpanded }) { Text(if (linesExpanded) "Hide raw text lines" else "Choose a raw text line") }
                if (linesExpanded) recognizedLines.forEachIndexed { index, line ->
                    OutlinedButton(onClick = { text = line; editRejected = false; scope.launch { pageScroll.animateScrollTo(pageScroll.maxValue) } }, enabled = !busy && !saving, modifier = Modifier.fillMaxWidth()) {
                        Text("${index + 1}. $line", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (recognizedText.isNotEmpty() && text != recognizedText) TextButton(onClick = { text = recognizedText; editRejected = false }, enabled = !busy && !saving) { Text("Use raw recognized text") }
            OutlinedTextField(text, { if (it.length <= CalcSession.MAX_ENTRY_CHARS) { text = it; editRejected = false } else { error = "The pasted text is too long and was not inserted."; editRejected = true } }, keyboardOptions = MathKeyboard,
                label = { Text("Expression · edit to correct") }, enabled = !busy && !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), maxLines = 6)
            Text("Angle mode: ${settings.angle.name}", fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
            RecognitionAnswer(text, settings, environment, enabled = !busy && !crop && !editRejected, saving = saving,
                onResolved = { if (scrollAfterRead) { scrollAfterRead = false; scope.launch { pageScroll.animateScrollTo(pageScroll.maxValue) } } }, onSave = onUse)
            Text("Word problems and complex or handwritten layouts may need manual entry. Only the recognized expression is calculated.", fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 12.dp))
            Text("Only the reviewed expression is saved in calculation history. The photo stays in memory for this draft; Clear photo releases it. The temporary camera file is deleted after reading.",
                fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}
