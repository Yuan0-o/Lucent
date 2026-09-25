package com.lucent.app.harness

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfToolsTest {

    @Test
    fun infoAndTextReadAHandWrittenPdf() {
        val file = write("simple.pdf", simplePdf())
        val info = PdfBook.info(file)
        assertTrue(info.contains("Pages: 1"), info)
        assertTrue(info.contains("Title: Hello PDF"), info)
        assertTrue(info.contains("Author: Lucent"), info)
        assertTrue(info.contains("Created: 2024-01-02 03:04"), info)
        assertTrue(info.contains("Encrypted: no"), info)
        val text = PdfBook.text(file)
        assertTrue(text.contains("--- Page 1 ---"), text)
        assertTrue(text.contains("Hello PDF"), text)
        assertTrue(text.contains("World"), text)
    }

    @Test
    fun pageTextAndPageSelectionWork() {
        val file = write("simple.pdf", simplePdf())
        assertTrue(PdfBook.pageText(file, 1).contains("Hello PDF"))
        assertTrue(PdfBook.pageText(file, 7).contains("out of range"))
        assertTrue(PdfBook.text(file, "2").contains("No pages"))
        assertTrue(PdfBook.text(file, "1").contains("Hello PDF"))
        assertTrue(PdfBook.text(file, "1-2").contains("--- Page 1 ---"))
    }

    @Test
    fun searchFindsTextAndReportsPages() {
        val file = write("simple.pdf", simplePdf())
        val hits = PdfBook.search(file, "hello")
        assertTrue(hits.contains("p.1"), hits)
        assertTrue(hits.contains("1 hit(s)"), hits)
        val none = PdfBook.search(file, "zebra")
        assertTrue(none.contains("No matches"), none)
        val empty = PdfBook.search(file, "  ")
        assertTrue(empty.contains("Give a word or phrase"), empty)
    }

    @Test
    fun flateStreamsAndToUnicodeCMapsAreDecoded() {
        val file = write("compressed.pdf", compressedPdf())
        val info = PdfBook.info(file)
        assertTrue(info.contains("Pages: 2"), info)
        val text = PdfBook.text(file)
        assertTrue(text.contains("--- Page 1 ---"), text)
        assertTrue(text.contains("Hi"), text)
        assertTrue(text.contains("--- Page 2 ---"), text)
        assertTrue(text.contains("plain text"), text)
    }

    @Test
    fun streamsWithAnIndirectLengthAreRecovered() {
        val file = write("indirect.pdf", indirectLengthPdf())
        val text = PdfBook.text(file)
        assertTrue(text.contains("Indirect length works"), text)
    }

    @Test
    fun malformedFilesFailGracefully() {
        val broken = write("broken.pdf", "this is not a pdf at all".toByteArray(Charsets.ISO_8859_1))
        val info = PdfBook.info(broken)
        assertTrue(info.startsWith("Cannot read this PDF"), info)
        assertTrue(PdfBook.text(broken).startsWith("Cannot read this PDF"))
        assertTrue(PdfBook.search(broken, "hello").startsWith("Cannot read this PDF"))
        assertTrue(PdfBook.pageText(broken, 1).startsWith("Cannot read this PDF"))
    }

    @Test
    fun truncatedPdfDoesNotThrow() {
        val bytes = simplePdf()
        val file = write("cut.pdf", bytes.copyOfRange(0, bytes.size / 2))
        val text = PdfBook.text(file)
        assertTrue(text.isNotBlank(), "a truncated PDF should still answer")
        val info = PdfBook.info(file)
        assertTrue(info.isNotBlank())
    }

    @Test
    fun missingFileFailsGracefully() {
        val missing = File(tempDir(), "nope.pdf")
        assertTrue(PdfBook.info(missing).startsWith("Cannot read this PDF"))
    }

    @Test
    fun toolDefinitionsCoverThePdfSurface() {
        assertEquals(HarnessGroup.PDF, PdfTools.group)
        val names = PdfTools.tools.map { it.name }.toSet()
        for (name in listOf(
            "read_pdf", "pdf_info", "pdf_search", "render_pdf_page", "pdf_to_images", "merge_pdfs", "split_pdf"
        )) {
            assertTrue(names.contains(name), "$name is not registered")
        }
        val read = PdfTools.tools.first { it.name == "read_pdf" }
        assertEquals(HarnessPermission.READ, read.permission)
        val merge = PdfTools.tools.first { it.name == "merge_pdfs" }
        assertEquals(HarnessPermission.WRITE, merge.permission)
    }

    private fun simplePdf(): ByteArray {
        val content = "BT /F1 18 Tf 72 700 Td (Hello PDF) Tj 0 -24 Td [(Wor) -200 (ld)] TJ ET"
        val objects = listOf(
            textObject("<< /Type /Catalog /Pages 2 0 R >>"),
            textObject("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
            textObject(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
                    "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>"
            ),
            streamObject(content.toByteArray(Charsets.ISO_8859_1), false),
            textObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"),
            textObject(
                "<< /Title (Hello PDF) /Author (Lucent) /Producer (Lucent test) " +
                    "/CreationDate (D:20240102030405Z) >>"
            )
        )
        return buildPdf(objects, 6)
    }

    private fun compressedPdf(): ByteArray {
        val pageOne = "BT /F1 24 Tf 72 700 Td <00030004> Tj ET"
        val pageTwo = "BT /F2 24 Tf 72 700 Td (plain text) Tj ET"
        val cmap = "/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n" +
            "1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n" +
            "2 beginbfchar\n<0003> <0048>\n<0004> <0069>\nendbfchar\n" +
            "endcmap\nend\nend"
        val objects = listOf(
            textObject("<< /Type /Catalog /Pages 2 0 R >>"),
            textObject("<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>"),
            textObject(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
                    "/Resources << /Font << /F1 7 0 R /F2 10 0 R >> >> /Contents 4 0 R >>"
            ),
            streamObject(deflate(pageOne.toByteArray(Charsets.ISO_8859_1)), true),
            textObject(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
                    "/Resources << /Font << /F1 7 0 R /F2 10 0 R >> >> /Contents 6 0 R >>"
            ),
            streamObject(deflate(pageTwo.toByteArray(Charsets.ISO_8859_1)), true),
            textObject(
                "<< /Type /Font /Subtype /Type0 /BaseFont /Lucent-Test /Encoding /Identity-H " +
                    "/ToUnicode 8 0 R /DescendantFonts [9 0 R] >>"
            ),
            streamObject(cmap.toByteArray(Charsets.ISO_8859_1), false),
            textObject(
                "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /Lucent-Test " +
                    "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /DW 1000 >>"
            ),
            textObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        )
        return buildPdf(objects, 0)
    }

    private fun indirectLengthPdf(): ByteArray {
        val content = "BT /F1 12 Tf 72 720 Td (Indirect length works) Tj ET"
        val objects = listOf(
            textObject("<< /Type /Catalog /Pages 2 0 R >>"),
            textObject("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
            textObject(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
                    "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>"
            ),
            textObject("<< /Length 6 0 R >>\nstream\n$content\nendstream"),
            textObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"),
            textObject(content.length.toString())
        )
        return buildPdf(objects, 0)
    }

    private fun buildPdf(objects: List<ByteArray>, infoNumber: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("%PDF-1.4\n".toByteArray(Charsets.ISO_8859_1))
        val offsets = IntArray(objects.size + 1)
        objects.forEachIndexed { index, body ->
            offsets[index + 1] = out.size()
            out.write("${index + 1} 0 obj\n".toByteArray(Charsets.ISO_8859_1))
            out.write(body)
            out.write("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }
        val xref = out.size()
        out.write("xref\n0 ${objects.size + 1}\n".toByteArray(Charsets.ISO_8859_1))
        out.write("0000000000 65535 f \n".toByteArray(Charsets.ISO_8859_1))
        for (index in 1..objects.size) {
            out.write((pad10(offsets[index]) + " 00000 n \n").toByteArray(Charsets.ISO_8859_1))
        }
        val info = if (infoNumber in 1..objects.size) " /Info $infoNumber 0 R" else ""
        out.write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R$info >>\n".toByteArray(Charsets.ISO_8859_1))
        out.write("startxref\n$xref\n%%EOF\n".toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    private fun textObject(body: String): ByteArray = body.toByteArray(Charsets.ISO_8859_1)

    private fun streamObject(content: ByteArray, compressed: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        val header = if (compressed) {
            "<< /Filter /FlateDecode /Length ${content.size} >>\nstream\n"
        } else {
            "<< /Length ${content.size} >>\nstream\n"
        }
        out.write(header.toByteArray(Charsets.ISO_8859_1))
        out.write(content)
        out.write("\nendstream".toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val deflater = Deflater()
        DeflaterOutputStream(out, deflater).use { stream -> stream.write(data) }
        deflater.end()
        return out.toByteArray()
    }

    private fun pad10(value: Int): String {
        val text = value.toString()
        val sb = StringBuilder()
        for (index in text.length until 10) sb.append('0')
        return sb.append(text).toString()
    }

    private fun write(name: String, bytes: ByteArray): File {
        val file = File(tempDir(), name)
        file.writeBytes(bytes)
        return file
    }

    private fun tempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "lucent-pdf-test-" + System.nanoTime())
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }
}
