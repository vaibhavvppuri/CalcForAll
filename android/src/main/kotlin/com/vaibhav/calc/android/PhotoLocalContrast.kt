package com.vaibhav.calc.android

import java.util.concurrent.CancellationException

/** Explicit dark-ink/light-paper assistance. No OCR, thresholded mask, or new stroke detail. */
internal object PhotoLocalContrast {
    /** Original gray bytes remain untouched. Peak full-raster scratch is one more ByteArray:
     * at most 8 MiB including [gray], plus two short deque buffers and a histogram. */
    fun normalize(width: Int, height: Int, gray: ByteArray,
                  shouldStop: () -> Boolean = { false }): ByteArray {
        require(width in 1..PhotoCropGeometry.MAX_EDGE && height in 1..PhotoCropGeometry.MAX_EDGE &&
            gray.size == width * height) { "Use a bounded grayscale image with edges no larger than 2048 pixels." }
        fun checkActive() {
            if (Thread.currentThread().isInterrupted || shouldStop()) throw CancellationException("Shadow reduction cancelled.")
        }
        checkActive()
        var darkest = 255; var lightest = 0
        for (y in 0 until height) {
            checkActive()
            for (x in 0 until width) {
                val value = gray[y * width + x].toInt() and 255
                darkest = minOf(darkest, value); lightest = maxOf(lightest, value)
            }
        }
        val background = gray.copyOf()
        // Uniform dark/blank captures contain no recoverable contrast. Do not invent it.
        if (lightest - darkest < 12) return background
        val radius = (minOf(width, height) / 12).coerceIn(12, 96)
        val positions = IntArray(maxOf(width, height))
        val values = IntArray(positions.size)

        fun pass(horizontal: Boolean, maximum: Boolean) {
            val lines = if (horizontal) height else width
            val length = if (horizontal) width else height
            for (line in 0 until lines) {
                checkActive()
                var head = 0; var tail = 0; var next = 0
                fun index(position: Int) = if (horizontal) line * width + position else position * width + line
                for (position in 0 until length) {
                    val end = minOf(length - 1, position + radius)
                    while (next <= end) {
                        // Values are copied before in-place output; the deque must never
                        // refer back to a previously overwritten raster cell.
                        val value = background[index(next)].toInt() and 255
                        while (tail > head && (if (maximum) values[tail - 1] <= value else values[tail - 1] >= value)) tail--
                        positions[tail] = next; values[tail] = value; tail++; next++
                    }
                    while (positions[head] < position - radius) head++
                    background[index(position)] = values[head].toByte()
                }
            }
        }
        // Rectangular grayscale closing fills narrow dark strokes in the background
        // estimate while preserving slowly varying illumination more faithfully than
        // using a local maximum alone. It cannot recover blur or absent ink.
        pass(horizontal = true, maximum = true)
        pass(horizontal = false, maximum = true)
        pass(horizontal = true, maximum = false)
        pass(horizontal = false, maximum = false)
        val histogram = IntArray(256)
        for (y in 0 until height) {
            checkActive()
            for (x in 0 until width) {
                val index = y * width + x
                val original = gray[index].toInt() and 255
                val paper = maxOf(original, background[index].toInt() and 255, 1)
                val value = ((original * 255 + paper / 2) / paper).coerceIn(0, 255)
                background[index] = value.toByte()
                histogram[value]++
            }
        }
        val curve = PhotoToneCurve.fromHistogram(histogram)
        for (y in 0 until height) {
            checkActive()
            for (x in 0 until width) {
                val index = y * width + x
                background[index] = curve.value(background[index].toInt() and 255).toByte()
            }
        }
        checkActive()
        return background
    }
}
