package com.lucent.app.harness

import com.lucent.app.harness.ooxml.Pptx
import com.lucent.app.network.ToolExecResult
import org.json.JSONArray
import org.json.JSONObject

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

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = try {
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

    private fun create(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val out = Workspace.forWrite(ctx, args.optString("path", ""))
        val spec = deckSpec(args)
        if (spec.length() == 0) {
            return ToolExecResult(
                "Give a spec object (title, slides) or markdown content to turn into slides.",
                success = false
            )
        }
        val hasSlides = (spec.optJSONArray("slides")?.length() ?: 0) > 0
        val hasContent = spec.optString("content", "").isNotBlank()
        if (!hasSlides && !hasContent) {
            return ToolExecResult(
                "The spec has neither slides nor content, so there is nothing to build.",
                success = false
            )
        }
        out.parentFile?.mkdirs()
        if (ctx.config.snapshots && out.exists()) Snapshots.capture(ctx, out)
        val summary = Pptx.create(spec.toString(), out)
        return ToolExecResult("Wrote ${Workspace.display(ctx, out)}: $summary")
    }

    private fun deckSpec(args: JSONObject): JSONObject {
        val raw = args.opt("spec")
        val spec = when (raw) {
            is JSONObject -> JSONObject(raw.toString())
            is JSONArray -> JSONObject().put("slides", raw)
            is String -> {
                val text = raw.trim()
                if (text.startsWith("{")) {
                    try {
                        JSONObject(text)
                    } catch (e: Exception) {
                        JSONObject().put("content", text)
                    }
                } else if (text.isNotEmpty()) {
                    JSONObject().put("content", text)
                } else {
                    JSONObject()
                }
            }
            else -> JSONObject()
        }
        val content = args.optString("content", "").trim()
        if (content.isNotEmpty() && spec.optString("content", "").isBlank()) spec.put("content", content)
        val title = args.optString("title", "").trim()
        if (title.isNotEmpty() && spec.optString("title", "").isBlank()) spec.put("title", title)
        return spec
    }

    private fun read(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forRead(ctx, args.optString("path", ""))
        val maxChars = args.optInt("max_chars", 20000)
        val shown = Workspace.display(ctx, file)
        val text = Pptx.read(file, maxChars)
        return ToolExecResult("$shown (${Workspace.humanSize(file.length())})\n$text")
    }

    private fun edit(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forWrite(ctx, args.optString("path", ""))
        val ops = opsJson(args.opt("ops"))
        if (ops.isBlank()) return ToolExecResult("Give at least one operation in ops.", success = false)
        if (ctx.config.snapshots && file.exists()) Snapshots.capture(ctx, file)
        val summary = Pptx.edit(file, ops)
        return ToolExecResult("Edited ${Workspace.display(ctx, file)}\n$summary")
    }

    private fun opsJson(value: Any?): String = when (value) {
        is JSONArray -> value.toString()
        is JSONObject -> value.toString()
        is String -> value.trim()
        else -> ""
    }
}
