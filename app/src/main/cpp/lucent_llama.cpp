// JNI bridge between Kotlin (local/LocalLlm.kt) and llama.cpp, pinned at tag b9888.
//
// Design rules, in order of importance (matching the task):
//   1. Never crash. Every entry point is wrapped; any native failure surfaces as a Java-side
//      error string or a 0/false return, never a SIGSEGV or an uncaught C++ exception.
//   2. Never block the UI. Kotlin calls everything here from one dedicated background thread
//      (LocalLlm's single-thread dispatcher); this file additionally serializes with a mutex so
//      even a misuse can't race the context.
//   3. Release everything. unload() frees sampler → context → model, and Kotlin calls it when
//      the user leaves the app, so the model's memory is returned the moment the app exits.
//
// Streaming is UTF-8-safe: llama tokens can end mid-codepoint (especially for CJK), so bytes are
// held back until they form complete UTF-8 sequences before being handed to Java — otherwise
// NewStringUTF would corrupt or reject the text and Chinese/Japanese/Korean output would garble.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

// PHASE 4 — multimodal (llama.cpp's official mtmd, tools/mtmd at the pinned b9888). The whole
// feature is compile-time optional: when the mtmd sources aren't wired in (LUCENT_NO_MTMD,
// see CMakeLists.txt), every entry point below degrades to "vision unavailable" and the text
// engine is byte-for-byte what it was — multimodal must never be the reason the engine fails
// to build or a text chat fails to run.
#ifndef LUCENT_NO_MTMD
#include "mtmd.h"
#include "mtmd-helper.h"

// ------------------------------------------------------------------------------------------------
// Helper-API adapter (first CI pass, Android job 2026-07-26).
//
// The pinned b9888 checkout's mtmd_helper_bitmap_init_from_buf is
//     mtmd_helper_bitmap_wrapper mtmd_helper_bitmap_init_from_buf(ctx, buf, len, bool placeholder)
// — four arguments, returning a small C wrapper struct — where earlier revisions returned the
// mtmd_bitmap* directly from three arguments. Written as overload-ranked templates so this file
// compiles against EITHER shape and extracts the pointer by whichever wrapper member exists:
// changing the llama.cpp pin someday must not make this call site the thing that breaks.
// ------------------------------------------------------------------------------------------------
template <int N> struct lucent_rank : lucent_rank<N - 1> {};
template <>      struct lucent_rank<0> {};

// A plain pointer passes straight through (the pre-wrapper API shape).
static inline mtmd_bitmap * lucent_unwrap_bitmap(mtmd_bitmap * p) { return p; }
// A wrapper yields whichever pointer member it actually declares.
template <typename W> static auto lucent_unwrap_impl(W & w, lucent_rank<3>) -> decltype(w.bitmap) { return w.bitmap; }
template <typename W> static auto lucent_unwrap_impl(W & w, lucent_rank<2>) -> decltype(w.bmp)    { return w.bmp; }
template <typename W> static auto lucent_unwrap_impl(W & w, lucent_rank<1>) -> decltype(w.ptr)    { return w.ptr; }
template <typename W> static auto lucent_unwrap_impl(W & w, lucent_rank<0>) -> decltype(w.data)   { return w.data; }
template <typename W> static inline mtmd_bitmap * lucent_unwrap_bitmap(W w) {
    return lucent_unwrap_impl(w, lucent_rank<3>{});
}

