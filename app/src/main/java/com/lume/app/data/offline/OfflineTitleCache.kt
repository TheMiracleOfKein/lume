package com.lume.app.data.offline

import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.OfflineTitleCacheEntity
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.TitleDetails
import kotlinx.serialization.json.Json

data class CachedTitle(
    val details: TitleDetails,
    val chapters: List<Chapter>,
)

class OfflineTitleCache(
    private val dao: LibraryDao,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    suspend fun get(sourceId: String, titleId: String): CachedTitle? {
        val row = dao.getOfflineTitleCache(sourceId, titleId) ?: return null
        return runCatching {
            CachedTitle(
                details = json.decodeFromString(row.titleJson),
                chapters = json.decodeFromString(row.chaptersJson),
            )
        }.getOrNull()
    }

    suspend fun save(
        sourceId: String,
        titleId: String,
        details: TitleDetails,
        chapters: List<Chapter>,
    ) {
        dao.upsertOfflineTitleCache(
            OfflineTitleCacheEntity(
                sourceId = sourceId,
                titleId = titleId,
                titleJson = json.encodeToString(details),
                chaptersJson = json.encodeToString(chapters),
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
