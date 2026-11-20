package com.vaibhav.calc.android

import android.graphics.Bitmap
import android.graphics.Matrix

/** Explicit image operations. A treatment changes pixels, never the recognized expression. */
enum class PhotoImageTreatment { ORIGINAL, CONTRAST, INVERT_CONTRAST }

object PhotoImageAssist {
    /**
     * The result is the exact image to display and recognize. Identity may return [image];
     * otherwise the caller owns a new bitmap. This method never recycles its input.
     */
    fun apply(image: Bitmap, treatment: PhotoImageTreatment = PhotoImageTreatment.ORIGINAL,
              quarterTurns: Int = 0): Bitmap {
        require(!image.isRecycled && image.width in 1..PhotoCropGeometry.MAX_EDGE &&
            image.height in 1..PhotoCropGeometry.MAX_EDGE) { "Use an image with edges no larger than 2048 pixels." }
        val turns = PhotoCropGeometry.turns(quarterTurns)
        val prepared = if (treatment == PhotoImageTreatment.ORIGINAL) image else {
            val row = IntArray(image.width)
            val histogram = IntArray(256)
            for (y in 0 until image.height) {
                image.getPixels(row, 0, row.size, 0, y, row.size, 1)
                for (pixel in row) histogram[PhotoToneCurve.gray(pixel, treatment)]++
            }
            val curve = PhotoToneCurve.fromHistogram(histogram)
            val output = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            try {
                for (y in 0 until image.height) {
                    image.getPixels(row, 0, row.size, 0, y, row.size, 1)
                    for (x in row.indices) row[x] = curve.argb(PhotoToneCurve.gray(row[x], treatment))
                    output.setPixels(row, 0, row.size, 0, y, row.size, 1)
                }
                output
            } catch (problem: Throwable) {
                output.recycle()
                throw problem
            }
        }
        if (turns == 0) return prepared
        var rotated: Bitmap? = null
        try {
            val transform = Matrix().apply { setRotate(turns * 90f) }
            return Bitmap.createBitmap(prepared, 0, 0, prepared.width, prepared.height, transform, false)
                .also { rotated = it }
        } finally {
            if (prepared !== image && prepared !== rotated) prepared.recycle()
        }
    }
}
