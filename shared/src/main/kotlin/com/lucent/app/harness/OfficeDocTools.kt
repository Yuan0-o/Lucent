package com.lucent.app.harness

import com.lucent.app.harness.ooxml.Docx
import com.lucent.app.harness.ooxml.stringOf
import com.lucent.app.network.ToolExecResult
import org.json.JSONArray
import org.json.JSONObject

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

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? {
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

    private fun createDocument(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forWrite(ctx, stringOf(args, "path"))
        if (file.isDirectory) {
            return ToolExecResult("${Workspace.display(ctx, file)} is a directory, not a .docx file.", success = false)
        }
        val spec = documentSpec(args)
        if (ctx.config.snapshots && file.exists()) Snapshots.capture(ctx, file)
        file.parentFile?.mkdirs()
        val detail = Docx.create(spec, file)
        return ToolExecResult(
            "Created ${Workspace.display(ctx, file)} (${Workspace.humanSize(file.length())}): $detail."
        )
    }

    private fun readDocument(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forRead(ctx, stringOf(args, "path"))
        if (file.isDirectory) {
            return ToolExecResult("${Workspace.display(ctx, file)} is a directory, not a .docx file.", success = false)
        }
        val maxChars = args.optInt("max_chars", 20000).coerceIn(500, 400000)
        return ToolExecResult(Docx.read(file, maxChars))
    }

    private fun editDocument(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forWrite(ctx, stringOf(args, "path"))
        if (file.isDirectory) {
            return ToolExecResult("${Workspace.display(ctx, file)} is a directory, not a .docx file.", success = false)
        }
        if (!file.exists()) {
            return ToolExecResult("${Workspace.display(ctx, file)} does not exist yet.", success = false)
        }
        val ops = operations(args)
        if (ctx.config.snapshots) Snapshots.capture(ctx, file)
        val detail = Docx.edit(file, ops)
        return ToolExecResult(
            "Edited ${Workspace.display(ctx, file)} (${Workspace.humanSize(file.length())}): $detail."
        )
    }

    private fun documentSpec(args: JSONObject): String {
        val explicit = jsonObject(args.opt("spec"), "spec")
        val root = if (explicit == null) JSONObject() else JSONObject(explicit.toString())
        val title = stringOf(args, "title")
        if (title.isNotBlank() && !root.has("title")) root.put("title", title)
        val author = stringOf(args, "author")
        if (author.isNotBlank() && !root.has("author")) root.put("author", author)
        val content = stringOf(args, "content")
        if (content.isNotBlank() && !root.has("content") && !root.has("blocks")) root.put("content", content)
        if (!root.has("content") && !root.has("blocks") && !root.has("title")) {
            throw IllegalArgumentException(
                "Give the document some text with \"content\", or a full \"spec\" with a title or a blocks array."
            )
        }
        return root.toString()
    }

    private fun operations(args: JSONObject): String {
        val raw = args.opt("ops")
        val array = when (raw) {
            is JSONArray -> raw
            is JSONObject -> JSONArray().put(raw)
            is String -> if (raw.isBlank()) JSONArray() else operationsFromText(raw)
            else -> JSONArray()
        }
        if (array.length() == 0) throw IllegalArgumentException("Give at least one edit operation in \"ops\".")
        return array.toString()
    }

    private fun operationsFromText(text: String): JSONArray = try {
        JSONArray(text)
    } catch (e: Exception) {
        try {
            JSONArray().put(JSONObject(text))
        } catch (e2: Exception) {
            throw IllegalArgumentException("The \"ops\" argument is not valid JSON: ${e.message ?: "parse error"}")
        }
    }

    private fun jsonObject(value: Any?, label: String): JSONObject? = when (value) {
        null -> null
        JSONObject.NULL -> null
        is JSONObject -> value
        is String -> if (value.isBlank()) {
            null
        } else {
            try {
                JSONObject(value)
            } catch (e: Exception) {
                throw IllegalArgumentException("The \"$label\" argument is not valid JSON: ${e.message ?: "parse error"}")
            }
        }
        else -> throw IllegalArgumentException("The \"$label\" argument must be a JSON object.")
    }
}
