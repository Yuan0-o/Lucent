package com.lucent.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.seconds

data class ToolAcc(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder(), var thoughtSignature: String? = null)

object LlmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30.seconds)
        .readTimeout(120.seconds)
        .callTimeout(180.seconds)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private const val MAX_ATTEMPTS = 3
    private val RETRY_BACKOFF_MS = longArrayOf(400L, 1200L)

    private fun isTransientNetwork(t: Throwable): Boolean {
        val e = if (t is ApiNetworkException) (t.cause ?: t) else t
        return when (e) {
            is okio.IOException,
            is okio.IOException,
            is okio.IOException,
            is okio.IOException -> true
            else -> false
        }
    }

    suspend fun fetchModels(baseUrl: String, spec: ApiSpec, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val adapter = adapterFor(spec)
            val url = adapter.modelsUrl(baseUrl)
            val requestBuilder = Request.Builder().url(url).get()
            adapter.addAuthHeaders(requestBuilder, apiKey)
            val response = client.newCall(requestBuilder.build()).execute()
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $bodyStr"))
            val json = Json.parseToJsonElement(bodyStr).jsonObject
            val dataArray = json["data"]?.jsonArray ?: json["models"]?.jsonArray ?: buildJsonArray {}
            val ids = mutableListOf<String>()
            for (i in 0 until dataArray.size) {
                val item = dataArray[i].jsonObject
                var id = item["id"]?.jsonPrimitive?.content ?: item["name"]?.jsonPrimitive?.content ?: ""
                if (spec == ApiSpec.GOOGLE) id = id.removePrefix("models/")
                if (id.isNotBlank()) ids.add(id)
            }
            Result.success(ids)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchEmbedding(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        model: String,
        text: String
    ): Result<FloatArray> = withContext(Dispatchers.IO) {
        if (spec != ApiSpec.OPENAI) {
            return@withContext Result.failure(
                UnsupportedOperationException("Cloud embeddings aren't implemented for $spec yet.")
            )
        }
        try {
            val url = baseUrl.trimEnd('/') + "/embeddings"
            val body = buildJsonObject { put("model", model); put("input", text) }
            val requestBuilder = Request.Builder().url(url).post(Json.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON))
            adapterFor(spec).addAuthHeaders(requestBuilder, apiKey)
            val response = client.newCall(requestBuilder.build()).execute()
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $bodyStr"))
            val json = Json.parseToJsonElement(bodyStr).jsonObject
            val embeddingJson = json["data"]!!.jsonArray[0].jsonObject["embedding"]!!.jsonArray
            val vec = FloatArray(embeddingJson.size) { i -> embeddingJson[i].jsonPrimitive.float }
            Result.success(vec)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendChat(
        baseUrl: String, spec: ApiSpec, apiKey: String, model: String,
        history: List<ChatTurn>, systemPrompt: String, tools: List<ToolDefinition>,
        reasoning: String = com.lucent.app.data.ReasoningEffort.DEFAULT.key,
        cacheKey: String = "",
        context: String = ""
    ): Result<RawModelReply> = withContext(Dispatchers.IO) {
        try {
            val adapter = adapterFor(spec)
            val url = adapter.chatUrl(baseUrl, model, streaming = false)
            val body = adapter.buildBody(
                model, history, systemPrompt, tools, streaming = false, reasoning = reasoning,
                provider = providerOf(spec, baseUrl), cacheKey = cacheKey, context = context
            )
            val requestBuilder = Request.Builder().url(url).post(Json.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON))
            adapter.addAuthHeaders(requestBuilder, apiKey)
            val response = client.newCall(requestBuilder.build()).execute()
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $bodyStr"))
            val reply = adapter.parseReply(bodyStr)
            Result.success(reply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun providerOf(spec: ApiSpec, baseUrl: String): String =
        com.lucent.app.data.ApiProviders.forRequest(spec.name.lowercase(), baseUrl)

    suspend fun streamChat(
        baseUrl: String, spec: ApiSpec, apiKey: String, model: String,
        history: List<ChatTurn>, systemPrompt: String, tools: List<ToolDefinition>,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit = {},
        onRetry: (Int) -> Unit = {},
        reasoning: String = com.lucent.app.data.ReasoningEffort.DEFAULT.key,
        cacheKey: String = "",
        context: String = ""
    ): Result<RawModelReply> = withContext(Dispatchers.IO) {
        val adapter = adapterFor(spec)
        val provider = providerOf(spec, baseUrl)
        val streamJob = coroutineContext[kotlinx.coroutines.Job]
        var attempt = 0
        while (true) {
            val acc = StreamAccumulator()

            val result: Result<RawModelReply> = try {
                val url = adapter.chatUrl(baseUrl, model, streaming = true)
                val bodyObj = adapter.buildBody(
                    model, history, systemPrompt, tools, streaming = true, reasoning = reasoning,
                    provider = provider, cacheKey = cacheKey, context = context
                )
                val body = if (spec != ApiSpec.GOOGLE) {
                    JsonObject(bodyObj + mapOf("stream" to JsonPrimitive(true)))
                } else bodyObj

                val requestBuilder = Request.Builder().url(url).post(Json.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON))
                adapter.addAuthHeaders(requestBuilder, apiKey)

                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    response.close()
                    Result.failure(ApiHttpException(response.code, err))
                } else {
                    val source = response.body?.source()
                    if (source == null) {
                        Result.failure(ApiNetworkException("Empty response body", null))
                    } else {
                        try {
                            streamBody(adapter, source, acc, onDelta, onReasoning,
                                isCancelled = { streamJob?.isActive == false })
                        } finally {
                            try { response.close() } catch (_: Throwable) {}
                        }
                        val toolCalls = acc.toolCalls(useAnthropicAcc = spec == ApiSpec.ANTHROPIC)
                        Result.success(
                            RawModelReply(
                                acc.fullText.toString(), toolCalls, acc.returnedImageMime, acc.returnedImageData,
                                acc.thinkingBlocksJson(), acc.fullReasoning.toString(), acc.usage
                            )
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(if (e is okio.IOException) ApiNetworkException(e.message ?: "network error", e) else e)
            }

            val error = result.exceptionOrNull()
            val retryable = error != null &&
                error !is ApiHttpException &&
                acc.fullText.isEmpty() &&
                isTransientNetwork(error) &&
                attempt < MAX_ATTEMPTS - 1
            if (retryable) {
                onRetry(attempt + 2)
                kotlinx.coroutines.delay(RETRY_BACKOFF_MS[attempt.coerceAtMost(RETRY_BACKOFF_MS.size - 1)])
                attempt++
                continue
            }
            return@withContext result
        }
        @Suppress("UNREACHABLE_CODE")
        Result.failure(ApiNetworkException("unreachable", null))
    }

    private fun streamBody(
        adapter: ProviderAdapter,
        source: okio.BufferedSource,
        acc: StreamAccumulator,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit,
        isCancelled: () -> Boolean = { false }
    ) {
        while (!source.exhausted()) {
            if (isCancelled()) {
                throw kotlinx.coroutines.CancellationException("stream cancelled")
            }
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty() || payload == "[DONE]") continue
            val json = try { Json.parseToJsonElement(payload).jsonObject } catch (e: Exception) { null } ?: continue
            adapter.parseStreamEvent(json, acc, onDelta, onReasoning)
        }
        flushContentRouting(acc, onDelta, onReasoning)
    }
}