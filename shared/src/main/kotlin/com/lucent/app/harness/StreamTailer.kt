package com.lucent.app.harness

import java.io.ByteArrayOutputStream

class StreamTailer {
    var offset = 0L
        private set
    private val buffer = ByteArrayOutputStream()

    fun processNewBytes(chunk: ByteArray, onLine: (String) -> Unit) {
        offset += chunk.size
        var start = 0
        for (i in chunk.indices) {
            if (chunk[i] == '\n'.code.toByte()) {
                buffer.write(chunk, start, i - start)
                var line = buffer.toString(Charsets.UTF_8.name())
                if (line.endsWith("\r")) line = line.dropLast(1)
                onLine(line)
                buffer.reset()
                start = i + 1
            }
        }
        if (start < chunk.size) {
            buffer.write(chunk, start, chunk.size - start)
        }
    }

    fun flush(onLine: (String) -> Unit) {
        if (buffer.size() > 0) {
            var line = buffer.toString(Charsets.UTF_8.name())
            if (line.endsWith("\r")) line = line.dropLast(1)
            onLine(line)
            buffer.reset()
        }
    }
}
