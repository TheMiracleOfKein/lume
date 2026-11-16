package com.lume.app.data.local.archive

import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import com.github.junrar.exception.UnsupportedDictionarySizeException
import com.github.junrar.exception.UnsupportedRarMethodException
import com.github.junrar.exception.UnsupportedRarVersionException as JunrarUnsupportedVersion
import com.github.junrar.rarfile.FileHeader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class RarArchiveReader(file: File) : ArchiveReader {
    private val archive: Archive = try {
        Archive(file)
    } catch (e: JunrarUnsupportedVersion) {
        throw UnsupportedRarVersionException()
    } catch (e: UnsupportedRarMethodException) {
        throw UnsupportedRarVersionException()
    } catch (e: UnsupportedDictionarySizeException) {
        throw UnsupportedRarVersionException()
    } catch (e: RarException) {
        throw IOException(e.message ?: "Can't read this RAR archive", e)
    }

    private val headers: Map<String, FileHeader> = archive.fileHeaders
        .filter { !it.isDirectory }
        .associateBy { it.fileName.replace('\\', '/') }

    override fun entries(): List<String> = headers.keys.toList()

    override fun open(name: String): InputStream {
        val header = headers[name] ?: throw FileNotFoundException(name)
        return archive.getInputStream(header)
    }

    override fun close() = archive.close()
}
