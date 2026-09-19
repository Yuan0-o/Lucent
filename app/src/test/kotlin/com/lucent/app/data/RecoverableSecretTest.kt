package com.lucent.app.data

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecoverableSecretTest {

    private val materialA = RecoverableSecret.material("device-android-id-aaa")
    private val materialB = RecoverableSecret.material("device-android-id-bbb")

    @Test
    fun sealAndOpenRoundTrip() {
        val plain = "the-raw-key-base64".toByteArray(Charsets.UTF_8)
        val blob = RecoverableSecret.seal(materialA, plain)
        assertNotNull(blob)
        assert(blob.size > 16 + 12)
        assertEquals(plain.toList(), RecoverableSecret.open(materialA, blob)!!.toList())
    }

    @Test
    fun wrongDeviceMaterialCannotOpen() {
        val blob = RecoverableSecret.seal(materialA, "key-from-device-a".toByteArray())
        assertNotNull(blob)
        assertNull(RecoverableSecret.open(materialB, blob))
    }

    @Test
    fun corruptOrTruncatedBlobReturnsNull() {
        val blob = RecoverableSecret.seal(materialA, "some-key".toByteArray())!!
        assertNull(RecoverableSecret.open(materialA, blob.copyOf(20)))
        val flipped = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() }
        assertNull(RecoverableSecret.open(materialA, flipped))
    }

    @Test
    fun freshSaltPerWriteMeansNoCollidingBlobs() {
        val a = RecoverableSecret.seal(materialA, "same-key".toByteArray())!!
        val b = RecoverableSecret.seal(materialA, "same-key".toByteArray())!!
        assertNotEquals(Base64.getEncoder().encodeToString(a), Base64.getEncoder().encodeToString(b))
        assertEquals("same-key", String(RecoverableSecret.open(materialA, a)!!, Charsets.UTF_8))
        assertEquals("same-key", String(RecoverableSecret.open(materialA, b)!!, Charsets.UTF_8))
    }

    @Test
    fun emptyInputRoundTripsThroughBase64Form() {
        val blob = RecoverableSecret.seal(materialA, ByteArray(0))
        assertNotNull(blob)
        assertEquals(0, RecoverableSecret.open(materialA, blob)!!.size)
    }
}
