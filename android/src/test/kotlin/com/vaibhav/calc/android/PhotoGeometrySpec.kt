package com.vaibhav.calc.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.hypot

class PhotoGeometrySpec : StringSpec({
    fun close(actual: Double, expected: Double, tolerance: Double = 1e-7) {
        (abs(actual - expected) <= tolerance) shouldBe true
    }
    "identity retains every edge and coordinate without unnecessary sampling" {
        for ((width, height) in listOf(1 to 1, 700 to 220, 2048 to 2048)) {
            val plan = PhotoGeometry.plan(width, height, PhotoGeometryAdjustment.NONE)
            plan.identity shouldBe true
            plan.width shouldBe width; plan.height shouldBe height
            plan.map(width / 2.0, height / 2.0) shouldBe (width / 2.0 to height / 2.0)
        }
    }
    "rectangular corner selection maps only that selected region to the output" {
        val quad = PhotoQuad(PhotoPoint(.25, .125), PhotoPoint(.75, .125), PhotoPoint(.75, .875), PhotoPoint(.25, .875))
        val plan = PhotoGeometry.plan(1000, 800, PhotoGeometryAdjustment(quad))
        plan.width shouldBe 500; plan.height shouldBe 600
        val expected = listOf(0.0 to 0.0, 500.0 to 0.0, 500.0 to 600.0, 0.0 to 600.0)
        quad.points().zip(expected).forEach { (point, destination) ->
            val actual = plan.map(point.x * 1000, point.y * 800)
            close(actual.first, destination.first); close(actual.second, destination.second)
        }
        plan.map(500.0, 400.0).let { close(it.first, 250.0); close(it.second, 300.0) }
    }
    "a known projective warp restores the independent interior point and straight line" {
        // Independent camera model: X=.1+.8*u/(1+.5*v), Y=.1+.8*v/(1+.5*v).
        val quad = PhotoQuad(PhotoPoint(.1, .1), PhotoPoint(.9, .1), PhotoPoint(19.0 / 30, 19.0 / 30), PhotoPoint(.1, 19.0 / 30))
        val plan = PhotoGeometry.plan(1000, 1000, PhotoGeometryAdjustment(quad))
        val expectedHeight = hypot(800.0 / 3, 1600.0 / 3)
        val center = plan.map(420.0, 420.0)
        close(center.first, 400.0); close(center.second, expectedHeight / 2)
        // Three points on u=1/2 must become a vertical line after correction.
        for (v in listOf(.2, .5, .8)) {
            val mapped = plan.map((.1 + .4 / (1 + .5 * v)) * 1000, (.1 + .8 * v / (1 + .5 * v)) * 1000)
            close(mapped.first, 400.0); close(mapped.second, expectedHeight * v)
        }
    }
    "positive small-angle rotation is clockwise and expands rather than trims" {
        val plan = PhotoGeometry.plan(600, 400, PhotoGeometryAdjustment(straightenDegrees = 10.0))
        (plan.width > 600 && plan.height > 400) shouldBe true
        val left = plan.map(100.0, 100.0); val right = plan.map(500.0, 100.0)
        (right.second > left.second) shouldBe true
        close(hypot(right.first - left.first, right.second - left.second), 400.0)
    }
    "every selected corner remains in the bounded canvas for both rotation signs" {
        val quad = PhotoQuad(PhotoPoint(.125, .0625), PhotoPoint(.875, .125), PhotoPoint(.9375, .875), PhotoPoint(.0625, .9375))
        for ((width, height) in listOf(2048 to 2048, 2048 to 800, 800 to 2048, 1 to 2000))
            for (angle in listOf(-20.0, -3.0, 0.0, 3.0, 20.0)) {
                val selected = if (width == 1) PhotoQuad.FULL else quad
                val plan = PhotoGeometry.plan(width, height, PhotoGeometryAdjustment(selected, angle))
                (plan.width in 1..2048 && plan.height in 1..2048) shouldBe true
                selected.points().forEach {
                    val mapped = plan.map(it.x * width, it.y * height)
                    (mapped.first >= -1e-7 && mapped.first <= plan.width + 1e-7) shouldBe true
                    (mapped.second >= -1e-7 && mapped.second <= plan.height + 1e-7) shouldBe true
                }
            }
    }
    "four clockwise turns preserve selection winding and the straightening angle" {
        val original = PhotoGeometryAdjustment(PhotoQuad(PhotoPoint(.125, .25), PhotoPoint(.875, .125),
            PhotoPoint(.75, .875), PhotoPoint(.25, .75)), -7.0)
        var rotated = original
        repeat(4) {
            rotated = rotated.rotateClockwise()
            rotated.straightenDegrees shouldBe -7.0
            PhotoGeometry.plan(800, 600, rotated).identity shouldBe false
        }
        rotated shouldBe original
        PhotoGeometryAdjustment.NONE.rotateClockwise() shouldBe PhotoGeometryAdjustment.NONE
        original.rotateClockwise().quad.topLeft shouldBe PhotoPoint(.25, .25)
    }
    "crossed reversed concave collinear and tiny selections refuse before bitmap work" {
        val bad = listOf(
            PhotoQuad(PhotoPoint(0.0, 0.0), PhotoPoint(1.0, 1.0), PhotoPoint(1.0, 0.0), PhotoPoint(0.0, 1.0)),
            PhotoQuad(PhotoPoint(0.0, 0.0), PhotoPoint(0.0, 1.0), PhotoPoint(1.0, 1.0), PhotoPoint(1.0, 0.0)),
            PhotoQuad(PhotoPoint(0.0, 0.0), PhotoPoint(1.0, 0.0), PhotoPoint(.25, .25), PhotoPoint(0.0, 1.0)),
            PhotoQuad(PhotoPoint(0.0, .5), PhotoPoint(.25, .5), PhotoPoint(.5, .5), PhotoPoint(.75, .5)),
            PhotoQuad(PhotoPoint(.1, .1), PhotoPoint(.11, .1), PhotoPoint(.11, .11), PhotoPoint(.1, .11)))
        bad.forEach { shouldThrow<IllegalArgumentException> { PhotoGeometry.plan(1000, 1000, PhotoGeometryAdjustment(it)) } }
    }
    "nonfinite coordinates unsupported angles and oversized rasters refuse" {
        shouldThrow<IllegalArgumentException> { PhotoPoint(Double.NaN, .5) }
        shouldThrow<IllegalArgumentException> { PhotoPoint(.5, Double.POSITIVE_INFINITY) }
        shouldThrow<IllegalArgumentException> { PhotoPoint(-.01, .5) }
        for (angle in listOf(-20.01, 20.01, Double.NaN, Double.NEGATIVE_INFINITY))
            shouldThrow<IllegalArgumentException> { PhotoGeometryAdjustment(straightenDegrees = angle) }
        shouldThrow<IllegalArgumentException> { PhotoGeometry.plan(2049, 1, PhotoGeometryAdjustment.NONE) }
        shouldThrow<IllegalArgumentException> { PhotoGeometry.plan(0, 800, PhotoGeometryAdjustment.NONE) }
    }
})
