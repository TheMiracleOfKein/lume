import java.time.Duration

// Comics mode: the image reader (paged and webtoon strip, tall-page tiles, zoom). It adds its
// screens to the app's navigation graph through ComicsScreens. Packages stay com.lume.app.*; the
// namespace com.lume.app.comics only names this module's own generated classes.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.lume.app.comics"
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
        unitTests.isReturnDefaultValues = true
        // Robolectric (screenshot and ViewModel tests) needs the merged assets and resources.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Live catalogue checks hit real sites: ./gradlew :feature:comics:testDebugUnitTest -Plume.live
            test.systemProperty("lume.live", project.hasProperty("lume.live").toString())
            project.findProperty("lume.live.sources")?.let { test.systemProperty("lume.live.sources", it.toString()) }
            test.systemProperty("lume.live.record", project.hasProperty("lume.live.record").toString())
            test.testLogging { events("started", "passed", "skipped", "failed"); showStandardStreams = true }
            // A hung test (e.g. a looper that never goes idle) fails the build instead of waiting for CI to kill it.
            test.timeout.set(Duration.ofMinutes(10))
        }
    }
}

// Screenshot tests (Roborazzi), as in :core:ui: goldens live under src/test/screenshots.
roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

dependencies {
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.jsoup)
    // Local files: CBR (junrar) and CB7 (commons-compress, xz for LZMA).
    implementation(libs.junrar)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    // Paged reader: subsampled decoding and sharp zoom.
    implementation(libs.telephoto.zoomable.image.coil3)

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
