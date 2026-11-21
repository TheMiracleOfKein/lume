package com.lume.app.translate

import android.content.Context
import com.lume.translate.ModelCatalog
import com.lume.translate.ModelPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Where chapters are translated. [id] is what the settings store. */
enum class TranslationEngine(val id: String, val online: Boolean) {
    /** On the phone, with the Lume Translate add-on and a downloaded language pack. */
    Offline("offline", online = false),

    /** Google Translate's free web endpoint: no key, chapter text goes to Google. */
    Google("google", online = true),

    /** DeepL's API with the user's own key: chapter text goes to DeepL. */
    DeepL("deepl", online = true),

    /** A chat model through an OpenAI-compatible API with the user's own key (OpenRouter and others). */
    Llm("llm", online = true),
    ;

    companion object {
        fun fromId(id: String?): TranslationEngine? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Translates HTML paragraphs (bold, italic and glossary `<span data-g>` marks kept) from one
 * language to another, in order, one result per input.
 */
interface TranslationProvider {
    /** Part of the cache key: output of different providers isn't mixed. */
    val cacheId: String

    /**
     * Whether a new glossary term means translating cached chapters again. True for engines that
     * only see the marks; false for [LlmProvider], which adds terms itself while translating.
     */
    val glossaryInCacheKey: Boolean get() = true

    /** Characters in the first request (about a screen, so reading starts soon) and in later ones. */
    val firstRunChars: Int get() = 1_000
    val runChars: Int get() = 3_000

    suspend fun translate(from: String, to: String, html: List<String>): List<String>
}

/** The Lume Translate add-on (Bergamot, on the phone). */
class AddonProvider(private val context: Context, private val pair: ModelPair) : TranslationProvider {
    override val cacheId: String = "offline:${pair.model.sha256}"

    override suspend fun translate(from: String, to: String, html: List<String>): List<String> =
        TranslateAddon.translate(context, pair, html)

    companion object {
        fun pairFor(from: String, to: String): ModelPair? = ModelCatalog.find(from, to)
    }
}

/**
 * Google Translate's free web endpoint (`translate_a/t`, `format=html`), the one browser
 * extensions use. Unofficial: Google may limit or change it; errors say so.
 */
class GoogleProvider(private val http: OkHttpClient) : TranslationProvider {
    override val cacheId: String = "google"

    override suspend fun translate(from: String, to: String, html: List<String>): List<String> = withContext(Dispatchers.IO) {
        val out = ArrayList<String>(html.size)
        for (batch in batches(html, GOOGLE_BATCH_CHARS)) {
            val body = FormBody.Builder().apply { batch.forEach { add("q", it) } }.build()
            val request = Request.Builder()
                .url("https://translate.googleapis.com/translate_a/t?client=gtx&format=html&sl=$from&tl=$to")
                .post(body)
                .build()
            http.newCall(request).execute().use { response ->
                if (response.code == 429) throw IOException("Google is limiting requests right now; try again later or pick another engine")
                if (!response.isSuccessful) throw IOException("Google Translate: HTTP ${response.code}")
                val translated = parseGoogle(response.body.string())
                if (translated.size != batch.size) throw IOException("Google Translate answered ${translated.size} of ${batch.size} paragraphs")
                out += translated
            }
        }
        out
    }
}

/** DeepL's API with the user's key; `:fx` keys use the free endpoint. Keeps HTML tags. */
class DeepLProvider(private val http: OkHttpClient, private val key: String) : TranslationProvider {
    override val cacheId: String = "deepl"

    override suspend fun translate(from: String, to: String, html: List<String>): List<String> = withContext(Dispatchers.IO) {
        val out = ArrayList<String>(html.size)
        for (batch in batches(html, DEEPL_BATCH_CHARS).flatMap { it.chunked(DEEPL_MAX_TEXTS) }) {
            val request = Request.Builder()
                .url(deeplUrl(key))
                .header("Authorization", "DeepL-Auth-Key $key")
                .post(deeplBody(from, to, batch).toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    403 -> throw IOException("DeepL refused the key; check it in the translation sheet")
                    456 -> throw IOException("This month's DeepL quota is used up")
                    429 -> throw IOException("DeepL is limiting requests right now; try again later")
                }
                if (!response.isSuccessful) throw IOException("DeepL: HTTP ${response.code}")
                val translated = parseDeepL(response.body.string())
                if (translated.size != batch.size) throw IOException("DeepL answered ${translated.size} of ${batch.size} paragraphs")
                out += translated
            }
        }
        out
    }
}

private const val GOOGLE_BATCH_CHARS = 5_000
private const val DEEPL_BATCH_CHARS = 30_000
private const val DEEPL_MAX_TEXTS = 50

private val json = Json { ignoreUnknownKeys = true }

/** Google's answer: a JSON array with one string per `q` (or `[text, language]` pairs). */
internal fun parseGoogle(body: String): List<String> {
    val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray
        ?: throw IOException("Google Translate sent an unexpected answer")
    return root.map { el ->
        when (el) {
            is JsonPrimitive -> el.contentOrNull.orEmpty()
            is JsonArray -> (el.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
            else -> ""
        }
    }
}

internal fun deeplUrl(key: String): String =
    if (key.trim().endsWith(":fx")) "https://api-free.deepl.com/v2/translate" else "https://api.deepl.com/v2/translate"

internal fun deeplBody(from: String, to: String, texts: List<String>): String = buildJsonObject {
    putJsonArray("text") { texts.forEach { add(JsonPrimitive(it)) } }
    put("source_lang", from.uppercase())
    // DeepL wants a variant for English targets.
    put("target_lang", if (to.equals("en", ignoreCase = true)) "EN-US" else to.uppercase())
    put("tag_handling", "html")
}.toString()

internal fun parseDeepL(body: String): List<String> {
    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        ?: throw IOException("DeepL sent an unexpected answer")
    return root["translations"]?.jsonArray?.map { (it as JsonObject)["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        ?: throw IOException("DeepL sent an unexpected answer")
}
