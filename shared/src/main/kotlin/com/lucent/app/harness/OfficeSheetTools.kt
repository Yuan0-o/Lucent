package com.lucent.app.harness

import com.lucent.app.harness.ooxml.Xlsx
import com.lucent.app.harness.ooxml.stringOf
import com.lucent.app.network.ToolExecResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object OfficeSheetTools : HarnessGroupTools {

    override val group: HarnessGroup = HarnessGroup.OFFICE

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "create_spreadsheet",
            group = HarnessGroup.OFFICE,
            permission = HarnessPermission.WRITE,
            description = "Create an Excel .xlsx workbook that opens in Excel, LibreOffice and WPS. \"spec\" is JSON: {\"sheets\":[{\"name\":\"Sheet1\",\"freeze\":\"A2\",\"autofilter\":true,\"columns\":[{\"width\":20}],\"heights\":{\"1\":24},\"merges\":[\"A1:C1\"],\"cells\":[{\"ref\":\"A1\",\"value\":\"Total\",\"formula\":\"SUM(B2:B9)\",\"style\":{\"bold\":true,\"italic\":true,\"size\":12,\"colour\":\"#FF0000\",\"fill\":\"#EEEEEE\",\"align\":\"center\",\"valign\":\"top\",\"wrap\":true,\"border\":true,\"format\":\"#,##0.00\"}}],\"rows\":[[\"Name\",\"Score\"],[\"Ada\",9]],\"row_start\":1,\"conditional\":[{\"range\":\"B2:B20\",\"type\":\"cellIs\",\"operator\":\"greaterThan\",\"formula\":\"100\",\"fill\":\"#FFC7CE\",\"colour\":\"#9C0006\"},{\"range\":\"C2:C20\",\"type\":\"colorScale\",\"min\":\"#FFFFFF\",\"mid\":\"#FFEB84\",\"max\":\"#63BE7B\"}],\"charts\":[{\"type\":\"bar\",\"title\":\"Scores\",\"categories\":\"A2:A9\",\"series\":[{\"name\":\"Score\",\"values\":\"B2:B9\"}],\"anchor\":\"E2\",\"width\":12,\"height\":8}]}]}. Chart types are bar, line and pie.",
            params = listOf(
                HarnessSchema.text("path", "Where to write the .xlsx, relative to the workspace or absolute"),
                HarnessSchema.json("spec", "Workbook spec with a sheets array")
            )
        ),
        HarnessTool(
            name = "read_spreadsheet",
            group = HarnessGroup.OFFICE,
            permission = HarnessPermission.READ,
            description = "Read one sheet of an Excel .xlsx workbook as CSV-ish text, with a short header listing the sheet names, the used rows and columns, and any formulas. Files written by Excel, LibreOffice, Google Sheets or WPS are accepted.",
            params = listOf(
                HarnessSchema.text("path", "The .xlsx file to read"),
                HarnessSchema.text("sheet", "Sheet name, defaults to the first sheet", required = false),
                HarnessSchema.text("range", "Cell range such as B2:D20 to limit the read", required = false),
                HarnessSchema.number("max_rows", "Maximum rows to return, defaults to 200", required = false)
            )
        ),
        HarnessTool(
            name = "edit_spreadsheet",
            group = HarnessGroup.OFFICE,
            permission = HarnessPermission.WRITE,
            description = "Edit an existing .xlsx workbook in place, keeping everything else in the file. \"ops\" is an array of operations: {\"op\":\"set\",\"sheet\":\"Sheet1\",\"ref\":\"B2\",\"value\":5,\"formula\":null,\"style\":{...}}, {\"op\":\"append_rows\",\"sheet\":\"Sheet1\",\"rows\":[[1,2],[3,4]]}, {\"op\":\"add_sheet\",\"name\":\"Sheet2\"}, {\"op\":\"rename_sheet\",\"from\":\"Sheet1\",\"to\":\"Data\"}, {\"op\":\"delete_sheet\",\"name\":\"Sheet2\"}, {\"op\":\"set_column_width\",\"sheet\":\"Sheet1\",\"column\":\"B\",\"width\":24}, {\"op\":\"merge\",\"sheet\":\"Sheet1\",\"range\":\"A1:C1\"}, {\"op\":\"freeze\",\"sheet\":\"Sheet1\",\"cell\":\"A2\"} and {\"op\":\"autofilter\",\"sheet\":\"Sheet1\",\"range\":\"A1:D1\",\"on\":true}.",
            params = listOf(
                HarnessSchema.text("path", "The .xlsx file to edit"),
                HarnessSchema.list("ops", "Array of edit operations", itemType = "object")
            )
        ),
        HarnessTool(
            name = "export_csv",
            group = HarnessGroup.OFFICE,
            permission = HarnessPermission.WRITE,
            description = "Write one sheet of an .xlsx workbook to a .csv text file. The output defaults to a .csv file next to the workbook with the same base name.",
            params = listOf(
                HarnessSchema.text("path", "The .xlsx file to read"),
                HarnessSchema.text("sheet", "Sheet name, defaults to the first sheet", required = false),
                HarnessSchema.text("out", "Where to write the .csv, defaults to a sibling file", required = false)
            )
        ),
        HarnessTool(
            name = "import_csv",
            group = HarnessGroup.OFFICE,
            permission = HarnessPermission.WRITE,
            description = "Write CSV text into one sheet of an .xlsx workbook, starting at a cell. The sheet is created if it does not exist yet, and existing cells keep their formatting. Give the CSV either as \"csv_text\" or as the path of a file in \"csv_path\".",
            params = listOf(
                HarnessSchema.text("path", "The .xlsx file to update"),
                HarnessSchema.text("csv_text", "CSV text to write", required = false),
                HarnessSchema.text("csv_path", "CSV file to read the text from", required = false),
                HarnessSchema.text("sheet", "Sheet name, defaults to the first sheet", required = false),
                HarnessSchema.text("start_cell", "Top left cell, defaults to A1", required = false)
            )
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? {
        return try {
            when (name) {
                "create_spreadsheet" -> createSpreadsheet(ctx, args)
                "read_spreadsheet" -> readSpreadsheet(ctx, args)
                "edit_spreadsheet" -> editSpreadsheet(ctx, args)
                "export_csv" -> exportCsv(ctx, args)
                "import_csv" -> importCsv(ctx, args)
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

    private fun createSpreadsheet(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forWrite(ctx, stringOf(args, "path"))
        if (file.isDirectory) {
            return ToolExecResult("${Workspace.display(ctx, file)} is a directory, not an .xlsx file.", success = false)
        }
        val spec = spreadsheetSpec(args)
        if (ctx.config.snapshots && file.exists()) Snapshots.capture(ctx, file)
        file.parentFile?.mkdirs()
        val detail = Xlsx.create(spec, file)
        return ToolExecResult(
            "Created ${Workspace.display(ctx, file)} (${Workspace.humanSize(file.length())}): $detail."
        )
    }

    private fun readSpreadsheet(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forRead(ctx, stringOf(args, "path"))
        if (file.isDirectory) {
            return ToolExecResult("${Workspace.display(ctx, file)} is a directory, not an .xlsx file.", success = false)
        }
        val maxRows = args.optInt("max_rows", 200).coerceIn(1, 5000)
        val text = Xlsx.read(file, stringOf(args, "sheet"), stringOf(args, "range"), maxRows)
        return ToolExecResult(text)
    }

    private fun editSpreadsheet(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forWrite(ctx, stringOf(args, "path"))
        if (file.isDirectory) {
            return ToolExecResult("${Workspace.display(ctx, file)} is a directory, not an .xlsx file.", success = false)
        }
        if (!file.exists()) {
            return ToolExecResult("${Workspace.display(ctx, file)} does not exist yet.", success = false)
        }
        val ops = operations(args)
        if (ctx.config.snapshots) Snapshots.capture(ctx, file)
        val detail = Xlsx.edit(file, ops)
        return ToolExecResult(
            "Edited ${Workspace.display(ctx, file)} (${Workspace.humanSize(file.length())}): $detail."
        )
    }

    private fun exportCsv(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val source = Workspace.forRead(ctx, stringOf(args, "path"))
        if (source.isDirectory) {
            return ToolExecResult("${Workspace.display(ctx, source)} is a directory, not an .xlsx file.", success = false)
        }
        val declared = stringOf(args, "out")
        val target = if (declared.isBlank()) {
            val sibling = File(source.parentFile ?: ctx.workspace, source.nameWithoutExtension + ".csv")
            Workspace.forWrite(ctx, sibling.path)
        } else {
            Workspace.forWrite(ctx, declared)
        }
        val csv = Xlsx.csvOut(source, stringOf(args, "sheet"))
        Workspace.writeText(ctx, target, csv)
        val rows = if (csv.isEmpty()) 0 else csv.count { it == '\n' } + 1
        return ToolExecResult(
            "Wrote ${Workspace.display(ctx, target)} (${Workspace.humanSize(target.length())}) with " +
                "$rows ${if (rows == 1) "row" else "rows"} of CSV."
        )
    }

    private fun importCsv(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forWrite(ctx, stringOf(args, "path"))
        if (file.isDirectory) {
            return ToolExecResult("${Workspace.display(ctx, file)} is a directory, not an .xlsx file.", success = false)
        }
        val text = stringOf(args, "csv_text")
        val csv = if (text.isNotBlank()) {
            text
        } else {
            val declared = stringOf(args, "csv_path")
            if (declared.isBlank()) {
                throw IllegalArgumentException("Give the CSV either as \"csv_text\" or as a file in \"csv_path\".")
            }
            val source = Workspace.forRead(ctx, declared)
            if (source.isDirectory) throw IllegalArgumentException("${Workspace.display(ctx, source)} is a directory.")
            Workspace.readText(source, 4 * 1024 * 1024)
        }
        if (ctx.config.snapshots && file.exists()) Snapshots.capture(ctx, file)
        val detail = Xlsx.csvIn(file, csv, stringOf(args, "sheet"), stringOf(args, "start_cell", "A1"))
        return ToolExecResult(
            "Updated ${Workspace.display(ctx, file)} (${Workspace.humanSize(file.length())}): $detail."
        )
    }

    private fun spreadsheetSpec(args: JSONObject): String {
        val raw = args.opt("spec")
        val spec = when (raw) {
            is JSONObject -> raw
            is String -> if (raw.isBlank()) {
                null
            } else {
                try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    throw IllegalArgumentException("The \"spec\" argument is not valid JSON: ${e.message ?: "parse error"}")
                }
            }
            else -> null
        } ?: throw IllegalArgumentException("Give the workbook spec in \"spec\" with a \"sheets\" array.")
        if (!spec.has("sheets")) {
            throw IllegalArgumentException(
                "The \"spec\" needs a \"sheets\" array, for example {\"sheets\":[{\"name\":\"Sheet1\",\"rows\":[[1,2]]}]}."
            )
        }
        return spec.toString()
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
}
