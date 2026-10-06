package com.lucent.app.harness

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

actual class ZipWriter actual constructor() {
    private val baos = ByteArrayOutputStream()
    private val zos = ZipOutputStream(baos)

    actual fun addEntry(name: String, data: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(data)
        zos.closeEntry()
    }

    actual fun close(): ByteArray {
        zos.close()
        return baos.toByteArray()
    }
}
