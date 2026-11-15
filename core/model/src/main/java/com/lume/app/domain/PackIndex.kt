package com.lume.app.domain

import kotlinx.serialization.Serializable

/**
 * The pack catalogue (`docs/packs/index.json`): source packs that can be installed from the
 * Sources screen. [PackEntry.url] may be relative to the index file.
 */
@Serializable
data class PackIndex(
    val schemaVersion: Int = 1,
    val packs: List<PackEntry> = emptyList(),
)

@Serializable
data class PackEntry(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val engine: String,
    val language: String? = null,
    val nsfw: Boolean = false,
    val url: String,
)

/** What the Sources screen shows for one catalogue entry. */
enum class PackState { NotInstalled, Installed, UpdateAvailable }

fun packState(entry: PackEntry, installedVersion: String?): PackState = when {
    installedVersion == null -> PackState.NotInstalled
    compareVersions(entry.version, installedVersion) > 0 -> PackState.UpdateAvailable
    else -> PackState.Installed
}

/** Resolves a pack url against the index url ("madara/x.json" next to ".../index.json"). */
fun resolvePackUrl(indexUrl: String, packUrl: String): String {
    if (packUrl.startsWith("http://") || packUrl.startsWith("https://")) return packUrl
    return java.net.URI(indexUrl).resolve(packUrl).toString()
}

/** Setting values merged over a manifest's own engine config. */
fun SourceManifest.withSettings(values: Map<String, String>?): SourceManifest {
    if (values.isNullOrEmpty()) return this
    val merged = engineConfig.toMutableMap()
    values.forEach { (k, v) -> merged[k] = kotlinx.serialization.json.JsonPrimitive(v) }
    return copy(engineConfig = kotlinx.serialization.json.JsonObject(merged))
}
