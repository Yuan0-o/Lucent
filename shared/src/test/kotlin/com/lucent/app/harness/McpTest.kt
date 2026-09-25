package com.lucent.app.harness

import android.content.Context
import com.lucent.app.LucentBuild
import com.lucent.app.data.AppDatabase
import com.lucent.app.harness.mcp.McpProtocol
import com.lucent.app.harness.mcp.McpSessions
import com.lucent.app.harness.mcp.McpTool
import com.lucent.app.harness.mcp.mcpCommandLine
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpTest {

    private val searchSchema = """
        {
          "type": "object",
          "properties": {
            "query": {"type": "string", "description": "What to search for"},
            "limit": {"type": "integer", "description": "How many hits to return"},
            "exact": {"type": "boolean"},
            "tags": {"type": "array", "items": {"type": "string"}},
            "filter": {
              "type": "object",
              "description": "Extra filters",
              "properties": {
                "limit": {"type": "integer"},
                "tags": {"type": "array"}
              }
            }
          },
          "required": ["query", "filter"]
        }
    """.trimIndent()

    private val toolsListJson = """
        {
          "tools": [
            {
              "name": "search",
              "description": "Search the catalogue",
              "inputSchema": {
                "type": "object",
                "properties": {
                  "query": {"type": "string", "description": "What to search for"},
                  "limit": {"type": "integer"}
                },
                "required": ["query"]
              }
            },
            {
              "name": "echo",
              "description": "Echo text back",
              "inputSchema": {"type": "object", "properties": {}}
            }
          ],
          "nextCursor": "page-2"
        }
    """.trimIndent()

    @Test
    fun requestEncodingCarriesJsonRpcFields() {
        val text = McpProtocol.request(
            7L,
            McpProtocol.TOOLS_CALL,
            McpProtocol.callParams("echo", JSONObject().put("text", "hi"))
        )
        val message = JSONObject(text)
        assertEquals("2.0", message.getString("jsonrpc"))
        assertEquals(7L, message.getLong("id"))
        assertEquals("tools/call", message.getString("method"))
        val params = message.getJSONObject("params")
        assertEquals("echo", params.getString("name"))
        assertEquals("hi", params.getJSONObject("arguments").getString("text"))
        val listed = McpProtocol.request(8L, McpProtocol.TOOLS_LIST)
        assertFalse(JSONObject(listed).has("params"))
        assertEquals("page-3", McpProtocol.listParams("page-3")?.getString("cursor"))
        assertNull(McpProtocol.listParams(""))
    }

    @Test
    fun notificationCarriesNoId() {
        val message = JSONObject(McpProtocol.notification(McpProtocol.INITIALIZED))
        assertFalse(message.has("id"))
        assertEquals("notifications/initialized", message.getString("method"))
        assertTrue(McpProtocol.isNotification(message))
        assertNull(McpProtocol.idOf(message))
    }

    @Test
    fun initializePayloadMatchesProtocolRevision() {
        val params = McpProtocol.initializeParams()
        assertEquals("2025-06-18", McpProtocol.VERSION)
        assertEquals(McpProtocol.VERSION, params.getString("protocolVersion"))
        val capabilities = params.getJSONObject("capabilities")
        assertFalse(capabilities.getJSONObject("roots").getBoolean("listChanged"))
        assertTrue(capabilities.has("sampling"))
        val client = params.getJSONObject("clientInfo")
        assertEquals("Lucent", client.getString("name"))
        assertEquals(LucentBuild.VERSION, client.getString("version"))
    }

    @Test
    fun sseStreamMixesNotificationWithReply() {
        val body = """
            event: message
            data: {"jsonrpc":"2.0","method":"notifications/message","params":{"level":"info"}}

            data: {"jsonrpc":"2.0","id":4,"result":{"tools":[]}}

        """.trimIndent()
        val messages = McpProtocol.messages(body, "text/event-stream; charset=utf-8")
        assertEquals(2, messages.size)
        assertTrue(McpProtocol.isNotification(messages[0]))
        assertNull(McpProtocol.idOf(messages[0]))
        val reply = assertNotNull(McpProtocol.pick(messages, 4L))
        assertEquals("2.0", reply.getString("jsonrpc"))
        assertNotNull(reply.optJSONObject("result"))
        assertNull(McpProtocol.pick(messages, 5L))
        assertNull(McpProtocol.pick(messages, 4L)?.optJSONObject("error"))
    }

    @Test
    fun plainJsonAndMultiLineSseBodiesDecode() {
        val plain = McpProtocol.messages("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"ok\":true}}", "application/json")
        assertEquals(1, plain.size)
        assertEquals(1L, McpProtocol.idOf(plain[0]))
        val streamed = McpProtocol.messages(
            "data: {\"jsonrpc\":\"2.0\",\ndata: \"id\":9,\"result\":{\"ok\":true}}\n\n",
            ""
        )
        assertEquals(1, streamed.size)
        assertEquals(9L, McpProtocol.idOf(streamed[0]))
        assertEquals(0, McpProtocol.messages("   ", "application/json").size)
        assertNull(McpProtocol.parse("not json"))
    }

    @Test
    fun jsonRpcErrorsMapToReadableText() {
        val message = JSONObject(
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"error\":{\"code\":-32601,\"message\":\"Unknown tool\"}}"
        )
        val text = McpProtocol.errorOf(message)
        assertTrue(text.contains("method not found"))
        assertTrue(text.contains("Unknown tool"))
        assertTrue(text.contains("-32601"))
        val ok = JSONObject("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}")
        assertEquals("", McpProtocol.errorOf(ok))
        assertTrue(McpProtocol.errorText(-32002, "").contains("resource not found"))
        assertTrue(McpProtocol.errorText(-32050, "boom").contains("server error"))
    }

    @Test
    fun toolsListDecodesDescriptorsAndCursor() {
        val result = JSONObject(toolsListJson)
        val tools = McpProtocol.decodeTools("demo", result)
        assertEquals(2, tools.size)
        assertEquals("demo", tools[0].serverId)
        assertEquals("search", tools[0].name)
        assertEquals("Search the catalogue", tools[0].description)
        assertTrue(tools[0].schemaJson.contains("\"query\""))
        assertEquals("echo", tools[1].name)
        assertEquals("page-2", McpProtocol.nextCursor(result))
        assertEquals("", McpProtocol.nextCursor(JSONObject()))
        val bare = McpProtocol.decodeTools("demo", JSONObject("{\"tools\":[{\"name\":\"noSchema\"}]}"))
        assertEquals(1, bare.size)
        assertTrue(bare[0].schemaJson.contains("properties"))
        assertEquals(0, McpProtocol.decodeTools("demo", JSONObject("{}")).size)
    }

    @Test
    fun callResultDecodesTextImagesAndResources() {
        val result = JSONObject(
            "{\"content\":[" +
                "{\"type\":\"text\",\"text\":\"42 degrees\"}," +
                "{\"type\":\"image\",\"data\":\"AAAA\",\"mimeType\":\"image/png\"}," +
                "{\"type\":\"resource\",\"resource\":{\"uri\":\"file:///notes/a.txt\",\"text\":\"hello\"}}" +
                "],\"isError\":true}"
        )
        val decoded = McpProtocol.decodeResult(result)
        assertTrue(decoded.text.contains("42 degrees"))
        assertTrue(decoded.text.contains("hello"))
        assertTrue(decoded.isError)
        assertEquals(1, decoded.images.size)
        assertEquals("image/png", decoded.images[0].first)
        assertEquals("AAAA", decoded.images[0].second)
        val plain = McpProtocol.decodeResult(JSONObject("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}"))
        assertFalse(plain.isError)
        assertEquals("ok", plain.text)
        assertTrue(plain.images.isEmpty())
        val structured = McpProtocol.decodeResult(JSONObject("{\"structuredContent\":{\"count\":3}}"))
        assertTrue(structured.text.contains("count"))
    }

    @Test
    fun resourcesAndPromptsDecode() {
        val contents = McpProtocol.decodeResourceContents(
            JSONObject("{\"contents\":[{\"uri\":\"file:///a.txt\",\"mimeType\":\"text/plain\",\"text\":\"body\"}]}")
        )
        assertTrue(contents.contains("body"))
        assertTrue(contents.contains("file:///a.txt"))
        val resources = McpProtocol.decodeResources(
            JSONObject("{\"resources\":[{\"uri\":\"file:///b.md\",\"name\":\"B\",\"mimeType\":\"text/markdown\"}]}")
        )
        assertEquals(1, resources.size)
        assertEquals("file:///b.md", resources[0].uri)
        assertEquals("B", resources[0].name)
        val prompts = McpProtocol.decodePrompts(
            JSONObject(
                "{\"prompts\":[{\"name\":\"summarise\",\"description\":\"Summarise a note\"," +
                    "\"arguments\":[{\"name\":\"title\",\"required\":true}]}]}"
            )
        )
        assertEquals(1, prompts.size)
        assertEquals("summarise", prompts[0].name)
        assertTrue(prompts[0].arguments.contains("title"))
        assertTrue(prompts[0].arguments.contains("required"))
    }

    @Test
    fun schemaMapsToToolParams() {
        val params = McpProtocol.params(searchSchema)
        assertEquals(5, params.size)
        val query = params.first { it.name == "query" }
        assertEquals("string", query.type)
        assertTrue(query.required)
        assertTrue(query.description.contains("What to search for"))
        val limit = params.first { it.name == "limit" }
        assertEquals("number", limit.type)
        assertFalse(limit.required)
        assertEquals("boolean", params.first { it.name == "exact" }.type)
        assertEquals("Value for exact.", params.first { it.name == "exact" }.description)
        val tags = params.first { it.name == "tags" }
        assertEquals("array", tags.type)
        assertTrue(tags.description.contains("Array of string"))
        val filter = params.first { it.name == "filter" }
        assertEquals("object", filter.type)
        assertTrue(filter.required)
        assertTrue(filter.description.contains("Extra filters"))
        assertTrue(filter.description.contains("limit: number"))
        assertEquals(0, McpProtocol.params("{}").size)
        assertEquals(0, McpProtocol.params("nonsense").size)
        val compact = McpProtocol.compactSchema(searchSchema)
        assertTrue(compact.contains("query: string (required)"))
        assertTrue(compact.contains("limit: number (optional)"))
        assertEquals("no arguments", McpProtocol.compactSchema("{\"type\":\"object\",\"properties\":{}}"))
    }

    @Test
    fun dynamicToolsUseServerPrefixedNames() = runBlocking {
        HarnessRuntime.update(
            HarnessConfig(
                mcpServers = listOf(
                    McpServer(id = "Demo Server", name = "Demo", url = "https://example.invalid/mcp")
                )
            )
        )
        McpSessions.invalidate()
        McpSessions.remember(
            "Demo Server",
            listOf(
                McpTool("Demo Server", "Search Items", "Search the catalogue", searchSchema),
                McpTool("Demo Server", "echo", "", "{}")
            )
        )
        try {
            val dynamic = McpTools.dynamicTools()
            assertEquals(2, dynamic.size)
            assertEquals(2, dynamic.map { it.name }.toSet().size)
            val search = dynamic.first { it.name == "mcp__demo_server__Search_Items" }
            assertEquals(HarnessGroup.MCP, search.group)
            assertEquals(HarnessPermission.NETWORK, search.permission)
            assertTrue(search.description.contains("Search the catalogue"))
            assertTrue(search.description.contains("Demo"))
            assertEquals("string", search.params.first { it.name == "query" }.type)
            assertTrue(search.params.first { it.name == "query" }.required)
            assertEquals("number", search.params.first { it.name == "limit" }.type)
            val echo = dynamic.first { it.name == "mcp__demo_server__echo" }
            assertTrue(echo.params.isEmpty())
            assertTrue(echo.description.contains("Demo"))
            assertEquals("mcp__demo_server__echo", McpTools.dynamicName("Demo Server", "echo"))
            assertEquals(64, McpTools.dynamicName("Demo Server", "x".repeat(200)).length)
            assertEquals("a_b_c", McpTools.sanitize("a-b c"))
        } finally {
            McpSessions.invalidate()
            HarnessRuntime.update(HarnessConfig())
        }
    }

    @Test
    fun executeReturnsNullForForeignNames() = runBlocking {
        HarnessRuntime.update(HarnessConfig())
        val ctx = testCtx() ?: return@runBlocking
        assertNull(McpTools.execute(ctx, "read_file", JSONObject()))
        assertNull(McpTools.execute(ctx, "list_notes", JSONObject()))
        assertNull(McpTools.execute(ctx, "mcp_tool", JSONObject()))
        assertNull(McpTools.execute(ctx, "mcp__ghost__search", JSONObject()))
        assertNotNull(McpTools.execute(ctx, "mcp_servers", JSONObject()))
        assertNotNull(McpTools.execute(ctx, "mcp_tools", JSONObject().put("server", "ghost")))
    }

    @Test
    fun dynamicNamesAreClaimedForConfiguredServersOnly() {
        HarnessRuntime.update(
            HarnessConfig(
                mcpServers = listOf(
                    McpServer(id = "Demo Server", name = "Demo", url = "https://example.invalid/mcp")
                )
            )
        )
        try {
            assertTrue(McpTools.canHandle("mcp_servers"))
            assertTrue(McpTools.canHandle("mcp__demo_server__anything"))
            assertFalse(McpTools.canHandle("read_file"))
            assertFalse(McpTools.canHandle("mcp__ghost__anything"))
        } finally {
            HarnessRuntime.update(HarnessConfig())
        }
    }

    @Test
    fun dynamicCallToDisabledServerFailsCleanly() = runBlocking {
        HarnessRuntime.update(
            HarnessConfig(
                mcpServers = listOf(
                    McpServer(id = "off", name = "Off", url = "https://example.invalid/mcp", enabled = false)
                )
            )
        )
        try {
            val ctx = testCtx() ?: return@runBlocking
            val result = assertNotNull(McpTools.execute(ctx, "mcp__off__echo", JSONObject()))
            assertFalse(result.success)
            assertTrue(result.summary.contains("switched off"))
        } finally {
            HarnessRuntime.update(HarnessConfig())
            McpSessions.invalidate()
        }
    }

    @Test
    fun disabledAndUnconfiguredServersFailCleanly() = runBlocking {
        val disabled = McpServer(id = "off", name = "Off", url = "https://example.invalid/mcp", enabled = false)
        val discovery = McpSessions.discovery(disabled)
        assertTrue(discovery.tools.isEmpty())
        assertTrue(discovery.error.contains("switched off"))
        assertTrue(McpSessions.tools(disabled).isEmpty())
        assertTrue(McpSessions.lastError("off").contains("switched off"))
        val bare = McpServer(id = "bare", name = "Bare")
        val call = McpSessions.call(bare, "echo", "{}")
        assertTrue(call.isError)
        assertTrue(call.text.contains("not configured"))
        assertTrue(call.images.isEmpty())
        val reply = McpSessions.exchange(bare, McpProtocol.PING)
        assertFalse(reply.ok)
        assertTrue(reply.error.isNotBlank())
        val broken = McpSessions.call(bare, "echo", "not json")
        assertTrue(broken.isError)
        assertTrue(broken.text.contains("JSON object"))
        val nameless = McpSessions.call(bare, "  ", "{}")
        assertTrue(nameless.isError)
        McpSessions.invalidate()
    }

    @Test
    fun stdioServersAreRefusedOnAndroid() = runBlocking {
        val server = McpServer(id = "local", name = "Local", command = "npx", arguments = "-y some-mcp")
        assertEquals("stdio", McpSessions.transportName(server))
        assertEquals(listOf("npx", "-y", "some-mcp"), mcpCommandLine(server))
        val wasAndroid = HarnessRuntime.android
        HarnessRuntime.android = true
        try {
            val reply = McpSessions.exchange(server, McpProtocol.TOOLS_LIST)
            assertFalse(reply.ok)
            assertTrue(reply.error.contains("termux"))
            assertTrue(reply.error.contains("url"))
        } finally {
            HarnessRuntime.android = wasAndroid
            McpSessions.invalidate()
        }
    }

    @Test
    fun serverListingNeverPrintsTokens() = runBlocking {
        HarnessRuntime.update(
            HarnessConfig(
                mcpServers = listOf(
                    McpServer(
                        id = "tokened",
                        name = "Tokened",
                        url = "https://example.invalid/mcp",
                        token = "super-secret-token",
                        enabled = false
                    )
                )
            )
        )
        try {
            val ctx = testCtx() ?: return@runBlocking
            val result = assertNotNull(McpTools.execute(ctx, "mcp_servers", JSONObject()))
            assertFalse(result.summary.contains("super-secret-token"))
            assertTrue(result.summary.contains("tokened"))
            assertTrue(result.summary.contains("switched off"))
        } finally {
            HarnessRuntime.update(HarnessConfig())
            McpSessions.invalidate()
        }
    }

    @Test
    fun toolSetIsCompleteAndDescriptionsStayShort() {
        assertEquals(
            listOf("mcp_servers", "mcp_tools", "mcp_call", "mcp_read_resource", "mcp_prompts", "mcp_session"),
            McpTools.tools.map { it.name }
        )
        for (tool in McpTools.tools) {
            assertEquals(HarnessGroup.MCP, tool.group)
            assertTrue(tool.description.isNotBlank())
            assertTrue(tool.description.length <= 450, "${tool.name} is ${tool.description.length} characters")
        }
        for (name in listOf("mcp_call", "mcp_read_resource", "mcp_session")) {
            assertEquals(HarnessPermission.NETWORK, McpTools.tools.first { it.name == name }.permission)
        }
        for (name in listOf("mcp_servers", "mcp_tools", "mcp_prompts")) {
            assertEquals(HarnessPermission.READ, McpTools.tools.first { it.name == name }.permission)
        }
    }

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-mcp-test-${System.nanoTime()}").apply { mkdirs() }

    private fun testCtx(): HarnessCtx? {
        val dir = tempDir()
        val config = HarnessRuntime.config()
        val context = allocate(Context::class.java) as? Context
        val db = allocate(AppDatabase::class.java) as? AppDatabase
        if (context != null && db != null) {
            return HarnessCtx(context, db, config, emptySet(), false, dir)
        }
        val ctx = allocate(HarnessCtx::class.java) as? HarnessCtx ?: return null
        fill(ctx, "config", config)
        fill(ctx, "capabilities", emptySet<String>())
        fill(ctx, "android", false)
        fill(ctx, "workspace", dir)
        return ctx
    }

    private fun fill(target: HarnessCtx, name: String, value: Any) {
        try {
            val field = HarnessCtx::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.set(target, value)
        } catch (t: Throwable) {
        }
    }

    private fun allocate(type: Class<*>): Any? = unsafeAllocate(type) ?: serializationAllocate(type)

    private fun unsafeAllocate(type: Class<*>): Any? = try {
        val unsafe = Class.forName("sun.misc.Unsafe")
        val field = unsafe.getDeclaredField("theUnsafe")
        field.isAccessible = true
        unsafe.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), type)
    } catch (t: Throwable) {
        null
    }

    private fun serializationAllocate(type: Class<*>): Any? = try {
        val factoryType = Class.forName("sun.reflect.ReflectionFactory")
        val factory = factoryType.getMethod("getReflectionFactory").invoke(null)
        val marker = Any::class.java.getDeclaredConstructor()
        val creator = factoryType
            .getMethod(
                "newConstructorForSerialization",
                Class::class.java,
                java.lang.reflect.Constructor::class.java
            )
            .invoke(factory, type, marker) as java.lang.reflect.Constructor<*>
        creator.isAccessible = true
        creator.newInstance()
    } catch (t: Throwable) {
        null
    }
}
