package com.lucent.app.harness

import com.lucent.app.harness.mcp.McpProtocol
import com.lucent.app.harness.mcp.McpResult
import com.lucent.app.harness.mcp.McpSessions
import com.lucent.app.harness.mcp.McpTool
import com.lucent.app.network.ToolExecResult
import com.lucent.app.network.ToolImage
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object McpTools : HarnessGroupTools {

    const val DYNAMIC_PREFIX = "mcp__"

    private const val NAME_LIMIT = 64

    private val dynamicNames = ConcurrentHashMap<String, Pair<String, String>>()

    override val group = HarnessGroup.MCP

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "mcp_servers",
            group = group,
            permission = HarnessPermission.READ,
            description = "List the MCP servers configured in Settings: each server's id, its transport " +
                "(streamable http or stdio), whether it is enabled, how many tools were discovered and the last " +
                "connection error. Call this first when an MCP server looks missing or broken, or before using " +
                "mcp_tools, mcp_call or mcp_read_resource. Access tokens are never shown."
        ),
        HarnessTool(
            name = "mcp_tools",
            group = group,
            permission = HarnessPermission.READ,
            description = "List the tools an MCP server offers, for one server id or for every enabled server when " +
                "server is left out. Each entry shows the server's own tool name, what it does and its input " +
                "schema. Pass that name unchanged to mcp_call with the same server id. If a tool is missing here, " +
                "the server did not advertise it."
        ),
        HarnessTool(
            name = "mcp_call",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Call a tool on an MCP server. Give the server id, the tool's own name exactly as " +
                "mcp_tools reports it (these are the server's names, not Lucent names), and arguments as a JSON " +
                "object matching the tool's input schema, for example {\"query\": \"rain\"}. Returns the text the " +
                "server sent, says when the server flagged an error, and shows any images it returned.",
            params = listOf(
                HarnessSchema.text("server", "The id of the MCP server, as mcp_servers shows it"),
                HarnessSchema.text("tool", "The server's own tool name, exactly as mcp_tools reports it"),
                HarnessSchema.json("arguments", "Arguments as a JSON object, e.g. {\"query\": \"rain\"}", false)
            )
        ),
        HarnessTool(
            name = "mcp_read_resource",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Read a resource (a file, document or record) from an MCP server by its uri, for servers " +
                "that expose resources. Pass the server id and the uri exactly as the server reports it, for " +
                "example file:///notes/todo.md. Returns the resource contents as text; binary resources are " +
                "reported without their bytes.",
            params = listOf(
                HarnessSchema.text("server", "The id of the MCP server, as mcp_servers shows it"),
                HarnessSchema.text("uri", "The resource uri, exactly as the server reports it")
            )
        ),
        HarnessTool(
            name = "mcp_prompts",
            group = group,
            permission = HarnessPermission.READ,
            description = "List the prompt templates one MCP server publishes, or every enabled server when server " +
                "is left out. Each entry shows the prompt's name, what it is for and the arguments it takes. This " +
                "only lists them; write the prompt yourself, or call a tool on the same server with mcp_call when " +
                "it exposes one.",
            params = listOf(
                HarnessSchema.text("server", "The id of one MCP server; leave out for every enabled server", false)
            )
        ),
        HarnessTool(
            name = "mcp_session",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Manage the MCP connections. action \"close\" drops every cached MCP session so the next " +
                "call reconnects from scratch; use it after changing a server's settings or when its tools look " +
                "stale. action \"ping\" checks every enabled server and reports how long each took to answer. Any " +
                "other action is refused.",
            params = listOf(
                HarnessSchema.text("action", "Either \"close\" or \"ping\"")
            )
        )
    )

    override fun canHandle(name: String): Boolean =
        super<HarnessGroupTools>.canHandle(name) || dynamicNames.containsKey(name) || parseDynamic(name) != null

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "mcp_servers" -> listServers(ctx)
        "mcp_tools" -> listTools(ctx, args)
        "mcp_call" -> callTool(ctx, args)
        "mcp_read_resource" -> readResourceTool(ctx, args)
        "mcp_prompts" -> listPrompts(ctx, args)
        "mcp_session" -> manageSession(ctx, args)
        else -> dynamicToolCall(ctx, name, args)
    }

    suspend fun dynamicTools(): List<HarnessTool> {
        val out = mutableListOf<HarnessTool>()
        val index = mutableMapOf<String, Pair<String, String>>()
        for (server in HarnessRuntime.config().mcpServers) {
            if (!server.enabled) continue
            val discovery = McpSessions.discovery(server)
            for (tool in discovery.tools) {
                val dynamic = dynamicTool(server, tool)
                index[dynamic.name] = server.id to tool.name
                out.add(dynamic)
            }
        }
        dynamicNames.clear()
        dynamicNames.putAll(index)
        return out
    }

    fun dynamicName(serverId: String, toolName: String): String =
        (DYNAMIC_PREFIX + sanitize(serverId).lowercase() + "__" + sanitize(toolName)).take(NAME_LIMIT)

    fun dynamicTool(server: McpServer, tool: McpTool): HarnessTool {
        val base = tool.description.trim().ifBlank { "The MCP tool ${tool.name} exposed by ${server.name}." }
        return HarnessTool(
            name = dynamicName(server.id, tool.name),
            group = HarnessGroup.MCP,
            permission = HarnessPermission.NETWORK,
            description = "$base (MCP server: ${server.name})",
            params = McpProtocol.params(tool.schemaJson)
        )
    }

    fun sanitize(raw: String): String {
        val out = StringBuilder()
        for (ch in raw) {
            val keep = (ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9') || ch == '_'
            out.append(if (keep) ch else '_')
        }
        return out.toString()
    }

    private suspend fun listServers(ctx: HarnessCtx): ToolExecResult {
        val configured = HarnessRuntime.config().mcpServers
        if (configured.isEmpty()) {
            return ToolExecResult(
                "No MCP servers are configured yet. Add one in Settings, Agent, MCP servers: give it an id, a " +
                    "name and either a url (streamable http) or a command (stdio).",
                success = false
            )
        }
        val lines = mutableListOf<String>()
        for (server in configured) {
            val state = if (server.enabled) "enabled" else "switched off"
            val count = if (!server.enabled) {
                "not checked"
            } else {
                val discovery = McpSessions.discovery(server)
                if (discovery.error.isBlank()) "${discovery.tools.size}" else "unknown"
            }
            val line = StringBuilder()
            line.append("- ").append(server.id).append(" (").append(server.name).append("): ")
            line.append("transport ").append(McpSessions.transportName(server)).append(", ").append(state)
            line.append(", tools discovered: ").append(count)
            line.append(", endpoint: ").append(McpSessions.endpoint(server))
            val error = McpSessions.lastError(server.id)
            if (error.isNotBlank()) line.append("\n  last error: ").append(error)
            lines.add(line.toString())
        }
        return ToolExecResult(ctx.limit("MCP servers:\n" + lines.joinToString("\n")))
    }

    private suspend fun listTools(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val servers = selectServers(args)
        if (servers.isEmpty()) return ToolExecResult(noServers(args, "mcp_tools"), success = false)
        val lines = mutableListOf<String>()
        var found = false
        for (server in servers) {
            val discovery = McpSessions.discovery(server)
            if (discovery.error.isNotBlank()) {
                lines.add("${server.id} (${server.name}): ${discovery.error}")
                continue
            }
            if (discovery.tools.isEmpty()) {
                lines.add("${server.id} (${server.name}): the server advertises no tools")
                continue
            }
            found = true
            lines.add("${server.id} (${server.name}) — ${discovery.tools.size} tool(s):")
            for (tool in discovery.tools) {
                val description = tool.description.ifBlank { "no description" }
                lines.add("  - ${tool.name}: $description")
                lines.add("    input: ${McpProtocol.compactSchema(tool.schemaJson)}")
            }
        }
        val hint = if (found) {
            "\n\nCall one with mcp_call (server, tool, arguments), or use the matching mcp__ tool when it is offered."
        } else {
            ""
        }
        return ToolExecResult(ctx.limit(lines.joinToString("\n") + hint), success = found)
    }

    private suspend fun callTool(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val tool = args.optString("tool", "").trim()
        if (tool.isEmpty()) {
            return ToolExecResult(
                "mcp_call needs the tool name from mcp_tools, in the tool field.",
                success = false
            )
        }
        val server = findServer(args.optString("server", "").trim())
            ?: return ToolExecResult(noServers(args, "mcp_call"), success = false)
        val result = McpSessions.call(server, tool, argumentText(args))
        return outcome(ctx, server, tool, result)
    }

    private suspend fun readResourceTool(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val uri = args.optString("uri", "").trim()
        if (uri.isEmpty()) {
            return ToolExecResult("mcp_read_resource needs the resource uri.", success = false)
        }
        val server = findServer(args.optString("server", "").trim())
            ?: return ToolExecResult(noServers(args, "mcp_read_resource"), success = false)
        val read = McpSessions.readResource(server, uri)
        if (read.error.isNotBlank()) {
            val hint = if (read.error.contains("method not found", ignoreCase = true)) {
                " This server does not expose resources; check mcp_servers first."
            } else {
                ""
            }
            return ToolExecResult(read.error + hint, success = false)
        }
        return ToolExecResult(ctx.limit(read.text))
    }

    private suspend fun listPrompts(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val servers = selectServers(args)
        if (servers.isEmpty()) return ToolExecResult(noServers(args, "mcp_prompts"), success = false)
        val lines = mutableListOf<String>()
        var found = false
        for (server in servers) {
            val listing = McpSessions.prompts(server)
            if (listing.error.isNotBlank() && listing.prompts.isEmpty()) {
                lines.add("${server.id} (${server.name}): ${listing.error}")
                continue
            }
            if (listing.prompts.isEmpty()) {
                lines.add("${server.id} (${server.name}): the server publishes no prompts")
                continue
            }
            found = true
            lines.add("${server.id} (${server.name}) — ${listing.prompts.size} prompt(s):")
            for (prompt in listing.prompts) {
                val description = prompt.description.ifBlank { "no description" }
                lines.add("  - ${prompt.name}: $description")
                if (prompt.arguments.isNotBlank()) lines.add("    arguments: ${prompt.arguments}")
            }
            if (listing.error.isNotBlank()) lines.add("  note: ${listing.error}")
        }
        return ToolExecResult(ctx.limit(lines.joinToString("\n")), success = found)
    }

    private suspend fun manageSession(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val action = args.optString("action", "").trim().lowercase()
        return when (action) {
            "close" -> {
                McpSessions.close()
                ToolExecResult(
                    "Dropped every cached MCP session. The next MCP call reconnects, shakes hands again and " +
                        "rediscovers the server's tools."
                )
            }
            "ping" -> {
                val servers = HarnessRuntime.config().mcpServers.filter { it.enabled }
                if (servers.isEmpty()) {
                    return ToolExecResult("No MCP server is enabled, so there is nothing to ping.", success = false)
                }
                val lines = servers.map { server ->
                    val status = McpSessions.ping(server)
                    if (status.ok) {
                        "- ${server.id} (${server.name}): ok in ${status.millis} ms"
                    } else {
                        "- ${server.id} (${server.name}): failed after ${status.millis} ms — ${status.detail}"
                    }
                }
                ToolExecResult(ctx.limit("MCP ping:\n" + lines.joinToString("\n")))
            }
            else -> ToolExecResult(
                "mcp_session understands action \"close\" or action \"ping\", not \"$action\".",
                success = false
            )
        }
    }

    private suspend fun dynamicToolCall(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? {
        val target = dynamicNames[name] ?: parseDynamic(name) ?: return null
        val server = HarnessRuntime.config().mcpServers.firstOrNull { it.id == target.first }
            ?: return null
        val result = McpSessions.call(server, target.second, args.toString())
        return outcome(ctx, server, target.second, result)
    }

    private fun parseDynamic(name: String): Pair<String, String>? {
        var bestId = ""
        var bestTool = ""
        var bestLength = 0
        for (server in HarnessRuntime.config().mcpServers) {
            val prefix = DYNAMIC_PREFIX + sanitize(server.id).lowercase() + "__"
            if (!name.startsWith(prefix)) continue
            if (name.length <= prefix.length) continue
            if (prefix.length <= bestLength) continue
            bestId = server.id
            bestTool = name.removePrefix(prefix)
            bestLength = prefix.length
        }
        if (bestId.isEmpty()) return null
        val real = McpSessions.cachedTools(bestId).firstOrNull { dynamicName(bestId, it.name) == name }
        return bestId to (real?.name ?: bestTool)
    }

    private fun selectServers(args: JSONObject): List<McpServer> {
        val wanted = args.optString("server", "").trim()
        val configured = HarnessRuntime.config().mcpServers
        if (wanted.isEmpty()) return configured.filter { it.enabled }
        val server = configured.firstOrNull { it.id == wanted }
            ?: configured.firstOrNull { it.id.equals(wanted, ignoreCase = true) }
        return listOfNotNull(server)
    }

    private fun findServer(id: String): McpServer? {
        val configured = HarnessRuntime.config().mcpServers
        if (id.isEmpty()) {
            return configured.firstOrNull { it.enabled }
        }
        return configured.firstOrNull { it.id == id }
            ?: configured.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    private fun noServers(args: JSONObject, tool: String): String {
        val wanted = args.optString("server", "").trim()
        val configured = HarnessRuntime.config().mcpServers
        if (configured.isEmpty()) {
            return "No MCP servers are configured yet, so $tool has nothing to work with. Add one in Settings, " +
                "Agent, MCP servers."
        }
        val ids = configured.joinToString(", ") { it.id }
        if (wanted.isNotEmpty()) {
            return "No MCP server with the id \"$wanted\" is configured. Known ids: $ids."
        }
        return "Every configured MCP server is switched off in Settings. Known ids: $ids."
    }

    private fun argumentText(args: JSONObject): String = when (val raw = args.opt("arguments")) {
        null -> "{}"
        is JSONObject -> raw.toString()
        is String -> raw.trim().ifBlank { "{}" }
        else -> raw.toString()
    }

    private fun outcome(ctx: HarnessCtx, server: McpServer, tool: String, result: McpResult): ToolExecResult {
        val body = result.text.ifBlank { "The server returned no text content." }
        val head = if (result.isError) {
            "The MCP server \"${server.name}\" flagged an error from $tool:"
        } else {
            "MCP server \"${server.name}\", tool $tool:"
        }
        val images = result.images.mapIndexed { index, image ->
            ToolImage(mime = image.first, data = image.second, name = imageName(server.id, tool, index, image.first))
        }
        val note = if (images.isEmpty()) "" else "\n\n${images.size} image(s) from the server are attached."
        return ToolExecResult(ctx.limit("$head\n$body$note"), images = images, success = !result.isError)
    }

    private fun imageName(serverId: String, tool: String, index: Int, mime: String): String {
        val extension = when {
            mime.contains("png", ignoreCase = true) -> "png"
            mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> "jpg"
            mime.contains("webp", ignoreCase = true) -> "webp"
            mime.contains("gif", ignoreCase = true) -> "gif"
            else -> "img"
        }
        val base = sanitize(serverId + "_" + tool).ifBlank { "mcp" }.take(40)
        return if (index == 0) "$base.$extension" else "${base}_$index.$extension"
    }
}
