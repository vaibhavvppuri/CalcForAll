package com.vaibhav.calc.android

import android.graphics.Bitmap
import java.util.concurrent.CancellationException

object PhotoImageQuality {
    /** At most 128×128 positions and three bounded row buffers; never alters/recycles input. */
    fun assess(image: Bitmap, shouldStop: () -> Boolean = { false }): PhotoQualityReport {
        fun checkActive() {
            if (Thread.currentThread().isInterrupted || shouldStop()) throw CancellationException("Photo assessment cancelled.")
        }
        checkActive()
        require(!image.isRecycled && image.width in 1..PhotoCropGeometry.MAX_EDGE && image.height in 1..PhotoCropGeometry.MAX_EDGE) {
            "Use an image with edges no larger than 2048 pixels."
        }
        val width = image.width; val height = image.height
        val row = IntArray(width); val above = IntArray(width); val below = IntArray(width)
        val stepX = (width + 127) / 128; val stepY = (height + 127) / 128
        val samples = PhotoQualitySamples()
        fun gray(pixel: Int) = PhotoToneCurve.gray(pixel, PhotoImageTreatment.ORIGINAL)
        for (y in 0 until height step stepY) {
            checkActive()
            image.getPixels(row, 0, width, 0, y, width, 1)
            image.getPixels(above, 0, width, 0, maxOf(0, y - 1), width, 1)
            image.getPixels(below, 0, width, 0, minOf(height - 1, y + 1), width, 1)
            for (x in 0 until width step stepX) samples.add(gray(row[x]), gray(row[maxOf(0, x - 1)]),
                gray(row[minOf(width - 1, x + 1)]), gray(above[x]), gray(below[x]))
        }
        checkActive()
        return samples.report()
    }
}
