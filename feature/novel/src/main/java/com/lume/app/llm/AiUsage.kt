package com.lume.app.llm

import com.lume.app.AppContainer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.YearMonth
import java.time.ZoneId

/**
 * What AI requests used: [requests], tokens sent and received, and the [cost] in dollars of the
 * [costRequests] requests whose service reported one (OpenRouter does; others leave it out).
 */
data class UsageTotals(
    val requests: Long = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val cost: Double = 0.0,
    val costRequests: Long = 0,
) {
    operator fun plus(o: UsageTotals) = UsageTotals(
        requests + o.requests, promptTokens + o.promptTokens, completionTokens + o.completionTokens,
        cost + o.cost, costRequests + o.costRequests,
    )

    /** Whether every request reported its cost, so [cost] is the whole bill. */
    val costComplete: Boolean get() = requests > 0 && costRequests == requests
}

/** Usage totals per month (`2026-11`) and feature id ([AiFeature.id]), on this phone only. */
object AiUsage {
    /** Months kept; older ones are dropped. */
    private const val KEEP_MONTHS = 3

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun record(app: AppContainer, feature: AiFeature, usage: LlmUsage) {
        val month = YearMonth.now(ZoneId.systemDefault()).toString()
        app.prefs.updateAiUsage { add(it, month, feature.id, usage) }
    }

    /** The totals of [month] per feature id, from the saved [text]. */
    fun month(text: String?, month: String = YearMonth.now(ZoneId.systemDefault()).toString()): Map<String, UsageTotals> =
        decode(text)[month].orEmpty()

    internal fun add(text: String?, month: String, feature: String, usage: LlmUsage): String {
        val all = decode(text).toMutableMap()
        val features = all[month].orEmpty().toMutableMap()
        val one = UsageTotals(1, usage.promptTokens, usage.completionTokens, usage.cost ?: 0.0, if (usage.cost != null) 1 else 0)
        features[feature] = (features[feature] ?: UsageTotals()) + one
        all[month] = features
        return encode(all.toSortedMap().entries.toList().takeLast(KEEP_MONTHS).associate { it.key to it.value })
    }

    internal fun decode(text: String?): Map<String, Map<String, UsageTotals>> {
        val root = text?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return emptyMap()
        return root.mapNotNull { (month, v) ->
            val features = (v as? JsonObject)?.mapNotNull { (feature, t) ->
                val a = (t as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0 } ?: return@mapNotNull null
                fun at(i: Int) = a.getOrElse(i) { 0.0 }
                feature to UsageTotals(at(0).toLong(), at(1).toLong(), at(2).toLong(), at(3), at(4).toLong())
            }?.toMap() ?: return@mapNotNull null
            month to features
        }.toMap()
    }

    private fun encode(all: Map<String, Map<String, UsageTotals>>): String = buildJsonObject {
        all.forEach { (month, features) ->
            putJsonObject(month) {
                features.forEach { (feature, t) ->
                    putJsonArray(feature) {
                        add(t.requests); add(t.promptTokens); add(t.completionTokens); add(t.cost); add(t.costRequests)
                    }
                }
            }
        }
    }.toString()
}
