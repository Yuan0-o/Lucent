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

/**
 * The quick model switcher that sits immediately left of the send button (B-group task 5).
 *
 * ### What it switches, and what it deliberately does not
 *
 * The MODEL, and only the model. The API profile — endpoint, spec, key — is untouched, which is the
 * explicit requirement: people want to try the same question against a cheaper or a stronger model
 * on the SAME provider without walking to Settings, opening the API page, editing a field and
 * saving. Switching provider remains a Settings action, because it changes billing and credentials
 * and deserves that friction.
 *
 * ### Where the list comes from
 *
 * Three sources, in decreasing order of immediacy, because a menu that has to hit the network before
 * it can show anything is not "quick":
 *
 *  1. **Recently used** ([com.lucent.app.data.ModelRecents]) — instant, offline, and in practice the
 *     two or three models a given user actually alternates between.
 *  2. **The provider's catalogue** — one tap, fetched through the existing [LlmClient.fetchModels]
 *     the Settings API page already uses. Opt-in rather than automatic: opening a menu should never
 *     silently spend a network round-trip, and some endpoints don't implement /models at all.
 *  3. **Typed by hand** — the escape hatch for a brand-new model id that no catalogue lists yet.
 *
 * In local mode the same control lists the imported on-device slots instead, because in that mode
 * those *are* the models; switching one frees the resident model immediately (a multi-gigabyte
 * allocation is not something to leave lying around) and the next send loads the new slot.
 *
 * The switcher is intentionally a single icon button: the chat input row is already crowded on a
 * phone, and the current model name is available in Settings and in the menu's own header.
 */
@Composable
fun QuickModelSwitcher(
    currentModel: String,
    recents: List<String>,
    baseUrl: String,
    spec: ApiSpec,
    apiKey: String,
    localModelEnabled: Boolean,
    tint: Color,
    mutedTint: Color,
    onPickCloudModel: (String) -> Unit,
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

    // Local slots are read on each open rather than cached: they change on the Local model page,
    // which this composable has no way to observe. `refresh` re-reads after a switch.
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

    IconButton(
        onClick = {
            // A fresh menu each time: a catalogue fetched against the previous API would be a list
            // of models this one cannot serve.
            fetched = emptyList()
            fetchNote = ""
            open = true
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
            // ---- Header: what is in use right now ----
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
                    // ---- On-device slots ----
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
                                                // Free the outgoing model before pointing at the new
                                                // slot — the same order the Local model page uses,
                                                // so a multi-gigabyte allocation is never held for a
                                                // model that is no longer selected.
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
                    // ---- Recently used ----
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

                    // ---- The provider's catalogue, on request ----
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
                            onClick = {
                                fetching = true
                                fetchNote = ""
                                scope.launch {
                                    val result = LlmClient.fetchModels(baseUrl, spec, apiKey)
                                    fetching = false
                                    result.onSuccess { list ->
                                        fetched = list
                                        if (list.isEmpty()) fetchNote = com.lucent.app.i18n.S.quickModelFetchEmpty
                                    }.onFailure {
                                        fetchNote = com.lucent.app.i18n.S.quickModelFetchFailed
                                    }
                                }
                            }
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

                    // ---- Typed by hand ----
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
