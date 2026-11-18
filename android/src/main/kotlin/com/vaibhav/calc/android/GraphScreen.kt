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
import kotlin.math.abs

private data class DrawPoint(val x: Float, val y: Float)
private data class Curve(val name: String, val segments: List<List<DrawPoint>>, val gaps: Int, val holes: Int, val issue: String?)
private data class GraphScene(val curves: List<Curve>, val xTicks: List<Pair<Float, String>>, val yTicks: List<Pair<Float, String>>, val error: String? = null)

@Composable
fun GraphScreen(settings: Settings, initial: String, environment: Environment, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val p = LocalCalcPalette.current
    var input by remember { mutableStateOf(initial.takeIf { it.contains("x", true) && !it.contains('=') } ?: "x^2-4") }
    var plotted by remember { mutableStateOf(input) }
    var viewport by remember { mutableStateOf(Viewport.DEFAULT) }
    var scene by remember { mutableStateOf<GraphScene?>(null) }
    var busy by remember { mutableStateOf(false) }
    var analysis by remember { mutableStateOf<String?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var traceX by remember { mutableStateOf("0") }
    var editRange by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = listOf(p.blue, p.amber2, Color(0xff2a9379))
    val calculator = remember(settings, environment) { Calculator(settings).also { c ->
        environment.names().forEach { n -> environment.get(n)?.let { c.environment.set(n, it) } }
    } }
    val interactiveView by rememberUpdatedState(viewport)

    fun move(next: Viewport) {
        val lower = Num.ofDecimal("0.000001"); val upper = Num.of(2_000_000)
        if (next.width >= lower && next.height >= lower && next.width <= upper && next.height <= upper &&
            next.xMin.abs() <= upper && next.xMax.abs() <= upper && next.yMin.abs() <= upper && next.yMax.abs() <= upper) {
            viewport = next; analysis = null
        }
    }
    LaunchedEffect(plotted, viewport, settings, environment) {
        busy = true
        scene = null
        delay(100)
        try {
            scene = withContext(Dispatchers.Default) {
                val started = System.nanoTime()
                val sources = plotted.lines().map { it.trim().removePrefix("y=").removePrefix("y =") }.filter { it.isNotBlank() }
                require(sources.size in 1..3) { "Enter one to three functions, one per line." }
                val curves = sources.map { source ->
                    require(source.length <= 2048) { "A graph expression can contain up to 2,048 characters." }
                    val f = when (val result = calculator.plot(source)) {
                        is CalcResult.Ok -> result.value
                        is CalcResult.Err -> throw IllegalArgumentException(result.error.message)
                    }
                    var gaps = 0; var holes = 0
                    var issue: String? = null
                    val segments = mutableListOf<List<DrawPoint>>()
                    var current = mutableListOf<DrawPoint>()
                    fun end() { if(current.size > 1) segments += current; current = mutableListOf() }
                    repeat(321) { i ->
                        ensureActive()
                        require((System.nanoTime() - started) / 1_000_000 < 5000) { "Plot exceeded its time budget. Try a simpler expression or a smaller range." }
                        val x = viewport.xMin + viewport.width * Num.of(i.toLong(), 320)
                        when (val sample = f.at(x)) {
                            is Sample.Defined -> {
                                val point = DrawPoint(i / 320f, ((viewport.yMax - sample.y) / viewport.height).toBigDecimal(java.math.MathContext(16)).toFloat())
                                val previous = current.lastOrNull()
                                // Screen geometry only: never bridge an unresolved jump/asymptote.
                                if (!point.y.isFinite() || point.y !in -5f..6f) end()
                                else {
                                    if (previous != null && abs(previous.y - point.y) > 1.2f) end()
                                    current += point
                                }
                            }
                            is Sample.Undefined -> { gaps++; end() }
                            is Sample.Unevaluated -> { holes++; if(issue == null) issue = sample.error.message; end() }
                        }
                    }
                    end()
                    Curve(source, segments, gaps, holes, issue)
                }
                val ticks = Axes.forViewport(viewport, 6, settings.computeMc)
                fun label(n: Num) = Formatter.decimal(RealValue(n), settings.copy(displayPrecision = minOf(settings.displayPrecision, 6)))
                GraphScene(curves,
                    ticks.first.values.map { ((it - viewport.xMin) / viewport.width).toBigDecimal(java.math.MathContext(16)).toFloat() to label(it) },
                    ticks.second.values.map { ((viewport.yMax - it) / viewport.height).toBigDecimal(java.math.MathContext(16)).toFloat() to label(it) })
            }
        } catch(e: CancellationException) { throw e }
          catch(e: IllegalArgumentException) { scene = GraphScene(emptyList(), emptyList(), emptyList(), e.message) }
          catch(e: ArithFault) { scene = GraphScene(emptyList(), emptyList(), emptyList(), e.message) }
        finally { busy = false }
    }
    fun analyze(kind: String) {
        if (analyzing || busy) return
        analyzing = true
        val currentView = viewport; val currentInput = plotted; val currentTrace = traceX
        scope.launch {
            try {
                val resultText = withContext(Dispatchers.Default) {
                    val functions = currentInput.lines().filter { it.isNotBlank() }.map { calculator.plot(it.trim().removePrefix("y=").removePrefix("y =")).getOrThrow() }
                    fun fmt(n: Num): String {
                        val value = RealValue(n)
                        return (if(com.vaibhav.calc.core.format.OutcomeFormatter.isApproximate(value, settings)) "≈ " else "") + Formatter.exact(value, settings)
                    }
                    when (kind) {
                        "Trace" -> {
                            val x = (Evaluator(settings, environment).eval(com.vaibhav.calc.core.parse.Parser.parse(currentTrace).getOrThrow()).getOrThrow() as? RealValue)?.num
                                ?: throw IllegalArgumentException("Trace x must be a real number.")
                            functions.mapIndexed { i, f ->
                                "${i + 1}: " + when (val sample = f.at(x)) {
                                    is Sample.Defined -> "(${fmt(x)}, ${fmt(sample.y)})"
                                    is Sample.Undefined -> "undefined — ${sample.cause.message}"
                                    is Sample.Unevaluated -> "not evaluated — ${sample.error.message}"
                                }
                            }.joinToString("\n")
                        }
                        else -> {
                            val analysisJob = currentCoroutineContext()[Job]
                            val options = RootSearchOptions(shouldStop = { analysisJob?.isActive == false })
                            val result = if (kind == "Intersections") {
                                require(functions.size >= 2) { "Enter at least two functions for intersections." }
                                RootFinder.intersections(functions[0], functions[1], currentView.xMin, currentView.xMax, settings, options = options)
                            } else RootFinder.roots(functions.first(), currentView.xMin, currentView.xMax, settings, options = options)
                            when (result) {
                                is RootResult.Found -> (if (result.roots.isEmpty()) "No roots found in this range." else result.roots.joinToString("\n") {
                                    (if(result.method.startsWith("numeric")) "x ≈ ${Formatter.decimal(RealValue(it.x), settings)}" else "x = ${fmt(it.x)}") +
                                        " · residual ${Formatter.decimal(RealValue(it.residual), settings)}"
                                }) +
                                    "\n${result.method}" + if (result.method.startsWith("numeric")) "\nA numeric scan may miss tangent or closely spaced roots." else ""
                                is RootResult.Unverified -> result.verified.joinToString("\n") {
                                    if(it.residual.isZero) "Verified x = ${fmt(it.x)}" else "Verified x ≈ ${Formatter.decimal(RealValue(it.x), settings)}"
                                } + "\nUnverified: ${result.reason}"
                                is RootResult.Failed -> result.error.message
                            }
                        }
                    }
                }
                if(viewport == currentView && plotted == currentInput && (kind != "Trace" || traceX == currentTrace)) analysis = resultText
            } catch(e: CancellationException) { throw e }
              catch(e: Exception) { if(viewport == currentView && plotted == currentInput) analysis = e.message ?: "Could not analyze this graph." }
            finally { analyzing = false }
        }
    }
    Surface(Modifier.fillMaxSize(), color = p.paper) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val plotHeight = (maxHeight * .5f).coerceIn(220.dp, 480.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Graph", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose) { Text("Calculator") }
            }
            OutlinedTextField(input, { if(it.length <= 6144) input = it }, keyboardOptions = MathKeyboard, label = { Text("Functions of x · one per line · ${settings.angle}") },
                maxLines = 3, modifier = Modifier.fillMaxWidth(), trailingIcon = { TextButton(onClick = { plotted = input; analysis = null }) { Text("Plot") } })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { move(viewport.zoom(Num.of(1, 2))) }) { Text("Zoom +") }
                TextButton(onClick = { move(viewport.zoom(Num.TWO)) }) { Text("Zoom −") }
                TextButton(onClick = { viewport = Viewport.DEFAULT; analysis = null }) { Text("Reset") }
                TextButton(onClick = { editRange = true }) { Text("Range") }
            }
            val currentScene = scene
            Canvas(Modifier.fillMaxWidth().height(plotHeight).border(1.dp, p.sheetEdge)
                .background(p.sheet).pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val view = interactiveView
                        fun amount(f: Float): Num = Num.of((f * 10000f).toLong(), 10000)
                        val dx = view.width * amount(-pan.x / size.width.coerceAtLeast(1))
                        val dy = view.height * amount(pan.y / size.height.coerceAtLeast(1))
                        move(view.pan(dx, dy).zoom(amount((1f / zoom).coerceIn(.5f, 2f))))
                    }
                }) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.ink3.toArgb(); textSize = 10.sp.toPx() }
                clipRect {
                    currentScene?.xTicks?.forEach { (x, label) ->
                        drawLine(p.sheetEdge, Offset(x * size.width, 0f), Offset(x * size.width, size.height), 1.dp.toPx())
                        drawContext.canvas.nativeCanvas.drawText(label, x * size.width + 3.dp.toPx(), size.height - 4.dp.toPx(), paint)
                    }
                    currentScene?.yTicks?.forEach { (y, label) ->
                        drawLine(p.sheetEdge, Offset(0f, y * size.height), Offset(size.width, y * size.height), 1.dp.toPx())
                        drawContext.canvas.nativeCanvas.drawText(label, 3.dp.toPx(), y * size.height - 3.dp.toPx(), paint)
                    }
                    val zeroX = ((Num.ZERO - viewport.xMin) / viewport.width).toBigDecimal(java.math.MathContext(16)).toFloat() * size.width
                    val zeroY = (viewport.yMax / viewport.height).toBigDecimal(java.math.MathContext(16)).toFloat() * size.height
                    drawLine(p.ink3, Offset(zeroX, 0f), Offset(zeroX, size.height), 1.4.dp.toPx())
                    drawLine(p.ink3, Offset(0f, zeroY), Offset(size.width, zeroY), 1.4.dp.toPx())
                    currentScene?.curves?.forEachIndexed { index, curve -> curve.segments.forEach { segment ->
                        val path = Path()
                        segment.forEachIndexed { i, point -> if(i == 0) path.moveTo(point.x * size.width, point.y * size.height) else path.lineTo(point.x * size.width, point.y * size.height) }
                        val effect = when(index) {
                            1 -> PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()))
                            2 -> PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
                            else -> null
                        }
                        drawPath(path, colors[index], style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), pathEffect = effect))
                    } }
                }
            }
            if (busy || analyzing) LinearProgressIndicator(Modifier.fillMaxWidth())
            scene?.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            scene?.curves?.forEachIndexed { i, curve ->
                Text("${i + 1}. ${curve.name}" + if(curve.gaps + curve.holes > 0) " · ${curve.gaps} undefined, ${curve.holes} not evaluated" else "",
                    color = colors[i], fontSize = 11.sp, maxLines = 1)
                curve.issue?.let { Text(it, color = p.amber2, fontSize = 11.sp, maxLines = 2) }
            }
            Text("Drag to pan · pinch to zoom · sampled curves can miss narrow features", fontSize = 10.sp, color = p.ink3)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { analyze("Roots") }, enabled = !busy && !analyzing) { Text("Roots (1)") }
                TextButton(onClick = { analyze("Intersections") }, enabled = !busy && !analyzing) { Text("Intersect (1, 2)") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(traceX, { traceX = it.take(80) }, keyboardOptions = MathKeyboard, label = { Text("Trace x") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = { analyze("Trace") }, enabled = !busy && !analyzing, modifier = Modifier.height(56.dp)) { Text("Trace") }
            }
            analysis?.let { Text(it, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) }
        }
        }
    }
    if (editRange) RangeDialog(viewport, { editRange = false }) { next -> move(next); editRange = false }
}

