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

/**
 * The on-device GGUF assistant engine (task: local large-model assistant).
 *
 * Everything llama.cpp-related funnels through here, and through **one dedicated background
 * thread** ([llmDispatcher]). That single decision is what delivers the task's hard requirements:
 *
 *  - **No lag, no freezes.** The UI thread never calls native code. Loading a multi-gigabyte model
 *    and decoding tokens both happen on the llm thread; the UI only ever observes state and
 *    receives streamed text through the existing typewriter, exactly as with a cloud model.
 *  - **No crashes.** A llama context is not thread-safe; a single serial executor makes concurrent
 *    access impossible by construction (the native layer holds a mutex too, as a second belt).
 *    Every native call is also wrapped so a failure becomes a return code, never an abort.
 *  - **Memory released on exit.** [shutdown] frees sampler, context, and model, and MainActivity
 *    calls it when the activity is finishing — so the moment the user actually leaves the app the
 *    gigabytes come back. (If the OS kills the process instead, the kernel reclaims them anyway.)
 *
 * Simplicity is deliberate: no tools, no cross-conversation memory, no KV-cache reuse between
 * turns. Each generation receives a self-contained prompt (recent turns of the current chat) and
 * starts from a clean context. Fluency was named the highest priority, and a stateless turn can
 * never be desynchronized, never leak context, and never hit the "cache poisoned, output garbage"
 * class of bug.
 */
object LocalLlm {

    /**
     * Context window. Kept modest — prompt-processing time is what old devices feel most — but large
     * enough to hold the tool-catalogue system prompt plus several turns of the current chat and a
     * couple of tool-result rounds. Anything longer is tail-truncated in the native layer, never a
     * crash. (Was 2048 before local tool-calling existed; the tool guide needs the extra room.)
     */
    const val N_CTX = 4096

    /** Cap on new tokens per reply — long enough for a real answer, bounded so a turn always ends. */
    const val MAX_NEW_TOKENS = 512

    /** Chat turns (user+assistant messages) of the current conversation sent with each prompt. */
    const val HISTORY_TURNS = 8

    // Desktop adaptation: the engine DLL is packaged as a resource and extracted on first use
    // (see nativebridge/NativeLoader). loadLlmEngine prefers the Vulkan-enabled DLL on machines
    // that can run it and falls back to the CPU-only DLL everywhere else (settings task A4);
    // everything below this line is the Android file verbatim.
    private val available: Boolean = run {
        val ok = com.lucent.app.nativebridge.NativeLoader.loadLlmEngine()
        if (!ok) Log.e("LocalLlm", "native engine library missing — local models unavailable")
        ok
    }

    /** Whether the native engine was packaged for this ABI at all. */
    fun isSupported(): Boolean = available

    /**
     * W-1: whether "unsupported" specifically means "this CPU lacks AVX2" (engine load refused by
     * NativeLoader's guard). Lets shared UI show the honest reason. Always false on Android, where
     * ABI packaging is the only way to be unsupported — the twin files stay identical by asking
     * this same question.
     */
    fun unsupportedBecauseCpuLacksAvx2(): Boolean =
        com.lucent.app.nativebridge.NativeLoader.cpuMissingAvx2

