package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import org.json.JSONObject
import java.io.File

object Csv {

    fun parse(text: String, delimiter: Char = ','): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                ch == '"' -> quoted = !quoted
                !quoted && ch == delimiter -> {
                    row.add(cell.toString())
                    cell.setLength(0)
                }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cell.toString())
                    cell.setLength(0)
                    if (row.any { it.isNotEmpty() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            if (row.any { it.isNotEmpty() }) rows.add(row)
        }
        return rows
    }

    fun write(rows: List<List<String>>): String =
        rows.joinToString("\n") { row -> row.joinToString(",") { escape(it) } }

    fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) "\"" + value.replace("\"", "\"\"") + "\""
        else value

    fun quote(value: String): String = "'" + value.replace("'", "''") + "'"
}

object DatabaseTools : HarnessGroupTools {

    override val group = HarnessGroup.DATA

    private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val BLOCKED = listOf("attach", "detach", "load_extension", "drop database")

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "db_query",
            group = group,
            permission = HarnessPermission.READ,
            description = "Run a read-only SQL query against a SQLite file and get the rows back as CSV. Arguments: " +
                "path (the .db file), sql, limit (default 200).",
            params = listOf(
                HarnessSchema.text("path", "SQLite database file"),
                HarnessSchema.text("sql", "SELECT statement"),
                HarnessSchema.number("limit", "Maximum rows", false)
            )
        ),
        HarnessTool(
            name = "db_exec",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Change a SQLite database: CREATE TABLE, INSERT, UPDATE or DELETE. The file is snapshotted " +
                "first. Arguments: path, sql.",
            params = listOf(
                HarnessSchema.text("path", "SQLite database file"),
                HarnessSchema.text("sql", "Statement to run")
            )
        ),
        HarnessTool(
            name = "db_schema",
            group = group,
            permission = HarnessPermission.READ,
            description = "List the tables, views and indexes in a SQLite database, with each table's columns.",
            params = listOf(HarnessSchema.text("path", "SQLite database file"))
        ),
        HarnessTool(
            name = "db_import_csv",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Load a CSV file into a SQLite table, creating the table and inferring column types. " +
                "Arguments: path (database), csv_path or csv_text, table (optional), delimiter (optional).",
            params = listOf(
                HarnessSchema.text("path", "SQLite database file"),
                HarnessSchema.text("csv_path", "CSV file to read", false),
                HarnessSchema.text("csv_text", "CSV text instead of a file", false),
                HarnessSchema.text("table", "Table name", false),
                HarnessSchema.text("delimiter", "Field delimiter, default a comma", false)
            )
        ),
        HarnessTool(
            name = "db_export_csv",
            group = group,
            permission = HarnessPermission.READ,
            description = "Write the result of a query, or a whole table, to a CSV file next to the database.",
            params = listOf(
                HarnessSchema.text("path", "SQLite database file"),
                HarnessSchema.text("sql_or_table", "SELECT statement or a table name"),
                HarnessSchema.text("out", "CSV path", false)
            )
        ),
        HarnessTool(
            name = "data_analyse",
            group = group,
            permission = HarnessPermission.READ,
            description = "Describe a CSV file or some CSV text: row count, column types, empty cells, numeric " +
                "minimum, maximum, mean and median, and the most common values of one column.",
            params = listOf(
                HarnessSchema.text("path", "CSV file", false),
                HarnessSchema.text("csv_text", "CSV text instead of a file", false),
                HarnessSchema.text("group_by", "Column to count values for", false)
            )
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "db_query" -> query(ctx, args)
        "db_exec" -> exec(ctx, args)
        "db_schema" -> schema(ctx, args)
        "db_import_csv" -> importCsv(ctx, args)
        "db_export_csv" -> exportCsv(ctx, args)
        "data_analyse" -> analyse(ctx, args)
        else -> null
    }

    fun blocked(sql: String): String? {
        val lower = sql.lowercase()
        BLOCKED.forEach { word -> if (lower.contains(word)) return "The statement uses $word, which is not allowed here." }
        return null
    }

    private fun database(ctx: HarnessCtx, args: JSONObject): File? =
        try {
            Workspace.forWrite(ctx, args.optString("path", ""))
        } catch (e: HarnessError) {
            null
        }

    private suspend fun query(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val sql = args.optString("sql", "").trim()
        if (sql.isEmpty()) return ToolExecResult("There is no query.", success = false)
        blocked(sql)?.let { return ToolExecResult(it, success = false) }
        val file = database(ctx, args) ?: return ToolExecResult("That database path cannot be used.", success = false)
        if (!file.exists()) return ToolExecResult("${Workspace.display(ctx, file)} does not exist.", success = false)
        val limit = args.optInt("limit", 200).coerceIn(1, 5000)
        val host = HarnessRuntime.host
        if (host != null && host.availableSqlite()) {
            return ToolExecResult(ctx.limit(host.sqliteQuery(file.path, sql, limit)))
        }
        if (!HarnessRuntime.shellReady()) {
            return ToolExecResult("No SQLite engine is available on this build.", success = false)
        }
        val outcome = HarnessRuntime.runShell(
            "sqlite3 -readonly -header -csv " + Csv.quote(file.path) + " " + Csv.quote(sql),
            null,
            120
        )
        return ToolExecResult(ctx.limit(outcome.text.ifBlank { "(no rows)" }), success = outcome.ok)
    }

    private suspend fun exec(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val sql = args.optString("sql", "").trim()
        if (sql.isEmpty()) return ToolExecResult("There is no statement.", success = false)
        blocked(sql)?.let { return ToolExecResult(it, success = false) }
        val file = database(ctx, args) ?: return ToolExecResult("That database path cannot be used.", success = false)
        if (file.exists() && ctx.config.snapshots) Snapshots.capture(ctx, file)
        val host = HarnessRuntime.host
        val answer = if (host != null && host.availableSqlite()) {
            host.sqliteExec(file.path, sql)
        } else if (HarnessRuntime.shellReady()) {
            HarnessRuntime.runShell("sqlite3 " + Csv.quote(file.path) + " " + Csv.quote(sql), null, 120).text
        } else {
            return ToolExecResult("No SQLite engine is available on this build.", success = false)
        }
        val failed = answer.startsWith("sqlite error") || answer.startsWith("Error")
        return ToolExecResult(
            "$answer\n${Workspace.display(ctx, file)} is now ${Workspace.humanSize(file.length())}.",
            success = !failed
        )
    }

    private suspend fun schema(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val request = JSONObject()
            .put("path", args.optString("path", ""))
            .put("sql", "SELECT type, name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name")
            .put("limit", 500)
        return query(ctx, request)
    }

    private suspend fun importCsv(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val csvText = when {
            args.optString("csv_text", "").isNotBlank() -> args.optString("csv_text", "")
            args.optString("csv_path", "").isNotBlank() -> {
                val source = try {
                    Workspace.forRead(ctx, args.optString("csv_path", ""))
                } catch (e: HarnessError) {
                    return ToolExecResult(e.message ?: "That CSV cannot be read", success = false)
                }
                source.readText()
            }
            else -> return ToolExecResult("Give me csv_path or csv_text.", success = false)
        }
        val delimiter = args.optString("delimiter", ",").firstOrNull() ?: ','
        val rows = Csv.parse(csvText, delimiter)
        if (rows.size < 2) return ToolExecResult("That CSV has no data rows.", success = false)
        val table = args.optString("table", "").ifBlank {
            args.optString("csv_path", "data").substringAfterLast('/').substringBefore('.').lowercase()
                .replace(Regex("[^a-z0-9_]+"), "_").ifBlank { "data" }
        }
        if (!IDENTIFIER.matches(table)) return ToolExecResult("Table name \"$table\" is not allowed.", success = false)
        val header = rows.first().mapIndexed { index, name ->
            val cleaned = name.trim().lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_')
            if (cleaned.isEmpty()) "column_$index" else cleaned
        }
        val body = rows.drop(1)
        val numeric = header.indices.map { column ->
            body.take(200).all { row -> row.getOrNull(column)?.trim()?.let { it.isEmpty() || it.toDoubleOrNull() != null } != false }
        }
        val columns = header.mapIndexed { index, name -> "$name ${if (numeric[index]) "REAL" else "TEXT"}" }
        val statements = StringBuilder()
        statements.append("DROP TABLE IF EXISTS ").append(table).append(";\n")
        statements.append("CREATE TABLE ").append(table).append(" (").append(columns.joinToString(", ")).append(");\n")
        body.chunked(200).forEach { chunk ->
            statements.append("INSERT INTO ").append(table).append(" (").append(header.joinToString(", ")).append(") VALUES ")
            statements.append(
                chunk.joinToString(", ") { row ->
                    "(" + header.indices.joinToString(", ") { index ->
                        val value = row.getOrNull(index)?.trim().orEmpty()
                        if (value.isEmpty()) "NULL" else Csv.quote(value)
                    } + ")"
                }
            )
            statements.append(";\n")
        }
        val exec = exec(ctx, JSONObject().put("path", args.optString("path", "")).put("sql", statements.toString()))
        val count = body.size
        return ToolExecResult(
            "Imported $count row${if (count == 1) "" else "s"} into table $table.\n${exec.summary}",
            success = exec.success
        )
    }

    private suspend fun exportCsv(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val selector = args.optString("sql_or_table", "").trim()
        if (selector.isEmpty()) return ToolExecResult("Give me a table name or a SELECT statement.", success = false)
        val sql = if (selector.lowercase().startsWith("select")) selector
        else "SELECT * FROM " + selector.filter { it.isLetterOrDigit() || it == '_' }
        val result = query(ctx, JSONObject().put("path", args.optString("path", "")).put("sql", sql).put("limit", 100000))
        if (!result.success) return result
        val dbFile = database(ctx, args) ?: return ToolExecResult("That path cannot be used.", success = false)
        val out = if (args.optString("out", "").isNotBlank()) File(args.optString("out", ""))
        else File(dbFile.parentFile, dbFile.nameWithoutExtension + "-export.csv")
        out.parentFile?.mkdirs()
        out.writeText(result.summary)
        return ToolExecResult("Wrote ${out.name} (${Workspace.humanSize(out.length())}).")
    }

    private fun analyse(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val csvText = when {
            args.optString("csv_text", "").isNotBlank() -> args.optString("csv_text", "")
            args.optString("path", "").isNotBlank() -> try {
                Workspace.readText(Workspace.forRead(ctx, args.optString("path", "")))
            } catch (e: HarnessError) {
                return ToolExecResult(e.message ?: "That file cannot be read", success = false)
            }
            else -> return ToolExecResult("Give me a file or some CSV text.", success = false)
        }
        val rows = Csv.parse(csvText)
        if (rows.size < 2) return ToolExecResult("That CSV has no data rows.", success = false)
        val header = rows.first()
        val body = rows.drop(1)
        val sb = StringBuilder()
        sb.append(body.size).append(" rows, ").append(header.size).append(" columns\n")
        header.forEachIndexed { index, name ->
            val values = body.mapNotNull { it.getOrNull(index)?.trim() }.filter { it.isNotEmpty() }
            val numbers = values.mapNotNull { it.toDoubleOrNull() }
            sb.append("- ").append(name).append(": ")
            sb.append(if (numbers.size == values.size && numbers.isNotEmpty()) "numeric" else "text")
            sb.append(", ").append(values.size).append(" filled")
            val empty = body.size - values.size
            if (empty > 0) sb.append(", ").append(empty).append(" empty")
            if (numbers.isNotEmpty() && numbers.size == values.size) {
                val sorted = numbers.sorted()
                val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
                else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                sb.append(", min ").append(trim(sorted.first()))
                sb.append(", max ").append(trim(sorted.last()))
                sb.append(", mean ").append(trim(numbers.average()))
                sb.append(", median ").append(trim(median))
            } else if (values.isNotEmpty()) {
                val common = values.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
                sb.append(", common ").append(common.joinToString(", ") { "${it.key} (${it.value})" })
            }
            sb.append('\n')
        }
        val groupBy = args.optString("group_by", "")
        if (groupBy.isNotBlank()) {
            val index = header.indexOfFirst { it.equals(groupBy, ignoreCase = true) }
            if (index >= 0) {
                val counts = body.mapNotNull { it.getOrNull(index)?.trim() }.filter { it.isNotEmpty() }
                    .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(10)
                sb.append("Top values in $groupBy:\n")
                counts.forEach { sb.append("  ").append(it.key).append(" — ").append(it.value).append('\n') }
            }
        }
        val duplicates = body.groupingBy { it.joinToString("\u0001") }.eachCount().filter { it.value > 1 }.size
        if (duplicates > 0) sb.append("$duplicates duplicated row pattern(s) found.")
        return ToolExecResult(sb.toString().trimEnd())
    }

    private fun trim(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.4f", value)
}
