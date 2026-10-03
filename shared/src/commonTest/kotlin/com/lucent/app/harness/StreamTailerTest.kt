package com.lucent.app.harness

import org.junit.Test
import kotlin.test.assertEquals

class StreamTailerTest {

    @Test
    fun testStreamTailer() {
        val tailer = StreamTailer()
        val lines = mutableListOf<String>()
        val onLine: (String) -> Unit = { lines.add(it) }

        tailer.processNewBytes("hello\nworld\n".toByteArray(), onLine)
        assertEquals(listOf("hello", "world"), lines)

        lines.clear()
        tailer.processNewBytes("parti".toByteArray(), onLine)
        assertEquals(emptyList<String>(), lines)

        tailer.processNewBytes("al\n".toByteArray(), onLine)
        assertEquals(listOf("partial"), lines)

        lines.clear()
        tailer.processNewBytes("flush_me".toByteArray(), onLine)
        assertEquals(emptyList<String>(), lines)
        tailer.flush(onLine)
        assertEquals(listOf("flush_me"), lines)

        val expectedOffset = "hello\nworld\npartial\nflush_me".toByteArray().size.toLong()
        assertEquals(expectedOffset, tailer.offset)
        
        lines.clear()
        tailer.processNewBytes("windows\r\n".toByteArray(), onLine)
        assertEquals(listOf("windows"), lines)
    }
}
