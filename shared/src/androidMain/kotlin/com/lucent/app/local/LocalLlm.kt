package com.lucent.app.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object LocalLlm {

    const val N_CTX = 4096

    const val MAX_NEW_TOKENS = 512

    const val HISTORY_TURNS = 8


    fun isSupported(): Boolean = LocalLlmProxy.isSupported()

    fun supportsVision(): Boolean = LocalLlmProxy.supportsVision()

    fun unsupportedBecauseCpuLacksAvx2(): Boolean = false

    fun setGpuEnabled(enabled: Boolean) = LocalLlmProxy.setGpuEnabled(enabled)

    fun isLoaded(): Boolean = LocalLlmProxy.isLoaded()

    fun isLoading(): Boolean = LocalLlmProxy.isLoading()

    fun isGenerating(): Boolean = LocalLlmProxy.isGenerating()

    internal var ensureLoadedOverride: (suspend (Context) -> Boolean)? = null

    internal var generateOverride: (suspend (List<Pair<String, String>>, List<ByteArray>, (String) -> Unit) -> Int)? = null

    internal fun resetOverridesForTesting() {
        ensureLoadedOverride = null
        generateOverride = null
    }

    suspend fun ensureLoaded(context: Context): Boolean =
        ensureLoadedOverride?.invoke(context) ?: LocalLlmProxy.ensureLoaded(context)

    suspend fun generate(
        messages: List<Pair<String, String>>,
        images: List<ByteArray> = emptyList(),
        onDelta: (String) -> Unit
    ): Int = generateOverride?.invoke(messages, images, onDelta)
        ?: LocalLlmProxy.generate(messages, images, onDelta)

    fun stop() = LocalLlmProxy.stop()

    fun shutdown() = LocalLlmProxy.shutdown()


    internal const val RC_ENGINE_GLUE_ERROR = -21

    private val available: Boolean by lazy {
        try {
            System.loadLibrary("lucent_llama")
            true
        } catch (t: Throwable) {
            Log.e("LocalLlm", "native library missing: ${t.message}")
            false
        }
    }

    internal fun engineIsSupported(): Boolean = available

    internal fun engineSupportsVision(): Boolean = handle != 0L && visionReady

    private val llmDispatcher: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "LucentLocalLlm") }.asCoroutineDispatcher()
    }
    private val llmScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + llmDispatcher) }

    @Volatile private var handle: Long = 0L
    @Volatile private var loadedPath: String? = null
    @Volatile private var loadedSlotId: String? = null
    @Volatile private var visionReady: Boolean = false
    private val generating = AtomicBoolean(false)
    private val loading = AtomicBoolean(false)

    private const val GPU_OFFLOAD_ALL = 999

    @Volatile private var loadedGpuLayers: Int = 0

    internal fun engineIsLoaded(): Boolean = handle != 0L

    internal fun engineIsLoading(): Boolean = loading.get()

    internal fun engineIsGenerating(): Boolean = generating.get()

    private fun threadCount(): Int = performanceCores().coerceIn(2, 8)

    @Volatile private var cachedPerfCores: Int = 0

    private fun performanceCores(): Int {
        cachedPerfCores.takeIf { it > 0 }?.let { return it }
        val total = Runtime.getRuntime().availableProcessors()
        val result = try {
            val freqs = (0 until total).mapNotNull { i ->
                java.io.File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                    .takeIf { it.canRead() }
                    ?.readText()
                    ?.trim()
                    ?.toLongOrNull()
            }
            if (freqs.isEmpty()) {
                (total / 2)
            } else {
                val top = freqs.max()
                freqs.count { it >= top * 85 / 100 }
            }
        } catch (t: Throwable) {
            total / 2
        }
        val safe = result.coerceAtLeast(1)
        cachedPerfCores = safe
        return safe
    }

    internal suspend fun engineEnsureLoaded(context: Context, gpuEnabled: Boolean): Boolean =
        withContext(llmDispatcher) {
        if (!available) return@withContext false
        val activeSlot = LocalModelStore.activeSlot(context) ?: return@withContext false
        val file = LocalModelStore.activeModelFile(context) ?: return@withContext false
        val wantGpu = if (gpuEnabled) GPU_OFFLOAD_ALL else 0
        if (handle != 0L &&
            loadedSlotId == activeSlot.id &&
            loadedPath == file.absolutePath &&
            loadedGpuLayers == wantGpu
        ) return@withContext true
        if (handle != 0L) {
            nativeUnload(handle)
            handle = 0L
            visionReady = false
            loadedPath = null
            loadedSlotId = null
        }
        fun attempt(gpuLayers: Int): Long = try {
            nativeLoad(file.absolutePath, N_CTX, threadCount(), gpuLayers)
        } catch (t: Throwable) {
            Log.e("LocalLlm", "load failed (gpuLayers=$gpuLayers)", t)
            0L
        }
        loading.set(true)
        try {
            var used = wantGpu
            var h = attempt(wantGpu)
            if (h == 0L && wantGpu > 0) {
                Log.w("LocalLlm", "GPU load failed; falling back to CPU")
                used = 0
                h = attempt(0)
            }
            if (h != 0L) {
                handle = h
                loadedPath = file.absolutePath
                loadedSlotId = activeSlot.id
                loadedGpuLayers = used
                visionReady = try {
                    val mmproj = LocalModelStore.activeMmprojFile(context)
                    mmproj != null && nativeMtmdLoad(h, mmproj.absolutePath, threadCount())
                } catch (t: Throwable) {
                    Log.e("LocalLlm", "mmproj load failed", t)
                    false
                }
            }
            h != 0L
        } finally {
            loading.set(false)
        }
    }

    interface PieceCallback {
        fun onPiece(piece: String)
    }

    internal suspend fun engineGenerate(
        messages: List<Pair<String, String>>,
        images: List<ByteArray> = emptyList(),
        onDelta: (String) -> Unit
    ): Int = withContext(llmDispatcher) {
        val h = handle
        if (h == 0L) return@withContext -1
        generating.set(true)
        try {
            val useVision = images.isNotEmpty() && visionReady
            val marker = if (useVision) {
                try { nativeMediaMarker() } catch (t: Throwable) { "" }
            } else ""
            val effective = if (useVision && marker.isNotEmpty()) {
                val lastUser = messages.indexOfLast { it.first == "user" }
                messages.mapIndexed { i, m ->
                    if (i == lastUser) m.first to (marker.repeat(images.size) + "\n" + m.second) else m
                }
            } else messages
            val roles = Array(effective.size) { effective[it].first }
            val texts = Array(effective.size) { effective[it].second }
            val prompt = try {
                nativeChatPrompt(h, roles, texts, true)
            } catch (t: Throwable) {
                Log.e("LocalLlm", "template failed", t)
                ""
            }
            if (prompt.isBlank()) return@withContext -2
            val cb = object : PieceCallback {
                override fun onPiece(piece: String) {
                    onDelta(piece)
                }
            }
            try {
                if (useVision && marker.isNotEmpty()) {
                    val rc = nativeGenerateWithImages(h, prompt, images.toTypedArray(), MAX_NEW_TOKENS, cb)
                    if (rc == -30 || rc == -31) nativeGenerate(h, prompt, MAX_NEW_TOKENS, cb) else rc
                } else {
                    nativeGenerate(h, prompt, MAX_NEW_TOKENS, cb)
                }
            } catch (t: Throwable) {
                Log.e("LocalLlm", "nativeGenerate threw", t)
                -20
            }
        } finally {
            generating.set(false)
        }
    }

    internal fun engineStop() {
        val h = handle
        if (h != 0L) try {
            nativeStop(h)
        } catch (_: Throwable) {
        }
    }

    internal fun engineShutdown() {
        if (!available) return
        engineStop()
        llmScope.launch {
            val h = handle
            handle = 0L
            visionReady = false
            loadedPath = null
            loadedSlotId = null
            if (h != 0L) try {
                nativeUnload(h)
            } catch (t: Throwable) {
                Log.e("LocalLlm", "unload failed", t)
            }
        }
    }

    private external fun nativeLoad(path: String, nCtx: Int, nThreads: Int, nGpuLayers: Int): Long
    private external fun nativeMtmdLoad(handle: Long, mmprojPath: String, nThreads: Int): Boolean
    private external fun nativeMediaMarker(): String
    private external fun nativeGenerateWithImages(handle: Long, prompt: String, images: Array<ByteArray>, maxNew: Int, callback: PieceCallback): Int
    private external fun nativeChatPrompt(handle: Long, roles: Array<String>, texts: Array<String>, addAssistant: Boolean): String
    private external fun nativeGenerate(handle: Long, prompt: String, maxNew: Int, callback: PieceCallback): Int
    private external fun nativeStop(handle: Long)
    private external fun nativeUnload(handle: Long)
}
