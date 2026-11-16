package com.lume.app.data.local.archive

import java.io.File

/** Every comic archive format: CBZ from :core:data plus CBR (junrar) and CB7 (commons-compress). */
object ComicArchives {
    val formats: Map<ArchiveReaders.Format, ArchiveOpener> = ArchiveReaders.ZIP_ONLY + mapOf(
        ArchiveReaders.Format.Rar to ::RarArchiveReader,
        ArchiveReaders.Format.SevenZ to ::SevenZArchiveReader,
    )

    fun open(file: File): ArchiveReader = ArchiveReaders.open(file, formats)
}
