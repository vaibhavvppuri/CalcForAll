package com.vaibhav.calc.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

object PhotoInput {
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
    fun decode(context: Context, uri: Uri): Bitmap {
        val maxBytes = 20 * 1024 * 1024
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                require(output.size().toLong() + read <= maxBytes) { "Choose an image smaller than 20 MB." }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IllegalArgumentException("The image could not be opened.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..50000 && bounds.outHeight in 1..50000) { "Unsupported image or invalid image dimensions." }
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IllegalArgumentException("This image format cannot be decoded on this device.")
        val orientation = try { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            catch (_: java.io.IOException) { ExifInterface.ORIENTATION_NORMAL }
        val transform = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> { setRotate(180f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        return if (transform.isIdentity) image else Bitmap.createBitmap(image, 0, 0, image.width, image.height, transform, true).also { image.recycle() }
    }
}
