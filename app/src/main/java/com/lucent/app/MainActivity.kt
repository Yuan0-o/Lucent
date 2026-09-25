package com.lucent.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.lucent.app.ui.LastScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lucent.app.AppScope
import com.lucent.app.data.AndroidUpdateInstaller
import com.lucent.app.data.AttachmentMigration
import com.lucent.app.data.AutoUpdate
import com.lucent.app.data.PrivilegedShell
import com.lucent.app.data.ShizukuShell
import com.lucent.app.data.ShizukuWatcher
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.ShareIntegration
import com.lucent.app.data.StartupLog
import com.lucent.app.data.TrashCleanup
import com.lucent.app.reminders.Notifications
import com.lucent.app.reminders.ReminderScheduler
import com.lucent.app.ui.AppReady
import com.lucent.app.ui.AppLockController
import com.lucent.app.ui.AssistantConfirmationDialog
import com.lucent.app.ui.AssistantController
import com.lucent.app.ui.AutoUpdateDialog
import com.lucent.app.ui.FluidGlassBackground
import com.lucent.app.ui.HomeDrawerSheet
import com.lucent.app.ui.HomeMode
import com.lucent.app.ui.HomeModeSwitcher
import com.lucent.app.ui.LocalBackgroundEnvironment
import com.lucent.app.ui.rememberBackgroundEnvironment
import com.lucent.app.ui.LocalHazeState
import com.lucent.app.ui.LocalBottomBarInset
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LockScreen
import com.lucent.app.ui.LucentSplash
import com.lucent.app.ui.lucentGlassRim
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.lucentTypography
import com.lucent.app.ui.LucentPalette
import com.lucent.app.ui.PALETTE_CYCLE
import com.lucent.app.ui.rememberCyclingPaletteColors
import com.lucent.app.ui.rememberNotificationPermissionRequester
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.ShareIntake
import com.lucent.app.ui.ShareIntakeDialog
import com.lucent.app.ui.ShizukuNoticeDialog
import com.lucent.app.ui.WidgetTaskConfirmDialog
import com.lucent.app.ui.UnsavedChangesGuard
import com.lucent.app.widget.WidgetActions
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

enum class Screen {
    Tasks, Notes, Notebooks, Assistant, Settings;

    val label: String
        get() = when (this) {
            Tasks -> com.lucent.app.i18n.S.tabTasks
            Notes -> com.lucent.app.i18n.S.tabNotes
            Notebooks -> com.lucent.app.i18n.S.screenNotebooks
            Assistant -> com.lucent.app.i18n.S.tabAssistant
            Settings -> com.lucent.app.i18n.S.tabSettings
        }
}

class MainActivity : FragmentActivity() {

