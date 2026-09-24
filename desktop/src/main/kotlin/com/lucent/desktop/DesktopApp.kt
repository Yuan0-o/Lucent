package com.lucent.desktop

import android.content.DesktopContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppNavigation
import com.lucent.app.Screen
import com.lucent.app.data.SettingsRepository
import com.lucent.app.ui.FluidGlassBackground
import com.lucent.app.ui.LocalBackgroundEnvironment
import com.lucent.app.ui.rememberBackgroundEnvironment
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LockScreen
import com.lucent.app.ui.LucentSplash
import com.lucent.app.ui.LucentPalette
import com.lucent.app.ui.PALETTE_CYCLE
import com.lucent.app.ui.rememberCyclingPaletteColors
import com.lucent.app.ui.LucentThemeMode
import com.lucent.app.ui.AssistantScreen
import com.lucent.app.ui.InsightsScreen
import com.lucent.app.ui.LastScreen
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.NotesScreen
import com.lucent.app.ui.SearchScreen
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.SettingsScreen
import com.lucent.app.ui.TasksScreen
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.lucentTypography
import kotlinx.coroutines.delay

@Composable
fun DesktopApp(startup: SettingsRepository.StartupPrefs, active: Boolean) {
    val context = DesktopContext
    val repo = remember { SettingsRepository(context) }
    val systemDark = isSystemInDarkTheme()

    val themeMode by repo.themeMode.collectAsState(initial = startup.display.themeMode)
    val paletteName by repo.palette.collectAsState(initial = startup.display.palette)
    val fontKey by repo.font.collectAsState(initial = startup.display.font)
    val backgroundAnimated by repo.backgroundAnimationEnabled.collectAsState(
        initial = startup.backgroundAnimationEnabled
    )
    val backgroundEnvironment = rememberBackgroundEnvironment(active)
    val splashEnabled by repo.splashEnabled.collectAsState(initial = startup.splashEnabled)
    val splashStyle by repo.splashStyle.collectAsState(initial = startup.splashStyle)
    var splashDone by remember { mutableStateOf(!splashEnabled) }
    val appBackgroundAnimated = backgroundAnimated && (splashDone || !splashEnabled)

    val dynamicColorOn by repo.dynamicColorEnabled.collectAsState(initial = startup.dynamicColor)

    val languageKey by repo.appLanguage.collectAsState(initial = startup.appLanguage)
    LaunchedEffect(languageKey) { com.lucent.app.i18n.L.apply(languageKey) }

    val autoUpdateOn by repo.autoUpdateEnabled.collectAsState(initial = startup.autoUpdateEnabled)
    LaunchedEffect(autoUpdateOn) {
        if (autoUpdateOn) {
            com.lucent.app.data.AutoUpdate.report(null)
            if (com.lucent.app.data.AutoUpdate.check(com.lucent.app.LucentBuild.VERSION) != null) {
                com.lucent.app.data.AutoUpdate.downloadOffered()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!autoUpdateOn && com.lucent.app.data.AutoUpdate.pendingVersion != null) {
            if (com.lucent.app.data.AutoUpdate.check(com.lucent.app.LucentBuild.VERSION) != null) {
                com.lucent.app.data.AutoUpdate.downloadOffered()
            }
        }
    }

    val appLockOn by repo.appLockEnabled.collectAsState(initial = startup.appLockEnabled)
    LaunchedEffect(appLockOn) { com.lucent.app.ui.AppLockController.enabled = appLockOn }

    LaunchedEffect(Unit) {
        val shieldWanted = try { repo.crashShieldEnabledOnce() } catch (t: Throwable) { false }
        if (shieldWanted) com.lucent.app.data.CrashShield.install(context)
    }

    val themeChoice = LucentThemeMode.fromKey(themeMode)
    val isDark = themeChoice.isDark(systemDark)
    val colors = if (isDark) darkColorScheme() else lightColorScheme()
    val onGradient = if (isDark) Color.White else Color(0xFF20202B)
    val onGradientMuted = onGradient.copy(alpha = 0.65f)
    val backdropColor = themeChoice.backdrop(systemDark)
    val paletteColors = if (paletteName == com.lucent.app.ui.PALETTE_RANDOM) {
        com.lucent.app.ui.rememberRandomPaletteColors(
            animated = backgroundAnimated,
            environment = backgroundEnvironment
        )
    } else if (paletteName == PALETTE_CYCLE) {
        rememberCyclingPaletteColors(
            LucentPalette.pickerEntries.map { it.colors },
            animated = backgroundAnimated,
            environment = backgroundEnvironment
        )
    } else {
        LucentPalette.entries.firstOrNull { it.name == paletteName }?.colors
            ?: LucentPalette.SUNSET.colors
    }

    MaterialTheme(colorScheme = colors, typography = lucentTypography(fontKey)) {
        CompositionLocalProvider(
            LocalOnGradient provides onGradient,
            LocalOnGradientMuted provides onGradientMuted,
            LocalBackgroundEnvironment provides backgroundEnvironment
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (com.lucent.app.ui.AppLockController.locked) {
                    LockScreen(
                        paletteColors = paletteColors,
                        backdropColor = backdropColor,
                        backgroundAnimated = appBackgroundAnimated
                    )
                } else {
                    DesktopShell(
                        repo = repo,
                        paletteColors = paletteColors,
                        backdropColor = backdropColor,
                        backgroundAnimated = appBackgroundAnimated
                    )
                }

                ToastOverlay()

                com.lucent.app.ui.AutoUpdateDialog(repo = repo)

                if (splashEnabled && !splashDone) {
                    LucentSplash(
                        paletteColors = paletteColors,
                        backdropColor = backdropColor,
                        onFinished = { splashDone = true },
                        backgroundAnimated = backgroundAnimated,
                        style = com.lucent.app.data.SplashStyle.fromKey(splashStyle)
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopShell(
    repo: SettingsRepository,
    paletteColors: List<Color>,
    backdropColor: Color,
    backgroundAnimated: Boolean
) {
    var current by remember { mutableStateOf(Screen.Tasks) }
    LaunchedEffect(AppNavigation.requestedScreen) {
        AppNavigation.consumeScreen()?.let { current = it }
    }
    LaunchedEffect(current) { LastScreen.remember(current) }

    BackHandler(
        enabled = current == Screen.Settings &&
            AppNavigation.settingsRoute == SettingsRoute.Root
    ) {
        AppNavigation.requestScreen(LastScreen.home)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FluidGlassBackground(
            palette = paletteColors,
            backdropColor = backdropColor,
            animated = backgroundAnimated,
            modifier = Modifier.fillMaxSize()
        )
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(current = current, onSelect = { current = it })
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (current) {
                    Screen.Assistant -> AssistantScreen()
                    Screen.Tasks -> TasksScreen()
                    Screen.Notes -> NotesScreen()
                    Screen.Insights -> InsightsScreen()
                    Screen.Search -> SearchScreen(
                        onOpenNote = { note -> AppNavigation.openNote(note.id, from = Screen.Search) },
                        onOpenTask = { task -> AppNavigation.openTask(task.id, from = Screen.Search) },
                        onBack = { AppNavigation.requestScreen(Screen.Tasks) }
                    )
                    Screen.Settings -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun ToastOverlay() {
    val entry by LucentToast.messages.collectAsState()
    val onGradient = LocalOnGradient.current
    Box(modifier = Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.BottomCenter) {
        entry?.let { e ->
            LaunchedEffect(e.id) {
                delay(if (e.longDuration) LucentToast.LONG_MS else LucentToast.SHORT_MS)
                LucentToast.clear(e)
            }
            Box(modifier = Modifier.frostedGlass().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(e.message, color = onGradient, fontSize = 14.sp)
            }
        }
    }
}
