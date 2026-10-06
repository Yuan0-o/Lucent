package com.lucent.app.harness

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val harnessFsPlatformSources = listOf(
    "shared/src/jvmMain/kotlin/com/lucent/app/harness/HarnessFs.platform.kt",
    "../shared/src/jvmMain/kotlin/com/lucent/app/harness/HarnessFs.platform.kt",
)

private val harnessPlatformPlatformSources = listOf(
    "shared/src/jvmMain/kotlin/com/lucent/app/harness/HarnessPlatform.platform.kt",
    "../shared/src/jvmMain/kotlin/com/lucent/app/harness/HarnessPlatform.platform.kt",
)

private fun locate(candidates: List<String>): File =
    candidates.map(::File).firstOrNull { it.exists() }
        ?: error("cannot locate ${candidates.first()} from ${System.getProperty("user.dir")}")

class HarnessFsPlatformTest {

    private fun withTempDir(block: (File) -> Unit) {
        val root = java.nio.file.Files.createTempDirectory("lucent-harness-fs").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun platformActualDeclarationsMatchTheCommonContract() {
        val fsSource = locate(harnessFsPlatformSources).readText()
        assertTrue("actual fun systemHarnessFs(): HarnessFs" in fsSource)
        assertTrue("override fun copy(src: String, dst: String, overwrite: Boolean) {" in fsSource)
        val platformSource = locate(harnessPlatformPlatformSources).readText()
        assertTrue("actual fun <T> withHarnessLock(lock: Any, block: () -> T): T" in platformSource)
        assertTrue("actual fun harnessCurrentTimeMillis(): Long" in platformSource)
    }

    @Test
    fun systemHarnessFsRoundTrip() = withTempDir { root ->
        val fs: HarnessFs = systemHarnessFs()
        val dir = File(root, "sub").path
        fs.mkdirs(dir)
        assertTrue(fs.isDirectory(dir), dir)

        val file = fs.join(dir, "note.txt")
        assertEquals("note.txt", fs.nameOf(file))
        assertTrue(fs.isAbsolute(fs.canonicalize(file)))
        assertFalse(fs.exists(file))

        fs.writeText(file, "hello")
        assertTrue(fs.exists(file))
        assertEquals("hello", fs.readText(file))
        assertEquals(listOf("hello"), fs.readLines(file))
        assertTrue(fs.length(file) > 0)

        fs.appendText(file, " world")
        assertEquals("hello world", fs.readText(file))

        val copy = fs.join(dir, "copy.txt")
        fs.copy(file, copy, overwrite = false)
        assertEquals("hello world", fs.readText(copy))
        assertEquals(listOf("copy.txt", "note.txt"), fs.list(dir).sorted())

        assertEquals(dir, fs.parentOf(file))
        assertEquals("sub", fs.relativize(root.path, dir).replace('\\', '/'))

        fs.delete(copy)
        assertFalse(fs.exists(copy))
        fs.deleteRecursively(dir)
        assertFalse(fs.exists(dir))
    }

    @Test
    fun systemHarnessFsReadBytesAndHead() = withTempDir { root ->
        val fs: HarnessFs = systemHarnessFs()
        val file = File(root, "bin.dat").path
        val payload = ByteArray(64) { it.toByte() }
        File(file).writeBytes(payload)
        assertTrue(fs.readBytes(file).contentEquals(payload))
        assertTrue(fs.readHead(file, 8).contentEquals(payload.copyOf(8)))
    }

    @Test
    fun harnessPlatformActualsWork() {
        assertTrue(harnessCurrentTimeMillis() > 0)
        val lock = Any()
        val result = withHarnessLock(lock) { 42 }
        assertEquals(42, result)
    }
}
