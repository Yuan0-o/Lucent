package com.lucent.desktop

import android.content.DesktopContext
import androidx.activity.compose.DesktopBackDispatcher
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Notification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.lucent.app.AppScope
import com.lucent.app.data.AttachmentAccess
import com.lucent.app.data.AttachmentMigration
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.TrashCleanup
import com.lucent.app.reminders.ReminderScheduler
import com.lucent.app.ui.AppLockController
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    System.setProperty("java.net.useSystemProxies", "true")
    System.setProperty("java.net.preferIPv4Stack", "true")

    val context = DesktopContext

    val startup = try {
        runBlocking { SettingsRepository(context).startupPrefsOnce() }
            .also { com.lucent.app.data.SettingsCache.seed(it) }
            .also { com.lucent.app.data.SessionRestore.hydrate(it.sessionSnapshot) }
    } catch (t: Throwable) {
        SettingsRepository.StartupPrefs(
            display = SettingsRepository.DisplayPrefs("system", "SUNSET", "system"),
            appLockEnabled = false,
            startupLoggingEnabled = false,
            systemIntegrationEnabled = false
        )
    }

    com.lucent.app.i18n.L.apply(startup.appLanguage)
    AppLockController.markProcessStarted(startup.appLockEnabled)
    com.lucent.app.data.StartupLog.setEnabled(startup.startupLoggingEnabled)
    com.lucent.app.AppScope.appContext = context
    com.lucent.app.data.PrivilegedShell.install(com.lucent.app.data.DesktopShell)
    com.lucent.app.data.AutoUpdate.installer = com.lucent.app.data.DesktopUpdateInstaller()

    val focusRequests = MutableStateFlow(0L)
    if (!com.lucent.desktop.platform.SingleInstance.acquire(context) {
            focusRequests.value = System.currentTimeMillis()
        }
    ) {
        return
    }

    AppScope.io.launch { runCatching { ReminderScheduler.rescheduleAll(context) } }
    AppScope.io.launch { runCatching { TrashCleanup.purgeExpired(context) } }
    AppScope.io.launch {
        runCatching {
            AttachmentMigration.runIfNeeded(context)
            AttachmentMigration.encryptExistingAttachments(context)
        }
    }
    AppScope.io.launch { runCatching { AttachmentAccess.clearPreviewCache(context) } }
    AppScope.io.launch { runCatching { com.lucent.app.data.AppDatabase.getInstance(context) } }
    AppScope.io.launch { runCatching { com.lucent.app.data.AutoBackupRunner.ensureStarted(context) } }

    application {
        val windowState = rememberWindowState(placement = WindowPlacement.Maximized)
        val trayState = rememberTrayState()
        var windowVisible by remember { mutableStateOf(true) }
        val settingsRepo = remember { SettingsRepository(context) }
        val closeToTray by settingsRepo.closeToTray.collectAsState(
            initial = com.lucent.app.data.SettingsCache.closeToTray)
        val fallbackIcon = rememberVectorPainter(Icons.Default.AutoAwesome)
        val icon: Painter = remember {
            runCatching {
                val bytes = Thread.currentThread().contextClassLoader!!
                    .getResourceAsStream("icons/lucent.png")!!.readBytes()
                BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap())
            }.getOrNull()
        } ?: fallbackIcon

        LaunchedEffect(trayState) {
            ReminderScheduler.notifier = { title, message ->
                trayState.sendNotification(Notification(title, message))
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { com.lucent.app.data.DesktopShell.exitRequested }.collect { wanted ->
                if (wanted) exitApplication()
            }
        }

        Tray(
            state = trayState,
            icon = icon,
            tooltip = "Lucent",
            menu = {
                Item(com.lucent.app.i18n.S.trayOpen, onClick = { windowVisible = true })
                Item(com.lucent.app.i18n.S.trayExit, onClick = ::exitApplication)
            }
        )

        Window(
            onCloseRequest = { if (closeToTray) windowVisible = false else exitApplication() },
            visible = windowVisible,
            state = windowState,
            title = "Lucent",
            icon = icon,
            onKeyEvent = { event ->
                val down = event.type == KeyEventType.KeyDown
                when {
                    down && event.key == Key.F11 -> {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Maximized
                            else WindowPlacement.Fullscreen
                        true
                    }
                    down && event.key == Key.Escape ->
                        DesktopBackDispatcher.dispatch()

                    down && event.isCtrlPressed && event.key == Key.N -> {
                        com.lucent.app.AppNavigation.requestScreen(com.lucent.app.Screen.Notes)
                        com.lucent.app.AppNavigation.requestComposeNote()
                        true
                    }
                    down && event.isCtrlPressed && event.key == Key.T -> {
                        com.lucent.app.AppNavigation.requestScreen(com.lucent.app.Screen.Tasks)
                        com.lucent.app.AppNavigation.requestComposeTask()
                        true
                    }
                    down && event.isCtrlPressed && event.key == Key.F -> {
                        com.lucent.app.AppNavigation.requestScreen(com.lucent.app.Screen.Search)
                        true
                    }
                    down && event.isCtrlPressed && event.key == Key.One -> {
                        com.lucent.app.AppNavigation.requestScreen(com.lucent.app.Screen.Assistant); true
                    }
                    down && event.isCtrlPressed && event.key == Key.Two -> {
                        com.lucent.app.AppNavigation.requestScreen(com.lucent.app.Screen.Tasks); true
                    }
                    down && event.isCtrlPressed && event.key == Key.Three -> {
                        com.lucent.app.AppNavigation.requestScreen(com.lucent.app.Screen.Notes); true
                    }
                    down && event.isCtrlPressed && event.key == Key.Four -> {
                        com.lucent.app.AppNavigation.requestScreen(com.lucent.app.Screen.Insights); true
                    }
                    down && event.isCtrlPressed && event.key == Key.Five -> {
                        com.lucent.app.AppNavigation.requestScreen(com.lucent.app.Screen.Search); true
                    }
                    down && event.isCtrlPressed && event.key == Key.Comma -> {
                        com.lucent.app.AppNavigation.requestScreen(com.lucent.app.Screen.Settings); true
                    }
                    down && event.isCtrlPressed && event.key == Key.W ->
                        DesktopBackDispatcher.dispatch()
                    else -> false
                }
            }
        ) {
            LaunchedEffect(Unit) {
                focusRequests.collect { tick ->
                    if (tick == 0L) return@collect
                    windowVisible = true
                    if (windowState.isMinimized) windowState.isMinimized = false
                    window.toFront()
                }
            }
            DesktopApp(startup, active = windowVisible && !windowState.isMinimized)
        }
    }

    runCatching { com.lucent.desktop.platform.SingleInstance.release() }
    runCatching { com.lucent.app.local.LocalLlm.shutdown() }
    runCatching { AttachmentAccess.clearPreviewCache(context) }
}
