package com.lucent.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lucent.app.data.AutoUpdate
import com.lucent.app.data.ReleaseAsset
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.StartupLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class UpdateDownloadService : Service() {

    companion object {
        const val ACTION_START = "com.lucent.app.action.UPDATE_DOWNLOAD_START"
        const val ACTION_CANCEL = "com.lucent.app.action.UPDATE_DOWNLOAD_CANCEL"
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"

        private const val CHANNEL_ID = "update_download"
        private const val NOTIFICATION_ID = 7102
        private const val DOWNLOAD_TIMEOUT_MS = 20L * 60L * 1000L
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val NOTIFY_INTERVAL_MS = 400L

        fun start(context: Context, asset: ReleaseAsset) {
            val intent = Intent(context, UpdateDownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_URL, asset.url)
                .putExtra(EXTRA_NAME, asset.name)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, UpdateDownloadService::class.java).setAction(ACTION_CANCEL)
            runCatching { context.startService(intent) }
        }

        fun partialFile(context: Context, name: String): File =
            File(File(context.cacheDir, "updates").apply { mkdirs() }, name)

        fun deleteStoredCopy(context: Context, name: String): Boolean {
            if (name.isBlank()) return false
            val folder = SettingsCache.autoBackup.folderUri
            if (folder.isBlank()) return false
            return try {
                val tree = Uri.parse(folder)
                val resolver = context.contentResolver
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                    tree,
                    DocumentsContract.getTreeDocumentId(tree)
                )
                var found: Uri? = null
                resolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getString(1) == name) {
                            found = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                            break
                        }
                    }
                }
                val document = found ?: return false
                DocumentsContract.deleteDocument(resolver, document)
            } catch (t: Throwable) {
                StartupLog.event(context, "update: the stored copy could not be removed (${t.message})")
                false
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var foregroundStarted = false
    private var currentName: String = ""
    private var lastPercent = -1
    private var lastNotifiedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val name = currentName.ifBlank { intent.getStringExtra(EXTRA_NAME).orEmpty() }
                job?.cancel()
                job = null
                removePartial(name)
                AutoUpdate.reportDownloadCancelled()
                AutoUpdate.report(com.lucent.app.i18n.S.updateDownloadCancelled)
                StartupLog.event(applicationContext, "update: the download was cancelled by the user")
                finish()
            }
            else -> {
                val url = intent?.getStringExtra(EXTRA_URL).orEmpty()
                val name = intent?.getStringExtra(EXTRA_NAME).orEmpty()
                if (url.isBlank() || name.isBlank()) {
                    finish()
                } else if (job?.isActive != true) {
                    begin(url, name)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun begin(url: String, name: String) {
        currentName = name
        lastPercent = -1
        lastNotifiedAt = 0L
        promoteToForeground(-1)
        job = scope.launch {
            val target = partialFile(applicationContext, name)
            val finished = try {
                withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) { fetch(url, target) }
            } catch (t: Throwable) {
                StartupLog.event(
                    applicationContext,
                    "update: download failed (${t::class.simpleName}: ${t.message})"
                )
                null
            }
            if (!isActive) {
                target.delete()
                return@launch
            }
            if (finished != true || target.length() <= 0L) {
                target.delete()
                AutoUpdate.reportDownloadFailed(com.lucent.app.i18n.S.updateDownloadFailed)
                StartupLog.event(applicationContext, "update: the download did not finish, partial file removed")
                finish()
                return@launch
            }
            StartupLog.event(applicationContext, "update: downloaded $name (${target.length()} bytes)")
            val stored = copyToBackupFolder(target, name)
            if (stored) {
                StartupLog.event(applicationContext, "update: the installer was placed in the backup folder")
            }
            AutoUpdate.reportProgress(1f)
            AutoUpdate.reportDownloadReady(listOf(name))
            AutoUpdate.report(null)
            stopForegroundCompat()
            notifyReady(stored)
            stopSelf()
        }
    }

    private suspend fun fetch(url: String, target: File): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(DOWNLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder().url(url).header("User-Agent", "Lucent").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                StartupLog.event(applicationContext, "update: the download answered ${response.code}")
                return@withContext false
            }
            val body = response.body ?: return@withContext false
            val total = body.contentLength().takeIf { it > 0L }
            var copied = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        coroutineContext.ensureActive()
                        if (total != null) {
                            val fraction = (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            AutoUpdate.reportProgress(fraction)
                            val percent = (fraction * 100f).toInt()
                            val now = System.currentTimeMillis()
                            if (percent != lastPercent && now - lastNotifiedAt >= NOTIFY_INTERVAL_MS) {
                                lastPercent = percent
                                lastNotifiedAt = now
                                promoteToForeground(percent)
                            }
                        }
                    }
                }
            }
        }
        target.length() > 0L
    }

    private fun removePartial(name: String) {
        if (name.isBlank()) return
        val file = partialFile(applicationContext, name)
        if (file.exists() && file.delete()) {
            StartupLog.event(applicationContext, "update: the partial download was removed")
        }
    }

    private fun copyToBackupFolder(source: File, name: String): Boolean {
        val folder = SettingsCache.autoBackup.folderUri
        if (folder.isBlank()) return false
        return try {
            val tree = Uri.parse(folder)
            val resolver = contentResolver
            val existing = findChild(resolver, tree, name)
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree)
            )
            val target = existing ?: DocumentsContract.createDocument(
                resolver,
                parent,
                "application/vnd.android.package-archive",
                name
            ) ?: return false
            resolver.openOutputStream(target, "wt")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return false
            true
        } catch (t: Throwable) {
            StartupLog.event(
                applicationContext,
                "update: the installer could not be copied to the backup folder (${t.message})"
            )
            false
        }
    }

    private fun findChild(resolver: ContentResolver, tree: Uri, name: String): Uri? = try {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree)
        )
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            var found: Uri? = null
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    found = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                    break
                }
            }
            found
        }
    } catch (t: Throwable) {
        null
    }

    private fun promoteToForeground(percent: Int) {
        createChannel()
        val label = if (percent < 0) com.lucent.app.i18n.S.updateNotifPreparing
        else com.lucent.app.i18n.S.updateNotifDownloading(percent)
        val notification = buildDownloadNotification(percent, label)
        if (!foregroundStarted) {
            foregroundStarted = true
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            postNotification(notification)
        }
    }

    private fun buildDownloadNotification(percent: Int, text: String): Notification {
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, UpdateDownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_NAME, currentName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(com.lucent.app.i18n.S.updateNotifTitle)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, percent.coerceIn(0, 100), percent < 0)
            .addAction(R.drawable.ic_notification, com.lucent.app.i18n.S.updateNotifCancel, cancel)
            .build()
    }

    private fun notifyReady(stored: Boolean) {
        createChannel()
        val text = if (stored) com.lucent.app.i18n.S.updateNotifReadyStored
        else com.lucent.app.i18n.S.updateNotifReady
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(com.lucent.app.i18n.S.updateNotifTitle)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        postNotification(notification)
    }

    private fun postNotification(notification: Notification) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (t: SecurityException) {
            StartupLog.event(applicationContext, "update: notifications are not permitted (${t.message})")
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        2,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createChannel() {
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(com.lucent.app.i18n.S.updateChannelName)
            .setDescription(com.lucent.app.i18n.S.updateChannelDesc)
            .build()
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
    }

    private fun stopForegroundCompat() {
        foregroundStarted = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun finish() {
        stopForegroundCompat()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