// Prefer the current 4-argument form (trailing bool = the header's "placeholder" flag, off — we
// always hand real image bytes); fall back to the older 3-argument form when that is what the
// header declares.
template <typename C> static auto lucent_from_buf_impl(C * c, const unsigned char * d, size_t n, lucent_rank<1>)
    -> decltype(lucent_unwrap_bitmap(mtmd_helper_bitmap_init_from_buf(c, d, n, false))) {
    return lucent_unwrap_bitmap(mtmd_helper_bitmap_init_from_buf(c, d, n, false));
}
template <typename C> static auto lucent_from_buf_impl(C * c, const unsigned char * d, size_t n, lucent_rank<0>)
    -> decltype(lucent_unwrap_bitmap(mtmd_helper_bitmap_init_from_buf(c, d, n))) {
    return lucent_unwrap_bitmap(mtmd_helper_bitmap_init_from_buf(c, d, n));
}
static inline mtmd_bitmap * lucent_bitmap_from_buf(mtmd_context * c, const unsigned char * d, size_t n) {
    return lucent_from_buf_impl(c, d, n, lucent_rank<1>{});
}
#endif

#define TAG "LucentLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Session {
    llama_model *   model   = nullptr;
    llama_context * ctx     = nullptr;
    llama_sampler * sampler = nullptr;
    const llama_vocab * vocab = nullptr;
    int             n_ctx   = 0;
    std::mutex      mutex;              // serializes generate/unload on this session
    std::atomic<bool> stop{false};      // set by nativeStop(); checked every token
    std::atomic<bool> busy{false};      // a generation is in flight
    // PHASE 4: the multimodal projector context (mtmd_context*), or null when this model has no
    // mmproj loaded. Owned by the session; freed in unload before the model it references.
    void *          mtmd    = nullptr;
};

std::once_flag g_backend_once;

void ensure_backend() {
    std::call_once(g_backend_once, [] {
        // Quiet llama.cpp's default stderr logging on release devices; keep errors.
        llama_log_set([](ggml_log_level level, const char * text, void *) {
            if (level >= GGML_LOG_LEVEL_ERROR) __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", text);
        }, nullptr);
        llama_backend_init();
    });
}

// How many trailing bytes of `s` form an *incomplete* UTF-8 sequence. Those bytes are held back
// until the next token completes them, so Java only ever receives valid UTF-8.
size_t utf8_incomplete_tail(const std::string & s) {
    size_t n = s.size();
    size_t look = n < 4 ? n : 4;
    for (size_t back = 1; back <= look; ++back) {
        unsigned char c = (unsigned char) s[n - back];
        if ((c & 0x80) == 0x00) return 0;                       // ASCII: complete
        if ((c & 0xC0) == 0xC0) {                               // a lead byte, `back-1` continuations follow
            size_t need = (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : (c & 0xF8) == 0xF0 ? 4 : 1;
            return back < need ? back : 0;                      // incomplete if fewer bytes than needed
        }
        // else: a continuation byte, keep scanning backwards for its lead
    }
    return 0;
}

// NewStringUTF requires *modified* UTF-8 and aborts the VM on invalid input on some devices.
// Building the String from a byte[] through the String(byte[], "UTF-8") constructor accepts any
// valid UTF-8 (including 4-byte emoji, which modified UTF-8 does not).
jstring to_jstring(JNIEnv * env, const std::string & s) {
    jbyteArray bytes = env->NewByteArray((jsize) s.size());
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, (jsize) s.size(), (const jbyte *) s.data());
    jclass cls = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "([BLjava/lang/String;)V");
    jstring enc = env->NewStringUTF("UTF-8");
    jstring out = (jstring) env->NewObject(cls, ctor, bytes, enc);
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(enc);
    env->DeleteLocalRef(cls);
    return out;
}

std::string from_jstring(JNIEnv * env, jstring js) {
    if (!js) return "";
    const char * chars = env->GetStringUTFChars(js, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(js, chars);
    return out;
}

std::string piece_of(const llama_vocab * vocab, llama_token tok) {
    char buf[256];
    int n = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, /*special=*/false);
    if (n <= 0) return "";
    return std::string(buf, (size_t) n);
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text, bool add_special) {
    int guess = (int) text.size() + 16;
    std::vector<llama_token> out((size_t) guess);
    int n = llama_tokenize(vocab, text.c_str(), (int) text.size(), out.data(), guess, add_special, /*parse_special=*/true);
    if (n < 0) {
        out.resize((size_t) -n);
        n = llama_tokenize(vocab, text.c_str(), (int) text.size(), out.data(), -n, add_special, /*parse_special=*/true);
    }
    if (n < 0) n = 0;
    out.resize((size_t) n);
    return out;
}

} // namespace

