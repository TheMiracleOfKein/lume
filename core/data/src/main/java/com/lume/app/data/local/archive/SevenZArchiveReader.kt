package com.lume.app.data.local.archive

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class SevenZArchiveReader(file: File) : ArchiveReader {
    // Caps the memory a (possibly hostile) LZMA dictionary may ask for.
    private val sevenZ: SevenZFile = SevenZFile.builder()
        .setFile(file)
        .setMaxMemoryLimitKiB(MAX_MEMORY_KIB)
        .get()

    private val byName: Map<String, SevenZArchiveEntry> = sevenZ.entries
        .filter { !it.isDirectory && !it.isAntiItem }
        .associateBy { it.name.replace('\\', '/') }

    override fun entries(): List<String> = byName.keys.toList()

    override fun open(name: String): InputStream {
        val entry = byName[name] ?: throw FileNotFoundException(name)
        return sevenZ.getInputStream(entry)
    }

    override fun close() = sevenZ.close()

    private companion object {
        const val MAX_MEMORY_KIB = 256 * 1024
    }
}
