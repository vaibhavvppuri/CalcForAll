package com.vaibhav.calc.android

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InkGeometrySpec : StringSpec({
    "eraser removes a fast stroke at its visible midpoint" {
        val ink = listOf(listOf(InkPoint(10f, 10f), InkPoint(10f, 210f)))
        InkGeometry.erase(ink, InkPoint(10f, 100f), InkPoint(10f, 100f), 4f) shouldBe emptyList()
    }
    "swept eraser crosses a stroke even when both input endpoints miss" {
        val ink = listOf(listOf(InkPoint(10f, 10f), InkPoint(10f, 210f)))
        InkGeometry.erase(ink, InkPoint(0f, 100f), InkPoint(20f, 100f), 2f) shouldBe emptyList()
    }
    "nearby disjoint strokes remain and a tap can be erased" {
        val line = listOf(InkPoint(100f, 10f), InkPoint(100f, 210f))
        val dot = listOf(InkPoint(10f, 100f))
        InkGeometry.erase(listOf(line, dot), InkPoint(0f, 100f), InkPoint(20f, 100f), 2f) shouldBe listOf(line)
    }
    "collinear eraser beyond the end does not erase a disjoint line" {
        val ink = listOf(listOf(InkPoint(10f, 10f), InkPoint(10f, 20f)))
        InkGeometry.erase(ink, InkPoint(10f, 100f), InkPoint(10f, 110f), 2f) shouldBe ink
    }
    "resizing preserves a uniform paper scale and maps the letterbox correctly" {
        val view = InkViewport(600f, 400f, 1200f, 400f)
        view.scale shouldBe 1f
        view.toPaper(300f, 0f) shouldBe InkPoint(0f, 0f)
        view.toPaper(900f, 400f) shouldBe InkPoint(600f, 400f)
        view.contains(299f, 200f) shouldBe false
        val narrow = InkViewport(600f, 400f, 300f, 400f)
        narrow.scale shouldBe .5f
        narrow.toPaper(150f, 200f) shouldBe InkPoint(300f, 200f)
    }
})
