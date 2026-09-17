package com.lucent.app.local

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.lucent.app.GenerationService
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * P3-2 — main-process side of Android local-model process isolation.
 *
 * Every function [LocalLlm]'s public API exposes now forwards here. This object binds to
 * [com.lucent.app.GenerationService], which — per `android:process=":llm"` in AndroidManifest.xml —
 * Android runs in its own process, separate from the UI/main one. The real llama.cpp engine (the
 * `engineXxx` functions at the bottom of LocalLlm.kt) only ever executes inside that process; this
 * object never touches them directly and never loads the native library, so a native fault there
 * (an OOM, a bad Vulkan driver — the exact problem P3-2 exists to contain) cannot bring down the
 * process this code runs in.
 *
 * ### How a crash surfaces here
 * [connection]'s `onServiceDisconnected` fires when the bound process dies for any reason —
 * killed by the system, or killed by a native fault the JNI layer's own try/catch cannot catch
 * (signals like SIGSEGV or an OOM-kill are not C++ exceptions). That callback resolves any
 * in-flight [generate] call with [RC_ENGINE_PROCESS_DIED] instead of leaving it hanging, and
 * clears the cached "loaded" state so the next turn cleanly re-loads instead of assuming a model
 * that is no longer there. [ensureBound] additionally wraps every direct call to the engine
 * ([RemoteException]) for the narrower race where the process dies between fetching the stub and
 * actually using it.
 *
 * ### What this does NOT catch
 * This defends against the isolated process dying outright. It does not detect a process that is
 * alive but wedged (a driver hang rather than a crash) beyond the fixed [BIND_TIMEOUT_MS] on the
 * very first connection — a hang partway through an already-connected generation has no timeout
 * here and would leave that one `generate()` call suspended until the user backs out. A follow-up
 * task could add a per-generation watchdog if that turns out to matter in practice; this task's
 * scope is crash isolation, not hang detection (see the delivery notes for more on this).
 *
 * ### Threading
 * [ensureLoaded] and [generate] always hop onto [Dispatchers.IO] themselves before making any
 * blocking or IPC call, regardless of what dispatcher the caller happens to be on — this class
 * makes no assumption about how AssistantController schedules its coroutines.
 */
internal object LocalLlmProxy {

    // New result codes generate() can return, layered below the native engine's own (roughly
    // -1..-31 — see LocalLlm.kt's native surface and lucent_llama.cpp) so nothing collides. These
    // three are the only failure modes P3-2 introduces that the native engine could not already
    // produce on its own; AssistantController shows them the same way it shows any other negative
    // rc — S.localModelGenerateFailed followed by "[$rc]" — so no shared code needed to change to
    // surface them.
    const val RC_ENGINE_UNAVAILABLE = -100   // could not bind/reach the isolated process at all
    const val RC_ENGINE_PROCESS_DIED = -101  // was connected, then the process died mid-call
    const val RC_IMAGE_STAGING_FAILED = -102 // couldn't stage an image to the shared cache file

    private const val TAG = "LocalLlmProxy"

    // Generous on purpose: this bounds process spawn + Service creation + onBind, not the model
    // load itself (ensureLoaded's own blocking AIDL call has no separate timeout — a slow load is
    // expected and already surfaced as turn.loadingModel by AssistantController). Unverified
    // against a real cold-start device; see delivery notes.
    private const val BIND_TIMEOUT_MS = 10_000L

    private const val IMAGE_CACHE_DIR = "local_llm_ipc_images"

    @Volatile private var engineStub: ILocalLlmEngine? = null
    @Volatile private var cachedGpuEnabled: Boolean = false
    @Volatile private var cachedIsLoaded: Boolean = false
    @Volatile private var cachedIsLoading: Boolean = false
    @Volatile private var cachedIsGenerating: Boolean = false
    @Volatile private var cachedSupportsVision: Boolean = false

    // Guards the "not bound yet -> bind and await onServiceConnected" sequence so concurrent
    // callers don't race into bindService twice. Once engineStub is non-null every caller takes
    // the fast path above the lock.
    private val bindMutex = Mutex()

    // The original LocalLlm confined every native call to one single-thread dispatcher, which
    // made "one generation in flight at a time" true by construction. This mutex preserves that
    // exact behaviour now that generate() is a network of suspend calls rather than a single
    // dispatcher confinement.
    private val generateMutex = Mutex()

