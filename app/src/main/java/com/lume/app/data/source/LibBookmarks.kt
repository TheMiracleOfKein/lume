package com.lume.app.data.source

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** A bookmark folder on a MangaLib-family site ("Читаю", "В планах", or one the user made). */
data class LibBookmarkFolder(val id: String, val name: String)

/** One bookmarked title. [titleId] is the Lume id (`/<slug_url>`). */
data class LibBookmark(
    val titleId: String,
    val title: String,
    val coverUrl: String?,
    val typeLabel: String?,
    val folderId: String?,
)

/** A page of `/bookmarks`; [hasNext] says whether another page should be asked for. */
data class LibBookmarkPage(val items: List<LibBookmark>, val hasNext: Boolean)

/* Parsing for the cdnlibs bookmark endpoints. Fields are read tolerantly: the API has moved names around before. */

private val bookmarkJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** `/bookmarks/folder/{userId}`: `{ data: [ { id, name, … } ] }`. */
internal fun parseLibBookmarkFolders(raw: String): List<LibBookmarkFolder> {
    val root = runCatching { bookmarkJson.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
    val list = root.arr() ?: root.obj()?.get("data").let { it.arr() ?: it.obj()?.get("data").arr() }
    return list.orEmpty().mapNotNull { el ->
        val item = el.obj() ?: return@mapNotNull null
        val id = item.str("id") ?: return@mapNotNull null
        val name = (item.str("name") ?: item.str("label"))?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        LibBookmarkFolder(id, name)
    }.distinctBy { it.id }
}

/**
 * `/bookmarks?user_id=…&status=…&page=…`. Each entry holds the title under `media` (older
 * answers put its fields at the top level). Titles of another Lib site are skipped when the
 * entry says which site it belongs to. [folderId] is used when an entry doesn't name its folder.
 */
internal fun parseLibBookmarksPage(raw: String, siteId: String, folderId: String?): LibBookmarkPage {
    val root = runCatching { bookmarkJson.parseToJsonElement(raw) }.getOrNull()
        ?: return LibBookmarkPage(emptyList(), hasNext = false)
    val data = root.obj()?.get("data") ?: root
    val list = data.arr() ?: data.obj()?.get("data").arr()
    val items = list.orEmpty().mapNotNull { el -> parseLibBookmark(el, siteId, folderId) }
    val meta = root.obj()?.get("meta").obj()
    val links = root.obj()?.get("links").obj()
    val hasNext = meta?.get("has_next_page").bool()
        ?: meta?.get("next_page_url").bool()
        ?: links?.str("next")?.let { true }
        ?: run {
            val current = meta.str("current_page")?.toIntOrNull()
            val last = meta.str("last_page")?.toIntOrNull()
            if (current != null && last != null) current < last else null
        }
        ?: (list.orEmpty().size >= LIB_BOOKMARKS_PAGE_GUESS)
    return LibBookmarkPage(items, hasNext && list.orEmpty().isNotEmpty())
}

private fun parseLibBookmark(el: JsonElement, siteId: String, folderId: String?): LibBookmark? {
    val entry = el.obj() ?: return null
    val media = entry["media"].obj() ?: entry
    val site = media.str("site") ?: media.str("site_id") ?: entry.str("site_id")
    if (site != null && site != siteId) return null
    val slug = media.str("slug_url") ?: entry.str("media_slug_url") ?: media.str("slug") ?: return null
    val cover = media["cover"].let { c ->
        c.obj()?.let { it.str("default") ?: it.str("md") ?: it.str("thumbnail") } ?: c.str()
    }
    val type = media["type"].let { t -> t.obj()?.let { it.str("label") ?: it.str("name") } ?: t.str() }
    val folder = entry.str("status")
        ?: entry["bookmark"].obj().str("status")
        ?: entry.str("folder_id")
        ?: folderId
    return LibBookmark(
        titleId = "/${slug.trim('/')}",
        title = media.str("rus_name") ?: media.str("name") ?: media.str("eng_name") ?: slug,
        coverUrl = cover,
        typeLabel = type,
        folderId = folder,
    )
}

/** `status` value that asks `/bookmarks` for every bookmark, whatever folder it is in. */
internal const val LIB_ALL_BOOKMARKS = "0"

/** The statuses every Lib account has, by id. The user's own folders have large ids. */
private val DEFAULT_LIB_FOLDERS = mapOf(
    "1" to "Читаю",
    "2" to "В планах",
    "3" to "Брошено",
    "4" to "Прочитано",
    "5" to "Любимые",
)

/**
 * [siteFolders] plus a name for each built-in status in [usedIds] the site didn't list, so a
 * failed `/bookmarks/folder` call still leaves the standard folders as categories. The user's
 * own folders without a name stay unnamed: their titles are imported without that category.
 */
internal fun withDefaultLibFolders(siteFolders: List<LibBookmarkFolder>, usedIds: Set<String>): List<LibBookmarkFolder> {
    val known = siteFolders.mapTo(HashSet()) { it.id }
    val extra = DEFAULT_LIB_FOLDERS.filterKeys { it in usedIds && it !in known }
        .map { (id, name) -> LibBookmarkFolder(id, name) }
    return siteFolders + extra
}

/** With no paging info, a full page means there may be more. */
private const val LIB_BOOKMARKS_PAGE_GUESS = 20
