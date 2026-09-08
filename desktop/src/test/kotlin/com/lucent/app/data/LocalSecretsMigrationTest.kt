package com.lucent.app.data

import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P0-1 / P0-4 tests for the desktop master-key store: the DPAPI binding of `master.key`.
 *
 * Real DPAPI only exists on Windows, and this suite runs on whatever OS CI gave us (Linux on the
 * check workflow), so the tests drive [LocalSecrets.MasterKeyWrapper] through a fake that either
 * "wraps" (simulating Windows DPAPI) or is unavailable (simulating macOS/Linux). The stored-form
 * discipline — `d1:` prefix for a wrapped key, bare Base64 for legacy — is what the production code
 * and these tests share, and it is exactly what must not regress: a legacy install must migrate in
 * place, a `d1:` store must round-trip, and a key that cannot be unwrapped must degrade to `p1:`
 * and report, never return plaintext.
 */
class LocalSecretsMigrationTest {

    /**
     * A fake wrapper whose "DPAPI" is a byte offset: wrap = +7, unwrap = -7. Round-trips
     * deterministically, and `available=false` models a non-Windows machine (or a user change
     * that leaves a `d1:` store unopenable).
     */
    private class FakeWrapper(var available: Boolean = true) : LocalSecrets.MasterKeyWrapper {
        override fun wrap(bytes: ByteArray): ByteArray? =
            if (available) ByteArray(bytes.size) { (bytes[it] + 7).toByte() } else null

        override fun unwrap(bytes: ByteArray): ByteArray? =
            if (available) ByteArray(bytes.size) { (bytes[it] - 7).toByte() } else null
    }

