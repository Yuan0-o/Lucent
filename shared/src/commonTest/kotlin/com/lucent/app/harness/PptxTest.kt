package com.lucent.app.harness

import com.lucent.app.harness.ooxml.Pptx
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class PptxTest {

    @Test
    fun createWritesACompletePackage() {
        val dir = tempDir()
        val deck = buildSample(dir)
        assertTrue(deck.exists(), "the deck was not written")
        assertTrue(deck.length() > 2000, "the deck is suspiciously small")
        val entries = zipEntries(deck)
        val required = listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "docProps/core.xml",
            "docProps/app.xml",
            "ppt/presentation.xml",
            "ppt/_rels/presentation.xml.rels",
            "ppt/presProps.xml",
            "ppt/viewProps.xml",
            "ppt/tableStyles.xml",
            "ppt/theme/theme1.xml",
            "ppt/slideMasters/slideMaster1.xml",
            "ppt/slideMasters/_rels/slideMaster1.xml.rels",
            "ppt/notesMasters/notesMaster1.xml",
            "ppt/notesMasters/_rels/notesMaster1.xml.rels",
            "ppt/slides/slide1.xml",
            "ppt/slides/_rels/slide1.xml.rels",
            "ppt/slides/slide5.xml",
            "ppt/slides/_rels/slide5.xml.rels",
            "ppt/notesSlides/notesSlide1.xml",
            "ppt/notesSlides/_rels/notesSlide1.xml.rels",
            "ppt/charts/chart1.xml",
            "ppt/charts/_rels/chart1.xml.rels",
            "ppt/embeddings/Microsoft_Excel_Worksheet1.xlsx",
            "ppt/media/image1.png"
        )
        required.forEach { name -> assertTrue(entries.containsKey(name), "the package is missing $name") }
        for (index in 1..5) {
            assertTrue(
                entries.containsKey("ppt/slideLayouts/slideLayout$index.xml"),
                "slideLayout$index.xml is missing"
            )
            assertTrue(
                entries.containsKey("ppt/slideLayouts/_rels/slideLayout$index.xml.rels"),
                "the relationship part for slideLayout$index.xml is missing"
            )
        }
    }

    @Test
    fun everyPartIsWellFormedAndEveryRelationshipResolves() {
        val dir = tempDir()
        val deck = buildSample(dir)
        val entries = zipEntries(deck)
        entries.forEach { (name, bytes) ->
            if (name.endsWith(".xml") || name.endsWith(".rels")) {
                try {
                    parseXml(bytes)
                } catch (e: Exception) {
                    fail("$name is not well formed XML: ${e.message}")
                }
            }
        }
        assertRelationshipsResolve(entries)
        val workbook = entries.getValue("ppt/embeddings/Microsoft_Excel_Worksheet1.xlsx")
        val bookEntries = inflateZip(workbook)
        assertTrue(bookEntries.containsKey("xl/workbook.xml"), "the embedded workbook has no workbook part")
        assertTrue(bookEntries.containsKey("xl/worksheets/sheet1.xml"), "the embedded workbook has no sheet")
        bookEntries.forEach { (name, bytes) ->
            if (name.endsWith(".xml") || name.endsWith(".rels")) {
                try {
                    parseXml(bytes)
                } catch (e: Exception) {
                    fail("the embedded workbook part $name is not well formed XML: ${e.message}")
                }
            }
        }
        val chartRels = entries.getValue("ppt/charts/_rels/chart1.xml.rels")
        val text = String(chartRels, Charsets.UTF_8)
        assertTrue(text.contains("Microsoft_Excel_Worksheet1.xlsx"), "the chart does not point at its workbook")
        val chart = String(entries.getValue("ppt/charts/chart1.xml"), Charsets.UTF_8)
        assertTrue(chart.contains("externalData"), "the chart has no externalData reference")
        assertTrue(chart.contains("barChart"), "the chart is not a bar chart")
    }

    @Test
    fun readShowsTitlesBulletsTablesChartsAndNotes() {
        val dir = tempDir()
        val deck = buildSample(dir)
        val text = Pptx.read(deck)
        assertTrue(text.contains("Slides: 5"), "the header does not report the slide count")
        assertTrue(text.contains("16:9"), "the header does not report the slide size")
        assertTrue(text.contains("# Slide 1"), "slide 1 is missing")
        assertTrue(text.contains("Quarterly review"), "the deck title is missing")
        assertTrue(text.contains("Highlights"), "a slide title is missing")
        assertTrue(text.contains("- Revenue up 12%"), "a bullet is missing")
        assertTrue(text.contains("| Region | Sales |"), "the table header is missing")
        assertTrue(text.contains("| North | 120 |"), "a table row is missing")
        assertTrue(text.contains("[image"), "the image is not described")
        assertTrue(text.contains("[chart bar: categories Q1, Q2; Revenue: 10, 14]"), "the chart is not described")
        assertTrue(text.contains("Notes: Welcome everyone"), "the speaker notes are missing")
        assertTrue(text.contains("Notes: Keep this short"), "the notes on slide 2 are missing")
    }

    @Test
    fun markdownContentBecomesSlides() {
        val dir = tempDir()
        val out = File(dir, "reading.pptx")
        val spec = "{\"title\":\"Reading list\",\"content\":\"# Reading list\\n\\n- One\\n- Two\\n\\n" +
            "## Part two\\n\\n- Three\\n\"}"
        Pptx.create(spec, out)
        val text = Pptx.read(out)
        assertTrue(text.contains("Reading list"), "the deck title is missing")
        assertTrue(text.contains("- One"), "the first bullet is missing")
        assertTrue(text.contains("- Two"), "the second bullet is missing")
        assertTrue(text.contains("Part two"), "the section heading is missing")
        assertTrue(text.contains("- Three"), "the section bullet is missing")
    }

    @Test
    fun editAppendsASlideAndReplacesText() {
        val dir = tempDir()
        val deck = buildSample(dir)
        val ops = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("op", "append_slide")
                    put("layout", "bullets")
                    put("title", "Next steps")
                    put("bullets", JSONArray(listOf("Ship it", "Tell everyone")))
                    put("notes", "Owner: Sam")
                }
            )
            put(
                JSONObject().apply {
                    put("op", "replace")
                    put("find", "Revenue up 12%")
                    put("replace", "Revenue up 15%")
                    put("all", true)
                }
            )
            put(
                JSONObject().apply {
                    put("op", "set_title")
                    put("slide", 3)
                    put("text", "Sales by region")
                }
            )
        }
        val summary = Pptx.edit(deck, ops.toString())
        assertTrue(summary.contains("6 slide"), "the summary does not report the new slide count")
        assertTrue(summary.contains("appended slide 6"), "the append was not reported")
        assertTrue(summary.contains("replaced 1 occurrence"), "the replacement was not reported")
        val text = Pptx.read(deck)
        assertTrue(text.contains("# Slide 6"), "the appended slide is missing")
        assertTrue(text.contains("- Ship it"), "the appended bullet is missing")
        assertTrue(text.contains("Notes: Owner: Sam"), "the appended notes are missing")
        assertTrue(text.contains("- Revenue up 15%"), "the replacement text is missing")
        assertFalse(text.contains("Revenue up 12%"), "the old text is still there")
        assertTrue(text.contains("Sales by region"), "the new slide title is missing")
    }

    @Test
    fun editRejectsAnUnknownOperation() {
        val dir = tempDir()
        val deck = buildSample(dir)
        val failure = try {
            Pptx.edit(deck, "[{\"op\":\"teleport_slide\",\"slide\":1}]")
            null
        } catch (e: Exception) {
            e
        }
        assertTrue(failure is IllegalArgumentException, "an unknown op should be refused")
    }

    private fun buildSample(dir: File): File {
        val image = File(dir, "pixel.png")
        image.writeBytes(tinyPng())
        val deck = File(dir, "deck.pptx")
        val spec = JSONObject().apply {
            put("title", "Quarterly review")
            put("subtitle", "Numbers that matter")
            put("author", "Lucent")
            put("size", "16:9")
            put(
                "theme",
                JSONObject().apply {
                    put("accent1", "#4472C4")
                    put("accent2", "#ED7D31")
                    put("font", "Calibri")
                }
            )
            put(
                "slides",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("layout", "title")
                            put("title", "Quarterly review")
                            put("subtitle", "Numbers that matter")
                            put("notes", "Welcome everyone")
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("layout", "bullets")
                            put("title", "Highlights")
                            put("bullets", JSONArray(listOf("Revenue up 12%", "Churn down")))
                            put("notes", "Keep this short")
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("layout", "table")
                            put("title", "By region")
                            put(
                                "table",
                                JSONObject().apply {
                                    put("header", JSONArray(listOf("Region", "Sales")))
                                    put(
                                        "rows",
                                        JSONArray().apply {
                                            put(JSONArray(listOf("North", "120")))
                                            put(JSONArray(listOf("South", "98")))
                                        }
                                    )
                                }
                            )
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("layout", "image")
                            put("title", "Trend")
                            put(
                                "image",
                                JSONObject().apply {
                                    put("path", image.path)
                                    put("x", 0.6)
                                    put("y", 1.6)
                                    put("w", 8.0)
                                    put("h", 4.5)
                                    put("caption", "Revenue by quarter")
                                }
                            )
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("layout", "chart")
                            put("title", "Revenue")
                            put(
                                "chart",
                                JSONObject().apply {
                                    put("type", "bar")
                                    put("title", "Revenue by quarter")
                                    put("categories", JSONArray(listOf("Q1", "Q2")))
                                    put(
                                        "series",
                                        JSONArray().apply {
                                            put(
                                                JSONObject().apply {
                                                    put("name", "Revenue")
                                                    put("values", JSONArray(listOf(10, 14)))
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
        Pptx.create(spec.toString(), deck)
        return deck
    }

    private fun assertRelationshipsResolve(entries: Map<String, ByteArray>) {
        entries.forEach { (name, bytes) ->
            if (!name.endsWith(".rels")) return@forEach
            val document = parseXml(bytes)
            val base = if (name == "_rels/.rels") "" else name.substringBeforeLast("/_rels")
            val nodes = document.getElementsByTagName("Relationship")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                if (element.getAttribute("TargetMode") == "External") continue
                val target = element.getAttribute("Target")
                val resolved = resolveTarget(base, target)
                assertTrue(
                    entries.containsKey(resolved),
                    "$name points at $target, which resolves to the missing part $resolved"
                )
            }
        }
    }

    private fun resolveTarget(base: String, target: String): String {
        val parts = mutableListOf<String>()
        if (base.isNotEmpty()) base.split('/').forEach { if (it.isNotEmpty()) parts.add(it) }
        target.split('/').forEach { segment ->
            when (segment) {
                "", "." -> {
                }
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }

    private fun zipEntries(file: File): Map<String, ByteArray> =
        inflateZip(file.readBytes())

    private fun inflateZip(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                if (entry.isDirectory) continue
                out[entry.name] = stream.readBytes()
            }
        }
        return out
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun tinyPng(): ByteArray {
        val signature = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        )
        val header = ByteArrayOutputStream().apply {
            write(0)
            write(0)
            write(0)
            write(1)
            write(0)
            write(0)
            write(0)
            write(1)
            write(8)
            write(2)
            write(0)
            write(0)
            write(0)
        }.toByteArray()
        val pixels = byteArrayOf(0, 0x40.toByte(), 0x80.toByte(), 0xC0.toByte())
        return signature + pngChunk("IHDR", header) + pngChunk("IDAT", deflate(pixels)) + pngChunk("IEND", ByteArray(0))
    }

    private fun pngChunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val length = data.size
        out.write((length ushr 24) and 0xFF)
        out.write((length ushr 16) and 0xFF)
        out.write((length ushr 8) and 0xFF)
        out.write(length and 0xFF)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val value = crc.value
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
        return out.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val deflater = Deflater()
        DeflaterOutputStream(out, deflater).use { stream -> stream.write(data) }
        deflater.end()
        return out.toByteArray()
    }

    private fun tempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "lucent-pptx-test-" + System.nanoTime())
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }
}
