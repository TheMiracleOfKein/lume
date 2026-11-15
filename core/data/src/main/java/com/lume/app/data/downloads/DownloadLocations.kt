package com.lume.app.data.downloads

/** Pure helpers for where downloads live when the storage is a folder picked through the Storage Access Framework. */
internal object DownloadLocations {
    /** Folder created inside the picked folder; everything Lume saves goes under it. */
    const val SAF_ROOT = "Lume"
    const val PART_SUFFIX = ".lume-part"
    const val CBZ_NAME = "chapter.cbz"
    const val META_NAME = "meta.json"

    /** Folders from the picked folder down to a title (`Lume/<source>/<title>`). */
    fun titleSegments(sourceId: String, titleId: String): List<String> =
        listOf(SAF_ROOT, safeFolderName(sourceId), safeFolderName(titleId))

    /** Folders from the picked folder down to a finished chapter. */
    fun chapterSegments(sourceId: String, titleId: String, chapterId: String): List<String> =
        titleSegments(sourceId, titleId) + safeFolderName(chapterId)

    /** Name of the folder a chapter is copied into before it is complete. */
    fun partName(chapterId: String): String = safeFolderName(chapterId) + PART_SUFFIX

    fun pageName(index: Int, ext: String): String = "%04d.%s".format(index, ext)

    fun isPartName(name: String): Boolean = name.endsWith(PART_SUFFIX)

    /** Files in a chapter folder that are pages (not metadata or leftovers of an unfinished copy). */
    fun isPageName(name: String): Boolean = name != META_NAME && !name.endsWith(".part")

    /** True when [documentUri] (a document inside a tree) belongs to [treeUri]. */
    fun isInTree(documentUri: String, treeUri: String): Boolean =
        documentUri.startsWith("$treeUri/document/")

    fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "avif" -> "image/avif"
        "heic" -> "image/heic"
        "bmp" -> "image/bmp"
        "jxl" -> "image/jxl"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }
}