extern "C" {

// ---------------------------------------------------------------------------------------------
// nativeLoad(path, nCtx, nThreads) -> session handle, or 0 on failure
// ---------------------------------------------------------------------------------------------
JNIEXPORT jlong JNICALL
Java_com_lucent_app_local_LocalLlm_nativeLoad(JNIEnv * env, jobject, jstring jpath, jint n_ctx, jint n_threads, jint n_gpu_layers) {
    ensure_backend();
    // Diagnostic, logged once per load: how many compute backends and devices ggml actually
    // registered for this ABI. If this prints "dev=0" the build shipped no working backend and
    // nothing can ever decode — it's the one line that tells a broken *build* apart from a broken
    // *model*, without needing the phone in hand.
    LOGI("backends registered: reg=%zu dev=%zu gpu_offload=%d",
         ggml_backend_reg_count(), ggml_backend_dev_count(), (int) llama_supports_gpu_offload());
    const std::string path = from_jstring(env, jpath);

    auto * s = new (std::nothrow) Session();
    if (!s) return 0;

    try {
        llama_model_params mparams = llama_model_default_params();
        mparams.use_mmap = true;   // page the weights in lazily; cold RAM cost stays low

        // Layers to offload to the GPU, chosen by the user's CPU/GPU setting (0 = CPU, the safe
        // default; a large value like 999 = offload everything). This only does anything when the
        // build shipped a GPU backend (LUCENT_ENABLE_VULKAN in CMakeLists.txt): with no backend
        // compiled there is nothing to offload to, so the engine stays on CPU no matter the value —
        // which is exactly why the in-app GPU switch is always safe. On Android, GPU (Vulkan) offload
        // is the biggest source of device-specific crashes, so it is opt-in and warned about in the UI.
        mparams.n_gpu_layers = n_gpu_layers > 0 ? n_gpu_layers : 0;

        s->model = llama_model_load_from_file(path.c_str(), mparams);
        if (!s->model) { LOGE("model load failed: %s", path.c_str()); delete s; return 0; }
        s->vocab = llama_model_get_vocab(s->model);

        llama_context_params cparams = llama_context_default_params();
        cparams.n_ctx           = (uint32_t) (n_ctx > 0 ? n_ctx : 2048);
        cparams.n_batch         = 512;   // prompt is fed in 512-token slices; bounds per-call latency
        cparams.n_threads       = n_threads > 0 ? n_threads : 4;
        cparams.n_threads_batch = n_threads > 0 ? n_threads : 4;

        s->ctx = llama_init_from_model(s->model, cparams);
        if (!s->ctx) { LOGE("context init failed"); llama_model_free(s->model); delete s; return 0; }
        s->n_ctx = (int) llama_n_ctx(s->ctx);

        // Same sampling the cloud path uses (LlmClient: temperature 0.6, top_p 0.9) plus a mild
        // top-k, so switching between cloud and local doesn't change the assistant's character.
        llama_sampler_chain_params sp = llama_sampler_chain_default_params();
        sp.no_perf = true;
        s->sampler = llama_sampler_chain_init(sp);
        llama_sampler_chain_add(s->sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_top_p(0.90f, 1));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_temp(0.60f));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        char desc[256];
        llama_model_desc(s->model, desc, sizeof(desc));
        LOGI("loaded %s (n_ctx=%d, threads=%d, gpu_layers=%d, vocab=%d, n_ctx_train=%d, model=%s)",
             path.c_str(), s->n_ctx, (int) cparams.n_threads, (int) n_gpu_layers,
             (int) llama_vocab_n_tokens(s->vocab), (int) llama_model_n_ctx_train(s->model), desc);
        return (jlong) (intptr_t) s;
    } catch (...) {
        LOGE("exception during load");
        if (s->ctx) llama_free(s->ctx);
        if (s->model) llama_model_free(s->model);
        delete s;
        return 0;
    }
}

