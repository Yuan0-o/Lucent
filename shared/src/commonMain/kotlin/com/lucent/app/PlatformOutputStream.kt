package com.lucent.app

expect class PlatformOutputStream {
    fun write(b: ByteArray)
    fun write(b: ByteArray, off: Int, len: Int)
    fun flush()
    fun close()
}
