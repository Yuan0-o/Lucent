package com.lucent.app.harness

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

actual class ZipWriter actual constructor() {
    private val baos = ByteArrayOutputStream()
    private val zos = ZipOutputStream(baos)

    actual fun addEntry(name: String, data: ByteArray) {
        val entry = ZipEntry(name)
        entry.time = 1704067200000L
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    actual fun addStoredEntry(name: String, data: ByteArray) {
        val entry = ZipEntry(name)
        entry.time = 1704067200000L
        val crc = java.util.zip.CRC32()
        crc.update(data)
        entry.method = ZipEntry.STORED
        entry.size = data.size.toLong()
        entry.compressedSize = data.size.toLong()
        entry.crc = crc.value
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    actual fun close(): ByteArray {
        zos.close()
        return baos.toByteArray()
    }
}