// ---------------------------------------------------------------------------------------------
// nativeChatPrompt(handle, roles[], texts[], addAssistant) -> templated prompt string
//
// Applies the model's own chat template (Qwen/Llama/Gemma/Phi/ChatML/...). If the model ships a
// template llama.cpp can't render, falls back to plain ChatML, which every instruct model of the
// last two years at least tolerates.
// ---------------------------------------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_com_lucent_app_local_LocalLlm_nativeChatPrompt(JNIEnv * env, jobject, jlong handle,
                                                    jobjectArray jroles, jobjectArray jtexts, jboolean add_assistant) {
    auto * s = (Session *) (intptr_t) handle;
    if (!s || !s->model) return to_jstring(env, "");

    jsize n = env->GetArrayLength(jroles);
    if (env->GetArrayLength(jtexts) < n) n = env->GetArrayLength(jtexts);

    std::vector<std::string> roles, texts;
    roles.reserve((size_t) n); texts.reserve((size_t) n);
    for (jsize i = 0; i < n; ++i) {
        auto jr = (jstring) env->GetObjectArrayElement(jroles, i);
        auto jt = (jstring) env->GetObjectArrayElement(jtexts, i);
        roles.push_back(from_jstring(env, jr));
        texts.push_back(from_jstring(env, jt));
        env->DeleteLocalRef(jr);
        env->DeleteLocalRef(jt);
    }

    std::vector<llama_chat_message> msgs((size_t) n);
    size_t total_chars = 0;
    for (jsize i = 0; i < n; ++i) {
        msgs[(size_t) i] = { roles[(size_t) i].c_str(), texts[(size_t) i].c_str() };
        total_chars += roles[(size_t) i].size() + texts[(size_t) i].size();
    }

    const char * tmpl = llama_model_chat_template(s->model, nullptr);
    std::string out;
    if (tmpl) {
        std::vector<char> buf(total_chars * 2 + 1024);
        int32_t len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), add_assistant, buf.data(), (int32_t) buf.size());
        if (len > (int32_t) buf.size()) {
            buf.resize((size_t) len + 1);
            len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), add_assistant, buf.data(), (int32_t) buf.size());
        }
        if (len > 0) out.assign(buf.data(), (size_t) len);
    }
    if (out.empty()) {
        // ChatML fallback — plain, predictable, and understood broadly.
        for (jsize i = 0; i < n; ++i) {
            out += "<|im_start|>" + roles[(size_t) i] + "\n" + texts[(size_t) i] + "<|im_end|>\n";
        }
        if (add_assistant) out += "<|im_start|>assistant\n";
    }
    return to_jstring(env, out);
}

