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

@Composable
fun HandwritingScreen(onClose: () -> Unit, onUse: (String) -> Unit) {
    BackHandler(onBack = onClose)
    val p = LocalCalcPalette.current
    var strokes by remember { mutableStateOf<List<List<InkPoint>>>(emptyList()) }
    var current by remember { mutableStateOf<List<InkPoint>>(emptyList()) }
    var expression by remember { mutableStateOf("") }
    var stylusOnly by remember { mutableStateOf(false) }
    val candidates = remember(strokes) { InkRecognizer.recognize(strokes) }
    Surface(Modifier.fillMaxSize(), color = p.paper) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Handwriting", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose) { Text("Close") }
            }
            Text("Experimental symbol pad · entirely offline", color = p.amber2, fontSize = 13.sp)
            Text("Write one digit or operator, then choose its match. Build your expression one symbol at a time. This pad does not read a whole handwritten equation.",
                fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Stylus only (ignore fingers)", modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(stylusOnly, onCheckedChange = { stylusOnly = it },
                    modifier = Modifier.semantics { contentDescription = "Stylus only, ignore fingers" })
            }
            Canvas(Modifier.fillMaxWidth().weight(1f).heightIn(min = 150.dp).background(p.sheet).border(1.dp, p.sheetEdge)
                .pointerInput(stylusOnly) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if(!stylusOnly || down.type == PointerType.Stylus || down.type == PointerType.Eraser) {
                            if(down.type == PointerType.Eraser) { strokes = emptyList(); current = emptyList() }
                            else if(strokes.size < 32) {
                                current = listOf(InkPoint(down.position.x, down.position.y))
                                while(true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if(!change.pressed) break
                                    if(current.size < 2000) current = current + InkPoint(change.position.x, change.position.y)
                                    change.consume()
                                }
                                if(current.isNotEmpty()) strokes = strokes + listOf(current)
                                current = emptyList()
                            }
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
                        expression += if(candidate.symbol == "√") "sqrt(" else candidate.symbol
                        strokes = emptyList()
                    }) { Text(candidate.symbol, fontSize = 24.sp) }
                }
            }
            Row {
                TextButton(onClick = { strokes = strokes.dropLast(1) }) { Text("Undo stroke") }
                TextButton(onClick = { strokes = emptyList() }) { Text("Clear pad") }
                TextButton(onClick = { expression += "." }) { Text("Decimal .") }
            }
            OutlinedTextField(expression, { expression = it.take(2048) }, label = { Text("Review expression") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            Button(onClick = { onUse(expression) }, enabled = expression.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Use reviewed expression") }
        }
    }
}
