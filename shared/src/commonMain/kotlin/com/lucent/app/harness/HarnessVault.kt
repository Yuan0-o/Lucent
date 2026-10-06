package com.lucent.app.harness

import com.lucent.app.platform.PlatformContext
import com.lucent.app.platform.appContext
import com.lucent.app.data.DataKeys
import com.lucent.app.data.FileCrypto
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

object HarnessVault {

    fun write(context: PlatformContext, file: Path, text: String) {
        try {
            file.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            val keyBytes = DataKeys.attachmentKey(context.appContext())
            val bytes = FileCrypto.encrypt(text.toByteArray(Charsets.UTF_8), keyBytes)
            FileSystem.SYSTEM.write(file) { write(bytes) }
        } catch (_: Throwable) {
        }
    }

    fun read(context: PlatformContext, file: Path): String {
        if (!FileSystem.SYSTEM.exists(file)) return ""
        return try {
            val raw = FileSystem.SYSTEM.read(file) { readByteArray() }
            if (!FileCrypto.isEncrypted(FileSystem.SYSTEM, file)) return String(raw, Charsets.UTF_8)
            val keyBytes = DataKeys.attachmentKey(context.appContext())
            String(FileCrypto.decrypt(raw, keyBytes), Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }
}
