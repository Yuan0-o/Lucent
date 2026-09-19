package com.lucent.app.data

import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalSecretsMigrationTest {

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
        val dir = freshDir()
        writeLegacyBareKey(dir, randomKey())
        val wrapper = FakeWrapper(available = true)

        use(dir, wrapper) {
            val sealed = LocalSecrets.encrypt("api-key-123")
            assertEquals("api-key-123", LocalSecrets.decrypt(sealed))

            val stored = masterKeyFile(dir).readText().trim()
            assertTrue(stored.startsWith("d1:"), "expected d1: re-wrap, got: ${stored.take(12)}…")
        }
    }

    @Test
    fun legacyBareKeyStillOpensWhenWrapperUnavailableAndStaysLegacy() {
        val dir = freshDir()
        writeLegacyBareKey(dir, randomKey())

        use(dir, FakeWrapper(available = false)) {
            val sealed = LocalSecrets.encrypt("key-on-non-windows")
            assertEquals("key-on-non-windows", LocalSecrets.decrypt(sealed))

            assertTrue(!masterKeyFile(dir).readText().trim().startsWith("d1:"))
            assertTrue(sealed.startsWith("v1:"), "value must still be v1: ciphertext, got: ${sealed.take(8)}")

            assertEquals(EncryptionStatus.State.ENCRYPTED, EncryptionStatus.secrets)
            assertTrue(
                EncryptionStatus.secretsReason?.contains("DPAPI") == true,
                "reason should mention DPAPI, got: ${EncryptionStatus.secretsReason}"
            )
        }
    }

    @Test
    fun d1StoredFormRoundTripsAcrossReloads() {
        val dir = freshDir()
        val wrapper = FakeWrapper(available = true)

        use(dir, wrapper) {
            val sealed = LocalSecrets.encrypt("persistent-secret")
            assertTrue(masterKeyFile(dir).readText().trim().startsWith("d1:"))
            assertEquals("persistent-secret", LocalSecrets.decrypt(sealed))

            LocalSecrets.resetForTesting()
            assertEquals("persistent-secret", LocalSecrets.decrypt(sealed))
            assertEquals("v1:", sealed.take(3))
        }
    }

    @Test
    fun corruptedOrForeignWrapperDegradesToPortableAndReports() {
        val dir = freshDir()
        val wrapper = FakeWrapper(available = true)

        var sealedV1 = ""
        use(dir, wrapper) {
            sealedV1 = LocalSecrets.encrypt("stored-while-wrapped")
            assertTrue(sealedV1.startsWith("v1:"))
            assertTrue(masterKeyFile(dir).readText().trim().startsWith("d1:"))
        }

        use(dir, FakeWrapper(available = false)) {
            val degraded = LocalSecrets.encrypt("brand-new-value")
            assertTrue(degraded.startsWith("p1:"), "expected p1: degrade, got: ${degraded.take(8)}")
            assertEquals("brand-new-value", LocalSecrets.decrypt(degraded))
            assertEquals(EncryptionStatus.State.PLAINTEXT, EncryptionStatus.secrets)
            assertTrue(!EncryptionStatus.secretsReason.isNullOrBlank())

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
