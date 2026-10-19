package com.lume.app.data.tracker

import android.content.Context
import com.lume.app.R
import com.lume.app.data.db.ChapterReadEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.network.AppHttp
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.domain.Chapter

class TrackerRegistry(
    http: AppHttp,
    dao: LibraryDao,
    prefs: AppPreferences,
) {
    val anilist = AniListTracker(http, dao, prefs)
    val shikimori = ShikimoriTracker(http, dao, prefs)
    val myAnimeList = MyAnimeListTracker(http, dao, prefs)
    val kitsu = KitsuTracker(http, dao, prefs)
    val mangaUpdates = MangaUpdatesTracker(http, dao, prefs)
    val all: List<Tracker> = listOf(shikimori, anilist, myAnimeList, kitsu, mangaUpdates)

    fun byId(id: String): Tracker? = all.firstOrNull { it.id == id }
}

class TrackerSync(
    private val context: Context,
    private val dao: LibraryDao,
    private val trackers: TrackerRegistry,
    private val prefs: AppPreferences? = null,
) {
    /**
     * Adding a title to the library: every signed-in tracker without a link gets one when its
     * search finds the title under exactly the same name. Nothing is guessed.
     */
    suspend fun autoLink(sourceId: String, titleId: String, names: List<String>) {
        if (prefs?.trackerAutoLink?.value == false) return
        val query = names.firstOrNull { it.isNotBlank() } ?: return
        val linked = dao.getTrackerLinks(sourceId, titleId).map { it.tracker }.toSet()
        for (tracker in trackers.all) {
            if (tracker.id in linked || !runCatching { tracker.isLoggedIn() }.getOrDefault(false)) continue
            val results = runCatching { tracker.search(query) }.getOrDefault(emptyList())
            val match = exactMatch(names, results) ?: continue
            runCatching { tracker.link(sourceId, titleId, match.remoteId, match.url) }
        }
    }

    suspend fun pushProgress(
        sourceId: String,
        titleId: String,
        chapters: List<Chapter>,
        chapterId: String,
    ) {
        val progressNum = chapterProgressNumber(chapters, chapterId)
        if (progressNum <= 0) return
        val links = dao.getTrackerLinks(sourceId, titleId)
        for (link in links) {
            val tracker = trackers.byId(link.tracker) ?: continue
            if (!tracker.isLoggedIn()) continue
            val remote = runCatching { tracker.fetchProgress(link.remoteId) }.getOrNull()
            val remoteCh = remote?.chaptersRead ?: 0
            if (progressNum <= remoteCh) {
                dao.upsertTrackerLink(link.copy(lastSync = System.currentTimeMillis()))
                continue
            }
            val ok = runCatching { tracker.updateProgress(link.remoteId, progressNum) }.getOrDefault(false)
            if (ok) {
                dao.upsertTrackerLink(
                    link.copy(
                        lastSync = System.currentTimeMillis(),
                        status = remote?.status ?: "watching",
                    ),
                )
            }
        }
    }

    suspend fun syncTitle(
        sourceId: String,
        titleId: String,
        chapters: List<Chapter>,
        titleName: String,
        coverUrl: String?,
        typeLabel: String?,
    ): TitleSyncResult {
        val links = dao.getTrackerLinks(sourceId, titleId)
        if (links.isEmpty()) {
            return TitleSyncResult(
                context.getString(R.string.sync_no_links),
                listOf(context.getString(R.string.sync_no_links_hint)),
            )
        }

        val localProgress = dao.getProgress(sourceId, titleId)
        val readIds = dao.readChapterIds(sourceId, titleId).toSet()
        val (localBest, localChapter) = resolveLocalChapterProgress(
            chapters = chapters,
            progressChapterId = localProgress?.chapterId,
            readChapterIds = readIds,
        )

        val details = mutableListOf<String>()
        details += context.getString(R.string.sync_local_progress, localBest)

        var target = localBest
        var targetChapter = localChapter
        val remoteByTracker = mutableMapOf<String, TrackerProgress?>()

        for (link in links) {
            val tracker = trackers.byId(link.tracker) ?: continue
            if (!tracker.isLoggedIn()) {
                details += context.getString(R.string.sync_not_logged_in, tracker.displayName)
                remoteByTracker[link.tracker] = null
                continue
            }
            val remote = runCatching { tracker.fetchProgress(link.remoteId) }
                .onFailure {
                    details += context.getString(
                        R.string.sync_read_error,
                        tracker.displayName,
                        it.message ?: "network",
                    )
                }
                .getOrNull()
            remoteByTracker[link.tracker] = remote
            if (remote == null) {
                details += context.getString(R.string.sync_progress_unavailable, tracker.displayName)
                continue
            }
            details += remote.status?.let {
                context.getString(
                    R.string.sync_remote_progress_status,
                    tracker.displayName,
                    remote.chaptersRead,
                    it,
                )
            } ?: context.getString(
                R.string.sync_remote_progress,
                tracker.displayName,
                remote.chaptersRead,
            )
            if (remote.chaptersRead > target) {
                target = remote.chaptersRead
                targetChapter = findChapterForProgress(chapters, target) ?: targetChapter
            }
        }

        if (target <= 0) {
            return TitleSyncResult(
                context.getString(R.string.sync_nothing),
                details + context.getString(R.string.sync_nothing_hint),
            )
        }

        val chapter = targetChapter
            ?: findChapterForProgress(chapters, target)
            ?: return TitleSyncResult(
                context.getString(R.string.sync_chapter_missing, target),
                details + context.getString(R.string.sync_chapter_missing_hint),
            )

        var localUpdated = false
        if (target > localBest) {
            dao.upsertProgress(
                ProgressEntity(
                    sourceId = sourceId,
                    titleId = titleId,
                    titleName = titleName,
                    coverUrl = coverUrl,
                    typeLabel = typeLabel,
                    chapterId = chapter.id,
                    chapterName = chapter.title,
                    chapterUrl = chapter.url,
                    pageIndex = localProgress?.takeIf { it.chapterId == chapter.id }?.pageIndex ?: 0,
                    pageCount = localProgress?.takeIf { it.chapterId == chapter.id }?.pageCount ?: 0,
                ),
            )
            var marked = 0
            for (ch in chapters) {
                val n = chapterProgressNumber(chapters, ch.id)
                if (n in 1..target && ch.id !in readIds) {
                    dao.markChapterRead(
                        ChapterReadEntity(
                            sourceId = sourceId,
                            titleId = titleId,
                            chapterId = ch.id,
                        ),
                    )
                    marked++
                }
            }
            localUpdated = true
            details += context.getString(R.string.sync_local_pulled, target) +
                if (marked > 0) context.getString(R.string.sync_marked_read, marked) else ""
        } else if (localProgress == null) {
            dao.upsertProgress(
                ProgressEntity(
                    sourceId = sourceId,
                    titleId = titleId,
                    titleName = titleName,
                    coverUrl = coverUrl,
                    typeLabel = typeLabel,
                    chapterId = chapter.id,
                    chapterName = chapter.title,
                    chapterUrl = chapter.url,
                ),
            )
            localUpdated = true
        }

        var pushed = 0
        var alreadyOk = 0
        var failed = 0
        for (link in links) {
            val tracker = trackers.byId(link.tracker) ?: continue
            if (!tracker.isLoggedIn()) continue
            val remote = remoteByTracker[link.tracker]
            val remoteCh = remote?.chaptersRead ?: -1
            when {
                remoteCh >= target -> {
                    alreadyOk++
                    dao.upsertTrackerLink(
                        link.copy(
                            lastSync = System.currentTimeMillis(),
                            status = remote?.status ?: link.status,
                            score = remote?.score ?: link.score,
                        ),
                    )
                    details += context.getString(R.string.sync_already_up_to_date, tracker.displayName)
                }
                else -> {
                    val ok = runCatching { tracker.updateProgress(link.remoteId, target) }
                        .onFailure {
                            failed++
                            details += context.getString(
                                R.string.sync_write_error,
                                tracker.displayName,
                                it.message ?: "fail",
                            )
                        }
                        .getOrDefault(false)
                    if (ok) {
                        pushed++
                        dao.upsertTrackerLink(
                            link.copy(
                                lastSync = System.currentTimeMillis(),
                                status = remote?.status ?: "watching",
                                score = remote?.score ?: link.score,
                            ),
                        )
                        details += if (remoteCh >= 0) {
                            context.getString(
                                R.string.sync_pushed_was,
                                tracker.displayName,
                                target,
                                remoteCh,
                            )
                        } else {
                            context.getString(R.string.sync_pushed, tracker.displayName, target)
                        }
                    } else {
                        failed++
                        details += context.getString(R.string.sync_update_failed, tracker.displayName)
                    }
                }
            }
        }

        val summary = buildString {
            append(context.getString(R.string.sync_summary, target))
            when {
                localUpdated && pushed > 0 -> append(context.getString(R.string.sync_summary_pull_and_push))
                localUpdated -> append(context.getString(R.string.sync_summary_pull_local))
                pushed > 0 -> append(context.getString(R.string.sync_summary_pushed))
                alreadyOk > 0 && failed == 0 -> append(context.getString(R.string.sync_summary_already))
                failed > 0 -> append(context.getString(R.string.sync_summary_errors))
                else -> append(context.getString(R.string.sync_summary_unchanged))
            }
        }
        return TitleSyncResult(summary, details)
    }
}
