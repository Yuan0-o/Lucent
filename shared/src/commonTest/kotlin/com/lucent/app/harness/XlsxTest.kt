package com.lucent.app.harness

import com.lucent.app.harness.ooxml.Xlsx
import com.lucent.app.harness.ooxml.parse
import com.lucent.app.harness.ooxml.readZip
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XlsxTest {

    @Test
    fun createsTwoSheetsWithFormulasFormattingAndAChart() {
        val dir = tempDir()
        val out = File(dir, "scores.xlsx")
        val detail = Xlsx.create(workbookSpec(), out)
        assertTrue(detail.contains("Scores"), "unexpected summary: $detail")
        val parts = readZip(out)
        val required = listOf(
            "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
            "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml",
            "xl/worksheets/_rels/sheet1.xml.rels", "xl/drawings/drawing1.xml",
            "xl/drawings/_rels/drawing1.xml.rels", "xl/charts/chart1.xml", "docProps/core.xml",
            "docProps/app.xml"
        )
        required.forEach { name -> assertTrue(parts.containsKey(name), "missing part $name") }
        parts.forEach { entry -> parseXmlPart(entry.key, entry.value) }
        assertTrue(parts.getValue("xl/workbook.xml").toString(Charsets.UTF_8).contains("fullCalcOnLoad"))
        val styles = parts.getValue("xl/styles.xml").toString(Charsets.UTF_8)
        assertTrue(styles.contains("patternType=\"solid\""), "no fill was written")
        assertTrue(styles.contains("<b/>"), "no bold font was written")
        assertTrue(styles.contains("numFmtId=\"4\""), "no number format was written")
        assertTrue(styles.contains("<dxfs count=\"1\">"), "no conditional format style was written")
        val sheet = parts.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
        assertTrue(sheet.contains("<pane"), "no freeze pane was written")
        assertTrue(sheet.contains("autoFilter"), "no autofilter was written")
        assertTrue(sheet.contains("<mergeCell ref=\"A1:C1\"/>"), "no merge was written")
        assertTrue(sheet.contains("colorScale"), "no color scale was written")
        assertTrue(sheet.contains("<drawing r:id="), "the sheet does not reference the chart drawing")
        val chart = parts.getValue("xl/charts/chart1.xml").toString(Charsets.UTF_8)
        assertTrue(chart.contains("<c:barChart>"), "no bar chart was written")
        assertTrue(chart.contains("Scores!\$A\$3:\$A\$5"), "no category reference was written")
        assertTrue(chart.contains("Scores!\$B\$3:\$B\$5"), "no value reference was written")
    }

    @Test
    fun readsValuesAndFormulas() {
        val dir = tempDir()
        val out = File(dir, "scores.xlsx")
        Xlsx.create(workbookSpec(), out)
        val text = Xlsx.read(out, "Scores")
        assertTrue(text.contains("Workbook: 2 sheets (Scores, Notes)"), "no workbook header: $text")
        assertTrue(text.contains("Sheet: Scores: rows 1-9, columns A-B"), "no sheet header: $text")
        assertTrue(text.contains("Formulas: B9=SUM(B3:B8)"), "no formula list: $text")
        assertTrue(text.contains("Ada,9"), "no row values: $text")
        assertTrue(text.contains("Grace,10"), "no row values: $text")
        assertTrue(text.contains("Total,=SUM(B3:B8)"), "no formula placeholder: $text")
        val notes = Xlsx.read(out, "Notes")
        assertTrue(notes.contains("Remember the review"), "the second sheet was not read: $notes")
        val clipped = Xlsx.read(out, "Scores", "A3:B4")
        assertTrue(clipped.contains("Ada,9"), "no clipped values: $clipped")
        assertTrue(clipped.contains("rows 3-4"), "the range was not applied: $clipped")
        val limited = Xlsx.read(out, "Scores", "", 2)
        assertTrue(limited.contains("more rows"), "maxRows was not reported: $limited")
    }

    @Test
    fun csvOutWritesTheSheetAsCsv() {
        val dir = tempDir()
        val out = File(dir, "scores.xlsx")
        Xlsx.create(workbookSpec(), out)
        val csv = Xlsx.csvOut(out, "Scores")
        assertEquals(
            "Scores,\nName,Score\nAda,9\nGrace,10\nAlan,7\n,\n,\n,\nTotal,=SUM(B3:B8)",
            csv
        )
        assertEquals("Note\nRemember the review", Xlsx.csvOut(out, "Notes"))
    }

    @Test
    fun editAppendsRowsAndAddsASheet() {
        val dir = tempDir()
        val out = File(dir, "scores.xlsx")
        Xlsx.create(workbookSpec(), out)
        val ops = """
            [
              {"op":"append_rows","sheet":"Scores","rows":[["Linus",8]]},
              {"op":"add_sheet","name":"Data"},
              {"op":"set","sheet":"Notes","ref":"B1","value":"ok"},
              {"op":"merge","sheet":"Notes","range":"A1:B1"},
              {"op":"freeze","sheet":"Notes","cell":"A2"},
              {"op":"autofilter","sheet":"Notes","range":"A1:B1","on":true},
              {"op":"set_column_width","sheet":"Notes","column":"A","width":30},
              {"op":"rename_sheet","from":"Data","to":"Extra"}
            ]
        """.trimIndent()
        val summary = Xlsx.edit(out, ops)
        assertTrue(summary.contains("append_rows"), "unexpected summary: $summary")
        val parts = readZip(out)
        parts.forEach { entry -> parseXmlPart(entry.key, entry.value) }
        assertTrue(parts.containsKey("xl/worksheets/sheet3.xml"), "the new sheet part was not written")
        val scores = Xlsx.read(out, "Scores")
        assertTrue(scores.contains("Linus,8"), "the appended row is missing: $scores")
        assertTrue(Xlsx.read(out, "Extra").contains("Sheet: Extra"), "the new sheet was not renamed")
        val notes = Xlsx.csvOut(out, "Notes")
        assertTrue(notes.contains("ok"), "the set op did not apply: $notes")
        assertFalse(parts.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8).contains("Extra"))
        assertTrue(parts.getValue("xl/workbook.xml").toString(Charsets.UTF_8).contains("Extra"))
    }

    @Test
    fun csvInCreatesTheSheetAndWritesNumbers() {
        val dir = tempDir()
        val out = File(dir, "data.xlsx")
        Xlsx.create("""{"sheets":[{"name":"Sheet1","rows":[["a"]]}]}""", out)
        val detail = Xlsx.csvIn(out, "name,score\nAda,9\n\"Grace, Hopper\",10", "Imported", "B2")
        assertTrue(detail.contains("Imported"), "unexpected summary: $detail")
        assertEquals(
            "name,score\nAda,9\n\"Grace, Hopper\",10",
            Xlsx.csvOut(out, "Imported")
        )
        readZip(out).forEach { entry -> parseXmlPart(entry.key, entry.value) }
        assertTrue(Xlsx.read(out).contains("Sheet1"), "the original sheet was lost")
    }

    @Test
    fun rejectsBadInput() {
        val dir = tempDir()
        assertFailsWith<IllegalArgumentException> { Xlsx.create("{oops", File(dir, "bad.xlsx")) }
        assertFailsWith<IllegalArgumentException> { Xlsx.create("""{"sheets":[]}""", File(dir, "bad.xlsx")) }
        assertFailsWith<IllegalArgumentException> {
            Xlsx.create("""{"sheets":[{"cells":[{"ref":"A1","value":1},{"value":2}]}]}""", File(dir, "bad.xlsx"))
        }
        val plain = File(dir, "notes.txt")
        plain.writeText("not a package")
        assertFailsWith<IllegalArgumentException> { Xlsx.read(plain) }
        assertFailsWith<IllegalArgumentException> { Xlsx.csvOut(plain) }
        val out = File(dir, "ok.xlsx")
        Xlsx.create("""{"sheets":[{"name":"One","rows":[[1]]}]}""", out)
        assertFailsWith<IllegalArgumentException> { Xlsx.read(out, "Missing") }
        assertFailsWith<IllegalArgumentException> { Xlsx.edit(out, """[{"op":"nope"}]""") }
        assertFailsWith<IllegalArgumentException> { Xlsx.edit(out, """[{"op":"delete_sheet","name":"One"}]""") }
        assertFailsWith<IllegalArgumentException> { Xlsx.csvIn(out, "") }
    }

    private fun workbookSpec(): String = """
        {
          "sheets": [
            {
              "name": "Scores",
              "freeze": "A2",
              "autofilter": true,
              "columns": [{"width": 20}, {"width": 12}],
              "heights": {"1": 24},
              "merges": ["A1:C1"],
              "cells": [
                {"ref":"A1","value":"Scores","style":{"bold":true,"size":14,"align":"center","fill":"#EEEEEE"}},
                {"ref":"A2","value":"Name","style":{"bold":true,"border":true}},
                {"ref":"B2","value":"Score","style":{"bold":true,"border":true}},
                {"ref":"A9","value":"Total","style":{"bold":true}},
                {"ref":"B9","formula":"SUM(B3:B8)","style":{"bold":true,"format":"#,##0.00"}}
              ],
              "rows": [["Ada",9],["Grace",10],["Alan",7]],
              "row_start": 3,
              "conditional": [
                {"range":"B3:B8","type":"cellIs","operator":"greaterThan","formula":"8","fill":"#FFC7CE","colour":"#9C0006"},
                {"range":"B3:B8","type":"colorScale","min":"#FFFFFF","mid":"#FFEB84","max":"#63BE7B"}
              ],
              "charts": [
                {"type":"bar","title":"Scores","categories":"A3:A5",
                 "series":[{"name":"Score","values":"B3:B5"}],"anchor":"E2","width":10,"height":8}
              ]
            },
            {
              "name": "Notes",
              "rows": [["Note"],["Remember the review"]]
            }
          ]
        }
    """.trimIndent()

    private fun tempDir(): File = Files.createTempDirectory("lucent-xlsx-test").toFile()

    private fun parseXmlPart(name: String, bytes: ByteArray) {
        if (!name.endsWith(".xml") && !name.endsWith(".rels")) return
        try {
            parse(bytes)
        } catch (e: Exception) {
            throw AssertionError("part $name is not valid XML: ${e.message}", e)
        }
    }
}
