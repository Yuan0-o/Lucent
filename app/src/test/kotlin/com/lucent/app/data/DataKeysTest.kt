package com.lucent.app.data

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.nio.file.Files
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataKeysTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("datakeys-test").toFile()
        DataKeys.resetCacheForTesting()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
        DataKeys.resetCacheForTesting()
    }


    @Test
    fun attachmentKeyThrowsWhenTheKeystoreCopyCannotBeUnwrappedAndThereIsNoRecoveryCopy() {
        val keysDir = File(tempDir, "keys").apply { mkdirs() }
        val keyFile = File(keysDir, "attachments.key")
        keyFile.writeText("k1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        val before = keyFile.readText()
        val context = FakeContext { tempDir }

        assertFailsWith<IllegalStateException> { DataKeys.attachmentKey(context) }

        assertEquals(before, keyFile.readText())
        assertFalse(File(keysDir, "attachments.key.recovery").exists())
    }

    @Test
    fun databasePassphraseThrowsOnGarbledPrimaryContentRatherThanRegenerating() {
        val keysDir = File(tempDir, "keys").apply { mkdirs() }
        val keyFile = File(keysDir, "database.key")
        val garbage = "this-is-not-a-key-just-corrupted-leftover-bytes"
        keyFile.writeText(garbage)
        val context = FakeContext { tempDir }

        assertFailsWith<IllegalStateException> { DataKeys.databasePassphrase(context) }

        assertEquals(garbage, keyFile.readText())
    }

    @Test
    fun hasDatabaseKeyReflectsFileExistenceOnly() {
        val context = FakeContext { tempDir }
        assertFalse(DataKeys.hasDatabaseKey(context))

        File(tempDir, "keys").apply { mkdirs() }
        File(tempDir, "keys/database.key").writeText("anything")
        assertTrue(DataKeys.hasDatabaseKey(context))
    }


    @Test
    fun decodeKeyOnEmptyStringReturnsNullBeforeTouchingThePlatformCodec() {
        assertNull(callDecodeKey(""))
    }

    @Test
    fun decodeKeyNeverThrowsRegardlessOfWhyTheInputCannotBecomeAKey() {
        assertNull(callDecodeKey("not valid base64 at all !!"))
        assertNull(callDecodeKey("YWJjZA=="))
    }


    @Test
    fun cachedAttachmentKeyShortCircuitsUntilReset() {
        val sentinel = SecretKeySpec(ByteArray(32), "AES")
        setCachedAttachmentKey(sentinel)
        val explodingContext = FakeContext {
            throw AssertionError("filesDir touched while the attachmentKey cache was warm")
        }

        assertSame(sentinel, DataKeys.attachmentKey(explodingContext))

        DataKeys.resetCacheForTesting()
        assertFailsWith<AssertionError> { DataKeys.attachmentKey(explodingContext) }
    }

    @Test
    fun cachedDatabasePassphraseShortCircuitsUntilReset() {
        val sentinel = "x'" + "ab".repeat(32) + "'"
        setCachedDatabaseKeyHex(sentinel)
        val explodingContext = FakeContext {
            throw AssertionError("filesDir touched while the databaseKeyHex cache was warm")
        }

        assertEquals(sentinel, DataKeys.databasePassphrase(explodingContext))

        DataKeys.resetCacheForTesting()
        assertFailsWith<AssertionError> { DataKeys.databasePassphrase(explodingContext) }
    }
}

private class FakeContext(private val filesDir: () -> File) : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = filesDir()
}

private fun callDecodeKey(base64: String): ByteArray? {
    val method = DataKeys::class.java.getDeclaredMethod("decodeKey", String::class.java)
    method.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return method.invoke(DataKeys, base64) as ByteArray?
}

private fun setCachedAttachmentKey(key: SecretKey) {
    val field = DataKeys::class.java.getDeclaredField("attachmentKey")
    field.isAccessible = true
    field.set(DataKeys, key)
}

private fun setCachedDatabaseKeyHex(value: String) {
    val field = DataKeys::class.java.getDeclaredField("databaseKeyHex")
    field.isAccessible = true
    field.set(DataKeys, value)
}
