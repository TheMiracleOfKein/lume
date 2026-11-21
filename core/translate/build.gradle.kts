// The contract between Lume and the optional "Lume Translate" add-on app: the AIDL interface, the
// signature permission that guards it, and the pinned list of translation models. Both apps
// depend on it; it has no engine and no network code, so Lume stays small.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.lume.translate.api"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
    }

    lint {
        disable.add("EditedTargetSdkVersion")
        disable.add("AndroidGradlePluginVersion")
    }
}

dependencies {
    testImplementation(libs.junit)
}
