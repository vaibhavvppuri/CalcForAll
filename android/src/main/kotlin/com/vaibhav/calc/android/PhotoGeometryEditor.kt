package com.vaibhav.calc.android

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.calc.android.theme.LocalCalcPalette
import kotlin.math.min
import kotlin.math.round

/** Controls edit source coordinates; pixels are transformed only after explicit Apply. */
@Composable
internal fun PhotoGeometryEditor(image: Bitmap, adjustment: PhotoGeometryAdjustment,
                                 enabled: Boolean, onChange: (PhotoGeometryAdjustment) -> Unit,
                                 onApply: () -> Unit, onCancel: () -> Unit) {
    val palette = LocalCalcPalette.current
    var selected by remember { mutableIntStateOf(0) }
    val current by rememberUpdatedState(adjustment)
    val change by rememberUpdatedState(onChange)
    val labels = listOf("1 · Top left", "2 · Top right", "3 · Bottom right", "4 · Bottom left")
    fun moveCorner(point: PhotoPoint) {
        val quad = current.quad
        change(current.copy(quad = when (selected) {
            0 -> quad.copy(topLeft = point)
            1 -> quad.copy(topRight = point)
            2 -> quad.copy(bottomRight = point)
            else -> quad.copy(bottomLeft = point)
        }))
    }
    Text("Adjust angle / perspective", style = MaterialTheme.typography.titleMedium)
    Text("For a slanted page, choose each corner below, then tap its position in the photo. Include every sign, fraction bar and power. Use Straighten for a small tilt.", fontSize = 12.sp, lineHeight = 18.sp)
    Box(Modifier.fillMaxWidth().height(260.dp).background(palette.sheet)) {
        Image(image.asImageBitmap(), "Original crop for angle correction", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Move ${labels[selected]} by tapping inside the image" }
            .pointerInput(image, selected, enabled) {
                if (enabled) detectTapGestures { tap ->
                    val scale = min(size.width.toFloat() / image.width, size.height.toFloat() / image.height)
                    val width = image.width * scale; val height = image.height * scale
                    val x = (tap.x - (size.width - width) / 2) / width
                    val y = (tap.y - (size.height - height) / 2) / height
                    if (x in 0f..1f && y in 0f..1f) moveCorner(PhotoPoint(x.toDouble(), y.toDouble()))
                }
            }) {
            val scale = min(size.width / image.width, size.height / image.height)
            val width = image.width * scale; val height = image.height * scale
            val offset = Offset((size.width - width) / 2, (size.height - height) / 2)
            val points = adjustment.quad.points().map { offset + Offset((it.x * width).toFloat(), (it.y * height).toFloat()) }
            points.forEachIndexed { index, point ->
                drawLine(palette.blue, point, points[(index + 1) % 4], 2.dp.toPx())
                drawCircle(palette.paper, 7.dp.toPx(), point)
                drawCircle(palette.blue, if (index == selected) 10.dp.toPx() else 6.dp.toPx(), point, style = Stroke(3.dp.toPx()))
            }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label -> FilterChip(selected == index, enabled = enabled,
            onClick = { selected = index }, label = { Text(label) }) }
    }
    // Sliders provide fine adjustment without requiring a precise touch on a corner.
    val point = adjustment.quad.points()[selected]
    Text("Selected corner: ${labels[selected]}", fontSize = 12.sp)
    Text("Left / right", fontSize = 12.sp)
    Slider(point.x.toFloat(), enabled = enabled, onValueChange = { moveCorner(point.copy(x = it.toDouble())) },
        modifier = Modifier.semantics { contentDescription = "Selected corner horizontal position" })
    Text("Top / bottom", fontSize = 12.sp)
    Slider(point.y.toFloat(), enabled = enabled, onValueChange = { moveCorner(point.copy(y = it.toDouble())) },
        modifier = Modifier.semantics { contentDescription = "Selected corner vertical position" })
    Text("Straighten: ${round(adjustment.straightenDegrees * 10) / 10}°", fontSize = 12.sp)
    Slider(adjustment.straightenDegrees.toFloat(), enabled = enabled, valueRange = -20f..20f, steps = 79,
        onValueChange = { onChange(adjustment.copy(straightenDegrees = it.toDouble())) },
        modifier = Modifier.semantics { contentDescription = "Straighten image angle" })
    Text("Negative angles turn left; positive angles turn right. Applying changes the preview and reads that image again.", fontSize = 12.sp, lineHeight = 18.sp)
    TextButton(enabled = enabled, onClick = { onChange(PhotoGeometryAdjustment.NONE) }) { Text("Reset corner and angle choices") }
    Button(enabled = enabled, onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Apply corrections and read") }
    TextButton(enabled = enabled, onClick = onCancel) { Text("Cancel angle correction") }
}
