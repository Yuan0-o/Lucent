package com.lucent.app

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.ui.AndroidRobotIcon
import com.lucent.app.ui.AssistantScreen
import com.lucent.app.ui.HomeMode
import com.lucent.app.ui.LastScreen
import com.lucent.app.ui.LocalHazeState
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.NotebooksScreen
import com.lucent.app.ui.NotesScreen
import com.lucent.app.ui.SettingsScreen
import com.lucent.app.ui.TasksScreen
import dev.chrisbanes.haze.rememberHazeState

enum class HomeTab {
    Home, Notebooks, Assistant, Settings;

    val label: String
        get() = when (this) {
            Home -> com.lucent.app.i18n.S.tabHome
            Notebooks -> com.lucent.app.i18n.S.screenNotebooks
            Assistant -> com.lucent.app.i18n.S.tabAssistant
            Settings -> com.lucent.app.i18n.S.tabSettings
        }

    fun screen(homeMode: HomeMode): Screen = when (this) {
        Home -> homeMode.screen
        Notebooks -> Screen.Notebooks
        Assistant -> Screen.Assistant
        Settings -> Screen.Settings
    }

    companion object {
        fun of(screen: Screen): HomeTab = when (screen) {
            Screen.Tasks, Screen.Notes -> Home
            Screen.Notebooks -> Notebooks
            Screen.Assistant -> Assistant
            Screen.Settings -> Settings
        }
    }
}

@Composable
internal fun CapsuleNavItem(
    tab: HomeTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val tint = if (selected) onGradient else onGradientMuted
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .then(if (selected) Modifier.background(onGradient.copy(alpha = 0.10f)) else Modifier)
            .clickable {
                com.lucent.app.ui.Haptics.tick(context)
                onClick()
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(iconFor(tab), contentDescription = tab.label, tint = tint)
        Text(tab.label, color = tint, fontSize = 11.sp, maxLines = 1)
    }
}

fun iconFor(tab: HomeTab): ImageVector = when (tab) {
    HomeTab.Home -> Icons.Default.Home
    HomeTab.Notebooks -> Icons.Default.Book
    HomeTab.Assistant -> AndroidRobotIcon
    HomeTab.Settings -> Icons.Default.Settings
}

private fun inertOwnerOf(real: OnBackPressedDispatcherOwner?): OnBackPressedDispatcherOwner =
    object : OnBackPressedDispatcherOwner {
        override val lifecycle get() = real!!.lifecycle
        override val onBackPressedDispatcher = OnBackPressedDispatcher()
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KeepAliveTabs(active: Screen, pagerState: PagerState, modifier: Modifier = Modifier) {
    val realBackOwner = LocalOnBackPressedDispatcherOwner.current
    val inertBackOwner = remember(realBackOwner) { inertOwnerOf(realBackOwner) }
    val sharedHaze = LocalHazeState.current
    val tabs = HomeTab.entries
    val activeTab = HomeTab.of(active)

    LaunchedEffect(activeTab) {
        val targetPage = tabs.indexOf(activeTab)
        if (pagerState.currentPage != targetPage) pagerState.scrollToPage(targetPage)
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != tabs.indexOf(activeTab)) {
            AppNavigation.requestScreen(tabs[pagerState.currentPage].screen(LastScreen.homeMode))
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = tabs.lastIndex,
        userScrollEnabled = !AppNavigation.innerBackActive,
        modifier = modifier
    ) { page ->
        val tab = tabs[page]
        val isActive = tab == activeTab
        key(tab) {
            val dummyHaze = rememberHazeState()
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalHazeState provides (if (isActive) sharedHaze else dummyHaze),
                    LocalOnBackPressedDispatcherOwner provides (if (isActive) realBackOwner!! else inertBackOwner)
                ) {
                    when (tab) {
                        HomeTab.Home -> HomePages(mode = HomeMode.of(active) ?: LastScreen.homeMode, active = isActive)
                        HomeTab.Notebooks -> NotebooksScreen(
                            onBack = { AppNavigation.requestScreen(LastScreen.homeMode.screen) },
                            onOpenNote = { note -> AppNavigation.openNote(note.id, from = Screen.Notebooks) },
                            onOpenTask = { task -> AppNavigation.openTask(task.id, from = Screen.Notebooks) },
                            showBack = false,
                            active = isActive
                        )
                        HomeTab.Assistant -> AssistantScreen(active = isActive)
                        HomeTab.Settings -> SettingsScreen(active = isActive)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePages(mode: HomeMode, active: Boolean) {
    val realBackOwner = LocalOnBackPressedDispatcherOwner.current
    val inertBackOwner = remember(realBackOwner) { inertOwnerOf(realBackOwner) }
    val sharedHaze = LocalHazeState.current
    val modes = HomeMode.entries
    val pagerState = rememberPagerState(initialPage = modes.indexOf(mode), pageCount = { modes.size })

    LaunchedEffect(mode) {
        val target = modes.indexOf(mode)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val entry = modes[page]
        val isActive = active && entry == mode
        key(entry) {
            val dummyHaze = rememberHazeState()
            CompositionLocalProvider(
                LocalHazeState provides (if (isActive) sharedHaze else dummyHaze),
                LocalOnBackPressedDispatcherOwner provides (if (isActive) realBackOwner!! else inertBackOwner)
            ) {
                when (entry) {
                    HomeMode.Tasks -> TasksScreen(active = isActive)
                    HomeMode.Notes -> NotesScreen(active = isActive)
                }
            }
        }
    }
}
