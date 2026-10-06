package com.lucent.app.harness

expect class ZipWriter() {
    fun addEntry(name: String, data: ByteArray)
    fun close(): ByteArray
}
