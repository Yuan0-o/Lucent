package com.lucent.app.harness.mcp

import com.lucent.app.LucentBuild
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.harness.McpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class McpReply(
    val ok: Boolean = false,
    val result: JSONObject? = null,
    val error: String = "",
    val sessionId: String = "",
    val status: Int = 0
)

data class McpDiscovery(
    val tools: List<McpTool> = emptyList(),
    val error: String = ""
)

data class McpPrompts(
    val prompts: List<McpPromptInfo> = emptyList(),
    val error: String = ""
)

data class McpStatus(
    val ok: Boolean,
    val millis: Long,
    val detail: String = ""
)

data class McpText(
    val text: String = "",
    val error: String = ""
)

interface McpTransport {
    val id: String
    fun describe(server: McpServer): String
    suspend fun send(server: McpServer, method: String, params: JSONObject?, notification: Boolean): McpReply
    fun close(serverId: String)
}

fun mcpCommandLine(server: McpServer): List<String> {
    val parts = mutableListOf<String>()
    val command = server.command.trim()
    if (command.isNotEmpty()) parts.add(command)
    parts.addAll(splitArguments(server.arguments))
    return parts
}

private fun splitArguments(raw: String): List<String> {
    val out = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    for (ch in raw) {
        when {
            ch == '"' -> quoted = !quoted
            ch.isWhitespace() && !quoted -> {
                if (current.isNotEmpty()) {
                    out.add(current.toString())
                    current.setLength(0)
                }
            }
            else -> current.append(ch)
        }
    }
    if (current.isNotEmpty()) out.add(current.toString())
    return out
}

private const val TIMEOUT_SECONDS = 60
private const val TIMEOUT_MILLIS = TIMEOUT_SECONDS * 1000L
private const val TTL_MILLIS = 5 * 60 * 1000L
private const val MAX_PAGES = 20
private const val MAX_STDERR_LINES = 40

private const val ANDROID_STDIO =
    "MCP servers that run a local command (stdio) are not available on Android, because the app cannot " +
        "start arbitrary binaries. Use a server with a url instead, or install the termux plugin and expose " +
        "the server over HTTP."

private const val NO_LOCAL_PROCESS =
    "MCP servers that run a local command (stdio) are not available here, because this build could not " +
        "start a local process. Use a server with a url instead."

private val MCP_IDS = AtomicLong(0)

private fun nextMcpId(): Long = MCP_IDS.incrementAndGet()

private class HttpTransport : McpTransport {

    override val id = "http"

    private val json = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val sessions = ConcurrentHashMap<String, String>()
    private val ready = ConcurrentHashMap<String, Boolean>()
    private val handshake = Mutex()

    override fun describe(server: McpServer): String = "streamable http ${server.url}"

    override fun close(serverId: String) {
        if (serverId.isBlank()) {
            sessions.clear()
            ready.clear()
            return
        }
        sessions.remove(serverId)
        ready.remove(serverId)
    }

    override suspend fun send(
        server: McpServer,
        method: String,
        params: JSONObject?,
        notification: Boolean
    ): McpReply {
        if (notification) {
            return post(server, McpProtocol.notification(method, params), 0L)
        }
        val opened = ensureReady(server)
        if (!opened.ok) return opened
        val id = nextMcpId()
        var reply = post(server, McpProtocol.request(id, method, params), id)
        if (reply.status == 404) {
            close(server.id)
            val again = ensureReady(server)
            if (!again.ok) return again
            val retryId = nextMcpId()
            reply = post(server, McpProtocol.request(retryId, method, params), retryId)
        }
        return reply
    }

    private suspend fun ensureReady(server: McpServer): McpReply {
        if (ready[server.id] == true) return McpReply(ok = true)
        return handshake.withLock {
            if (ready[server.id] == true) return@withLock McpReply(ok = true)
            val id = nextMcpId()
            val reply = post(
                server,
                McpProtocol.request(id, McpProtocol.INITIALIZE, McpProtocol.initializeParams()),
                id
            )
            if (!reply.ok) {
                return@withLock McpReply(
                    ok = false,
                    error = "The MCP handshake with \"${server.name}\" failed: ${reply.error}"
                )
            }
            if (reply.sessionId.isNotBlank()) sessions[server.id] = reply.sessionId
            val note = post(server, McpProtocol.notification(McpProtocol.INITIALIZED), 0L)
            if (!note.ok) {
                return@withLock McpReply(
                    ok = false,
                    error = "The MCP handshake with \"${server.name}\" failed: ${note.error}"
                )
            }
            ready[server.id] = true
            McpReply(ok = true, result = reply.result, sessionId = reply.sessionId)
        }
    }

