import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "com.vaibhav.calc"
version = "2.3.0"

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.vaibhav.calc.api.AppKt")
}

dependencies {
    implementation(project(":core")) // the SAME engine the app uses — Article 1's payoff

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.test { useJUnitPlatform() }

// Standalone server archives retain the project and upstream license notices.
distributions {
    named("main") {
        contents {
            from(rootProject.file("LICENSE"))
            from(rootProject.file("THIRD-PARTY-NOTICES.md"))
            from(rootProject.file("docs")) {
                include("API.md", "PRIVACY.md", "SYMBOLIC.md", "NUMERIC-LIMITS.md", "licenses/**")
                // OpenJDK desugaring supports Android only; its source archive travels with the Android/source release.
                exclude("licenses/corresponding-source/*.zip")
                into("docs")
            }
            from(rootProject.file("android/src/main/assets/fonts")) {
                include("OFL-*.txt")
                into("android/src/main/assets/fonts")
            }
        }
    }
}
