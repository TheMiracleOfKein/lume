plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
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
        versionCode = 18
        versionName = "0.7.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // OAuth app credentials for AniList / Shikimori (identify Lume itself).
        // Put values in root local.properties (gitignored), e.g. LUME_SHIKIMORI_CLIENT_ID=...
        buildConfigField("String", "ANILIST_CLIENT_ID", oauthBuildConfig("LUME_ANILIST_CLIENT_ID"))
        buildConfigField("String", "ANILIST_CLIENT_SECRET", oauthBuildConfig("LUME_ANILIST_CLIENT_SECRET"))
        buildConfigField("String", "SHIKIMORI_CLIENT_ID", oauthBuildConfig("LUME_SHIKIMORI_CLIENT_ID"))
        buildConfigField("String", "SHIKIMORI_CLIENT_SECRET", oauthBuildConfig("LUME_SHIKIMORI_CLIENT_SECRET"))
        // MyAnimeList: register an app with redirect URL lume://oauth/myanimelist (no secret needed).
        buildConfigField("String", "MAL_CLIENT_ID", oauthBuildConfig("LUME_MAL_CLIENT_ID"))
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
        // AGP is pinned to what the installed Android Studio can sync (see .github/dependabot.yml).
        disable.add("AndroidGradlePluginVersion")
    }

    testOptions {
        // Engines touch android.webkit / Context only through runCatching fallbacks.
        unitTests.isReturnDefaultValues = true
        // Robolectric (Room migration tests) needs the merged assets and resources.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Live catalogue checks hit real sites: ./gradlew :app:testDebugUnitTest -Plume.live
            test.systemProperty("lume.live", project.hasProperty("lume.live").toString())
            project.findProperty("lume.live.sources")?.let { test.systemProperty("lume.live.sources", it.toString()) }
            test.testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
        }
    }
}

// Tests that run both on the JVM (Robolectric) and on a device live in src/sharedTest.
// The migration tests read the exported schemas as assets. JVM tests only see the tested
// variant's assets, so they also go into debug builds; release APKs don't carry them.
android.sourceSets.getByName("test").kotlin.directories.add("src/sharedTest/java")
android.sourceSets.getByName("androidTest").kotlin.directories.add("src/sharedTest/java")
android.sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
android.sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")

// Exported Room schemas (app/schemas) make future migrations reviewable and testable.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation(libs.okhttp.doh)
    // App lock: the system screen-lock prompt (fingerprint, face, PIN).
    implementation(libs.androidx.biometric)
    implementation(libs.jsoup)
    implementation(libs.jspecify)
    implementation(libs.coil.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    // Reads Mihon / Tachiyomi backups (.tachibk is gzipped protobuf).
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    // Installs the baseline profile on sideloaded installs too (Play does it itself).
    implementation(libs.androidx.profileinstaller)
    // Measured profile from :baselineprofile (./gradlew :app:generateBaselineProfile with a device attached).
    baselineProfile(project(":baselineprofile"))
    debugImplementation(libs.androidx.compose.ui.tooling)
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
