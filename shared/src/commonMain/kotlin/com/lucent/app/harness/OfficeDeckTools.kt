package com.lucent.app.harness

import okio.Path.Companion.toPath

import com.lucent.app.harness.ooxml.Pptx
import com.lucent.app.network.ToolExecResult
import kotlinx.serialization.json.*

object OfficeDeckTools : HarnessGroupTools {

    override val group = HarnessGroup.OFFICE

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "create_presentation",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Build a native PowerPoint .pptx file that opens in PowerPoint, LibreOffice Impress, WPS and " +
                "Google Slides. Pass spec as JSON: {\"title\":\"...\",\"subtitle\":\"...\",\"size\":\"16:9\"," +
                "\"author\":\"...\",\"theme\":{\"accent1\":\"#4472C4\",\"font\":\"Calibri\"},\"slides\":[{\"layout\":" +
                "\"title|bullets|section|two_content|image|table|chart|blank\",\"title\":\"...\",\"bullets\":[\"...\"]," +
                "\"notes\":\"...\",\"image\":{\"path\":\"...\",\"x\":0.6,\"y\":1.6,\"w\":8,\"h\":4.5,\"caption\":\"...\"}," +
                "\"table\":{\"header\":[\"A\",\"B\"],\"rows\":[[\"1\",\"2\"]]},\"chart\":{\"type\":\"bar|line|pie\"," +
                "\"title\":\"...\",\"categories\":[\"Q1\"],\"series\":[{\"name\":\"S\",\"values\":[1,2]}]}}]}. Simpler " +
                "still: pass content as Markdown and level-1 headings become slides with their list items as bullets.",
            params = listOf(
                HarnessSchema.text("path", "Where to write the .pptx file"),
                HarnessSchema.json("spec", "Deck specification as JSON", false),
                HarnessSchema.text("content", "Markdown to turn into slides instead of a spec", false),
                HarnessSchema.text("title", "Deck title when the spec has none", false)
            )
        ),
        HarnessTool(
            name = "read_presentation",
            group = group,
            permission = HarnessPermission.READ,
            description = "Read a .pptx back as text: one block per slide with its title, bullets, pipe tables, " +
                "images, a description of each chart and the speaker notes. Arguments: path, max_chars.",
            params = listOf(
                HarnessSchema.text("path", "PowerPoint file to read"),
                HarnessSchema.number("max_chars", "Stop after roughly this many characters (default 20000)", false)
            )
        ),
        HarnessTool(
            name = "edit_presentation",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Change an existing .pptx with a list of operations, given as a JSON array in ops. Slides " +
                "are numbered from 1. Operations: append_slide (layout, title, bullets, notes), replace (find, " +
                "replace, all), set_title (slide, text), set_notes (slide, text), add_bullet (slide, text), " +
                "insert_image (slide, path, x, y, w, h), delete_slide (slide) and set_theme_colour (name, value).",
            params = listOf(
                HarnessSchema.text("path", "PowerPoint file to edit"),
                HarnessSchema.list("ops", "Operations to apply, in order", itemType = "object")
            )
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JsonObject): ToolExecResult? = try {
        when (name) {
            "create_presentation" -> create(ctx, args)
            "read_presentation" -> read(ctx, args)
            "edit_presentation" -> edit(ctx, args)
            else -> null
        }
    } catch (e: HarnessError) {
        ToolExecResult(e.message ?: "That path cannot be used", success = false)
    } catch (e: Exception) {
        ToolExecResult("$name failed: ${e.message ?: e::class.simpleName}", success = false)
    }

    private fun create(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val out = Workspace.forWriteFile(ctx, args["path"]?.jsonPrimitive?.content ?: "")
        val spec = deckSpec(args)
        if (spec.size == 0) {
            return ToolExecResult(
                "Give a spec object (title, slides) or markdown content to turn into slides.",
                success = false
            )
        }
        val hasSlides = (spec["slides"]?.jsonArray?.size ?: 0) > 0
        val hasContent = (spec["content"]?.jsonPrimitive?.content ?: "").isNotBlank()
        if (!hasSlides && !hasContent) {
            return ToolExecResult(
                "The spec has neither slides nor content, so there is nothing to build.",
                success = false
            )
        }
        out.parent?.let { okio.FileSystem.SYSTEM.createDirectories(it) }
        if (ctx.config.snapshots && okio.FileSystem.SYSTEM.exists(out)) Snapshots.capture(ctx, out)
        val summary = Pptx.create(spec.toString(), out)
        return ToolExecResult("Wrote ${Workspace.display(ctx, out)}: $summary")
    }

    private fun deckSpec(args: JsonObject): JsonObject {
        val raw = args["spec"]
        val spec = when (raw) {
            is JsonObject -> raw
            is JsonArray -> buildJsonObject { put("slides", raw) }
            is JsonPrimitive -> {
                if (raw.isString) {
                    val text = raw.content.trim()
                    if (text.startsWith("{")) {
                        try {
                            Json.parseToJsonElement(text).jsonObject
                        } catch (e: Exception) {
                            buildJsonObject { put("content", text) }
                        }
                    } else if (text.isNotEmpty()) {
                        buildJsonObject { put("content", text) }
                    } else {
                        JsonObject(emptyMap())
                    }
                } else {
                    JsonObject(emptyMap())
                }
            }
            else -> JsonObject(emptyMap())
        }
        val content = (args["content"]?.jsonPrimitive?.content ?: "").trim()
        val title = (args["title"]?.jsonPrimitive?.content ?: "").trim()
        
        if ((content.isNotEmpty() && (spec["content"]?.jsonPrimitive?.content ?: "").isBlank()) ||
            (title.isNotEmpty() && (spec["title"]?.jsonPrimitive?.content ?: "").isBlank())) {
            return buildJsonObject {
                spec.forEach { k, v -> put(k, v) }
                if (content.isNotEmpty() && (spec["content"]?.jsonPrimitive?.content ?: "").isBlank()) put("content", content)
                if (title.isNotEmpty() && (spec["title"]?.jsonPrimitive?.content ?: "").isBlank()) put("title", title)
            }
        }
        return spec
    }

    private fun read(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val file = Workspace.forReadFile(ctx, args["path"]?.jsonPrimitive?.content ?: "")
        val maxChars = args["max_chars"]?.jsonPrimitive?.intOrNull ?: 20000
        val shown = Workspace.display(ctx, file)
        val text = Pptx.read(file, maxChars)
        return ToolExecResult("$shown (${Workspace.humanSize(okio.FileSystem.SYSTEM.metadataOrNull(file)?.size ?: 0L)})\n$text")
    }

    private fun edit(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val file = Workspace.forWriteFile(ctx, args["path"]?.jsonPrimitive?.content ?: "")
        val ops = opsJson(args["ops"])
        if (ops.isBlank()) return ToolExecResult("Give at least one operation in ops.", success = false)
        if (ctx.config.snapshots && okio.FileSystem.SYSTEM.exists(file)) Snapshots.capture(ctx, file)
        val summary = Pptx.edit(file, ops)
        return ToolExecResult("Edited ${Workspace.display(ctx, file)}\n$summary")
    }

    private fun opsJson(value: JsonElement?): String = when (value) {
        is JsonArray -> value.toString()
        is JsonObject -> value.toString()
        is JsonPrimitive -> if (value.isString) value.content.trim() else value.toString()
        else -> ""
    }
}
