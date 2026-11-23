package com.lume.app.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * A service that speaks the OpenAI chat-completions API with the user's own key. [baseUrl] is null
 * for [Custom], where the user types it (a local Ollama or LM Studio, another provider).
 */
enum class LlmPreset(
    val id: String,
    val label: String,
    val baseUrl: String?,
    /** The models "Automatic" prefers, best first; the first is used until a check has seen the service's list. */
    val autoModels: List<String>,
    /** Where the user gets a key. */
    val keyUrl: String?,
) {
    // Cheap, fast models with JSON answers and a long context; OpenRouter's own router (which may
    // pick an expensive model) only when none of them is offered.
    OpenRouter(
        "openrouter", "OpenRouter", "https://openrouter.ai/api/v1",
        listOf("deepseek/deepseek-v4-flash", "google/gemini-3.1-flash-lite", "deepseek/deepseek-v3.2", "google/gemini-2.5-flash", "openrouter/auto"),
        "https://openrouter.ai/keys",
    ),
    OpenAI("openai", "OpenAI", "https://api.openai.com/v1", listOf("gpt-5-mini", "gpt-4.1-mini", "gpt-4o-mini"), "https://platform.openai.com/api-keys"),
    DeepSeek("deepseek", "DeepSeek", "https://api.deepseek.com/v1", listOf("deepseek-chat"), "https://platform.deepseek.com/api_keys"),
    Gemini(
        "gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
        listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash"), "https://aistudio.google.com/apikey",
    ),
    Custom("custom", "", null, emptyList(), null),
    ;

    /** A local server usually has no key. */
    val needsKey: Boolean get() = this != Custom

    /** The automatic model before any check; null for [Custom], where the server's list decides. */
    val defaultModel: String? get() = autoModels.firstOrNull()

    /**
     * The model "Automatic" uses given the service's [available] model ids (null: not checked yet):
     * the first preferred one the service has, else a close match, else the first preferred one.
     */
    fun autoModel(available: List<String>?): String? {
        if (available.isNullOrEmpty()) return defaultModel
        val ids = available.map { it.removePrefix("models/") }
        autoModels.firstOrNull { it in ids }?.let { return it }
        val fallback = when (this) {
            OpenAI -> ids.filter { it.startsWith("gpt-") && it.contains("mini") && NOT_CHAT.none(it::contains) }.maxOrNull()
            Gemini -> ids.filter { Regex("""gemini-[\d.]+-flash""").matches(it) }.maxOrNull()
            Custom -> ids.firstOrNull { NOT_CHAT.none(it::contains) } ?: ids.first()
            else -> null
        }
        return fallback ?: defaultModel
    }

    companion object {
        val DEFAULT = OpenRouter

        fun fromId(id: String?): LlmPreset = entries.firstOrNull { it.id == id } ?: DEFAULT

        /** Parts of model ids that aren't text chat models. */
        private val NOT_CHAT = listOf("audio", "realtime", "transcribe", "tts", "search", "image", "embed", "moderation", "whisper", "dall-e")
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

/** What a request is for; usage is totalled per feature. */
enum class AiFeature(val id: String) {
    Translation("translation"),
    Notes("notes"),
    Recap("recap"),
    Summary("summary"),
    Other("other"),
}

/** Tokens a request used, and its cost in dollars when the service reports it (OpenRouter). */
data class LlmUsage(val promptTokens: Long, val completionTokens: Long, val cost: Double?)

/** One chat answer: the text, whether it was cut off by the length limit, and what it used. */
data class LlmAnswer(val content: String, val truncated: Boolean, val usage: LlmUsage? = null)

/**
 * Calls an OpenAI-compatible service. One instance per config; safe to share between coroutines.
 * [onUsage] gets each answer's token use, for the usage totals.
 */
class LlmClient(
    http: OkHttpClient,
    val config: LlmConfig,
    private val onUsage: (suspend (AiFeature, LlmUsage) -> Unit)? = null,
) {
    // Models can take a while to write a long chapter part.
    private val http = http.newBuilder().readTimeout(3, TimeUnit.MINUTES).callTimeout(5, TimeUnit.MINUTES).build()

    /**
     * Sends [system] and [user] and returns the answer, asking for a JSON object when [json] is set.
     * [maxTokens] caps the answer. A busy service (429, 502–504) is asked again up to [retries]
     * times after its `Retry-After`. An optional parameter the service or model refuses (the
     * answer format, a token cap, temperature, reasoning effort) is left out from then on.
     */
    suspend fun chat(
        system: String,
        user: String,
        json: Boolean = true,
        temperature: Double = 0.3,
        maxTokens: Int? = null,
        feature: AiFeature = AiFeature.Other,
        retries: Int = 1,
    ): LlmAnswer = withContext(Dispatchers.IO) {
        val options = ChatOptions(json, temperature, maxTokens)
        val refusedKey = "${config.baseUrl}|${config.model}"
        var attempt = 0
        var answer: LlmAnswer? = null
        while (answer == null) {
            val dropped = refused[refusedKey].orEmpty()
            val body = chatBody(config.preset, config.model, system, user, options, dropped)
            try {
                answer = post(body)
            } catch (e: ParamsRefused) {
                // Each round drops at least one parameter that was sent, so this ends.
                refused[refusedKey] = dropped + e.params
            } catch (e: ServiceHttpException) {
                if (e.code !in RETRY_CODES || attempt >= retries) throw e
                attempt++
                delay((e.retryAfterMs ?: DEFAULT_RETRY_MS).coerceIn(0, MAX_RETRY_MS))
            }
        }
        answer.usage?.let { u -> onUsage?.let { runCatching { it(feature, u) } } }
        answer
    }

    /** The service's models, for the picker and the key check; a short wait, as nothing is generated. */
    suspend fun models(): List<LlmModel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${config.baseUrl}/models").get().auth().build()
        val quick = http.newBuilder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
        quick.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw llmError(response.code, body)
            parseModels(body)
        }
    }

    /** OpenRouter only: checks the key (its model list is public) and returns the dollars left, null without a limit. */
    suspend fun openRouterCreditsLeft(): Double? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${config.baseUrl}/key").get().auth().build()
        val quick = http.newBuilder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
        quick.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw llmError(response.code, body)
            parseOpenRouterKey(body)
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
            if (response.code == 400 || response.code == 422) {
                refusedParams(body, text).takeIf { it.isNotEmpty() }?.let { throw ParamsRefused(it) }
            }
            if (!response.isSuccessful) {
                throw llmError(response.code, text, response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000))
            }
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

    private class ParamsRefused(val params: Set<String>) : IOException()

    private companion object {
        val RETRY_CODES = setOf(429, 502, 503, 504)
        const val DEFAULT_RETRY_MS = 3_000L
        const val MAX_RETRY_MS = 20_000L

        /** Optional parameters a service or model refused, per base URL and model, for this run of the app. */
        val refused = ConcurrentHashMap<String, Set<String>>()
    }
}

