// Root build script. No plugins are applied here — each module opts in.
// Declaring them with `apply false` just puts them on the classpath so modules
// can `alias(...)` them from the version catalog.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
}
