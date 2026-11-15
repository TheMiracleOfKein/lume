plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.lume.app.shell"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lume.app"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("lume.versionCode").get().toInt()
        versionName = providers.gradleProperty("lume.versionName").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing from the user's ~/.gradle/gradle.properties (never in the repository).
    // Without all four values the release build stays unsigned, as on CI.
    val signingProps = listOf("LUME_STORE_FILE", "LUME_STORE_PASSWORD", "LUME_KEY_ALIAS", "LUME_KEY_PASSWORD")
        .associateWith { providers.gradleProperty(it).orNull?.takeIf(String::isNotBlank) }
    val releaseSigning = if (signingProps.values.all { it != null }) {
        signingConfigs.create("release") {
            storeFile = file(signingProps.getValue("LUME_STORE_FILE")!!)
            storePassword = signingProps.getValue("LUME_STORE_PASSWORD")
            keyAlias = signingProps.getValue("LUME_KEY_ALIAS")
            keyPassword = signingProps.getValue("LUME_KEY_PASSWORD")
        }
    } else {
        null
    }

    buildTypes {
        release {
            releaseSigning?.let { signingConfig = it }
            // R8: smaller, faster release builds. Kept in its own commit so it is easy to revert.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // IDE-only advisory after bumping targetSdk 35→37 (does not affect CLI builds).
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
            test.testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
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
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    // App lock: the system screen-lock prompt (fingerprint, face, PIN).
    implementation(libs.androidx.biometric)
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Paged reader: subsampled decoding and sharp zoom.
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.kotlinx.serialization.json)
    // Installs the baseline profile on sideloaded installs too (Play does it itself).
    implementation(libs.androidx.profileinstaller)
    // Measured profile from :baselineprofile (./gradlew :app:generateBaselineProfile with a device attached).
    baselineProfile(project(":baselineprofile"))
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
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
