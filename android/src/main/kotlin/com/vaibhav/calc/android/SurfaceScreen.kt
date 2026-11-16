package com.vaibhav.calc.android

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.calc.android.theme.LocalCalcPalette
import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.graph.*
import com.vaibhav.calc.core.num.*
import kotlinx.coroutines.*
import kotlin.math.*

private data class SurfaceVertex(val x: Float, val y: Float, val z: Float)
private data class SurfaceDrawing(val points: List<SurfaceVertex?>, val grid: SurfaceGrid)

/** The sampled mathematics stays in core; Float is used only for screen projection. */
@Composable
fun SurfaceScreen(settings: Settings, onClose: () -> Unit, environment: Environment = Environment()) {
    BackHandler(onBack = onClose)
    val p = LocalCalcPalette.current
    var input by remember { mutableStateOf("x^2-y^2") }
    var boundsText by remember { mutableStateOf(listOf("-5", "5", "-5", "5")) }
    var showBounds by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf<SurfaceDrawing?>(null) }
    var plottedSurface by remember { mutableStateOf<PlottableSurface?>(null) }
    var plottedInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var yaw by remember { mutableStateOf(-.65f) }
    var pitch by remember { mutableStateOf(.65f) }
    var zoom by remember { mutableStateOf(1f) }
    var traceX by remember { mutableStateOf("0") }
    var traceY by remember { mutableStateOf("0") }
    var trace by remember { mutableStateOf<String?>(null) }
    var traceBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun number(text: String): Num = (Evaluator(settings, environment).eval(com.vaibhav.calc.core.parse.Parser.parse(text).getOrThrow()).getOrThrow() as? RealValue)?.num
        ?: throw IllegalArgumentException("Bounds and trace coordinates must be real numbers without units.")
    fun renderNumber(n: Num) = Formatter.decimal(RealValue(n), settings)
    fun plot() {
        if(busy) return
        val source = input
        val fields = boundsText
        busy = true; error = null; trace = null
        scope.launch {
            try {
                val computed = withContext(Dispatchers.Default) {
                    val nums = fields.map(::number)
                    val bounds = SurfaceBounds(nums[0], nums[1], nums[2], nums[3])
                    val surface = PlottableSurface.of(source, settings, environment).getOrThrow()
                    val grid = SurfaceSampler.sample(surface, bounds, 25, 25).getOrThrow()
                    val min = grid.minZ ?: Num.ZERO; val max = grid.maxZ ?: Num.ONE
                    val middle = (max + min) / Num.TWO
                    val span = if(max == min) Num.TWO else max - min
                    val points = grid.samples.map { point ->
                        (point as? SurfaceSample.Defined)?.let {
                            SurfaceVertex((((it.x - bounds.xMin) / (bounds.xMax - bounds.xMin)) * Num.TWO - Num.ONE).toBigDecimal(java.math.MathContext(16)).toFloat(),
                                (((it.y - bounds.yMin) / (bounds.yMax - bounds.yMin)) * Num.TWO - Num.ONE).toBigDecimal(java.math.MathContext(16)).toFloat(),
                                ((it.z - middle) / span * Num.TWO).toBigDecimal(java.math.MathContext(16)).toFloat())
                        }
                    }
                    surface to SurfaceDrawing(points, grid)
                }
                if(input == source && boundsText == fields) {
                    plottedSurface = computed.first; drawing = computed.second; plottedInput = source
                }
            } catch(e: CancellationException) { throw e }
              catch(e: Exception) { if(input == source && boundsText == fields) error = e.message ?: "Could not sample this surface." }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { plot() }
    Surface(Modifier.fillMaxSize(), color = p.paper) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val plotHeight = (maxHeight * .5f).coerceIn(260.dp, 480.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("3D surface", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onClose) { Text("Calculator") }
                }
                Text("Real z = f(x, y) · ${settings.angle} · sampled wireframe", fontSize = 12.sp)
                OutlinedTextField(input, { input = it.take(4096); error = null }, label = { Text("Expression in x and y") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), maxLines = 3,
                    trailingIcon = { TextButton(enabled = !busy, onClick = ::plot) { Text("Plot") } })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { showBounds = !showBounds }) { Text("Domain") }
                    TextButton(onClick = { yaw -= .25f }) { Text("Rotate ←") }
                    TextButton(onClick = { yaw += .25f }) { Text("Rotate →") }
                    TextButton(onClick = { pitch = (pitch + .2f).coerceAtMost(1.4f) }) { Text("Tilt ↑") }
                    TextButton(onClick = { pitch = (pitch - .2f).coerceAtLeast(-1.4f) }) { Text("Tilt ↓") }
                    TextButton(onClick = { yaw = -.65f; pitch = .65f; zoom = 1f }) { Text("Reset view") }
                }
                if(showBounds) {
                    listOf("x minimum" to "x maximum", "y minimum" to "y maximum").forEachIndexed { row, labels ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(labels.first, labels.second).forEachIndexed { col, label ->
                                val i = row * 2 + col
                                OutlinedTextField(boundsText[i], { next -> boundsText = boundsText.toMutableList().also { it[i] = next.take(40) } },
                                    label = { Text(label) }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Text("Press Plot after changing the domain. Bounds are limited to ±1,000,000.", fontSize = 11.sp)
                }
                Canvas(Modifier.fillMaxWidth().height(plotHeight).background(p.sheet).border(1.dp, p.sheetEdge)
                    .pointerInput(Unit) { detectTransformGestures { _, pan, factor, _ ->
                        yaw += pan.x * .008f; pitch = (pitch + pan.y * .008f).coerceIn(-1.4f, 1.4f)
                        zoom = (zoom * factor).coerceIn(.5f, 2.5f)
                    } }) {
                    fun project(v: SurfaceVertex): Offset {
                        val x = v.x * cos(yaw) - v.y * sin(yaw)
                        val depth = v.x * sin(yaw) + v.y * cos(yaw)
                        val y = v.z * cos(pitch) - depth * sin(pitch)
                        val scale = minOf(size.width, size.height) * .30f * zoom
                        return Offset(size.width / 2 + x * scale, size.height / 2 - y * scale)
                    }
                    clipRect {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.ink2.toArgb(); textSize = 12.sp.toPx() }
                        val origin = project(SurfaceVertex(-1f, -1f, -1f))
                        listOf("x" to SurfaceVertex(1.2f,-1f,-1f), "y" to SurfaceVertex(-1f,1.2f,-1f), "z" to SurfaceVertex(-1f,-1f,1.2f)).forEach { (label, end) ->
                            val point = project(end)
                            drawLine(p.ink3, origin, point, 1.4.dp.toPx())
                            drawContext.canvas.nativeCanvas.drawText(label, point.x + 4.dp.toPx(), point.y, paint)
                        }
                        drawing?.let { data ->
                            fun edge(i: Int, j: Int, color: Color) {
                                val a = data.points[i] ?: return; val b = data.points[j] ?: return
                                if(abs(a.z - b.z) > 1.3f) return
                                drawLine(color, project(a), project(b), 1.1.dp.toPx())
                            }
                            for(r in 0 until data.grid.rows) for(c in 0 until data.grid.columns) {
                                val i = r * data.grid.columns + c
                                if(c + 1 < data.grid.columns) edge(i, i + 1, p.blue.copy(alpha = .8f))
                                if(r + 1 < data.grid.rows) edge(i, i + data.grid.columns, p.amber2.copy(alpha = .75f))
                            }
                        }
                    }
                }
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Text("Drag to rotate · pinch to zoom · axes scaled independently to fit", fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                Row {
                    TextButton(onClick = { zoom = (zoom * 1.2f).coerceAtMost(2.5f) }) { Text("Zoom +") }
                    TextButton(onClick = { zoom = (zoom / 1.2f).coerceAtLeast(.5f) }) { Text("Zoom −") }
                }
                drawing?.let { data ->
                    Text("Plotted: $plottedInput", fontSize = 12.sp)
                    Text("z range: ${data.grid.minZ?.let(::renderNumber) ?: "none"} to ${data.grid.maxZ?.let(::renderNumber) ?: "none"}", fontSize = 12.sp)
                    Text("${data.grid.definedCount} values · ${data.grid.undefinedCount} undefined · ${data.grid.unevaluatedCount} not evaluated", fontSize = 11.sp)
                    val issue = data.grid.samples.firstOrNull { it is SurfaceSample.Unevaluated } as? SurfaceSample.Unevaluated
                    issue?.let { Text(it.error.message, fontSize = 12.sp, color = p.amber2) }
                    Text(data.grid.warnings.joinToString(" "), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(traceX, { traceX = it.take(80); trace = null }, label = { Text("Trace x") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(traceY, { traceY = it.take(80); trace = null }, label = { Text("Trace y") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                TextButton(enabled = plottedSurface != null && !busy && !traceBusy, onClick = {
                    val f = plottedSurface ?: return@TextButton
                    val xText = traceX; val yText = traceY
                    traceBusy = true
                    scope.launch {
                        try {
                        val answer = withContext(Dispatchers.Default) {
                            try {
                                when(val point = f.at(number(xText), number(yText))) {
                                    is SurfaceSample.Defined -> {
                                        val value = RealValue(point.z)
                                        val rounded = com.vaibhav.calc.core.format.OutcomeFormatter.isApproximate(value, settings)
                                        "z ${if(rounded) "≈" else "="} ${Formatter.exact(value, settings)}"
                                    }
                                    is SurfaceSample.Undefined -> "Undefined: ${point.error.message}"
                                    is SurfaceSample.Unevaluated -> "Not evaluated: ${point.error.message}"
                                }
                            } catch(e: CancellationException) { throw e }
                              catch(e: Exception) { e.message ?: "Invalid trace coordinates." }
                        }
                        if(f === plottedSurface && xText == traceX && yText == traceY) trace = answer
                        } finally { traceBusy = false }
                    }
                }) { Text(if(traceBusy) "Tracing…" else "Trace plotted surface") }
                trace?.let { Text(it, fontSize = 13.sp) }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
