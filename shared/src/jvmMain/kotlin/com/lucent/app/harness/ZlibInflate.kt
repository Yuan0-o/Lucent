package com.lucent.app.harness

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

actual fun zlibInflate(compressed: ByteArray): ByteArray {
    var inflater = Inflater(false)
    try {
        inflater.setInput(compressed)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16384)
        while (!inflater.finished()) {
            val produced = inflater.inflate(buffer)
            if (produced == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            }
            out.write(buffer, 0, produced)
        }
        inflater.end()
        val res = out.toByteArray()
        if (res.isNotEmpty()) return res
    } catch (e: Exception) {
        inflater.end()
    }

    inflater = Inflater(true)
    try {
        inflater.setInput(compressed)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16384)
        while (!inflater.finished()) {
            val produced = inflater.inflate(buffer)
            if (produced == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            }
            out.write(buffer, 0, produced)
        }
        return out.toByteArray()
    } catch (e: Exception) {
        return ByteArray(0)
    } finally {
        inflater.end()
    }
}
