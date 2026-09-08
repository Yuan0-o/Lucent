package com.lucent.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ToolAcc(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder(), var thoughtSignature: String? = null)

object LlmClient {

    // retryOnConnectionFailure lets OkHttp transparently re-establish a dropped socket (common on
    // mobile as the radio flips between wifi and cellular), and the call-level retry below adds a
    // couple of backed-off attempts on top for timeouts and resets. Together they are the "fix the
    // underlying connection stability" half of issue 19; the modal is the other half.
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // Call-level retry for transient network faults. HTTP errors are never retried (a 401 won't fix
    // itself); only connectivity exceptions are.
    private const val MAX_ATTEMPTS = 3
    private val RETRY_BACKOFF_MS = longArrayOf(400L, 1200L)

    private fun isTransientNetwork(t: Throwable): Boolean {
        // The controller-facing error is wrapped in ApiNetworkException; the retry decision cares
        // about the original cause.
        val e = if (t is ApiNetworkException) (t.cause ?: t) else t
        return when (e) {
            is java.net.SocketTimeoutException,
            is java.io.InterruptedIOException,
            is java.net.ConnectException,
            is java.net.SocketException -> true
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
            val json = JSONObject(bodyStr)
            val dataArray = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
            val ids = mutableListOf<String>()
            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                var id = item.optString("id", item.optString("name", ""))
                if (spec == ApiSpec.GOOGLE) id = id.removePrefix("models/")
                if (id.isNotBlank()) ids.add(id)
            }
            Result.success(ids)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendChat(
        baseUrl: String, spec: ApiSpec, apiKey: String, model: String,
        history: List<ChatTurn>, systemPrompt: String, tools: List<ToolDefinition>
    ): Result<RawModelReply> = withContext(Dispatchers.IO) {
        try {
            val adapter = adapterFor(spec)
            val url = adapter.chatUrl(baseUrl, model, streaming = false)
            val body = adapter.buildBody(model, history, systemPrompt, tools, streaming = false)
            val requestBuilder = Request.Builder().url(url).post(body.toString().toRequestBody(JSON))
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

    suspend fun streamChat(
        baseUrl: String, spec: ApiSpec, apiKey: String, model: String,
        history: List<ChatTurn>, systemPrompt: String, tools: List<ToolDefinition>,
        onDelta: (String) -> Unit
    ): Result<RawModelReply> = withContext(Dispatchers.IO) {
        val adapter = adapterFor(spec)
        // Cooperative cancellation for the blocking SSE read below: cancelling this coroutine
        // cannot interrupt a socket read on its own, so without this poll a "stopped" stream
        // kept being consumed to its very end — pumping the rest of the old reply into the
        // caller's buffer long after the user had moved on to another conversation.
        val streamJob = coroutineContext[kotlinx.coroutines.Job]
        var attempt = 0
        while (true) {
            val acc = StreamAccumulator()

            val result: Result<RawModelReply> = try {
                val url = adapter.chatUrl(baseUrl, model, streaming = true)
                val body = adapter.buildBody(model, history, systemPrompt, tools, streaming = true)
                // Google's streaming endpoint is selected by URL; the others signal it in the body.
                if (spec != ApiSpec.GOOGLE) body.put("stream", true)

                val requestBuilder = Request.Builder().url(url).post(body.toString().toRequestBody(JSON))
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
                            streamBody(adapter, source, acc, onDelta,
                                isCancelled = { streamJob?.isActive == false })
                        } finally {
                            try { response.close() } catch (_: Throwable) {}
                        }
                        val toolCalls = acc.toolCalls(useAnthropicAcc = spec == ApiSpec.ANTHROPIC)
                        Result.success(RawModelReply(acc.fullText.toString(), toolCalls, acc.returnedImageMime, acc.returnedImageData))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(if (e is java.io.IOException) ApiNetworkException(e.message ?: "network error", e) else e)
            }

            val error = result.exceptionOrNull()
            val retryable = error != null &&
                error !is ApiHttpException &&
                acc.fullText.isEmpty() &&
                isTransientNetwork(error) &&
                attempt < MAX_ATTEMPTS - 1
            if (retryable) {
                kotlinx.coroutines.delay(RETRY_BACKOFF_MS[attempt.coerceAtMost(RETRY_BACKOFF_MS.size - 1)])
                attempt++
                continue
            }
            return@withContext result
        }
        @Suppress("UNREACHABLE_CODE")
        Result.failure(ApiNetworkException("unreachable", null))
    }

    /**
     * Consume the SSE stream for one attempt, delegating per-provider parsing to the adapter.
     */
    private fun streamBody(
        adapter: ProviderAdapter,
        source: okio.BufferedSource,
        acc: StreamAccumulator,
        onDelta: (String) -> Unit,
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
            val json = try { JSONObject(payload) } catch (e: Exception) { null } ?: continue
            adapter.parseStreamEvent(json, acc, onDelta)
        }
    }
}