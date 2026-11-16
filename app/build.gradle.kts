plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
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

    // Which reading modes a build ships. Same applicationId and signing key in every flavor, so
    // switching builds keeps the user's data. Each flavor lists its modules in InstalledModules.kt.
    flavorDimensions += "modes"
    productFlavors {
        create("full") {
            dimension = "modes"
        }
        create("manga") {
            dimension = "modes"
        }
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

}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":feature:comics"))
    "fullImplementation"(project(":feature:novel"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // App lock: the system screen-lock prompt (fingerprint, face, PIN).
    implementation(libs.androidx.biometric)
    // The manifest declares zxing's CaptureActivity (QR scanner), so lint needs it on this classpath.
    implementation(libs.zxing.embedded)
    // Installs the baseline profile on sideloaded installs too (Play does it itself).
    implementation(libs.androidx.profileinstaller)
    // Measured profile from :baselineprofile (./gradlew :app:generateBaselineProfile with a device attached).
    baselineProfile(project(":baselineprofile"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