    private val runningVersion: String by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
            ?: com.lucent.app.LucentBuild.VERSION
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        PrivilegedShell.install(ShizukuShell)
        val updateInstaller = AndroidUpdateInstaller(applicationContext)
        AutoUpdate.installer = updateInstaller
        val settingsRepo = SettingsRepository(applicationContext)
        val crashShieldWanted = try {
            runBlocking { settingsRepo.crashShieldEnabledOnce() }
        } catch (t: Throwable) {
            StartupLog.event(applicationContext, "crash shield: preference read failed at startup (${t::class.simpleName}) - shield not installed this launch")
            false
        }
        if (crashShieldWanted) com.lucent.app.data.CrashShield.install(applicationContext)
        val startup = try {
            runBlocking { settingsRepo.startupPrefsOnce() }
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
        val display = startup.display
        val initialThemeMode = display.themeMode
        val initialPalette = display.palette
        val initialFont = display.font

        com.lucent.app.i18n.L.apply(startup.appLanguage)

        val lockEnabled = startup.appLockEnabled
        AppLockController.markProcessStarted(lockEnabled)

        StartupLog.setEnabled(startup.startupLoggingEnabled)
        AppScope.appContext = applicationContext

        if (startup.privilegedEnabled) ShizukuWatcher.ensureStarted(applicationContext)

        AutoUpdate.restorePending(startup.pendingUpdateVersion)
        AutoUpdate.onPendingChange = { version ->
            AppScope.io.launch { settingsRepo.setPendingUpdateVersion(version.orEmpty()) }
        }
        AppScope.io.launch { updateInstaller.purgeStale(runningVersion, AutoUpdate.pendingVersion) }

        val integrationEnabled = startup.systemIntegrationEnabled
        AppScope.io.launch { ShareIntegration.setEnabled(applicationContext, integrationEnabled) }

        StartupLog.event(applicationContext, "App starting (lock=${if (lockEnabled) "on" else "off"})")

        handleShareIntent(intent)
        handleWidgetIntent(intent)


        AppScope.io.launch {
            AttachmentMigration.runIfNeeded(applicationContext)
            AttachmentMigration.encryptExistingAttachments(applicationContext)
        }

        AppScope.io.launch { TrashCleanup.purgeExpired(applicationContext) }

        AppScope.io.launch { com.lucent.app.data.AttachmentAccess.clearPreviewCache(applicationContext) }

        AppScope.io.launch {
            runCatching { com.lucent.app.data.AutoBackupRunner.ensureStarted(applicationContext) }
        }

        AppScope.io.launch { Notifications.ensureChannel(applicationContext) }
        AppScope.io.launch { ReminderScheduler.rescheduleAll(applicationContext) }

        AppScope.io.launch {
            try {
                val db = com.lucent.app.data.AppDatabase.getInstance(applicationContext)
                com.lucent.app.data.DataCache.warm(db)
                AssistantController.ensureMessagesLoaded(applicationContext)
                com.lucent.app.ui.AppReady.databaseReady = true
            } catch (t: Throwable) {
                android.util.Log.e("LucentStartup", "database init failed at startup", t)
                StartupLog.event(
                    applicationContext,
                    "db: startup init failed (${t::class.simpleName}: ${t.message})"
                )
            }
        }
        StartupLog.event(applicationContext, "Startup tasks dispatched; composing UI")

        setContent {
            val themeMode by settingsRepo.themeMode.collectAsState(initial = initialThemeMode)
            val paletteName by settingsRepo.palette.collectAsState(initial = initialPalette)
            val fontKey by settingsRepo.font.collectAsState(initial = initialFont)
            val backgroundAnimated by settingsRepo.backgroundAnimationEnabled.collectAsState(
                initial = startup.backgroundAnimationEnabled
            )
            val backgroundEnvironment = rememberBackgroundEnvironment()
            val splashEnabled by settingsRepo.splashEnabled.collectAsState(
                initial = com.lucent.app.data.SettingsCache.splashEnabled
            )
            val splashStyle by settingsRepo.splashStyle.collectAsState(
                initial = com.lucent.app.data.SettingsCache.splashStyle
            )
            var splashDone by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(splashEnabled) {
                if (!splashEnabled) {
                    delay(2500)
                    splashDone = true
                }
            }
            val appBackgroundAnimated = backgroundAnimated && (splashDone || !splashEnabled)

            val dynamicColorOn by settingsRepo.dynamicColorEnabled.collectAsState(
                initial = startup.dynamicColor
            )

            val languageKey by settingsRepo.appLanguage.collectAsState(initial = startup.appLanguage)
            LaunchedEffect(languageKey) { com.lucent.app.i18n.L.apply(languageKey) }

            val autoUpdateOn by settingsRepo.autoUpdateEnabled.collectAsState(
                initial = com.lucent.app.data.SettingsCache.autoUpdateEnabled
            )
            LaunchedEffect(autoUpdateOn) {
                if (autoUpdateOn) {
                    com.lucent.app.data.AutoUpdate.report(null)
                    if (com.lucent.app.data.AutoUpdate.check(runningVersion) != null) {
                        com.lucent.app.data.AutoUpdate.downloadOffered()
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (!autoUpdateOn && com.lucent.app.data.AutoUpdate.pendingVersion != null) {
                    if (com.lucent.app.data.AutoUpdate.check(runningVersion) != null) {
                        com.lucent.app.data.AutoUpdate.downloadOffered()
                    }
                }
            }

            val systemDark = isSystemInDarkTheme()
            val themeChoice = com.lucent.app.ui.LucentThemeMode.fromKey(themeMode)
            val isDarkTheme = themeChoice.isDark(systemDark)

            val dynamicActive = dynamicColorOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val colors: ColorScheme
            val onGradient: Color
            val backdropColor: Color
            val paletteColors: List<Color>
            if (dynamicActive) {
                val scheme = if (systemDark) {
                    dynamicDarkColorScheme(this@MainActivity)
                } else {
                    dynamicLightColorScheme(this@MainActivity)
                }
                colors = scheme
                onGradient = scheme.onBackground
                backdropColor = scheme.background
                paletteColors = com.lucent.app.ui.dynamicBlobPalette(scheme)
            } else {
                colors = if (isDarkTheme) darkColorScheme() else lightColorScheme()
                onGradient = if (isDarkTheme) Color.White else Color(0xFF20202B)
                backdropColor = themeChoice.backdrop(systemDark)
                paletteColors = if (paletteName == com.lucent.app.ui.PALETTE_RANDOM) {
                    com.lucent.app.ui.rememberRandomPaletteColors(
                        animated = appBackgroundAnimated,
                        environment = backgroundEnvironment
                    )
                } else if (paletteName == PALETTE_CYCLE) {
                    rememberCyclingPaletteColors(
                        LucentPalette.pickerEntries.map { it.colors },
                        animated = appBackgroundAnimated,
                        environment = backgroundEnvironment
                    )
                } else {
                    LucentPalette.entries.firstOrNull { it.name == paletteName }?.colors
                        ?: LucentPalette.SUNSET.colors
                }
            }
            val onGradientMuted = onGradient.copy(alpha = 0.65f)

            MaterialTheme(colorScheme = colors, typography = lucentTypography(fontKey)) {
                CompositionLocalProvider(
                    LocalOnGradient provides onGradient,
                    LocalOnGradientMuted provides onGradientMuted,
                    LocalBackgroundEnvironment provides backgroundEnvironment
                ) {
                    val appLockOn by settingsRepo.appLockEnabled.collectAsState(initial = lockEnabled)
                    LaunchedEffect(appLockOn) { AppLockController.enabled = appLockOn }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (AppReady.databaseReady || splashDone) {
                            if (AppLockController.locked) {
                                LockScreen(
                                    paletteColors = paletteColors,
                                    backdropColor = backdropColor,
                                    backgroundAnimated = appBackgroundAnimated
                                )
                            } else {
                                LucentApp(
                                    paletteColors = paletteColors,
                                    backdropColor = backdropColor,
                                    backgroundAnimated = appBackgroundAnimated
                                )
                            }
                        } else if (!splashEnabled) {
                            FluidGlassBackground(
                                palette = paletteColors,
                                backdropColor = backdropColor,
                                animated = appBackgroundAnimated,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (splashEnabled && !splashDone) {
                            LucentSplash(
                                paletteColors = paletteColors,
                                backdropColor = backdropColor,
                                onFinished = { splashDone = true },
                                backgroundAnimated = appBackgroundAnimated,
                                style = com.lucent.app.data.SplashStyle.fromKey(splashStyle)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLockController.onStart()
    }

    override fun onStop() {
        super.onStop()
        if (AssistantController.sending && AssistantController.localTurnInFlight) {
            val repo = SettingsRepository(applicationContext)
            AppScope.io.launch {
                val keepGoing = try {
                    repo.localBackgroundReplyEnabledOnce()
                } catch (t: Throwable) {
                    false
                }
                withContext(Dispatchers.Main) { AssistantController.onAppBackgrounded(keepGoing) }
            }
        }
        AppLockController.onStop()
        UnsavedChangesGuard.autoDraft()
        try { com.lucent.app.widget.WidgetUpdater.refreshContent(applicationContext) } catch (t: Throwable) { }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        handleWidgetIntent(intent)
    }

    override fun onDestroy() {
        if (isFinishing) com.lucent.app.local.LocalLlm.shutdown()
        super.onDestroy()
    }

    private fun handleShareIntent(intent: Intent?) {
        val shared = ShareIntegration.parse(intent) ?: return
        val enabled = try {
            runBlocking { SettingsRepository(applicationContext).systemIntegrationEnabledOnce() }
        } catch (t: Throwable) {
            false
        }
        if (enabled) ShareIntake.offer(shared)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        when (intent?.getStringExtra(WidgetActions.EXTRA_ACTION)) {
            WidgetActions.NEW_NOTE -> AppNavigation.requestComposeNote()
            WidgetActions.NEW_TASK -> AppNavigation.requestComposeTask()
            WidgetActions.ASK -> AppNavigation.requestScreen(Screen.Assistant)
            WidgetActions.OPEN_TASKS -> AppNavigation.requestScreen(Screen.Tasks)
            WidgetActions.OPEN_TASK_ITEM -> {
                val id = intent.getLongExtra(WidgetActions.EXTRA_ID, -1L)
                if (id > 0) AppNavigation.openTask(id) else AppNavigation.requestScreen(Screen.Tasks)
            }
            WidgetActions.TOGGLE_TASK_ITEM -> {
                val id = intent.getLongExtra(WidgetActions.EXTRA_ID, -1L)
                if (id > 0) com.lucent.app.ui.WidgetTaskConfirm.offer(id)
                AppNavigation.requestScreen(Screen.Tasks)
            }
            WidgetActions.OPEN_NOTE_ITEM -> {
                val id = intent.getLongExtra(WidgetActions.EXTRA_ID, -1L)
                if (id > 0) AppNavigation.openNote(id) else AppNavigation.requestScreen(Screen.Notes)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun LucentApp(paletteColors: List<Color>, backdropColor: Color, backgroundAnimated: Boolean = true) {
    var currentScreen by rememberSaveable { mutableStateOf(LastScreen.current) }
    val tabs = HomeTab.entries
    val currentTab = HomeTab.of(currentScreen)
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(currentTab),
        pageCount = { tabs.size }
    )
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val tabScope = rememberCoroutineScope()
    val lastScreenContext = LocalContext.current
    val lastScreenRepo = remember(lastScreenContext) {
        com.lucent.app.data.SettingsRepository(lastScreenContext.applicationContext)
    }
    LaunchedEffect(currentScreen) {
        LastScreen.remember(currentScreen)
        lastScreenRepo.setLastScreen(LastScreen.persistedName())
        StartupLog.event(lastScreenContext, "nav: showing ${currentScreen.name.lowercase()}")
    }
    LaunchedEffect(currentTab) {
        if (currentTab != HomeTab.Home && drawerState.isOpen) drawerState.close()
    }
    val hazeState = rememberHazeState()
    val onGradient = LocalOnGradient.current
    val context = LocalContext.current
    val updateRepo = remember {
        com.lucent.app.data.SettingsRepository(context.applicationContext)
    }
    val requestNotificationPermission = rememberNotificationPermissionRequester()
    LaunchedEffect(AutoUpdate.phase) {
        if (AutoUpdate.phase == AutoUpdate.Phase.DOWNLOADING) requestNotificationPermission()
    }

    val notesScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val tasksScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pinnedScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val headerCollapsible = currentScreen == Screen.Notes || currentScreen == Screen.Tasks
    val scrollBehavior = when (currentScreen) {
        Screen.Notes -> notesScrollBehavior
        Screen.Tasks -> tasksScrollBehavior
        else -> pinnedScrollBehavior
    }

    var backArmed by remember { mutableStateOf(false) }
    LaunchedEffect(backArmed) {
        if (backArmed) {
            delay(2000)
            backArmed = false
        }
    }

    fun finishActivity() {
        var c: android.content.Context = context
        while (c is android.content.ContextWrapper && c !is Activity) c = c.baseContext
        (c as? Activity)?.finish()
    }

    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun runOrConfirm(action: () -> Unit) {
        if (UnsavedChangesGuard.dirty) pendingNavigation = action else action()
    }

    var confirmExitWhileReplying by remember { mutableStateOf(false) }

    pendingNavigation?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingNavigation = null },
            title = { Text(com.lucent.app.i18n.S.unsavedChangesTitle) },
            text = { Text(com.lucent.app.i18n.S.unsavedChangesBody) },
            confirmButton = {
                TextButton(onClick = {
                    UnsavedChangesGuard.save()
                    pendingNavigation = null
                    action()
                }) { Text(com.lucent.app.i18n.S.actionSave) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        UnsavedChangesGuard.discard()
                        pendingNavigation = null
                        action()
                    }) { Text(com.lucent.app.i18n.S.actionDiscard) }
                    TextButton(onClick = { pendingNavigation = null }) { Text(com.lucent.app.i18n.S.actionCancel) }
                }
            }
        )
    }

    LaunchedEffect(AppNavigation.requestedScreen) {
        AppNavigation.consumeScreen()?.let { target ->
            if (target != currentScreen) currentScreen = target
        }
    }

    BackHandler(enabled = !AppNavigation.innerBackActive) {
        when {
            currentScreen == Screen.Settings && AppNavigation.settingsRoute == SettingsRoute.Root ->
                runOrConfirm {
                    AppNavigation.resetSettingsRoute()
                    currentScreen = LastScreen.home
                }
            currentTab == HomeTab.Notebooks || currentTab == HomeTab.Assistant ->
                currentScreen = LastScreen.homeMode.screen
            !backArmed -> {
                backArmed = true
                LucentToast.show(context, com.lucent.app.i18n.S.pressBackAgainToExit)
            }
            AssistantController.sending && AssistantController.localTurnInFlight ->
                confirmExitWhileReplying = true
            else -> runOrConfirm { finishActivity() }
        }
    }

    if (confirmExitWhileReplying) {
        AlertDialog(
            onDismissRequest = { confirmExitWhileReplying = false },
            title = { Text(com.lucent.app.i18n.S.lmExitWhileReplyingTitle) },
            text = { Text(com.lucent.app.i18n.S.lmExitWhileReplyingBody) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExitWhileReplying = false
                    AssistantController.stopAllGeneration()
                    runOrConfirm { finishActivity() }
                }) { Text(com.lucent.app.i18n.S.lmExitAnyway) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExitWhileReplying = false }) {
                    Text(com.lucent.app.i18n.S.lmKeepWaiting)
                }
            }
        )
    }

    AssistantConfirmationDialog()

    fun selectHomeMode(mode: HomeMode) {
        if (HomeMode.of(currentScreen) != mode) {
            currentScreen = mode.screen
            StartupLog.event(context, "home: switched to ${mode.name.lowercase()}")
        }
    }

    fun closeDrawer() {
        tabScope.launch { drawerState.close() }
    }

    if (drawerState.isOpen) {
        BackHandler { closeDrawer() }
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier = Modifier.fillMaxSize()) {
            FluidGlassBackground(
                palette = paletteColors,
                backdropColor = backdropColor,
                animated = backgroundAnimated,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .clearAndSetSemantics { }
            )
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                scrimColor = Color.Black.copy(alpha = 0.28f),
                drawerContent = {
                    HomeDrawerSheet(
                        mode = HomeMode.of(currentScreen) ?: LastScreen.homeMode,
                        onSelectMode = { mode -> selectHomeMode(mode) },
                        onOpenPanel = { panel ->
                            closeDrawer()
                            runOrConfirm {
                                AppNavigation.requestPanel(panel)
                                StartupLog.event(context, "drawer: requested ${panel.logKey}")
                            }
                        }
                    )
                }
            ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = {
                            val homeMode = HomeMode.of(currentScreen)
                            if (homeMode != null) {
                                HomeModeSwitcher(mode = homeMode, onSelect = { mode -> selectHomeMode(mode) })
                            } else if (currentScreen == Screen.Settings) {
                                com.lucent.app.ui.SettingsBreadcrumb(
                                    route = AppNavigation.settingsRoute,
                                    onNavigate = { SettingsNav.go(it) }
                                )
                            } else {
                                Text(currentScreen.label, color = onGradient, fontSize = 30.sp)
                            }
                        },
                        navigationIcon = {
                            if (currentTab == HomeTab.Home) {
                                IconButton(onClick = {
                                    com.lucent.app.ui.Haptics.tick(context)
                                    tabScope.launch { drawerState.open() }
                                    StartupLog.event(context, "drawer: opened")
                                }) {
                                    Icon(Icons.Default.Menu, contentDescription = com.lucent.app.i18n.S.drawerOpen, tint = onGradient)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        scrollBehavior = scrollBehavior,
                        modifier = Modifier.hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.ultraThin(
                                if (onGradient.luminance() > 0.5f) com.lucent.app.ui.LucentGlass.HazeContainerDark
                                else com.lucent.app.ui.LucentGlass.HazeContainerLight
                            )
                        )
                    )
                },
                bottomBar = {
                    val capsuleShape = RoundedCornerShape(percent = 50)
                    val glassDark = onGradient.luminance() > 0.5f
                    val capsuleFill = Color.White.copy(
                        alpha = if (glassDark) com.lucent.app.ui.LucentGlass.BLURRED_FILL_DARK
                        else com.lucent.app.ui.LucentGlass.BLURRED_FILL_LIGHT
                    )
                    val capsuleRim = lucentGlassRim(strong = true)
                    val capsuleDivider = if (glassDark) Color.White.copy(alpha = 0.08f) else onGradient.copy(alpha = 0.10f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(top = 12.dp, bottom = 26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.875f)
                                .height(76.dp)
                                .shadow(
                                    elevation = if (glassDark) 18.dp else 12.dp,
                                    shape = capsuleShape,
                                    clip = false,
                                    ambientColor = if (glassDark) Color.Black.copy(alpha = 0.35f) else Color(0xFF2A2A3A).copy(alpha = 0.26f),
                                    spotColor = if (glassDark) Color.Black.copy(alpha = 0.45f) else Color(0xFF2A2A3A).copy(alpha = 0.34f)
                                )
                                .clip(capsuleShape)
                                .hazeEffect(
                                    state = hazeState,
                                    style = HazeMaterials.ultraThin(
                                        if (glassDark) com.lucent.app.ui.LucentGlass.HazeContainerDark
                                        else com.lucent.app.ui.LucentGlass.HazeContainerLight
                                    )
                                )
                                .background(capsuleFill)
                                .border(
                                    1.5.dp,
                                    capsuleRim,
                                    capsuleShape
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                tabs.forEachIndexed { index, tab ->
                                    if (index > 0) {
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(24.dp)
                                                .background(capsuleDivider)
                                        )
                                    }
                                    CapsuleNavItem(
                                        tab = tab,
                                        selected = currentTab == tab,
                                        onClick = {
                                            currentScreen = tab.screen(LastScreen.homeMode)
                                            tabScope.launch { pagerState.scrollToPage(tabs.indexOf(tab)) }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                val topPad = padding.calculateTopPadding()
                val bottomInset = padding.calculateBottomPadding()
                val contentModifier = if (headerCollapsible) {
                    Modifier.padding(top = topPad).fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier.padding(top = topPad).fillMaxSize()
                }
                CompositionLocalProvider(LocalBottomBarInset provides bottomInset) {
                    KeepAliveTabs(active = currentScreen, pagerState = pagerState, modifier = contentModifier)
                }
            }
            }

            ShareIntakeDialog()
            WidgetTaskConfirmDialog()
            AutoUpdateDialog(
                repo = updateRepo,
                onOpenUrl = { url ->
                    runCatching {
                        var owner: android.content.Context = context
                        while (owner is android.content.ContextWrapper && owner !is Activity) owner = owner.baseContext
                        (owner as? Activity)?.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)
                            )
                        )
                    }
                }
            )
            ShizukuNoticeDialog()
        }
    }
}
