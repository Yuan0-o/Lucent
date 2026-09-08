package com.lucent.app.data

import android.content.Context
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * P0-4: the desktop DataKeys contract — an unreadable key file must throw instead of minting a
 * fresh key over it (minting would silently orphan every encrypted byte), the zero-length file
 * (the power-loss case the fsync exists for) is treated as unreadable, and resetCacheForTesting
 * genuinely re-reads the file rather than serving a stale cached key.
 */
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
        try {
            block()
        } finally {
            LocalSecrets.filesDirOverride = null
            LocalSecrets.resetForTesting()
        }
    }

    private fun keyFile(dir: File, name: String): File = File(dir, "keys/$name")

    @Test
    fun unreadableKeyFileThrowsInsteadOfReminting() {
        val dir = freshDir()
        use(dir) {
            val ctx = TestContext(dir)
            DataKeys.databasePassphrase(ctx) // mint
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

            // The file was NOT replaced with a fresh mint — the evidence stays on disk.
            assertEquals("garbage-not-a-sealed-key", keyFile.readText())
        }
    }

    @Test
    fun zeroLengthKeyFileIsTreatedAsUnreadable() {
        val dir = freshDir()
        use(dir) {
            val ctx = TestContext(dir)
            DataKeys.databasePassphrase(ctx) // mint
            val keyFile = keyFile(dir, "database.key")

            // The power-loss case: present but empty.
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

            // Replace the sealed key file with a DIFFERENT key's sealed form, then ask again.
            val other = ByteArray(32) { 9 }
            keyFile(dir, "database.key").writeText(
                LocalSecrets.encrypt(Base64.getEncoder().encodeToString(other))
            )
            DataKeys.resetCacheForTesting()

            val second = DataKeys.databasePassphrase(ctx)
            assertNotEquals(first, second, "resetCacheForTesting must re-read the file")
            // And the new passphrase really is the replacement key (x'0909…').
            val expected = "x'" + other.joinToString("") { "%02x".format(it) } + "'"
            assertEquals(expected, second)
        }
    }
}
