package com.lume.app.data.offline

import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.OfflineTitleCacheEntity
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.TitleDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class CachedTitle(
    val details: TitleDetails,
    val chapters: List<Chapter>,
)

class OfflineTitleCache(
    private val dao: LibraryDao,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    /** Decoding a long chapter list is real work; keep it off the main thread (and out of transitions). */
    suspend fun get(sourceId: String, titleId: String): CachedTitle? {
        val row = dao.getOfflineTitleCache(sourceId, titleId) ?: return null
        return withContext(Dispatchers.Default) {
            runCatching {
                CachedTitle(
                    details = json.decodeFromString(row.titleJson),
                    chapters = json.decodeFromString(row.chaptersJson),
                )
            }.getOrNull()
        }
    }

    /** Writes only when something changed, so refreshing an unchanged title costs no disk write. */
    suspend fun save(
        sourceId: String,
        titleId: String,
        details: TitleDetails,
        chapters: List<Chapter>,
    ) {
        val (titleJson, chaptersJson) = withContext(Dispatchers.Default) {
            json.encodeToString(details) to json.encodeToString(chapters)
        }
        val existing = dao.getOfflineTitleCache(sourceId, titleId)
        if (existing != null && existing.titleJson == titleJson && existing.chaptersJson == chaptersJson) return
        dao.upsertOfflineTitleCache(
            OfflineTitleCacheEntity(
                sourceId = sourceId,
                titleId = titleId,
                titleJson = titleJson,
                chaptersJson = chaptersJson,
            ),
        )
    }

    suspend fun detailsFromDownloads(sourceId: String, titleId: String): TitleDetails? {
        get(sourceId, titleId)?.details?.let { return it }
        val downloads = dao.downloadsForTitle(sourceId, titleId)
        val first = downloads.firstOrNull() ?: return null
        return TitleDetails(
            id = titleId,
            title = first.titleName,
            coverUrl = first.coverUrl,
        )
    }

    suspend fun chaptersFromDownloads(sourceId: String, titleId: String): List<Chapter> {
        get(sourceId, titleId)?.chapters?.takeIf { it.isNotEmpty() }?.let { return it }
        return dao.downloadsForTitle(sourceId, titleId).map {
            Chapter(
                id = it.chapterId,
                title = it.chapterName,
                url = it.chapterId,
            )
        }
    }

}

/**
 * Network-first load with an offline fallback that does not swallow errors: if [fetch] fails and
 * [fallback] has nothing usable, the original exception (Cloudflare challenge, HTTP 403, …) is
 * rethrown so screens can show the real reason or open the challenge WebView.
 */
suspend fun <T : Any> networkOr(
    source: CatalogSource?,
    fetch: suspend CatalogSource.() -> T,
    isUsable: (T) -> Boolean = { true },
    fallback: suspend () -> T?,
): T? {
    if (source == null) return fallback()
    val failure = try {
        return source.fetch()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }
    return fallback()?.takeIf(isUsable) ?: throw failure
}
