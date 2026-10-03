package com.lucent.app.harness.mcp

import com.lucent.app.LucentBuild
import com.lucent.app.network.ToolParam
import org.json.JSONArray
import org.json.JSONObject

data class McpTool(
    val serverId: String,
    val name: String,
    val description: String,
    val schemaJson: String
)

data class McpResult(
    val text: String,
    val images: List<Pair<String, String>> = emptyList(),
    val isError: Boolean = false
)

data class McpResourceInfo(
    val uri: String,
    val name: String,
    val mimeType: String,
    val description: String
)

data class McpPromptInfo(
    val name: String,
    val description: String,
    val arguments: String
)

object McpProtocol {

    const val VERSION = "2025-06-18"
    const val CLIENT_NAME = "Lucent"
    const val JSONRPC = "2.0"

    const val INITIALIZE = "initialize"
    const val INITIALIZED = "notifications/initialized"
    const val TOOLS_LIST = "tools/list"
    const val TOOLS_CALL = "tools/call"
    const val RESOURCES_LIST = "resources/list"
    const val RESOURCES_READ = "resources/read"
    const val PROMPTS_LIST = "prompts/list"
    const val PING = "ping"

    const val CONTENT_JSON = "application/json"
    const val CONTENT_SSE = "text/event-stream"

    private const val SCHEMA_LIMIT = 900

