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

/**
 * P0-3: DataKeys on a plain JVM -- the third piece the plan called for, alongside
 * [RecoverableSecretTest] (envelope round-trip) and [LocalSecretsPrefixTest] (prefix handling).
 *
 * DataKeys' two real crypto backends -- the AndroidKeyStore wrapping via [LocalSecrets] and the
 * escrow wrapping via [RecoverableSecret] -- both bottom out in `android.util.Base64` and/or a real
 * `AndroidKeyStore` security provider. Neither exists on a plain JVM without Robolectric, and there
 * is no such dependency in this module (see the lone `testImplementation` line in
 * app/build.gradle.kts). So, like its two siblings in this package, this suite does not attempt
 * "does a key actually round-trip end to end" -- that needs a real device and is exactly what task 2
 * of this handoff (the androidTest suite) covers instead.
 *
 * What IS pure logic, reachable, and worth pinning on this JVM:
 *
 *  - [DataKeys.getOrCreate] (private; reached through [DataKeys.attachmentKey] /
 *    [DataKeys.databasePassphrase]) must throw rather than silently mint a replacement key when an
 *    existing key file can't be read back -- silently regenerating would orphan every byte already
 *    encrypted under the lost key. This is provable here, faithfully rather than by accident: "the
 *    Keystore can't unwrap a `k1:` value" is not a corner case on this JVM, it is the ONLY thing
 *    that ever happens (there is no real AndroidKeyStore here either), so a `k1:`-prefixed primary
 *    file deterministically reproduces the exact "Keystore corruption" scenario DataKeys.kt's class
 *    doc describes, with no workaround needed to get there.
 *  - [DataKeys.decodeKey] (private, reached via reflection since it takes no Context at all and is
 *    the one DataKeys internal that doesn't need one) never throws, no matter why the input is
 *    unusable.
 *  - [DataKeys.resetCacheForTesting] genuinely clears the in-memory cache. Proven with a [Context]
 *    double whose `filesDir` explodes if it's ever touched: before reset, the cached value must come
 *    back with the double left untouched; after reset, the double must be the thing that's reached.
 *
 * There is no Robolectric or Mockito in this module, so [FakeContext] below -- a minimal
 * `ContextWrapper` subclass overriding only the two members DataKeys ever calls -- is the only way
 * to hand these functions a [Context] on a plain JVM at all.
 */
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

    // ---- getOrCreate (via the public wrappers): never silently regenerates over a bad key -------

    @Test
    fun attachmentKeyThrowsWhenTheKeystoreCopyCannotBeUnwrappedAndThereIsNoRecoveryCopy() {
        // A `k1:`-prefixed primary file with no matching ".recovery" file is precisely "the
        // Keystore-wrapped copy is present, the Keystore itself can't open it, and the escrow copy
        // doesn't exist either" -- see getOrCreate()'s doc comment, branch 3. Never destructive: the
        // file must be left exactly as it was, and nothing gets silently minted in its place.
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
        // No k1:/p1: prefix at all: LocalSecrets.decrypt treats this as legacy plaintext and hands
        // it straight back untouched (see LocalSecretsPrefixTest), so decodeKey is what actually
        // rejects it -- it is neither valid Base64 nor, even if it were, 32 bytes long once decoded.
        // Same contract as the test above, the other realistic flavour of "corrupted key file".
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
        // The one DataKeys entry point that touches disk without touching any crypto at all, so it
        // is fully exercisable here rather than just partially.
        val context = FakeContext { tempDir }
        assertFalse(DataKeys.hasDatabaseKey(context))

        File(tempDir, "keys").apply { mkdirs() }
        File(tempDir, "keys/database.key").writeText("anything")
        assertTrue(DataKeys.hasDatabaseKey(context))
    }

    // ---- decodeKey: never throws, whatever the reason --------------------------------------------

    @Test
    fun decodeKeyOnEmptyStringReturnsNullBeforeTouchingThePlatformCodec() {
        // The one decodeKey input whose null result is verified for the stated reason even on this
        // JVM: the empty-string branch returns before android.util.Base64 is ever called.
        assertNull(callDecodeKey(""))
    }

    @Test
    fun decodeKeyNeverThrowsRegardlessOfWhyTheInputCannotBecomeAKey() {
        // android.util.Base64 itself is not reachable from a plain JVM unit test (no Robolectric
        // here), so this cannot verify Base64's own validation the way it would run on a device --
        // that needs the androidTest suite. What this DOES verify faithfully, because the code path
        // is identical either way, is decodeKey's catch-all contract: whatever breaks while turning
        // the string into 32 key bytes -- malformed Base64, a decoded length that isn't 32, or, in
        // this sandbox, the codec being unavailable at all -- collapses to null, never an exception.
        // That fail-safe contract is the actual property getOrCreate depends on.
        assertNull(callDecodeKey("not valid base64 at all !!"))
        // Syntactically valid Base64 a real device would decode without error -- just to 4 bytes,
        // not the required 32 -- so on a device this specific input exercises the length check
        // rather than Base64's own validation.
        assertNull(callDecodeKey("YWJjZA=="))
    }

    // ---- resetCacheForTesting: actually forces a fresh read, not just a relabelled cached one -----

    @Test
    fun cachedAttachmentKeyShortCircuitsUntilReset() {
        val sentinel = SecretKeySpec(ByteArray(32), "AES")
        setCachedAttachmentKey(sentinel)
        val explodingContext = FakeContext {
            throw AssertionError("filesDir touched while the attachmentKey cache was warm")
        }

        // Cache warm: the sentinel comes back and the context is never touched.
        assertSame(sentinel, DataKeys.attachmentKey(explodingContext))

        // Cache cleared: the very same context now visibly explodes -- the only way to tell "still
        // serving the cache" and "genuinely went back to disk" apart from outside the class.
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

/**
 * The smallest possible [Context] double for these tests. DataKeys only ever calls
 * `context.applicationContext.filesDir`, so that is the entire surface implemented here; every
 * other Context member is inherited, untouched, from ContextWrapper and is never invoked by the
 * code under test. [filesDir] is a lambda rather than a fixed value so the same class can either
 * point at a real temp directory (the file-based tests above) or refuse to be touched at all (the
 * cache tests, where reaching disk at all is exactly the bug being tested for).
 */
private class FakeContext(private val filesDir: () -> File) : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = filesDir()
}

/**
 * Reach the private `decodeKey(String): ByteArray?` via reflection. It takes no Context at all, so
 * of DataKeys' private internals it's the one that can be pinned directly instead of only through
 * the public API.
 */
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
