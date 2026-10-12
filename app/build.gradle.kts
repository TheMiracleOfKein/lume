plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Must stay outside android { defaultConfig } — nested mapNotNull/return@ confuses the IDE
// (suspicious receiver: ApplicationDefaultConfig vs Iterable).
private val oauthProps: Map<String, String> = run {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return@run emptyMap()
    file.readLines().mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
        val eq = trimmed.indexOf('=')
        if (eq <= 0) return@mapNotNull null
        trimmed.substring(0, eq).trim() to trimmed.substring(eq + 1).trim()
    }.toMap()
}

private fun oauthBuildConfig(name: String): String {
    val raw = oauthProps[name].orEmpty()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$raw\""
}

android {
    namespace = "com.lume.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lume.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // OAuth app credentials for AniList / Shikimori (identify Lume itself).
        // Put values in root local.properties (gitignored), e.g. LUME_SHIKIMORI_CLIENT_ID=...
        buildConfigField("String", "ANILIST_CLIENT_ID", oauthBuildConfig("LUME_ANILIST_CLIENT_ID"))
        buildConfigField("String", "ANILIST_CLIENT_SECRET", oauthBuildConfig("LUME_ANILIST_CLIENT_SECRET"))
        buildConfigField("String", "SHIKIMORI_CLIENT_ID", oauthBuildConfig("LUME_SHIKIMORI_CLIENT_ID"))
        buildConfigField("String", "SHIKIMORI_CLIENT_SECRET", oauthBuildConfig("LUME_SHIKIMORI_CLIENT_SECRET"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // IDE-only advisory after bumping targetSdk 35→37 (does not affect CLI builds).
    lint {
        disable.add("EditedTargetSdkVersion")
    }

    testOptions {
        // Engines touch android.webkit / Context only through runCatching fallbacks.
        unitTests.isReturnDefaultValues = true
        unitTests.all { test ->
            // Live catalogue checks hit real sites: ./gradlew :app:testDebugUnitTest -Plume.live
            test.systemProperty("lume.live", project.hasProperty("lume.live").toString())
            project.findProperty("lume.live.sources")?.let { test.systemProperty("lume.live.sources", it.toString()) }
            test.testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
        }
    }
}

dependencies {
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
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)
    implementation(libs.jspecify)
    implementation(libs.coil.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
