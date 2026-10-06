package com.lucent.app.harness

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

actual object ZipReader {
    actual fun readEntries(bytes: ByteArray, wanted: (String) -> Boolean): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && wanted(entry.name)) {
                        val baos = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var read = zis.read(buffer)
                        while (read != -1) {
                            baos.write(buffer, 0, read)
                            read = zis.read(buffer)
                        }
                        out[entry.name] = baos.toByteArray()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
        }
        return out
    }
}
