package com.lume.app.data.local.archive

import com.lume.app.R
import com.lume.app.data.locale.appString
import java.io.Closeable
import java.io.IOException
import java.io.InputStream

/** Read-only view of a comic archive (CBZ, CBR or CB7). */
interface ArchiveReader : Closeable {
    /** Names of the files in the archive (no directories), with `/` as the separator. */
    fun entries(): List<String>

    /** Opens one entry by a name from [entries]. The caller closes the stream. */
    fun open(name: String): InputStream
}

/** A RAR archive the reader can't unpack (a RAR5 variant or method junrar doesn't handle). */
class UnsupportedRarVersionException : IOException(appString(R.string.error_rar5_unsupported))

/** An archive that is damaged or can't be read. */
class DamagedArchiveException(cause: Throwable? = null) : IOException(appString(R.string.error_archive_damaged), cause)
