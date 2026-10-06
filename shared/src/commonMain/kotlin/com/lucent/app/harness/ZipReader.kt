package com.lucent.app.harness

expect object ZipReader {
    fun readEntries(bytes: ByteArray, wanted: (String) -> Boolean = { true }): Map<String, ByteArray>
}
