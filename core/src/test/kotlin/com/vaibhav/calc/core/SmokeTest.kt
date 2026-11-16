package com.vaibhav.calc.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SmokeTest : StringSpec({
    "toolchain and kotest are wired" {
        BuildInfo.ENGINE shouldBe "calc-core"
    }
})
