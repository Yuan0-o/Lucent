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

/**
 * The Windows entry point — the desktop peer of Android's MainActivity.onCreate.
 *
 * It does the same startup work in the same order: read the appearance/lock/language the first frame
 * depends on synchronously (so the window opens already in the user's chosen look rather than blinking
 * from defaults), apply the language and lock state, kick off the idempotent startup chores off the
 * UI thread, then open the window. Instead of an Activity it opens a Compose for Desktop [Window]
 * maximized, wires F11 to a fullscreen toggle and Esc to the shared back dispatcher, and routes task
 * reminders to Windows tray notifications. On exit it frees the on-device model and clears the
 * decrypted-preview cache, exactly as MainActivity.onDestroy does.
 */
fun main() {
    // ---- Task 6: why the desktop build could not reach the API while the phone could ----
    //
    // Two JVM defaults, neither of which applies on Android:
    //
    //  1. **The system proxy is ignored.** Android hands every app the platform's proxy settings
    //     automatically; the JVM does not consult the Windows proxy configuration at all unless
    //     `java.net.useSystemProxies` is set. On any machine behind a corporate or VPN proxy — which
    //     is most managed Windows machines — every request therefore went out direct and timed out,
    //     while the same account worked fine on the phone next to it.
    //  2. **IPv6 is preferred.** The JVM tries AAAA records first. Where IPv6 is advertised but not
    //     actually routable (common on consumer Windows with a tunnel adapter left enabled) each
    //     connection stalls for the full connect timeout before falling back, which presents as
    //     "the network is never available" rather than as a delay.
    //
    // Both are set before anything can open a socket. They are properties rather than OkHttp
    // configuration on purpose: they belong to the JVM's own resolver and proxy selector, which
    // OkHttp then inherits, so this also covers any other client the desktop build ever grows.
    System.setProperty("java.net.useSystemProxies", "true")
    System.setProperty("java.net.preferIPv4Stack", "true")

    val context = DesktopContext

    // ONE synchronous read for everything the first frame needs, mirroring MainActivity. Falls back
    // to defaults on any failure so startup can never be blocked by it.
    val startup = try {
        runBlocking { SettingsRepository(context).startupPrefsOnce() }
            // Seed the first-frame settings cache (B-group task 9) — see the Android twin.
            .also { com.lucent.app.data.SettingsCache.seed(it) }
            // Round R1, task 5 — the previous run's recovery snapshot; see the Android twin.
            .also { com.lucent.app.data.SessionRestore.hydrate(it.sessionSnapshot) }
    } catch (t: Throwable) {
        SettingsRepository.StartupPrefs(
            display = SettingsRepository.DisplayPrefs("system", "SUNSET", "system"),
            appLockEnabled = false,
            startupLoggingEnabled = false,
            systemIntegrationEnabled = false
        )
    }

    // Language + lock decided before anything composes (prevents the startup "blink").
    com.lucent.app.i18n.L.apply(startup.appLanguage)
    AppLockController.markProcessStarted(startup.appLockEnabled)

    // PHASE 3 (C-4): one Lucent, ever. If an instance is already running, this launch asks it to
    // surface its window and exits before any startup chore, scheduler, or model touches disk —
    // the point at which two processes would start stepping on each other.
    val focusRequests = MutableStateFlow(0L)
    if (!com.lucent.desktop.platform.SingleInstance.acquire(context) {
            focusRequests.value = System.currentTimeMillis()
        }
    ) {
        return
    }

    // Startup chores: the same idempotent set MainActivity runs on launch, each on the process-lifetime
    // IO scope and each wrapped so one failing can't take down the others or the app.
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

    application {
        val windowState = rememberWindowState(placement = WindowPlacement.Maximized)
        val trayState = rememberTrayState()
        // PHASE 3 (C-3): whether the window is on screen. Close-to-tray hides it; the tray menu,
        // a second launch (C-4), and a tray notification-click all bring it back.
        var windowVisible by remember { mutableStateOf(true) }
        // remember{}ed: a fresh repository (and a fresh Flow) per recomposition would resubscribe
        // the collector on every frame the window state changes.
        val settingsRepo = remember { SettingsRepository(context) }
        val closeToTray by settingsRepo.closeToTray.collectAsState(initial = true)
        // The app icon — the same artwork as the Android launcher icon, bundled at
        // resources/icons/lucent.png (and lucent.ico for the installer, see build.gradle). Loaded via
        // Skia so it's version-robust; if anything goes wrong we fall back to the vector mark so a
        // missing/broken resource can never stop the window from opening.
        val fallbackIcon = rememberVectorPainter(Icons.Default.AutoAwesome)
        val icon: Painter = remember {
            runCatching {
                val bytes = Thread.currentThread().contextClassLoader!!
                    .getResourceAsStream("icons/lucent.png")!!.readBytes()
                BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap())
            }.getOrNull()
        } ?: fallbackIcon

        // Desktop has no OS alarm service, so reminders can only fire while the app runs; when they do,
        // ReminderScheduler calls this notifier, which raises a Windows tray notification.
        LaunchedEffect(trayState) {
            ReminderScheduler.notifier = { title, message ->
                trayState.sendNotification(Notification(title, message))
            }
        }

        Tray(
            state = trayState,
            icon = icon,
            tooltip = "Lucent",
            menu = {
                Item(com.lucent.app.i18n.S.trayOpen, onClick = { windowVisible = true })
                // Exit must REALLY exit even with close-to-tray on — the review's acceptance test
                // names exactly this. It is the one path that does.
                Item(com.lucent.app.i18n.S.trayExit, onClick = ::exitApplication)
            }
        )

        Window(
            // PHASE 3 (C-3): with the setting on (the default), the close button hides the window
            // and the process — reminders, tray icon, scheduler — keeps running. The old behaviour
            // is one toggle away in Settings, and Exit in the tray menu always truly quits.
            onCloseRequest = { if (closeToTray) windowVisible = false else exitApplication() },
            visible = windowVisible,
            state = windowState,
            title = "Lucent",
            icon = icon,
            onKeyEvent = { event ->
                val down = event.type == KeyEventType.KeyDown
                when {
                    // F11 toggles fullscreen (returns to maximized), as the requirement asks.
                    down && event.key == Key.F11 -> {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Maximized
                            else WindowPlacement.Fullscreen
                        true
                    }
                    // Esc drives the shared back dispatcher (close the open editor, then the sub-screen),
                    // reproducing Android's system-back for the verbatim screens' BackHandlers.
                    down && event.key == Key.Escape ->
                        DesktopBackDispatcher.dispatch()

                    // ---- PHASE 3 (review C-7): the conventional desktop set. ----
                    // All routed through AppNavigation, which the shell already consumes for
                    // cross-screen jumps — no screen learns anything new. Window-level onKeyEvent
                    // sees these only when no focused child consumed them, so a Ctrl+N typed into a
                    // text field still reaches here (text fields don't claim Ctrl chords), while
                    // anything a field DOES claim (Ctrl+C/V/A) never arrives — exactly right.
                    down && event.isCtrlPressed && event.key == Key.N -> {
                        // New note: land on the Notes screen AND open its composer — the same pair
                        // of signals the widgets/shortcuts path sends on Android.
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
                    // Ctrl+1..5 mirror the sidebar order top-to-bottom; Ctrl+Comma is Settings, the
                    // near-universal desktop convention.
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
                    // Ctrl+W = "close what's open" — same meaning as Esc here: the back dispatcher
                    // closes the editor first, then the sub-screen. It never closes the window;
                    // the window close button (and the tray Exit) own that.
                    down && event.isCtrlPressed && event.key == Key.W ->
                        DesktopBackDispatcher.dispatch()
                    else -> false
                }
            }
        ) {
            // PHASE 3 (C-4): a later launch knocked — surface this window. Un-minimize as well:
            // "focus" on a minimized window that stays minimized is indistinguishable from nothing
            // having happened, which is how single-instance guards get reported as broken.
            LaunchedEffect(Unit) {
                focusRequests.collect { tick ->
                    if (tick == 0L) return@collect
                    windowVisible = true
                    if (windowState.isMinimized) windowState.isMinimized = false
                    window.toFront()
                }
            }
            DesktopApp(startup)
        }
    }

    // application { } returns once all windows close. Free the model and wipe decrypted previews.
    runCatching { com.lucent.desktop.platform.SingleInstance.release() }
    runCatching { com.lucent.app.local.LocalLlm.shutdown() }
    runCatching { AttachmentAccess.clearPreviewCache(context) }
}
