import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    // Package id is fixed (DECISIONS.md A4/D-000); display name deferred to M7.
    namespace = "com.vaibhav.calc.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vaibhav.calc"
        minSdk = 23
        targetSdk = 34
        versionCode = 5
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Local unit tests are kotest (JUnit 5), same as core — they run headless
        // on the JVM. src/androidTest holds the device gate (1.3): it runs the engine
        // and every catalogued tool on a real Android runtime, which is the only place
        // a Java member newer than minSdk shows itself. Run it with an emulator or
        // phone attached: `gradle :android:connectedDebugAndroidTest`.
        unitTests.all { it.useJUnitPlatform() }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
    // The app is a thin shell over the pure-Kotlin engine. No calculator logic
    // lives here — that would violate Article 1's spirit even if it compiled.
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    // Bundled model: first-use OCR works offline, with no runtime model download.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