    private suspend fun post(server: McpServer, payload: String, expectId: Long): McpReply =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(server.url)
                .header("Content-Type", McpProtocol.CONTENT_JSON)
                .header("Accept", "${McpProtocol.CONTENT_JSON}, ${McpProtocol.CONTENT_SSE}")
                .header("MCP-Protocol-Version", McpProtocol.VERSION)
                .header("User-Agent", "${McpProtocol.CLIENT_NAME}/${LucentBuild.VERSION}")
                .post(payload.toRequestBody(json))
            if (server.token.isNotBlank()) builder.header("Authorization", "Bearer ${server.token}")
            sessions[server.id]?.let { builder.header("Mcp-Session-Id", it) }
            try {
                client.newCall(builder.build()).execute().use { response ->
                    val status = response.code
                    val sessionId = response.header("Mcp-Session-Id").orEmpty()
                    val body = response.body?.string().orEmpty()
                    when {
                        status == 202 -> McpReply(ok = true, sessionId = sessionId, status = status)
                        status == 404 -> McpReply(
                            ok = false,
                            error = "The MCP server \"${server.name}\" dropped the session (HTTP 404).",
                            sessionId = sessionId,
                            status = status
                        )
                        status == 401 || status == 403 -> McpReply(
                            ok = false,
                            error = "The MCP server \"${server.name}\" refused the request (HTTP $status). " +
                                "Check the access token in Settings.",
                            sessionId = sessionId,
                            status = status
                        )
                        status !in 200..299 -> McpReply(
                            ok = false,
                            error = "The MCP server \"${server.name}\" answered HTTP $status${snippet(body)}",
                            sessionId = sessionId,
                            status = status
                        )
                        body.isBlank() -> McpReply(ok = true, sessionId = sessionId, status = status)
                        else -> readReply(server, response.header("Content-Type").orEmpty(), body, expectId, sessionId, status)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                McpReply(
                    ok = false,
                    error = "Could not reach the MCP server at ${server.url}: ${t.message ?: t::class.simpleName}"
                )
            }
        }

    private fun readReply(
        server: McpServer,
        contentType: String,
        body: String,
        expectId: Long,
        sessionId: String,
        status: Int
    ): McpReply {
        if (expectId == 0L) return McpReply(ok = true, sessionId = sessionId, status = status)
        val messages = McpProtocol.messages(body, contentType)
        val reply = McpProtocol.pick(messages, expectId) ?: return McpReply(
            ok = false,
            error = "The MCP server \"${server.name}\" sent no reply for request $expectId${snippet(body)}",
            sessionId = sessionId,
            status = status
        )
        val error = McpProtocol.errorOf(reply)
        if (error.isNotBlank()) {
            return McpReply(ok = false, error = error, sessionId = sessionId, status = status)
        }
        return McpReply(ok = true, result = McpProtocol.resultOf(reply), sessionId = sessionId, status = status)
    }

    private fun snippet(body: String): String {
        val clean = body.trim().replace(Regex("\\s+"), " ")
        return if (clean.isEmpty()) "" else ": ${clean.take(300)}"
    }
}

private class StdioTransport : McpTransport {

    override val id = "stdio"

    private val sessions = ConcurrentHashMap<String, StdioSession>()

    @Volatile private var usable: Boolean? = null

    override fun describe(server: McpServer): String = "stdio ${mcpCommandLine(server).joinToString(" ")}"

    override fun close(serverId: String) {
        if (serverId.isBlank()) {
            sessions.values.forEach { it.stop() }
            sessions.clear()
            return
        }
        val session = sessions.remove(serverId)
        session?.stop()
    }

    override suspend fun send(
        server: McpServer,
        method: String,
        params: JSONObject?,
        notification: Boolean
    ): McpReply {
        val opened = session(server)
        val live = opened.session ?: return McpReply(ok = false, error = opened.error)
        return live.roundTrip(method, params, notification)
    }

    private fun session(server: McpServer): Opened {
        val existing = sessions[server.id]
        if (existing != null) {
            if (existing.alive) return Opened(session = existing)
            sessions.remove(server.id)
            existing.stop()
        }
        val problem = blocked()
        if (problem.isNotBlank()) return Opened(error = problem)
        return synchronized(sessions) { locked(server) }
    }

    private fun locked(server: McpServer): Opened {
        val raced = sessions[server.id]
        if (raced != null && raced.alive) return Opened(session = raced)
        val started = start(server)
        started.session?.let { sessions[server.id] = it }
        return started
    }

