package com.lume.app.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** One stored reading session, as the statistics see it. */
data class StatSession(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val startedAt: Long,
    val durationMs: Long,
    val pagesRead: Int,
)

/** A chapter marked read at [readAt]. */
data class StatChapterRead(val sourceId: String, val titleId: String, val readAt: Long)

data class MonthStat(
    val month: YearMonth,
    val chapters: Int,
    val pages: Int,
    val durationMs: Long,
)

data class TitleStat(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val chapters: Int,
    val pages: Int,
    val durationMs: Long,
)

data class ReadingStats(
    val chapters: Int,
    val pages: Int,
    val durationMs: Long,
    /** The last [MONTHS] months, newest first; months without reading are present with zeros. */
    val months: List<MonthStat>,
    /** Titles by reading time, longest first, at most [TITLES]. */
    val titles: List<TitleStat>,
) {
    val isEmpty: Boolean get() = chapters == 0 && pages == 0 && durationMs == 0L

    companion object {
        const val MONTHS = 12
        const val TITLES = 50
    }
}

/**
 * Chapters come from the read marks of chapters read in the app (the DAO leaves out marks that
 * only came from imports, sync or "mark as read"), pages and time from the sessions.
 * Months are cut in [zone].
 */
fun computeReadingStats(
    sessions: List<StatSession>,
    chapterReads: List<StatChapterRead>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): ReadingStats {
    fun monthOf(time: Long) = YearMonth.from(Instant.ofEpochMilli(time).atZone(zone))

    val current = monthOf(now)
    val wanted = (0 until ReadingStats.MONTHS).map { current.minusMonths(it.toLong()) }
    val sessionsByMonth = sessions.groupBy { monthOf(it.startedAt) }
    val readsByMonth = chapterReads.groupBy { monthOf(it.readAt) }
    val months = wanted.map { m ->
        val inMonth = sessionsByMonth[m].orEmpty()
        MonthStat(
            month = m,
            chapters = readsByMonth[m]?.size ?: 0,
            pages = inMonth.sumOf { it.pagesRead },
            durationMs = inMonth.sumOf { it.durationMs },
        )
    }

    val chaptersByTitle = chapterReads.groupingBy { it.sourceId to it.titleId }.eachCount()
    val titles = sessions
        .groupBy { it.sourceId to it.titleId }
        .map { (key, list) ->
            TitleStat(
                sourceId = key.first,
                titleId = key.second,
                titleName = list.maxBy { it.startedAt }.titleName,
                chapters = chaptersByTitle[key] ?: 0,
                pages = list.sumOf { it.pagesRead },
                durationMs = list.sumOf { it.durationMs },
            )
        }
        .sortedByDescending { it.durationMs }
        .take(ReadingStats.TITLES)

    return ReadingStats(
        chapters = chapterReads.size,
        pages = sessions.sumOf { it.pagesRead },
        durationMs = sessions.sumOf { it.durationMs },
        months = months,
        titles = titles,
    )
}
