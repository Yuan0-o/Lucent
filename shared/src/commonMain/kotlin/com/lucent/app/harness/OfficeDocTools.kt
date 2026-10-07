package com.lucent.app.harness

import okio.Path.Companion.toPath

import com.lucent.app.harness.ooxml.Docx
import com.lucent.app.network.ToolExecResult
import kotlinx.serialization.json.*

object OfficeDocTools : HarnessGroupTools {

    override val group: HarnessGroup = HarnessGroup.OFFICE

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "create_document",
            group = HarnessGroup.OFFICE,
            permission = HarnessPermission.WRITE,
            description = "Create a Word .docx file that opens in Word, LibreOffice and WPS. Pass \"content\" as Markdown (headings, bullet and numbered lists, tables, quotes, fenced code, links, images) or \"spec\" as JSON for full control: {\"title\":\"...\",\"author\":\"...\",\"header\":\"...\",\"footer\":\"...\",\"page_numbers\":true,\"toc\":true,\"blocks\":[{\"type\":\"heading\",\"level\":1,\"text\":\"...\"},{\"type\":\"paragraph\",\"text\":\"...\",\"align\":\"center\",\"style\":{\"bold\":true,\"italic\":true,\"underline\":true,\"strike\":true,\"colour\":\"#C00000\",\"size\":14,\"font\":\"Arial\",\"highlight\":\"yellow\",\"spacing\":1.5}},{\"type\":\"bullets\",\"items\":[\"...\"]},{\"type\":\"numbers\",\"items\":[\"...\"]},{\"type\":\"table\",\"header\":[\"A\",\"B\"],\"rows\":[[\"1\",\"2\"]],\"widths\":[3000,3000]},{\"type\":\"image\",\"path\":\"chart.png\",\"width\":480,\"caption\":\"...\"},{\"type\":\"pagebreak\"},{\"type\":\"quote\",\"text\":\"...\"},{\"type\":\"code\",\"text\":\"...\"},{\"type\":\"link\",\"text\":\"...\",\"url\":\"https://...\"},{\"type\":\"rule\"}]}. Relative image paths are resolved against the folder of the new document.",
            params = listOf(
                HarnessSchema.text("path", "Where to write the .docx, relative to the workspace or absolute"),
                HarnessSchema.text("title", "Document title, also stored in the document properties", required = false),
                HarnessSchema.text("author", "Author name stored in the document properties", required = false),
                HarnessSchema.text("content", "Markdown body text", required = false),
                HarnessSchema.json("spec", "Full document spec with title, header, footer and blocks", required = false)
            )
        ),
        HarnessTool(
            name = "read_document",
            group = HarnessGroup.OFFICE,
            permission = HarnessPermission.READ,
            description = "Read a Word .docx file as plain text. Headings come back as #, lists as - or 1., tables as Markdown pipe tables and images as [image name]. Files written by Word, LibreOffice, Google Docs or WPS are accepted, and missing optional parts are tolerated.",
            params = listOf(
                HarnessSchema.text("path", "The .docx file to read"),
                HarnessSchema.number("max_chars", "Maximum characters to return, 500 to 400000", required = false)
            )
        ),
        HarnessTool(
            name = "edit_document",
            group = HarnessGroup.OFFICE,
            permission = HarnessPermission.WRITE,
            description = "Edit an existing Word .docx in place, keeping everything else in the file. \"ops\" is an array of operations: {\"op\":\"append\",\"content\":\"markdown\"}, {\"op\":\"append_blocks\",\"blocks\":[...]}, {\"op\":\"replace\",\"find\":\"old\",\"replace\":\"new\",\"all\":true}, {\"op\":\"set_header\",\"text\":\"...\"}, {\"op\":\"set_footer\",\"text\":\"...\",\"page_numbers\":true}, {\"op\":\"insert_image\",\"path\":\"pic.png\",\"width\":480,\"caption\":\"...\"}, {\"op\":\"delete_paragraph\",\"find\":\"exact text\"} and {\"op\":\"set_title\",\"text\":\"...\"}.",
            params = listOf(
                HarnessSchema.text("path", "The .docx file to edit"),
                HarnessSchema.list("ops", "Array of edit operations", itemType = "object")
            )
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JsonObject): ToolExecResult? {
        return try {
            when (name) {
                "create_document" -> createDocument(ctx, args)
                "read_document" -> readDocument(ctx, args)
                "edit_document" -> editDocument(ctx, args)
                else -> null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: HarnessError) {
            ToolExecResult(e.message ?: "That path is not allowed.", success = false)
        } catch (e: IllegalArgumentException) {
            ToolExecResult("$name failed: ${e.message ?: "the request was not valid"}", success = false)
        } catch (e: Exception) {
            ToolExecResult("$name failed: ${e.message ?: e::class.simpleName}", success = false)
        }
    }

    private fun createDocument(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val file = Workspace.forWriteFile(ctx, args["path"]?.jsonPrimitive?.content ?: "")
        if (okio.FileSystem.SYSTEM.metadataOrNull(file)?.isDirectory == true) {
            return ToolExecResult("${Workspace.display(ctx, file)} is a directory, not a .docx file.", success = false)
        }
        val spec = documentSpec(args)
        if (ctx.config.snapshots && okio.FileSystem.SYSTEM.exists(file)) Snapshots.capture(ctx, file)
        file.parent?.let { okio.FileSystem.SYSTEM.createDirectories(it) }
        val detail = Docx.create(spec, file)
        return ToolExecResult(
            "Created ${Workspace.display(ctx, file)} (${Workspace.humanSize(okio.FileSystem.SYSTEM.metadataOrNull(file)?.size ?: 0L)}): $detail."
        )
    }

    private fun readDocument(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val file = Workspace.forReadFile(ctx, args["path"]?.jsonPrimitive?.content ?: "")
        if (okio.FileSystem.SYSTEM.metadataOrNull(file)?.isDirectory == true) {
            return ToolExecResult("${Workspace.display(ctx, file)} is a directory, not a .docx file.", success = false)
        }
        val maxChars = (args["max_chars"]?.jsonPrimitive?.intOrNull ?: 20000).coerceIn(500, 400000)
        return ToolExecResult(Docx.read(file, maxChars))
    }

    private fun editDocument(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val file = Workspace.forWriteFile(ctx, args["path"]?.jsonPrimitive?.content ?: "")
        if (okio.FileSystem.SYSTEM.metadataOrNull(file)?.isDirectory == true) {
            return ToolExecResult("${Workspace.display(ctx, file)} is a directory, not a .docx file.", success = false)
        }
        if (!okio.FileSystem.SYSTEM.exists(file)) {
            return ToolExecResult("${Workspace.display(ctx, file)} does not exist yet.", success = false)
        }
        val ops = operations(args)
        if (ctx.config.snapshots) Snapshots.capture(ctx, file)
        val detail = Docx.edit(file, ops)
        return ToolExecResult(
            "Edited ${Workspace.display(ctx, file)} (${Workspace.humanSize(okio.FileSystem.SYSTEM.metadataOrNull(file)?.size ?: 0L)}): $detail."
        )
    }

    private fun documentSpec(args: JsonObject): String {
        val explicit = jsonObject(args["spec"], "spec")
        val root = explicit ?: JsonObject(emptyMap())
        val title = args["title"]?.jsonPrimitive?.content ?: ""
        val author = args["author"]?.jsonPrimitive?.content ?: ""
        val content = args["content"]?.jsonPrimitive?.content ?: ""

        if (!root.containsKey("content") && !root.containsKey("blocks") && !root.containsKey("title") && title.isBlank() && content.isBlank()) {
            throw IllegalArgumentException(
                "Give the document some text with \"content\", or a full \"spec\" with a title or a blocks array."
            )
        }
        
        if ((title.isNotBlank() && !root.containsKey("title")) || 
            (author.isNotBlank() && !root.containsKey("author")) ||
            (content.isNotBlank() && !root.containsKey("content") && !root.containsKey("blocks"))) {
            return buildJsonObject {
                root.forEach { k, v -> put(k, v) }
                if (title.isNotBlank() && !root.containsKey("title")) put("title", title)
                if (author.isNotBlank() && !root.containsKey("author")) put("author", author)
                if (content.isNotBlank() && !root.containsKey("content") && !root.containsKey("blocks")) put("content", content)
            }.toString()
        }
        return root.toString()
    }

    private fun operations(args: JsonObject): String {
        val raw = args["ops"]
        val array = when (raw) {
            is JsonArray -> raw
            is JsonObject -> buildJsonArray { add(raw) }
            is JsonPrimitive -> if (raw.isString) {
                if (raw.content.isBlank()) buildJsonArray {} else operationsFromText(raw.content)
            } else {
                buildJsonArray {}
            }
            else -> buildJsonArray {}
        }
        if (array.size == 0) throw IllegalArgumentException("Give at least one edit operation in \"ops\".")
        return array.toString()
    }

    private fun operationsFromText(text: String): JsonArray = try {
        Json.parseToJsonElement(text).jsonArray
    } catch (e: Exception) {
        try {
            buildJsonArray { add(Json.parseToJsonElement(text).jsonObject) }
        } catch (e2: Exception) {
            throw IllegalArgumentException("The \"ops\" argument is not valid JSON: ${e.message ?: "parse error"}")
        }
    }

    private fun jsonObject(value: JsonElement?, label: String): JsonObject? = when (value) {
        null -> null
        is JsonNull -> null
        is JsonObject -> value
        is JsonPrimitive -> if (value.isString) {
            if (value.content.isBlank()) {
                null
            } else {
                try {
                    Json.parseToJsonElement(value.content).jsonObject
                } catch (e: Exception) {
                    throw IllegalArgumentException("The \"$label\" argument is not valid JSON: ${e.message ?: "parse error"}")
                }
            }
        } else {
            throw IllegalArgumentException("The \"$label\" argument must be a JSON object.")
        }
        else -> throw IllegalArgumentException("The \"$label\" argument must be a JSON object.")
    }
}
