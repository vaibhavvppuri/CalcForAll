package com.vaibhav.calc.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CancellationException

class PhotoLocalContrastSpec : StringSpec({
    fun unsigned(bytes: ByteArray) = bytes.map { it.toInt() and 255 }

    "uniform dark gray and blank images are retained without invented contrast" {
        for (value in listOf(0, 31, 127, 254, 255)) {
            val original = ByteArray(40 * 30) { value.toByte() }
            unsigned(PhotoLocalContrast.normalize(40, 30, original)) shouldBe List(1200) { value }
            unsigned(original) shouldBe List(1200) { value }
        }
    }

    "optimized separable closing agrees with an independent two dimensional window oracle" {
        val width = 17; val height = 15; val radius = 12
        val original = ByteArray(width * height) { i -> (90 + (i * 37 + i / width * 13) % 150).toByte() }
        fun brute(input: List<Int>, maximum: Boolean) = List(input.size) { index ->
            val x = index % width; val y = index / width
            val neighbourhood = (maxOf(0, y - radius)..minOf(height - 1, y + radius)).flatMap { yy ->
                (maxOf(0, x - radius)..minOf(width - 1, x + radius)).map { xx -> input[yy * width + xx] }
            }
            if (maximum) neighbourhood.max() else neighbourhood.min()
        }
        val source = unsigned(original)
        val closed = brute(brute(source, true), false)
        val normalized = source.indices.map { i ->
            val paper = maxOf(source[i], closed[i], 1)
            (source[i] * 255 + paper / 2) / paper
        }
        val sorted = normalized.sorted()
        val tail = maxOf(1, normalized.size / 200)
        var low = sorted[tail - 1]; var high = sorted[sorted.size - tail]
        if (low == high) { low = sorted.first(); high = sorted.last() }
        val expected = normalized.map { value -> if (low == high) value else
            ((value - low).coerceIn(0, high - low) * 255 + (high - low) / 2) / (high - low) }
        unsigned(PhotoLocalContrast.normalize(width, height, original)) shouldBe expected
        unsigned(original) shouldBe source
    }

    "uneven illumination improves actual thin mark contrast without moving the marks" {
        val width = 256; val height = 160
        val gray = ByteArray(width * height) { i -> (100 + i % width / 2).toByte() }
        val marks = (45..110).map { y -> 60 to y } + (55..185).map { x -> x to 90 } + listOf(200 to 115)
        for ((x, y) in marks) gray[y * width + x] = (100 + x / 2 - 40).toByte()
        val before = gray.copyOf()
        val normalized = PhotoLocalContrast.normalize(width, height, gray)
        for ((x, y) in listOf(60 to 55, 150 to 90, 200 to 115)) {
            val ink = normalized[y * width + x].toInt() and 255
            val paper = normalized[(y + 3) * width + x + 3].toInt() and 255
            (paper - ink > 80) shouldBe true
        }
        gray.contentEquals(before) shouldBe true
    }

    "isolated decimal dots hairlines and connected black strokes are not deleted" {
        val width = 160; val height = 100
        val gray = ByteArray(width * height) { 255.toByte() }
        for (x in 30..125) gray[50 * width + x] = 0
        for (y in 25..75) gray[y * width + 70] = 0
        gray[75 * width + 140] = 0
        PhotoLocalContrast.normalize(width, height, gray).contentEquals(gray) shouldBe true
    }

    "a near uniform noisy blank is left unchanged" {
        val gray = ByteArray(60 * 60) { (160 + it % 5).toByte() }
        PhotoLocalContrast.normalize(60, 60, gray).contentEquals(gray) shouldBe true
    }

    "cancellation during preparation and each image pass leaves the input untouched" {
        val gray = ByteArray(64 * 64) { (it % 256).toByte() }
        val original = gray.copyOf()
        for (stopAt in listOf(1, 70, 140, 210, 280, 350, 420)) {
            var checks = 0
            shouldThrow<CancellationException> { PhotoLocalContrast.normalize(64, 64, gray) { ++checks >= stopAt } }
            gray.contentEquals(original) shouldBe true
        }
    }

    "invalid raster sizes and unbounded edges refuse before processing" {
        for ((width, height, size) in listOf(Triple(0, 10, 0), Triple(2049, 1, 2049), Triple(2, 2, 3))) {
            shouldThrow<IllegalArgumentException> { PhotoLocalContrast.normalize(width, height, ByteArray(size)) }
        }
    }
})
