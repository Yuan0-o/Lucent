package com.lucent.app.harness

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.multipdf.Splitter
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.Desktop
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.File
import java.sql.DriverManager
import javax.imageio.ImageIO

object DesktopHarnessShell : HarnessShell {

    override val id = "desktop"

    override fun isReady(): Boolean = true

    override fun describe(): String =
        if (isWindows()) "Windows command shell" else (System.getProperty("os.name") ?: "Unix") + " shell"

    fun isWindows(): Boolean = System.getProperty("os.name", "").lowercase().contains("win")

    override fun capabilityNames(): Set<String> = setOf("shell", "process")

    override suspend fun run(
        command: String,
        workdir: File?,
        timeoutSeconds: Int,
        env: Map<String, String>
    ): ShellOutcome = withContext(Dispatchers.IO) {
        try {
            val builder = if (isWindows()) {
                ProcessBuilder("cmd", "/c", command)
            } else {
                ProcessBuilder("/bin/sh", "-lc", command)
            }
            if (workdir != null && workdir.isDirectory) builder.directory(workdir)
            if (env.isNotEmpty()) builder.environment().putAll(env)
            val process = builder.start()
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val reader = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { synchronized(stdout) { stdout.append(it).append('\n') } }
                } catch (_: Throwable) {
                }
            }
            val errorReader = Thread {
                try {
                    process.errorStream.bufferedReader().forEachLine { synchronized(stderr) { stderr.append(it).append('\n') } }
                } catch (_: Throwable) {
                }
            }
            reader.start()
            errorReader.start()
            val finished = process.waitFor(timeoutSeconds.coerceIn(1, 7200).toLong(), java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                reader.join(1000)
                errorReader.join(1000)
                return@withContext ShellOutcome(
                    false,
                    synchronized(stdout) { stdout.toString() },
                    synchronized(stderr) { stderr.toString() },
                    -1,
                    timedOut = true
                )
            }
            reader.join(2000)
            errorReader.join(2000)
            val out = synchronized(stdout) { stdout.toString() }
            val err = synchronized(stderr) { stderr.toString() }
            ShellOutcome(process.exitValue() == 0, out, err, process.exitValue())
        } catch (t: Throwable) {
            ShellOutcome(false, "", t.message ?: "Shell exec failed", -1)
        }
    }
}

object DesktopHarnessHost : HarnessHost {

    override val android: Boolean = false

    override fun filesDir(): File = File(System.getProperty("user.home"), ".lucent").apply { mkdirs() }

    override fun cacheDir(): File = File(filesDir(), "cache").apply { mkdirs() }

    override fun defaultWorkspace(): File {
        val home = File(System.getProperty("user.home") ?: ".")
        val documents = File(home, "Documents")
        val base = if (documents.isDirectory) documents else home
        return File(base, "Lucent").apply { mkdirs() }
    }

    override fun capabilities(): Set<String> = setOf("sqlite", "pdf", "clipboard", "notify", "open_url", "share")

    override fun workspaceCandidates(): List<String> {
        val home = File(System.getProperty("user.home") ?: ".")
        val out = mutableListOf<String>()
        out.add(File(home, "Lucent").path)
        listOf("Documents", "Desktop", "Downloads").forEach { name ->
            val dir = File(home, name)
            if (dir.isDirectory) out.add(File(dir, "Lucent").path)
        }
        out.add(File(System.getProperty("user.dir") ?: ".").path)
        return out.distinct()
    }

    override fun openUrl(url: String): Boolean = try {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(java.net.URI(url)) else false
        true
    } catch (t: Throwable) {
        false
    }

