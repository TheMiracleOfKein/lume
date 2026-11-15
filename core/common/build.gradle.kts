// Shared Android foundation: all resources (strings, icons, themes, widget layout), util/ and
// BuildConfig (version and OAuth ids). The namespace stays com.lume.app so com.lume.app.R and
// com.lume.app.BuildConfig resolve unchanged for every module.
plugins {
    alias(libs.plugins.android.library)
}

// Must stay outside android { defaultConfig } — nested mapNotNull/return@ confuses the IDE.
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
        minSdk = 26

        // Single source of the version: root gradle.properties.
        val versionCode = providers.gradleProperty("lume.versionCode").get().toInt()
        val versionName = providers.gradleProperty("lume.versionName").get()
        buildConfigField("int", "VERSION_CODE", versionCode.toString())
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")

        // OAuth app credentials for AniList / Shikimori (identify Lume itself).
        // Put values in root local.properties (gitignored), e.g. LUME_SHIKIMORI_CLIENT_ID=...
        buildConfigField("String", "ANILIST_CLIENT_ID", oauthBuildConfig("LUME_ANILIST_CLIENT_ID"))
        buildConfigField("String", "ANILIST_CLIENT_SECRET", oauthBuildConfig("LUME_ANILIST_CLIENT_SECRET"))
        buildConfigField("String", "SHIKIMORI_CLIENT_ID", oauthBuildConfig("LUME_SHIKIMORI_CLIENT_ID"))
        buildConfigField("String", "SHIKIMORI_CLIENT_SECRET", oauthBuildConfig("LUME_SHIKIMORI_CLIENT_SECRET"))
        // MyAnimeList: register an app with redirect URL lume://oauth/myanimelist (no secret needed).
        buildConfigField("String", "MAL_CLIENT_ID", oauthBuildConfig("LUME_MAL_CLIENT_ID"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        disable.add("EditedTargetSdkVersion")
        // AGP is pinned to what the installed Android Studio can sync (see .github/dependabot.yml).
        disable.add("AndroidGradlePluginVersion")
    }
}

dependencies {
    testImplementation(libs.junit)
}
