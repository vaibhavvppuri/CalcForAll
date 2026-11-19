package com.vaibhav.calc.android

import kotlin.math.ceil
import kotlin.math.floor

/** A nonempty region of the full image after EXIF orientation and user quarter-turns. */
data class PhotoRegion(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0.0..1.0 } &&
            left < right && top < bottom) { "Choose a nonempty crop inside the image." }
    }

    /** The selection is relative to this currently displayed region. */
    fun subregion(selection: PhotoRegion): PhotoRegion {
        fun coordinate(start: Double, end: Double, fraction: Double) = when (fraction) {
            0.0 -> start
            1.0 -> end
            else -> (start + (end - start) * fraction).coerceIn(start, end)
        }
        return PhotoRegion(coordinate(left, right, selection.left), coordinate(top, bottom, selection.top),
            coordinate(left, right, selection.right), coordinate(top, bottom, selection.bottom))
    }

    fun rotateClockwise() = PhotoRegion(1.0 - bottom, left, 1.0 - top, right)

    companion object { val FULL = PhotoRegion(0.0, 0.0, 1.0, 1.0) }
}

internal data class PhotoPixelRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Pure geometry: no bitmap allocation, Android API, or guessed OCR coordinates. */
internal object PhotoCropGeometry {
    const val MAX_EDGE = 2048
    const val MAX_SOURCE_EDGE = 50_000
    const val MAX_SOURCE_PIXELS = 100_000_000L

    fun checkDimensions(width: Int, height: Int) {
        require(width in 1..MAX_SOURCE_EDGE && height in 1..MAX_SOURCE_EDGE &&
            width.toLong() * height <= MAX_SOURCE_PIXELS) {
            "Choose an image with valid dimensions, no more than 100 megapixels and 50,000 pixels per edge."
        }
    }

    fun turns(quarterTurns: Int) = ((quarterTurns % 4) + 4) % 4

    fun sampleSize(width: Int, height: Int): Int {
        checkDimensions(width, height)
        var sample = 1
        // Decoders may round sampled dimensions upward. Floor division admitted 2049px.
        while ((width + sample - 1) / sample > MAX_EDGE || (height + sample - 1) / sample > MAX_EDGE) sample *= 2
        return sample
    }

    fun rawRegion(width: Int, height: Int, orientation: Int, region: PhotoRegion, quarterTurns: Int): PhotoPixelRegion {
        checkDimensions(width, height)
        require(orientation in 1..8) { "Invalid image orientation." }
        fun inverse(horizontal: Double, vertical: Double): Pair<Double, Double> {
            var x = horizontal; var y = vertical
            repeat(turns(quarterTurns)) { val oldX = x; x = y; y = 1.0 - oldX }
            return when (orientation) {
                2 -> 1.0 - x to y
                3 -> 1.0 - x to 1.0 - y
                4 -> x to 1.0 - y
                5 -> y to x
                6 -> y to 1.0 - x
                7 -> 1.0 - y to 1.0 - x
                8 -> 1.0 - y to x
                else -> x to y
            }
        }
        val corners = listOf(inverse(region.left, region.top), inverse(region.right, region.top),
            inverse(region.left, region.bottom), inverse(region.right, region.bottom))
        val left = floor(corners.minOf { it.first } * width).toInt().coerceIn(0, width - 1)
        val top = floor(corners.minOf { it.second } * height).toInt().coerceIn(0, height - 1)
        val right = ceil(corners.maxOf { it.first } * width).toInt().coerceIn(left + 1, width)
        val bottom = ceil(corners.maxOf { it.second } * height).toInt().coerceIn(top + 1, height)
        return PhotoPixelRegion(left, top, right, bottom)
    }
}