// ---------------------------------------------------------------------------------------------
// nativeGenerate(handle, prompt, maxNew, callback) -> 0 ok / 1 stopped / negative = error
//
// Streams UTF-8-complete pieces to callback.onPiece(String). Each call starts from a clean
// context (llama_memory_clear): the *prompt itself* carries whatever history Kotlin decided to
// include, which keeps this function stateless, simple, and impossible to desynchronize.
// ---------------------------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_com_lucent_app_local_LocalLlm_nativeGenerate(JNIEnv * env, jobject, jlong handle,
                                                  jstring jprompt, jint max_new, jobject callback) {
    auto * s = (Session *) (intptr_t) handle;
    if (!s || !s->ctx) return -1;

    std::lock_guard<std::mutex> guard(s->mutex);
    s->busy.store(true);
    s->stop.store(false);

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onPiece = env->GetMethodID(cbClass, "onPiece", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cbClass);
    if (!onPiece) {
        // A failed lookup leaves a pending NoSuchMethodError in the env; clear it so this reports
        // the deterministic -2 instead of throwing back into Kotlin as a raw error (-20). Seen when
        // R8 renames the callback in a minified build — the keep rules in proguard-rules.pro prevent
        // that, and this keeps the failure mode diagnosable if the rules are ever lost again.
        if (env->ExceptionCheck()) env->ExceptionClear();
        LOGE("generate: callback has no onPiece(String) — check the LocalLlm proguard keep rules");
        s->busy.store(false);
        return -2;
    }

    int rc = 0;
    try {
        const std::string prompt = from_jstring(env, jprompt);
        if (!s->vocab) { LOGE("generate: vocab is null — model has no tokenizer"); s->busy.store(false); return -9; }
        LOGI("generate: prompt is %zu chars", prompt.size());
        std::vector<llama_token> tokens = tokenize(s->vocab, prompt, /*add_special=*/true);
        if (tokens.empty()) {
            LOGE("generate: tokenize returned 0 tokens for a %zu-char prompt — tokenizer/model mismatch", prompt.size());
            s->busy.store(false); return -3;
        }

        const int budget = max_new > 0 ? max_new : 512;
        // Keep the *tail* of an over-long prompt: the newest turns matter most, and truncating
        // instead of failing is what "it must not crash" means when a huge history shows up.
        const int max_prompt = s->n_ctx - budget - 8;
        if (max_prompt > 0 && (int) tokens.size() > max_prompt) {
            tokens.erase(tokens.begin(), tokens.end() - max_prompt);
        }

        llama_memory_clear(llama_get_memory(s->ctx), true);
        llama_sampler_reset(s->sampler);

        LOGI("generate: prompt=%zu tokens, budget=%d, n_ctx=%d", tokens.size(), (int) budget, s->n_ctx);

        // Explicit-batch decode — the production path. llama.h documents llama_batch_get_one as a
        // transition helper to be avoided; here we own the token/pos/seq_id/logits arrays outright,
        // advance the position counter ourselves, and mark for output only the token whose logits we
        // actually sample. One reusable batch of n_batch slots for the whole turn.
        const int n_batch = 512;
        llama_batch batch = llama_batch_init(n_batch, 0, 1);
        auto put = [&](llama_token id, llama_pos pos, bool want_logits) {
            const int k = batch.n_tokens;
            batch.token[k]     = id;
            batch.pos[k]       = pos;
            batch.n_seq_id[k]  = 1;
            batch.seq_id[k][0] = 0;
            batch.logits[k]    = want_logits ? 1 : 0;
            batch.n_tokens++;
        };

        llama_pos n_past = 0;

        // Prefill: feed the prompt in n_batch-sized slices. Only the last token of each slice is
        // marked for output (the very last one is what the first sample reads); the rest just fill
        // the KV cache.
        for (size_t i = 0; i < tokens.size(); ) {
            if (s->stop.load()) { llama_batch_free(batch); s->busy.store(false); return 1; }
            size_t end = std::min(i + (size_t) n_batch, tokens.size());
            batch.n_tokens = 0;
            for (size_t j = i; j < end; ++j) put(tokens[j], n_past++, j + 1 == end);
            int dec = llama_decode(s->ctx, batch);
            if (dec != 0) {
                LOGE("prompt decode failed: rc=%d at pos=%d (n_ctx=%d, batch=%d)", dec, (int) n_past, s->n_ctx, batch.n_tokens);
                llama_batch_free(batch); s->busy.store(false); return -4;
            }
            i = end;
        }

        std::string pending;   // bytes held back until they form complete UTF-8
        int produced = 0;
        llama_token tok;
        while (produced < budget && !s->stop.load()) {
            tok = llama_sampler_sample(s->sampler, s->ctx, -1);
            if (llama_vocab_is_eog(s->vocab, tok)) break;

            pending += piece_of(s->vocab, tok);
            size_t hold = utf8_incomplete_tail(pending);
            if (pending.size() > hold) {
                std::string ready = pending.substr(0, pending.size() - hold);
                pending.erase(0, pending.size() - hold);
                jstring jpiece = to_jstring(env, ready);
                if (jpiece) {
                    env->CallVoidMethod(callback, onPiece, jpiece);
                    env->DeleteLocalRef(jpiece);
                    if (env->ExceptionCheck()) { env->ExceptionClear(); s->stop.store(true); }
                }
            }

            batch.n_tokens = 0;
            put(tok, n_past++, true);
            int dec = llama_decode(s->ctx, batch);
            if (dec != 0) { LOGE("gen decode failed: rc=%d at pos=%d", dec, (int) n_past); rc = -5; break; }
            ++produced;
        }
        llama_batch_free(batch);

        // Stop takes precedence over the zero-tokens check: a Stop pressed while the prompt is
        // still prefilling (common — prefill takes seconds on big prompts) leaves produced == 0
        // with the stop flag set, and that is a QUIET user stop (rc 1), not an engine error. The
        // old order turned exactly that case into a spurious "-7 generate failed" banner.
        if (s->stop.load() && rc == 0) rc = 1;
        if (rc == 0 && produced == 0) {
            // Loaded and decoded cleanly, but the model emitted end-of-turn before any content —
            // almost always a chat-template mismatch for this specific model rather than an engine
            // fault. Reported with its own code so it doesn't masquerade as a decode failure.
            LOGE("generate produced 0 tokens (immediate EOG — check the chat template for this model)");
            rc = -7;
        }
    } catch (...) {
        LOGE("exception during generate");
        rc = -6;
    }

    s->busy.store(false);
    return rc;
}

