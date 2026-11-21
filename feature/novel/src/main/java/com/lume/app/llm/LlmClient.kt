package com.lume.app.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A service that speaks the OpenAI chat-completions API with the user's own key. [baseUrl] is null
 * for [Custom], where the user types it (a local Ollama or LM Studio, another provider).
 */
enum class LlmPreset(val id: String, val label: String, val baseUrl: String?, val defaultModel: String?) {
    OpenRouter("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openrouter/auto"),
    OpenAI("openai", "OpenAI", "https://api.openai.com/v1", null),
    DeepSeek("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    Gemini("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", null),
    Custom("custom", "", null, null),
    ;

    /** A local server usually has no key. */
    val needsKey: Boolean get() = this != Custom

    companion object {
        val DEFAULT = OpenRouter

        fun fromId(id: String?): LlmPreset = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Everything a request needs; [key] may be blank for a local server. */
data class LlmConfig(val preset: LlmPreset, val baseUrl: String, val model: String, val key: String?) {
    /** Part of cache keys: answers of different services and models aren't mixed. */
    val cacheId: String get() = "${preset.id}:$model"

    companion object {
        /** The config from the saved settings, or null when something needed is missing. */
        fun from(presetId: String?, customUrl: String?, model: String?, keys: Map<String, String>): LlmConfig? {
            val preset = LlmPreset.fromId(presetId)
            val url = (preset.baseUrl ?: customUrl)?.trim()?.trimEnd('/')?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: return null
            val m = model?.trim()?.takeIf { it.isNotEmpty() } ?: preset.defaultModel ?: return null
            val key = keys[preset.id]?.takeIf { it.isNotBlank() }
            if (preset.needsKey && key == null) return null
            return LlmConfig(preset, url, m, key)
        }
    }
}

/** A model the service offers; [promptPrice] and [completionPrice] are dollars per million tokens, when known. */
data class LlmModel(val id: String, val name: String, val promptPrice: Double?, val completionPrice: Double?)

/** One chat answer: the text, and whether it was cut off by the length limit. */
data class LlmAnswer(val content: String, val truncated: Boolean)

/** Calls an OpenAI-compatible service. One instance per config; safe to share between coroutines. */
class LlmClient(http: OkHttpClient, val config: LlmConfig) {
    // Models can take a while to write a long chapter part.
    private val http = http.newBuilder().readTimeout(3, TimeUnit.MINUTES).callTimeout(5, TimeUnit.MINUTES).build()

    /** Sends [system] and [user] and returns the answer, asking for a JSON object when [json] is set. */
    suspend fun chat(system: String, user: String, json: Boolean = true, temperature: Double = 0.3): LlmAnswer =
        withContext(Dispatchers.IO) {
            try {
                post(chatBody(config.model, system, user, json, temperature))
            } catch (e: ResponseFormatRefused) {
                // Some models and local servers don't know response_format: ask again without it.
                post(chatBody(config.model, system, user, json = false, temperature = temperature))
            }
        }

    /** The service's models, for the picker. */
    suspend fun models(): List<LlmModel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${config.baseUrl}/models").get().auth().build()
        http.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw llmError(response.code, body)
            parseModels(body)
        }
    }

    private fun post(body: String): LlmAnswer {
        val request = Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .post(body.toRequestBody("application/json".toMediaType()))
            .auth()
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (response.code == 400 && text.contains("response_format", ignoreCase = true)) throw ResponseFormatRefused()
            if (!response.isSuccessful) throw llmError(response.code, text)
            return parseChat(text)
        }
    }

    private fun Request.Builder.auth(): Request.Builder = apply {
        config.key?.let { header("Authorization", "Bearer $it") }
        if (config.preset == LlmPreset.OpenRouter) {
            // OpenRouter's app attribution headers; they name the app, nothing about the user.
            header("HTTP-Referer", "https://github.com/TheMiracleOfKein/lume")
            header("X-Title", "Lume")
        }
    }

    private class ResponseFormatRefused : IOException()
}

private val json = Json { ignoreUnknownKeys = true }

internal fun chatBody(model: String, system: String, user: String, json: Boolean, temperature: Double): String = buildJsonObject {
    put("model", model)
    putJsonArray("messages") {
        addJsonObject { put("role", "system"); put("content", system) }
        addJsonObject { put("role", "user"); put("content", user) }
    }
    put("temperature", temperature)
    if (json) putJsonObject("response_format") { put("type", "json_object") }
}.toString()

/** The first choice's text; an answer without one is an error. */
internal fun parseChat(body: String): LlmAnswer {
    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        ?: throw IOException("The AI service sent an unexpected answer")
    (root["error"] as? JsonObject)?.let { throw IOException("AI service: ${errorMessage(it) ?: "error"}") }
    val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        ?: throw IOException("The AI service sent no answer")
    val content = ((choice["message"] as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull.orEmpty()
    val finish = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
    if (content.isBlank() && finish != "length") throw IOException("The AI service sent an empty answer")
    return LlmAnswer(content, truncated = finish == "length")
}

internal fun parseModels(body: String): List<LlmModel> {
    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        ?: throw IOException("The AI service sent an unexpected model list")
    val data = root["data"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return emptyList()
    return data.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val pricing = o["pricing"] as? JsonObject
        // OpenRouter gives dollars per token as strings.
        fun price(key: String) = (pricing?.get(key) as? JsonPrimitive)?.let { it.contentOrNull?.toDoubleOrNull() ?: it.doubleOrNull }
            ?.takeIf { it >= 0 }?.let { it * 1_000_000 }
        LlmModel(id, (o["name"] as? JsonPrimitive)?.contentOrNull ?: id, price("prompt"), price("completion"))
    }.sortedBy { it.id }
}

private fun errorMessage(error: JsonObject): String? = (error["message"] as? JsonPrimitive)?.contentOrNull?.take(200)

internal fun llmError(code: Int, body: String): IOException {
    val detail = runCatching {
        (json.parseToJsonElement(body).jsonObject["error"] as? JsonObject)?.let(::errorMessage)
    }.getOrNull()
    val text = when (code) {
        401, 403 -> "The AI service refused the key; check it in the translation sheet"
        402 -> "The AI service account has no credits left"
        404 -> "The AI service doesn't know this model; pick another one"
        408, 504 -> "The AI service took too long; try again"
        429 -> "The AI service is limiting requests right now; try again later"
        else -> "AI service: HTTP $code"
    }
    return IOException(if (detail != null && code !in setOf(401, 403)) "$text ($detail)" else text)
}

/**
 * The JSON object in a model's [text]: as is, inside a ``` fence, or the outermost `{…}` in other
 * text. Throws when there is none.
 */
internal fun extractJsonObject(text: String): JsonObject {
    val trimmed = text.trim()
    runCatching { return json.parseToJsonElement(trimmed).jsonObject }
    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(trimmed)?.groupValues?.get(1)
    if (fenced != null) runCatching { return json.parseToJsonElement(fenced.trim()).jsonObject }
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    if (start >= 0 && end > start) runCatching { return json.parseToJsonElement(trimmed.substring(start, end + 1)).jsonObject }
    throw IOException("The AI answer wasn't the expected JSON")
}
