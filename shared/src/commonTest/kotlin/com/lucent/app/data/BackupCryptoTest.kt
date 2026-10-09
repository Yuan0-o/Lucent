package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class BackupCryptoTest {

    private val payload = "notes=hello&tasks=buy milk&chats={\"role\":\"user\"}".toByteArray(Charsets.UTF_8)


    @Test
    fun appKeyRoundTripPreservesPayload() {
        val blob = BackupCrypto.encrypt(payload, password = null)
        assertTrue(BackupCrypto.looksEncrypted(blob))
        val header = BackupCrypto.readHeader(blob)
        assertNotNull(header)
        assertEquals(BackupCrypto.Mode.APP_KEY, header.mode)
        assertFalse(header.needsPassword)
        assertContentEquals(payload, BackupCrypto.decrypt(blob, password = null))
    }


    @Test
    fun passwordRoundTripPreservesPayload() {
        val blob = BackupCrypto.encrypt(payload, password = "hunter2-secret")
        val header = BackupCrypto.readHeader(blob)
        assertNotNull(header)
        assertEquals(BackupCrypto.Mode.PASSWORD, header.mode)
        assertTrue(header.needsPassword)
        assertContentEquals(payload, BackupCrypto.decrypt(blob, password = "hunter2-secret"))
    }

    @Test
    fun wrongPasswordThrowsWrongPasswordException() {
        val blob = BackupCrypto.encrypt(payload, password = "right-password")
        assertFailsWith<BackupCrypto.WrongPasswordException> {
            BackupCrypto.decrypt(blob, password = "wrong-password")
        }
    }

    @Test
    fun missingPasswordThrowsWrongPasswordException() {
        val blob = BackupCrypto.encrypt(payload, password = "right-password")
        assertFailsWith<BackupCrypto.WrongPasswordException> {
            BackupCrypto.decrypt(blob, password = null)
        }
    }

    @Test
    fun passwordModeIsChosenForAnyNonNullNonEmptyPassword() {
        val spaced = BackupCrypto.encrypt(payload, password = "   ")
        assertEquals(BackupCrypto.Mode.PASSWORD, BackupCrypto.readHeader(spaced)!!.mode)

        val empty = BackupCrypto.encrypt(payload, password = "")
        assertEquals(BackupCrypto.Mode.APP_KEY, BackupCrypto.readHeader(empty)!!.mode)

        val none = BackupCrypto.encrypt(payload, password = null)
        assertEquals(BackupCrypto.Mode.APP_KEY, BackupCrypto.readHeader(none)!!.mode)
    }


    @Test
    fun refusesForeignBytes() {
        val foreign = "PK\u0003\u0004this-is-a-zip-or-json".toByteArray(Charsets.UTF_8)
        assertFalse(BackupCrypto.looksEncrypted(foreign))
        assertNull(BackupCrypto.readHeader(foreign))
        assertFailsWith<java.io.IOException> {
            BackupCrypto.decrypt(foreign, password = null)
        }
    }

    @Test
    fun truncatedBackupIsRejected() {
        val blob = BackupCrypto.encrypt(payload, password = null)
        val truncated = blob.copyOfRange(0, blob.size - 10)
        assertFailsWith<java.io.IOException> {
            BackupCrypto.decrypt(truncated, password = null)
        }
    }

    @Test
    fun magicPrefixAloneIsNotEnough() {
        val garbage = ("LCNTBAK1" + "X".repeat(30)).toByteArray(Charsets.UTF_8)
        assertTrue(BackupCrypto.looksEncrypted(garbage))
        assertNull(BackupCrypto.readHeader(garbage))
    }


    @Test
    fun passwordBackupCanCarryRecoveryEnvelope() {
        val envelope = BackupRecovery.Envelope(
            question = "Your first pet?",
            salt = ByteArray(16) { it.toByte() },
            iterations = 1000,
            iv = ByteArray(12) { (it + 1).toByte() },
            wrapped = ByteArray(32) { (it * 2).toByte() }
        )
        val blob = BackupCrypto.encrypt(payload, password = "pw", recovery = envelope)
        val header = BackupCrypto.readHeader(blob)
        assertNotNull(header)
        assertTrue(header.needsPassword)
        assertTrue(header.hasRecovery)
        assertEquals("Your first pet?", header.recovery?.question)
        assertContentEquals(envelope.salt, header.recovery?.salt)
        assertContentEquals(envelope.iv, header.recovery?.iv)
        assertContentEquals(envelope.wrapped, header.recovery?.wrapped)
        assertContentEquals(payload, BackupCrypto.decrypt(blob, password = "pw"))
    }

    @Test
    fun appKeyBackupIgnoresRecoveryEnvelope() {
        val envelope = BackupRecovery.Envelope(
            question = "Q", salt = ByteArray(16), iterations = 1000,
            iv = ByteArray(12), wrapped = ByteArray(32)
        )
        val blob = BackupCrypto.encrypt(payload, password = null, recovery = envelope)
        val header = BackupCrypto.readHeader(blob)
        assertNotNull(header)
        assertNull(header.recovery)
        assertFalse(header.hasRecovery)
        assertContentEquals(payload, BackupCrypto.decrypt(blob, password = null))
    }


    @Test
    fun headerParsingIsStableAcrossSaltLengths() {
        val blob = BackupCrypto.encrypt(payload, password = null)
        val header = BackupCrypto.readHeader(blob)
        assertNotNull(header)
        assertEquals(16, header.salt.size)
        assertTrue(header.iterations > 0)
    }

    @Test
    fun multiPasswordRoundTripEachPasswordDecrypts() {
        val passwords = listOf("first-pass", "second-pass", "third-pass")
        val blob = BackupCrypto.encryptMultiPassword(payload, passwords, recovery = null)
        assertTrue(BackupCrypto.looksEncrypted(blob))
        val header = BackupCrypto.readHeader(blob)
        assertNotNull(header)
        assertEquals(BackupCrypto.VERSION_MULTI_PASSWORD, header.version)
        assertEquals(BackupCrypto.Mode.PASSWORD, header.mode)
        assertTrue(header.needsPassword)
        assertFalse(header.hasRecovery)
        assertEquals(3, header.slots.size)

        for (pw in passwords) {
            assertContentEquals(payload, BackupCrypto.decrypt(blob, password = pw))
        }
    }

    @Test
    fun multiPasswordWrongPasswordThrowsWrongPasswordException() {
        val passwords = listOf("pass-one", "pass-two")
        val blob = BackupCrypto.encryptMultiPassword(payload, passwords, recovery = null)
        assertFailsWith<BackupCrypto.WrongPasswordException> {
            BackupCrypto.decrypt(blob, password = "wrong-password")
        }
        assertFailsWith<BackupCrypto.WrongPasswordException> {
            BackupCrypto.decrypt(blob, password = null)
        }
        assertFailsWith<BackupCrypto.WrongPasswordException> {
            BackupCrypto.decrypt(blob, password = "")
        }
    }

    @Test
    fun multiPasswordRecoveryEnvelopeRoundTripViaAnswer() {
        val dek = secureRandomBytes(32)
        val envelope = BackupRecovery.createForKey("Your favorite constellation?", "Orion", dek)
        assertNotNull(envelope)

        val passwords = listOf("p1", "p2", "p3")
        val blob = BackupCrypto.encryptMultiPassword(payload, passwords, recovery = envelope, dek = dek)
        val header = BackupCrypto.readHeader(blob)
        assertNotNull(header)
        assertTrue(header.needsPassword)
        assertTrue(header.hasRecovery)
        assertEquals("Your favorite constellation?", header.recovery?.question)

        for (pw in passwords) {
            assertContentEquals(payload, BackupCrypto.decrypt(blob, password = pw))
        }

        val decryptedWithAnswer = BackupCrypto.decryptWithAnswer(blob, "Orion")
        assertNotNull(decryptedWithAnswer)
        assertContentEquals(payload, decryptedWithAnswer)

        val decryptedCaseInsensitive = BackupCrypto.decryptWithAnswer(blob, "  orion  ")
        assertNotNull(decryptedCaseInsensitive)
        assertContentEquals(payload, decryptedCaseInsensitive)

        assertNull(BackupCrypto.decryptWithAnswer(blob, "Ursa Major"))
        assertNull(BackupCrypto.decryptWithAnswer(blob, ""))
    }
}
