package com.vaibhav.calc.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.calc.android.theme.LocalCalcPalette
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.hypot

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun HandwritingScreen(settings: Settings, environment: Environment, saving: Boolean = false, onClose: () -> Unit, onUse: (String) -> Unit) {
    BackHandler(onBack = onClose)
    val p = LocalCalcPalette.current
    var strokes by remember { mutableStateOf<List<List<InkPoint>>>(emptyList()) }
    var redo by remember { mutableStateOf<List<List<InkPoint>>>(emptyList()) }
    var current by remember { mutableStateOf<List<InkPoint>>(emptyList()) }
    var expression by rememberSaveable { mutableStateOf("") }
    var stylusOnly by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf("Math layout") }
    var erase by remember { mutableStateOf(false) }
    var lineDraft by remember { mutableStateOf<InkLineResult.Draft?>(null) }
    var mathDraft by remember { mutableStateOf<MathLayoutResult.Draft?>(null) }
    var lineSymbols by remember { mutableStateOf<List<String>>(emptyList()) }
    var candidates by remember { mutableStateOf<List<InkCandidate>>(emptyList()) }
    var selectedGlyph by remember { mutableStateOf<Int?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }
    var inkBlocked by remember { mutableStateOf(false) }
    val lock = remember { Mutex() }
    LaunchedEffect(strokes, mode) {
        val ink = strokes; val readingMode = mode
        lineDraft = null; mathDraft = null; selectedGlyph = null; candidates = emptyList()
        if (ink.isEmpty()) { reading = false; return@LaunchedEffect }
        reading = true
        if (mode != "One symbol") expression = ""
        delay(650)
        try {
            val result = lock.withLock { withContext(Dispatchers.Default) {
                when (readingMode) {
                    "Math layout" -> MathInkRecognizer.read(ink)
                    "Spaced line" -> InkLineRecognizer.recognize(ink)
                    else -> InkRecognizer.recognize(ink)
                }
            } }
            if (strokes == ink && current.isEmpty() && mode == readingMode) when (result) {
                is MathLayoutResult.Draft -> { mathDraft = result; expression = result.expression; notice = result.warnings.joinToString(" ") }
                is MathLayoutResult.Rejected -> notice = result.reason
                is InkLineResult.Draft -> { lineDraft = result; lineSymbols = result.glyphs.map { it.candidates.first().symbol }; expression = result.text; notice = "Review the transcription. Tap a symbol for alternatives." }
                is InkLineResult.Rejected -> notice = result.reason
                is List<*> -> { candidates = result.filterIsInstance<InkCandidate>(); notice = "Choose the matching symbol to append it to the expression." }
            }
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { notice = e.message ?: "The ink could not be read. Edit the expression below." }
        finally { if (strokes == ink && mode == readingMode) reading = false }
    }
    Surface(Modifier.fillMaxSize(), color = p.paper) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Write & calculate", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close") }
            }
            Text("Write a calculation with a finger or stylus.", color = p.blue, fontSize = 13.sp, lineHeight = 19.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Math layout", "Spaced line", "One symbol").forEach { item ->
                    FilterChip(mode == item, onClick = { mode = item; notice = null }, label = { Text(item) })
                }
            }
            Text(when (mode) {
                "Math layout" -> "Leave gaps between symbols. Use raised powers or stacked fractions. Results appear after pen-up."
                "Spaced line" -> "Leave gaps between symbols on one line. Use Math layout for fractions and powers."
                else -> "Write one digit or operator, choose its match, then write the next."
            }, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Stylus only (ignore fingers)", modifier = Modifier.weight(1f), fontSize = 13.sp, lineHeight = 19.sp)
                Switch(stylusOnly, onCheckedChange = { stylusOnly = it }, modifier = Modifier.semantics { contentDescription = "Stylus only, ignore fingers" })
            }
            Canvas(Modifier.fillMaxWidth().height(240.dp).background(p.sheet).border(1.dp, p.sheetEdge)
                .semantics { contentDescription = "Handwriting pad" }
                .pointerInput(stylusOnly, mode, erase) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (!stylusOnly || down.type == PointerType.Stylus || down.type == PointerType.Eraser) {
                            down.consume()
                            val erasing = erase || down.type == PointerType.Eraser
                            if (erasing) {
                                fun removeNear(point: Offset) {
                                    val removed = strokes.filter { path -> path.any { hypot(it.x - point.x, it.y - point.y) < 18.dp.toPx() } }
                                    if (removed.isNotEmpty()) { strokes = strokes - removed.toSet(); redo = emptyList(); inkBlocked = false; if (mode != "One symbol") expression = "" }
                                }
                                removeNear(down.position)
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    removeNear(change.position); change.consume()
                                }
                            } else if (!inkBlocked && strokes.size < (if (mode == "One symbol") 32 else 64) && strokes.sumOf { it.size } < 12000) {
                                val pointBudget = minOf(InkStrokeBuffer.MAX_POINTS, 12000 - strokes.sumOf { it.size })
                                val capture = InkStrokeBuffer(pointBudget, size.width.toFloat(), size.height.toFloat())
                                capture.append(InkPoint(down.position.x, down.position.y))
                                current = capture.snapshot()
                                var ended = false
                                try {
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        // Fast/coalesced gestures may deliver no separate move event.
                                        // Keep the final position before handling the up transition.
                                        capture.append(InkPoint(change.position.x, change.position.y),
                                            change.historical.asSequence().map { InkPoint(it.position.x, it.position.y) })
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
                                        capture.completed()?.takeIf { it.isNotEmpty() }?.let { strokes = strokes + listOf(it); redo = emptyList() }
                                    }
                                } finally { current = emptyList() }
                            } else { inkBlocked = true; notice = "Ink limit reached. Undo or clear the pad before adding more." }
                        }
                    }
                }) {
                val grid = 24.dp.toPx()
                var x = grid; while (x < size.width) { drawLine(p.grid, Offset(x, 0f), Offset(x, size.height)); x += grid }
                var y = grid; while (y < size.height) { drawLine(p.grid, Offset(0f, y), Offset(size.width, y)); y += grid }
                mathDraft?.sourceTokens?.forEachIndexed { index, token ->
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
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { candidate -> OutlinedButton(onClick = {
                    val glyph = if (candidate.symbol == "√") "sqrt(" else candidate.symbol
                    if (expression.length + glyph.length > RecognitionCalculator.MAX_CHARS) {
                        notice = "The expression is full. Shorten it before adding this symbol; your ink is still on the pad."
                    } else {
                        expression += glyph
                        strokes = emptyList(); redo = emptyList(); notice = null
                    }
                }) { Text(candidate.symbol, fontSize = 24.sp) } }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(enabled = strokes.isNotEmpty(), onClick = { redo = redo + listOf(strokes.last()); strokes = strokes.dropLast(1); inkBlocked = false; if (mode != "One symbol") expression = ""; notice = null }) { Text("Undo stroke") }
                TextButton(enabled = redo.isNotEmpty(), onClick = { strokes = strokes + listOf(redo.last()); redo = redo.dropLast(1); inkBlocked = false; notice = null }) { Text("Redo stroke") }
                TextButton(onClick = { strokes = emptyList(); redo = emptyList(); inkBlocked = false; if (mode != "One symbol") expression = ""; notice = null }) { Text("Clear pad") }
                FilterChip(erase, onClick = { erase = !erase }, label = { Text("Eraser") })
            }
            if (reading) LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let { Text(it, color = p.amber2, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 6.dp)) }
            val symbols = mathDraft?.sourceTokens?.map { it.text } ?: if (lineDraft != null) lineSymbols else emptyList()
            if (symbols.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                symbols.forEachIndexed { index, symbol -> TextButton(onClick = { selectedGlyph = index }) { Text("${index + 1}: $symbol") } }
            }
            OutlinedTextField(expression, { if (it.length <= CalcSession.MAX_ENTRY_CHARS) { expression = it; inkBlocked = false } else { notice = "The pasted text is too long and was not inserted."; inkBlocked = true } }, enabled = !reading,
                keyboardOptions = MathKeyboard, label = { Text("Expression · edit to correct") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            Text("${settings.angle.name} · New ink replaces the draft. Cursive and unclear symbols need correction.", fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
            RecognitionAnswer(expression, settings, environment, enabled = !reading && current.isEmpty() && !inkBlocked, saving = saving, onSave = onUse)
            Spacer(Modifier.height(16.dp))
        }
    }
    selectedGlyph?.let { index ->
        val token = mathDraft?.sourceTokens?.getOrNull(index)
        val options = token?.let { (listOf(it.text) + it.alternatives).distinct() }
            ?: lineDraft?.glyphs?.getOrNull(index)?.candidates?.map { it.symbol }.orEmpty()
        if (options.isNotEmpty()) AlertDialog(onDismissRequest = { selectedGlyph = null },
            title = { Text("Symbol ${index + 1}: choose a match") },
            text = { Column {
                Text("Choosing a match rebuilds the transcription. Make manual edits afterward.")
                options.forEach { symbol -> TextButton(onClick = {
                    if (token != null) {
                        val tokens = mathDraft!!.sourceTokens.toMutableList().also { it[index] = token.copy(text = symbol, uncertain = false) }
                        when (val rebuilt = MathLayout.read(tokens)) {
                            is MathLayoutResult.Draft -> { mathDraft = rebuilt; expression = rebuilt.expression; notice = rebuilt.warnings.joinToString(" ") }
                            is MathLayoutResult.Rejected -> { expression = ""; notice = rebuilt.reason }
                        }
                    } else { lineSymbols = lineSymbols.toMutableList().also { it[index] = symbol }; expression = lineSymbols.joinToString("") }
                    selectedGlyph = null
                }) { Text(symbol, fontSize = 24.sp) } }
            } }, confirmButton = { TextButton(onClick = { selectedGlyph = null }) { Text("Keep current") } })
    }
}
