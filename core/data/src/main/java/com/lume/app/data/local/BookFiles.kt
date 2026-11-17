package com.lume.app.data.local

import java.io.File

/**
 * Which picked files are books and where they are stored. A book is a single file (`.epub`,
 * `.txt`): it is copied to `local-novel/<file name without extension>/<file name>`, whatever
 * folder it was found in.
 */
internal object BookFiles {
    const val FOLDER = "local-novel"
    const val PLAIN_TEXT = "txt"

    /** How deep below the picked folder books are looked for (the folder itself is 0). */
    const val MAX_DEPTH = 3

    fun isBook(fileName: String, extensions: Set<String>): Boolean =
        extensions.isNotEmpty() && fileName.substringAfterLast('.', "").lowercase() in extensions

    /** The book folder name: the file name without its extension. */
    fun folderName(fileName: String): String = safeName(fileName.substringBeforeLast('.', fileName))

    /** Where a picked book file is stored under [root] (`filesDir/local-novel`). */
    fun destination(root: File, fileName: String): File = File(File(root, folderName(fileName)), safeName(fileName))

    fun safeName(name: String): String = name.replace(Regex("""[<>:"/\|?*]"""), "_").trim().ifEmpty { "_" }
}
