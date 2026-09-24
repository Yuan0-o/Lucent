package com.lucent.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupFramesTest {

    private val manifest = """{"modules":["notes"],"version":13}""".toByteArray(Charsets.UTF_8)

    private fun framedPayload(blobs: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(BackupFrames.FRAME_MAGIC, BackupFrames.FRAME_VERSION))
        BackupFrames.writeInt(out, manifest.size)
        out.write(manifest)
        for ((name, bytes) in blobs) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            BackupFrames.writeInt(out, nameBytes.size)
            out.write(nameBytes)
            BackupFrames.writeLong(out, bytes.size.toLong())
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun scan(bytes: ByteArray): BackupFrames.PayloadScan =
        BackupFrames.scanPayload(ByteArrayInputStream(bytes))


    @Test
    fun legacyJsonPayloadScansUnchanged() {
        val json = """{"notes":[]}""".toByteArray(Charsets.UTF_8)
        val scan = scan(json)
        assertFalse(scan.framed)
        assertEquals(String(json, Charsets.UTF_8), scan.manifestJson)
        assertEquals(0, scan.modelCount)
        assertEquals(0, scan.fontCount)
    }


    @Test
    fun framedPayloadCountsModelBlobs() {
        val payload = framedPayload(listOf("model_1.gguf" to ByteArray(1024) { 7 }))
        val scan = scan(payload)
        assertTrue(scan.framed)
        assertEquals(String(manifest, Charsets.UTF_8), scan.manifestJson)
        assertEquals(1, scan.modelCount)
        assertEquals(1024L, scan.modelBytes)
        assertEquals(0, scan.fontCount)
    }

    @Test
    fun framedPayloadSeparatesFontBlobs() {
        val payload = framedPayload(
            listOf(
                "font:NotoSans.otf" to ByteArray(2048) { 1 },
                "model_2.gguf" to ByteArray(64) { 2 }
            )
        )
        val scan = scan(payload)
        assertTrue(scan.framed)
        assertEquals(1, scan.fontCount)
        assertEquals(2048L, scan.fontBytes)
        assertEquals(1, scan.modelCount)
        assertEquals(64L, scan.modelBytes)
    }

    @Test
    fun onBlobReceivesEveryFrameInOrder() {
        val payload = framedPayload(
            listOf("model_1.gguf" to byteArrayOf(1, 2, 3), "model_2.gguf" to byteArrayOf(4, 5))
        )
        val seen = mutableListOf<Pair<String, Int>>()
        val scan = BackupFrames.scanPayload(ByteArrayInputStream(payload)) { name, len, stream ->
            val buf = ByteArray(len.toInt())
            var off = 0
            while (off < buf.size) {
                val n = stream.read(buf, off, buf.size - off)
                if (n < 0) throw java.io.EOFException("ended early")
                off += n
            }
            seen.add(name to buf.size)
        }
        assertEquals(2, seen.size)
        assertEquals("model_1.gguf" to 3, seen[0])
        assertEquals("model_2.gguf" to 2, seen[1])
        assertEquals(2, scan.modelCount)
    }


    @Test
    fun oversizedManifestLengthIsRejected() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(BackupFrames.FRAME_MAGIC, BackupFrames.FRAME_VERSION))
        BackupFrames.writeInt(out, BackupFrames.MAX_MANIFEST_BYTES + 1)
        assertFailsWith<IllegalArgumentException> {
            scan(out.toByteArray())
        }
    }

    @Test
    fun hostileBlobNameLengthEndsWalkQuietly() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(BackupFrames.FRAME_MAGIC, BackupFrames.FRAME_VERSION))
        BackupFrames.writeInt(out, manifest.size)
        out.write(manifest)
        BackupFrames.writeInt(out, BackupFrames.MAX_BLOB_NAME_BYTES + 1)
        val scan = scan(out.toByteArray())
        assertTrue(scan.framed)
        assertEquals(String(manifest, Charsets.UTF_8), scan.manifestJson)
        assertEquals(0, scan.modelCount)
    }

    @Test
    fun truncatedTailEndsWalkButKeepsCountedBlobs() {
        val full = framedPayload(listOf("model_1.gguf" to ByteArray(4096) { 9 }))
        val truncated = full.copyOfRange(0, full.size - 2000)
        val scan = scan(truncated)
        assertTrue(scan.framed)
        assertEquals(String(manifest, Charsets.UTF_8), scan.manifestJson)
        assertTrue(scan.modelBytes in 1..4096)
    }

    @Test
    fun emptyPayloadProducesEmptyScan() {
        val scan = scan(ByteArray(0))
        assertEquals("", scan.manifestJson)
        assertFalse(scan.framed)
    }


    @Test
    fun bigEndianPrimitivesRoundTrip() {
        val out = ByteArrayOutputStream()
        BackupFrames.writeInt(out, 0x6A3B4C5D)
        BackupFrames.writeLong(out, 0x0123456789ABCDEFL)
        val bytes = out.toByteArray()
        val input = ByteArrayInputStream(bytes)
        assertEquals(0x6A3B4C5D, BackupFrames.readIntOrEnd(input))
        assertEquals(0x0123456789ABCDEFL, BackupFrames.readLongFrom(input))
        assertTrue(BackupFrames.readIntOrEnd(input) == null)
        assertFailsWith<java.io.EOFException> { BackupFrames.readLongFrom(input) }
    }
}
