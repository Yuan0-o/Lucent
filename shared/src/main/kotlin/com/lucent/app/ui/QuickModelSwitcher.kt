package com.lucent.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
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

private val MENU_WIDTH = 214.dp

private val menuPosition = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0)
        val above = anchorBounds.top - popupContentSize.height - 12
        val y = if (above >= 0) above else (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

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
    plain: Boolean = false,
    reasoningProviderId: String = "",
    reasoningCurrent: String = ReasoningEffort.DEFAULT.key,
    onPickReasoning: (ReasoningEffort) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var menu by remember { mutableStateOf(MENU_CLOSED) }
    var query by remember { mutableStateOf("") }
    var fetched by remember { mutableStateOf<List<String>>(emptyList()) }
    var fetching by remember { mutableStateOf(false) }
    var fetchNote by remember { mutableStateOf("") }
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
        query = ""
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
                query = ""
                fetchNote = ""
                menu = MENU_ROOT
                if (hasProfile && profileModels.isEmpty() && canFetch && !autoFetchTried) {
                    autoFetchTried = true
                    loadAllModels()
                }
            },
            tint = tint,
            diameter = buttonSize,
            plain = plain
        )

        if (menu != MENU_CLOSED) {
            Popup(
                popupPositionProvider = menuPosition,
                onDismissRequest = {
                    menu = MENU_CLOSED
                    query = ""
                },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp
                ) {
                    Column(modifier = Modifier.width(MENU_WIDTH)) {
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
                            else -> modelsMenu(
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
                                query = query,
                                onQuery = { query = it },
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
                                onFetch = { loadAllModels() }
                            )
                        }
                    }
                }
            }
        }
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
    MenuRow(
        title = if (localModelEnabled) com.lucent.app.i18n.S.quickModelLocalSection
        else com.lucent.app.i18n.S.quickModelCurrent,
        subtitle = localModelLabel,
        tint = tint,
        mutedTint = mutedTint,
        icon = {
            Icon(
                Icons.Default.Memory,
                contentDescription = null,
                tint = mutedTint,
                modifier = Modifier.size(18.dp)
            )
        },
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
    Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
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
}

@Composable
private fun modelsMenu(
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
    query: String,
    onQuery: (String) -> Unit,
    tint: Color,
    mutedTint: Color,
    onBack: () -> Unit,
    onPickCloud: (String) -> Unit,
    onPickLocal: (String) -> Unit,
    onFetch: () -> Unit
) {
    MenuHeader(com.lucent.app.i18n.S.quickModelTitle, mutedTint, onBack)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, mutedTint.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp)
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = mutedTint, modifier = Modifier.size(14.dp))
        Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
            if (query.isEmpty()) {
                Text(
                    com.lucent.app.i18n.S.quickModelSearch,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = mutedTint.copy(alpha = 0.75f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, lineHeight = 14.sp, color = tint),
                cursorBrush = SolidColor(tint),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

    val needle = query.trim()
    Column(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
        if (localModelEnabled) {
            val visible = slots.filter { needle.isBlank() || it.name.contains(needle, ignoreCase = true) }
            if (visible.isEmpty()) {
                MenuNote(com.lucent.app.i18n.S.quickModelLocalEmpty, mutedTint)
            } else {
                visible.forEach { slot ->
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
            if (needle.isNotBlank()) {
                MenuRow(
                    title = com.lucent.app.i18n.S.quickModelUseNamed(needle),
                    tint = tint,
                    mutedTint = mutedTint,
                    onClick = { onPickCloud(needle) }
                )
            }
            when {
                profileMenu -> profileModels
                    .filter { needle.isBlank() || it.contains(needle, ignoreCase = true) }
                    .forEach { model ->
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
                    val shownRecents = recents
                        .filter { it.isNotBlank() && it != currentModel }
                        .filter { needle.isBlank() || it.contains(needle, ignoreCase = true) }
                    if (shownRecents.isNotEmpty()) {
                        MenuHeader(com.lucent.app.i18n.S.quickModelRecent, mutedTint)
                        shownRecents.forEach { model ->
                            MenuRow(model, tint = tint, mutedTint = mutedTint, onClick = { onPickCloud(model) })
                        }
                    }
                    val shownFetched = fetched
                        .filter { it != currentModel }
                        .filter { needle.isBlank() || it.contains(needle, ignoreCase = true) }
                    if (shownFetched.isEmpty() && shownRecents.isEmpty()) {
                        if (fetchNote.isNotBlank()) MenuNote(fetchNote, mutedTint)
                    } else {
                        shownFetched.forEach { model ->
                            MenuRow(model, tint = tint, mutedTint = mutedTint, onClick = { onPickCloud(model) })
                        }
                    }
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
                }
            }
        }
    }
    if (!localModelEnabled) {
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun MenuHeader(
    text: String,
    mutedTint: Color,
    onBack: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 8.dp, end = 10.dp, bottom = 2.dp)
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = com.lucent.app.i18n.S.actionBack,
                    tint = mutedTint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text,
            color = mutedTint,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (onBack == null) 8.dp else 2.dp)
        )
    }
}

@Composable
private fun MenuNote(text: String, mutedTint: Color) {
    Text(
        text,
        color = mutedTint,
        fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
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
                    fontSize = 13.sp
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = mutedTint,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
