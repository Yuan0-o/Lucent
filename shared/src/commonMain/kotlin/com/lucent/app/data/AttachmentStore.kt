package com.lucent.app.data

import com.lucent.app.PlatformFile
import com.lucent.app.PlatformOutputStream
import com.lucent.app.platform.PlatformContext
import com.lucent.app.platform.PlatformInputStream

expect object AttachmentStore {
    fun baseDir(context: PlatformContext): PlatformFile
    fun fileFor(context: PlatformContext, id: String): PlatformFile
    fun looksLikeId(value: String): Boolean
    fun importBytes(context: PlatformContext, bytes: ByteArray): String?
    fun writeBytes(context: PlatformContext, id: String, bytes: ByteArray): Boolean
    fun openOutputStream(context: PlatformContext, id: String): PlatformOutputStream?
    fun openInputStream(context: PlatformContext, id: String): PlatformInputStream?
    fun readBytes(context: PlatformContext, id: String, maxBytes: Long = 33554432L): ByteArray?
    fun exists(context: PlatformContext, id: String): Boolean
    fun sizeOf(context: PlatformContext, id: String): Long
    fun totalBytes(context: PlatformContext): Long
    fun encryptExistingFile(context: PlatformContext, id: String): Boolean
    fun delete(context: PlatformContext, id: String): Boolean
    fun pruneOrphans(context: PlatformContext, referencedIds: Set<String>)
}