    @Volatile private var connectWaiter: CancellableContinuation<Boolean>? = null
    private val pendingGenerate = AtomicReference<CancellableContinuation<Int>?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            engineStub = ILocalLlmEngine.Stub.asInterface(service)
            connectWaiter?.let { if (it.isActive) it.resume(true) }
            connectWaiter = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Fires when the isolated process is gone for ANY reason — an OOM kill, a native
            // fault (Vulkan driver, allocator abort — signals a try/catch cannot intercept), or
            // the system reclaiming it under memory pressure all land here identically. This IS
            // the crash-isolation boundary the task exists to build: the main process observes a
            // clean callback instead of dying with it.
            Log.w(TAG, "GenerationService (isolated process) disconnected")
            engineStub = null
            cachedIsLoaded = false
            cachedIsLoading = false
            cachedSupportsVision = false
            failPendingGenerate(RC_ENGINE_PROCESS_DIED)
        }
    }

    private fun failPendingGenerate(rc: Int) {
        cachedIsGenerating = false
        pendingGenerate.getAndSet(null)?.let { cont -> if (cont.isActive) cont.resume(rc) }
    }

    // ====================================================================================
    // Public surface mirrored 1:1 by LocalLlm.kt's thin delegators.
    // ====================================================================================

    /**
     * Whether the native engine was packaged for this ABI at all — answered WITHOUT touching IPC
     * or the native library, by checking whether liblucent_llama.so exists in this install's
     * nativeLibraryDir. This is a deliberate, slightly more precise re-reading of the original
     * doc's own words ("whether the native engine was packaged for this ABI"), which the original
     * implementation only approximated by attempting a real dlopen. The one behavioural gap: a
     * present-but-corrupted .so file now reads as "supported" until an actual load is attempted,
     * where before dlopen would have caught it immediately. Given corrupted-but-present native
     * libraries are not a realistic failure mode for a CI-built APK, this trade is deliberate, not
     * an oversight — see the delivery notes.
     */
    fun isSupported(): Boolean {
        val ctx = LocalLlmContextHolder.appContext
            // Should not be reachable in practice (see LocalLlmContextHolder's class doc) — if it
            // ever is, fail open rather than silently hiding a feature that likely works fine.
            ?: return true
        return try {
            File(ctx.applicationInfo.nativeLibraryDir, System.mapLibraryName("lucent_llama")).exists()
        } catch (t: Throwable) {
            true
        }
    }

    fun supportsVision(): Boolean = cachedSupportsVision
    fun isLoaded(): Boolean = cachedIsLoaded
    fun isLoading(): Boolean = cachedIsLoading
    fun isGenerating(): Boolean = cachedIsGenerating

    /**
     * Mirrors the original setGpuEnabled contract exactly: a setter and nothing else, applied by
     * the NEXT [ensureLoaded] call. No IPC round trip happens here at all — the flag rides along
     * as [ensureLoaded]'s own parameter, so a flip made while a reply streams still changes
     * nothing until the next turn, same as before.
     */
    fun setGpuEnabled(enabled: Boolean) {
        cachedGpuEnabled = enabled
    }

    suspend fun ensureLoaded(context: Context): Boolean = withContext(Dispatchers.IO) {
        cachedIsLoading = true
        try {
            val stub = ensureBound(context) ?: return@withContext false
            val loaded = try {
                stub.ensureLoaded(cachedGpuEnabled)
            } catch (e: RemoteException) {
                Log.e(TAG, "ensureLoaded RPC failed", e)
                false
            }
            cachedIsLoaded = loaded
            cachedSupportsVision = if (loaded) {
                try {
                    stub.supportsVision()
                } catch (e: RemoteException) {
                    false
                }
            } else {
                false
            }
            loaded
        } finally {
            cachedIsLoading = false
        }
    }

    suspend fun generate(
        messages: List<Pair<String, String>>,
        images: List<ByteArray>,
        onDelta: (String) -> Unit
    ): Int = generateMutex.withLock {
        withContext(Dispatchers.IO) {
            val stub = ensureBound(null) ?: return@withContext RC_ENGINE_UNAVAILABLE
            val imageFiles = try {
                stageImages(images)
            } catch (t: Throwable) {
                Log.e(TAG, "staging images for IPC failed", t)
                return@withContext RC_IMAGE_STAGING_FAILED
            }
            cachedIsGenerating = true
            try {
                val roles = Array(messages.size) { messages[it].first }
                val texts = Array(messages.size) { messages[it].second }
                val imagePaths = Array(imageFiles.size) { imageFiles[it].absolutePath }
                suspendCancellableCoroutine<Int> { cont ->
                    pendingGenerate.set(cont)
                    cont.invokeOnCancellation { pendingGenerate.compareAndSet(cont, null) }
                    val callback = object : ILocalLlmCallback.Stub() {
                        override fun onPiece(piece: String) {
                            onDelta(piece)
                        }
                        override fun onDone(rc: Int) {
                            pendingGenerate.getAndSet(null)?.let { c -> if (c.isActive) c.resume(rc) }
                        }
                    }
                    try {
                        stub.generate(roles, texts, imagePaths, callback)
                    } catch (e: RemoteException) {
                        Log.e(TAG, "generate RPC failed", e)
                        pendingGenerate.getAndSet(null)?.let { c ->
                            if (c.isActive) c.resume(RC_ENGINE_PROCESS_DIED)
                        }
                    }
                }
            } finally {
                cachedIsGenerating = false
                imageFiles.forEach { it.delete() }
            }
        }
    }

    /** Fire-and-forget, callable from any thread, exactly like the original — no bind attempt if
     *  nothing is currently bound, matching the original's "no-op if nothing loaded" behaviour. */
    fun stop() {
        val stub = engineStub ?: return
        try {
            stub.stop()
        } catch (e: RemoteException) {
            Log.w(TAG, "stop RPC failed (engine likely already gone)", e)
        }
    }

    fun shutdown() {
        cachedIsLoaded = false
        cachedSupportsVision = false
        val stub = engineStub ?: return
        try {
            stub.shutdown()
        } catch (e: RemoteException) {
            Log.w(TAG, "shutdown RPC failed (engine likely already gone)", e)
        }
    }

    // ====================================================================================
    // Binding / IPC plumbing
    // ====================================================================================

    /**
     * Returns the connected engine stub, binding (and waiting for onServiceConnected) the first
     * time this is called. The connection, once established, is deliberately never torn down by
     * this class — see the class doc: it rides with the main process's own lifetime, protected
     * during an active generation by GenerationService's existing foreground-service start/stop,
     * and otherwise left to Android's normal background-process management, exactly as the
     * single-process app was before this task (nothing here is worse than what backgrounding the
     * whole app already risked).
     */
    private suspend fun ensureBound(contextHint: Context?): ILocalLlmEngine? {
        engineStub?.let { return it }
        val appCtx = (contextHint ?: LocalLlmContextHolder.appContext)?.applicationContext
            ?: return null
        return bindMutex.withLock {
            engineStub?.let { return@withLock it } // re-check: someone may have bound while we waited
            val connected = withTimeoutOrNull(BIND_TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    connectWaiter = cont
                    val requested = try {
                        appCtx.bindService(
                            Intent(appCtx, GenerationService::class.java),
                            connection,
                            Context.BIND_AUTO_CREATE
                        )
                    } catch (t: Throwable) {
                        Log.e(TAG, "bindService threw", t)
                        false
                    }
                    if (!requested) {
                        connectWaiter = null
                        cont.resume(false)
                    }
                    cont.invokeOnCancellation { connectWaiter = null }
                }
            } ?: false
            if (connected) engineStub else null
        }
    }

    /**
     * Writes each image's bytes to its own file under a private cache subdirectory and returns
     * the files written. On failure partway through, whatever was already written is deleted
     * before rethrowing, so a bad write never leaks a stray file — [generate] itself deletes the
     * full returned list once the call resolves, success or not.
     */
    private fun stageImages(images: List<ByteArray>): List<File> {
        if (images.isEmpty()) return emptyList()
        val ctx = LocalLlmContextHolder.appContext?.applicationContext
            ?: throw IllegalStateException("LocalLlmContextHolder has no context yet")
        val dir = File(ctx.cacheDir, IMAGE_CACHE_DIR).apply { mkdirs() }
        val written = mutableListOf<File>()
        try {
            images.forEach { bytes ->
                val f = File(dir, "${UUID.randomUUID()}.bin")
                f.writeBytes(bytes)
                written.add(f)
            }
            return written
        } catch (t: Throwable) {
            written.forEach { it.delete() }
            throw t
        }
    }
}
