package com.lucent.app.harness

interface HarnessFs {
    fun canonicalize(path: String): String
    fun exists(path: String): Boolean
    fun isDirectory(path: String): Boolean
    fun isAbsolute(path: String): Boolean
    fun length(path: String): Long
    fun parentOf(path: String): String?
    fun rootOf(path: String): String?
    fun nameOf(path: String): String
    fun join(parent: String, child: String): String
    fun relativize(base: String, target: String): String
    val separator: String
    fun readBytes(path: String): ByteArray
    fun readHead(path: String, n: Int): ByteArray
    fun readLines(path: String): List<String>
    fun readText(path: String): String
    fun writeText(path: String, text: String)
    fun appendText(path: String, text: String)
    fun mkdirs(path: String)
    fun copy(src: String, dst: String, overwrite: Boolean)
    fun delete(path: String)
    fun deleteRecursively(path: String)
    fun list(path: String): List<String>
}

expect fun systemHarnessFs(): HarnessFs
