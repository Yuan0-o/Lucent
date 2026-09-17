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
 *  - **No crashes reach the app the user is looking at.** A llama context is not thread-safe; a
 *    single serial executor makes concurrent access impossible by construction (the native layer
 *    holds a mutex too, as a second belt). Every native call is also wrapped so a Java-level
 *    failure becomes a return code, never an uncaught exception — and as of P3-2, the native
 *    library itself is loaded only in an isolated process (see below), so even a failure a
 *    try/catch cannot intercept at all (a signal, an OOM-kill) cannot take the UI down with it.
 *  - **Memory released on exit.** [shutdown] frees sampler, context, and model, and MainActivity
 *    calls it when the activity is finishing — so the moment the user actually leaves the app the
 *    gigabytes come back. (If the OS kills the process instead, the kernel reclaims them anyway.)
 *
 * Simplicity is deliberate: no tools, no cross-conversation memory, no KV-cache reuse between
 * turns. Each generation receives a self-contained prompt (recent turns of the current chat) and
 * starts from a clean context. Fluency was named the highest priority, and a stateless turn can
 * never be desynchronized, never leak context, and never hit the "cache poisoned, output garbage"
 * class of bug.
 *
 * ## P3-2 — process isolation
 *
 * Before P3-2, all of the above ran directly in the app's main process: an OOM or a bad Vulkan
 * driver took the whole app down mid-edit. As of P3-2, this object leads a double life, and BOTH
 * halves are required to keep the exact public surface AssistantController / MainActivity /
 * SettingsScreen already call completely unchanged (none of those call sites were touched):
 *
 *  - **In the main (UI) process**, every function in the section below marked "Public surface" —
 *    [isSupported], [generate], [ensureLoaded], [stop], [shutdown], etc. — forwards to
 *    [LocalLlmProxy], which talks over AIDL to [com.lucent.app.GenerationService] running in the
 *    isolated `":llm"` process declared in AndroidManifest.xml. [available] is `by lazy`
 *    specifically so that merely calling one of these proxy functions in the main process can
 *    never trigger `System.loadLibrary` there — the native engine is loaded ONLY inside the
 *    isolated process, on purpose, so a fault in it cannot reach the process the UI runs in.
 *  - **In the isolated process**, [com.lucent.app.GenerationService]'s AIDL stub calls the
 *    `engineXxx` functions in the section below directly, in-process. Those functions — and the
 *    private state above them, and the `native` declarations at the very bottom — are BYTE-FOR-BYTE
 *    the original pre-P3-2 implementation (renamed and re-visibilitied only, logic untouched),
 *    because moving them, or the `private external fun` declarations, to a different class would
 *    silently break the JNI binding: `lucent_llama.cpp`'s native methods are compiled against the
 *    literal symbol names `Java_com_lucent_app_local_LocalLlm_nativeXxx`, so this exact
 *    package-and-class must keep hosting them, in this exact file, forever (or until the native
 *    side is rebuilt to match a rename — well outside this task's scope).
 *
 * The split between the two halves is enforced STRUCTURALLY, not by asking the OS at runtime
 * "which process am I in" (that would need `Application.getProcessName()`/an `ActivityManager`
 * fallback and would be one more thing to get wrong): the `engineXxx` functions are called from
 * exactly one place, [com.lucent.app.GenerationService]'s binder stub, and that Service only ever
 * runs in the isolated process — guaranteed by its manifest declaration, not by a heuristic.
 * Nothing else in this codebase calls them, and nothing should.
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

    // ====================================================================================
    // Public surface — UNCHANGED signatures. Called from AssistantController (shared/ui, off
    // limits for this task), MainActivity, and SettingsScreen. Every one of these now runs in the
    // MAIN process only and forwards to LocalLlmProxy; none of them touch native code directly.
    // ====================================================================================

    /**
     * Whether the native engine was packaged for this ABI at all. As of P3-2 this is answered
     * without loading the native library or doing any IPC — see [LocalLlmProxy.isSupported].
     */
    fun isSupported(): Boolean = LocalLlmProxy.isSupported()

    /**
     * PHASE 4: whether the resident model can see images — i.e. an mmproj was imported for the
     * active slot AND the projector loaded successfully. False whenever no model is resident.
     * As of P3-2 this reads a cache on the main-process proxy, refreshed each time [ensureLoaded]
     * completes — see [LocalLlmProxy.supportsVision].
     */
    fun supportsVision(): Boolean = LocalLlmProxy.supportsVision()

    /**
     * W-1 (desktop): whether "unsupported" specifically means the CPU lacks AVX2. On Android the
     * engine ships per-ABI, so this reason cannot occur — the constant keeps the shared UI files
     * (AssistantController is a twin) identical across trees while each platform answers truthfully.
     * Untouched by P3-2: this never depended on the engine being resident or in-process.
     */
    fun unsupportedBecauseCpuLacksAvx2(): Boolean = false

    /**
     * Apply the user's CPU/GPU choice. Called from the assistant before a local turn. Only records
     * the desired backend; the next [ensureLoaded] notices the mismatch and reloads the model cleanly
     * on the chosen backend. Cheap and idempotent when nothing changes.
     *
     * DELIBERATE CONTRACT — a flip made WHILE a reply is generating is silent and deferred: the
     * in-flight reply keeps the backend it started on, and the change takes effect from the next
     * reply. As of P3-2 this is upheld WITHOUT an extra IPC round trip: [LocalLlmProxy] caches the
     * bool locally and folds it into the single [ensureLoaded] call's own parameter, so there is no
     * separate "set" message that could arrive out of order relative to a load already in flight.
     * See [LocalLlmProxy.setGpuEnabled] / [LocalLlmProxy.ensureLoaded].
     */
    fun setGpuEnabled(enabled: Boolean) = LocalLlmProxy.setGpuEnabled(enabled)

    /** True while a model is resident in memory (best-effort cache on the proxy side — see
     *  [LocalLlmProxy.isLoaded] — kept for API parity; nothing in this codebase currently reads it). */
    fun isLoaded(): Boolean = LocalLlmProxy.isLoaded()

    /** True while a model is being loaded into memory (used to show a "loading…" state). */
    fun isLoading(): Boolean = LocalLlmProxy.isLoading()

    /** True while a local generation is in flight (used by Stop). */
    fun isGenerating(): Boolean = LocalLlmProxy.isGenerating()

    /**
     * Make sure the imported model is loaded, loading it if needed. Safe to call every send: a
     * second call with the same file AND the same backend is a fast no-op (still one IPC round
     * trip to ask, but the isolated process itself answers instantly without touching llama.cpp
     * again). A changed file or a flipped CPU/GPU choice swaps cleanly. If a GPU load fails (e.g. a
     * flaky Vulkan driver), it transparently retries on CPU rather than failing — GPU is opt-in, so
     * it must never be able to take the whole feature down. Returns false when there is no model,
     * the ABI is unsupported, even the CPU load failed, OR (new in P3-2) the isolated process could
     * not be reached at all — AssistantController already treats every false the same way (a
     * generic "couldn't load the model" message), so this needed no change on the caller's side.
     */
    suspend fun ensureLoaded(context: Context): Boolean = LocalLlmProxy.ensureLoaded(context)

    /**
     * One full turn: template the [messages] (role → text, oldest first) with the model's own chat
     * template, then decode, streaming each UTF-8-complete piece to [onDelta]. Before P3-2 this ran
     * on the llm thread in-process; as of P3-2 it still does — just inside the isolated process —
     * and [onDelta] is instead invoked from an AIDL callback arriving on a Binder pool thread in
     * THIS process. AssistantController's own doc already requires its delta sink to be
     * thread-safe (not specifically single-thread-confined), so this is a compatible narrowing, not
     * a new requirement; see [LocalLlmProxy] for the full design and its "what this does NOT catch"
     * section for the one honest gap (a hung-but-not-crashed isolated process mid-generation).
     *
     * Returns 0 on success, 1 if stopped by the user, negative on an engine error. The native
     * engine's own negative codes (roughly -1..-31) are unchanged by this task. Three new negative
     * codes exist for failures that can only happen now that a second process is involved at all —
     * see [LocalLlmProxy.RC_ENGINE_UNAVAILABLE], [LocalLlmProxy.RC_ENGINE_PROCESS_DIED],
     * [LocalLlmProxy.RC_IMAGE_STAGING_FAILED] — and [RC_ENGINE_GLUE_ERROR] below for a failure in
     * the new engine-hosting glue code itself, as opposed to the native call it wraps.
     */
    suspend fun generate(
        messages: List<Pair<String, String>>,
        // PHASE 4: raw encoded image bytes (png/jpeg/webp) for THIS turn. Used only when the
        // resident model has a projector (see supportsVision); otherwise silently ignored so the
        // reply still happens as text — a missing mmproj must degrade, never dead-end. As of P3-2
        // these bytes are staged to a shared cache file rather than sent inline over IPC — see
        // LocalLlmProxy's class doc for why (Binder's transaction buffer is small and shared).
        images: List<ByteArray> = emptyList(),
        onDelta: (String) -> Unit
    ): Int = LocalLlmProxy.generate(messages, images, onDelta)

    /** Ask a running generation to stop after the current token. Callable from any thread. */
    fun stop() = LocalLlmProxy.stop()

    /**
     * Free the model and all native memory. Called when the user leaves the app (MainActivity
     * finishing) and when the model file is deleted/replaced in Settings. As of P3-2 this is a
     * fire-and-forget request to the isolated process (matching the original's own fire-and-forget
     * `llmScope.launch`); this call does not itself tear down the IPC connection to that process —
     * see [LocalLlmProxy]'s class doc for why that is a deliberate choice, not an oversight.
     */
    fun shutdown() = LocalLlmProxy.shutdown()

    // ====================================================================================
    // ENGINE — runs ONLY inside the isolated ":llm" process, called ONLY by
    // com.lucent.app.GenerationService's AIDL stub. Everything from here down is the ORIGINAL
    // pre-P3-2 LocalLlm implementation. Changes from the original are limited to: (a) the public
    // functions above were renamed to `engineXxx` / made `internal`; (b) the former
    // `desiredGpuLayers` field is now a per-call parameter instead of separately-set mutable state
    // (LocalLlmProxy caches the user's choice and passes it into engineEnsureLoaded — see above);
    // (c) `available`, `llmDispatcher`, `llmScope` became `by lazy` so that simply referencing this
    // object from the public functions above (which happens in the MAIN process) can never trigger
    // a native library load or spin up the llm thread there — only actually reading one of these
    // three from `engineXxx` code (i.e. only from inside the isolated process) does. Everything
    // else below — the state, the load/generate/stop/shutdown logic, the thread counting, the
    // native surface — is unchanged.
    // ====================================================================================

    /** New in P3-2: the engine-hosting glue code (GenerationService's stub, or the mapping in
     *  [engineGenerate] between IPC parameters and the original call) threw, as opposed to the
     *  native call it wraps throwing (which is still -20, see [engineGenerate] below). */
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

    /** Engine-side answer to [isSupported] — reads [available], which only this call path ever
     *  forces to evaluate (see the `by lazy` note above). */
    internal fun engineIsSupported(): Boolean = available

    /** Engine-side answer to [supportsVision]. */
    internal fun engineSupportsVision(): Boolean = handle != 0L && visionReady

    // One thread for every native call, for the model's whole lifetime. `by lazy`: created only
    // when engine code first actually runs, i.e. only inside the isolated process.
    private val llmDispatcher: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "LucentLocalLlm") }.asCoroutineDispatcher()
    }
    private val llmScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + llmDispatcher) }

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

    // [loadedGpuLayers] is what the resident model was actually loaded with, so a change to the
    // setting triggers a clean reload on the next send. Unlike before P3-2, there is no
    // `desiredGpuLayers` FIELD any more — the caller (engineEnsureLoaded's `gpuEnabled` parameter,
    // sourced from LocalLlmProxy's own cache) supplies the desired backend on every call instead.
    @Volatile private var loadedGpuLayers: Int = 0

    /** True while a model is resident in memory. Not currently wired to any IPC method (nothing
     *  calls it) — kept for potential future use alongside [engineIsSupported] / [engineSupportsVision]. */
    internal fun engineIsLoaded(): Boolean = handle != 0L

    /** True while a model is being loaded into memory. Not currently wired to any IPC method. */
    internal fun engineIsLoading(): Boolean = loading.get()

    /** True while a local generation is in flight. Not currently wired to any IPC method. */
    internal fun engineIsGenerating(): Boolean = generating.get()

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
     * Engine-side implementation of [ensureLoaded]. `gpuEnabled` replaces the former
     * `desiredGpuLayers` field read — see the ENGINE section doc above — everything else in this
     * function's body is the original, unchanged.
     */
    internal suspend fun engineEnsureLoaded(context: Context, gpuEnabled: Boolean): Boolean =
        withContext(llmDispatcher) {
        if (!available) return@withContext false
        // Load whichever slot is ACTIVE. A different active slot than the resident one is what makes
        // "switch models" release the old and load the new: the id/path no longer match, so the block
        // below unloads first. Only one model is ever resident.
        val activeSlot = LocalModelStore.activeSlot(context) ?: return@withContext false
        val file = LocalModelStore.activeModelFile(context) ?: return@withContext false
        val wantGpu = if (gpuEnabled) GPU_OFFLOAD_ALL else 0
        if (handle != 0L &&
            loadedSlotId == activeSlot.id &&
            loadedPath == file.absolutePath &&
            loadedGpuLayers == wantGpu
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

    /** JNI streaming callback — the native side looks this method up by name and signature (see
     *  lucent_llama.cpp, which resolves it reflectively via GetObjectClass + GetMethodID rather
     *  than a hardcoded class name, so this interface is free to live anywhere in this file — it
     *  stays right next to the native declarations it exists for). */
    interface PieceCallback {
        fun onPiece(piece: String)
    }

    /**
     * Engine-side implementation of [generate] — the body below is byte-for-byte the original.
     * Called by GenerationService's AIDL stub with `onDelta` wired to relay each piece over the
     * `ILocalLlmCallback.onPiece` oneway call, and the final return value delivered via
     * `onDone(rc)`.
     */
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

    /** Engine-side implementation of [stop] — unchanged body. */
    internal fun engineStop() {
        val h = handle
        if (h != 0L) try {
            nativeStop(h)
        } catch (_: Throwable) {
        }
    }

    /** Engine-side implementation of [shutdown] — unchanged body. */
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

    // ---- Native surface (lucent_llama.cpp) — UNCHANGED. Must keep living in this exact
    // package+class: the compiled .so binds these by the literal symbol names
    // Java_com_lucent_app_local_LocalLlm_nativeXxx (verified against app/src/main/cpp/lucent_llama.cpp
    // before this task touched anything — there is no JNI_OnLoad/RegisterNatives in that file, so
    // the binding is name-based, not a runtime-registered table that could simply be re-pointed). ----
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
