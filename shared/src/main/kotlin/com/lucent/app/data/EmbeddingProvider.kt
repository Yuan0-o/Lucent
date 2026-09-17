package com.lucent.app.data

import android.content.Context
import com.lucent.app.network.ApiSpec
import com.lucent.app.network.LlmClient
import kotlinx.coroutines.flow.first

/**
 * P2-2: decides *how* a note's text becomes a vector, and hands the result to [EmbeddingStore].
 * This is the one place that reads [SettingsRepository.embeddingProvider], so it is also the one
 * place responsible for the privacy promise behind that setting — "local" must never reach this
 * file's cloud branch, and "cloud" must never silently fall back to local (a fallback would defeat
 * the point of a user having explicitly chosen local for privacy, or explicitly chosen cloud
 * because local wasn't giving them the recall quality they wanted — either direction of silent
 * substitution is wrong, so both branches fail loudly into [Outcome.Unavailable]/[Outcome.Failed]
 * instead).
 *
 * **Local is not implemented yet.** [LocalLlm]'s native surface is deliberately confined to the
 * `:llm` process by P3-2 (see its class doc — "never trigger `System.loadLibrary`" outside it) and
 * currently exposes load/generate/stop/unload only. Adding local embedding for real means: one more
 * `ILocalLlmEngine.aidl` method, the matching `LocalLlmProxy`/`GenerationService` plumbing (P3-2's
 * own pattern is the template), and — the part nothing in this Kotlin-only environment can write or
 * verify — the actual llama.cpp-side embedding forward pass (`llama_set_embeddings` /
 * `llama_get_embeddings_seq` or equivalent, matched to the exact pinned llama.cpp commit). Until
 * that lands, the "local" branch below returns [Outcome.Unavailable] rather than pretending to work
 * or silently substituting cloud — see the class doc above for why silent substitution is the wrong
 * failure mode here specifically.
 *
 * **Cloud covers OpenAI-shaped endpoints only** — see [LlmClient.fetchEmbedding]'s own doc comment
 * for why Anthropic and Google aren't implemented alongside it yet, and [EMBEDDING_MODEL_OPENAI] for
 * why the model name is hard-coded rather than reusing the user's configured *chat* model (OpenAI's
 * embedding models are a separate model family; a chat model name isn't a valid embeddings request).
 */
object EmbeddingProvider {

    /** Why [embed] didn't return a vector, or did. [model] on success is what gets stored in
     *  [NoteEmbedding.model] — see that entity's own doc comment for why the model travels with the
     *  vector instead of being assumed from context. */
    sealed interface Outcome {
        data class Success(val vector: FloatArray, val model: String) : Outcome
        /** Nothing went wrong; this path simply isn't implemented/reachable yet. Not an error — a
         *  caller like the recall tool should say "not available", not "something failed". */
        data class Unavailable(val reason: String) : Outcome
        /** The path is implemented but this specific attempt failed (network error, bad API key, ...). */
        data class Failed(val error: Throwable) : Outcome
    }

    // OpenAI's smallest current embedding model — a deliberate default, not a placeholder: cheap
    // and fast enough to run per-note and per-query without the user noticing, and its output
    // dimensionality doesn't change, so switching to it later can't silently produce vectors of two
    // different sizes under the same model name. Revisit only alongside adding a real settings field
    // for it — hard-coding one sensible choice now is more honest than a half-built settings UI for
    // a value with nothing behind it yet.
    private const val EMBEDDING_MODEL_OPENAI = "text-embedding-3-small"

    /** [text] to a vector, per the user's current [SettingsRepository.embeddingProvider] choice. */
    suspend fun embed(context: Context, text: String): Outcome {
        val repo = SettingsRepository(context)
        return when (repo.embeddingProvider.first()) {
            "cloud" -> embedCloud(repo, text)
            else -> Outcome.Unavailable(
                "Local embedding generation isn't implemented in this build yet — see EmbeddingProvider.kt."
            )
        }
    }

    private suspend fun embedCloud(repo: SettingsRepository, text: String): Outcome {
        val specStr = repo.apiSpec.first()
        val spec = when (specStr) {
            "anthropic" -> ApiSpec.ANTHROPIC
            "google" -> ApiSpec.GOOGLE
            else -> ApiSpec.OPENAI
        }
        val baseUrl = repo.baseUrl.first()
        val apiKey = repo.apiKey.first()
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            return Outcome.Unavailable("No API base URL/key is configured for the assistant yet.")
        }
        val model = when (spec) {
            ApiSpec.OPENAI -> EMBEDDING_MODEL_OPENAI
            else -> return Outcome.Unavailable(
                "Cloud embeddings aren't implemented for $spec yet — only OpenAI-shaped endpoints are."
            )
        }
        return LlmClient.fetchEmbedding(baseUrl, spec, apiKey, model, text).fold(
            onSuccess = { vec -> Outcome.Success(vec, "cloud:openai:$model") },
            onFailure = { e -> Outcome.Failed(e) }
        )
    }
}
