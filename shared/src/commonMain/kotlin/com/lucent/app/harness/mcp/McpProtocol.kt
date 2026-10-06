package com.lucent.app.harness.mcp

import com.lucent.app.LucentBuild
import com.lucent.app.network.ToolParam
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class McpTool(
    val serverId: String,
    val name: String,
    val description: String,
    val schemaJson: String
)

@Serializable
data class McpResult(
    val text: String,
    val images: List<Pair<String, String>> = emptyList(),
    val isError: Boolean = false
)

@Serializable
data class McpResourceInfo(
    val uri: String,
    val name: String,
    val mimeType: String,
    val description: String
)

@Serializable
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

    private val EMPTY_SCHEMA: String = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }.toString()

    fun capabilities(): JsonObject {
        return buildJsonObject {
            put("roots", buildJsonObject { put("listChanged", false) })
            put("sampling", buildJsonObject {})
        }
    }

    fun initializeParams(): JsonObject {
        return buildJsonObject {
            put("protocolVersion", VERSION)
            put("capabilities", capabilities())
            put("clientInfo", buildJsonObject {
                put("name", CLIENT_NAME)
                put("version", LucentBuild.VERSION)
            })
        }
    }

    fun request(id: Long, method: String, params: JsonObject? = null): String {
        return buildJsonObject {
            put("jsonrpc", JSONRPC)
            put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }.toString()
    }

    fun notification(method: String, params: JsonObject? = null): String {
        return buildJsonObject {
            put("jsonrpc", JSONRPC)
            put("method", method)
            if (params != null) put("params", params)
        }.toString()
    }

    fun listParams(cursor: String): JsonObject? =
        if (cursor.isBlank()) null else buildJsonObject { put("cursor", cursor) }

    fun callParams(name: String, arguments: JsonObject): JsonObject {
        return buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        }
    }

    fun readParams(uri: String): JsonObject = buildJsonObject { put("uri", uri) }

    fun parse(raw: String): JsonObject? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            null
        }
    }

    fun messages(body: String, contentType: String): List<JsonObject> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        val streamed = contentType.contains(CONTENT_SSE, ignoreCase = true) ||
            trimmed.startsWith("data:") ||
            trimmed.startsWith("event:")
        if (!streamed) return listOfNotNull(parse(trimmed))
        return sseMessages(trimmed)
    }

    fun sseMessages(body: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
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

    private fun drain(data: StringBuilder, out: MutableList<JsonObject>) {
        if (data.isEmpty()) return
        val payload = data.toString().trim()
        data.setLength(0)
        if (payload.isEmpty()) return
        parse(payload)?.let { out.add(it) }
    }

    fun pick(messages: List<JsonObject>, id: Long): JsonObject? {
        for (message in messages) {
            if (idOf(message) != id) continue
            if (message.containsKey("result") || message.containsKey("error")) return message
        }
        return null
    }

    fun idOf(message: JsonObject): Long? {
        if (!message.containsKey("id")) return null
        return message["id"]?.jsonPrimitive?.content?.toLongOrNull()
    }

    fun isNotification(message: JsonObject): Boolean = !message.containsKey("id") && message.containsKey("method")

    fun resultOf(message: JsonObject): JsonObject? = message["result"]?.jsonObject

    fun errorOf(message: JsonObject): String {
        val error = message["error"]?.jsonObject ?: return ""
        val code = error["code"]?.jsonPrimitive?.intOrNull ?: 0
        val text = error["message"]?.jsonPrimitive?.content ?: ""
        return errorText(code, text, error["data"])
    }

    fun errorText(code: Int, message: String, data: JsonElement? = null): String {
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

    private fun renderData(data: JsonElement?): String = when {
        data == null -> ""
        data is JsonPrimitive && data.isString -> data.content.trim().replace(Regex("\\s+"), " ").take(200)
        else -> data.toString().replace(Regex("\\s+"), " ").take(200)
    }

    fun schemaJson(tool: JsonObject): String {
        val schema = tool["inputSchema"]?.jsonObject ?: tool["input_schema"]?.jsonObject
        return schema?.toString() ?: EMPTY_SCHEMA
    }

    fun decodeTools(serverId: String, result: JsonObject): List<McpTool> {
        val array = result["tools"]?.jsonArray ?: return emptyList()
        val out = mutableListOf<McpTool>()
        for (i in 0 until array.size) {
            val item = array[i] as? JsonObject ?: continue
            val name = (item["name"]?.jsonPrimitive?.content ?: "").trim()
            if (name.isEmpty()) continue
            out.add(
                McpTool(
                    serverId = serverId,
                    name = name,
                    description = (item["description"]?.jsonPrimitive?.content ?: "").trim(),
                    schemaJson = schemaJson(item)
                )
            )
        }
        return out
    }

    fun nextCursor(result: JsonObject): String = (result["nextCursor"]?.jsonPrimitive?.content ?: "").trim()

    fun decodeResult(result: JsonObject): McpResult {
        val parts = mutableListOf<String>()
        val images = mutableListOf<Pair<String, String>>()
        val content = result["content"]?.jsonArray
        if (content != null) {
            for (i in 0 until content.size) {
                val item = content[i] as? JsonObject ?: continue
                when (item["type"]?.jsonPrimitive?.content ?: "") {
                    "text" -> {
                        val text = item["text"]?.jsonPrimitive?.content ?: ""
                        if (text.isNotEmpty()) parts.add(text)
                    }
                    "image" -> {
                        val data = item["data"]?.jsonPrimitive?.content ?: ""
                        val mime = (item["mimeType"]?.jsonPrimitive?.content ?: "").ifBlank { "image/png" }
                        if (data.isNotEmpty()) {
                            images.add(mime to data)
                            parts.add("[image $mime — shown to you]")
                        }
                    }
                    "resource" -> parts.add(renderResource(item["resource"] as? JsonObject))
                    "resource_link" -> {
                        val uri = item["uri"]?.jsonPrimitive?.content ?: ""
                        val name = (item["name"]?.jsonPrimitive?.content ?: "").ifBlank { uri }
                        parts.add("[resource link $name — $uri]")
                    }
                    "audio" -> parts.add("[audio content from the server is not supported]")
                    else -> {
                        val text = item["text"]?.jsonPrimitive?.content ?: ""
                        if (text.isNotEmpty()) parts.add(text)
                    }
                }
            }
        }
        if (parts.isEmpty()) {
            val structured = result["structuredContent"] as? JsonObject
            if (structured != null) parts.add(structured.toString())
        }
        return McpResult(
            text = parts.joinToString("\n").trim(),
            images = images,
            isError = result["isError"]?.jsonPrimitive?.booleanOrNull ?: false
        )
    }

    private fun renderResource(resource: JsonObject?): String {
        if (resource == null) return "[resource without contents]"
        val uri = resource["uri"]?.jsonPrimitive?.content ?: ""
        val mime = resource["mimeType"]?.jsonPrimitive?.content ?: ""
        val text = resource["text"]?.jsonPrimitive?.content ?: ""
        if (text.isNotEmpty()) return text
        val blob = resource["blob"]?.jsonPrimitive?.content ?: ""
        if (blob.isNotEmpty()) {
            val label = if (mime.isBlank()) uri else "$uri ($mime)"
            return "[binary resource $label — ${blob.length} base64 characters]"
        }
        return "[empty resource $uri]"
    }

    fun decodeResources(result: JsonObject): List<McpResourceInfo> {
        val array = result["resources"]?.jsonArray ?: return emptyList()
        val out = mutableListOf<McpResourceInfo>()
        for (i in 0 until array.size) {
            val item = array[i] as? JsonObject ?: continue
            val uri = (item["uri"]?.jsonPrimitive?.content ?: "").trim()
            if (uri.isEmpty()) continue
            out.add(
                McpResourceInfo(
                    uri = uri,
                    name = (item["name"]?.jsonPrimitive?.content ?: "").trim(),
                    mimeType = (item["mimeType"]?.jsonPrimitive?.content ?: "").trim(),
                    description = (item["description"]?.jsonPrimitive?.content ?: "").trim()
                )
            )
        }
        return out
    }

    fun decodeResourceContents(result: JsonObject): String {
        val array = result["contents"]?.jsonArray ?: return ""
        val parts = mutableListOf<String>()
        for (i in 0 until array.size) {
            val item = array[i] as? JsonObject ?: continue
            val uri = item["uri"]?.jsonPrimitive?.content ?: ""
            val mime = item["mimeType"]?.jsonPrimitive?.content ?: ""
            val label = if (mime.isBlank()) uri else "$uri ($mime)"
            val text = item["text"]?.jsonPrimitive?.content ?: ""
            if (text.isNotEmpty()) {
                parts.add("----- $label -----\n$text")
                continue
            }
            val blob = item["blob"]?.jsonPrimitive?.content ?: ""
            if (blob.isNotEmpty()) {
                parts.add("[binary resource $label — ${blob.length} base64 characters, contents not shown]")
            } else {
                parts.add("[empty resource $uri]")
            }
        }
        return parts.joinToString("\n\n")
    }

    fun decodePrompts(result: JsonObject): List<McpPromptInfo> {
        val array = result["prompts"]?.jsonArray ?: return emptyList()
        val out = mutableListOf<McpPromptInfo>()
        for (i in 0 until array.size) {
            val item = array[i] as? JsonObject ?: continue
            val name = (item["name"]?.jsonPrimitive?.content ?: "").trim()
            if (name.isEmpty()) continue
            out.add(
                McpPromptInfo(
                    name = name,
                    description = (item["description"]?.jsonPrimitive?.content ?: "").trim(),
                    arguments = promptArguments(item["arguments"]?.jsonArray)
                )
            )
        }
        return out
    }

    private fun promptArguments(array: JsonArray?): String {
        if (array == null || array.size == 0) return ""
        val parts = mutableListOf<String>()
        for (i in 0 until array.size) {
            val item = array[i] as? JsonObject ?: continue
            val name = (item["name"]?.jsonPrimitive?.content ?: "").trim()
            if (name.isEmpty()) continue
            val flag = if (item["required"]?.jsonPrimitive?.booleanOrNull == true) " (required)" else ""
            val description = (item["description"]?.jsonPrimitive?.content ?: "").trim()
            val note = if (description.isBlank()) "" else " — $description"
            parts.add(name + flag + note)
        }
        return parts.joinToString("; ")
    }

    fun params(schemaJson: String): List<ToolParam> {
        val schema = parse(schemaJson) ?: return emptyList()
        val properties = schema["properties"]?.jsonObject ?: return emptyList()
        val required = stringSet(schema["required"]?.jsonArray)
        val out = mutableListOf<ToolParam>()
        val keys = properties.keys
        for (name in keys) {
            val spec = properties[name] as? JsonObject ?: buildJsonObject {}
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
        val properties = schema["properties"]?.jsonObject
        if (properties == null || properties.isEmpty()) {
            val type = (schema["type"]?.jsonPrimitive?.content ?: "").trim()
            return if (type.isBlank() || type == "object") "no arguments" else type
        }
        val required = stringSet(schema["required"]?.jsonArray)
        val parts = mutableListOf<String>()
        val keys = properties.keys
        for (name in keys) {
            val spec = properties[name] as? JsonObject ?: buildJsonObject {}
            val flag = if (required.contains(name)) "required" else "optional"
            val description = (spec["description"]?.jsonPrimitive?.content ?: "").trim().replace(Regex("\\s+"), " ")
            val note = if (description.isEmpty()) "" else " — ${description.take(120)}"
            parts.add("$name: ${paramType(spec)} ($flag)$note")
        }
        return parts.joinToString("; ").take(SCHEMA_LIMIT)
    }

    private fun paramType(spec: JsonObject): String {
        val raw = (spec["type"]?.jsonPrimitive?.content ?: "").trim().lowercase()
        if (raw.isNotEmpty()) return mappedType(raw)
        if (spec.containsKey("properties")) return "object"
        if (spec.containsKey("items")) return "array"
        val union = spec["anyOf"]?.jsonArray ?: spec["oneOf"]?.jsonArray
        if (union != null) {
            for (i in 0 until union.size) {
                val nestedItem = union[i] as? JsonObject
                val nested = (nestedItem?.get("type")?.jsonPrimitive?.content ?: "").trim().lowercase()
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

    private fun paramDescription(name: String, spec: JsonObject): String {
        val parts = mutableListOf<String>()
        val description = (spec["description"]?.jsonPrimitive?.content ?: "").trim()
        val title = (spec["title"]?.jsonPrimitive?.content ?: "").trim()
        when {
            description.isNotEmpty() -> parts.add(description)
            title.isNotEmpty() -> parts.add(title)
            else -> parts.add("Value for $name.")
        }
        enumValues(spec)?.let { parts.add("One of: $it") }
        val nested = nestedProperties(spec)
        if (nested.isNotEmpty()) parts.add("Object with: $nested")
        val items = spec["items"] as? JsonObject
        if (items != null) {
            val itemType = (items["type"]?.jsonPrimitive?.content ?: "").trim()
            if (itemType.isNotEmpty()) parts.add("Array of $itemType")
        }
        return parts.joinToString(" ").take(400)
    }

    private fun enumValues(spec: JsonObject): String? {
        val array = spec["enum"]?.jsonArray ?: return null
        val values = mutableListOf<String>()
        for (i in 0 until array.size) {
            val value = (array[i] as? JsonPrimitive)?.content?.trim() ?: ""
            if (value.isNotEmpty()) values.add(value)
        }
        return if (values.isEmpty()) null else values.joinToString(", ")
    }

    private fun nestedProperties(spec: JsonObject): String {
        val properties = spec["properties"] as? JsonObject ?: return ""
        val parts = mutableListOf<String>()
        val keys = properties.keys
        for (name in keys) {
            val nested = properties[name] as? JsonObject ?: continue
            parts.add("$name: ${paramType(nested)}")
        }
        return parts.joinToString(", ")
    }

    private fun stringSet(array: JsonArray?): Set<String> {
        if (array == null) return emptySet()
        val out = mutableSetOf<String>()
        for (i in 0 until array.size) {
            val value = (array[i] as? JsonPrimitive)?.content?.trim() ?: ""
            if (value.isNotEmpty()) out.add(value)
        }
        return out
    }
}
