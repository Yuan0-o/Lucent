package com.lucent.app.ui

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.local.LocalLlm
import com.lucent.app.local.LocalModelStore
import com.lucent.app.network.ApiSpec
import com.lucent.app.network.LlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var open by remember { mutableStateOf(false) }
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

    var refresh by remember { mutableStateOf(0) }
    val slots = remember(refresh, open, localModelEnabled) {
        if (localModelEnabled) runCatching { LocalModelStore.slots(context) }.getOrDefault(emptyList())
        else emptyList()
    }
    val activeSlotId = remember(refresh, open, localModelEnabled) {
        if (localModelEnabled) runCatching { LocalModelStore.index(context).activeId }.getOrNull() else null
    }

    fun pickCloud(model: String) {
        open = false
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

    IconButton(
        onClick = {
            fetched = emptyList()
            fetchNote = ""
            open = true
            if (hasProfile && profileModels.isEmpty() && canFetch && !autoFetchTried) {
                autoFetchTried = true
                loadAllModels()
            }
        },
        modifier = modifier
    ) {
        Icon(
            Icons.Default.SwapHoriz,
            contentDescription = com.lucent.app.i18n.S.quickModelTitle,
            tint = tint
        )
    }

    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        Column(modifier = Modifier.width(260.dp)) {
            Text(
                if (localModelEnabled) com.lucent.app.i18n.S.quickModelLocalSection
                else com.lucent.app.i18n.S.quickModelCurrent,
                color = mutedTint,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 14.dp, top = 10.dp)
            )
            Text(
                if (localModelEnabled) (LocalModelStore.displayName(context) ?: com.lucent.app.i18n.S.quickModelNone)
                else currentModel.ifBlank { com.lucent.app.i18n.S.quickModelNone },
                color = tint,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
            )
            HorizontalDivider()

            Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                if (localModelEnabled) {
                    if (slots.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(com.lucent.app.i18n.S.quickModelLocalEmpty, fontSize = 12.sp) },
                            onClick = { },
                            enabled = false
                        )
                    } else {
                        slots.forEach { slot ->
                            val active = slot.id == activeSlotId
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        slot.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (active) tint else mutedTint
                                    )
                                },
                                leadingIcon = if (active) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                onClick = {
                                    open = false
                                    if (!active) {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                LocalLlm.shutdown()
                                                LocalModelStore.setActive(context, slot.id)
                                            }
                                            refresh++
                                            LucentToast.show(context, com.lucent.app.i18n.S.quickModelSwitched(slot.name))
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    when {
                        profileMenu -> {
                            profileModels.forEach { model ->
                                val active = model == currentModel
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            model,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (active) tint else mutedTint
                                        )
                                    },
                                    leadingIcon = if (active) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null,
                                    onClick = { if (active) open = false else pickCloud(model) }
                                )
                            }
                        }

                        hasProfile -> {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (fetching) com.lucent.app.i18n.S.quickModelFetching
                                        else com.lucent.app.i18n.S.quickModelFetch
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                enabled = !fetching && canFetch,
                                onClick = { loadAllModels() }
                            )
                            if (fetchNote.isNotBlank()) {
                                Text(
                                    fetchNote,
                                    color = mutedTint,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                )
                            }
                        }

                        else -> {
                            val shownRecents = recents.filter { it.isNotBlank() && it != currentModel }
                            if (shownRecents.isNotEmpty()) {
                                Text(
                                    com.lucent.app.i18n.S.quickModelRecent,
                                    color = mutedTint,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 14.dp, top = 8.dp)
                                )
                                shownRecents.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model, maxLines = 1, overflow = TextOverflow.Ellipsis, color = tint) },
                                        onClick = { pickCloud(model) }
                                    )
                                }
                                HorizontalDivider()
                            }

                            if (fetched.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (fetching) com.lucent.app.i18n.S.quickModelFetching
                                            else com.lucent.app.i18n.S.quickModelFetch
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    enabled = !fetching && baseUrl.isNotBlank(),
                                    onClick = { loadAllModels() }
                                )
                                if (fetchNote.isNotBlank()) {
                                    Text(
                                        fetchNote,
                                        color = mutedTint,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                fetched.filter { it != currentModel }.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model, maxLines = 1, overflow = TextOverflow.Ellipsis, color = tint) },
                                        onClick = { pickCloud(model) }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(com.lucent.app.i18n.S.quickModelCustom) },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            typed = currentModel
                            open = false
                            typing = true
                        }
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