    override fun readClipboard(): String = try {
        val contents = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
        if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            contents.getTransferData(DataFlavor.stringFlavor) as? String ?: ""
        } else ""
    } catch (t: Throwable) {
        ""
    }

    override fun writeClipboard(text: String): Boolean = try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    } catch (t: Throwable) {
        false
    }

    override fun notify(title: String, text: String): Boolean = try {
        if (!SystemTray.isSupported()) false else {
            val tray = SystemTray.getSystemTray()
            val icon = java.awt.ImageIO.read(java.io.ByteArrayInputStream(emptyPng())) ?: return false
            val trayIcon = TrayIcon(icon, "Lucent")
            trayIcon.isImageAutoSize = true
            tray.add(trayIcon)
            trayIcon.displayMessage(title, text, TrayIcon.MessageType.INFO)
            Thread {
                Thread.sleep(12000)
                try { tray.remove(trayIcon) } catch (_: Throwable) {
                }
            }.start()
            true
        }
    } catch (t: Throwable) {
        false
    }

    override fun shareText(text: String, subject: String): Boolean = writeClipboard(text)

    override fun shareFile(path: String, mime: String): Boolean = try {
        val file = File(path)
        if (!file.exists()) false else {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.parentFile ?: file)
            }
            true
        }
    } catch (t: Throwable) {
        false
    }

    override fun exportFile(path: String): Boolean {
        val source = File(path)
        if (!source.isFile) return false
        val target = File(defaultWorkspace(), source.name)
        return try {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            true
        } catch (t: Throwable) {
            false
        }
    }

    override fun availableSqlite(): Boolean = true

    override suspend fun sqliteQuery(dbPath: String, sql: String, limit: Int): String = withContext(Dispatchers.IO) {
        val url = "jdbc:sqlite:$dbPath"
        try {
            DriverManager.getConnection(url).use { connection ->
                connection.createStatement().use { statement ->
                    statement.fetchSize = limit.coerceIn(1, 5000)
                    statement.executeQuery(sql).use { rows ->
                        val meta = rows.metaData
                        val columns = meta.columnCount
                        val sb = StringBuilder()
                        for (i in 1..columns) {
                            if (i > 1) sb.append(',')
                            sb.append(meta.getColumnName(i))
                        }
                        sb.append('\n')
                        var count = 0
                        while (rows.next() && count < limit) {
                            for (i in 1..columns) {
                                if (i > 1) sb.append(',')
                                val value = rows.getString(i)
                                sb.append(if (value == null) "" else value.replace(",", "\\,"))
                            }
                            sb.append('\n')
                            count++
                        }
                        sb.append("($count row${if (count == 1) "" else "s"})")
                        sb.toString()
                    }
                }
            }
        } catch (t: Throwable) {
            "sqlite error: ${t.message ?: t::class.simpleName}"
        }
    }

    override suspend fun sqliteExec(dbPath: String, sql: String): String = withContext(Dispatchers.IO) {
        val url = "jdbc:sqlite:$dbPath"
        try {
            DriverManager.getConnection(url).use { connection ->
                connection.createStatement().use { statement ->
                    val updated = statement.executeUpdate(sql)
                    "ok, $updated row${if (updated == 1) "" else "s"} affected"
                }
            }
        } catch (t: Throwable) {
            "sqlite error: ${t.message ?: t::class.simpleName}"
        }
    }

    override suspend fun readPdfText(path: String): String = withContext(Dispatchers.IO) {
        try {
            Loader.loadPDF(File(path)).use { document -> PDFTextStripper().getText(document) }
        } catch (t: Throwable) {
            ""
        }
    }

    override suspend fun renderPdfPage(path: String, page: Int, width: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            Loader.loadPDF(File(path)).use { document ->
                if (page < 1 || page > document.numberOfPages) return@withContext null
                val renderer = PDFRenderer(document)
                val box = document.getPage(page - 1).cropBox
                val scale = (width.coerceIn(200, 4000) / box.width).coerceIn(0.2f, 6f)
                val image: BufferedImage = renderer.renderImage(page - 1, scale, ImageType.RGB)
                val out = java.io.ByteArrayOutputStream()
                ImageIO.write(image, "png", out)
                out.toByteArray()
            }
        } catch (t: Throwable) {
            null
        }
    }

    override suspend fun pdfMerge(inputs: List<String>, out: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val merger = PDFMergerUtility()
            inputs.forEach { merger.addSource(File(it)) }
            merger.destinationFileName = out
            merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache())
            true
        } catch (t: Throwable) {
            false
        }
    }

    override suspend fun pdfSplit(input: String, out: String, pages: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val bounds = pages.split('-')
            val from = bounds.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
            val to = bounds.getOrNull(1)?.trim()?.toIntOrNull() ?: from
            Loader.loadPDF(File(input)).use { document ->
                val splitter = Splitter()
                splitter.startPage = from.coerceAtLeast(1)
                splitter.endPage = to.coerceAtMost(document.numberOfPages)
                val parts = splitter.split(document)
                if (parts.isEmpty()) return@withContext false
                val target = File(out)
                target.parentFile?.mkdirs()
                parts.first().save(target)
                parts.drop(1).forEach { it.close() }
                true
            }
        } catch (t: Throwable) {
            false
        }
    }

    private fun emptyPng(): ByteArray {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val out = java.io.ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }
}
