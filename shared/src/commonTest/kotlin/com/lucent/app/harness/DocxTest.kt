package com.lucent.app.harness

import com.lucent.app.harness.ooxml.Docx
import com.lucent.app.harness.ooxml.parse
import com.lucent.app.harness.ooxml.readZip
import com.lucent.app.harness.ooxml.textOf
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocxTest {

    @Test
    fun createsEveryRequiredPackagePart() {
        val dir = tempDir()
        val out = File(dir, "report.docx")
        File(dir, "chart.png").writeBytes(tinyPng())
        val detail = Docx.create(reportSpec(), out)
        assertTrue(out.length() > 0L)
        assertTrue(detail.contains("paragraph"), "unexpected summary: $detail")
        val parts = readZip(out)
        val required = listOf(
            "[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/_rels/document.xml.rels",
            "word/styles.xml", "word/numbering.xml", "word/settings.xml", "word/header1.xml",
            "word/footer1.xml", "docProps/core.xml", "docProps/app.xml"
        )
        required.forEach { name -> assertTrue(parts.containsKey(name), "missing part $name") }
        assertTrue(parts.keys.any { it.startsWith("word/media/image") }, "no image part was written")
        parts.forEach { entry -> parseXmlPart(entry.key, entry.value) }
        assertEquals("[Content_Types].xml", firstZipEntry(out))
        val document = parts.getValue("word/document.xml").toString(Charsets.UTF_8)
        assertTrue(document.contains("TOC \\o"), "no TOC field was written")
        assertTrue(document.contains("w:drawing"), "no drawing was written")
        assertTrue(document.contains("w:headerReference"), "no header reference was written")
        assertTrue(document.contains("w:footerReference"), "no footer reference was written")
        assertTrue(parts.getValue("word/settings.xml").toString(Charsets.UTF_8).contains("updateFields"))
        assertTrue(parts.getValue("word/footer1.xml").toString(Charsets.UTF_8).contains("PAGE"))
        assertTrue(parts.getValue("word/styles.xml").toString(Charsets.UTF_8).contains("Heading1"))
        assertTrue(parts.getValue("word/numbering.xml").toString(Charsets.UTF_8).contains("abstractNum"))
    }

    @Test
    fun readsBackHeadingsListsTablesAndImages() {
        val dir = tempDir()
        val out = File(dir, "report.docx")
        File(dir, "chart.png").writeBytes(tinyPng())
        Docx.create(reportSpec(), out)
        val text = Docx.read(out)
        assertTrue(text.contains("Word document:"), "no summary line: $text")
        assertTrue(text.contains("# Quarterly Report"), "no heading: $text")
        assertTrue(text.contains("- First bullet"), "no bullet: $text")
        assertTrue(text.contains("1. First step"), "no numbered item: $text")
        assertTrue(text.contains("> Quoted words"), "no quote: $text")
        assertTrue(text.contains("| Name | Score |"), "no table header: $text")
        assertTrue(text.contains("| Ada | 9 |"), "no table row: $text")
        assertTrue(text.contains("[image word/media/image1.png]"), "no image marker: $text")
        assertTrue(text.contains("Revenue grew by"), "no paragraph text: $text")
    }

    @Test
    fun truncatesLongReads() {
        val dir = tempDir()
        val out = File(dir, "long.docx")
        Docx.create("""{"title":"Long","content":"${"word ".repeat(400)}"}""", out)
        val text = Docx.read(out, 200)
        assertTrue(text.length < 400, "read was not truncated")
        assertTrue(text.contains("truncated"))
    }

    @Test
    fun editReplacesAppendsAndRewritesHeader() {
        val dir = tempDir()
        val out = File(dir, "report.docx")
        File(dir, "chart.png").writeBytes(tinyPng())
        Docx.create(reportSpec(), out)
        val ops = """
            [
              {"op":"replace","find":"Ada","replace":"Grace","all":true},
              {"op":"append","content":"## Added Section\n\nFresh paragraph."},
              {"op":"set_header","text":"Draft 2"},
              {"op":"set_footer","text":"Page","page_numbers":true},
              {"op":"delete_paragraph","find":"Second bullet"}
            ]
        """.trimIndent()
        val summary = Docx.edit(out, ops)
        assertTrue(summary.contains("5"), "unexpected summary: $summary")
        val text = Docx.read(out)
        assertTrue(text.contains("Grace"), "replace did not apply: $text")
        assertFalse(text.contains("| Ada |"), "old text is still there: $text")
        assertTrue(text.contains("## Added Section"), "append did not apply: $text")
        assertTrue(text.contains("Fresh paragraph."), "append did not apply: $text")
        assertFalse(text.contains("Second bullet"), "delete_paragraph did not apply: $text")
        assertEquals("Draft 2", headerText(out, "word/header1.xml"))
        val footer = headerText(out, "word/footer1.xml")
        assertTrue(footer.contains("Page"), "footer text is missing: $footer")
        assertTrue(
            readZip(out).getValue("word/footer1.xml").toString(Charsets.UTF_8).contains("PAGE"),
            "footer page number field is missing"
        )
        readZip(out).forEach { entry -> parseXmlPart(entry.key, entry.value) }
    }

    @Test
    fun acceptsMarkdownOnlyContent() {
        val dir = tempDir()
        val out = File(dir, "note.docx")
        val markdown = "# Top\n\nSome **bold** text.\n\n- one\n- two\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n"
        Docx.create("""{"title":"Note","content":${quote(markdown)}}""", out)
        val text = Docx.read(out)
        assertTrue(text.contains("# Note"), "no title: $text")
        assertTrue(text.contains("# Top"), "no markdown heading: $text")
        assertTrue(text.contains("- one"), "no markdown bullet: $text")
        assertTrue(text.contains("| 1 | 2 |"), "no markdown table row: $text")
    }

    @Test
    fun addsMissingHeaderAndFooterParts() {
        val dir = tempDir()
        val out = File(dir, "plain.docx")
        Docx.create("""{"title":"Plain","content":"Body text."}""", out)
        assertFalse(readZip(out).containsKey("word/header1.xml"))
        Docx.edit(out, """[{"op":"set_header","text":"Added later"},{"op":"set_title","text":"Renamed"}]""")
        val parts = readZip(out)
        assertTrue(parts.containsKey("word/header1.xml"), "header part was not created")
        assertEquals("Added later", headerText(out, "word/header1.xml"))
        assertTrue(Docx.read(out).contains("# Renamed"), "title was not replaced")
        parts.forEach { entry -> parseXmlPart(entry.key, entry.value) }
    }

    @Test
    fun rejectsBadInput() {
        val dir = tempDir()
        assertFailsWith<IllegalArgumentException> { Docx.create("{not json", File(dir, "bad.docx")) }
        assertFailsWith<IllegalArgumentException> {
            Docx.create("""{"blocks":[{"type":"nope","text":"x"}]}""", File(dir, "bad.docx"))
        }
        assertFailsWith<IllegalArgumentException> { Docx.create("""{"blocks":[{"type":"image"}]}""", File(dir, "bad.docx")) }
        val plain = File(dir, "notes.txt")
        plain.writeText("not a package")
        assertFailsWith<IllegalArgumentException> { Docx.read(plain) }
        assertFailsWith<IllegalArgumentException> { Docx.edit(File(dir, "missing.docx"), """[{"op":"append","content":"x"}]""") }
        assertFailsWith<IllegalArgumentException> { Docx.edit(plain, "[]") }
    }

    private fun reportSpec(): String = """
        {
          "title": "Quarterly Report",
          "author": "Ada Lovelace",
          "header": "Confidential",
          "footer": "Lucent",
          "page_numbers": true,
          "toc": true,
          "blocks": [
            {"type":"heading","level":1,"text":"Quarterly Report"},
            {"type":"paragraph","text":"Revenue grew by **12%** this quarter.","align":"center",
             "style":{"italic":true,"colour":"#C00000","size":14,"font":"Arial","highlight":"yellow","spacing":1.5}},
            {"type":"bullets","items":["First bullet","Second bullet"]},
            {"type":"numbers","items":["First step","Second step"]},
            {"type":"table","header":["Name","Score"],"rows":[["Ada","9"],["Grace","10"]],"widths":[3000,3000]},
            {"type":"image","path":"chart.png","width":240,"caption":"Growth"},
            {"type":"pagebreak"},
            {"type":"quote","text":"Quoted words"},
            {"type":"code","text":"val total = 1 + 2"},
            {"type":"link","text":"Lucent","url":"https://example.com"},
            {"type":"rule"}
          ]
        }
    """.trimIndent()

    private fun tempDir(): File = Files.createTempDirectory("lucent-docx-test").toFile()

    private fun quote(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun parseXmlPart(name: String, bytes: ByteArray) {
        if (!name.endsWith(".xml") && !name.endsWith(".rels")) return
        try {
            parse(bytes)
        } catch (e: Exception) {
            throw AssertionError("part $name is not valid XML: ${e.message}", e)
        }
    }

    private fun headerText(file: File, part: String): String {
        val bytes = readZip(file)[part] ?: return ""
        return textOf(parse(bytes).documentElement).trim()
    }

    private fun firstZipEntry(file: File): String {
        ZipInputStream(file.inputStream()).use { zip ->
            val entry = zip.nextEntry
            return entry?.name ?: ""
        }
    }

    private fun tinyPng(width: Int = 2, height: Int = 2): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(
            byteArrayOf(
                0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
                0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
            )
        )
        val header = ByteArrayOutputStream()
        val data = DataOutputStream(header)
        data.writeInt(width)
        data.writeInt(height)
        data.writeByte(8)
        data.writeByte(2)
        data.writeByte(0)
        data.writeByte(0)
        data.writeByte(0)
        data.flush()
        pngChunk(out, "IHDR", header.toByteArray())
        val raw = ByteArrayOutputStream()
        for (row in 0 until height) {
            raw.write(0)
            for (column in 0 until width) {
                raw.write(0x40)
                raw.write(0x80)
                raw.write(0xC0)
            }
        }
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed).use { stream -> stream.write(raw.toByteArray()) }
        pngChunk(out, "IDAT", compressed.toByteArray())
        pngChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun pngChunk(out: ByteArrayOutputStream, type: String, payload: ByteArray) {
        val size = payload.size
        out.write(byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()))
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(payload)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(payload)
        val value = crc.value
        out.write(
            byteArrayOf(
                (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()
            )
        )
    }
}
