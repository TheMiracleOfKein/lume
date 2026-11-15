package com.lume.app.domain

/**
 * Russian sources send labels like "Том 2 Глава 15 - Name". When the app is in another language
 * these words are swapped for the app's own ("Vol. 2 Ch. 15 - Name"); the rest stays as sent.
 */
object ChapterLabels {
    @Volatile
    var enabled: Boolean = true

    @Volatile
    var volume: (String) -> String = { "Том $it" }

    @Volatile
    var chapter: (String) -> String = { "Глава $it" }

    private val volumeRegex = Regex("""(?iu)(?<![\p{L}])(?:том|т\.)\s*(\d+(?:[.,]\d+)?)""")
    private val chapterRegex = Regex("""(?iu)(?<![\p{L}])(?:глава|гл\.)\s*(\d+(?:[.,]\d+)?)""")

    fun localize(label: String): String {
        if (!enabled || label.none { it in 'А'..'я' || it == 'ё' || it == 'Ё' }) return label
        return translate(label, volume, chapter)
    }

    /** The pure part, for tests. */
    fun translate(label: String, volume: (String) -> String, chapter: (String) -> String): String =
        label
            .replace(volumeRegex) { volume(it.groupValues[1]) }
            .replace(chapterRegex) { chapter(it.groupValues[1]) }
}
