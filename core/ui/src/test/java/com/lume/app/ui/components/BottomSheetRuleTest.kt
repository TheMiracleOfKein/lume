package com.lume.app.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every bottom sheet goes through [LumeBottomSheet]. Material's `ModalBottomSheet` used directly pads
 * its top by the status bar it overlaps, and a tall sheet then twitches up and down without end. That
 * bug came back three times (filter sheet in v0.9.14 and v0.9.16, reader sheets in v1.5.2), each time
 * in a sheet that missed the fix. This test fails as soon as a new one does.
 */
class BottomSheetRuleTest {
    private val call = Regex("""\bModalBottomSheet\s*\(""")

    @Test
    fun noSheetBypassesLumeBottomSheet() {
        val root = projectRoot()
        val offenders = listOf("app", "core", "feature")
            .map { File(root, it) }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { "${File.separator}src${File.separator}main${File.separator}" in it.path }
            .filter { it.name != "SheetStates.kt" }
            .filter { file -> file.readLines().any { line -> call.containsMatchIn(line.substringBefore("//")) } }
            .map { it.relativeTo(root).invariantSeparatorsPath }
        assertTrue(
            "Use LumeBottomSheet (core/ui SheetStates.kt), not ModalBottomSheet, in: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun scanFindsTheSources() {
        // Guards the rule itself: a wrong root would scan nothing and always pass.
        val sheets = File(projectRoot(), "core/ui/src/main/java/com/lume/app/ui/components/SheetStates.kt")
        assertTrue("SheetStates.kt not found from ${projectRoot()}", sheets.isFile)
        assertTrue(call.containsMatchIn(sheets.readText()))
    }

    private fun projectRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "settings.gradle.kts not found above ${File("").absolutePath}" }
    }
}
