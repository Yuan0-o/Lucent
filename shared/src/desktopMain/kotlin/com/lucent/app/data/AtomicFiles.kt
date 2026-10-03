package com.lucent.app.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object AtomicFiles {

    fun replace(temp: File, target: File): Boolean {
        val atomic = runCatching {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.isSuccess
        if (atomic) return true
        return runCatching {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.isSuccess
    }
}
