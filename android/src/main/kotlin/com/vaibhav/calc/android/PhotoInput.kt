package com.vaibhav.calc.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/** Bounded encoded source retained only while the photo draft is alive. No decoder is shared. */
class PhotoSource internal constructor(
    private val encoded: ByteArray,
    val rawWidth: Int,
    val rawHeight: Int,
    val exifOrientation: Int,
) {
    init {
        require(encoded.size in 1..PhotoInput.MAX_BYTES) { "Choose a nonempty image smaller than 20 MB." }
        PhotoCropGeometry.checkDimensions(rawWidth, rawHeight)
        require(exifOrientation in 1..8) { "Invalid image orientation." }
    }
    val encodedByteCount: Int get() = encoded.size

    fun preview(quarterTurns: Int = 0): Bitmap = crop(PhotoRegion.FULL, quarterTurns)

    /** Decode the selected original pixels before downsampling, preserving small-expression detail. */
    @Suppress("DEPRECATION") // The byte-array overload is available on every supported Android API.
    fun crop(region: PhotoRegion, quarterTurns: Int = 0): Bitmap {
        val raw = PhotoCropGeometry.rawRegion(rawWidth, rawHeight, exifOrientation, region, quarterTurns)
        val options = BitmapFactory.Options().apply {
            inSampleSize = PhotoCropGeometry.sampleSize(raw.width, raw.height)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val decoder = try { BitmapRegionDecoder.newInstance(encoded, 0, encoded.size, false) }
            catch (problem: java.io.IOException) {
                throw IllegalArgumentException("This image cannot be cropped safely on this device. Choose a JPEG, PNG or supported WebP image.", problem)
            } ?: throw IllegalArgumentException("This image format cannot be decoded on this device.")
        val image = try {
            require(decoder.width == rawWidth && decoder.height == rawHeight) { "The image dimensions changed while decoding." }
            decoder.decodeRegion(Rect(raw.left, raw.top, raw.right, raw.bottom), options)
                ?: throw IllegalArgumentException("The selected image region cannot be decoded.")
        } finally { decoder.recycle() }
        if (image.width !in 1..PhotoCropGeometry.MAX_EDGE || image.height !in 1..PhotoCropGeometry.MAX_EDGE) {
            image.recycle()
            throw IllegalArgumentException("The image decoder exceeded the crop size limit.")
        }
        val transform = Matrix().apply {
            when (exifOrientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> { setRotate(180f); postScale(-1f, 1f) }
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
            postRotate(PhotoCropGeometry.turns(quarterTurns) * 90f)
        }
        if (transform.isIdentity) return image
        var oriented: Bitmap? = null
        try {
            val result = Bitmap.createBitmap(image, 0, 0, image.width, image.height, transform, true)
            oriented = result
            return result
        } finally {
            // This unpublished decode is ours; never recycle a previously displayed bitmap.
            if (oriented !== image) image.recycle()
        }
    }
}

object PhotoInput {
    internal const val MAX_BYTES = 20 * 1024 * 1024
    /** Only our private capture directory is shared, and only by a temporary URI grant. */
    fun createCapture(context: Context): Pair<String, Uri> {
        val directory = File(context.cacheDir, "captures").apply { check(mkdirs() || isDirectory) }
        // Recover abandoned captures after process death without deleting a recent camera handoff.
        val staleBefore = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        directory.listFiles()?.take(100)?.filter { it.isFile && it.lastModified() < staleBefore }?.forEach {
            runCatching { discardCapture(context, it.absolutePath) }
        }
        val file = File.createTempFile("photo-", ".jpg", directory)
        return file.absolutePath to FileProvider.getUriForFile(context, "${context.packageName}.capture", file)
    }

    fun captureUri(context: Context, path: String): Uri {
        val file = checkedCapture(context, path)
        return FileProvider.getUriForFile(context, "${context.packageName}.capture", file)
    }

    fun discardCapture(context: Context, path: String) {
        checkedCapture(context, path).delete()
    }

    private fun checkedCapture(context: Context, path: String): File {
        val directory = File(context.cacheDir, "captures").canonicalFile
        val file = File(path).canonicalFile
        require(file.parentFile == directory && file.name.startsWith("photo-") && file.extension == "jpg") { "Invalid capture location." }
        return file
    }

    /** Bounds encoded bytes, dimensions and decoded pixels before allocating an image. */
    fun decode(context: Context, uri: Uri): Bitmap = open(context, uri).preview()

    fun open(context: Context, uri: Uri): PhotoSource {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readBounded(it) }
            ?: throw IllegalArgumentException("The image could not be opened.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        PhotoCropGeometry.checkDimensions(bounds.outWidth, bounds.outHeight)
        val orientation = try { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            catch (_: java.io.IOException) { ExifInterface.ORIENTATION_NORMAL }
        return PhotoSource(bytes, bounds.outWidth, bounds.outHeight, orientation.takeIf { it in 1..8 } ?: 1)
    }

    internal fun readBounded(input: InputStream, maxBytes: Int = MAX_BYTES): ByteArray {
        require(maxBytes in 1..MAX_BYTES)
        val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
        val buffer = ByteArray(minOf(maxBytes, 16 * 1024))
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) {
                // A misbehaving provider must not trap the image reader in a zero-progress loop.
                val next = input.read()
                if (next < 0) break
                require(output.size() < maxBytes) { "Choose an image smaller than 20 MB." }
                output.write(next)
            } else {
                require(output.size().toLong() + read <= maxBytes) { "Choose an image smaller than 20 MB." }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }
}
