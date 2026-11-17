package com.lume.app.data.source

import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.data.network.AppHttp
import com.lume.app.domain.Chapter
import com.lume.app.domain.NovelChapter
import com.lume.app.domain.NovelSource
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceManifest

/**
 * RanobeLib: the shared Lib API ([LibSocialBase], `Site-Id: 3`) with chapter text instead of
 * pages. The chapter answer is the manga one with `content` and `attachments` in place of
 * `pages`; [LibChapterText] turns it into HTML for the text reader.
 */
class RanobeLibEngine(
    manifest: SourceManifest,
    http: AppHttp,
    auth: LibAuthStore? = null,
) : LibSocialBase(manifest, http, auth), NovelSource {
    override suspend fun getChapterText(chapter: Chapter): NovelChapter =
        NovelChapter(chapter.id, parseChapterText(fetchChapter(chapter).body))

    fun parseChapterText(body: String): String {
        val root = asObject(json.parseToJsonElement(body))
        val data = asObject(root["data"] ?: root)
        data["toast"].obj()?.str("message")?.let { throw SourceFetchException(it) }
        return LibChapterText.toHtml(data, manifest.baseUrl)
            ?: throw SourceFetchException(appString(R.string.error_text_failed_named, manifest.name))
    }
}
