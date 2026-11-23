package com.lume.app.llm

import com.lume.app.AppContainer
import com.lume.app.translate.deeplUsage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/** What the last check of a key found. */
enum class CheckStatus(val id: String) {
    /** The key works and the service answers. */
    Ok("ok"),

    /** The key works, but the picked model isn't in the service's list. */
    ModelMissing("model"),

    /** The service refused the key. */
    Refused("refused"),

    /** The key works, but the account has no credits or quota left. */
    NoCredits("credits"),

    /** No answer: no internet, a wrong address, the service down. */
    Unreachable("unreachable"),

    /** Another error; [ServiceCheck.detail] says which. */
    Failed("failed"),
    ;

    companion object {
        fun fromId(id: String?): CheckStatus? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The last check of one service's key: when, what it found, how many models the service offers
 * and which one "Automatic" picked from them. For DeepL, [used] and [limit] are this month's
 * characters.
 */
data class ServiceCheck(
    val status: CheckStatus,
    val at: Long,
    val modelCount: Int? = null,
    val autoModel: String? = null,
    val detail: String? = null,
    val used: Long? = null,
    val limit: Long? = null,
) {
    val works: Boolean get() = status == CheckStatus.Ok || status == CheckStatus.ModelMissing

    fun encode(): String = buildJsonObject {
        put("s", status.id)
        put("at", at)
        modelCount?.let { put("n", it) }
        autoModel?.let { put("auto", it) }
        detail?.let { put("d", it.take(300)) }
        used?.let { put("used", it) }
        limit?.let { put("limit", it) }
    }.toString()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String?): ServiceCheck? {
            val o = text?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return null
            fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            val status = CheckStatus.fromId(str("s")) ?: return null
            return ServiceCheck(
                status = status,
                at = str("at")?.toLongOrNull() ?: 0L,
                modelCount = str("n")?.toIntOrNull(),
                autoModel = str("auto"),
                detail = str("d"),
                used = str("used")?.toLongOrNull(),
                limit = str("limit")?.toLongOrNull(),
            )
        }
    }
}

/**
 * The AI services and their keys, as Settings → AI and translation keeps them. One service is
 * active; translation, the recap and codex summaries all use it, with its model: the one the user
 * picked for that service, or the automatic one.
 */
object AiServices {
    const val DEEPL = "deepl"

    fun activePreset(app: AppContainer): LlmPreset = LlmPreset.fromId(app.prefs.llmPreset.value)

    fun check(app: AppContainer, id: String): ServiceCheck? = ServiceCheck.decode(app.prefs.aiChecks.value[id])

    /** True when [preset] uses its automatic model. */
    fun isAuto(app: AppContainer, preset: LlmPreset): Boolean = app.prefs.llmModels.value[preset.id].isNullOrBlank()

    /**
     * The automatic model of [preset]: what its last check picked, else its first preference.
     * Checks before v1.11.0 picked OpenRouter's own router, which can choose expensive models;
     * those give way to the cheaper preferences until the next check.
     */
    fun autoModel(app: AppContainer, preset: LlmPreset): String? =
        check(app, preset.id)?.autoModel?.takeUnless { preset == LlmPreset.OpenRouter && it == LEGACY_OPENROUTER_AUTO }
            ?: preset.defaultModel

    private const val LEGACY_OPENROUTER_AUTO = "openrouter/auto"

    /** A client for [config] whose token use is added to the usage totals. */
    fun client(app: AppContainer, config: LlmConfig): LlmClient =
        LlmClient(app.http.okHttp, config) { feature, usage -> AiUsage.record(app, feature, usage) }

    /** The model [preset] uses now: the picked one, else the automatic one. */
    fun model(app: AppContainer, preset: LlmPreset): String? =
        app.prefs.llmModels.value[preset.id]?.takeIf { it.isNotBlank() } ?: autoModel(app, preset)

    /** Whether [preset] has what a request needs (a key, or the address for a custom server). */
    fun hasSetup(app: AppContainer, preset: LlmPreset): Boolean =
        LlmConfig.from(preset.id, app.prefs.llmBaseUrl.value, "-", app.prefs.llmKeys.value) != null

