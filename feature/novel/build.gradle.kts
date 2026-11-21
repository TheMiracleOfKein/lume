import java.time.Duration

// Novel (ranobe) mode: the text model, local EPUB and TXT books, and the text reader. It adds the
// reader to the app's navigation graph through NovelScreens. Packages stay com.lume.app.*; the
// namespace com.lume.app.novel only names generated classes.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.lume.app.novel"
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
            test.testLogging { events("started", "passed", "skipped", "failed"); showStandardStreams = true }
            // A hung test (e.g. a looper that never goes idle) fails the build instead of waiting for CI to kill it.
            test.timeout.set(Duration.ofMinutes(10))
            // Live check of RanobeLib against the real site: ./gradlew :feature:novel:testDebugUnitTest -Plume.live
            test.systemProperty("lume.live", project.hasProperty("lume.live").toString())
        }
    }
}

// Parser tests on saved pages run both on the JVM and on a device, where the regex engine is
// Android's (ICU), not the JVM's: they live in src/sharedTest. The fixtures and the bundled source
// manifests are read as test resources, so both runs find them the same way.
sourceSet("test").kotlin.directories.add("src/sharedTest/java")
sourceSet("androidTest").kotlin.directories.add("src/sharedTest/java")
sourceSet("test").resources.directories.add("src/main/assets")
sourceSet("androidTest").resources.directories.add("src/main/assets")
sourceSet("androidTest").resources.directories.add("src/test/resources")

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
    // Read aloud: a media session (notification, lock screen, headset buttons) around Android's TTS.
    implementation(libs.media3.session)
    implementation(libs.media3.common)

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

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

private fun sourceSet(name: String): com.android.build.api.dsl.AndroidSourceSet =
    extensions.getByType<com.android.build.api.dsl.LibraryExtension>().sourceSets.getByName(name)

// Plain JVM unit tests have no Android assets, where OkHttp's Android build looks for its
// public-suffix list (every request through AppHttp failed in the live checks). Tests use the
// JVM build of the same version, which carries the list as a resource.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.squareup.okhttp3:okhttp-android"))
            .using(module("com.squareup.okhttp3:okhttp-jvm:${libs.versions.okhttp.get()}"))
    }
}
