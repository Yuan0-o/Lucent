package com.lucent.app.harness

import android.content.Context
import com.lucent.app.data.DataKeys
import com.lucent.app.data.FileCrypto
import java.io.File

object HarnessVault {

    fun write(context: Context, file: File, text: String) {
        try {
            file.parentFile?.mkdirs()
            val key = DataKeys.attachmentKey(context.applicationContext)
            val bytes = FileCrypto.encrypt(text.toByteArray(Charsets.UTF_8), key)
            file.writeBytes(bytes)
        } catch (_: Throwable) {
        }
    }

    fun read(context: Context, file: File): String {
        if (!file.exists()) return ""
        return try {
            val raw = file.readBytes()
            if (!FileCrypto.isEncrypted(file)) return String(raw, Charsets.UTF_8)
            val key = DataKeys.attachmentKey(context.applicationContext)
            String(FileCrypto.decrypt(raw, key), Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }
}
