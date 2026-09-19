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

internal object LocalLlmProxy {

    const val RC_ENGINE_UNAVAILABLE = -100
    const val RC_ENGINE_PROCESS_DIED = -101
    const val RC_IMAGE_STAGING_FAILED = -102

    private const val TAG = "LocalLlmProxy"

    private const val BIND_TIMEOUT_MS = 10_000L

    private const val IMAGE_CACHE_DIR = "local_llm_ipc_images"

    @Volatile private var engineStub: ILocalLlmEngine? = null
    @Volatile private var cachedGpuEnabled: Boolean = false
    @Volatile private var cachedIsLoaded: Boolean = false
    @Volatile private var cachedIsLoading: Boolean = false
    @Volatile private var cachedIsGenerating: Boolean = false
    @Volatile private var cachedSupportsVision: Boolean = false

    private val bindMutex = Mutex()

    private val generateMutex = Mutex()

    @Volatile private var connectWaiter: CancellableContinuation<Boolean>? = null
    private val pendingGenerate = AtomicReference<CancellableContinuation<Int>?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            engineStub = ILocalLlmEngine.Stub.asInterface(service)
            connectWaiter?.let { if (it.isActive) it.resume(true, onCancellation = null) }
            connectWaiter = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
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
        pendingGenerate.getAndSet(null)?.let { cont -> if (cont.isActive) cont.resume(rc, onCancellation = null) }
    }


    fun isSupported(): Boolean {
        val ctx = LocalLlmContextHolder.appContext
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
                            pendingGenerate.getAndSet(null)?.let { c -> if (c.isActive) c.resume(rc, onCancellation = null) }
                        }
                    }
                    try {
                        stub.generate(roles, texts, imagePaths, callback)
                    } catch (e: RemoteException) {
                        Log.e(TAG, "generate RPC failed", e)
                        pendingGenerate.getAndSet(null)?.let { c ->
                            if (c.isActive) c.resume(RC_ENGINE_PROCESS_DIED, onCancellation = null)
                        }
                    }
                }
            } finally {
                cachedIsGenerating = false
                imageFiles.forEach { it.delete() }
            }
        }
    }

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


    private suspend fun ensureBound(contextHint: Context?): ILocalLlmEngine? {
        engineStub?.let { return it }
        val appCtx = (contextHint ?: LocalLlmContextHolder.appContext)?.applicationContext
            ?: return null
        return bindMutex.withLock {
            engineStub?.let { return@withLock it }
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
                        cont.resume(false, onCancellation = null)
                    }
                    cont.invokeOnCancellation { connectWaiter = null }
                }
            } ?: false
            if (connected) engineStub else null
        }
    }

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