    private fun blocked(): String {
        if (HarnessRuntime.android) return ANDROID_STDIO
        if (!probe()) return NO_LOCAL_PROCESS
        return ""
    }

    private fun probe(): Boolean {
        usable?.let { return it }
        val answer = try {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val probe = if (os.contains("win")) {
                listOf("cmd", "/c", "exit")
            } else {
                listOf("/bin/sh", "-c", "exit")
            }
            val process = ProcessBuilder(probe).start()
            process.waitFor(5, TimeUnit.SECONDS)
            process.destroy()
            true
        } catch (t: Throwable) {
            false
        }
        usable = answer
        return answer
    }

    private fun start(server: McpServer): Opened {
        val parts = mcpCommandLine(server)
        if (parts.isEmpty()) {
            return Opened(error = "The MCP server \"${server.name}\" has no command configured.")
        }
        return try {
            val builder = ProcessBuilder(parts)
            val workspace = HarnessRuntime.workspace()
            if (workspace.isDirectory) builder.directory(workspace)
            Opened(session = StdioSession(server, builder.start()))
        } catch (t: Throwable) {
            Opened(
                error = "Could not start the MCP server \"${server.name}\" " +
                    "(${parts.joinToString(" ")}): ${t.message ?: t::class.simpleName}. " +
                    "Use a server with a url instead."
            )
        }
    }

    private class Opened(val session: StdioSession? = null, val error: String = "")

    private class StdioSession(private val server: McpServer, private val process: Process) {

        private val stdin: BufferedWriter =
            BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))

        private val stdout = LinkedBlockingQueue<String>()

        private val stderr = ArrayDeque<String>()

        private val stderrLock = Any()

        private val lock = Mutex()

        @Volatile private var ready = false

        val alive: Boolean get() = process.isAlive

        init {
            pump(BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))) { line ->
                stdout.put(line)
            }
            pump(BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8))) { line ->
                synchronized(stderrLock) {
                    stderr.addLast(line)
                    while (stderr.size > MAX_STDERR_LINES) stderr.removeFirst()
                }
            }
        }

        private fun pump(reader: BufferedReader, sink: (String) -> Unit) {
            val thread = Thread {
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        sink(line)
                    }
                } catch (_: Throwable) {
                }
            }
            thread.isDaemon = true
            thread.name = "lucent-mcp-${server.id}"
            thread.start()
        }

        suspend fun roundTrip(method: String, params: JSONObject?, notification: Boolean): McpReply =
            lock.withLock {
                if (notification) {
                    val failure = write(McpProtocol.notification(method, params))
                    return@withLock if (failure.isBlank()) McpReply(ok = true) else McpReply(ok = false, error = failure)
                }
                if (!ready) {
                    val handshake = exchange(McpProtocol.INITIALIZE, McpProtocol.initializeParams())
                    if (!handshake.ok) return@withLock handshake
                    val failure = write(McpProtocol.notification(McpProtocol.INITIALIZED))
                    if (failure.isNotBlank()) return@withLock McpReply(ok = false, error = failure)
                    ready = true
                }
                exchange(method, params)
            }

        private suspend fun exchange(method: String, params: JSONObject?): McpReply {
            val id = nextMcpId()
            val failure = write(McpProtocol.request(id, method, params))
            if (failure.isNotBlank()) return McpReply(ok = false, error = failure)
            val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    return McpReply(
                        ok = false,
                        error = "The MCP server \"${server.name}\" did not answer $method within " +
                            "$TIMEOUT_SECONDS seconds.${tail()}"
                    )
                }
                if (!process.isAlive && stdout.isEmpty()) {
                    return McpReply(
                        ok = false,
                        error = "The MCP server \"${server.name}\" stopped (exit code ${exitCode()}).${tail()}"
                    )
                }
                val line = try {
                    stdout.poll(remaining.coerceAtMost(500L), TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    null
                } ?: continue
                val message = McpProtocol.parse(line) ?: continue
                if (McpProtocol.idOf(message) != id) continue
                val error = McpProtocol.errorOf(message)
                if (error.isNotBlank()) return McpReply(ok = false, error = error)
                return McpReply(ok = true, result = McpProtocol.resultOf(message))
            }
        }

        private fun write(payload: String): String = try {
            stdin.write(payload)
            stdin.newLine()
            stdin.flush()
            ""
        } catch (t: Throwable) {
            "Could not write to the MCP server \"${server.name}\": ${t.message ?: t::class.simpleName}.${tail()}"
        }

        private fun exitCode(): Int = try {
            process.exitValue()
        } catch (t: Throwable) {
            -1
        }

        private fun tail(): String {
            val recent = synchronized(stderrLock) { stderr.toList() }
            if (recent.isEmpty()) return ""
            return " The server said: " + recent.takeLast(8).joinToString(" ").take(400)
        }

        fun stop() {
            try {
                stdin.close()
            } catch (_: Throwable) {
            }
            try {
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
            } catch (_: Throwable) {
            }
        }
    }
}

