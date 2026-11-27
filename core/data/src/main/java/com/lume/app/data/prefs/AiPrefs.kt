package com.lume.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lume.app.data.security.SecretSealer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** AI story notes are off: nothing is sent. */
const val STORY_NOTES_OFF = "off"

/** AI story notes are written as each chapter is finished. */
const val STORY_NOTES_FINISHED = "finished"

/** AI story notes are written only when the recap or the codex needs them, several chapters per request. */
const val STORY_NOTES_NEEDED = "needed"

private val STORY_NOTES_MODES = setOf(STORY_NOTES_OFF, STORY_NOTES_FINISHED, STORY_NOTES_NEEDED)

// "secret" in the name keeps these keys private and encrypted.
private const val LLM_KEY_PREFIX = "translate_llm_"
private const val LLM_KEY_SUFFIX = "_secret"
private const val LLM_MODEL_PREFIX = "ai_model_"
private const val AI_CHECK_PREFIX = "ai_check_"

/** The service used before the user picks one (`LlmPreset.DEFAULT` in the novel module). */
private const val LLM_DEFAULT_PRESET = "openrouter"

/** Translation engine, AI services (models, keys, checks, usage), DeepL and the AI story notes. */
abstract class AiPrefs(context: Context, scope: CoroutineScope, secrets: SecretSealer) :
    TrackerPrefs(context, scope, secrets) {
    private val storyIndexKey = booleanPreferencesKey("story_index")
    private val recapAfterHoursKey = intPreferencesKey("recap_after_hours")
    private val aiRecapKey = booleanPreferencesKey("story_ai_recap")
    private val storyNotesKey = stringPreferencesKey("story_notes_mode")
    private val aiUsageKey = stringPreferencesKey("ai_usage")
    private val translationEngineKey = stringPreferencesKey("translation_engine")
    private val llmPresetKey = stringPreferencesKey("translate_llm_preset")
    private val llmBaseUrlKey = stringPreferencesKey("translate_llm_base_url")
    private val llmModelKey = stringPreferencesKey("translate_llm_model")

    // "secret" in the name: private store, encrypted, never in backups or sync.
    private val deeplKeySecret = stringPreferencesKey("translate_deepl_secret")

    /** Chapters read or downloaded are indexed for name lookup. */
    val storyIndex: StateFlow<Boolean> = state { it[storyIndexKey] ?: true }

    /** "Previously..." recap after this many hours away from a title (0 = off). */
    val recapAfterHours: StateFlow<Int> = state { it[recapAfterHoursKey] ?: 24 }

    suspend fun setStoryIndex(value: Boolean) = put(storyIndexKey, value)

    suspend fun setRecapAfterHours(value: Int) = put(recapAfterHoursKey, value)

    /**
     * AI story notes: read chapters get a digest, the names in them and a fact about each from the
     * active AI service, for "Previously…" and the codex. [STORY_NOTES_OFF] by default, as it sends
     * chapter text there; [STORY_NOTES_FINISHED] writes them as each chapter is finished,
     * [STORY_NOTES_NEEDED] only when the recap or the codex needs them, several chapters per
     * request. Up to v1.11.0 a switch, "AI recap"; on counts as [STORY_NOTES_FINISHED].
     */
    val storyNotes: StateFlow<String> = state { storyNotesOf(it) }

    /** Whether AI story notes are on in either mode (the AI recap with them). */
    val aiRecap: StateFlow<Boolean> = state { storyNotesOf(it) != STORY_NOTES_OFF }

    private fun storyNotesOf(prefs: Preferences): String =
        prefs[storyNotesKey]?.takeIf { it in STORY_NOTES_MODES }
            ?: if (prefs[aiRecapKey] == true) STORY_NOTES_FINISHED else STORY_NOTES_OFF

    suspend fun setStoryNotes(mode: String) {
        if (mode !in STORY_NOTES_MODES) return
        put(storyNotesKey, mode)
    }

    /**
     * How the text reader translates: `offline` (the Lume Translate add-on), `google` or `deepl`
     * (online, chapter text goes to that service). Null until the user picks one.
     */
    val translationEngine: StateFlow<String?> = state { it[translationEngineKey] }

    suspend fun setTranslationEngine(id: String) = put(translationEngineKey, id)

    /** The user's own DeepL API key (free or paid), or null. */
    val deeplKey: StateFlow<String?> = privateState { it.secret(deeplKeySecret) }

    suspend fun setDeeplKey(key: String?) = putSecret(deeplKeySecret, key?.trim())

    /** The AI service (`openrouter`, `openai`, ...) translation, the recap and codex summaries use; null = the default. */
    val llmPreset: StateFlow<String?> = state { it[llmPresetKey] }

    /** AI translation: the base URL for the `custom` service (for example a local Ollama). */
    val llmBaseUrl: StateFlow<String?> = state { it[llmBaseUrlKey]?.takeIf(String::isNotBlank) }

    /**
     * AI: the model picked for each service id; a service without an entry uses its automatic
     * model. Up to v1.9.0 one model was kept for the active service only; it still counts for it.
     */
    val llmModels: StateFlow<Map<String, String>> = state { prefs ->
        val perService = prefs.asMap().mapNotNull { (k, v) ->
            if (!k.name.startsWith(LLM_MODEL_PREFIX) || v !is String || v.isBlank()) return@mapNotNull null
            k.name.removePrefix(LLM_MODEL_PREFIX) to v
        }.toMap()
        val legacy = prefs[llmModelKey]?.takeIf(String::isNotBlank)
        val active = prefs[llmPresetKey] ?: LLM_DEFAULT_PRESET
        if (legacy != null && active !in perService) perService + (active to legacy) else perService
    }

    /** Makes [preset] the AI service that translation, the recap and codex summaries use. */
    suspend fun setLlmPreset(preset: String) {
        context.appDataStore.edit { prefs ->
            // The old single model belongs to the service that was active; keep it there.
            prefs[llmModelKey]?.takeIf(String::isNotBlank)?.let { legacy ->
                val owner = prefs[llmPresetKey] ?: LLM_DEFAULT_PRESET
                val key = stringPreferencesKey(LLM_MODEL_PREFIX + owner)
                if (prefs[key].isNullOrBlank()) prefs[key] = legacy
                prefs.remove(llmModelKey)
            }
            prefs[llmPresetKey] = preset
        }
    }

    suspend fun setLlmBaseUrl(url: String?) {
        context.appDataStore.edit { if (url.isNullOrBlank()) it.remove(llmBaseUrlKey) else it[llmBaseUrlKey] = url.trim() }
    }

    /** The model for [preset]; null goes back to the service's automatic model. */
    suspend fun setLlmModel(preset: String, model: String?) {
        context.appDataStore.edit { prefs ->
            val key = stringPreferencesKey(LLM_MODEL_PREFIX + preset)
            if (model.isNullOrBlank()) prefs.remove(key) else prefs[key] = model.trim()
            if ((prefs[llmPresetKey] ?: LLM_DEFAULT_PRESET) == preset) prefs.remove(llmModelKey)
        }
    }

    /**
     * The last check of each AI service and of DeepL (`deepl`), as the feature encodes it. Kept
     * in the private store: it is about this phone's keys and isn't backed up or synced.
     */
    val aiChecks: StateFlow<Map<String, String>> = privateState { prefs ->
        prefs.asMap().mapNotNull { (k, v) ->
            if (!k.name.startsWith(AI_CHECK_PREFIX) || v !is String) return@mapNotNull null
            k.name.removePrefix(AI_CHECK_PREFIX) to v
        }.toMap()
    }

    suspend fun setAiCheck(id: String, value: String?) {
        context.privateDataStore.edit {
            val key = stringPreferencesKey(AI_CHECK_PREFIX + id)
            if (value == null) it.remove(key) else it[key] = value
        }
    }

    /**
     * AI usage totals (tokens and cost per month and feature) as the feature encodes them. Kept in
     * the private store: it is about this phone's requests and isn't backed up or synced.
     */
    val aiUsage: StateFlow<String?> = privateState { it[aiUsageKey] }

    /** Replaces the usage totals with [transform] of the current ones, atomically. */
    suspend fun updateAiUsage(transform: (String?) -> String?) {
        context.privateDataStore.edit { prefs ->
            val next = transform(prefs[aiUsageKey])
            if (next == null) prefs.remove(aiUsageKey) else prefs[aiUsageKey] = next
        }
    }

    /** The user's API keys for AI translation, one per service id, decrypted. */
    val llmKeys: StateFlow<Map<String, String>> = privateState { prefs ->
        prefs.asMap().keys.mapNotNull { key ->
            val preset = key.name.removePrefix(LLM_KEY_PREFIX).removeSuffix(LLM_KEY_SUFFIX)
            if (!key.name.startsWith(LLM_KEY_PREFIX) || !key.name.endsWith(LLM_KEY_SUFFIX)) return@mapNotNull null
            prefs.secret(stringPreferencesKey(key.name))?.takeIf(String::isNotBlank)?.let { preset to it }
        }.toMap()
    }

    suspend fun setLlmKey(preset: String, key: String?) =
        putSecret(stringPreferencesKey(LLM_KEY_PREFIX + preset + LLM_KEY_SUFFIX), key?.trim())
}
