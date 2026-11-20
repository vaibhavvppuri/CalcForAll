package com.vaibhav.calc.android

/** Bounded monotone grayscale contrast. No thresholding, stroke synthesis, or OCR feedback. */
internal class PhotoToneCurve private constructor(val low: Int, val high: Int) {
    fun value(gray: Int): Int {
        require(gray in 0..255)
        return if (low == high) gray else ((gray - low).coerceIn(0, high - low) * 255 + (high - low) / 2) / (high - low)
    }
    fun argb(gray: Int): Int = value(gray).let { 0xff000000.toInt() or (it shl 16) or (it shl 8) or it }

    companion object {
        fun gray(argb: Int, treatment: PhotoImageTreatment): Int {
            val alpha = (argb ushr 24) and 255
            val luminance = (((argb ushr 16) and 255) * 77 + ((argb ushr 8) and 255) * 150 +
                (argb and 255) * 29 + 128) / 256
            val onWhite = (luminance * alpha + 255 * (255 - alpha) + 127) / 255
            return if (treatment == PhotoImageTreatment.INVERT_CONTRAST) 255 - onWhite else onWhite
        }

        fun fromHistogram(histogram: IntArray): PhotoToneCurve {
            require(histogram.size == 256 && histogram.all { it >= 0 }) { "Invalid image histogram." }
            val total = histogram.sumOf { it.toLong() }
            require(total in 1..(PhotoCropGeometry.MAX_EDGE.toLong() * PhotoCropGeometry.MAX_EDGE)) {
                "Image histogram exceeds the pixel limit."
            }
            val tail = maxOf(1L, total / 200) // Clip at most half a percent at either end.
            fun quantile(target: Long): Int {
                var sum = 0L
                for (i in histogram.indices) { sum += histogram[i]; if (sum >= target) return i }
                error("Incomplete image histogram")
            }
            var low = quantile(tail)
            var high = quantile(total - tail + 1)
            if (low == high) {
                // A small decimal point can occupy less than the clipped tail. Preserve
                // it rather than treating a mostly blank page as completely uniform.
                low = histogram.indexOfFirst { it > 0 }
                high = histogram.indexOfLast { it > 0 }
            }
            return PhotoToneCurve(low, high)
        }
    }
}
