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

    private val available: Boolean = run {
        val ok = com.lucent.app.nativebridge.NativeLoader.loadLlmEngine()
        if (!ok) Log.e("LocalLlm", "native engine library missing — local models unavailable")
        ok
    }

    fun isSupported(): Boolean = available

    fun supportsVision(): Boolean = handle != 0L && visionReady

    fun unsupportedBecauseCpuLacksAvx2(): Boolean =
        com.lucent.app.nativebridge.NativeLoader.cpuMissingAvx2

    private val llmDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "LucentLocalLlm") }.asCoroutineDispatcher()
    private val llmScope = CoroutineScope(SupervisorJob() + llmDispatcher)

    @Volatile private var handle: Long = 0L
    @Volatile private var loadedPath: String? = null
    @Volatile private var loadedSlotId: String? = null
    @Volatile private var visionReady: Boolean = false
    private val generating = AtomicBoolean(false)
    private val loading = AtomicBoolean(false)

    private const val GPU_OFFLOAD_ALL = 999

    private const val VRAM_HEADROOM_BYTES = 1_200L * 1024 * 1024

    const val N_CTX_GPU = 2048

    private const val GPU_LAYERS_BLIND_CAP = 20

    private const val KV_BYTES_PER_LAYER_PER_TOKEN = 512L

    private fun gpuLayersFor(modelFile: java.io.File): Int {
        val vram = detectVramBytes()
        if (vram <= 0L) {
            Log.w("LocalLlm", "VRAM unknown; capping GPU offload at $GPU_LAYERS_BLIND_CAP layers")
            return GPU_LAYERS_BLIND_CAP
        }
        val weights = modelFile.length()
        if (weights <= 0L) return GPU_LAYERS_BLIND_CAP
        val budget = vram - VRAM_HEADROOM_BYTES
        if (budget <= 0L) {
            Log.w("LocalLlm", "VRAM too small for any offload; staying on CPU")
            return 0
        }
        val assumedLayers = 32
        val perLayerWeights = (weights / assumedLayers).coerceAtLeast(1L)
        val perLayerKv = KV_BYTES_PER_LAYER_PER_TOKEN * N_CTX_GPU
        val affordable = (budget / (perLayerWeights + perLayerKv)).toInt()
        val layers = affordable.coerceIn(0, assumedLayers)
        Log.i(
            "LocalLlm",
            "GPU budget: vram=${vram / (1024 * 1024)}MB model=${weights / (1024 * 1024)}MB -> $layers layers"
        )
        return layers
    }

    private fun detectVramBytes(): Long {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        if (!os.contains("win")) return 0L
        return try {
            val script =
                "(Get-ItemProperty -Path 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Class\\" +
                    "{4d36e968-e325-11ce-bfc1-08002be10318}\\0*' -Name HardwareInformation.qwMemorySize " +
                    "-ErrorAction SilentlyContinue).'HardwareInformation.qwMemorySize'"
            val proc = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command", script
            ).redirectErrorStream(true).start()
            val text = proc.inputStream.bufferedReader().use { it.readText() }.trim()
            if (!proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                return 0L
            }
            text.lineSequence()
                .mapNotNull { it.trim().toLongOrNull() }
                .maxOrNull() ?: 0L
        } catch (t: Throwable) {
            Log.w("LocalLlm", "VRAM probe failed: ${t.message}")
            0L
        }
    }

    @Volatile private var desiredGpuLayers: Int = 0
    @Volatile private var loadedGpuLayers: Int = 0

    fun setGpuEnabled(enabled: Boolean) {
        desiredGpuLayers = if (enabled) GPU_OFFLOAD_ALL else 0
    }

    fun isLoaded(): Boolean = handle != 0L

    fun isLoading(): Boolean = loading.get()

    fun isGenerating(): Boolean = generating.get()

    private fun threadCount(): Int {
        val total = Runtime.getRuntime().availableProcessors()
        return (if (total > 4) total - 1 else total).coerceIn(2, 8)
    }

    suspend fun ensureLoaded(context: Context): Boolean = withContext(llmDispatcher) {
        if (!available) return@withContext false
        val activeSlot = LocalModelStore.activeSlot(context) ?: return@withContext false
        val file = LocalModelStore.activeModelFile(context) ?: return@withContext false
        if (handle != 0L &&
            loadedSlotId == activeSlot.id &&
            loadedPath == file.absolutePath &&
            (loadedGpuLayers > 0) == (desiredGpuLayers > 0)
        ) return@withContext true
        if (handle != 0L) {
            nativeUnload(handle)
            handle = 0L
            visionReady = false
            loadedPath = null
            loadedSlotId = null
        }
        val wantGpu = if (desiredGpuLayers > 0) gpuLayersFor(file) else 0
        fun attempt(gpuLayers: Int): Long = try {
            val ctx = if (gpuLayers > 0) N_CTX_GPU else N_CTX
            nativeLoad(file.absolutePath, ctx, threadCount(), gpuLayers)
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

    suspend fun generate(
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

    fun stop() {
        val h = handle
        if (h != 0L) try {
            nativeStop(h)
        } catch (_: Throwable) {
        }
    }

    fun shutdown() {
        if (!available) return
        stop()
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
