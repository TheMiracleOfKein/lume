package com.lume.app.data.local

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Regenerates `src/test/resources/archives/`: run with `LUME_WRITE_FIXTURES=1`. Otherwise it does
 * nothing.
 */
class FixtureWriter {
    @Test
    fun writeFixtures() {
        assumeTrue(System.getenv("LUME_WRITE_FIXTURES") == "1")
        val dir = File("src/test/resources/archives").also { it.mkdirs() }
        File(dir, "sample.cbr").writeBytes(ArchiveFixtures.rar4(ArchiveFixtures.samplePages()))
        File(dir, "sample.cb7").delete()
        ArchiveFixtures.sevenZ(ArchiveFixtures.samplePages(), File(dir, "sample.cb7"))
    }
}
