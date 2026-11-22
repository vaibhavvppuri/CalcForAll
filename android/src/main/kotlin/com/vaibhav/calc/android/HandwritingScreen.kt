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
    var inkBlocked by workspace::inkBlocked
    var erasingGesture by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val lock = workspace.recognitionLock
    LaunchedEffect(strokes, mode, current.isEmpty(), erasingGesture) {
        val ink = strokes; val readingMode = mode
        if (current.isNotEmpty() || erasingGesture) { reading = false; return@LaunchedEffect }
        if (ink == workspace.recognizedStrokes && readingMode == workspace.recognizedMode) { reading = false; return@LaunchedEffect }
        lineDraft = null; mathDraft = null; reviewTokens = emptyList(); selectedGlyph = null; candidates = emptyList()
        if (ink.isEmpty()) { reading = false; return@LaunchedEffect }
        reading = true
        if (mode != "One symbol") expression = ""
        delay(650)
        try {
            val result = lock.withLock { withContext(Dispatchers.Default) {
                when (readingMode) {
                    "Math layout" -> MathInkRecognizer.readDetailed(ink)
                    "Spaced line" -> InkLineRecognizer.recognize(ink)
                    else -> InkRecognizer.recognize(ink)
                }
            } }
            if (strokes == ink && current.isEmpty() && mode == readingMode) {
              when (result) {
                is MathInkReading -> {
                    reviewTokens = result.tokens
                    when (val layout = result.layout) {
                        is MathLayoutResult.Draft -> { mathDraft = layout; expression = layout.expression; notice = layout.warnings.joinToString(" ") }
                        is MathLayoutResult.Rejected -> notice = layout.reason +
                            if (result.tokens.isNotEmpty()) " Tap a numbered symbol to correct it; no partial expression is calculated." else ""
                    }
                }
                is InkLineResult.Draft -> { lineDraft = result; lineSymbols = result.glyphs.map { it.candidates.first().symbol }; expression = result.text; notice = "Review the transcription. Tap a symbol for alternatives." }
                is InkLineResult.Rejected -> notice = result.reason
                is List<*> -> { candidates = result.filterIsInstance<InkCandidate>(); notice = "Choose the matching symbol to append it to the expression." }
              }
              workspace.recognizedStrokes = ink; workspace.recognizedMode = readingMode
            }
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { notice = e.message ?: "The ink could not be read. Edit the expression below." }
        finally { if (strokes == ink && mode == readingMode) reading = false }
    }
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
                    FilterChip(mode == item, onClick = { mode = item; notice = null }, enabled = !saving && !erasingGesture && current.isEmpty(), label = { Text(item) })
                }
            }
            Text(when (mode) {
                "Math layout" -> "Leave gaps between symbols. Use raised powers or stacked fractions. Results appear after pen-up."
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
                (strokes + listOf(current)).forEach { path ->
                    if (path.size == 1) drawCircle(p.ink, 2.dp.toPx(), Offset(path[0].x, path[0].y))
                    path.zipWithNext().forEach { (a, b) -> drawLine(p.ink, Offset(a.x, a.y), Offset(b.x, b.y), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round) }
                }
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
            if (reading) LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let { Text(it, color = p.amber2, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 6.dp)) }
            val symbols = if (reviewTokens.isNotEmpty()) reviewTokens.map { it.text } else if (lineDraft != null) lineSymbols else emptyList()
            if (symbols.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                symbols.forEachIndexed { index, symbol -> TextButton(enabled = !reading && !saving && !erasingGesture && current.isEmpty(), onClick = { selectedGlyph = index },
                    modifier = Modifier.semantics { contentDescription = "Correct symbol ${index + 1}: $symbol" }) { Text("${index + 1}: $symbol") } }
            }
            OutlinedTextField(expression, { if (it.length <= CalcSession.MAX_ENTRY_CHARS) { expression = it; inkBlocked = false } else { notice = "The pasted text is too long and was not inserted."; inkBlocked = true } }, enabled = !reading && !saving && !erasingGesture && current.isEmpty(),
                keyboardOptions = MathKeyboard, label = { Text("Expression · edit to correct") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            Text("Apply a function to this expression, or start typed entry if empty", fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf("sqrt", "sin", "cos", "tan", "ln", "log", "abs").forEach { function ->
                    TextButton(enabled = !reading && !saving && !erasingGesture && current.isEmpty(), onClick = {
                        val changed = if (expression.isBlank()) "$function(" else "$function($expression)"
                        if (changed.length <= RecognitionCalculator.MAX_CHARS) {
                            expression = changed; inkBlocked = false
                        } else notice = "The expression is full. Shorten it before adding a function."
                    }) { Text(function) }
                }
            }
            Text("${settings.angle.name} · New ink replaces the draft. Cursive and unclear symbols need correction.", fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
            RecognitionAnswer(expression, settings, environment, enabled = !reading && current.isEmpty() && !erasingGesture && !inkBlocked, saving = saving, onSave = onUse)
            Spacer(Modifier.height(16.dp))
        }
    }
    selectedGlyph?.let { index ->
        val token = reviewTokens.getOrNull(index)
        val options = token?.let { (listOf(it.text) + it.alternatives).filter { symbol -> symbol in InkReview.symbols }.distinct() }
            ?: lineDraft?.glyphs?.getOrNull(index)?.candidates?.map { it.symbol }.orEmpty()
        val palette = if (token != null) InkReview.symbols else InkReview.lineSymbols
        fun choose(symbol: String) {
            if (token != null) workspace.replaceInkSymbol(index, symbol)
            else if (index in lineSymbols.indices && symbol in palette) {
                lineSymbols = lineSymbols.toMutableList().also { it[index] = symbol }
                expression = lineSymbols.joinToString(""); selectedGlyph = null
            }
        }
        if (token != null || options.isNotEmpty()) AlertDialog(onDismissRequest = { if (!saving) selectedGlyph = null },
            title = { Text("Symbol ${index + 1}: choose a match") },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("Choosing a symbol rebuilds the transcription from all the ink groups. Make text edits afterward. If a group contains several symbols, erase and redraw it.")
                if (options.isNotEmpty()) {
                    Text("Suggested matches", modifier = Modifier.padding(top = 12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        options.forEach { symbol -> TextButton(enabled = !saving && !reading, onClick = { choose(symbol) },
                            modifier = Modifier.semantics { contentDescription = "Suggested symbol $symbol" }) { Text(symbol, fontSize = 24.sp) } }
                    }
                }
                Text("Choose another symbol", modifier = Modifier.padding(top = 8.dp))
                palette.chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { symbol -> TextButton(enabled = !saving && !reading, onClick = { choose(symbol) },
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Replace with $symbol" },
                            contentPadding = PaddingValues(0.dp)) { Text(symbol, fontSize = 22.sp) } }
                    }
                }
            } }, confirmButton = { TextButton(enabled = !saving, onClick = { selectedGlyph = null }) { Text("Keep current") } })
    }
}
