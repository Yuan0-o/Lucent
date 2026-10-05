package com.lucent.app.platform

expect abstract class PlatformInputStream : AutoCloseable {
    fun read(): Int
    fun read(b: ByteArray): Int
    fun read(b: ByteArray, off: Int, len: Int): Int
    override fun close()
}
