package com.vaibhav.calc.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import java.util.concurrent.CancellationException

/** Manual geometry changes pixels only. Run within PhotoStaging's image preparation lane. */
object PhotoImageGeometry {
    /** Positive degrees turn clockwise. Identity can return [image]; input is never recycled. */
    fun apply(image: Bitmap, adjustment: PhotoGeometryAdjustment = PhotoGeometryAdjustment.NONE,
              shouldStop: () -> Boolean = { false }): Bitmap {
        fun checkActive() {
            if (Thread.currentThread().isInterrupted || shouldStop()) throw CancellationException("Photo correction cancelled.")
        }
        checkActive()
        require(!image.isRecycled) { "The image is no longer available." }
        val plan = PhotoGeometry.plan(image.width, image.height, adjustment)
        checkActive()
        if (plan.identity) return image
        val matrix = Matrix().apply { setValues(plan.matrixValues()) }
        val selected = Path().apply {
            adjustment.quad.points().forEachIndexed { index, point ->
                val x = (point.x * image.width).toFloat(); val y = (point.y * image.height).toFloat()
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val output = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.ARGB_8888)
        try {
            checkActive()
            Canvas(output).apply {
                drawColor(Color.WHITE)
                concat(matrix)
                clipPath(selected)
                drawBitmap(image, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            checkActive()
            return output
        } catch (problem: Throwable) {
            output.recycle() // Only this unpublished output is owned here.
            throw problem
        }
    }
}
