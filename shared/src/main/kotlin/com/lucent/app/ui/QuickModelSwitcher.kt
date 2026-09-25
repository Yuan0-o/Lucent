package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lucent.app.data.ReasoningEffort
import com.lucent.app.data.ReasoningEfforts
import com.lucent.app.local.LocalLlm
import com.lucent.app.local.LocalModelStore
import com.lucent.app.network.ApiSpec
import com.lucent.app.network.LlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MENU_CLOSED = 0
private const val MENU_ROOT = 1
private const val MENU_MODELS = 2
private const val MENU_REASONING = 3

@Composable
fun QuickModelSwitcher(
    currentModel: String,
    recents: List<String>,
    baseUrl: String,
    spec: ApiSpec,
    apiKey: String,
    selectedModels: List<String>,
    hasProfile: Boolean,
    localModelEnabled: Boolean,
    tint: Color,
    mutedTint: Color,
    onPickCloudModel: (String) -> Unit,
    onSelectedModelsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 46.dp,
    reasoningProviderId: String = "",
    reasoningCurrent: String = ReasoningEffort.DEFAULT.key,
    onPickReasoning: (ReasoningEffort) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var menu by remember { mutableStateOf(MENU_CLOSED) }
    var fetched by remember { mutableStateOf<List<String>>(emptyList()) }
    var fetching by remember { mutableStateOf(false) }
    var fetchNote by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var autoFetchTried by remember { mutableStateOf(false) }

    val profileModels = remember(selectedModels) {
        selectedModels.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    val profileMenu = hasProfile && profileModels.isNotEmpty()
    val canFetch = baseUrl.isNotBlank() && apiKey.isNotBlank()

    val reasoningOptions = remember(reasoningProviderId, currentModel) {
        ReasoningEfforts.optionsFor(reasoningProviderId, currentModel)
    }
    val reasoningOffered = !localModelEnabled && reasoningOptions.size > 1
    val settledReasoning = remember(reasoningProviderId, currentModel, reasoningCurrent) {
        ReasoningEfforts.settled(reasoningProviderId, currentModel, reasoningCurrent)
    }

    var refresh by remember { mutableStateOf(0) }
    val slots = remember(refresh, menu, localModelEnabled) {
        if (localModelEnabled) runCatching { LocalModelStore.slots(context) }.getOrDefault(emptyList())
        else emptyList()
    }
    val activeSlotId = remember(refresh, menu, localModelEnabled) {
        if (localModelEnabled) runCatching { LocalModelStore.index(context).activeId }.getOrNull() else null
    }

    fun pickCloud(model: String) {
        menu = MENU_CLOSED
        onPickCloudModel(model)
        LucentToast.show(context, com.lucent.app.i18n.S.quickModelSwitched(model))
    }

    fun loadAllModels() {
        if (fetching) return
        fetching = true
        fetchNote = ""
        scope.launch {
            val result = LlmClient.fetchModels(baseUrl, spec, apiKey)
            fetching = false
            result.onSuccess { list ->
                val usable = list.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                if (usable.isEmpty()) {
                    fetchNote = com.lucent.app.i18n.S.quickModelFetchEmpty
                } else {
                    fetched = usable
                    onSelectedModelsChange(usable)
                }
            }.onFailure {
                fetchNote = com.lucent.app.i18n.S.quickModelFetchFailed
            }
        }
    }

    Box(modifier = modifier) {
        GlassRoundButton(
            icon = Icons.Default.SwapHoriz,
            contentDescription = com.lucent.app.i18n.S.quickModelTitle,
            onClick = {
                fetched = emptyList()
                fetchNote = ""
                menu = MENU_ROOT
                if (hasProfile && profileModels.isEmpty() && canFetch && !autoFetchTried) {
                    autoFetchTried = true
                    loadAllModels()
                }
            },
            tint = tint,
            diameter = buttonSize
        )

        if (menu != MENU_CLOSED) {
            Popup(
                alignment = Alignment.BottomStart,
                offset = IntOffset(0, -with(density) { (buttonSize + 6.dp).roundToPx() }),
                onDismissRequest = { menu = MENU_CLOSED },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.width(268.dp)) {
                        when (menu) {
                            MENU_ROOT -> rootMenu(
                                currentModel = currentModel,
                                localModelLabel = if (localModelEnabled) {
                                    runCatching { LocalModelStore.displayName(context) }.getOrNull()
                                        ?: com.lucent.app.i18n.S.quickModelNone
                                } else currentModel.ifBlank { com.lucent.app.i18n.S.quickModelNone },
                                localModelEnabled = localModelEnabled,
                                reasoningOffered = reasoningOffered,
                                reasoningLabel = if (settledReasoning == ReasoningEffort.PROVIDER_DEFAULT) {
                                    com.lucent.app.i18n.S.reasoningDefaultLabel
                                } else settledReasoning.label,
                                tint = tint,
                                mutedTint = mutedTint,
                                onModels = { menu = MENU_MODELS },
                                onReasoning = { menu = MENU_REASONING }
                            )
                            MENU_REASONING -> reasoningMenu(
                                options = reasoningOptions,
                                current = settledReasoning,
                                tint = tint,
                                mutedTint = mutedTint,
                                onBack = { menu = MENU_ROOT },
                                onPick = { option ->
                                    menu = MENU_CLOSED
                                    onPickReasoning(option)
                                }
                            )
                            else -> modelMenu(
                                localModelEnabled = localModelEnabled,
                                currentModel = currentModel,
                                slots = slots,
                                activeSlotId = activeSlotId,
                                profileMenu = profileMenu,
                                profileModels = profileModels,
                                hasProfile = hasProfile,
                                fetching = fetching,
                                fetchNote = fetchNote,
                                canFetch = canFetch,
                                recents = recents,
                                fetched = fetched,
                                tint = tint,
                                mutedTint = mutedTint,
                                onBack = { menu = MENU_ROOT },
                                onPickCloud = { pickCloud(it) },
                                onPickLocal = { slot ->
                                    menu = MENU_CLOSED
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            LocalLlm.shutdown()
                                            LocalModelStore.setActive(context, slot)
                                        }
                                        refresh++
                                        LucentToast.show(context, com.lucent.app.i18n.S.quickModelSwitched(slot))
                                    }
                                },
                                onFetch = { loadAllModels() },
                                onCustom = {
                                    typed = currentModel
                                    menu = MENU_CLOSED
                                    typing = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (typing) {
        AlertDialog(
            onDismissRequest = { typing = false },
            title = { Text(com.lucent.app.i18n.S.quickModelTitle) },
            text = {
                Column {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text(com.lucent.app.i18n.S.quickModelCustomLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(com.lucent.app.i18n.S.quickModelSameApiHint, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typed.isNotBlank(),
                    onClick = {
                        val model = typed.trim()
                        typing = false
                        if (model.isNotBlank()) {
                            onPickCloudModel(model)
                            LucentToast.show(context, com.lucent.app.i18n.S.quickModelSwitched(model))
                        }
                    }
                ) { Text(com.lucent.app.i18n.S.actionSave) }
            },
            dismissButton = { TextButton(onClick = { typing = false }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }
}

@Composable
private fun rootMenu(
    currentModel: String,
    localModelLabel: String,
    localModelEnabled: Boolean,
    reasoningOffered: Boolean,
    reasoningLabel: String,
    tint: Color,
    mutedTint: Color,
    onModels: () -> Unit,
    onReasoning: () -> Unit
) {
    MenuHeader(com.lucent.app.i18n.S.quickModelTitle, mutedTint)
    MenuRow(
        title = if (localModelEnabled) com.lucent.app.i18n.S.quickModelLocalSection
        else com.lucent.app.i18n.S.quickModelCurrent,
        subtitle = localModelLabel,
        tint = tint,
        mutedTint = mutedTint,
        onClick = onModels
    )
    if (reasoningOffered) {
        MenuRow(
            title = com.lucent.app.i18n.S.reasoningTitle,
            subtitle = reasoningLabel,
            tint = tint,
            mutedTint = mutedTint,
            icon = {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = mutedTint,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = onReasoning
        )
    }
}

@Composable
private fun reasoningMenu(
    options: List<ReasoningEffort>,
    current: ReasoningEffort,
    tint: Color,
    mutedTint: Color,
    onBack: () -> Unit,
    onPick: (ReasoningEffort) -> Unit
) {
    MenuHeader(com.lucent.app.i18n.S.reasoningTitle, mutedTint, onBack)
    options.forEach { option ->
        MenuRow(
            title = option.label,
            subtitle = option.detail,
            tint = if (option == current) tint else mutedTint,
            mutedTint = mutedTint,
            selected = option == current,
            onClick = { onPick(option) }
        )
    }
}

@Composable
private fun modelMenu(
    localModelEnabled: Boolean,
    currentModel: String,
    slots: List<LocalModelStore.ModelSlot>,
    activeSlotId: String?,
    profileMenu: Boolean,
    profileModels: List<String>,
    hasProfile: Boolean,
    fetching: Boolean,
    fetchNote: String,
    canFetch: Boolean,
    recents: List<String>,
    fetched: List<String>,
    tint: Color,
    mutedTint: Color,
    onBack: () -> Unit,
    onPickCloud: (String) -> Unit,
    onPickLocal: (String) -> Unit,
    onFetch: () -> Unit,
    onCustom: () -> Unit
) {
    MenuHeader(com.lucent.app.i18n.S.quickModelTitle, mutedTint, onBack)
    Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
        if (localModelEnabled) {
            if (slots.isEmpty()) {
                MenuNote(com.lucent.app.i18n.S.quickModelLocalEmpty, mutedTint)
            } else {
                slots.forEach { slot ->
                    MenuRow(
                        title = slot.name,
                        tint = if (slot.id == activeSlotId) tint else mutedTint,
                        mutedTint = mutedTint,
                        selected = slot.id == activeSlotId,
                        onClick = { onPickLocal(slot.id) }
                    )
                }
            }
        } else {
            when {
                profileMenu -> profileModels.forEach { model ->
                    MenuRow(
                        title = model,
                        tint = if (model == currentModel) tint else mutedTint,
                        mutedTint = mutedTint,
                        selected = model == currentModel,
                        onClick = { if (model != currentModel) onPickCloud(model) }
                    )
                }

                hasProfile -> {
                    MenuRow(
                        title = if (fetching) com.lucent.app.i18n.S.quickModelFetching
                        else com.lucent.app.i18n.S.quickModelFetch,
                        tint = tint,
                        mutedTint = mutedTint,
                        enabled = !fetching && canFetch,
                        icon = {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = mutedTint,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = onFetch
                    )
                    if (fetchNote.isNotBlank()) MenuNote(fetchNote, mutedTint)
                }

                else -> {
                    val shownRecents = recents.filter { it.isNotBlank() && it != currentModel }
                    if (shownRecents.isNotEmpty()) {
                        MenuHeader(com.lucent.app.i18n.S.quickModelRecent, mutedTint)
                        shownRecents.forEach { model ->
                            MenuRow(model, tint = tint, mutedTint = mutedTint, onClick = { onPickCloud(model) })
                        }
                    }
                    if (fetched.isEmpty()) {
                        MenuRow(
                            title = if (fetching) com.lucent.app.i18n.S.quickModelFetching
                            else com.lucent.app.i18n.S.quickModelFetch,
                            tint = tint,
                            mutedTint = mutedTint,
                            enabled = !fetching && canFetch,
                            icon = {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = mutedTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = onFetch
                        )
                        if (fetchNote.isNotBlank()) MenuNote(fetchNote, mutedTint)
                    } else {
                        fetched.filter { it != currentModel }.forEach { model ->
                            MenuRow(model, tint = tint, mutedTint = mutedTint, onClick = { onPickCloud(model) })
                        }
                    }
                }
            }

            HorizontalDivider()
            MenuRow(
                title = com.lucent.app.i18n.S.quickModelCustom,
                tint = tint,
                mutedTint = mutedTint,
                icon = {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = mutedTint,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = onCustom
            )
        }
    }
    if (!localModelEnabled) {
        Text(
            com.lucent.app.i18n.S.quickModelSameApiHint,
            color = mutedTint,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp)
        )
    }
}

@Composable
private fun MenuHeader(
    text: String,
    mutedTint: Color,
    onBack: (() -> Unit)? = null
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, end = 8.dp)
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.height(28.dp)) {
                Text("‹", color = mutedTint, fontSize = 16.sp)
            }
        }
        Text(text, color = mutedTint, fontSize = 11.sp)
    }
}

@Composable
private fun MenuNote(text: String, mutedTint: Color) {
    Text(
        text,
        color = mutedTint,
        fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
    )
}

@Composable
private fun MenuRow(
    title: String,
    tint: Color,
    mutedTint: Color,
    subtitle: String = "",
    selected: Boolean = false,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    androidx.compose.material3.DropdownMenuItem(
        text = {
            Column {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (enabled) tint else mutedTint,
                    fontSize = 14.sp
                )
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = mutedTint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            icon
        },
        enabled = enabled,
        onClick = onClick
    )
}