    // One thread for every native call, for the model's whole lifetime.
    private val llmDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "LucentLocalLlm") }.asCoroutineDispatcher()
    private val llmScope = CoroutineScope(SupervisorJob() + llmDispatcher)

    @Volatile private var handle: Long = 0L
    @Volatile private var loadedPath: String? = null
    // The slot id of the resident model, so a switch to a different slot forces a clean reload even
    // if two slots ever shared a path (they don't today, but tracking the id makes the intent exact).
    @Volatile private var loadedSlotId: String? = null
    private val generating = AtomicBoolean(false)
    // True only while a model is actually being loaded into memory (the slow, multi-second first
    // load). The assistant reads this to show a distinct "loading the model…" line, so the wait is
    // visible and never looks like a hang — loading a multi-gigabyte model is expected to take time.
    private val loading = AtomicBoolean(false)

    /**
     * Offload-all sentinel for GPU mode; llama.cpp keeps on CPU any layer the device can't take.
     *
     * **Do not pass this to [nativeLoad] on Windows unguarded — see [gpuLayersFor].** It is kept as
     * the value of the *user's preference* ("GPU, please"), not as the number of layers actually
     * requested from the engine.
     */
    private const val GPU_OFFLOAD_ALL = 999

    /**
     * ### C-group task 4: why "offload everything" was a machine-killer
     *
     * The reported failure was: GPU acceleration on, ask the assistant to create a task, whole
     * machine goes black, forced restart, and afterwards Lucent will not start, will not uninstall,
     * and will not reinstall into the same folder. That chain starts here.
     *
     * `n_gpu_layers = 999` asks llama.cpp to put **every** layer in VRAM. llama.cpp honours that
     * literally: unlike the CPU path there is no "and fall back if it does not fit" — it allocates
     * until the driver refuses. On top of the weights sits the KV cache for [N_CTX] = 4096 tokens,
     * which for a 7-8B model is another ~1-2 GB. A 6 GB or 8 GB consumer card running a desktop,
     * a browser and a compositor does not have that headroom.
     *
     * Asking the assistant to *create a task* is what tips it over, and that is not a coincidence:
     * a tool call forces a **second** generation round (generate → call the tool → feed the result
     * back → generate again) against a longer prompt. Peak VRAM lands on that second decode. When a
     * Vulkan allocation fails or a shader overruns its budget there, the Windows display driver hits
     * **TDR** (Timeout Detection and Recovery). A TDR that recovers gives a black flicker; one that
     * does not gives exactly what was reported — a black screen with no way out but the power
     * button.
     *
     * Everything after that is collateral from the hard power-off, not a second bug: see
     * [com.lucent.app.data.LocalSecrets] and [com.lucent.app.data.DataKeys] for the key files a
     * forced power-off can leave present-but-empty, which is what produces the
     * "could not be unlocked with this machine's key" dialog on the next launch.
     *
     * ### The fix
     *
     * Three changes, in order of how much they matter:
     *
     *  1. **Never request more layers than the card can hold.** [gpuLayersFor] budgets against the
     *     model file's own size plus the KV cache, and leaves [VRAM_HEADROOM_BYTES] for the desktop
     *     compositor. A model that cannot fit gets a *partial* offload, which is what GPU
     *     acceleration is supposed to mean anyway — llama.cpp runs the remaining layers on the CPU.
     *  2. **Shrink the context on GPU.** The KV cache is the part that scales with context and the
     *     part users never see, so GPU turns use [N_CTX_GPU] rather than [N_CTX]. A shorter memory
     *     is a far better outcome than a driver reset.
     *  3. **Refuse rather than gamble when the budget is unknown.** If VRAM cannot be determined,
     *     the offload is capped at [GPU_LAYERS_BLIND_CAP] instead of falling through to "all of
     *     them". An unknown card is not evidence of a large card.
     */
    private const val VRAM_HEADROOM_BYTES = 1_200L * 1024 * 1024   // left for the compositor/browser

    /** Context length used for GPU turns; the KV cache is the allocation that scales with it. */
    const val N_CTX_GPU = 2048

    /** Layer cap applied when this machine's VRAM could not be determined at all. */
    private const val GPU_LAYERS_BLIND_CAP = 20

    /** Rough per-layer KV cache cost used for budgeting; deliberately generous. */
    private const val KV_BYTES_PER_LAYER_PER_TOKEN = 512L

    /**
     * How many layers may safely be offloaded for [modelFile] on this machine.
     *
     * Budgeting, not probing: a probe means allocating, and allocating is the thing that hangs the
     * driver. So the decision is made from numbers that are free to obtain — the model file's size
     * on disk (a good proxy for the weights, since a GGUF is almost entirely weights) and the
     * reported VRAM — and it errs downwards at every step.
     *
     * Returns 0 when the answer is "do not use the GPU at all", which the caller treats exactly like
     * a CPU turn.
     */
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
        // Assume a typical 32-layer model unless the file is small enough to imply fewer; the exact
        // count is not knowable without parsing the GGUF header, and over-estimating layers makes
        // the per-layer budget SMALLER, which is the safe direction to be wrong in.
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

    /**
     * Total VRAM on the primary adapter, in bytes, or 0 when it cannot be determined.
     *
     * Read through WMI via PowerShell, the same mechanism `security/WindowsHello.kt` already uses to
     * reach a Windows API the JVM cannot see. `AdapterRAM` is a 32-bit field and therefore wrong
     * (it wraps) on cards above 4 GB, so the driver's own `HardwareInformation.qwMemorySize`
     * registry value is read first and `AdapterRAM` is only the fallback. Any failure returns 0,
     * which [gpuLayersFor] treats as "unknown", not as "plenty".
     */
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

    // The GPU choice (0 = CPU, the safe default; GPU_OFFLOAD_ALL = GPU). [desiredGpuLayers] is what
    // the user's setting asks for; [loadedGpuLayers] is what the resident model was actually loaded
    // with, so a change to the setting triggers a clean reload on the next send.
    @Volatile private var desiredGpuLayers: Int = 0
    @Volatile private var loadedGpuLayers: Int = 0

    /**
     * Apply the user's CPU/GPU choice. Called from the assistant before a local turn. Only records
     * the desired backend; the next [ensureLoaded] notices the mismatch and reloads the model cleanly
     * on the chosen backend (no separate async unload, so there is no race with a concurrent load).
     * Cheap and idempotent when nothing changes.
     *
     * DELIBERATE CONTRACT — a flip made WHILE a reply is generating is silent and deferred: the
     * in-flight reply keeps the backend it started on, and the change takes effect from the next
     * reply. Three things uphold this, and all three must survive future edits:
     *  1. This setter records a preference and nothing else — it must never stop, unload, or
     *     reload anything.
     *  2. A turn captures its choice once, at send: AssistantController receives useGpu as a
     *     parameter and calls this exactly once, before its single ensureLoaded.
     *  3. Every native call runs on [llmDispatcher]'s one thread, so even a stray ensureLoaded
     *     from some future call site queues behind a running decode instead of swapping the
     *     model out from under it.
     */
    fun setGpuEnabled(enabled: Boolean) {
        desiredGpuLayers = if (enabled) GPU_OFFLOAD_ALL else 0
    }

    /** True while a model is resident in memory. */
    fun isLoaded(): Boolean = handle != 0L

    /** True while a model is being loaded into memory (used to show a "loading…" state). */
    fun isLoading(): Boolean = loading.get()

    /** True while a local generation is in flight (used by Stop). */
    fun isGenerating(): Boolean = generating.get()

    /**
     * Threads for token generation: half the cores, clamped to 2..4. More threads than that hurts
     * on big.LITTLE phones (little cores drag the pace) and cooks the battery; fewer starves it.
     */
    /**
     * Threads for token generation (B-group task 10: "squeeze the processor").
     *
     * The previous rule — half the cores, capped at 4 — was inherited from the Android build, where
     * it exists to avoid handing work to big.LITTLE little cores that would set the pace for every
     * token. On a desktop that reasoning mostly does not apply: a typical Windows machine has one
     * uniform performance cluster (or, on 12th-gen-and-later Intel, P-cores plus E-cores), and the
     * cap meant a 16-core workstation ran the model on four threads.
     *
     * So the cap moves to 8 and the count is taken from the machine's actual core count rather than
     * an arbitrary half. Still capped, and deliberately: past ~8 threads llama.cpp spends more time
     * synchronising per token than the extra parallelism wins back, so more threads would be slower
     * as well as noisier. Leaving a couple of cores free also keeps the UI thread responsive while
     * a reply generates, which on a desktop the user WILL notice.
     */
    private fun threadCount(): Int {
        val total = Runtime.getRuntime().availableProcessors()
        // Leave one core for the rest of the app on anything with room to spare.
        return (if (total > 4) total - 1 else total).coerceIn(2, 8)
    }

    /**
     * Make sure the imported model is loaded, loading it if needed. Safe to call every send:
     * a second call with the same file AND the same backend is a no-op, and a changed file or a
     * flipped CPU/GPU choice swaps cleanly. If a GPU load fails (e.g. a flaky Vulkan driver), it
     * transparently retries on CPU rather than failing — GPU is opt-in, so it must never be able to
     * take the whole feature down. Returns false when there is no model, the ABI is unsupported, or
     * even the CPU load failed.
     */
    suspend fun ensureLoaded(context: Context): Boolean = withContext(llmDispatcher) {
        if (!available) return@withContext false
        // Load whichever slot is ACTIVE. A different active slot than the resident one is what makes
        // "switch models" release the old and load the new: the id/path no longer match, so the block
        // below unloads first. Only one model is ever resident.
        val activeSlot = LocalModelStore.activeSlot(context) ?: return@withContext false
        val file = LocalModelStore.activeModelFile(context) ?: return@withContext false
        // Compare the *preference* (GPU on/off), not the budgeted layer count: the budget is derived
        // from live VRAM and can legitimately differ by a layer or two between calls, and comparing
        // it directly would reload a multi-gigabyte model on every single send.
        if (handle != 0L &&
            loadedSlotId == activeSlot.id &&
            loadedPath == file.absolutePath &&
            (loadedGpuLayers > 0) == (desiredGpuLayers > 0)
        ) return@withContext true
        if (handle != 0L) {
            // A previous model is resident (a different slot, or a changed backend). Free it before
            // the new one loads so the peak footprint is one model, not two.
            nativeUnload(handle)
            handle = 0L
            visionReady = false
            loadedPath = null
            loadedSlotId = null
        }
        // C-group task 4: the user's preference is still "GPU or not", but what reaches the engine
        // is a BUDGETED layer count, never the offload-all sentinel. See [gpuLayersFor].
        val wantGpu = if (desiredGpuLayers > 0) gpuLayersFor(file) else 0
        fun attempt(gpuLayers: Int): Long = try {
            // A GPU turn also runs on the shorter context: the KV cache is what scales with context
            // length, and it is the allocation that pushed the driver over the edge.
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
                // GPU offload didn't take — fall back to CPU so the feature still works.
                Log.w("LocalLlm", "GPU load failed; falling back to CPU")
                used = 0
                h = attempt(0)
            }
            if (h != 0L) {
                handle = h
                loadedPath = file.absolutePath
                loadedSlotId = activeSlot.id
                loadedGpuLayers = used
                // PHASE 4: if this slot has a projector, load it beside the model. Best-effort in
                // the strictest sense — a failed projector leaves a perfectly good TEXT model
                // resident, so the flag is the only thing that changes.
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

    /** JNI streaming callback — the native side looks this method up by name and signature. */
    interface PieceCallback {
        fun onPiece(piece: String)
    }

    /**
     * One full turn: template the [messages] (role → text, oldest first) with the model's own chat
     * template, then decode, streaming each UTF-8-complete piece to [onDelta] **on the llm thread**
     * (the caller's sink must be thread-safe; AssistantController's delta buffer is).
     *
     * Returns 0 on success, 1 if stopped by the user, negative on an engine error.
     */
    suspend fun generate(
        messages: List<Pair<String, String>>,
        // PHASE 4: raw encoded image bytes (png/jpeg/webp) for THIS turn. Used only when the
        // resident model has a projector (see supportsVision); otherwise silently ignored so the
        // reply still happens as text — a missing mmproj must degrade, never dead-end.
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
            // The media markers go INSIDE the last user message, before its text, so after
            // templating each image lands exactly where every multimodal chat template expects
            // media: in the user turn. mtmd_tokenize then replaces each marker with that image's
            // embedding chunks, in order.
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
                    // -30/-31 = projector missing at native level or the image didn't decode.
                    // Retry the same turn as plain text rather than failing it: the words the
                    // user typed still deserve an answer.
                    if (rc == -30 || rc == -31) nativeGenerate(h, prompt, MAX_NEW_TOKENS, cb) else rc
                } else {
                    nativeGenerate(h, prompt, MAX_NEW_TOKENS, cb)
                }
            } catch (t: Throwable) {
                // Distinct from the native side's own -3 (empty tokens): -20 means the native call
                // itself threw into Java (e.g. a missing symbol), which is a different problem to chase.
                Log.e("LocalLlm", "nativeGenerate threw", t)
                -20
            }
        } finally {
            generating.set(false)
        }
    }

    /** Ask a running generation to stop after the current token. Callable from any thread. */
    fun stop() {
        val h = handle
        if (h != 0L) try {
            nativeStop(h)
        } catch (_: Throwable) {
        }
    }

    /**
     * Free the model and all native memory. Called when the user leaves the app (MainActivity
     * finishing) and when the model file is deleted/replaced in Settings. Runs on the llm thread;
     * an in-flight generation is stopped first, then freed once it has actually let go.
     */
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

    // ---- Native surface (lucent_llama.cpp) ----
    private external fun nativeLoad(path: String, nCtx: Int, nThreads: Int, nGpuLayers: Int): Long
    private external fun nativeChatPrompt(handle: Long, roles: Array<String>, texts: Array<String>, addAssistant: Boolean): String
    private external fun nativeGenerate(handle: Long, prompt: String, maxNew: Int, callback: PieceCallback): Int
    private external fun nativeStop(handle: Long)
    private external fun nativeUnload(handle: Long)
}
