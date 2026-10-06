package com.lucent.app.harness

import okio.Buffer

class StreamTailer {
    var offset = 0L
        private set
    private val buffer = Buffer()

    fun processNewBytes(chunk: ByteArray, onLine: (String) -> Unit) {
        offset += chunk.size
        var start = 0
        for (i in chunk.indices) {
            if (chunk[i] == '\n'.code.toByte()) {
                buffer.write(chunk, start, i - start)
                var line = buffer.readUtf8()
                if (line.endsWith("\r")) line = line.dropLast(1)
                onLine(line)
                start = i + 1
            }
        }
        if (start < chunk.size) {
            buffer.write(chunk, start, chunk.size - start)
        }
    }

    fun flush(onLine: (String) -> Unit) {
        if (buffer.size > 0) {
            var line = buffer.readUtf8()
            if (line.endsWith("\r")) line = line.dropLast(1)
            onLine(line)
        }
    }
}
