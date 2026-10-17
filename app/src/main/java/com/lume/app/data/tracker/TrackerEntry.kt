package com.lume.app.data.tracker

import com.lume.app.R

/** Reading status in Lume's words; each tracker maps it to its own values. */
enum class TrackStatus(val labelRes: Int) {
    Reading(R.string.track_status_reading),
    Completed(R.string.track_status_completed),
    OnHold(R.string.track_status_on_hold),
    Dropped(R.string.track_status_dropped),
    Planned(R.string.track_status_planned),
    Rereading(R.string.track_status_rereading),
}

/**
 * A title's entry in a tracker's list. [score] is on a 0–10 scale (0 = no score); dates are
 * `yyyy-MM-dd` or null.
 */
data class TrackerEntry(
    val status: TrackStatus? = null,
    val score: Float = 0f,
    val chaptersRead: Int = 0,
    val totalChapters: Int? = null,
    val startDate: String? = null,
    val finishDate: String? = null,
)

/** How a tracker signs in. */
enum class TrackerLogin { OAuth, Password }

/** Maps between [TrackStatus] and a tracker's own status strings. */
class StatusMap(private val pairs: List<Pair<TrackStatus, String>>) {
    val statuses: List<TrackStatus> = pairs.map { it.first }
    fun toRemote(status: TrackStatus): String? = pairs.firstOrNull { it.first == status }?.second
    fun fromRemote(value: String?): TrackStatus? = pairs.firstOrNull { it.second.equals(value, ignoreCase = true) }?.first
}

/** Letters and digits only, lower case: "Solo Leveling!" == "solo leveling". */
fun normalTitle(title: String): String = title.lowercase().filter { it.isLetterOrDigit() }

/**
 * The search result that is clearly the same title: its title or one of its other titles equals
 * one of [names] after [normalTitle]. Null when nothing matches exactly (no guessing).
 */
fun exactMatch(names: List<String>, results: List<TrackerSearchResult>): TrackerSearchResult? {
    val wanted = names.map(::normalTitle).filter { it.isNotEmpty() }.toSet()
    if (wanted.isEmpty()) return null
    return results.firstOrNull { r -> (listOf(r.title) + r.altTitles).any { normalTitle(it) in wanted } }
}

/** `yyyy-MM-dd` from a year / month / day triple (AniList's FuzzyDate); null when incomplete. */
fun fuzzyDate(year: Int?, month: Int?, day: Int?): String? {
    if (year == null || year <= 0) return null
    return "%04d-%02d-%02d".format(year, month?.takeIf { it in 1..12 } ?: 1, day?.takeIf { it in 1..31 } ?: 1)
}

/** Accepts `yyyy-MM-dd` (also the date part of an ISO timestamp); anything else is null. */
fun cleanDate(value: String?): String? =
    value?.trim()?.take(10)?.takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) }
