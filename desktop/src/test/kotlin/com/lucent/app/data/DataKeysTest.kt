package com.lucent.app.data

import android.content.Context
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DataKeysTest {

    private class TestContext(private val dir: File) : Context() {
        override val filesDir: File get() = dir
    }

    private fun freshDir(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-datakeys-test-${System.nanoTime()}")
            .apply { mkdirs() }

    private fun use(dir: File, block: () -> Unit) {
        LocalSecrets.filesDirOverride = dir
        LocalSecrets.resetForTesting()
        DataKeys.resetCacheForTesting()
        try {
            block()
        } finally {
            LocalSecrets.filesDirOverride = null
            LocalSecrets.resetForTesting()
            DataKeys.resetCacheForTesting()
        }
    }

    private fun keyFile(dir: File, name: String): File = File(dir, "keys/$name")

    @Test
    fun unreadableKeyFileThrowsInsteadOfReminting() {
        val dir = freshDir()
        use(dir) {
            val ctx = TestContext(dir)
            DataKeys.databasePassphrase(ctx)
            val keyFile = keyFile(dir, "database.key")
            assertTrue(keyFile.exists())

            keyFile.writeText("garbage-not-a-sealed-key")
            DataKeys.resetCacheForTesting()

            val err = assertFailsWith<IllegalStateException> { DataKeys.databasePassphrase(ctx) }
            assertTrue(
                err.message?.contains("could not be read") == true,
                "expected a 'could not be read' message, got: ${err.message}"
            )
            assertTrue(err.message!!.contains(".lcb"), "message should point at the .lcb backup")

            assertEquals("garbage-not-a-sealed-key", keyFile.readText())
        }
    }

    @Test
    fun zeroLengthKeyFileIsTreatedAsUnreadable() {
        val dir = freshDir()
        use(dir) {
            val ctx = TestContext(dir)
            DataKeys.databasePassphrase(ctx)
            val keyFile = keyFile(dir, "database.key")

            keyFile.writeText("")
            DataKeys.resetCacheForTesting()

            val err = assertFailsWith<IllegalStateException> { DataKeys.databasePassphrase(ctx) }
            assertTrue(err.message!!.contains("could not be read"))
            assertEquals("", keyFile.readText())
        }
    }

    @Test
    fun resetCacheGenuinelyRereads() {
        val dir = freshDir()
        use(dir) {
            val ctx = TestContext(dir)
            val first = DataKeys.databasePassphrase(ctx)

            val other = ByteArray(32) { 9 }
            keyFile(dir, "database.key").writeText(
                LocalSecrets.encrypt(Base64.getEncoder().encodeToString(other))
            )
            DataKeys.resetCacheForTesting()

            val second = DataKeys.databasePassphrase(ctx)
            assertNotEquals(first, second, "resetCacheForTesting must re-read the file")
            val expected = "x'" + other.joinToString("") { "%02x".format(it) } + "'"
            assertEquals(expected, second)
        }
    }
}
