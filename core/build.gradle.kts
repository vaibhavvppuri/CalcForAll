import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.vaibhav.calc"
version = "0.1.0"

// PURE JVM. No Android plugin, no Android dependency, no Android SDK on the
// classpath — by construction. This is the wall Article 1 asks for: an Android
// import here is an unresolved reference the compiler cannot satisfy.
kotlin {
    compilerOptions {
        // Target 17 bytecode so android/ (AGP 8) can consume core without fuss,
        // while still compiling on the JDK 21 that's actually installed.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // big-math supplies BigDecimal transcendental kernels (exp/ln/sin/…/pow/pi/e)
    // so the engine never touches Double for computation. Pure JVM, no Android.
    implementation(libs.bigmath)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// ---------------------------------------------------------------------------
// Article 1 — secondary tripwire.
//
// The PRIMARY wall is the classpath: there is no Android SDK here, so any
// `import android.*` / `import androidx.*` fails to compile. This task is
// belt-and-suspenders — a fast, explicit signal in CI that names the offending
// file and line before the compiler even runs. It is NOT the enforcement; the
// enforcement is that the build physically cannot resolve the symbols.
// ---------------------------------------------------------------------------
val checkNoAndroidImports by tasks.registering {
    group = "verification"
    description = "Fails if any android/androidx import leaks into core."
    val ktFiles = fileTree("src") { include("**/*.kt") }
    inputs.files(ktFiles)
    doLast {
        val forbidden = Regex("""^\s*import\s+(android|androidx)\.""")
        val scanned = ktFiles.files.filter { it.isFile }
        // Article 11: a check that scans nothing must FAIL, not pass quietly. If the
        // source path ever moves, "0 offenders in 0 files" would report the boundary
        // wall as clean while having checked nothing at all.
        if (scanned.isEmpty()) {
            throw GradleException(
                "checkNoAndroidImports scanned 0 files — the source path is wrong. " +
                    "This check would otherwise PASS without checking anything (Article 11)."
            )
        }
        val offenders = scanned
            .mapNotNull { f ->
                val hits = f.readLines().withIndex()
                    .filter { (_, line) -> forbidden.containsMatchIn(line) }
                if (hits.isNotEmpty()) f to hits else null
            }
        if (offenders.isNotEmpty()) {
            val report = offenders.joinToString("\n") { (f, hits) ->
                "  ${f.relativeTo(projectDir)}\n" +
                    hits.joinToString("\n") { (i, l) -> "    line ${i + 1}: ${l.trim()}" }
            }
            throw GradleException(
                "Article 1 violation — Android import found in :core (the boundary wall):\n$report"
            )
        }
        // Report what was CHECKED, not just that nothing failed (Article 11).
        logger.lifecycle("[calc] checkNoAndroidImports: scanned ${scanned.size} files, no Android imports")
    }
}

tasks.named("check") { dependsOn(checkNoAndroidImports) }
