package com.lume.app.data.source

import org.jsoup.nodes.Element

/** Chapter text cut out of a site's reading page, ready for the text reader. */
internal object NovelHtml {
    private const val NOISE = "script, style, noscript, iframe, ins, form, button, input, select, textarea, " +
        "svg, [class*=adsbygoogle], [id^=div-gpt], [class*=sharedaddy]"

    /**
     * The HTML of [content] without scripts, ads and form controls. Images keep an absolute link
     * (lazy loaders often leave the real one in `data-src`); links keep their text only; other
     * attributes are dropped.
     */
    fun clean(content: Element): String {
        val copy = content.clone()
        copy.select(NOISE).remove()
        copy.select("img").forEach { img ->
            val src = listOf("data-src", "data-lazy-src", "src")
                .map { img.absUrl(it) }
                .firstOrNull { it.startsWith("http") }
            if (src == null) img.remove() else {
                img.clearAttributes()
                img.attr("src", src)
            }
        }
        copy.select("a").forEach { it.unwrap() }
        // Site classes and inline styles mean nothing to the reader and bloat downloads.
        copy.getAllElements().forEach { if (it.tagName() != "img") it.clearAttributes() }
        return copy.html().trim()
    }

    /** True when [html] has something to read: text or a picture. */
    fun hasContent(html: String): Boolean =
        html.contains("<img") || org.jsoup.Jsoup.parseBodyFragment(html).text().isNotBlank()

    /**
     * Class names a page's own stylesheets hide (`.x { display: none }`). Royal Road hides an
     * anti-copy line in every chapter this way; the reader has no CSS, so it must go.
     */
    fun hiddenClasses(styles: List<String>): Set<String> = styles.flatMap { css ->
        HIDDEN_RULE.findAll(css).flatMap { rule ->
            CLASS_NAME.findAll(rule.groupValues[1]).map { it.groupValues[1] }
        }
    }.toSet()

    // Every brace escaped: Android's ICU regex rejects a bare `}` that the JVM accepts, and a
    // failing pattern here breaks the whole object (every Royal Road / Scribble Hub chapter).
    private val HIDDEN_RULE = Regex("""([^\{\}]+)\{[^\}]*display\s*:\s*none[^\}]*\}""", RegexOption.IGNORE_CASE)
    private val CLASS_NAME = Regex("""\.([A-Za-z0-9_-]+)""")
}
