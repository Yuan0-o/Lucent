package com.lucent.app.harness

class StreamTailer {
    var offset = 0L
        private set
    private val buffer = StringBuilder()

    fun processNewBytes(chunk: ByteArray, onLine: (String) -> Unit) {
        offset += chunk.size
        val text = String(chunk)
        
        var start = 0
        for (i in text.indices) {
            if (text[i] == '\n') {
                var line = buffer.toString() + text.substring(start, i)
                if (line.endsWith("\r")) line = line.dropLast(1)
                onLine(line)
                buffer.clear()
                start = i + 1
            }
        }
        if (start < text.length) {
            buffer.append(text.substring(start))
        }
    }

    fun flush(onLine: (String) -> Unit) {
        if (buffer.isNotEmpty()) {
            var line = buffer.toString()
            if (line.endsWith("\r")) line = line.dropLast(1)
            onLine(line)
            buffer.clear()
        }
    }
}