    private fun freshDir(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-secrets-test-${System.nanoTime()}")
            .apply { mkdirs() }

    private fun masterKeyFile(dir: File): File = File(dir, "keys/master.key")

    private fun use(dir: File, wrapper: LocalSecrets.MasterKeyWrapper, block: () -> Unit) {
        LocalSecrets.filesDirOverride = dir
        LocalSecrets.wrapperOverride = wrapper
        LocalSecrets.resetForTesting()
        try {
            block()
        } finally {
            LocalSecrets.filesDirOverride = null
            LocalSecrets.wrapperOverride = null
            LocalSecrets.resetForTesting()
        }
    }

    private fun writeLegacyBareKey(dir: File, key: ByteArray) {
        val file = masterKeyFile(dir)
        file.parentFile.mkdirs()
        file.writeText(Base64.getEncoder().encodeToString(key))
    }

    private fun randomKey(): ByteArray = ByteArray(32) { it.toByte() }

    @Test
    fun legacyBareKeyIsRewrappedInPlaceAndStillOpens() {
        // Simulates a pre-P0-1 Windows install: bare Base64 master.key, wrapper now available.
        val dir = freshDir()
        writeLegacyBareKey(dir, randomKey())
        val wrapper = FakeWrapper(available = true)

        use(dir, wrapper) {
            val sealed = LocalSecrets.encrypt("api-key-123")
            assertEquals("api-key-123", LocalSecrets.decrypt(sealed))

            // The legacy file was re-wrapped in place — the plaintext root is gone.
            val stored = masterKeyFile(dir).readText().trim()
            assertTrue(stored.startsWith("d1:"), "expected d1: re-wrap, got: ${stored.take(12)}…")
        }
    }

    @Test
    fun legacyBareKeyStillOpensWhenWrapperUnavailableAndStaysLegacy() {
        // Simulates a macOS/Linux developer machine: no DPAPI, legacy file must keep working
        // untouched, and the missing binding must be reported, not silently ignored.
        val dir = freshDir()
        writeLegacyBareKey(dir, randomKey())

        use(dir, FakeWrapper(available = false)) {
            val sealed = LocalSecrets.encrypt("key-on-non-windows")
            assertEquals("key-on-non-windows", LocalSecrets.decrypt(sealed))

            // Not re-wrapped (no wrapper), but the values are still v1: AES-GCM ciphertext.
            assertTrue(!masterKeyFile(dir).readText().trim().startsWith("d1:"))
            assertTrue(sealed.startsWith("v1:"), "value must still be v1: ciphertext, got: ${sealed.take(8)}")

            // The unbound root key is reported so the Settings banner tells the truth.
            assertEquals(EncryptionStatus.State.ENCRYPTED, EncryptionStatus.secrets)
            assertTrue(
                EncryptionStatus.secretsReason?.contains("DPAPI") == true,
                "reason should mention DPAPI, got: ${EncryptionStatus.secretsReason}"
            )
        }
    }

    @Test
    fun d1StoredFormRoundTripsAcrossReloads() {
        // A d1: store written by a Windows build must open again after a fresh process state —
        // this is the whole point of the prefix discipline.
        val dir = freshDir()
        val wrapper = FakeWrapper(available = true)

        use(dir, wrapper) {
            val sealed = LocalSecrets.encrypt("persistent-secret")
            assertTrue(masterKeyFile(dir).readText().trim().startsWith("d1:"))
            assertEquals("persistent-secret", LocalSecrets.decrypt(sealed))

            // Simulate a relaunch: forget the cached key entirely and re-read from disk.
            LocalSecrets.resetForTesting()
            assertEquals("persistent-secret", LocalSecrets.decrypt(sealed))
            assertEquals("v1:", sealed.take(3))
        }
    }

    @Test
    fun corruptedOrForeignWrapperDegradesToPortableAndReports() {
        // A d1: store that can no longer be unwrapped (wrong user, corrupt bytes) must NOT return
        // plaintext or mint a fresh key over the old one — it degrades to p1: and reports.
        val dir = freshDir()
        val wrapper = FakeWrapper(available = true)

        var sealedV1 = ""
        use(dir, wrapper) {
            // Write a v1: value keyed by a d1:-stored master key, exactly as Windows would.
            sealedV1 = LocalSecrets.encrypt("stored-while-wrapped")
            assertTrue(sealedV1.startsWith("v1:"))
            assertTrue(masterKeyFile(dir).readText().trim().startsWith("d1:"))
        }

        // Now the wrapper becomes unavailable / the user changes: unwrap fails.
        use(dir, FakeWrapper(available = false)) {
            // Sealing a NEW value degrades to the portable scheme — never the caller's plaintext.
            val degraded = LocalSecrets.encrypt("brand-new-value")
            assertTrue(degraded.startsWith("p1:"), "expected p1: degrade, got: ${degraded.take(8)}")
            assertEquals("brand-new-value", LocalSecrets.decrypt(degraded))
            assertEquals(EncryptionStatus.State.PLAINTEXT, EncryptionStatus.secrets)
            assertTrue(!EncryptionStatus.secretsReason.isNullOrBlank())

            // Opening the OLD v1: value (whose master key is now unopenable) fails closed: the
            // caller gets "" and can never mistake degraded ciphertext for the original plaintext.
            assertEquals("", LocalSecrets.decrypt(sealedV1))
        }
    }

    @Test
    fun freshStoreOnWrapperMachineIsWrittenDpapiWrapped() {
        val dir = freshDir()
        use(dir, FakeWrapper(available = true)) {
            LocalSecrets.encrypt("x")
            assertTrue(masterKeyFile(dir).readText().trim().startsWith("d1:"))
            assertEquals(EncryptionStatus.State.ENCRYPTED, EncryptionStatus.secrets)
        }
    }

    @Test
    fun zeroLengthKeyFileIsTreatedAsUnreadableAndDegrades() {
        // The power-loss case the fsync exists for: master.key exists but is empty. Must be
        // treated as unreadable (never minted over, never plaintext).
        val dir = freshDir()
        masterKeyFile(dir).parentFile.mkdirs()
        masterKeyFile(dir).writeText("")

        use(dir, FakeWrapper(available = false)) {
            val sealed = LocalSecrets.encrypt("after-power-cut")
            assertTrue(sealed.startsWith("p1:"))
            assertEquals("after-power-cut", LocalSecrets.decrypt(sealed))
            assertEquals(EncryptionStatus.State.PLAINTEXT, EncryptionStatus.secrets)
        }
    }
}
