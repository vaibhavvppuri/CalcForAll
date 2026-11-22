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
    var orientationOptions by remember { mutableStateOf<List<PhotoOrientationOption>>(emptyList()) }
    var orientationImage by remember { mutableStateOf<Bitmap?>(null) }
    var searchingOrientations by remember { mutableStateOf(false) }
    var orientationPass by remember { mutableIntStateOf(1) }

    fun applyDraft(draft: PhotoMathDraft) {
        recognizedText = draft.completeRawText
        text = draft.expressionForReview
        corrections = if (draft.truncated || draft.incomplete) emptyList() else draft.corrections
        warnings = when (val layout = draft.layout) {
            is MathLayoutResult.Draft -> layout.warnings
            is MathLayoutResult.Rejected -> listOf(layout.reason)
        }
        if (draft.rawText.isBlank()) error = "No text was found. Use a closer, sharper image with good lighting."
        if (draft.truncated) error = "This page contains too much text. Crop one expression before calculating."
        if (draft.incomplete) error = "The image could not be transcribed completely. Crop one expression or enter it below."
        workspace.needsRead = false
    }
    fun start(capturePath: String? = null, replacing: Boolean = false,
              draftOverride: PhotoMathDraft? = null, publish: () -> Unit = {},
              imageOperation: suspend () -> Bitmap) {
        job?.cancel()
        val ticket = ++revision
        busy = true; searchingOrientations = false; error = null
        orientationOptions = emptyList(); orientationImage = null
        // Keep the previous image and corrected text until decoding succeeds.
        // New imports are not resumable until their private source has been read.
        val previousNeedsRead = workspace.needsRead
        if (!replacing) workspace.needsRead = true
        job = scope.launch {
            var published = false
            try {
                val image = PhotoStaging.prepare(ticket, { revision }, imageOperation) { prepared ->
                    publish()
                    bitmap = prepared
                    text = ""; recognizedText = ""; warnings = emptyList(); corrections = emptyList(); editRejected = false
                    workspace.needsRead = true; published = true; scrollAfterRead = true
                } ?: return@launch
                val draft = draftOverride ?: withTimeout(45_000) { PhotoMathReader.read(image) }
                if (ticket == revision) applyDraft(draft)
            } catch (_: TimeoutCancellationException) {
                if (ticket == revision) { error = "Reading took too long. Crop a smaller expression or enter it below."; workspace.needsRead = false }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { if (ticket == revision) {
                  error = if (published) "Could not read this image: ${e.message ?: "recognition unavailable"}"
                      else "Could not open this image. Your previous draft is still here. ${e.message ?: "Try another image."}"
                  workspace.needsRead = if (published) false else previousNeedsRead
                  workspace.pendingRender = null
              } }
            finally {
                if (ticket == revision) busy = false
                capturePath?.let { path -> withContext(NonCancellable + Dispatchers.IO) { runCatching { PhotoInput.discardCapture(context, path) } } }
            }
        }
    }
    fun read(image: Bitmap) { workspace.pendingRender = null; start { image } }
    fun render(region: PhotoRegion, turns: Int, treatment: PhotoImageTreatment = workspace.treatment) {
        val source = workspace.source ?: return
        workspace.pendingRender = PhotoRenderRequest(region, turns, treatment)
        start(publish = {
            workspace.region = region; workspace.quarterTurns = PhotoCropGeometry.turns(turns)
            workspace.treatment = treatment; crop = false; workspace.pendingRender = null
        }) {
            withContext(Dispatchers.IO) {
                val decoded = source.crop(region, turns)
                try { PhotoImageAssist.apply(decoded, treatment).also { if (it !== decoded) decoded.recycle() } }
                catch (problem: Throwable) { decoded.recycle(); throw problem }
            }
        }
    }
    fun load(uri: Uri, capturePath: String? = null) {
        var stagedSource: PhotoSource? = null
        start(capturePath, replacing = true, publish = {
            workspace.source = stagedSource
            workspace.region = PhotoRegion.FULL; workspace.quarterTurns = 0
            workspace.treatment = PhotoImageTreatment.ORIGINAL; workspace.pendingRender = null
            crop = false; cropX = 0f..1f; cropY = 0f..1f
        }) {
            withContext(Dispatchers.IO) {
                PhotoInput.open(context, uri).let { source -> source.preview().also { stagedSource = source } }
            }
        }
    }
    fun searchOrientations(image: Bitmap) {
        job?.cancel()
        val ticket = ++revision
        busy = true; searchingOrientations = true; orientationPass = 1; error = null
        orientationOptions = emptyList(); orientationImage = null
        job = scope.launch {
            try {
                val options = withTimeout(45_000) {
                    PhotoMathReader.orientationAlternatives(image,
                        onTurnStarted = { orientationPass = it },
                        onOptionsReady = { completed -> if (ticket == revision) {
                            orientationImage = image; orientationOptions = completed
                        } })
                }
                if (ticket == revision) {
                    orientationImage = image; orientationOptions = options
                    if (options.isEmpty()) error = "No complete text was found at other orientations. Crop the expression or edit it below."
                }
            } catch (_: TimeoutCancellationException) {
                if (ticket == revision) error = if (orientationOptions.isNotEmpty())
                    "The orientation search reached its time limit. Completed choices are kept below; remaining orientations were not finished. Your current draft is unchanged."
                else "The orientation search took too long. Your current draft is unchanged; try rotating the image manually."
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { if (ticket == revision) error = "Could not finish the orientation search. Your current draft is unchanged. ${e.message ?: "Try a smaller image."}" }
            finally { if (ticket == revision) { busy = false; searchingOrientations = false } }
        }
    }
    fun chooseOrientation(option: PhotoOrientationOption) {
        val image = orientationImage ?: return
        var region = workspace.region
        repeat(option.turns) { region = region.rotateClockwise() }
        val turns = PhotoCropGeometry.turns(workspace.quarterTurns + option.turns)
        workspace.pendingRender = PhotoRenderRequest(region, turns, workspace.treatment)
        start(draftOverride = option.draft, publish = {
            workspace.region = region; workspace.quarterTurns = turns; crop = false; workspace.pendingRender = null
        }) { withContext(Dispatchers.Default) { PhotoImageAssist.apply(image, quarterTurns = option.turns) } }
    }
    fun cancelReading() {
        val wasOrientationSearch = searchingOrientations
        revision++; job?.cancel(); busy = false; workspace.needsRead = false; workspace.pendingRender = null
        searchingOrientations = false
        error = if (wasOrientationSearch && orientationOptions.isNotEmpty())
            "Orientation search cancelled. Completed choices are kept below; remaining orientations were not finished. Your current draft is unchanged."
        else "Reading cancelled. Your current image and draft are kept; review them or read again."
    }
    LaunchedEffect(workspace) {
        if (workspace.needsRead) {
            val pending = workspace.pendingRender
            if (pending != null) render(pending.region, pending.quarterTurns, pending.treatment)
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
                TextButton(enabled = !saving && pendingCapture == null, onClick = { job?.cancel(); workspace.clear(); busy = false; searchingOrientations = false; orientationOptions = emptyList(); orientationImage = null }) { Text("Clear photo") }
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
                Text("Image treatment · changes the preview before reading", fontSize = 12.sp, lineHeight = 18.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PhotoImageTreatment.ORIGINAL to "Original", PhotoImageTreatment.CONTRAST to "Boost contrast",
                        PhotoImageTreatment.INVERT_CONTRAST to "Light text on dark").forEach { (treatment, label) ->
                        FilterChip(workspace.treatment == treatment, enabled = !busy && !saving && !crop,
                            onClick = { if (workspace.treatment != treatment) render(workspace.region, workspace.quarterTurns, treatment) }, label = { Text(label) })
                    }
                }
                TextButton(enabled = !busy && !saving && !crop, onClick = { searchOrientations(image) }) { Text("Try other orientations") }
                if (orientationOptions.isNotEmpty()) {
                    Text("Choose an orientation after checking its text. You can choose a completed option while the remaining rotations are still being read.", fontSize = 12.sp, lineHeight = 18.sp)
                    orientationOptions.forEach { option ->
                        OutlinedButton(enabled = !saving && (!busy || searchingOrientations), modifier = Modifier.fillMaxWidth(), onClick = { chooseOrientation(option) }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text("Rotate ${option.turns * 90}° clockwise")
                                Text(option.draft.expressionForReview.ifBlank { option.draft.completeRawText }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                if (option.draft.expressionForReview.isBlank()) Text("Layout needs manual review", fontSize = 12.sp)
                            }
                        }
                    }
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
                if (workspace.region != PhotoRegion.FULL || workspace.quarterTurns != 0 || workspace.treatment != PhotoImageTreatment.ORIGINAL)
                    TextButton(enabled = !busy && !saving, onClick = { render(PhotoRegion.FULL, 0, PhotoImageTreatment.ORIGINAL) }) { Text("Restore original image") }
            }
            if (busy) {
                if (searchingOrientations) {
                    LinearProgressIndicator(progress = { (orientationPass - 1) / 3f }, modifier = Modifier.fillMaxWidth())
                    Text("Reading orientation $orientationPass of 3 offline. The current draft stays unchanged until you choose one.", fontSize = 12.sp, lineHeight = 18.sp)
                } else LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(enabled = !saving, onClick = ::cancelReading) { Text("Cancel reading") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
            warnings.forEach { Text(it, color = p.amber2, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp)) }
            if (corrections.isNotEmpty()) {
                Text("Possible corrections · check the image first", fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp))
                corrections.forEach { correction ->
                    OutlinedButton(enabled = !busy && !saving, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(), onClick = {
                        text = correction.expression; editRejected = false; scrollAfterRead = true
                        warnings = listOf("You selected a suggested correction. Check every symbol against the image before saving.")
                    }) { Column(Modifier.fillMaxWidth()) {
                        // A multi-equation choice must show every row before selection.
                        // Candidate text is bounded by the recognition input limits.
                        Text(correction.expression)
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
