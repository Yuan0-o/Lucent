// P3-2 — Android local-model process isolation.
package com.lucent.app.local;

// Streaming callback for one local-model generation, delivered from the isolated engine process
// (com.lucent.app.GenerationService, see AndroidManifest.xml's android:process=":llm") back to
// the main process.
//
// The whole interface is declared `oneway`: the engine's single generation thread
// (LocalLlm's llmDispatcher, unchanged by this task) must never block waiting for the main
// process to drain these calls, and — this is the part worth being precise about, since it is
// exactly what keeps a streamed reply from arriving scrambled — the Android Binder driver
// guarantees that oneway calls made from the SAME calling thread to the SAME remote object are
// delivered and executed in that same order on the receiving side. LocalLlm's engine-side
// generate loop calls onPiece from one thread only (llmDispatcher), so token order survives the
// process boundary.
oneway interface ILocalLlmCallback {

    // One UTF-8-complete piece of the reply. Called many times per generation; never called after
    // onDone for the same generation.
    void onPiece(String piece);

    // The generation finished (or failed). rc mirrors the original, pre-P3-2 contract of
    // LocalLlm.generate(): 0 success, 1 stopped by the user (see LocalLlm.stop()), negative an
    // engine error. The native engine's own negative codes (roughly -1..-31; see the native
    // surface at the bottom of LocalLlm.kt and lucent_llama.cpp) are unchanged by this task.
    // LocalLlm.kt additionally defines RC_ENGINE_GLUE_ERROR (-21) for a failure in the new
    // engine-hosting code itself, and LocalLlmProxy.kt defines RC_ENGINE_UNAVAILABLE (-100),
    // RC_ENGINE_PROCESS_DIED (-101) and RC_IMAGE_STAGING_FAILED (-102) for failures that can only
    // happen now that a second process is involved at all.
    void onDone(int rc);
}
