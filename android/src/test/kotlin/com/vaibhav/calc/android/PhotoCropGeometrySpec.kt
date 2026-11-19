package com.vaibhav.calc.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PhotoCropGeometrySpec : StringSpec({
    "all EXIF orientations and user rotations map each visible tile back to its original pixels" {
        // Independently drawn orientation grids for original ABCD/EFGH, not the inverse implementation.
        val oriented = listOf(listOf("ABCD", "EFGH"), listOf("DCBA", "HGFE"),
            listOf("HGFE", "DCBA"), listOf("EFGH", "ABCD"), listOf("AE", "BF", "CG", "DH"),
            listOf("EA", "FB", "GC", "HD"), listOf("HD", "GC", "FB", "EA"), listOf("DH", "CG", "BF", "AE"))
        for (orientation in 1..8) {
            var grid = oriented[orientation - 1]
            for (turns in 0..3) {
                for (row in grid.indices) for (column in grid[row].indices) {
                    val region = PhotoRegion(column.toDouble() / grid[row].length, row.toDouble() / grid.size,
                        (column + 1.0) / grid[row].length, (row + 1.0) / grid.size)
                    val original = grid[row][column] - 'A'
                    withClue("orientation=$orientation turns=$turns tile=${grid[row][column]}") {
                        PhotoCropGeometry.rawRegion(400, 200, orientation, region, turns) shouldBe
                            PhotoPixelRegion(original % 4 * 100, original / 4 * 100, (original % 4 + 1) * 100, (original / 4 + 1) * 100)
                    }
                }
                val old = grid
                grid = List(old.first().length) { x -> old.indices.reversed().joinToString("") { y -> old[y][x].toString() } }
            }
        }
    }

    "repeated crops compose within the original region and rotation retains that selection" {
        val region = PhotoRegion(.25, .125, .75, .875)
        region.subregion(PhotoRegion(.25, .5, .75, 1.0)) shouldBe PhotoRegion(.375, .5, .625, .875)
        region.subregion(PhotoRegion.FULL) shouldBe region
        region.rotateClockwise() shouldBe PhotoRegion(.125, .25, .875, .75)
        region.rotateClockwise().rotateClockwise().rotateClockwise().rotateClockwise() shouldBe region
        PhotoCropGeometry.rawRegion(400, 200, 6, region.rotateClockwise(), 1) shouldBe
            PhotoCropGeometry.rawRegion(400, 200, 6, region, 0)
    }

    "sampling rounds dimensions upward before enforcing the output edge budget" {
        PhotoCropGeometry.sampleSize(2048, 2048) shouldBe 1
        PhotoCropGeometry.sampleSize(2049, 1) shouldBe 2
        PhotoCropGeometry.sampleSize(4097, 1) shouldBe 4
        for (width in listOf(1, 2048, 2049, 4096, 4097, 8193, 10000, 50000)) {
            val sample = PhotoCropGeometry.sampleSize(width, 100)
            ((width - 1) / sample + 1 <= 2048) shouldBe true
            (sample and (sample - 1)) shouldBe 0
            if (sample > 1) ((width - 1) / (sample / 2) + 1 > 2048) shouldBe true
        }
    }

    "fractional boundaries include edge pixels and tiny valid crops remain nonempty" {
        PhotoCropGeometry.rawRegion(10, 10, 1, PhotoRegion(.21, .01, .29, .09), 0) shouldBe PhotoPixelRegion(2, 0, 3, 1)
        for (orientation in 1..8) for (turns in -4..4)
            PhotoCropGeometry.rawRegion(1, 1, orientation, PhotoRegion(.49, .49, .51, .51), turns) shouldBe PhotoPixelRegion(0, 0, 1, 1)
        PhotoCropGeometry.rawRegion(400, 200, 1, PhotoRegion.FULL, Int.MIN_VALUE) shouldBe PhotoPixelRegion(0, 0, 400, 200)
    }

    "invalid crop and source sizes refuse before a decoder or pixel array is allocated" {
        shouldThrow<IllegalArgumentException> { PhotoRegion(Double.NaN, 0.0, 1.0, 1.0) }
        shouldThrow<IllegalArgumentException> { PhotoRegion(0.0, 0.0, Double.POSITIVE_INFINITY, 1.0) }
        shouldThrow<IllegalArgumentException> { PhotoRegion(.5, 0.0, .5, 1.0) }
        shouldThrow<IllegalArgumentException> { PhotoRegion(-.1, 0.0, 1.0, 1.0) }
        shouldThrow<IllegalArgumentException> { PhotoCropGeometry.sampleSize(0, 10) }
        shouldThrow<IllegalArgumentException> { PhotoCropGeometry.sampleSize(50001, 1) }
        shouldThrow<IllegalArgumentException> { PhotoCropGeometry.sampleSize(50000, 50000) }
        shouldThrow<IllegalArgumentException> { PhotoCropGeometry.rawRegion(100, 100, 9, PhotoRegion.FULL, 0) }
        PhotoCropGeometry.sampleSize(10000, 10000) shouldBe 8
    }
})
