package com.lucent.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Frame-protocol tests for [BackupFrames] (P0-7 first cut): the `.lcb` payload framing must round
 * trip manifest + blob frames through the same bounded big-endian primitives export and import
 * use, a legacy plain-JSON payload must scan unchanged, hostile length fields must terminate the
 * walk (or be rejected) instead of provoking huge allocations, and a truncated tail must not take
 * down a restore whose manifest already parsed.
 */
class BackupFramesTest {

    private val manifest = """{"modules":["notes"],"version":13}""".toByteArray(Charsets.UTF_8)

    /** Build a framed payload the way exportEncrypted does: magic, version, json, then blobs. */
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

    // ---- Legacy plain-JSON payload ----

    @Test
    fun legacyJsonPayloadScansUnchanged() {
        val json = """{"notes":[]}""".toByteArray(Charsets.UTF_8)
        val scan = scan(json)
        assertFalse(scan.framed)
        assertEquals(String(json, Charsets.UTF_8), scan.manifestJson)
        assertEquals(0, scan.modelCount)
        assertEquals(0, scan.fontCount)
    }

    // ---- Framed round trip ----

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
            // consume exactly the advertised bytes (as restoreOneBlob does)
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

    // ---- Hostile inputs ----

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
        // A name length beyond the bound must stop the walk, not allocate.
        BackupFrames.writeInt(out, BackupFrames.MAX_BLOB_NAME_BYTES + 1)
        val scan = scan(out.toByteArray())
        assertTrue(scan.framed)
        assertEquals(String(manifest, Charsets.UTF_8), scan.manifestJson)
        assertEquals(0, scan.modelCount)
    }

    @Test
    fun truncatedTailEndsWalkButKeepsCountedBlobs() {
        val full = framedPayload(listOf("model_1.gguf" to ByteArray(4096) { 9 }))
        // Cut the payload inside the first blob's bytes: the manifest and the frame header parsed,
        // the blob body is truncated. The walk must end quietly with what it counted.
        val truncated = full.copyOfRange(0, full.size - 2000)
        val scan = scan(truncated)
        assertTrue(scan.framed)
        assertEquals(String(manifest, Charsets.UTF_8), scan.manifestJson)
        assertTrue(scan.modelBytes in 1..4096) // partially-consumed blob may or may not complete
    }

    @Test
    fun emptyPayloadProducesEmptyScan() {
        val scan = scan(ByteArray(0))
        assertEquals("", scan.manifestJson)
        assertFalse(scan.framed)
    }

    // ---- Primitives ----

    @Test
    fun bigEndianPrimitivesRoundTrip() {
        val out = ByteArrayOutputStream()
        BackupFrames.writeInt(out, 0x6A3B4C5D)
        BackupFrames.writeLong(out, 0x0123456789ABCDEFL)
        val bytes = out.toByteArray()
        val input = ByteArrayInputStream(bytes)
        assertEquals(0x6A3B4C5D, BackupFrames.readIntOrEnd(input))
        assertEquals(0x0123456789ABCDEFL, BackupFrames.readLongFrom(input))
        assertTrue(BackupFrames.readIntOrEnd(input) == null) // clean end before any byte
        assertFailsWith<java.io.EOFException> { BackupFrames.readLongFrom(input) }
    }
}