    private val EMPTY_SCHEMA: String = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
    }.toString()

    fun capabilities(): JSONObject {
        val capabilities = JSONObject()
        capabilities.put("roots", JSONObject().put("listChanged", false))
        capabilities.put("sampling", JSONObject())
        return capabilities
    }

    fun initializeParams(): JSONObject {
        val params = JSONObject()
        params.put("protocolVersion", VERSION)
        params.put("capabilities", capabilities())
        val client = JSONObject()
        client.put("name", CLIENT_NAME)
        client.put("version", LucentBuild.VERSION)
        params.put("clientInfo", client)
        return params
    }

    fun request(id: Long, method: String, params: JSONObject? = null): String {
        val message = JSONObject()
        message.put("jsonrpc", JSONRPC)
        message.put("id", id)
        message.put("method", method)
        if (params != null) message.put("params", params)
        return message.toString()
    }

    fun notification(method: String, params: JSONObject? = null): String {
        val message = JSONObject()
        message.put("jsonrpc", JSONRPC)
        message.put("method", method)
        if (params != null) message.put("params", params)
        return message.toString()
    }

    fun listParams(cursor: String): JSONObject? =
        if (cursor.isBlank()) null else JSONObject().put("cursor", cursor)

    fun callParams(name: String, arguments: JSONObject): JSONObject {
        val params = JSONObject()
        params.put("name", name)
        params.put("arguments", arguments)
        return params
    }

    fun readParams(uri: String): JSONObject = JSONObject().put("uri", uri)

    fun parse(raw: String): JSONObject? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            null
        }
    }

    fun messages(body: String, contentType: String): List<JSONObject> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        val streamed = contentType.contains(CONTENT_SSE, ignoreCase = true) ||
            trimmed.startsWith("data:") ||
            trimmed.startsWith("event:")
        if (!streamed) return listOfNotNull(parse(trimmed))
        return sseMessages(trimmed)
    }

    fun sseMessages(body: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        val data = StringBuilder()
        for (line in body.split('\n')) {
            val text = line.trimEnd('\r')
            if (text.isEmpty()) {
                drain(data, out)
                continue
            }
            if (text.startsWith(":")) continue
            val colon = text.indexOf(':')
            val field = if (colon < 0) text else text.substring(0, colon)
            if (field != "data") continue
            val value = if (colon < 0) "" else text.substring(colon + 1).removePrefix(" ")
            if (data.isNotEmpty()) data.append('\n')
            data.append(value)
        }
        drain(data, out)
        return out
    }

    private fun drain(data: StringBuilder, out: MutableList<JSONObject>) {
        if (data.isEmpty()) return
        val payload = data.toString().trim()
        data.setLength(0)
        if (payload.isEmpty()) return
        parse(payload)?.let { out.add(it) }
    }

    fun pick(messages: List<JSONObject>, id: Long): JSONObject? {
        for (message in messages) {
            if (idOf(message) != id) continue
            if (message.has("result") || message.has("error")) return message
        }
        return null
    }

    fun idOf(message: JSONObject): Long? {
        if (!message.has("id")) return null
        return when (val raw = message.opt("id")) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
    }

    fun isNotification(message: JSONObject): Boolean = !message.has("id") && message.has("method")

    fun resultOf(message: JSONObject): JSONObject? = message.optJSONObject("result")

    fun errorOf(message: JSONObject): String {
        val error = message.optJSONObject("error") ?: return ""
        val code = error.optInt("code", 0)
        val text = error.optString("message", "")
        return errorText(code, text, error.opt("data"))
    }

    fun errorText(code: Int, message: String, data: Any? = null): String {
        val label = when (code) {
            -32700 -> "parse error"
            -32600 -> "invalid request"
            -32601 -> "method not found"
            -32602 -> "invalid params"
            -32603 -> "internal error"
            -32002 -> "resource not found"
            in -32099..-32000 -> "server error"
            else -> "error"
        }
        val head = if (message.isBlank()) "MCP $label" else "MCP $label: $message"
        val detail = renderData(data)
        return if (detail.isBlank()) "$head (code $code)" else "$head (code $code) — $detail"
    }

    private fun renderData(data: Any?): String = when (data) {
        null -> ""
        is String -> data.trim().replace(Regex("\\s+"), " ").take(200)
        else -> data.toString().replace(Regex("\\s+"), " ").take(200)
    }

    fun schemaJson(tool: JSONObject): String {
        val schema = tool.optJSONObject("inputSchema") ?: tool.optJSONObject("input_schema")
        return schema?.toString() ?: EMPTY_SCHEMA
    }

    fun decodeTools(serverId: String, result: JSONObject): List<McpTool> {
        val array = result.optJSONArray("tools") ?: return emptyList()
        val out = mutableListOf<McpTool>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name", "").trim()
            if (name.isEmpty()) continue
            out.add(
                McpTool(
                    serverId = serverId,
                    name = name,
                    description = item.optString("description", "").trim(),
                    schemaJson = schemaJson(item)
                )
            )
        }
        return out
    }

    fun nextCursor(result: JSONObject): String = result.optString("nextCursor", "").trim()

    fun decodeResult(result: JSONObject): McpResult {
        val parts = mutableListOf<String>()
        val images = mutableListOf<Pair<String, String>>()
        val content = result.optJSONArray("content")
        if (content != null) {
            for (i in 0 until content.length()) {
                val item = content.optJSONObject(i) ?: continue
                when (item.optString("type", "")) {
                    "text" -> {
                        val text = item.optString("text", "")
                        if (text.isNotEmpty()) parts.add(text)
                    }
                    "image" -> {
                        val data = item.optString("data", "")
                        val mime = item.optString("mimeType", "").ifBlank { "image/png" }
                        if (data.isNotEmpty()) {
                            images.add(mime to data)
                            parts.add("[image $mime — shown to you]")
                        }
                    }
                    "resource" -> parts.add(renderResource(item.optJSONObject("resource")))
                    "resource_link" -> {
                        val uri = item.optString("uri", "")
                        val name = item.optString("name", "").ifBlank { uri }
                        parts.add("[resource link $name — $uri]")
                    }
                    "audio" -> parts.add("[audio content from the server is not supported]")
                    else -> {
                        val text = item.optString("text", "")
                        if (text.isNotEmpty()) parts.add(text)
                    }
                }
            }
        }
        if (parts.isEmpty()) {
            val structured = result.optJSONObject("structuredContent")
            if (structured != null) parts.add(structured.toString())
        }
        return McpResult(
            text = parts.joinToString("\n").trim(),
            images = images,
            isError = result.optBoolean("isError", false)
        )
    }

    private fun renderResource(resource: JSONObject?): String {
        if (resource == null) return "[resource without contents]"
        val uri = resource.optString("uri", "")
        val mime = resource.optString("mimeType", "")
        val text = resource.optString("text", "")
        if (text.isNotEmpty()) return text
        val blob = resource.optString("blob", "")
        if (blob.isNotEmpty()) {
            val label = if (mime.isBlank()) uri else "$uri ($mime)"
            return "[binary resource $label — ${blob.length} base64 characters]"
        }
        return "[empty resource $uri]"
    }

    fun decodeResources(result: JSONObject): List<McpResourceInfo> {
        val array = result.optJSONArray("resources") ?: return emptyList()
        val out = mutableListOf<McpResourceInfo>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val uri = item.optString("uri", "").trim()
            if (uri.isEmpty()) continue
            out.add(
                McpResourceInfo(
                    uri = uri,
                    name = item.optString("name", "").trim(),
                    mimeType = item.optString("mimeType", "").trim(),
                    description = item.optString("description", "").trim()
                )
            )
        }
        return out
    }

    fun decodeResourceContents(result: JSONObject): String {
        val array = result.optJSONArray("contents") ?: return ""
        val parts = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val uri = item.optString("uri", "")
            val mime = item.optString("mimeType", "")
            val label = if (mime.isBlank()) uri else "$uri ($mime)"
            val text = item.optString("text", "")
            if (text.isNotEmpty()) {
                parts.add("----- $label -----\n$text")
                continue
            }
            val blob = item.optString("blob", "")
            if (blob.isNotEmpty()) {
                parts.add("[binary resource $label — ${blob.length} base64 characters, contents not shown]")
            } else {
                parts.add("[empty resource $uri]")
            }
        }
        return parts.joinToString("\n\n")
    }

    fun decodePrompts(result: JSONObject): List<McpPromptInfo> {
        val array = result.optJSONArray("prompts") ?: return emptyList()
        val out = mutableListOf<McpPromptInfo>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name", "").trim()
            if (name.isEmpty()) continue
            out.add(
                McpPromptInfo(
                    name = name,
                    description = item.optString("description", "").trim(),
                    arguments = promptArguments(item.optJSONArray("arguments"))
                )
            )
        }
        return out
    }

    private fun promptArguments(array: JSONArray?): String {
        if (array == null || array.length() == 0) return ""
        val parts = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name", "").trim()
            if (name.isEmpty()) continue
            val flag = if (item.optBoolean("required", false)) " (required)" else ""
            val description = item.optString("description", "").trim()
            val note = if (description.isBlank()) "" else " — $description"
            parts.add(name + flag + note)
        }
        return parts.joinToString("; ")
    }

    fun params(schemaJson: String): List<ToolParam> {
        val schema = parse(schemaJson) ?: return emptyList()
        val properties = schema.optJSONObject("properties") ?: return emptyList()
        val required = stringSet(schema.optJSONArray("required"))
        val out = mutableListOf<ToolParam>()
        val keys = properties.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val spec = properties.optJSONObject(name) ?: JSONObject()
            out.add(
                ToolParam(
                    name = name,
                    type = paramType(spec),
                    description = paramDescription(name, spec),
                    required = required.contains(name)
                )
            )
        }
        return out
    }

    fun compactSchema(schemaJson: String): String {
        val schema = parse(schemaJson) ?: return "unknown"
        val properties = schema.optJSONObject("properties")
        if (properties == null || properties.length() == 0) {
            val type = schema.optString("type", "").trim()
            return if (type.isBlank() || type == "object") "no arguments" else type
        }
        val required = stringSet(schema.optJSONArray("required"))
        val parts = mutableListOf<String>()
        val keys = properties.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val spec = properties.optJSONObject(name) ?: JSONObject()
            val flag = if (required.contains(name)) "required" else "optional"
            val description = spec.optString("description", "").trim().replace(Regex("\\s+"), " ")
            val note = if (description.isEmpty()) "" else " — ${description.take(120)}"
            parts.add("$name: ${paramType(spec)} ($flag)$note")
        }
        return parts.joinToString("; ").take(SCHEMA_LIMIT)
    }

    private fun paramType(spec: JSONObject): String {
        val raw = spec.optString("type", "").trim().lowercase()
        if (raw.isNotEmpty()) return mappedType(raw)
        if (spec.has("properties")) return "object"
        if (spec.has("items")) return "array"
        val union = spec.optJSONArray("anyOf") ?: spec.optJSONArray("oneOf")
        if (union != null) {
            for (i in 0 until union.length()) {
                val nested = union.optJSONObject(i)?.optString("type", "").orEmpty().trim().lowercase()
                if (nested.isNotEmpty()) return mappedType(nested)
            }
        }
        return "string"
    }

    private fun mappedType(raw: String): String = when (raw) {
        "string" -> "string"
        "number" -> "number"
        "integer" -> "number"
        "boolean" -> "boolean"
        "array" -> "array"
        "object" -> "object"
        else -> "string"
    }

    private fun paramDescription(name: String, spec: JSONObject): String {
        val parts = mutableListOf<String>()
        val description = spec.optString("description", "").trim()
        val title = spec.optString("title", "").trim()
        when {
            description.isNotEmpty() -> parts.add(description)
            title.isNotEmpty() -> parts.add(title)
            else -> parts.add("Value for $name.")
        }
        enumValues(spec)?.let { parts.add("One of: $it") }
        val nested = nestedProperties(spec)
        if (nested.isNotEmpty()) parts.add("Object with: $nested")
        val items = spec.optJSONObject("items")
        if (items != null) {
            val itemType = items.optString("type", "").trim()
            if (itemType.isNotEmpty()) parts.add("Array of $itemType")
        }
        return parts.joinToString(" ").take(400)
    }

    private fun enumValues(spec: JSONObject): String? {
        val array = spec.optJSONArray("enum") ?: return null
        val values = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, "").trim()
            if (value.isNotEmpty()) values.add(value)
        }
        return if (values.isEmpty()) null else values.joinToString(", ")
    }

    private fun nestedProperties(spec: JSONObject): String {
        val properties = spec.optJSONObject("properties") ?: return ""
        val parts = mutableListOf<String>()
        val keys = properties.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val nested = properties.optJSONObject(name) ?: continue
            parts.add("$name: ${paramType(nested)}")
        }
        return parts.joinToString(", ")
    }

    private fun stringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        val out = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, "").trim()
            if (value.isNotEmpty()) out.add(value)
        }
        return out
    }
}
