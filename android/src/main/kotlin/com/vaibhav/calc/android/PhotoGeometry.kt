package com.vaibhav.calc.android

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Coordinates on the currently selected, quarter-turned source crop, before correction. */
data class PhotoPoint(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0) { "Keep every corner inside the image." } }
}

/** Explicit clockwise selection: top-left, top-right, bottom-right, bottom-left. */
data class PhotoQuad(val topLeft: PhotoPoint, val topRight: PhotoPoint, val bottomRight: PhotoPoint, val bottomLeft: PhotoPoint) {
    fun points(): List<PhotoPoint> = listOf(topLeft, topRight, bottomRight, bottomLeft)
    fun rotateClockwise(): PhotoQuad {
        fun turn(point: PhotoPoint) = PhotoPoint(1.0 - point.y, point.x)
        return PhotoQuad(turn(bottomLeft), turn(topLeft), turn(topRight), turn(bottomRight))
    }
    companion object {
        val FULL = PhotoQuad(PhotoPoint(0.0, 0.0), PhotoPoint(1.0, 0.0), PhotoPoint(1.0, 1.0), PhotoPoint(0.0, 1.0))
    }
}

data class PhotoGeometryAdjustment(val quad: PhotoQuad = PhotoQuad.FULL, val straightenDegrees: Double = 0.0) {
    init { require(straightenDegrees.isFinite() && straightenDegrees in -20.0..20.0) { "Straighten by no more than 20 degrees in either direction." } }
    fun rotateClockwise() = copy(quad = quad.rotateClockwise())
    companion object { val NONE = PhotoGeometryAdjustment() }
}

/** Fixed-size geometry only. No OCR feedback, image allocation, or automatic corner selection. */
internal object PhotoGeometry {
    data class Plan(val width: Int, val height: Int, private val values: List<Double>, val identity: Boolean) {
        fun matrixValues(): FloatArray = values.map { it.toFloat() }.toFloatArray()
        fun map(x: Double, y: Double): Pair<Double, Double> {
            val denominator = values[6] * x + values[7] * y + values[8]
            require(denominator.isFinite() && abs(denominator) > 1e-12) { "The selected perspective is too extreme." }
            return (values[0] * x + values[1] * y + values[2]) / denominator to
                (values[3] * x + values[4] * y + values[5]) / denominator
        }
    }

    fun plan(width: Int, height: Int, adjustment: PhotoGeometryAdjustment): Plan {
        require(width in 1..PhotoCropGeometry.MAX_EDGE && height in 1..PhotoCropGeometry.MAX_EDGE) {
            "Use an image with edges no larger than 2048 pixels."
        }
        if (adjustment == PhotoGeometryAdjustment.NONE)
            return Plan(width, height, listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0), true)
        val points = adjustment.quad.points()
        var twiceArea = 0.0
        val lengths = DoubleArray(4)
        for (index in 0..3) {
            val a = points[index]; val b = points[(index + 1) % 4]; val c = points[(index + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            require(cross > 1e-5) { "Corners must form a clear clockwise rectangle without crossing or folding." }
            val first = hypot((b.x - a.x) * width, (b.y - a.y) * height)
            val second = hypot((c.x - b.x) * width, (c.y - b.y) * height)
            require(adjustment.quad == PhotoQuad.FULL ||
                (first >= 8.0 && second >= 8.0 && cross * width * height / (first * second) >= 0.05)) {
                "The selected region is too narrow or its corners are too sharp. Crop the photo or retake it more squarely."
            }
            lengths[index] = first
            twiceArea += a.x * b.y - a.y * b.x
        }
        require(twiceArea >= 0.02) { "Select a region covering at least one percent of the image; use Crop for smaller detail." }
        val unit = unitTransform(points)
        val denominators = points.map { unit[6] * it.x + unit[7] * it.y + 1.0 }
        require(denominators.all { it > 0.0 } || denominators.all { it < 0.0 }) { "The selected perspective folds through infinity." }
        require(denominators.minOf { abs(it) } / denominators.maxOf { abs(it) } >= 0.02) {
            "The selected perspective is too extreme. Retake the photo more squarely."
        }
        // Opposing sides can have different photographed lengths. Retain the longer
        // one; this is an explicit visual approximation, not physical page calibration.
        val rectangleWidth = maxOf(lengths[0], lengths[2])
        val rectangleHeight = maxOf(lengths[1], lengths[3])
        val radians = adjustment.straightenDegrees * Math.PI / 180.0
        val cosine = cos(radians); val sine = sin(radians)
        val rotated = listOf(0.0 to 0.0, rectangleWidth * cosine to rectangleWidth * sine,
            rectangleWidth * cosine - rectangleHeight * sine to rectangleWidth * sine + rectangleHeight * cosine,
            -rectangleHeight * sine to rectangleHeight * cosine)
        val left = rotated.minOf { it.first }; val top = rotated.minOf { it.second }
        val spanWidth = rotated.maxOf { it.first } - left; val spanHeight = rotated.maxOf { it.second } - top
        val scale = minOf(1.0, PhotoCropGeometry.MAX_EDGE / spanWidth, PhotoCropGeometry.MAX_EDGE / spanHeight)
        val outWidth = ceil(spanWidth * scale).toInt().coerceIn(1, PhotoCropGeometry.MAX_EDGE)
        val outHeight = ceil(spanHeight * scale).toInt().coerceIn(1, PhotoCropGeometry.MAX_EDGE)
        val physical = listOf(rectangleWidth * unit[0] / width, rectangleWidth * unit[1] / height, rectangleWidth * unit[2],
            rectangleHeight * unit[3] / width, rectangleHeight * unit[4] / height, rectangleHeight * unit[5],
            unit[6] / width, unit[7] / height, 1.0)
        val result = List(9) { index ->
            val column = index % 3
            when (index / 3) {
                0 -> scale * (cosine * physical[column] - sine * physical[3 + column] - left * physical[6 + column])
                1 -> scale * (sine * physical[column] + cosine * physical[3 + column] - top * physical[6 + column])
                else -> physical[index]
            }
        }
        require(result.all { it.isFinite() && abs(it) <= 1e8 }) { "The selected image transform is not stable." }
        return Plan(outWidth, outHeight, result, false)
    }

    /** Eight unknowns, normalized coordinates and partial pivoting; always fixed bounded work. */
    private fun unitTransform(points: List<PhotoPoint>): DoubleArray {
        val destinations = listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0)
        val rows = Array(8) { DoubleArray(9) }
        for (index in points.indices) {
            val (x, y) = points[index]; val (u, v) = destinations[index]
            rows[2 * index] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            rows[2 * index + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        for (column in 0..7) {
            val pivot = (column..7).maxByOrNull { abs(rows[it][column]) }!!
            require(abs(rows[pivot][column]) >= 1e-10) { "The selected corners are too close to a line." }
            val swap = rows[column]; rows[column] = rows[pivot]; rows[pivot] = swap
            val divisor = rows[column][column]
            for (entry in column..8) rows[column][entry] /= divisor
            for (row in 0..7) if (row != column) {
                val factor = rows[row][column]
                for (entry in column..8) rows[row][entry] -= factor * rows[column][entry]
            }
        }
        return DoubleArray(9) { if (it == 8) 1.0 else rows[it][8] }
    }
}