private val json = Json { ignoreUnknownKeys = true }

/** What a request asks for besides the messages. */
internal data class ChatOptions(val json: Boolean, val temperature: Double, val maxTokens: Int?)

/** The optional parameters, most specific names first (a refusal is matched by name in the error text). */
internal val OPTIONAL_PARAMS = listOf(
    "response_format", "max_completion_tokens", "max_tokens", "temperature", "reasoning_effort", "reasoning", "usage",
)

/**
 * The request body. Leaves out the parameters in [dropped], and those the model is known not to
 * take: OpenAI's reasoning models only allow the default temperature and name the cap
 * `max_completion_tokens`. Reasoning effort is set as low as the service allows, since a
 * translation or summary gains little from thinking and pays for every thinking token.
 */
internal fun chatBody(
    preset: LlmPreset,
    model: String,
    system: String,
    user: String,
    options: ChatOptions,
    dropped: Set<String> = emptySet(),
): String = buildJsonObject {
    fun allowed(name: String) = name !in dropped
    put("model", model)
    putJsonArray("messages") {
        addJsonObject { put("role", "system"); put("content", system) }
        addJsonObject { put("role", "user"); put("content", user) }
    }
    val openAiReasoning = preset == LlmPreset.OpenAI && isOpenAiReasoningModel(model)
    if (!openAiReasoning && allowed("temperature")) put("temperature", options.temperature)
    options.maxTokens?.let { cap ->
        val name = if (preset == LlmPreset.OpenAI) "max_completion_tokens" else "max_tokens"
        if (allowed(name)) put(name, cap)
    }
    if (options.json && allowed("response_format")) putJsonObject("response_format") { put("type", "json_object") }
    when (preset) {
        LlmPreset.OpenAI -> if (openAiReasoning && allowed("reasoning_effort")) {
            put("reasoning_effort", if (model.startsWith("gpt-5")) "minimal" else "low")
        }
        LlmPreset.Gemini -> if (model.contains("gemini-2.5") && allowed("reasoning_effort")) {
            // Flash can think not at all; Pro can only think less.
            put("reasoning_effort", if (model.contains("flash")) "none" else "low")
        }
        LlmPreset.OpenRouter -> {
            if (allowed("reasoning")) putJsonObject("reasoning") { put("effort", "low") }
            if (allowed("usage")) putJsonObject("usage") { put("include", true) }
        }
        LlmPreset.DeepSeek, LlmPreset.Custom -> Unit
    }
}.toString()

