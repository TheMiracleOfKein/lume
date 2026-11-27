import java.time.Duration

// Data layer: Room database, repositories, sources and engines, downloads, trackers, sync,
// backups, workers, and AppContainer (the manual DI root). Packages stay com.lume.app.*; the
// namespace com.lume.app.data only names this module's own generated classes. com.lume.app.R
// and com.lume.app.BuildConfig come from :core:common.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.lume.app.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable.add("EditedTargetSdkVersion")
        // AGP is pinned to what the installed Android Studio can sync (see .github/dependabot.yml).
        disable.add("AndroidGradlePluginVersion")
    }

    testOptions {
        // Engines touch android.webkit / Context only through runCatching fallbacks.
        unitTests.isReturnDefaultValues = true
        // Robolectric (Room migration tests) needs the merged assets and resources.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.testLogging { events("started", "passed", "skipped", "failed"); showStandardStreams = true }
            // A hung test (e.g. a looper that never goes idle) fails the build instead of waiting for CI to kill it.
            test.timeout.set(Duration.ofMinutes(10))
        }
    }
}

// Tests that run both on the JVM (Robolectric) and on a device live in src/sharedTest.
// The migration tests read the exported schemas as assets. JVM tests only see the tested
// variant's assets, so they also go into debug builds; release APKs do not carry them.
sourceSet("test").kotlin.directories.add("src/sharedTest/java")
sourceSet("androidTest").kotlin.directories.add("src/sharedTest/java")
sourceSet("androidTest").assets.directories.add("$projectDir/schemas")
sourceSet("debug").assets.directories.add("$projectDir/schemas")

// Exported Room schemas (core/data/schemas) make future migrations reviewable and testable.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))

    // Types that appear in public signatures used by :app.
    api(libs.okhttp)
    api(libs.room.runtime)
    api(libs.room.ktx)
    api(libs.datastore.preferences)
    api(libs.androidx.work.runtime.ktx)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.coil.compose)
    api(libs.coil.network.okhttp)

    ksp(libs.room.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.okhttp.doh)
    implementation(libs.jsoup)
    implementation(libs.zxing.core)
    // Reads Mihon / Tachiyomi backups (.tachibk is gzipped protobuf).
    implementation(libs.kotlinx.serialization.protobuf)
    // NearbyQr returns a Compose ImageBitmap.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

private fun sourceSet(name: String): com.android.build.api.dsl.AndroidSourceSet =
    extensions.getByType<com.android.build.api.dsl.LibraryExtension>().sourceSets.getByName(name)
