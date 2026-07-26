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

    private val available: Boolean = try {
        System.loadLibrary("lucent_llama")
        true
    } catch (t: Throwable) {
        Log.e("LocalLlm", "native library missing: ${t.message}")
        false
    }

    /** Whether the native engine was packaged for this ABI at all. */
    fun isSupported(): Boolean = available

    /**
     * PHASE 4: whether the resident model can see images — i.e. an mmproj was imported for the
     * active slot AND the projector loaded successfully. False whenever no model is resident.
     */
    fun supportsVision(): Boolean = handle != 0L && visionReady

    /**
     * W-1 (desktop): whether "unsupported" specifically means the CPU lacks AVX2. On Android the
     * engine ships per-ABI, so this reason cannot occur — the constant keeps the shared UI files
     * (AssistantController is a twin) identical across trees while each platform answers truthfully.
     */
    fun unsupportedBecauseCpuLacksAvx2(): Boolean = false

    // One thread for every native call, for the model's whole lifetime.
    private val llmDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "LucentLocalLlm") }.asCoroutineDispatcher()
    private val llmScope = CoroutineScope(SupervisorJob() + llmDispatcher)

    @Volatile private var handle: Long = 0L
    @Volatile private var loadedPath: String? = null
    // The slot id of the resident model, so a switch to a different slot forces a clean reload even
    // if two slots ever shared a path (they don't today, but tracking the id makes the intent exact).
    @Volatile private var loadedSlotId: String? = null
    // PHASE 4: whether the ACTIVE model has a working multimodal projector loaded next to it.
    @Volatile private var visionReady: Boolean = false
    private val generating = AtomicBoolean(false)
    // True only while a model is actually being loaded into memory (the slow, multi-second first
    // load). The assistant reads this to show a distinct "loading the model…" line, so the wait is
    // visible and never looks like a hang — loading a multi-gigabyte model is expected to take time.
    private val loading = AtomicBoolean(false)

    /** Offload-all sentinel for GPU mode; llama.cpp keeps on CPU any layer the device can't take. */
    private const val GPU_OFFLOAD_ALL = 999

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
     * Threads for token generation (B-group task 10: "squeeze the processor").
     *
     * ### What was wrong with half-the-cores-capped-at-4
     *
     * The old rule was written for a phone and it is genuinely right about one thing: on a
     * big.LITTLE SoC, handing work to the little cores makes generation SLOWER, not faster, because
     * llama.cpp splits a matrix row-wise and then waits for the slowest thread. A little core
     * finishing last sets the pace for every token. So oversubscribing is a real trap and the cap
     * existed for a reason.
     *
     * But "half the cores" is a poor proxy for "the big cores", and the hard ceiling of 4 left
     * performance on the table everywhere the proxy was wrong:
     *
     *  - An 8-core phone with 4 big + 4 little cores got 4 threads: correct by accident.
     *  - A modern 8-core phone with 1 prime + 3 big + 4 little got 4: also fine.
     *  - A 12-core phone (2+4+6) got 4 when 6 would have been right.
     *  - A desktop with 16 identical performance cores got FOUR. That is not a big.LITTLE machine
     *    at all — there are no little cores to drag anything — and the app was using a quarter of
     *    a machine that could have used all of it. The Windows build shares this file, so the
     *    phone's safety rule was quietly throttling desktops.
     *
     * ### The rule now
     *
     * Count the cores that are actually FAST ([performanceCores]) and use those, floored at 2 and
     * capped at 8. The floor keeps a weak device usable; the cap is not about cores but about
     * diminishing returns — past ~8 threads llama.cpp spends more time synchronising per token
     * than it saves, and on a phone the extra heat throttles the whole SoC within a minute, which
     * makes sustained generation slower than a lower thread count would have been. Squeezing the
     * processor means running the fast cores flat out, not spawning threads that fight each other.
     */
    private fun threadCount(): Int = performanceCores().coerceIn(2, 8)

    /**
     * How many of this device's cores are "fast", i.e. worth giving a generation thread to.
     *
     * On Linux (which Android is) each CPU exposes its maximum clock through
     * `/sys/devices/system/cpu/cpuN/cpufreq/cpuinfo_max_freq`. Cores within 15% of the highest
     * value found are counted as the performance cluster — 15% because a prime core is typically
     * ~10% above the other big cores and must not split them off into a cluster of one, while a
     * little core sits 40-60% below and is never mistaken for a big one.
     *
     * Falls back to half the cores (the previous rule, which was a safe-if-pessimistic guess) when
     * the sysfs files are unreadable, which is the case on some hardened kernels and on every
     * non-Linux desktop. Cached: the topology cannot change while the process lives, and this is
     * called on the model-load path where an extra file walk is pure waste.
     */
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
        if (handle != 0L &&
            loadedSlotId == activeSlot.id &&
            loadedPath == file.absolutePath &&
            loadedGpuLayers == desiredGpuLayers
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
        val wantGpu = desiredGpuLayers
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
    // PHASE 4 — multimodal. All three degrade gracefully when the engine was built text-only.
    private external fun nativeMtmdLoad(handle: Long, mmprojPath: String, nThreads: Int): Boolean
    private external fun nativeMediaMarker(): String
    private external fun nativeGenerateWithImages(handle: Long, prompt: String, images: Array<ByteArray>, maxNew: Int, callback: PieceCallback): Int
    private external fun nativeChatPrompt(handle: Long, roles: Array<String>, texts: Array<String>, addAssistant: Boolean): String
    private external fun nativeGenerate(handle: Long, prompt: String, maxNew: Int, callback: PieceCallback): Int
    private external fun nativeStop(handle: Long)
    private external fun nativeUnload(handle: Long)
}
