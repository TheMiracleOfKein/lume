// "Lume Translate": the optional add-on app with the offline translation engine (bergamot-translator,
// Firefox Translations models). Lume binds to its service; Lume's own APKs carry no engine. Its
// own versionCode, Lume's versionName, the same signing key (the signature permission needs it).
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.lume.translate.addon"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.lume.translate"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("lume.translateVersionCode").get().toInt()
        versionName = providers.gradleProperty("lume.versionName").get()

        ndk {
            // 64-bit phones and the emulator / x86 Chromebooks.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Same release signing as :app (see its build file); unsigned without the four values.
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // Native libraries stay compressed in the APK: a smaller download for an add-on that
        // loads its library once per process.
        jniLibs.useLegacyPackaging = true
    }

    lint {
        disable.add("EditedTargetSdkVersion")
        disable.add("AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(project(":core:translate"))
    testImplementation(libs.junit)
}
