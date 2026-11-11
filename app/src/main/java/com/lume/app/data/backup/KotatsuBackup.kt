package com.lume.app.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/*
 * The parts of a Kotatsu backup (`.bk.zip`) that Lume imports. The zip holds JSON arrays named
 * `index`, `history`, `categories`, `favourites`, `bookmarks`, `sources`, `settings` (some versions
 * add `.json`). Lume reads categories and favourites; unknown entries and keys are skipped.
 * History is not read: it points at chapters by a hash id and has no chapter number, so there is
 * nothing Lume could match honestly.
 */

@Serializable
data class KotatsuManga(
    val id: Long = 0,
    val title: String = "",
    val url: String = "",
    @SerialName("public_url") val publicUrl: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("large_cover_url") val largeCoverUrl: String? = null,
    /** Kotatsu's parser enum name, such as `MANGADEX` or `ASURASCANS`. */
    val source: String = "",
)

@Serializable
data class KotatsuFavourite(
    @SerialName("manga_id") val mangaId: Long = 0,
    @SerialName("category_id") val categoryId: Long = 0,
    @SerialName("sort_key") val sortKey: Int = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    val manga: KotatsuManga? = null,
)

@Serializable
data class KotatsuCategory(
    @SerialName("category_id") val id: Long = 0,
    val title: String = "",
    @SerialName("sort_key") val sortKey: Int = 0,
)

/** One library title: every favourite row of the same manga merged, with its category names. */
data class KotatsuTitle(
    val manga: KotatsuManga,
    val createdAt: Long,
    val categoryNames: List<String>,
)

data class KotatsuBackup(
    val categories: List<KotatsuCategory> = emptyList(),
    val favourites: List<KotatsuFavourite> = emptyList(),
) {
    /** Library titles; a manga in several categories is one title. Rows without a manga are dropped. */
    fun titles(): List<KotatsuTitle> {
        val names = categories.associate { it.id to it.title.trim() }
        val byManga = LinkedHashMap<Long, MutableList<KotatsuFavourite>>()
        for (f in favourites.sortedBy { it.sortKey }) {
            val manga = f.manga ?: continue
            byManga.getOrPut(if (f.mangaId != 0L) f.mangaId else manga.id) { mutableListOf() } += f
        }
        return byManga.values.map { rows ->
            KotatsuTitle(
                manga = rows.first().manga!!,
                createdAt = rows.minOf { it.createdAt },
                categoryNames = rows.mapNotNull { names[it.categoryId]?.takeIf(String::isNotEmpty) }.distinct(),
            )
        }
    }
}

object KotatsuBackupFormat {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /** True for a zip file (`PK\u0003\u0004`). */
    fun isZip(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /**
     * Reads the zip. Each entry, and all entries together, are capped at [maxBytes] so a zip bomb
     * throws instead of filling memory. A missing or broken `categories` / `favourites` entry gives
     * an empty list; a zip with neither is not a Kotatsu backup and throws.
     */
    fun parse(bytes: ByteArray, maxBytes: Long = MAX_BACKUP_BYTES): KotatsuBackup {
        var categories: List<KotatsuCategory>? = null
        var favourites: List<KotatsuFavourite>? = null
        var budget = maxBytes
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/').lowercase().removeSuffix(".json")
                if (name != "categories" && name != "favourites") continue
                val data = zip.readCapped(budget)
                budget -= data.size
                val text = data.decodeToString().trimStart(0xFEFF.toChar())
                if (name == "categories") {
                    categories = runCatching { json.decodeFromString(ListSerializer(KotatsuCategory.serializer()), text) }.getOrNull()
                } else {
                    favourites = runCatching { json.decodeFromString(ListSerializer(KotatsuFavourite.serializer()), text) }.getOrNull()
                }
            }
        }
        if (categories == null && favourites == null) throw java.io.IOException("Not a Kotatsu backup")
        return KotatsuBackup(categories.orEmpty(), favourites.orEmpty())
    }

    /**
     * Picks the Lume source for a Kotatsu parser name ("ASURASCANS", "MANGA_KATANA"); [lume] holds
     * (id, name) pairs. Ids count too, so a source whose name differs from its id still matches.
     */
    fun matchSource(kotatsuName: String, lume: List<Pair<String, String>>): String? {
        val wanted = MihonBackupFormat.normalName(kotatsuName)
        if (wanted.isEmpty() || wanted == "local") return null
        lume.firstOrNull { MihonBackupFormat.normalName(it.first) == wanted }?.let { return it.first }
        return MihonBackupFormat.matchSource(kotatsuName, lume)
    }

    /** Links to try for a title, best first: the public link, then the source's own link (relative or full). */
    fun linkCandidates(manga: KotatsuManga, baseUrl: String): List<String> {
        val own = manga.url.trim().takeIf { it.isNotEmpty() }?.let {
            if (it.startsWith("http")) it else baseUrl.trimEnd('/') + "/" + it.trimStart('/')
        }
        return listOfNotNull(manga.publicUrl.trim().takeIf { it.startsWith("http") }, own).distinct()
    }
}
