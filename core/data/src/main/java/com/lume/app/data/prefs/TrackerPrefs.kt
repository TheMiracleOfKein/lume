package com.lume.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lume.app.data.security.SecretSealer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Tracker accounts (AniList, Shikimori and the v0.5 trackers) and the tracker switch. */
abstract class TrackerPrefs(context: Context, scope: CoroutineScope, secrets: SecretSealer) :
    PrefsBase(context, scope, secrets) {
    private val trackerAutoLinkKey = booleanPreferencesKey("tracker_auto_link_enabled")
    private val anilistTokenKey = stringPreferencesKey("anilist_access_token")
    private val anilistClientIdKey = stringPreferencesKey("anilist_client_id")
    private val anilistClientSecretKey = stringPreferencesKey("anilist_client_secret")
    private val shikiTokenKey = stringPreferencesKey("shikimori_access_token")
    private val shikiRefreshKey = stringPreferencesKey("shikimori_refresh_token")
    private val shikiUserIdKey = stringPreferencesKey("shikimori_user_id")
    private val shikiClientIdKey = stringPreferencesKey("shikimori_client_id")
    private val shikiClientSecretKey = stringPreferencesKey("shikimori_client_secret")

    val anilistAccessToken: StateFlow<String?> = privateState { it.secret(anilistTokenKey) }
    val anilistClientId: StateFlow<String?> = privateState { it[anilistClientIdKey] }
    val anilistClientSecret: StateFlow<String?> = privateState { it.secret(anilistClientSecretKey) }
    val shikimoriAccessToken: StateFlow<String?> = privateState { it.secret(shikiTokenKey) }
    val shikimoriRefreshToken: StateFlow<String?> = privateState { it.secret(shikiRefreshKey) }
    val shikimoriUserId: StateFlow<String?> = privateState { it[shikiUserIdKey] }
    val shikimoriClientId: StateFlow<String?> = privateState { it[shikiClientIdKey] }
    val shikimoriClientSecret: StateFlow<String?> = privateState { it.secret(shikiClientSecretKey) }

    /**
     * Values of the trackers added in v0.5 (MyAnimeList, Kitsu, MangaUpdates), stored as
     * `tracker_<id>_<name>`. Never part of backups (see [isPrivateKey]).
     */
    val trackerValues: StateFlow<Map<String, String>> = privateState { prefs ->
        prefs.asMap().mapNotNull { (k, v) ->
            if (!k.name.startsWith(TRACKER_PREFIX) || v !is String) return@mapNotNull null
            secrets.open(v)?.let { k.name.removePrefix(TRACKER_PREFIX) to it }
        }.toMap()
    }

    fun trackerValue(trackerId: String, name: String): String? = trackerValues.value["${trackerId}_$name"]

    suspend fun setTrackerValue(trackerId: String, name: String, value: String?) {
        putSecret(stringPreferencesKey("$TRACKER_PREFIX${trackerId}_$name"), value)
    }

    /** Link titles to trackers by an exact title match when they are added to the library. */
    val trackerAutoLink: StateFlow<Boolean> = state { it[trackerAutoLinkKey] ?: true }

    suspend fun setTrackerAutoLink(value: Boolean) = put(trackerAutoLinkKey, value)

    suspend fun setAniListAccessToken(token: String?) {
        putSecret(anilistTokenKey, token)
    }

    @Suppress("unused")
    suspend fun setAniListClient(clientId: String, clientSecret: String) {
        context.privateDataStore.edit {
            it[anilistClientIdKey] = clientId
            it[anilistClientSecretKey] = secrets.seal(clientSecret)
        }
    }

    suspend fun setShikimoriTokens(access: String?, refresh: String?) {
        context.privateDataStore.edit {
            if (access.isNullOrBlank()) {
                it.remove(shikiTokenKey)
                it.remove(shikiUserIdKey)
            } else {
                it[shikiTokenKey] = secrets.seal(access)
            }
            if (refresh.isNullOrBlank()) it.remove(shikiRefreshKey) else it[shikiRefreshKey] = secrets.seal(refresh)
        }
    }

    suspend fun setShikimoriUserId(userId: String?) {
        context.privateDataStore.edit {
            if (userId.isNullOrBlank()) it.remove(shikiUserIdKey) else it[shikiUserIdKey] = userId
        }
    }

    @Suppress("unused")
    suspend fun setShikimoriClient(clientId: String, clientSecret: String) {
        context.privateDataStore.edit {
            it[shikiClientIdKey] = clientId
            it[shikiClientSecretKey] = secrets.seal(clientSecret)
        }
    }
}
