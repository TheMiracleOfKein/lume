package com.lume.app.data.source

import com.lume.app.data.novel.EpubBook
import com.lume.app.data.novel.TxtBook
import com.lume.app.domain.Chapter
import com.lume.app.domain.FilterList
import com.lume.app.domain.HomeFeed
import com.lume.app.domain.NaturalOrder
import com.lume.app.domain.NovelChapter
import com.lume.app.domain.NovelSource
import com.lume.app.domain.PagedResult
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local books under `filesDir/local-novel/<Book>/`: each folder holds one `.epub` or `.txt` file
 * and is one title. Chapters are the book's table of contents (EPUB) or its headings (TXT); the
 * chapter id is `<Book>/<n>` with `n` counting from 0.
 */
class LocalNovelEngine(
    private val filesDir: File,
    private val cacheDir: File,
) : NovelSource {
    override val manifest: SourceManifest = SourceEngines.localNovelManifest

    private fun rootDir(): File = File(filesDir, ROOT).also { it.mkdirs() }

    private fun bookDirs(): List<File> =
        rootDir().listFiles()?.filter { it.isDirectory && bookFile(it) != null }.orEmpty()

    /** The book inside [dir]: an `.epub` first, else a `.txt`. */
    private fun bookFile(dir: File): File? {
        val files = dir.listFiles()?.filter { it.isFile }?.sortedWith(compareBy(NaturalOrder) { it.name }).orEmpty()
        return files.firstOrNull { it.extension.equals("epub", true) }
            ?: files.firstOrNull { it.extension.equals("txt", true) }
    }

    private sealed interface Loaded {
        class Epub(val book: EpubBook) : Loaded
        class Txt(val book: TxtBook) : Loaded
    }

    /** What the catalogue needs without reading the whole book. */
    private class Info(val title: String, val author: String?, val description: String?, val cover: String?, val typeLabel: String)

    private val books = object : LinkedHashMap<String, Loaded>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Loaded>) = size > CACHED_BOOKS
    }
    private val infos = java.util.concurrent.ConcurrentHashMap<String, Info>()

    private fun keyOf(file: File) = "${file.absolutePath}|${file.lastModified()}"

    private fun load(file: File): Loaded = synchronized(books) {
        books.getOrPut(keyOf(file)) {
            if (file.extension.equals("epub", true)) Loaded.Epub(EpubBook.open(file)) else Loaded.Txt(TxtBook.open(file))
        }
    }

    private fun infoOf(dir: File, file: File): Info = infos.getOrPut(keyOf(file)) {
        if (file.extension.equals("epub", true)) {
            runCatching {
                val book = (load(file) as Loaded.Epub).book
                Info(
                    title = book.title ?: dir.name,
                    author = book.author,
                    description = book.description,
                    cover = book.extractCover(File(cacheDir, "local_covers/${coverKey(file)}"))?.absolutePath,
                    typeLabel = "EPUB",
                )
            }.getOrElse { Info(dir.name, null, null, null, "EPUB") }
        } else {
            Info(dir.name, null, null, null, "TXT")
        }
    }

    private fun coverKey(file: File) = "novel_%08x_%d".format(file.absolutePath.hashCode(), file.lastModified())

    private fun summaries(): List<Pair<File, TitleSummary>> = bookDirs().mapNotNull { dir ->
        val file = bookFile(dir) ?: return@mapNotNull null
        val info = infoOf(dir, file)
        dir to TitleSummary(id = dir.name, title = info.title, coverUrl = info.cover, typeLabel = info.typeLabel, subtitle = info.author)
    }

    private fun byName(): List<TitleSummary> =
        summaries().map { it.second }.sortedWith(compareBy(NaturalOrder) { it.title.lowercase() })

    private fun byModified(): List<TitleSummary> =
        summaries().sortedByDescending { (dir, _) -> bookFile(dir)?.lastModified() ?: 0L }.map { it.second }

    override suspend fun getHome(): HomeFeed = withContext(Dispatchers.IO) {
        HomeFeed(spotlight = byName().take(6), latest = byModified())
    }

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> =
        withContext(Dispatchers.IO) { paged(byModified(), page) }

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> =
        withContext(Dispatchers.IO) { paged(byName(), page) }

    private fun paged(all: List<TitleSummary>, page: Int): PagedResult<TitleSummary> {
        val from = ((page - 1) * PAGE_SIZE).coerceAtLeast(0)
        val slice = if (from >= all.size) emptyList() else all.drop(from).take(PAGE_SIZE)
        return PagedResult(items = slice, page = page, hasNext = from + PAGE_SIZE < all.size)
    }

    override fun getFilters(): FilterList = FilterList()

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> =
        withContext(Dispatchers.IO) {
            val q = query.trim().lowercase()
            val hits = byName().filter { q.isEmpty() || it.title.lowercase().contains(q) || it.id.lowercase().contains(q) }
            PagedResult(items = hits, page = 1, hasNext = false)
        }

    override suspend fun getTitle(id: String): TitleDetails = withContext(Dispatchers.IO) {
        val dir = bookDir(id)
        val file = bookFile(dir) ?: error("No book in $id")
        val info = infoOf(dir, file)
        TitleDetails(
            id = id,
            title = info.title,
            coverUrl = info.cover,
            typeLabel = info.typeLabel,
            description = info.description,
            authors = listOfNotNull(info.author),
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> = withContext(Dispatchers.IO) {
        val dir = bookDir(titleId)
        val file = bookFile(dir) ?: return@withContext emptyList()
        val titles = when (val book = load(file)) {
            is Loaded.Epub -> book.book.chapters.map { it.title }
            is Loaded.Txt -> book.book.chapters.map { it.title }
        }
        titles.mapIndexed { i, name ->
            val id = chapterId(titleId, i)
            // The number keeps reading order even when names carry their own numbers.
            Chapter(id = id, title = name, url = id, number = (i + 1).toString())
        }.reversed()
    }

    override suspend fun getChapterText(chapter: Chapter): NovelChapter = withContext(Dispatchers.IO) {
        val (titleId, index) = parseChapterId(chapter.id.ifEmpty { chapter.url })
        val file = bookFile(bookDir(titleId)) ?: error("No book in $titleId")
        val html = when (val book = load(file)) {
            is Loaded.Epub -> book.book.chapterHtml(index, File(cacheDir, "novel_img"))
            is Loaded.Txt -> (book.book.chapters.getOrNull(index) ?: error("No chapter $index in $titleId")).html()
        }
        NovelChapter(chapter.id, html)
    }

    /** The folder of a book; ids come from the app, but a stray `..` must not leave the root. */
    private fun bookDir(id: String): File {
        require(id.isNotEmpty() && '/' !in id && '\\' !in id && id != "." && id != "..") { "Bad book id" }
        return File(rootDir(), id)
    }

    companion object {
        const val ROOT = "local-novel"
        private const val PAGE_SIZE = 50
        private const val CACHED_BOOKS = 4

        fun chapterId(titleId: String, index: Int) = "$titleId/$index"

        /** `<Book>/<n>` to the book folder name and chapter index. */
        fun parseChapterId(id: String): Pair<String, Int> {
            val cut = id.lastIndexOf('/')
            require(cut > 0) { "Bad chapter id: $id" }
            return id.substring(0, cut) to (id.substring(cut + 1).toIntOrNull() ?: error("Bad chapter id: $id"))
        }
    }
}
