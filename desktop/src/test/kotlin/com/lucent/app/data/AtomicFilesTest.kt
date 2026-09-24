package com.lucent.app.data

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtomicFilesTest {

    private fun freshDir(): File = Files.createTempDirectory("lucent-atomic").toFile()

    @Test
    fun replacesAnExistingTargetOnEveryPlatform() {
        val dir = freshDir()
        val target = File(dir, "master.key").apply { writeText("old") }
        val temp = File(dir, "master.key.tmp").apply { writeText("new") }
        assertTrue(AtomicFiles.replace(temp, target))
        assertEquals("new", target.readText())
        assertFalse(temp.exists())
        dir.deleteRecursively()
    }

    @Test
    fun movesIntoPlaceWhenNoTargetExists() {
        val dir = freshDir()
        val target = File(dir, "attachments.key")
        val temp = File(dir, "attachments.key.tmp").apply { writeText("fresh") }
        assertTrue(AtomicFiles.replace(temp, target))
        assertEquals("fresh", target.readText())
        dir.deleteRecursively()
    }

    @Test
    fun reportsFailureWhenTheSourceIsMissing() {
        val dir = freshDir()
        assertFalse(AtomicFiles.replace(File(dir, "missing.tmp"), File(dir, "target")))
        dir.deleteRecursively()
    }
}