private fun isOpenAiReasoningModel(model: String): Boolean =
    model.startsWith("gpt-5") || Regex("""o\d.*""").matches(model)

/** The optional parameters of [body] that the error [text] names, so the request can go again without them. */
internal fun refusedParams(body: String, text: String): Set<String> {
    val sent = runCatching { json.parseToJsonElement(body).jsonObject.keys }.getOrNull() ?: return emptySet()
    val out = LinkedHashSet<String>()
    var rest = text
    for (name in OPTIONAL_PARAMS) {
        if (name !in sent || !rest.contains(name, ignoreCase = true)) continue
        out += name
        // "reasoning_effort" also contains "reasoning": count each mention once.
        rest = rest.replace(name, "", ignoreCase = true)
    }
    return out
}

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
    return LlmAnswer(content, truncated = finish == "length", usage = parseUsage(root["usage"] as? JsonObject))
}

private fun parseUsage(usage: JsonObject?): LlmUsage? {
    usage ?: return null
    fun number(key: String) = (usage[key] as? JsonPrimitive)?.let { it.contentOrNull?.toDoubleOrNull() }
    val prompt = number("prompt_tokens")?.toLong() ?: return null
    val completion = number("completion_tokens")?.toLong() ?: 0L
    return LlmUsage(prompt, completion, number("cost")?.takeIf { it >= 0 })
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

/** An error answer of an online service, with its HTTP [code], so callers can tell a refused key from a busy service. */
class ServiceHttpException(val code: Int, message: String, val retryAfterMs: Long? = null) : IOException(message) {
    /** The user can fix it in the settings: the key, the account or the model. */
    val needsSettings: Boolean get() = code in setOf(401, 402, 403, 404, 456)
}

internal fun llmError(code: Int, body: String, retryAfterMs: Long? = null): ServiceHttpException {
    val detail = runCatching {
        (json.parseToJsonElement(body).jsonObject["error"] as? JsonObject)?.let(::errorMessage)
    }.getOrNull()
    val text = when (code) {
        401, 403 -> "The AI service refused the key; check it in Settings → AI and translation"
        402 -> "The AI service account has no credits left"
        404 -> "The AI service doesn't know this model; pick another one"
        408, 504 -> "The AI service took too long; try again"
        429 -> "The AI service is limiting requests right now; try again later"
        else -> "AI service: HTTP $code"
    }
    return ServiceHttpException(code, if (detail != null && code !in setOf(401, 403)) "$text ($detail)" else text, retryAfterMs)
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