JNIEXPORT void JNICALL
Java_com_lucent_app_local_LocalLlm_nativeStop(JNIEnv *, jobject, jlong handle) {
    auto * s = (Session *) (intptr_t) handle;
    if (s) s->stop.store(true);
}

JNIEXPORT void JNICALL
Java_com_lucent_app_local_LocalLlm_nativeUnload(JNIEnv *, jobject, jlong handle) {
    auto * s = (Session *) (intptr_t) handle;
    if (!s) return;
    s->stop.store(true);
    {
        // Wait for any in-flight generation to observe the stop flag and release the mutex, so
        // the context is never freed under a running decode.
        std::lock_guard<std::mutex> guard(s->mutex);
#ifndef LUCENT_NO_MTMD
        if (s->mtmd)    { mtmd_free((mtmd_context *) s->mtmd); s->mtmd = nullptr; }
#endif
        if (s->sampler) { llama_sampler_free(s->sampler); s->sampler = nullptr; }
        if (s->ctx)     { llama_free(s->ctx);             s->ctx = nullptr; }
        if (s->model)   { llama_model_free(s->model);     s->model = nullptr; }
    }
    delete s;
    LOGI("unloaded — model memory released");
}


// ---------------------------------------------------------------------------------------------
// PHASE 4 — multimodal entry points (all no-ops / graceful failures when LUCENT_NO_MTMD)
// ---------------------------------------------------------------------------------------------

