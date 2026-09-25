
package com.lucent.app.harness

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lucent.app.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class AndroidHarnessHost(private val context: Context) : HarnessHost {

    override val android: Boolean = true

    override fun filesDir(): File = context.filesDir

    override fun cacheDir(): File = context.cacheDir

    override fun defaultWorkspace(): File {
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val base = if (documents != null && (documents.isDirectory || documents.mkdirs())) documents
        else context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "Lucent").apply { if (!exists()) mkdirs() }
    }

    override fun workspaceCandidates(): List<String> {
        val out = mutableListOf<String>()
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (documents != null) out.add(File(documents, "Lucent").path)
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let {
            out.add(File(it, "Lucent").path)
        }
        context.getExternalFilesDir(null)?.let { out.add(File(it, "Lucent").path) }
        out.add(File(context.filesDir, "workspace").path)
        return out
    }

    override fun capabilities(): Set<String> {
        val out = mutableSetOf("clipboard", "notify", "toast", "vibrate", "share", "open_url", "export", "apps", "sqlite", "pdf")
        if (LucentAccessibilityService.isRunning()) {
            out.add("accessibility")
            out.add("screenshot")
            out.add("screen")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) out.add("torch")
        out.add("sensors")
        out.add("location")
        return out
    }

    override fun permissionNote(): String =
        if (LucentAccessibilityService.isRunning()) "" else "Turn the Lucent accessibility service on to read the screen and tap."

    override fun openUrl(url: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (t: Throwable) {
        false
    }

    override fun shareText(text: String, subject: String): Boolean = try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, subject.ifBlank { "Share" }).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (t: Throwable) {
        false
    }

    override fun shareFile(path: String, mime: String): Boolean = false

    override fun notify(title: String, text: String): Boolean = try {
        val manager = NotificationManagerCompat.from(context)
        val notification = NotificationCompat.Builder(context, "lucent_agent")
            .setSmallIcon(com.lucent.app.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
        true
    } catch (t: Throwable) {
        false
    }

    override fun toast(text: String): Boolean = try {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        true
    } catch (t: Throwable) {
        false
    }

    override fun vibrate(millis: Long): Boolean = try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis.coerceIn(30, 3000), VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(millis)
        }
        true
    } catch (t: Throwable) {
        false
    }

    override fun readClipboard(): String = try {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    } catch (t: Throwable) {
        ""
    }

    override fun writeClipboard(text: String): Boolean = try {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.setPrimaryClip(ClipData.newPlainText("Lucent", text))
        true
    } catch (t: Throwable) {
        false
    }

    override fun screenText(): String = LucentAccessibilityService.dump()

    override suspend fun screenshot(): ByteArray? = LucentAccessibilityService.screenshot()

    override suspend fun tap(x: Int, y: Int): Boolean = LucentAccessibilityService.tap(x, y)

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, millis: Int): Boolean =
        LucentAccessibilityService.swipe(x1, y1, x2, y2, millis)

    override suspend fun typeText(text: String): Boolean = LucentAccessibilityService.type(text)

    override suspend fun pressKey(key: String): Boolean = LucentAccessibilityService.key(key)

    override suspend fun deviceInfo(): String = withContext(Dispatchers.IO) {
        val runtime = Runtime.getRuntime()
        val stat = StatFs(Environment.getDataDirectory().path)
        val free = stat.availableBytes
        val total = stat.totalBytes
        buildString {
            append("Model: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Storage: ").append(Workspace.humanSize(free)).append(" free of ").append(Workspace.humanSize(total)).append('\n')
            append("Memory: ").append(Workspace.humanSize(runtime.totalMemory() - runtime.freeMemory()))
            append(" used by this process, ").append(Workspace.humanSize(runtime.maxMemory())).append(" limit\n")
            val battery = batteryLine()
            if (battery.isNotEmpty()) append(battery).append('\n')
            append("Accessibility service: ").append(if (LucentAccessibilityService.isRunning()) "running" else "off").append('\n')
            append("Termux: ").append(TermuxBridge.describe(context))
        }.trimEnd()
    }

    private fun batteryLine(): String = try {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (level < 0) "" else "Battery: $level%"
    } catch (t: Throwable) {
        ""
    }

    override suspend fun launchApp(packageName: String): Boolean = try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (t: Throwable) {
        false
    }

    override suspend fun stopApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val outcome = HarnessRuntime.runShell("am force-stop $packageName", null, 30)
        outcome.ok
    }

    override suspend fun installedApps(query: String): String = withContext(Dispatchers.IO) {
        val manager = context.packageManager
        val packages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                manager.getInstalledPackages(0)
            }
        } catch (t: Throwable) {
            emptyList()
        }
        val user = mutableListOf<String>()
        val system = mutableListOf<String>()
        packages.forEach { info ->
            val label = try {
                info.applicationInfo?.loadLabel(manager)?.toString().orEmpty()
            } catch (t: Throwable) {
                ""
            }
            val entry = "$label — ${info.packageName}"
            if (query.isNotBlank() && !entry.contains(query, ignoreCase = true)) return@forEach
            val isSystem = (info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
            if (isSystem) system.add(entry) else user.add(entry)
        }
        buildString {
            append("User apps (").append(user.size).append("):\n")
            append(user.sorted().take(80).joinToString("\n"))
            append("\nSystem apps (").append(system.size).append("):\n")
            append(system.sorted().take(40).joinToString("\n"))
        }
    }

    override suspend fun notifications(): String = withContext(Dispatchers.IO) {
        val outcome = HarnessRuntime.runShell(
            "dumpsys notification --noredact 2>/dev/null | grep -E 'NotificationRecord|android.title|android.text' | head -n 60",
            null,
            45
        )
        if (outcome.text.isBlank()) {
            "No notifications could be read. Reading them needs the privileged shell; notification access alone is not enough."
        } else outcome.text
    }

    override suspend fun location(): String = withContext(Dispatchers.IO) {
        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withContext "No location service."
            val provider = when {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> return@withContext "Location is switched off on this phone."
            }
            @Suppress("MissingPermission")
            val last = manager.getLastKnownLocation(provider)
                ?: return@withContext "No location fix yet."
            "Latitude ${last.latitude}, longitude ${last.longitude}, accuracy ${last.accuracy} m, provider $provider"
        } catch (t: Throwable) {
            "Location is not permitted: ${t.message ?: t::class.simpleName}"
        }
    }

    override suspend fun sensors(): String = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return@withContext "No sensors on this device."
        manager.getSensorList(Sensor.TYPE_ALL).take(40).joinToString("\n") { sensor ->
            "${sensor.name} (type ${sensor.type}, ${sensor.vendor})"
        }
    }

    override fun setTorch(on: Boolean): Boolean = try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        val id = manager.cameraIdList.firstOrNull { camera ->
            manager.getCameraCharacteristics(camera)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        manager.setTorchMode(id, on)
        true
    } catch (t: Throwable) {
        false
    }

    override fun exportFile(path: String): Boolean {
        val source = File(path)
        if (!source.isFile) return false
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, source.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } } ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (t: Throwable) {
            false
        }
    }

    override suspend fun askUser(question: String, options: List<String>): String =
        suspendCancellableCoroutine { continuation ->
            HarnessAsk.request(question, options) { answer -> continuation.resume(answer) }
        }

    override suspend fun importFile(hint: String): String = ""

    override fun availableSqlite(): Boolean = true

    override suspend fun sqliteQuery(dbPath: String, sql: String, limit: Int): String = withContext(Dispatchers.IO) {
        try {
            SQLiteDatabase.openDatabase(
                dbPath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { database ->
                database.rawQuery(sql, null).use { cursor ->
                    val sb = StringBuilder()
                    sb.append(cursor.columnNames.joinToString(",")).append('\n')
                    var count = 0
                    while (cursor.moveToNext() && count < limit.coerceIn(1, 5000)) {
                        val row = (0 until cursor.columnCount).joinToString(",") { index ->
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> ""
                                Cursor.FIELD_TYPE_BLOB -> "<blob>"
                                else -> cursor.getString(index).orEmpty().replace(",", "\\,")
                            }
                        }
                        sb.append(row).append('\n')
                        count++
                    }
                    sb.append("($count row${if (count == 1) "" else "s"})")
                    sb.toString()
                }
            }
        } catch (t: Throwable) {
            "sqlite error: ${t.message ?: t::class.simpleName}"
        }
    }

    override suspend fun sqliteExec(dbPath: String, sql: String): String = withContext(Dispatchers.IO) {
        try {
            SQLiteDatabase.openDatabase(
                dbPath,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
            ).use { database ->
                database.execSQL(sql)
                "ok"
            }
        } catch (t: Throwable) {
            "sqlite error: ${t.message ?: t::class.simpleName}"
        }
    }

    override suspend fun readPdfText(path: String): String = withContext(Dispatchers.IO) {
        SimplePdfText.extract(File(path)).take(400000)
    }

    override suspend fun renderPdfPage(path: String, page: Int, width: Int): ByteArray? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return@withContext null
        try {
            PdfRenderer(ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                if (page < 1 || page > renderer.pageCount) return@withContext null
                renderer.openPage(page - 1).use { pdfPage ->
                    val target = width.coerceIn(200, 3000)
                    val height = (target.toFloat() / pdfPage.width * pdfPage.height).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(target, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val out = java.io.ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    bitmap.recycle()
                    out.toByteArray()
                }
            }
        } catch (t: Throwable) {
            null
        }
    }

    override suspend fun pdfMerge(inputs: List<String>, out: String): Boolean = withContext(Dispatchers.IO) {
        if (!HarnessRuntime.shellReady()) return@withContext false
        val target = File(out)
        target.parentFile?.mkdirs()
        val list = inputs.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
        val outcome = HarnessRuntime.runShell(
            "python3 -c \"import sys;from pypdf import PdfWriter;w=PdfWriter()\nfor p in sys.argv[1:]:w.append(p)\nw.write('${target.path}')\" $list",
            null,
            300
        )
        outcome.ok && target.exists()
    }

    override suspend fun pdfSplit(input: String, out: String, pages: String): Boolean = withContext(Dispatchers.IO) {
        if (!HarnessRuntime.shellReady()) return@withContext false
        val target = File(out)
        target.parentFile?.mkdirs()
        val bounds = pages.split('-')
        val from = bounds.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
        val to = bounds.getOrNull(1)?.trim()?.toIntOrNull() ?: from
        val outcome = HarnessRuntime.runShell(
            "python3 -c \"from pypdf import PdfReader,PdfWriter;r=PdfReader('${input.replace("'", "'\\''")}');" +
                "w=PdfWriter()\nfor i in range(${from - 1},min($to,len(r.pages))):w.add_page(r.pages[i])\n" +
                "w.write('${target.path}')\"",
            null,
            300
        )
        outcome.ok && target.exists()
    }

    fun audioFocus(): Boolean = try {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        manager != null
    } catch (t: Throwable) {
        false
    }

    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
        }
    }

    fun openApp(): Boolean = try {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (t: Throwable) {
        false
    }
}
