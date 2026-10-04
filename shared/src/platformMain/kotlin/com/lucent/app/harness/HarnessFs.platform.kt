package com.lucent.app.harness

import java.io.File
import java.nio.charset.StandardCharsets

private object JvmHarnessFs : HarnessFs {
    override fun canonicalize(path: String): String = File(path).canonicalPath
    override fun exists(path: String): Boolean = File(path).exists()
    override fun isDirectory(path: String): Boolean = File(path).isDirectory
    override fun isAbsolute(path: String): Boolean = File(path).isAbsolute
    override fun length(path: String): Long = File(path).length()
    override fun parentOf(path: String): String? = File(path).parentFile?.path
    override fun rootOf(path: String): String? = File(path).toPath().root?.toFile()?.path
    override fun nameOf(path: String): String = File(path).name
    override fun join(parent: String, child: String): String = File(parent, child).path
    override fun relativize(base: String, target: String): String =
        File(base).toPath().relativize(File(target).toPath()).toString().replace('\\', '/')
    override val separator: String get() = File.separator
    override fun readBytes(path: String): ByteArray = File(path).readBytes()
    override fun readHead(path: String, n: Int): ByteArray {
        val head = ByteArray(n)
        File(path).inputStream().use { it.read(head) }
        return head
    }
    override fun readLines(path: String): List<String> = File(path).readLines()
    override fun readText(path: String): String = File(path).readText()
    override fun writeText(path: String, text: String) = File(path).writeText(text)
    override fun appendText(path: String, text: String) = File(path).appendText(text)
    override fun mkdirs(path: String) { File(path).mkdirs() }
    override fun copy(src: String, dst: String, overwrite: Boolean) = File(src).copyTo(File(dst), overwrite = overwrite)
    override fun delete(path: String) { File(path).delete() }
    override fun deleteRecursively(path: String) { File(path).deleteRecursively() }
    override fun list(path: String): List<String> = File(path).list()?.toList() ?: emptyList()
}

actual fun systemHarnessFs(): HarnessFs = JvmHarnessFs
