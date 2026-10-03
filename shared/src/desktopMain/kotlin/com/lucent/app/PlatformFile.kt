package com.lucent.app

import com.lucent.app.platform.PlatformInputStream

actual class PlatformFile {
    private val file: java.io.File

    actual constructor(pathname: String) {
        file = java.io.File(pathname)
    }

    actual constructor(parent: PlatformFile?, child: String) {
        file = java.io.File(parent?.file, child)
    }

    actual fun exists(): Boolean = file.exists()

    actual fun mkdirs(): Boolean = file.mkdirs()

    actual fun delete(): Boolean = file.delete()

    actual fun deleteRecursively(): Boolean = file.deleteRecursively()

    actual fun length(): Long = file.length()

    actual fun listFiles(): Array<PlatformFile>? =
        file.listFiles()?.map { PlatformFile(it.absolutePath) }?.toTypedArray()

    actual fun renameTo(dest: PlatformFile): Boolean = file.renameTo(dest.file)

    actual fun inputStream(): PlatformInputStream = file.inputStream()

    actual fun outputStream(): PlatformOutputStream = file.outputStream()

    actual val name: String get() = file.name

    actual val parentFile: PlatformFile?
        get() = file.parentFile?.let { PlatformFile(it) }
}