    /** The config of [preset] (the active one by default), or null when its key, address or model is missing. */
    fun config(app: AppContainer, preset: LlmPreset = activePreset(app)): LlmConfig? =
        LlmConfig.from(preset.id, app.prefs.llmBaseUrl.value, model(app, preset), app.prefs.llmKeys.value)

    /** Saves [key] for [preset] (blank removes it) and checks it at once. */
    suspend fun saveKey(app: AppContainer, preset: LlmPreset, key: String?): ServiceCheck? {
        app.prefs.setLlmKey(preset.id, key?.trim()?.takeIf { it.isNotEmpty() })
        app.prefs.setAiCheck(preset.id, null)
        return if (hasSetup(app, preset)) check(app, preset) else null
    }

    /**
     * Asks [preset] for its models with the saved key (and, for OpenRouter, whose list is public,
     * for the key itself) and keeps the result.
     */
    suspend fun check(app: AppContainer, preset: LlmPreset): ServiceCheck? {
        val config = LlmConfig.from(preset.id, app.prefs.llmBaseUrl.value, "-", app.prefs.llmKeys.value) ?: run {
            app.prefs.setAiCheck(preset.id, null)
            return null
        }
        val now = System.currentTimeMillis()
        val result = try {
            val client = LlmClient(app.http.okHttp, config)
            val credits = if (preset == LlmPreset.OpenRouter) client.openRouterCreditsLeft() else null
            val ids = client.models().map { it.id }
            val picked = app.prefs.llmModels.value[preset.id]?.takeIf { it.isNotBlank() }
            val missing = picked != null && ids.isNotEmpty() && ids.none { it == picked || it.removePrefix("models/") == picked }
            val status = when {
                credits != null && credits <= 0.0 -> CheckStatus.NoCredits
                missing -> CheckStatus.ModelMissing
                else -> CheckStatus.Ok
            }
            ServiceCheck(status, now, ids.size, preset.autoModel(ids), detail = picked.takeIf { missing })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failedCheck(e, now)
        }
        app.prefs.setAiCheck(preset.id, result.encode())
        return result
    }

    suspend fun saveDeepLKey(app: AppContainer, key: String?): ServiceCheck? {
        app.prefs.setDeeplKey(key?.trim()?.takeIf { it.isNotEmpty() })
        app.prefs.setAiCheck(DEEPL, null)
        return if (app.prefs.deeplKey.value.isNullOrBlank()) null else checkDeepL(app)
    }

    /** Asks DeepL for the key's usage this month and keeps the result. */
    suspend fun checkDeepL(app: AppContainer): ServiceCheck? {
        val key = app.prefs.deeplKey.value?.takeIf { it.isNotBlank() } ?: return null
        val now = System.currentTimeMillis()
        val result = try {
            val usage = deeplUsage(app.http.okHttp, key)
            val status = if (usage.limit > 0 && usage.used >= usage.limit) CheckStatus.NoCredits else CheckStatus.Ok
            ServiceCheck(status, now, used = usage.used, limit = usage.limit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failedCheck(e, now)
        }
        app.prefs.setAiCheck(DEEPL, result.encode())
        return result
    }
}

internal fun failedCheck(e: Exception, at: Long): ServiceCheck = when {
    e is ServiceHttpException && e.code in setOf(401, 403) -> ServiceCheck(CheckStatus.Refused, at)
    e is ServiceHttpException && e.code in setOf(402, 456) -> ServiceCheck(CheckStatus.NoCredits, at)
    e is ServiceHttpException -> ServiceCheck(CheckStatus.Failed, at, detail = e.message)
    e is IOException -> ServiceCheck(CheckStatus.Unreachable, at, detail = e.message)
    else -> ServiceCheck(CheckStatus.Failed, at, detail = e.message ?: e.toString())
}

/** OpenRouter's key endpoint: the dollars left on a key with a limit, null without one. Throws on a refused key. */
internal fun parseOpenRouterKey(body: String): Double? {
    val data = runCatching { Json.parseToJsonElement(body).jsonObject["data"] as? JsonObject }.getOrNull() ?: return null
    return (data["limit_remaining"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
}
