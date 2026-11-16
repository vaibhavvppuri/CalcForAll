import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The B1/B2/B3 calculator battery harness. TEST SOURCES ONLY — there is no
// src/main here, and nothing in this module ships. It reads a battery JSON,
// drives :core through its public evaluation door, and writes per-case results.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(project(":core"))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.test {
    useJUnitPlatform()
    // Keep Gradle's default failure for an unmatched test filter. A filtered run
    // that executes nothing must not look green; use the unfiltered full gate.
    // Which chunk to run, where the battery lives, where results go, and which
    // tokens calibration found unsupported — all supplied per invocation.
    listOf("battery.chunk", "battery.file", "battery.out", "battery.unsupported").forEach { k ->
        systemProperty(k, System.getProperty(k) ?: "")
    }
    // The chunk is a runtime input; never serve a cached result for a different one.
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
