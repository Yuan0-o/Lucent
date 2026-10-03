package com.lucent.app

import com.lucent.app.platform.PlatformInputStream

expect class PlatformFile {
    constructor(pathname: String)
    constructor(parent: PlatformFile?, child: String)
    fun exists(): Boolean
    fun mkdirs(): Boolean
    fun delete(): Boolean
    fun deleteRecursively(): Boolean
    fun length(): Long
    fun listFiles(): Array<PlatformFile>?
    fun renameTo(dest: PlatformFile): Boolean
    fun inputStream(): PlatformInputStream
    fun outputStream(): PlatformOutputStream
    val name: String
    val parentFile: PlatformFile?
    val absolutePath: String
}