// nativeMtmdLoad(handle, mmprojPath, nThreads) -> true when the projector is ready.
// The projector runs on CPU on purpose (use_gpu = false): on Android, GPU clip encoders are the
// crash-prone half of every multimodal report upstream, and encoding one image takes well under a
// second on CPU — the safe choice costs nothing a user can feel.
JNIEXPORT jboolean JNICALL
Java_com_lucent_app_local_LocalLlm_nativeMtmdLoad(JNIEnv * env, jobject, jlong handle, jstring jpath, jint n_threads) {
#ifdef LUCENT_NO_MTMD
    (void) env; (void) handle; (void) jpath; (void) n_threads;
    LOGE("mtmd: engine built without multimodal support");
    return JNI_FALSE;
#else
    auto * s = (Session *) (intptr_t) handle;
    if (!s || !s->model) return JNI_FALSE;
    std::lock_guard<std::mutex> guard(s->mutex);
    if (s->mtmd) { mtmd_free((mtmd_context *) s->mtmd); s->mtmd = nullptr; }
    const std::string path = from_jstring(env, jpath);
    try {
        mtmd_context_params mparams = mtmd_context_params_default();
        mparams.use_gpu    = false;
        mparams.n_threads  = n_threads > 0 ? n_threads : 4;
        mparams.print_timings = false;
        mtmd_context * mc = mtmd_init_from_file(path.c_str(), s->model, mparams);
        if (!mc) { LOGE("mtmd: projector load failed: %s", path.c_str()); return JNI_FALSE; }
        s->mtmd = mc;
        LOGI("mtmd: projector loaded (%s), vision=%d audio=%d",
             path.c_str(), (int) mtmd_support_vision(mc), (int) mtmd_support_audio(mc));
        return JNI_TRUE;
    } catch (...) {
        LOGE("mtmd: exception during projector load");
        return JNI_FALSE;
    }
#endif
}

// nativeMediaMarker() -> the marker string mtmd_tokenize expects at each media position in the
// prompt text. Kotlin splices it into the *user* message before templating, so the media lands
// inside the user turn where every multimodal chat template expects it.
JNIEXPORT jstring JNICALL
Java_com_lucent_app_local_LocalLlm_nativeMediaMarker(JNIEnv * env, jobject) {
#ifdef LUCENT_NO_MTMD
    return to_jstring(env, "");
#else
    return to_jstring(env, mtmd_default_marker());
#endif
}

