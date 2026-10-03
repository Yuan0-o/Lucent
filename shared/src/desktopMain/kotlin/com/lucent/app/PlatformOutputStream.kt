package com.lucent.app

actual class PlatformOutputStream(val stream: java.io.OutputStream) {
    actual fun write(b: ByteArray) = stream.write(b)

    actual fun write(b: ByteArray, off: Int, len: Int) = stream.write(b, off, len)

    actual fun flush() = stream.flush()

    actual fun close() = stream.close()
}
