package com.lucent.app.data

import android.content.Context
import com.lucent.app.network.ApiSpec
import com.lucent.app.network.LlmClient
import kotlinx.coroutines.flow.first

object EmbeddingProvider {

    sealed interface Outcome {
        data class Success(val vector: FloatArray, val model: String) : Outcome
        data class Unavailable(val reason: String) : Outcome
        data class Failed(val error: Throwable) : Outcome
    }

    private const val EMBEDDING_MODEL_OPENAI = "text-embedding-3-small"

    suspend fun embed(context: Context, text: String): Outcome {
        val repo = SettingsRepository(context)
        return if (repo.localModelEnabled.first()) {
            Outcome.Unavailable(
                "Local embedding generation isn't implemented in this build yet — see EmbeddingProvider.kt."
            )
        } else {
            embedCloud(repo, text)
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
