package com.vaibhav.calc.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PhotoQualitySpec : StringSpec({
    "dark uniform input gives lighting advice without claiming a recognition failure" {
        val samples = PhotoQualitySamples()
        repeat(100) { samples.add(20, 20, 20, 20, 20) }
        val report = samples.report()
        report.mostlyDark shouldBe true; report.lowContrast shouldBe true; report.softEdges shouldBe false
        report.sampleCount shouldBe 100
        report.messages.any { it.contains("Light text on dark") } shouldBe true
    }
    "faint but sharp print suggests contrast without inventing blur" {
        val samples = PhotoQualitySamples()
        repeat(100) { samples.add(235, 212, 235, 235, 235); samples.add(212, 235, 212, 212, 212) }
        val report = samples.report()
        report.lowContrast shouldBe true; report.mostlyDark shouldBe false; report.softEdges shouldBe false
    }
    "a strong sharp edge remains distinct from a smooth low-curvature transition" {
        val sharp = PhotoQualitySamples(); val soft = PhotoQualitySamples()
        repeat(100) {
            sharp.add(255, 0, 255, 255, 255); sharp.add(0, 0, 255, 0, 0)
            val gray = 60 + it
            soft.add(gray, gray - 10, gray + 10, gray, gray)
        }
        sharp.report().softEdges shouldBe false
        sharp.report().messages shouldBe emptyList()
        soft.report().softEdges shouldBe true
        soft.report().messages.single().contains("cannot restore missing detail") shouldBe true
    }
    "sampling has a strict fixed budget and rejects invalid pixel values" {
        val samples = PhotoQualitySamples()
        shouldThrow<IllegalArgumentException> { samples.report() }
        shouldThrow<IllegalArgumentException> { samples.add(-1, 0, 0, 0, 0) }
        repeat(PhotoQualitySamples.MAX_SAMPLES) { samples.add(255, 255, 255, 255, 255) }
        samples.report().sampleCount shouldBe PhotoQualitySamples.MAX_SAMPLES
        shouldThrow<IllegalArgumentException> { samples.add(255, 255, 255, 255, 255) }
    }
})
