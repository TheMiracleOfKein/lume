package com.lume.app.data.local.archive

import java.io.Closeable
import java.io.InputStream

/** Read-only view of a comic archive (CBZ, CBR or CB7). */
interface ArchiveReader : Closeable {
    /** Names of the files in the archive (no directories), with `/` as the separator. */
    fun entries(): List<String>

    /** Opens one entry by a name from [entries]. The caller closes the stream. */
    fun open(name: String): InputStream
}
