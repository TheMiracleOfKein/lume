// Novel (ranobe) mode. For now only NovelModule with no engines: it lets the full build switch
// to the novel mode and show its empty state. The text reader and novel sources come after 1.0.
// Packages stay com.lume.app.*; the namespace com.lume.app.novel only names generated classes.
plugins {
    alias(libs.plugins.android.library)
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

    lint {
        disable.add("EditedTargetSdkVersion")
        // AGP is pinned to what the installed Android Studio can sync (see .github/dependabot.yml).
        disable.add("AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(project(":core:data"))

    testImplementation(libs.junit)
}
