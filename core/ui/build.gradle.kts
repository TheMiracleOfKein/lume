import java.time.Duration

// UI layer: screens, settings, components, theme, shell, navigation helpers and the two auth
// activities. Readers live in the feature modules and join the graph through ContentScreens. Packages stay com.lume.app.*; the
// namespace com.lume.app.ui only names this module's own generated classes. Screenshot tests
// (Roborazzi) and ViewModel tests live here.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.lume.app.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        disable.add("EditedTargetSdkVersion")
        // AGP is pinned to what the installed Android Studio can sync (see .github/dependabot.yml).
        disable.add("AndroidGradlePluginVersion")
    }

    testOptions {
        // Engines touch android.webkit / Context only through runCatching fallbacks.
        unitTests.isReturnDefaultValues = true
        // Robolectric (screenshot tests) needs the merged assets and resources.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.testLogging { events("started", "passed", "skipped", "failed"); showStandardStreams = true }
            // A hung test (e.g. a looper that never goes idle) fails the build instead of waiting for CI to kill it.
            test.timeout.set(Duration.ofMinutes(10))
        }
    }
}

// Screenshot tests (Roborazzi): golden images live in the repository under src/test/screenshots.
// A plain testDebugUnitTest skips the image step; recordRoborazziDebug writes the goldens and
// verifyRoborazziDebug compares against them (see docs/ARCHITECTURE.md, "Screenshot tests").
roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

dependencies {
    api(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    // Compose types appear in public signatures (LumeRoot, LumeTheme, LocalAppContainer).
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    // App lock: the system screen-lock prompt (fingerprint, face, PIN).
    implementation(libs.androidx.biometric)
    implementation(libs.zxing.embedded)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
