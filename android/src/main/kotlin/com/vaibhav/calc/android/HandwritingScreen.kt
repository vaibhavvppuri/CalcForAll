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
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.calc.android.theme.LocalCalcPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HandwritingScreen(onClose: () -> Unit, onUse: (String) -> Unit) {
    BackHandler(onBack = onClose)
    val p = LocalCalcPalette.current
    var strokes by remember { mutableStateOf<List<List<InkPoint>>>(emptyList()) }
    var current by remember { mutableStateOf<List<InkPoint>>(emptyList()) }
    var expression by remember { mutableStateOf("") }
    var stylusOnly by remember { mutableStateOf(false) }
    var lineMode by remember { mutableStateOf(false) }
    var lineDraft by remember { mutableStateOf<InkLineResult.Draft?>(null) }
    var lineSymbols by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedGlyph by remember { mutableStateOf<Int?>(null) }
    var inkNotice by remember { mutableStateOf<String?>(null) }
    var readingLine by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val candidates = remember(strokes, lineMode) { if (lineMode) emptyList() else InkRecognizer.recognize(strokes) }
    Surface(Modifier.fillMaxSize(), color = p.paper) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Handwriting", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose) { Text("Close") }
            }
            Text("Experimental handwriting · entirely offline", color = p.amber2, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!lineMode, onClick = { lineMode = false; lineDraft = null; selectedGlyph = null; inkNotice = null }, label = { Text("One symbol") })
                FilterChip(lineMode, onClick = { lineMode = true; lineDraft = null; selectedGlyph = null; inkNotice = null }, label = { Text("Spaced line") })
            }
            Text(if (lineMode) "Leave gaps between symbols on one line. No stacked fractions, superscripts or radical bars. Every draft needs review."
                else "Write a digit or operator, then choose its match. Build your expression one symbol at a time.",
                fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Stylus only (ignore fingers)", modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(stylusOnly, onCheckedChange = { stylusOnly = it },
                    modifier = Modifier.semantics { contentDescription = "Stylus only, ignore fingers" })
            }
            Canvas(Modifier.fillMaxWidth().height(220.dp).background(p.sheet).border(1.dp, p.sheetEdge)
                .pointerInput(stylusOnly, lineMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if(!stylusOnly || down.type == PointerType.Stylus || down.type == PointerType.Eraser) {
                            if(down.type == PointerType.Eraser) { strokes = emptyList(); current = emptyList(); lineDraft = null; selectedGlyph = null }
                            else if(strokes.size < (if (lineMode) 64 else 32) && strokes.sumOf { it.size } < 12000) {
                                val pointBudget = minOf(2000, 12000 - strokes.sumOf { it.size })
                                current = listOf(InkPoint(down.position.x, down.position.y))
                                while(true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if(!change.pressed) break
                                    if(current.size < pointBudget) current = current + InkPoint(change.position.x, change.position.y)
                                    else inkNotice = "Ink limit reached. Undo or clear the pad before adding more."
                                    change.consume()
                                }
                                if(current.isNotEmpty()) strokes = strokes + listOf(current)
                                current = emptyList()
                                lineDraft = null
                            } else inkNotice = "Ink limit reached. Undo or clear the pad before adding more."
                        }
                    }
                }) {
                val grid = 24.dp.toPx()
                var x = grid; while(x < size.width) { drawLine(p.grid, Offset(x,0f), Offset(x,size.height)); x += grid }
                var y = grid; while(y < size.height) { drawLine(p.grid, Offset(0f,y), Offset(size.width,y)); y += grid }
                (strokes + listOf(current)).forEach { path ->
                    if(path.size == 1) drawCircle(p.ink, 2.dp.toPx(), Offset(path[0].x, path[0].y))
                    path.zipWithNext().forEach { (a,b) -> drawLine(p.ink, Offset(a.x,a.y), Offset(b.x,b.y), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round) }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { candidate ->
                    OutlinedButton(onClick = {
                        expression = (expression + if(candidate.symbol == "√") "sqrt(" else candidate.symbol).take(2048)
                        strokes = emptyList()
                    }) { Text(candidate.symbol, fontSize = 24.sp) }
                }
            }
            Row {
                TextButton(onClick = { strokes = strokes.dropLast(1); lineDraft = null; inkNotice = null }) { Text("Undo stroke") }
                TextButton(onClick = { strokes = emptyList(); lineDraft = null; inkNotice = null }) { Text("Clear pad") }
                if (!lineMode) TextButton(onClick = { expression = (expression + ".").take(2048) }) { Text("Decimal .") }
                else TextButton(enabled = !readingLine, onClick = {
                    val ink = strokes
                    readingLine = true; lineDraft = null; selectedGlyph = null
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.Default) { InkLineRecognizer.recognize(ink) }
                            if (strokes == ink && current.isEmpty() && lineMode) when (result) {
                                is InkLineResult.Rejected -> { lineDraft = null; inkNotice = result.reason }
                                is InkLineResult.Draft -> {
                                    lineDraft = result; lineSymbols = result.glyphs.map { it.candidates.first().symbol }
                                    expression = result.text
                                    inkNotice = "Unverified draft. Tap a symbol for alternatives, then edit and review below."
                                }
                            }
                        } finally {
                            readingLine = false
                        }
                    }
                }) { Text(if (readingLine) "Reading…" else "Read line") }
            }
            inkNotice?.let { Text(it, color = p.amber2, fontSize = 12.sp) }
            lineDraft?.let {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    lineSymbols.forEachIndexed { index, symbol ->
                        TextButton(onClick = { selectedGlyph = index }) { Text("${index + 1}: $symbol") }
                    }
                }
            }
            OutlinedTextField(expression, { expression = it.take(2048) }, enabled = !readingLine,
                label = { Text("Review expression") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            Button(onClick = { onUse(expression) }, enabled = expression.isNotBlank() && !readingLine,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Use reviewed expression") }
        }
    }
    selectedGlyph?.let { index ->
        val glyph = lineDraft?.glyphs?.getOrNull(index)
        if (glyph != null) AlertDialog(onDismissRequest = { selectedGlyph = null },
            title = { Text("Symbol ${index + 1}: choose a match") },
            text = { Column {
                Text("Choosing a match rebuilds the draft. Make manual edits afterward.")
                glyph.candidates.forEach { candidate -> TextButton(onClick = {
                    lineSymbols = lineSymbols.toMutableList().also { it[index] = candidate.symbol }
                    expression = lineSymbols.joinToString(""); selectedGlyph = null
                }) { Text(candidate.symbol, fontSize = 24.sp) } }
            } }, confirmButton = { TextButton(onClick = { selectedGlyph = null }) { Text("Keep current") } })
    }
}