@Composable
private fun RangeDialog(view: Viewport, dismiss: () -> Unit, apply: (Viewport) -> Unit) {
    var fields by remember { mutableStateOf(listOf(view.xMin, view.xMax, view.yMin, view.yMax).map { it.toBigDecimal(Num.MC).stripTrailingZeros().toPlainString() }) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Graph range") }, text = {
        Column {
            listOf("x minimum", "x maximum", "y minimum", "y maximum").forEachIndexed { i, label ->
                OutlinedTextField(fields[i], { value -> fields = fields.toMutableList().also { it[i] = value.take(30) } },
                    keyboardOptions = MathKeyboard, label = { Text(label) }, singleLine = true)
            }
            Text("Bounds within ±2,000,000; span 0.000001 to 2,000,000.", fontSize = 11.sp)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        try {
            val nums = fields.map { Num.ofDecimal(it.trim()) }
            val next = Viewport(nums[0], nums[1], nums[2], nums[3])
            val minimum: Num = Num.ofDecimal("0.000001"); val maximum: Num = Num.of(2_000_000)
            require(nums.all { it.abs() <= maximum } && next.width >= minimum && next.width <= maximum && next.height >= minimum && next.height <= maximum) { "Range exceeds the graph limits." }
            apply(next)
        } catch(e: Exception) { error = e.message ?: "Enter valid decimal bounds." }
    }) { Text("Apply") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