// nativeGenerateWithImages(handle, prompt, images[][], maxNew, callback) -> same codes as
// nativeGenerate, plus -30 (no projector) and -31 (image decode/tokenize failed).
//
// A deliberate near-duplicate of nativeGenerate rather than a shared core: the text path is the
// proven, shipping path, and threading multimodal branches through it would put every text chat
// behind new code. The prefill differs (mtmd_helper_eval_chunks evaluates text AND image chunks,
// including the image's encoder pass); the sampling loop after it is the same shape on purpose.
JNIEXPORT jint JNICALL
Java_com_lucent_app_local_LocalLlm_nativeGenerateWithImages(JNIEnv * env, jobject, jlong handle,
                                                            jstring jprompt, jobjectArray jimages,
                                                            jint max_new, jobject callback) {
#ifdef LUCENT_NO_MTMD
    (void) env; (void) handle; (void) jprompt; (void) jimages; (void) max_new; (void) callback;
    return -30;
#else
    auto * s = (Session *) (intptr_t) handle;
    if (!s || !s->ctx) return -1;
    if (!s->mtmd) return -30;

    std::lock_guard<std::mutex> guard(s->mutex);
    s->busy.store(true);
    s->stop.store(false);
    auto * mctx = (mtmd_context *) s->mtmd;

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onPiece = env->GetMethodID(cbClass, "onPiece", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cbClass);
    if (!onPiece) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        s->busy.store(false);
        return -2;
    }

    int rc = 0;
    std::vector<mtmd_bitmap *> bitmaps;
    mtmd_input_chunks * chunks = nullptr;
    try {
        const std::string prompt = from_jstring(env, jprompt);

        // Decode every image byte[] (png/jpeg/webp/bmp — stb does the decoding inside the helper).
        const jsize n_img = env->GetArrayLength(jimages);
        for (jsize i = 0; i < n_img; ++i) {
            auto jarr = (jbyteArray) env->GetObjectArrayElement(jimages, i);
            if (!jarr) continue;
            const jsize len = env->GetArrayLength(jarr);
            std::vector<unsigned char> buf((size_t) len);
            env->GetByteArrayRegion(jarr, 0, len, (jbyte *) buf.data());
            env->DeleteLocalRef(jarr);
            mtmd_bitmap * bmp = lucent_bitmap_from_buf(mctx, buf.data(), buf.size());
            if (!bmp) { LOGE("mtmd: image %d failed to decode (%d bytes)", (int) i, (int) len); rc = -31; break; }
            bitmaps.push_back(bmp);
        }
        if (rc == 0 && bitmaps.empty()) rc = -31; // called with images, none usable

        if (rc == 0) {
            chunks = mtmd_input_chunks_init();
            mtmd_input_text text;
            text.text          = prompt.c_str();
            text.add_special   = true;
            text.parse_special = true;
            // mtmd_tokenize takes const mtmd_bitmap** (the log's exact words); the owning vector
            // stays non-const because mtmd_bitmap_free wants the mutable pointer back.
            std::vector<const mtmd_bitmap *> cbitmaps(bitmaps.begin(), bitmaps.end());
            int32_t tok = mtmd_tokenize(mctx, chunks, &text,
                                        cbitmaps.data(), cbitmaps.size());
            if (tok != 0) { LOGE("mtmd: tokenize failed rc=%d", (int) tok); rc = -31; }
        }

        if (rc == 0) {
            // Stateless, like the text path: clean memory, replay everything this turn needs.
            llama_memory_clear(llama_get_memory(s->ctx), true);
            llama_sampler_reset(s->sampler);

            llama_pos n_past = 0;
            int32_t ev = mtmd_helper_eval_chunks(mctx, s->ctx, chunks,
                                                 /*n_past*/ 0, /*seq_id*/ 0,
                                                 /*n_batch*/ 512, /*logits_last*/ true,
                                                 &n_past);
            if (ev != 0) {
                LOGE("mtmd: eval_chunks failed rc=%d", (int) ev);
                rc = -4;
            } else {
                const int budget = max_new > 0 ? max_new : 512;
                const int n_batch = 512;
                llama_batch batch = llama_batch_init(n_batch, 0, 1);
                auto put = [&](llama_token id, llama_pos pos, bool want_logits) {
                    const int k = batch.n_tokens;
                    batch.token[k]     = id;
                    batch.pos[k]       = pos;
                    batch.n_seq_id[k]  = 1;
                    batch.seq_id[k][0] = 0;
                    batch.logits[k]    = want_logits ? 1 : 0;
                    batch.n_tokens++;
                };

                std::string pending;
                int produced = 0;
                llama_token tok2;
                while (produced < budget && !s->stop.load()) {
                    tok2 = llama_sampler_sample(s->sampler, s->ctx, -1);
                    if (llama_vocab_is_eog(s->vocab, tok2)) break;

                    pending += piece_of(s->vocab, tok2);
                    size_t hold = utf8_incomplete_tail(pending);
                    if (pending.size() > hold) {
                        std::string ready = pending.substr(0, pending.size() - hold);
                        pending.erase(0, pending.size() - hold);
                        jstring jpiece = to_jstring(env, ready);
                        if (jpiece) {
                            env->CallVoidMethod(callback, onPiece, jpiece);
                            env->DeleteLocalRef(jpiece);
                            if (env->ExceptionCheck()) { env->ExceptionClear(); s->stop.store(true); }
                        }
                    }

                    batch.n_tokens = 0;
                    put(tok2, n_past++, true);
                    int dec = llama_decode(s->ctx, batch);
                    if (dec != 0) { LOGE("mtmd: gen decode failed rc=%d at pos=%d", dec, (int) n_past); rc = -5; break; }
                    ++produced;
                }
                llama_batch_free(batch);

                if (s->stop.load() && rc == 0) rc = 1;
                if (rc == 0 && produced == 0) rc = -7;
            }
        }
    } catch (...) {
        LOGE("mtmd: exception during multimodal generate");
        rc = -6;
    }

    if (chunks) mtmd_input_chunks_free(chunks);
    for (auto * b : bitmaps) mtmd_bitmap_free(b);
    s->busy.store(false);
    return rc;
#endif
}

} // extern "C"
