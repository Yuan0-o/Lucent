package com.lucent.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lucent.app.local.ILocalLlmCallback
import com.lucent.app.local.ILocalLlmEngine
import com.lucent.app.local.LocalLlm
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class GenerationService : Service() {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val engineBinder = object : ILocalLlmEngine.Stub() {
        override fun ensureLoaded(gpuEnabled: Boolean): Boolean = try {
            runBlocking { LocalLlm.engineEnsureLoaded(applicationContext, gpuEnabled) }
        } catch (t: Throwable) {
            false
        }

        override fun supportsVision(): Boolean = try {
            LocalLlm.engineSupportsVision()
        } catch (t: Throwable) {
            false
        }

        override fun generate(
            roles: Array<String>,
            texts: Array<String>,
            imagePaths: Array<String>,
            callback: ILocalLlmCallback
        ) {
            engineScope.launch {
                val rc = try {
                    val images = imagePaths.mapNotNull { path ->
                        try {
                            File(path).readBytes()
                        } catch (t: Throwable) {
                            null
                        }
                    }
                    val messages = roles.indices.map { roles[it] to texts[it] }
                    LocalLlm.engineGenerate(messages, images) { piece ->
                        try {
                            callback.onPiece(piece)
                        } catch (t: RemoteException) {
                        }
                    }
                } catch (t: Throwable) {
                    LocalLlm.RC_ENGINE_GLUE_ERROR
                }
                try {
                    callback.onDone(rc)
                } catch (t: RemoteException) {
                }
            }
        }

        override fun stop() {
            try {
                LocalLlm.engineStop()
            } catch (t: Throwable) {
            }
        }

        override fun shutdown() {
            try {
                LocalLlm.engineShutdown()
            } catch (t: Throwable) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = engineBinder

    override fun onDestroy() {
        engineScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name = intent?.getStringExtra(EXTRA_NAME)?.takeIf { it.isNotBlank() } ?: com.lucent.app.i18n.S.tabAssistant
        ensureChannel(this)
        val notification = buildNotification(this, name)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (t: Throwable) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "assistant_generation"
        private val CHANNEL_NAME: String get() = com.lucent.app.i18n.S.genChannelName
        private val CHANNEL_DESC: String get() = com.lucent.app.i18n.S.genChannelDesc
        private const val NOTIF_ID = 5170
        private const val EXTRA_NAME = "assistant_name"

        fun start(context: Context, assistantName: String) {
            val intent = Intent(context.applicationContext, GenerationService::class.java)
                .putExtra(EXTRA_NAME, assistantName)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, GenerationService::class.java)
            )
        }

        private fun ensureChannel(context: Context) {
            val channel = NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(CHANNEL_NAME)
                .setDescription(CHANNEL_DESC)
                .setVibrationEnabled(false)
                .setShowBadge(false)
                .build()
            NotificationManagerCompat.from(context.applicationContext).createNotificationChannel(channel)
        }

        private fun buildNotification(context: Context, name: String): Notification {
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(com.lucent.app.i18n.S.genReplyingTitle(name))
                .setContentText(com.lucent.app.i18n.S.genReplyingBody)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(open)
                .build()
        }
    }
}
