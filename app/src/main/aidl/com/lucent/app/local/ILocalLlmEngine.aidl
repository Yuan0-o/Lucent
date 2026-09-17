// P3-2 — Android local-model process isolation.
package com.lucent.app.local;

import com.lucent.app.local.ILocalLlmCallback;

// The isolated engine process's RPC surface. Hosted by com.lucent.app.GenerationService, which
// runs in the ":llm" process declared in AndroidManifest.xml; called only by LocalLlmProxy.kt in
// the main process. See LocalLlm.kt's class doc for why the JNI-bound engine logic had to stay in
// that exact class rather than move to a differently-named one.
//
// isSupported() is deliberately NOT part of this interface — LocalLlmProxy answers it locally
// (checking whether liblucent_llama.so exists in this ABI's nativeLibraryDir) precisely so that
// asking "is local mode available" never has to spin up the isolated process at all.
interface ILocalLlmEngine {

    // Mirrors LocalLlm.ensureLoaded(context): loads the active LocalModelStore slot if it is not
    // already resident with a matching backend. Blocking (NOT oneway) — a cold load of a
    // multi-gigabyte model can take several real seconds — so LocalLlmProxy always calls this off
    // the main thread (Dispatchers.IO). gpuEnabled folds in what LocalLlm.setGpuEnabled(...) used
    // to record as separate mutable state, so no extra round trip is needed for it: see
    // LocalLlmProxy.kt for how the "flip mid-reply is silently deferred to the next turn"
    // contract is preserved across the process boundary.
    boolean ensureLoaded(boolean gpuEnabled);

    // Mirrors LocalLlm.supportsVision(): whether the currently-resident model has a working
    // multimodal projector loaded. Cheap (reads already-resident state, does no engine work) —
    // call right after a successful ensureLoaded to refresh the main process's cached answer,
    // since LocalLlm.supportsVision() itself must stay a synchronous, non-suspend call.
    boolean supportsVision();

    // Starts one generation and returns immediately (oneway) — the result streams back through
    // callback.onPiece/onDone on the SAME calling (llmDispatcher) thread, which is what keeps
    // pieces in order (see ILocalLlmCallback's doc).
    //
    // roles/texts are parallel arrays, oldest message first (mirrors LocalLlm.generate's
    // `messages: List<Pair<String,String>>`; AIDL has no tuple/pair type, so this is the
    // marshalling-friendly equivalent — GenerationService's stub zips them back into pairs).
    //
    // imagePaths are paths to files under the shared app cache directory, NOT inline image bytes.
    // This is a deliberate choice, not an oversight: Binder's per-process transaction buffer is
    // shared and only about 1MB, and a `TransactionTooLargeException` is a real risk once photos
    // are involved (this app's local-vision feature accepts raw png/jpeg/webp bytes, easily
    // several hundred KB to a few MB each). LocalLlmProxy stages each image to
    // context.cacheDir/local_llm_ipc_images/<uuid>.bin before calling generate, and deletes the
    // files again once the call resolves; GenerationService's stub reads them back into byte[]
    // before handing them to LocalLlm.engineGenerate exactly as before.
    oneway void generate(in String[] roles, in String[] texts, in String[] imagePaths,
                         in ILocalLlmCallback callback);

    // Mirrors LocalLlm.stop(): ask a running generation to stop after the current token.
    // Fire-and-forget, exactly like the original (callable from any thread, no confirmation).
    oneway void stop();

    // Mirrors LocalLlm.shutdown(): free the resident model's native memory. Fire-and-forget, like
    // the original — LocalLlmProxy does not wait for this to finish before returning.
    oneway void shutdown();
}
