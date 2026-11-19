package com.vaibhav.calc.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.InputStream

class PhotoInputBoundsSpec : StringSpec({
    "encoded input accepts its exact byte limit and refuses one extra byte" {
        PhotoInput.readBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3).toList() shouldBe listOf<Byte>(1, 2, 3)
        shouldThrow<IllegalArgumentException> { PhotoInput.readBounded(ByteArrayInputStream(ByteArray(4)), 3) }
    }

    "a zero-progress bulk read falls back to single bytes and still obeys the budget" {
        fun source(size: Int) = object : InputStream() {
            var next = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
            override fun read(): Int = if (next < size) next++ else -1
        }
        PhotoInput.readBounded(source(3), 3).toList() shouldBe listOf<Byte>(0, 1, 2)
        shouldThrow<IllegalArgumentException> { PhotoInput.readBounded(source(4), 3) }
    }

    "invalid encoded budgets are rejected before reading the provider" {
        val untouched = object : InputStream() { override fun read(): Int = error("Input must not be read") }
        shouldThrow<IllegalArgumentException> { PhotoInput.readBounded(untouched, 0) }
        shouldThrow<IllegalArgumentException> { PhotoInput.readBounded(untouched, PhotoInput.MAX_BYTES + 1) }
    }
})
