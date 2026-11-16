rootProject.name = "calc"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

// ---------------------------------------------------------------------------
// Article 1 — the module boundary, made structural.
//
// core/ and api/ are pure JVM modules and are ALWAYS in the build.
// android/ is only included when an Android SDK is actually present. This means
// a machine (or CI job) with no Android SDK builds core + api with zero Android
// anything on the classpath — the boundary is not a rule anyone remembers, it's
// the shape of the build. The core CI job runs WITHOUT an SDK on purpose: if an
// Android import ever leaks into core, that job cannot compile it.
// ---------------------------------------------------------------------------
include(":core", ":api")

// A TEST-ONLY harness module: it has no main source set at all, depends on :core
// only from `testImplementation`, and ships nothing. It lives outside :core's test
// source set on purpose — adding ~1000 data-driven cases there would move :core:test's
// receipted baseline count, and the baseline is evidence. Pure JVM; Article 1 untouched.
include(":battery")

val androidSdkPresent: Boolean =
    System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (androidSdkPresent) {
    include(":android")
} else {
    gradle.rootProject {
        logger.lifecycle(
            "[calc] No Android SDK detected — :android excluded from this build. " +
                "core/api are Android-free by construction (Constitution, Article 1)."
        )
    }
}