object McpTransports {

    val http: McpTransport = HttpTransport()

    val stdio: McpTransport = StdioTransport()

    fun of(server: McpServer): McpTransport = if (server.url.isNotBlank()) http else stdio

    fun describe(server: McpServer): String = when {
        server.url.isNotBlank() -> "streamable http"
        server.command.isNotBlank() -> "stdio"
        else -> "not configured"
    }
}

object McpSessions {

    private class Entry(val tools: List<McpTool>, val at: Long)

    private val cached = ConcurrentHashMap<String, Entry>()

    private val failures = ConcurrentHashMap<String, String>()

    private val locks = HashMap<String, Mutex>()

    private val http: McpTransport = McpTransports.http

    private val stdio: McpTransport = McpTransports.stdio

    fun remember(serverId: String, tools: List<McpTool>) {
        if (serverId.isBlank()) return
        cached[serverId] = Entry(tools, System.currentTimeMillis())
        failures.remove(serverId)
    }

    fun cachedTools(serverId: String): List<McpTool> = cached[serverId]?.tools.orEmpty()

    fun lastError(serverId: String): String = failures[serverId].orEmpty()

    fun transportName(server: McpServer): String = McpTransports.describe(server)

    fun endpoint(server: McpServer): String = when {
        server.url.isNotBlank() -> server.url
        server.command.isNotBlank() -> mcpCommandLine(server).joinToString(" ")
        else -> "no url or command yet"
    }

    suspend fun tools(server: McpServer, force: Boolean = false): List<McpTool> =
        discovery(server, force).tools

    suspend fun exchange(server: McpServer, method: String, params: JSONObject? = null): McpReply {
        val problem = unavailable(server)
        if (problem.isNotBlank()) return McpReply(ok = false, error = problem)
        return try {
            McpTransports.of(server).send(server, method, params, false)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            McpReply(
                ok = false,
                error = "The MCP server \"${server.name}\" failed: ${t.message ?: t::class.simpleName}"
            )
        }
    }

