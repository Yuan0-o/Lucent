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
            else -> Home
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun KeepAliveTabs(pagerState: PagerState, modifier: Modifier = Modifier) {
    val realBackOwner = LocalOnBackPressedDispatcherOwner.current
    val inertBackOwner = remember(realBackOwner) { inertOwnerOf(realBackOwner) }
    val sharedHaze = LocalHazeState.current
    val context = LocalContext.current
    val swipeChain = remember { listOf(Screen.Tasks, Screen.Notes, Screen.Notebooks, Screen.Assistant, Screen.Settings) }
    val pendingPhotoCallback: androidx.compose.runtime.MutableState<((String?) -> Unit)?> = remember { androidx.compose.runtime.mutableStateOf<((String?) -> Unit)?>(null) }
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
             val id = com.lucent.app.data.AttachmentStore.importUri(context, uri)
             if (id != null) {
                 pendingPhotoCallback.value?.invoke("photo:$id")
                 com.lucent.app.data.StartupLog.event(context, "notebooks: imported cover photo $id")
             } else {
                 com.lucent.app.data.StartupLog.event(context, "notebooks: cover photo import failed")
                 pendingPhotoCallback.value?.invoke(null)
             }
        } else pendingPhotoCallback.value?.invoke(null)
        pendingPhotoCallback.value = null
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            AppNavigation.requestScreen(swipeChain[pagerState.currentPage])
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = swipeChain.lastIndex,
        userScrollEnabled = !AppNavigation.innerBackActive,
        modifier = modifier
    ) { page ->
        val screen = swipeChain[page]
        val isActive = pagerState.currentPage == page
        key(screen) {
            val dummyHaze = rememberHazeState()
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalHazeState provides (if (isActive) sharedHaze else dummyHaze),
                    LocalOnBackPressedDispatcherOwner provides (if (isActive) realBackOwner!! else inertBackOwner)
                ) {
                    when (screen) {
                        Screen.Tasks -> TasksScreen(active = isActive)
                        Screen.Notes -> NotesScreen(active = isActive)
                        Screen.Notebooks -> NotebooksScreen(
                            onBack = { AppNavigation.requestScreen(LastScreen.homeMode.screen) },
                            onOpenNote = { note -> AppNavigation.openNote(note.id, from = Screen.Notebooks) },
                            onOpenTask = { task -> AppNavigation.openTask(task.id, from = Screen.Notebooks) },
                            showBack = false,
                            onPickPhoto = { callback ->
                                pendingPhotoCallback.value = callback
                                photoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            active = isActive
                        )
                        Screen.Assistant -> AssistantScreen(active = isActive)
                        Screen.Settings -> SettingsScreen(active = isActive)
                        else -> {}
                    }
                }
            }
        }
    }
}
