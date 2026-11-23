package com.vaibhav.calc.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import com.vaibhav.calc.android.theme.LocalCalcPalette
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun HandwritingScreen(settings: Settings, environment: Environment, workspace: InkWorkspace, saving: Boolean = false, onClose: () -> Unit, onUse: (String) -> Unit) {
    BackHandler { if (!saving) onClose() }
    val p = LocalCalcPalette.current
    var strokes by workspace::strokes
    var current by remember { mutableStateOf<List<InkPoint>>(emptyList()) }
    var expression by workspace::expression
    var stylusOnly by workspace::stylusOnly
    var mode by workspace::mode
    var erase by workspace::erase
    var lineDraft by workspace::lineDraft
    var mathDraft by workspace::mathDraft
    var reviewTokens by workspace::reviewTokens
    var lineSymbols by workspace::lineSymbols
    var candidates by workspace::candidates
    var selectedGlyph by workspace::selectedGlyph
    var notice by workspace::notice
    var reading by remember { mutableStateOf(false) }
    var rebuilding by remember { mutableStateOf(false) }
    var readGeneration by remember { mutableLongStateOf(0L) }
    var correctionGeneration by remember { mutableLongStateOf(0L) }
    var correctionJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val expressionFocus = remember { FocusRequester() }
    var inkBlocked by workspace::inkBlocked
    var erasingGesture by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val lock = workspace.recognitionLock
    val busy = reading || rebuilding
    LaunchedEffect(workspace.inkRevision, mode, current.isEmpty()) {
        correctionGeneration++; correctionJob?.cancel(); rebuilding = false
    }
    LaunchedEffect(strokes, mode, current.isEmpty(), erasingGesture, workspace.recognitionRequest, workspace.recognitionPaused) {
        val generation = ++readGeneration
        val ink = strokes; val readingMode = mode; val revision = workspace.inkRevision
        val request = workspace.recognitionRequest
        if (current.isNotEmpty() || erasingGesture || workspace.recognitionPaused) { reading = false; return@LaunchedEffect }
        val sameInk = ink == workspace.recognizedStrokes && readingMode == workspace.recognizedMode
        if (sameInk && request == workspace.recognizedRequest) { reading = false; return@LaunchedEffect }
        val previousTokens = if (sameInk) reviewTokens.map { it.copy(alternatives = it.alternatives.toList()) } else emptyList()
        val previousLine = if (sameInk) lineDraft else null
        val previousLineSymbols = if (sameInk) lineSymbols.toList() else emptyList()
        val retainReviewedText = workspace.expressionWasReviewed
        val reviewedText = expression
        if (!sameInk) {
            lineDraft = null; mathDraft = null; reviewTokens = emptyList(); selectedGlyph = null; candidates = emptyList()
        }
        if (ink.isEmpty()) { reading = false; return@LaunchedEffect }
        reading = true
        if (!sameInk && !retainReviewedText && mode != "One symbol") expression = ""
        try {
            delay(650)
            val result = lock.withLock { withContext(Dispatchers.Default) {
                when (readingMode) {
                    "Math layout" -> InkRecovery.retainExplicitChoices(previousTokens, MathInkRecognizer.readDetailed(ink))
                    "Spaced line" -> InkLineRecognizer.recognize(ink)
                    else -> InkRecognizer.recognize(ink)
                }
            } }
            if (generation == readGeneration && workspace.inkRevision == revision &&
                !workspace.recognitionPaused && strokes == ink && current.isEmpty() && mode == readingMode) {
              when (result) {
                is MathInkReading -> {
                    if (sameInk && previousTokens.isNotEmpty() && result.tokens.isEmpty()) {
                        notice = (result.layout as? MathLayoutResult.Rejected)?.reason.orEmpty() + " Your previous symbol choices are kept."
                    } else {
                        reviewTokens = result.tokens
                        when (val layout = result.layout) {
                            is MathLayoutResult.Draft -> { mathDraft = layout; expression = layout.expression; notice = layout.warnings.joinToString(" ") }
                            is MathLayoutResult.Rejected -> {
                                mathDraft = null; expression = ""
                                notice = layout.reason + if (result.tokens.isNotEmpty()) " Tap a numbered symbol to correct it; no partial expression is calculated." else ""
                            }
                        }
                    }
                }
                is InkLineResult.Draft -> {
                    lineDraft = result
                    lineSymbols = if (previousLine != null && previousLineSymbols.size == result.glyphs.size)
                        previousLineSymbols else result.glyphs.map { it.candidates.first().symbol }
                    expression = lineSymbols.joinToString(""); notice = "Review the transcription. Tap a symbol for alternatives."
                }
                is InkLineResult.Rejected -> notice = result.reason + if (previousLine != null) " Your previous symbol choices are kept." else ""
                is List<*> -> { candidates = result.filterIsInstance<InkCandidate>(); notice = "Choose the matching symbol to append it to the expression." }
              }
              if (retainReviewedText) workspace.editExpression(reviewedText)
              workspace.recognizedStrokes = ink; workspace.recognizedMode = readingMode
              workspace.recognizedRequest = request
            }
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { if (generation == readGeneration) notice = e.message ?: "The ink could not be read. Edit the expression below." }
        finally { if (generation == readGeneration) reading = false }
    }
    val needsReview = workspace.needsExpressionReview
    val recovery = if (!busy && !workspace.expressionWasReviewed) InkRecovery.guidance(
        strokes.isNotEmpty(), mode, reviewTokens, mathDraft, lineDraft, workspace.reviewedLineSymbols) else null
    Surface(Modifier.fillMaxSize(), color = p.paper) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Write & calculate", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(enabled = !saving, onClick = onClose) { Text("Close") }
            }
            Text("Write a calculation with a finger or stylus.", color = p.blue, fontSize = 13.sp, lineHeight = 19.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Your draft stays here until cleared, saved, or the app restarts.", modifier = Modifier.weight(1f), fontSize = 12.sp, lineHeight = 18.sp)
                TextButton(enabled = !saving && !erasingGesture && current.isEmpty(), onClick = { workspace.clear() }) { Text("Clear draft") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Math layout", "Spaced line", "One symbol").forEach { item ->
                    FilterChip(mode == item, onClick = {
                        workspace.changeMode(item)
                    }, enabled = !saving && !erasingGesture && current.isEmpty(), label = { Text(item) })
                }
            }
            Text(when (mode) {
                "Math layout" -> "Leave gaps between symbols. Use raised powers or stacked fractions. Clear readings calculate after pen-up; uncertain readings need your review."
                "Spaced line" -> "Leave gaps between symbols on one line. Use Math layout for fractions and powers."
                else -> "Write one digit or operator, choose its match, then write the next."
            }, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Stylus only (ignore fingers)", modifier = Modifier.weight(1f), fontSize = 13.sp, lineHeight = 19.sp)
                Switch(stylusOnly, onCheckedChange = { stylusOnly = it }, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Stylus only, ignore fingers" })
            }
            Canvas(Modifier.fillMaxWidth().height(240.dp).background(p.sheet).border(1.dp, p.sheetEdge)
                .semantics { contentDescription = "Handwriting pad" }
                .onSizeChanged { canvasSize = it }
                .pointerInput(stylusOnly, mode, erase, saving, canvasSize) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (!saving && (!stylusOnly || down.type == PointerType.Stylus || down.type == PointerType.Eraser)) {
                            if (workspace.paperWidth <= 0f || workspace.paperHeight <= 0f) {
                                workspace.paperWidth = size.width.toFloat(); workspace.paperHeight = size.height.toFloat()
                            }
                            val viewport = InkViewport(workspace.paperWidth, workspace.paperHeight, size.width.toFloat(), size.height.toFloat())
                            if (!viewport.contains(down.position.x, down.position.y)) return@awaitEachGesture
                            fun point(position: Offset) = viewport.toPaper(position.x, position.y)
                            down.consume()
                            val erasing = erase || down.type == PointerType.Eraser
                            if (erasing) {
                                val before = strokes
                                var ended = false
                                val capture = InkStrokeBuffer(InkStrokeBuffer.MAX_POINTS, workspace.paperWidth, workspace.paperHeight)
                                var previous = point(down.position)
                                fun removeNear(next: InkPoint) {
                                    val kept = InkGeometry.erase(strokes, previous, next, 18.dp.toPx() / viewport.scale)
                                    if (kept !== strokes) { strokes = kept }
                                    previous = next
                                }
                                erasingGesture = true
                                try {
                                    capture.append(previous); removeNear(previous)
                                    var processed = 1
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        capture.append(point(change.position), change.historical.asSequence().map { point(it.position) })
                                        if (capture.failure != null) { notice = "Lift the eraser and start another short gesture."; break }
                                        val path = capture.snapshot()
                                        path.drop(processed).forEach(::removeNear); processed = path.size
                                        change.consume()
                                        if (!change.pressed) { ended = true; break }
                                    }
                                } finally {
                                    if (ended && capture.failure == null) {
                                        workspace.recordInkEdit(before)
                                        if (before != strokes) { inkBlocked = false; if (mode != "One symbol") expression = "" }
                                    } else strokes = before // Interrupted erasing is one cancelled edit.
                                    erasingGesture = false
                                }
                            } else if (!inkBlocked && strokes.size < (if (mode == "One symbol") 32 else 64) && strokes.sumOf { it.size } < 12000) {
                                val pointBudget = minOf(InkStrokeBuffer.MAX_POINTS, 12000 - strokes.sumOf { it.size })
                                val capture = InkStrokeBuffer(pointBudget, workspace.paperWidth, workspace.paperHeight)
                                capture.append(point(down.position))
                                current = capture.snapshot()
                                var ended = false
                                try {
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        // Fast/coalesced gestures may deliver no separate move event.
                                        // Keep the final position before handling the up transition.
                                        capture.append(point(change.position), change.historical.asSequence().map { point(it.position) })
                                        current = capture.snapshot()
                                        change.consume()
                                        if (!change.pressed) { ended = true; break }
                                    }
                                    if (capture.failure != null) {
                                        inkBlocked = true
                                        notice = if (capture.failure == InkStrokeBuffer.Failure.POINT_LIMIT)
                                            "That stroke was too long and was not added. Undo or clear the pad before continuing."
                                        else "That stroke contained invalid coordinates and was not added. Undo or clear the pad before continuing."
                                    } else if (ended) {
                                        capture.completed()?.takeIf { it.isNotEmpty() }?.let { workspace.commitInk(strokes + listOf(it)) }
                                    }
                                } finally { current = emptyList() }
                            } else { inkBlocked = true; notice = "Ink limit reached. Undo or clear the pad before adding more." }
                        }
                    }
                }) {
                val paperWidth = workspace.paperWidth.takeIf { it > 0f } ?: size.width
                val paperHeight = workspace.paperHeight.takeIf { it > 0f } ?: size.height
                val viewport = InkViewport(paperWidth, paperHeight, size.width, size.height)
                withTransform({ translate(viewport.left, viewport.top); scale(viewport.scale, viewport.scale, Offset.Zero) }) {
                val grid = 24.dp.toPx()
                drawRect(p.sheetEdge, size = Size(paperWidth, paperHeight), style = Stroke(1.dp.toPx() / viewport.scale))
                var x = grid; while (x < paperWidth) { drawLine(p.grid, Offset(x, 0f), Offset(x, paperHeight)); x += grid }
                var y = grid; while (y < paperHeight) { drawLine(p.grid, Offset(0f, y), Offset(paperWidth, y)); y += grid }
                reviewTokens.forEachIndexed { index, token ->
                    if (token.uncertain || selectedGlyph == index) {
                        val b = token.bounds
                        drawRect(if (selectedGlyph == index) p.blue else p.amber2, Offset(b.left - 4, b.top - 4), Size(b.width + 8, b.height + 8), style = Stroke(1.dp.toPx()))
                    }
                }
                fun drawPath(path: List<InkPoint>) {
                    if (path.size == 1) drawCircle(p.ink, 2.dp.toPx(), Offset(path[0].x, path[0].y))
                    for (index in 1 until path.size) {
                        val a = path[index - 1]; val b = path[index]
                        drawLine(p.ink, Offset(a.x, a.y), Offset(b.x, b.y), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
                strokes.forEach(::drawPath); drawPath(current)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { candidate -> OutlinedButton(enabled = !saving && !erasingGesture && current.isEmpty(), onClick = {
                    val glyph = if (candidate.symbol == "√") "sqrt(" else candidate.symbol
                    if (expression.length + glyph.length > RecognitionCalculator.MAX_CHARS) {
                        notice = "The expression is full. Shorten it before adding this symbol; your ink is still on the pad."
                    } else {
                        expression += glyph
                        workspace.invalidateRecognition(); strokes = emptyList(); workspace.resetInkHistory(); notice = null
                    }
                }) { Text(candidate.symbol, fontSize = 24.sp) } }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(enabled = !saving && !erasingGesture && current.isEmpty() && workspace.canUndoInk, onClick = { workspace.undoInk(); inkBlocked = false; if (mode != "One symbol") expression = ""; notice = null }) { Text("Undo ink") }
                TextButton(enabled = !saving && !erasingGesture && current.isEmpty() && workspace.canRedoInk, onClick = { workspace.redoInk(); inkBlocked = false; if (mode != "One symbol") expression = ""; notice = null }) { Text("Redo ink") }
                TextButton(enabled = !saving && !erasingGesture && current.isEmpty() && strokes.isNotEmpty(), onClick = { workspace.commitInk(emptyList()); inkBlocked = false; if (mode != "One symbol") expression = ""; notice = null }) { Text("Clear pad") }
                FilterChip(erase, enabled = !saving, onClick = { erase = !erase }, label = { Text("Eraser") })
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(enabled = !saving, onClick = {
                    readGeneration++; correctionGeneration++; correctionJob?.cancel()
                    workspace.pauseRecognition(); reading = false; rebuilding = false
                    notice = "Reading stopped. Your ink and completed corrections are kept. Edit the expression or read again when ready."
                }) { Text("Cancel reading") }
            } else if (strokes.isNotEmpty()) {
                TextButton(enabled = !saving && !erasingGesture && current.isEmpty(), onClick = {
                    notice = null; workspace.retryRecognition()
                }) { Text("Read again") }
            }
            notice?.let { Text(it, color = p.amber2, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 6.dp)) }
            recovery?.let { advice ->
                Text(advice.message, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    advice.firstSymbol?.let { index ->
                        TextButton(enabled = !saving && !erasingGesture && current.isEmpty(), onClick = { selectedGlyph = index }) {
                            Text("Review symbol ${index + 1}")
                        }
                    }
                    TextButton(enabled = !saving && !erasingGesture && current.isEmpty(), onClick = { expressionFocus.requestFocus() }) { Text("Edit expression") }
                }
            }
            val symbols = if (reviewTokens.isNotEmpty()) reviewTokens.map { it.text } else if (lineDraft != null) lineSymbols else emptyList()
            if (symbols.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                symbols.forEachIndexed { index, symbol -> TextButton(enabled = !busy && !saving && !erasingGesture && current.isEmpty(), onClick = { selectedGlyph = index },
                    modifier = Modifier.semantics { contentDescription = "Correct symbol ${index + 1}: $symbol" }) { Text("${index + 1}: $symbol") } }
            }
            OutlinedTextField(expression, { if (!workspace.editExpression(it)) { notice = "The pasted text is too long and was not inserted."; inkBlocked = true } }, enabled = !busy && !saving && !erasingGesture && current.isEmpty(),
                keyboardOptions = MathKeyboard, label = { Text("Expression · edit to correct") }, modifier = Modifier.fillMaxWidth().focusRequester(expressionFocus), maxLines = 4)
            if (needsReview && !busy) {
                Text("Some symbols have more than one possible reading. Check the expression before calculating; no answer is selected for you.",
                    color = p.amber2, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))
                OutlinedButton(enabled = !saving && !erasingGesture && current.isEmpty() && !inkBlocked,
                    onClick = { workspace.acknowledgeExpression() }, shape = MaterialTheme.shapes.medium) {
                    Text("I checked the expression · calculate")
                }
            }
            Text("Apply a function to this expression, or start typed entry if empty", fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf("sqrt", "sin", "cos", "tan", "ln", "log", "abs").forEach { function ->
                    TextButton(enabled = !busy && !saving && !erasingGesture && current.isEmpty(), onClick = {
                        val changed = if (expression.isBlank()) "$function(" else "$function($expression)"
                        if (changed.length <= RecognitionCalculator.MAX_CHARS) {
                            workspace.transformExpression(changed)
                        } else notice = "The expression is full. Shorten it before adding a function."
                    }) { Text(function) }
                }
            }
            Text("${settings.angle.name} · New ink replaces the draft. Cursive and unclear symbols need correction.", fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
            RecognitionAnswer(expression, settings, environment, enabled = !busy && current.isEmpty() && !erasingGesture && !inkBlocked && !needsReview, saving = saving, onSave = onUse)
            Spacer(Modifier.height(16.dp))
        }
    }
    selectedGlyph?.let { index ->
        val token = reviewTokens.getOrNull(index)
        val options = token?.let { (listOf(it.text) + it.alternatives).filter { symbol -> symbol in InkReview.symbols }.distinct() }
            ?: lineDraft?.glyphs?.getOrNull(index)?.candidates?.map { it.symbol }.orEmpty()
        val palette = if (token != null) InkReview.symbols else InkReview.lineSymbols
        fun choose(symbol: String) {
            if (saving || busy || erasingGesture || current.isNotEmpty()) return
            if (token != null) {
                val revision = workspace.inkRevision
                val expected = reviewTokens.map { it.copy(alternatives = it.alternatives.toList()) }
                val readingMode = mode
                val generation = ++correctionGeneration
                selectedGlyph = null; rebuilding = true
                correctionJob = scope.launch {
                    try {
                        val edit = lock.withLock { withContext(Dispatchers.Default) { InkReview.replace(expected, index, symbol) } }
                        if (edit != null && generation == correctionGeneration && current.isEmpty() && mode == readingMode)
                            workspace.applyInkEdit(revision, expected, edit)
                    } catch (e: CancellationException) { throw e }
                      catch (e: Exception) { if (generation == correctionGeneration) notice = "That correction could not be applied. Your previous choices are kept; edit the expression instead." }
                    finally { if (generation == correctionGeneration) rebuilding = false }
                }
            } else {
                workspace.replaceLineSymbol(index, symbol)
            }
        }
        if (token != null || options.isNotEmpty()) AlertDialog(onDismissRequest = { if (!saving) selectedGlyph = null },
            title = { Text("Symbol ${index + 1}: choose a match") },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("Choosing a symbol rebuilds the transcription from all the ink groups. Make text edits afterward. If a group contains several symbols, erase and redraw it.")
                if (options.isNotEmpty()) {
                    Text("Suggested matches", modifier = Modifier.padding(top = 12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        options.forEach { symbol -> TextButton(enabled = !saving && !busy && current.isEmpty() && !erasingGesture, onClick = { choose(symbol) },
                            modifier = Modifier.semantics { contentDescription = "Suggested symbol $symbol" }) { Text(symbol, fontSize = 24.sp) } }
                    }
                }
                Text("Choose another symbol", modifier = Modifier.padding(top = 8.dp))
                palette.chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { symbol -> TextButton(enabled = !saving && !busy && current.isEmpty() && !erasingGesture, onClick = { choose(symbol) },
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Replace with $symbol" },
                            contentPadding = PaddingValues(0.dp)) { Text(symbol, fontSize = 22.sp) } }
                    }
                }
            } }, confirmButton = { TextButton(enabled = !saving, onClick = { selectedGlyph = null }) { Text("Keep current") } })
    }
}