    suspend fun discovery(server: McpServer, force: Boolean = false): McpDiscovery {
        val problem = unavailable(server)
        if (problem.isNotBlank()) {
            failures[server.id] = problem
            return McpDiscovery(error = problem)
        }
        if (!force) fresh(server.id)?.let { return McpDiscovery(tools = it.tools) }
        return try {
            lockFor(server.id).withLock {
                if (!force) fresh(server.id)?.let { return@withLock McpDiscovery(tools = it.tools) }
                val collected = mutableListOf<McpTool>()
                var cursor = ""
                var error = ""
                var truncated = false
                for (page in 0 until MAX_PAGES) {
                    val reply = exchange(server, McpProtocol.TOOLS_LIST, McpProtocol.listParams(cursor))
                    if (!reply.ok) {
                        error = reply.error
                        break
                    }
                    val result = reply.result
                    if (result == null) {
                        error = "The MCP server \"${server.name}\" sent an empty tools/list reply."
                        break
                    }
                    collected.addAll(McpProtocol.decodeTools(server.id, result))
                    cursor = McpProtocol.nextCursor(result)
                    if (cursor.isBlank()) break
                    if (page == MAX_PAGES - 1) truncated = true
                }
                if (error.isNotBlank()) {
                    failures[server.id] = error
                    cached.remove(server.id)
                    return@withLock McpDiscovery(error = error)
                }
                val note = if (truncated) {
                    "The tool list of \"${server.name}\" was truncated after $MAX_PAGES pages."
                } else {
                    ""
                }
                cached[server.id] = Entry(collected, System.currentTimeMillis())
                if (note.isBlank()) failures.remove(server.id) else failures[server.id] = note
                McpDiscovery(tools = collected, error = note)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val message = "Discovering tools on \"${server.name}\" failed: ${t.message ?: t::class.simpleName}"
            failures[server.id] = message
            McpDiscovery(error = message)
        }
    }

    suspend fun call(server: McpServer, tool: String, argumentsJson: String): McpResult {
        val name = tool.trim()
        if (name.isEmpty()) {
            return McpResult("No MCP tool name was given.", emptyList(), true)
        }
        val arguments = try {
            val raw = argumentsJson.trim()
            if (raw.isEmpty()) JSONObject() else JSONObject(raw)
        } catch (e: Exception) {
            return McpResult(
                "The arguments for $name must be a JSON object, for example {\"query\": \"rain\"}.",
                emptyList(),
                true
            )
        }
        val reply = exchange(server, McpProtocol.TOOLS_CALL, McpProtocol.callParams(name, arguments))
        if (!reply.ok) return McpResult(reply.error, emptyList(), true)
        val result = reply.result ?: return McpResult(
            "The MCP server \"${server.name}\" sent an empty reply for $name.",
            emptyList(),
            true
        )
        return McpProtocol.decodeResult(result)
    }

    suspend fun readResource(server: McpServer, uri: String): McpText {
        val clean = uri.trim()
        if (clean.isEmpty()) return McpText(error = "No resource uri was given.")
        val reply = exchange(server, McpProtocol.RESOURCES_READ, McpProtocol.readParams(clean))
        if (!reply.ok) return McpText(error = reply.error)
        val result = reply.result ?: return McpText(
            error = "The MCP server \"${server.name}\" sent an empty resources/read reply."
        )
        val text = McpProtocol.decodeResourceContents(result)
        return McpText(text = text.ifBlank { "The resource \"$clean\" came back empty." })
    }

    suspend fun resources(server: McpServer): McpText {
        val reply = exchange(server, McpProtocol.RESOURCES_LIST, null)
        if (!reply.ok) return McpText(error = reply.error)
        val result = reply.result ?: return McpText(
            error = "The MCP server \"${server.name}\" sent an empty resources/list reply."
        )
        val items = McpProtocol.decodeResources(result)
        if (items.isEmpty()) return McpText(text = "The server exposes no resources.")
        val lines = items.map { item ->
            val label = if (item.name.isBlank()) item.uri else "${item.name} — ${item.uri}"
            val mime = if (item.mimeType.isBlank()) "" else " (${item.mimeType})"
            val note = if (item.description.isBlank()) "" else ": ${item.description}"
            "- $label$mime$note"
        }
        return McpText(text = lines.joinToString("\n"))
    }

    suspend fun prompts(server: McpServer): McpPrompts {
        return try {
            val collected = mutableListOf<McpPromptInfo>()
            var cursor = ""
            var error = ""
            var truncated = false
            for (page in 0 until MAX_PAGES) {
                val reply = exchange(server, McpProtocol.PROMPTS_LIST, McpProtocol.listParams(cursor))
                if (!reply.ok) {
                    error = reply.error
                    break
                }
                val result = reply.result
                if (result == null) {
                    error = "The MCP server \"${server.name}\" sent an empty prompts/list reply."
                    break
                }
                collected.addAll(McpProtocol.decodePrompts(result))
                cursor = McpProtocol.nextCursor(result)
                if (cursor.isBlank()) break
                if (page == MAX_PAGES - 1) truncated = true
            }
            val note = if (truncated) "The prompt list of \"${server.name}\" was truncated after $MAX_PAGES pages." else ""
            if (error.isNotBlank()) McpPrompts(error = error) else McpPrompts(prompts = collected, error = note)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            McpPrompts(error = "Listing prompts on \"${server.name}\" failed: ${t.message ?: t::class.simpleName}")
        }
    }

    suspend fun ping(server: McpServer): McpStatus {
        val started = System.currentTimeMillis()
        val reply = exchange(server, McpProtocol.PING)
        val millis = System.currentTimeMillis() - started
        return McpStatus(ok = reply.ok, millis = millis, detail = reply.error)
    }

    fun invalidate(serverId: String = "") {
        if (serverId.isBlank()) {
            cached.clear()
            failures.clear()
            http.close("")
            stdio.close("")
            return
        }
        cached.remove(serverId)
        failures.remove(serverId)
        http.close(serverId)
        stdio.close(serverId)
    }

    suspend fun close() {
        withContext(Dispatchers.IO) { invalidate("") }
    }

    private fun unavailable(server: McpServer): String = when {
        !server.enabled -> "The MCP server \"${server.name}\" is switched off in Settings."
        server.url.isBlank() && server.command.isBlank() ->
            "The MCP server \"${server.name}\" is not configured yet: add a url (streamable http) or a " +
                "command (stdio)."
        else -> ""
    }

    private fun fresh(serverId: String): Entry? {
        val entry = cached[serverId] ?: return null
        return if (System.currentTimeMillis() - entry.at < TTL_MILLIS) entry else null
    }

    private fun lockFor(serverId: String): Mutex = synchronized(locks) {
        locks[serverId] ?: Mutex().also { locks[serverId] = it }
    }
}
