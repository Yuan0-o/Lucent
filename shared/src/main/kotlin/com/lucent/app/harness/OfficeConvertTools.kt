package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import com.lucent.app.network.ToolImage
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

object OfficeConvertTools : HarnessGroupTools {

    override val group = HarnessGroup.OFFICE

    private const val SOFFICE = "soffice"

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "convert_office",
            group = group,
            permission = HarnessPermission.EXECUTE,
            description = "Convert a document with LibreOffice: docx, xlsx, pptx, odt, rtf, html, csv and pdf are all " +
                "accepted. Arguments: path, target (pdf, docx, xlsx, pptx, csv, txt, png), out (optional).",
            params = listOf(
                HarnessSchema.text("path", "Document to convert"),
                HarnessSchema.text("target", "Format to convert to, for example pdf"),
                HarnessSchema.text("out", "Output path", false)
            ),
            requires = "libreoffice"
        ),
        HarnessTool(
            name = "render_office",
            group = group,
            permission = HarnessPermission.EXECUTE,
            description = "Render a document to images so you can look at the result and check the layout. Converts to " +
                "PDF first, then draws up to four pages. Use it after creating or editing a deck or a report.",
            params = listOf(
                HarnessSchema.text("path", "Document to render"),
                HarnessSchema.text("pages", "Pages to draw, for example 1-4", false),
                HarnessSchema.number("width", "Pixel width of each image", false)
            ),
            requires = "libreoffice"
        ),
        HarnessTool(
            name = "office_doctor",
            group = group,
            permission = HarnessPermission.READ,
            description = "Check an Office file for structural damage: lists its internal parts and reports anything " +
                "missing or unreadable. Cheap, and worth running when a file will not open.",
            params = listOf(HarnessSchema.text("path", "Office file to inspect"))
        ),
        HarnessTool(
            name = "document_text",
            group = group,
            permission = HarnessPermission.READ,
            description = "Extract the plain text of any document Lucent can read: docx, xlsx, pptx, pdf, rtf, csv, " +
                "markdown or code. Handy for summarising material you did not create.",
            params = listOf(HarnessSchema.text("path", "Document to read"))
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "convert_office" -> convert(ctx, args)
        "render_office" -> render(ctx, args)
        "office_doctor" -> doctor(ctx, args)
        "document_text" -> documentText(ctx, args)
        else -> null
    }

    private fun sofficeCall(ctx: HarnessCtx, input: File, target: String, outDir: File): ShellOutcome {
        val command = buildString {
            append(SOFFICE)
            append(" --headless --norestore --nolockcheck --nodefault --nofirststartwizard")
            append(" --convert-to ").append(target)
            append(" --outdir '").append(outDir.path.replace("'", "'\\''")).append("'")
            append(" '").append(input.path.replace("'", "'\\''")).append("'")
            append(" 2>&1")
        }
        return HarnessRuntime.runShell(command, outDir, 600)
    }

    private fun convert(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val input = try {
            Workspace.forRead(ctx, args.optString("path", ""))
        } catch (e: HarnessError) {
            return ToolExecResult(e.message ?: "That file cannot be read", success = false)
        }
        val target = args.optString("target", "pdf").lowercase().removePrefix(".")
        if (target.isBlank()) return ToolExecResult("Which format?", success = false)
        if (!HarnessRuntime.shellReady()) {
            return ToolExecResult("Converting needs a shell: install the libreoffice plugin first.", success = false)
        }
        val outDir = if (args.optString("out", "").isBlank()) input.parentFile
        else Workspace.forWrite(ctx, args.optString("out", "")).parentFile ?: input.parentFile
        outDir.mkdirs()
        val outcome = sofficeCall(ctx, input, target, outDir)
        val produced = outDir.listFiles()?.firstOrNull {
            it.name.startsWith(input.nameWithoutExtension + ".") && it.extension.equals(target, ignoreCase = true)
        }
        if (produced == null) {
            return ToolExecResult(
                "LibreOffice did not produce a .$target file.\n" + ctx.limit(outcome.text).take(2000),
                success = false
            )
        }
        val asked = args.optString("out", "")
        val finalFile = if (asked.isNotBlank()) {
            val wanted = Workspace.forWrite(ctx, asked)
            wanted.parentFile?.mkdirs()
            produced.copyTo(wanted, overwrite = true)
            wanted
        } else produced
        return ToolExecResult(
            "Converted ${Workspace.display(ctx, input)} to ${Workspace.display(ctx, finalFile)} " +
                "(${Workspace.humanSize(finalFile.length())})."
        )
    }

    private suspend fun render(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val input = try {
            Workspace.forRead(ctx, args.optString("path", ""))
        } catch (e: HarnessError) {
            return ToolExecResult(e.message ?: "That file cannot be read", success = false)
        }
        if (!HarnessRuntime.shellReady()) {
            return ToolExecResult("Rendering needs a shell: install the libreoffice plugin first.", success = false)
        }
        val work = File(HarnessRuntime.subDir("render"), input.nameWithoutExtension)
        work.mkdirs()
        val pdf = if (input.extension.equals("pdf", true)) input else {
            val outcome = sofficeCall(ctx, input, "pdf", work)
            work.listFiles()?.firstOrNull { it.extension.equals("pdf", true) }
                ?: return ToolExecResult(
                    "Could not render ${Workspace.display(ctx, input)}.\n" + ctx.limit(outcome.text).take(2000),
                    success = false
                )
        }
        val pages = pageList(args.optString("pages", "1-4"))
        val width = args.optInt("width", 1280).coerceIn(320, 3000)
        val images = mutableListOf<ToolImage>()
        val written = mutableListOf<String>()
        val host = HarnessRuntime.host
        for (page in pages.take(4)) {
            val bytes = host?.renderPdfPage(pdf.path, page, width)
            if (bytes == null || bytes.isEmpty()) break
            val file = File(work, "${input.nameWithoutExtension}-p$page.png")
            file.writeBytes(bytes)
            written.add(Workspace.display(ctx, file))
            images.add(
                ToolImage(
                    "image/png",
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
                    file.name
                )
            )
        }
        if (images.isEmpty()) {
            return ToolExecResult(
                "Converted to PDF (${Workspace.display(ctx, pdf)}) but this build cannot draw PDF pages to images.",
                success = false
            )
        }
        return ToolExecResult(
            "Rendered page${if (written.size == 1) "" else "s"} ${written.joinToString(", ")}. Look at the image" +
                "${if (images.size == 1) "" else "s"} above and fix anything that overflows or looks wrong.",
            images = images
        )
    }

    private fun pageList(spec: String): List<Int> {
        val pages = mutableListOf<Int>()
        spec.split(',').forEach { part ->
            val piece = part.trim()
            if (piece.isEmpty()) return@forEach
            if (piece.contains('-')) {
                val bounds = piece.split('-')
                val from = bounds.getOrNull(0)?.trim()?.toIntOrNull() ?: return@forEach
                val to = bounds.getOrNull(1)?.trim()?.toIntOrNull() ?: from
                for (page in from..to) if (page > 0 && pages.size < 30) pages.add(page)
            } else {
                piece.toIntOrNull()?.let { if (it > 0 && pages.size < 30) pages.add(it) }
            }
        }
        return if (pages.isEmpty()) listOf(1) else pages
    }

    private fun doctor(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = try {
            Workspace.forRead(ctx, args.optString("path", ""))
        } catch (e: HarnessError) {
            return ToolExecResult(e.message ?: "That file cannot be read", success = false)
        }
        if (!file.extension.lowercase().let { it == "docx" || it == "xlsx" || it == "pptx" || it == "odt" || it == "zip" }) {
            return ToolExecResult("${Workspace.display(ctx, file)} is not an Office package.", success = false)
        }
        val parts = mutableListOf<String>()
        val bad = mutableListOf<String>()
        try {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    parts.add(entry.name)
                    if (entry.name.endsWith(".xml") || entry.name.endsWith(".rels")) {
                        val bytes = zip.readBytes()
                        try {
                            javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                                .parse(bytes.inputStream())
                        } catch (e: Exception) {
                            bad.add("${entry.name}: ${e.message ?: "unreadable"}")
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            return ToolExecResult("${Workspace.display(ctx, file)} could not be opened: ${e.message}", success = false)
        }
        val sb = StringBuilder()
        sb.append("${Workspace.display(ctx, file)} — ${parts.size} parts, ${Workspace.humanSize(file.length())}\n")
        sb.append("Required parts: ")
        val required = listOf("[Content_Types].xml", "_rels/.rels")
        sb.append(required.joinToString(", ") { "$it=${if (parts.contains(it)) "ok" else "missing"}" }).append('\n')
        sb.append("Main document: ")
        sb.append(parts.firstOrNull { it.startsWith("word/") || it.startsWith("xl/") || it.startsWith("ppt/") } ?: "none")
        sb.append('\n')
        if (bad.isEmpty()) sb.append("Every XML part parses. The package looks intact.")
        else sb.append("Problems:\n").append(bad.joinToString("\n"))
        return ToolExecResult(sb.toString(), success = bad.isEmpty())
    }

    private fun documentText(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = try {
            Workspace.forRead(ctx, args.optString("path", ""))
        } catch (e: HarnessError) {
            return ToolExecResult(e.message ?: "That file cannot be read", success = false)
        }
        return when (file.extension.lowercase()) {
            "pdf" -> {
                val text = HarnessRuntime.host?.let { host -> kotlinx.coroutines.runBlocking { host.readPdfText(file.path) } }.orEmpty()
                if (text.isBlank()) ToolExecResult("No text could be pulled out of that PDF here.", success = false)
                else ToolExecResult(ctx.limit(text))
            }
            else -> try {
                ToolExecResult(ctx.limit(Workspace.readText(file, 512 * 1024)))
            } catch (e: HarnessError) {
                ToolExecResult(e.message ?: "That file is not text", success = false)
            }
        }
    }
}
