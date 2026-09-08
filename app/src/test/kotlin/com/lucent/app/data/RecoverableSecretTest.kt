package com.lucent.app.data

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * P0-3: the RecoverableSecret envelope round-trip, exercised on a plain JVM.
 *
 * [RecoverableSecret.seal]/[open]/[material] are deliberately Android-free (they take the key
 * material as a parameter), which is exactly why the most security-critical recovery path — the
 * Keystore-independent copy of the data keys — can be tested without a device.
 */
class RecoverableSecretTest {

    private val materialA = RecoverableSecret.material("device-android-id-aaa")
    private val materialB = RecoverableSecret.material("device-android-id-bbb")

    @Test
    fun sealAndOpenRoundTrip() {
        val plain = "the-raw-key-base64".toByteArray(Charsets.UTF_8)
        val blob = RecoverableSecret.seal(materialA, plain)
        assertNotNull(blob)
        // Layout is salt(16) | iv(12) | ciphertext+tag — the salt/iv are stored beside it.
        assert(blob.size > 16 + 12)
        assertEquals(plain.toList(), RecoverableSecret.open(materialA, blob)!!.toList())
    }

    @Test
    fun wrongDeviceMaterialCannotOpen() {
        val blob = RecoverableSecret.seal(materialA, "key-from-device-a".toByteArray())
        assertNotNull(blob)
        // Device B must NOT be able to recover A's key — this is what makes the blob device-bound.
        assertNull(RecoverableSecret.open(materialB, blob!!))
    }

    @Test
    fun corruptOrTruncatedBlobReturnsNull() {
        val blob = RecoverableSecret.seal(materialA, "some-key".toByteArray())!!
        // Truncated below salt+iv.
        assertNull(RecoverableSecret.open(materialA, blob.copyOf(20)))
        // Bit-flipped ciphertext: GCM tag check fails.
        val flipped = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() }
        assertNull(RecoverableSecret.open(materialA, flipped))
    }

    @Test
    fun freshSaltPerWriteMeansNoCollidingBlobs() {
        // Two wrappings of the SAME key must never produce the same bytes (fresh salt+IV each time),
        // so GCM nonces are never reused under a derived key.
        val a = RecoverableSecret.seal(materialA, "same-key".toByteArray())!!
        val b = RecoverableSecret.seal(materialA, "same-key".toByteArray())!!
        assertNotEquals(Base64.getEncoder().encodeToString(a), Base64.getEncoder().encodeToString(b))
        // …yet both open.
        assertEquals("same-key", String(RecoverableSecret.open(materialA, a)!!, Charsets.UTF_8))
        assertEquals("same-key", String(RecoverableSecret.open(materialA, b)!!, Charsets.UTF_8))
    }

    @Test
    fun emptyInputRoundTripsThroughBase64Form() {
        // The Base64-facing wrappers (encrypt/decrypt) are Android-bound, so the raw envelope is
        // what is exercised here; a zero-length key string must seal and open cleanly.
        val blob = RecoverableSecret.seal(materialA, ByteArray(0))
        assertNotNull(blob)
        assertEquals(0, RecoverableSecret.open(materialA, blob!!)!!.size)
    }
}
