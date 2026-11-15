package com.lume.app.data.tracker

import com.lume.app.BuildConfig

/**
 * OAuth **application** credentials (identify Lume as a client).
 * Each end user still logs into *their* Shikimori/AniList account; they never enter these IDs.
 *
 * Set once in `local.properties` (gitignored) → baked into the APK via BuildConfig.
 */
object TrackerOAuthDefaults {
    val ANILIST_CLIENT_ID: String = BuildConfig.ANILIST_CLIENT_ID
    val ANILIST_CLIENT_SECRET: String = BuildConfig.ANILIST_CLIENT_SECRET
    val SHIKIMORI_CLIENT_ID: String = BuildConfig.SHIKIMORI_CLIENT_ID
    val SHIKIMORI_CLIENT_SECRET: String = BuildConfig.SHIKIMORI_CLIENT_SECRET
    val MAL_CLIENT_ID: String = BuildConfig.MAL_CLIENT_ID
}
