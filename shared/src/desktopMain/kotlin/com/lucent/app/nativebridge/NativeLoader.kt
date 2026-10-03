package com.lucent.app.nativebridge

import android.content.DesktopContext
import java.io.File

object NativeLoader {

    @Volatile
    var cpuMissingAvx2: Boolean = false
        private set

    fun loadLlmEngine(): Boolean {
        if (LucentNative.cpuHasAvx2() == false) {
            cpuMissingAvx2 = true
            return false
        }
        if (vulkanRuntimePresent() && load("lucent_llama_vk")) return true
        return load("lucent_llama")
    }

    private fun vulkanRuntimePresent(): Boolean {
        return try {
            val os = System.getProperty("os.name")?.lowercase() ?: ""
            if (!os.contains("win")) {
                false
            } else {
                val sysRoot = System.getenv("SystemRoot") ?: "C:\\Windows"
                File(sysRoot, "System32\\vulkan-1.dll").exists() ||
                    File(sysRoot, "SysWOW64\\vulkan-1.dll").exists()
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun load(baseName: String): Boolean {
        try {
            System.loadLibrary(baseName)
            return true
        } catch (_: Throwable) {
        }
        return try {
            val mapped = System.mapLibraryName(baseName)
            val resource = NativeLoader::class.java.getResourceAsStream("/native/$mapped") ?: return false
            val dir = File(DesktopContext.filesDir, "native").apply { mkdirs() }
            val target = File(dir, mapped)
            resource.use { input ->
                val bytes = input.readBytes()
                if (!target.exists() || target.length() != bytes.size.toLong()) {
                    val tmp = File(dir, "$mapped.tmp")
                    tmp.writeBytes(bytes)
                    if (!tmp.renameTo(target)) {
                        target.delete()
                        if (!tmp.renameTo(target)) return false
                    }
                }
            }
            System.load(target.absolutePath)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
