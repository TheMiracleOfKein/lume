package com.lume.app.data.local.archive

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipFile

class ZipArchiveReader(file: File) : ArchiveReader {
    private val zip = ZipFile(file)

    private val byName = zip.entries().asSequence()
        .filter { !it.isDirectory }
        .associateBy { it.name.replace('\\', '/') }

    override fun entries(): List<String> = byName.keys.toList()

    override fun open(name: String): InputStream {
        val entry = byName[name] ?: throw FileNotFoundException(name)
        return zip.getInputStream(entry)
    }

    override fun close() = zip.close()
}
