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

/**
 * A short-lived foreground service that runs only while the assistant is generating a reply, so the
 * work survives the app being sent to the background (issue 17) — AND, as of P3-2, the isolated
 * process that hosts the local-model engine itself.
 *
 * ### Why a foreground service
 * Coroutine work in [com.lucent.app.ui.AssistantController] already lives on a process-lifetime
 * scope, so leaving the Assistant *tab* never interrupted a reply. What this adds is protection from
 * the OS reclaiming the whole process once the app is in the background: a running foreground service
 * raises the process's importance, so Android is far less likely to kill it mid-reply. The service is
 * started when a send begins and stopped the instant the reply finishes (or is stopped), so there is
 * no lingering notification and no battery cost once the work is done.
 *
 * ### Defensive by design
 * The controller starts and stops this best-effort, inside try/catch: modern Android places real
 * restrictions on starting foreground services and on service types, and the *correct* failure mode
 * for "couldn't keep the process alive extra-hard" is to simply generate on the background scope as
 * before — never to crash. The service itself also stops immediately if it can't enter the foreground
 * state, for the same reason.
 *
 * ### P3-2 — also the isolated engine's host
 * AndroidManifest.xml now declares `android:process=":llm"` on this component, so Android runs it
 * (and only it — nothing else in the manifest shares that process) in a separate process from the
 * UI. [engineBinder] is the AIDL surface [com.lucent.app.local.LocalLlmProxy] binds to from the main
 * process; its methods do essentially nothing themselves beyond marshalling — the real work is still
 * [LocalLlm]'s `engineXxx` functions, on [LocalLlm]'s own single-thread dispatcher, completely
 * unchanged by living behind a binder call now instead of a direct one. The two roles (start/stop
 * foreground promotion here, AIDL binding below) are independent and layer cleanly: this Service is
 * "both started and bound" for as long as a generation is in flight and the app is open, which
 * Android supports natively — a plain `unbindService` alone does not destroy a Service that is also
 * currently started, and a bound-but-not-started Service is not destroyed just because [stop] was
 * called between turns. See [LocalLlmProxy][com.lucent.app.local.LocalLlmProxy]'s class doc for the
 * main-process side.
 */
class GenerationService : Service() {

    // P3-2: hosts the coroutines that call into LocalLlm's engine-side functions and relay results
    // back over the binder. Cancelled in onDestroy. This scope is pure glue — the actual llama.cpp
    // work still funnels through LocalLlm's OWN single-thread dispatcher regardless of what
    // dispatcher this scope uses, so Dispatchers.Default here is just "somewhere to launch from",
    // not a change to how native calls are serialized.
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val engineBinder = object : ILocalLlmEngine.Stub() {
        override fun ensureLoaded(gpuEnabled: Boolean): Boolean = try {
            // Blocking by design (see ILocalLlmEngine.aidl) — runBlocking here parks one Binder
            // pool thread for the duration of a cold load, which is expected and acceptable for a
            // service whose whole job is doing exactly this on request.
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
            // oneway: must return immediately. The actual generation runs on engineScope, which
            // itself hands off to LocalLlm's own llmDispatcher — this launch() call is only ever
            // "in flight" for the few microseconds it takes to hop dispatchers.
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
                            // The caller went away mid-stream. engineStop() cannot help here — the
                            // decode loop that would check it is what's still running on
                            // llmDispatcher — so this just stops relaying pieces nobody can
                            // receive; the loop finishes (or the user's next ensureLoaded/generate
                            // reconnects) on its own.
                        }
                    }
                } catch (t: Throwable) {
                    LocalLlm.RC_ENGINE_GLUE_ERROR
                }
                try {
                    callback.onDone(rc)
                } catch (t: RemoteException) {
                    // Caller already gone; nothing left to notify.
                }
            }
        }

        override fun stop() {
            try {
                LocalLlm.engineStop()
            } catch (t: Throwable) {
                // stop() is best-effort by contract even in the pre-P3-2 original.
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
                // API 34+ requires a declared service type that matches a held permission.
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (t: Throwable) {
            // Couldn't enter the foreground — bail out cleanly. Generation continues on the
            // controller's own scope regardless.
            stopSelf()
        }
        // Not sticky: if the process is killed anyway, we don't want the OS respawning a bare service
        // with no generation attached to it.
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "assistant_generation"
        // Read at call time so the channel registers under the current UI language (see Notifications).
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

        /** A quiet, low-importance channel — this is an activity indicator, not an alert. */
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
