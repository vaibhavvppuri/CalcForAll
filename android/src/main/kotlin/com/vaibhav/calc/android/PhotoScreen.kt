package com.vaibhav.calc.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vaibhav.calc.android.theme.LocalCalcPalette
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun PhotoScreen(onClose: () -> Unit, onUse: (String) -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val p = LocalCalcPalette.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun read(image: Bitmap) {
        busy = true; error = null
        scope.launch {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val recognized = suspendCancellableCoroutine<String> { continuation ->
                    recognizer.process(InputImage.fromBitmap(image, 0))
                        .addOnSuccessListener { result -> if(continuation.isActive) continuation.resume(result.text) }
                        .addOnFailureListener { failure -> if(continuation.isActive) continuation.resumeWithException(failure) }
                }
                text = recognized.take(CalcSession.MAX_ENTRY_CHARS)
                if(recognized.isBlank()) error = "No text was found. Try a sharper, closer image with good lighting."
                else if(recognized.length > CalcSession.MAX_ENTRY_CHARS) error = "Only the first 8,192 characters are shown. Select a smaller expression."
            } catch(e: CancellationException) { throw e }
              catch(e: Exception) { error = "Could not read this image: ${e.message ?: "recognition unavailable"}" }
            finally { recognizer.close(); busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if(uri != null) {
            busy = true; error = null; text = ""
            scope.launch {
                try {
                    val image = withContext(Dispatchers.IO) { readPhoto(context, uri) }
                    bitmap = image
                    read(image)
                } catch(e: CancellationException) { throw e }
                  catch(e: Exception) { error = e.message ?: "Could not open this image."; busy = false }
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = p.paper) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Photo text", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose) { Text("Close") }
            }
            Text("Reads printed text on your device. No image upload or model download.",
                color = p.blue, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            Text("Review and correct every expression before using it. Fractions, superscripts, handwriting and word problems may not be interpreted correctly. This is text recognition, not a photo math solver.", fontSize = 13.sp)
            Button(onClick = { picker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("Choose an image") }
            bitmap?.let { image ->
                Image(image.asImageBitmap(), contentDescription = "Selected image preview", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp).background(p.sheet))
                Row {
                    TextButton(enabled = !busy, onClick = {
                        val rotated = Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply { postRotate(90f) }, true)
                        bitmap = rotated; read(rotated)
                    }) { Text("Rotate 90°") }
                    TextButton(enabled = !busy, onClick = { read(image) }) { Text("Read again") }
                }
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
            OutlinedTextField(text, { text = it.take(CalcSession.MAX_ENTRY_CHARS) }, label = { Text("Review and correct the expression") },
                enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), maxLines = 10)
            Button(onClick = { onUse(text.trim()) }, enabled = !busy && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Use reviewed expression") }
            Text("The image is held in memory for this screen and is not saved in your history.", fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** Bounds encoded bytes, dimensions and decoded pixels before allocating an image. */
private fun readPhoto(context: Context, uri: Uri): Bitmap {
    val maxBytes = 20 * 1024 * 1024
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while(true) {
            val read = input.read(buffer)
            if(read < 0) break
            require(output.size().toLong() + read <= maxBytes) { "Choose an image smaller than 20 MB." }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: throw IllegalArgumentException("The image could not be opened.")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth in 1..50000 && bounds.outHeight in 1..50000) { "Unsupported image or invalid image dimensions." }
    var sample = 1
    while(bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: throw IllegalArgumentException("This image format cannot be decoded on this device.")
    val orientation = try { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        catch(_: java.io.IOException) { ExifInterface.ORIENTATION_NORMAL }
    val transform = Matrix().apply {
        when(orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { setRotate(180f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
    return if(transform.isIdentity) image else Bitmap.createBitmap(image, 0, 0, image.width, image.height, transform, true).also { image.recycle() }
}
