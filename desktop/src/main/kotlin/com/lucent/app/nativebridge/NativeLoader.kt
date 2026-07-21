package com.lucent.app.nativebridge

import android.content.DesktopContext
import java.io.File

/**
 * Loads the app's optional native libraries on desktop.
 *
 * On Android the .so files ride inside the APK and `System.loadLibrary` finds them. On desktop the
 * DLLs are packaged as classpath resources (`/native/<mapped name>`, put there by the Windows CI
 * workflow), so loading is: try the library path first (a developer running with -Djava.library.path
 * set), then extract the resource beside the app data and `System.load` the absolute path.
 *
 * Both native libraries are pure accelerators/engines with graceful degradation on the Kotlin
 * side, so a missing or unloadable DLL must — and does — resolve to `false`, never to a crash.
 */
object NativeLoader {

    /**
     * Load the on-device LLM engine, preferring the Vulkan-enabled build when this machine can run
     * it (settings task A4 — Windows GPU support, mirroring the Android build's approach).
     *
     * The Windows CI packages up to two engine DLLs: `lucent_llama.dll` (CPU-only — the guaranteed
     * baseline) and `lucent_llama_vk.dll` (the same engine with llama.cpp's Vulkan GPU backend
     * compiled in — built best-effort when the Vulkan SDK is available). The Vulkan build links
     * against `vulkan-1.dll`, which ships with GPU drivers but is absent on some VMs and servers —
     * loading it there would fail outright and take the local model down with it. So the choice is
     * made HERE, once, before anything is loaded: the _vk variant is used only when the Windows
     * Vulkan runtime is actually present, and everything else gets the CPU DLL. Only one of the two
     * is ever loaded into the process (they export identical JNI symbols).
     *
     * With the CPU DLL resident, the in-app GPU switch stays harmless: llama.cpp simply reports no
     * GPU devices, so a requested offload loads on the CPU instead — the same graceful fallback
     * LocalLlm already applies when a driver rejects the offload. Default remains CPU either way;
     * GPU is opt-in behind the existing warning dialog, exactly like Android.
     */
    fun loadLlmEngine(): Boolean {
        if (vulkanRuntimePresent() && load("lucent_llama_vk")) return true
        return load("lucent_llama")
    }

    /** Whether the Windows Vulkan loader (vulkan-1.dll) is present on this machine. */
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
        // 1) The conventional path, for developers who put the DLL on java.library.path.
        try {
            System.loadLibrary(baseName)
            return true
        } catch (_: Throwable) {
        }
        // 2) The packaged path: extract /native/<mapped> from resources and load it absolutely.
        return try {
            val mapped = System.mapLibraryName(baseName) // lucent_llama -> lucent_llama.dll on Windows
            val resource = NativeLoader::class.java.getResourceAsStream("/native/$mapped") ?: return false
            val dir = File(DesktopContext.filesDir, "native").apply { mkdirs() }
            val target = File(dir, mapped)
            resource.use { input ->
                val bytes = input.readBytes()
                // Re-extract only when the packaged copy differs, so a locked in-use DLL on Windows
                // (from a still-closing previous instance) doesn't fail the load of an identical one.
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
