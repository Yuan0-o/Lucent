package com.lucent.app.ui

import com.lucent.app.data.createSettingsRepository

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.lucent.app.platform.LocalPlatformContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppNavigation
import com.lucent.app.BackClaim
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.harness.terminal.PtyKeys
import com.lucent.app.harness.terminal.PtySession
import com.lucent.app.harness.terminal.TerminalSessionListener
import com.lucent.app.harness.terminal.TerminalSessions
import com.lucent.app.harness.terminal.TerminalTab
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { createSettingsRepository(context) }

    BackClaim(active = true)
    BackHandler { onBack() }

    var tabs by remember { mutableStateOf(TerminalSessions.manager.snapshot()) }
    var currentTab by remember { mutableStateOf(TerminalSessions.manager.current()) }
    var tick by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val listener = object : TerminalSessionListener {
            override fun onTabsChanged() {
                tabs = TerminalSessions.manager.snapshot()
                currentTab = TerminalSessions.manager.current()
            }
            override fun onSessionOutput(tab: TerminalTab<PtySession>, chunk: String) {
                if (currentTab?.id == tab.id) {
                    tick++
                }
            }
            override fun onSessionExit(tab: TerminalTab<PtySession>, exitCode: Int) {
            }
        }
        TerminalSessions.manager.addListener(listener)
        onDispose { TerminalSessions.manager.removeListener(listener) }
    }

    var fontSize by remember { mutableFloatStateOf(SettingsCache.terminalFontSize ?: 14f) }
    var keyBarVisible by remember { mutableStateOf(SettingsCache.terminalKeyBarVisible) }
    var input by remember { mutableStateOf("") }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).background(Color(0xFF1E1E1E)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.lucent.app.i18n.S.actionBack, tint = Color.White)
            }
            Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                tabs.forEach { tab ->
                    val selected = currentTab?.id == tab.id
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFF333333) else Color.Transparent)
                            .clickable { TerminalSessions.manager.select(tab.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tab.label ?: "Terminal ${tab.number}", color = Color.White, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Close,
                                contentDescription = com.lucent.app.i18n.S.actionClose,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp).clickable { TerminalSessions.manager.closeSession(tab.id) }
                            )
                        }
                    }
                }
                IconButton(onClick = {
                    try {
                        TerminalSessions.manager.createSession()
                    } catch (_: Throwable) {
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = com.lucent.app.i18n.S.actionAdd, tint = Color.White)
                }
            }
            IconButton(onClick = {
                val newSize = (fontSize - 1f).coerceAtLeast(8f)
                fontSize = newSize
                scope.launch { repo.setTerminalFontSize(newSize) }
            }) {
                Text("A-", color = Color.White)
            }
            IconButton(onClick = {
                val newSize = (fontSize + 1f).coerceAtMost(32f)
                fontSize = newSize
                scope.launch { repo.setTerminalFontSize(newSize) }
            }) {
                Text("A+", color = Color.White)
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val density = LocalDensity.current
            val widthDp = maxWidth
            val heightDp = maxHeight
            LaunchedEffect(widthDp, heightDp, fontSize) {
                val pxWidth = with(density) { widthDp.toPx() }
                val pxHeight = with(density) { heightDp.toPx() }
                val charWidth = with(density) { (fontSize * 0.6f).sp.toPx() }
                val charHeight = with(density) { (fontSize * 1.2f).sp.toPx() }
                if (charWidth > 0 && charHeight > 0) {
                    val cols = (pxWidth / charWidth).toInt().coerceAtLeast(10)
                    val rows = (pxHeight / charHeight).toInt().coerceAtLeast(10)
                    TerminalSessions.manager.resizeCurrent(cols, rows)
                }
            }
            if (!TerminalSessions.manager.isAvailable()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        com.lucent.app.i18n.S.terminalSetupGuidance,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            } else {
                val transcript = remember(currentTab, tick) {
                    currentTab?.value?.transcript()?.let { stripAnsi(it) } ?: ""
                }
                val scrollState = rememberScrollState()
                LaunchedEffect(transcript) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
                    Text(
                        text = transcript,
                        color = Color.Green,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp
                    )
                }
            }
        }

        if (keyBarVisible) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0xFF222222)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val sendKey = { key: String -> TerminalSessions.manager.writeToCurrent(key) }
                TerminalKey("ESC") { sendKey(PtyKeys.ESCAPE) }
                TerminalKey("TAB") { sendKey(PtyKeys.TAB) }
                TerminalKey("CTRL", active = ctrlActive) { ctrlActive = !ctrlActive }
                TerminalKey("ALT", active = altActive) { altActive = !altActive }
                TerminalKey("↑") { sendKey(PtyKeys.ARROW_UP) }
                TerminalKey("↓") { sendKey(PtyKeys.ARROW_DOWN) }
                TerminalKey("←") { sendKey(PtyKeys.ARROW_LEFT) }
                TerminalKey("→") { sendKey(PtyKeys.ARROW_RIGHT) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val newState = !keyBarVisible
                keyBarVisible = newState
                scope.launch { repo.setTerminalKeyBarVisible(newState) }
            }) {
                Icon(Icons.Default.Keyboard, contentDescription = com.lucent.app.i18n.S.terminalToggleKeyBar, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = input,
                onValueChange = { newValue ->
                    if (newValue.isNotEmpty() && ctrlActive) {
                        TerminalSessions.manager.writeToCurrent(PtyKeys.ctrl(newValue.last()))
                        ctrlActive = false
                        input = ""
                    } else if (newValue.isNotEmpty() && altActive) {
                        TerminalSessions.manager.writeToCurrent(PtyKeys.alt(newValue.last().toString()))
                        altActive = false
                        input = ""
                    } else {
                        input = com.lucent.app.collapseExcessBlankLines(newValue)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF333333), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .onPreviewKeyEvent { event ->
                        if (HarnessRuntime.android) return@onPreviewKeyEvent false
                        val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
                        when {
                            !isEnter || event.type != KeyEventType.KeyDown -> false
                            event.isShiftPressed -> false
                            else -> {
                                if (TerminalSessions.manager.current() == null) {
                                    try {
                                        TerminalSessions.manager.createSession()
                                    } catch (_: Throwable) { }
                                }
                                TerminalSessions.manager.writeToCurrent(input + PtyKeys.ENTER)
                                input = ""
                                true
                            }
                        }
                    },
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (TerminalSessions.manager.current() == null) {
                        try {
                            TerminalSessions.manager.createSession()
                        } catch (_: Throwable) { }
                    }
                    TerminalSessions.manager.writeToCurrent(input + PtyKeys.ENTER)
                    input = ""
                })
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = {
                if (TerminalSessions.manager.current() == null) {
                    try {
                        TerminalSessions.manager.createSession()
                    } catch (_: Throwable) { }
                }
                TerminalSessions.manager.writeToCurrent(input + PtyKeys.ENTER)
                input = ""
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = com.lucent.app.i18n.S.a11ySend, tint = Color.White)
            }
        }
    }
}

@Composable
fun TerminalKey(label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) Color(0xFF555555) else Color(0xFF333333))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

fun stripAnsi(text: String): String {
    return text.replace(Regex("\u001B\\[[0-9;?]*[a-zA-Z]"), "")
}
