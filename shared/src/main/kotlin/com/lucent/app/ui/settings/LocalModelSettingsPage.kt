package com.lucent.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.AttachmentLimits
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.local.LocalLlm
import com.lucent.app.local.LocalModelStore
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

@Composable
internal fun LocalModelSettingsPage(
    repo: SettingsRepository,
    lmModels: List<LocalModelStore.ModelSlot>,
    lmActiveId: String?,
    lmImporting: Boolean,
    lmCanImportMore: Boolean,
    lmError: String,
    lmRefresh: Int,
    onSelectLocalModel: (String) -> Unit,
    onRequestRenameModel: (LocalModelStore.ModelSlot) -> Unit,
    onRequestDeleteModel: (LocalModelStore.ModelSlot) -> Unit,
    onImportModelClick: () -> Unit,
    onImportMmprojClick: () -> Unit,
    onRemoveMmproj: () -> Unit,
    onRequestUseLocalOn: () -> Unit,
    onRequestToolsOn: () -> Unit,
    onRequestGpuOn: () -> Unit,
    onRequestBackgroundOn: () -> Unit,
    onRoute: (SettingsRoute) -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val localModelEnabled by repo.localModelEnabled.collectAsState(initial = false)
    val localToolsEnabled by repo.localToolsEnabled.collectAsState(initial = false)
    val localGpuEnabled by repo.localGpuEnabled.collectAsState(initial = false)
    val localBackgroundReply by repo.localBackgroundReplyEnabled.collectAsState(initial = false)

    BackHeader(S.settingsLocalModelTitle) { onRoute(SettingsRoute.Assistant) }

    if (!LocalLlm.isSupported()) {
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(
                if (LocalLlm.unsupportedBecauseCpuLacksAvx2()) S.localModelNeedsAvx2 else S.lmUnsupportedAbiNote,
                color = onGradientMuted, fontSize = 13.sp
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(S.lmUseLocalToggle, color = onGradient, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = localModelEnabled,
                    onCheckedChange = { on ->
                        if (on) onRequestUseLocalOn()
                        else AppScope.io.launch { repo.setLocalModelEnabled(false) }
                    }
                )
            }
            if (!localModelEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(S.lmEnableToConfigureNote, color = onGradientMuted, fontSize = 12.sp)
            }
        }

        if (localModelEnabled) {
            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.lmModelsTitle, color = onGradient, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("${lmModels.size}/${LocalModelStore.MAX_MODELS}", color = onGradientMuted, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (lmModels.isEmpty()) {
                    Text(S.lmNeedModelNotice, color = onGradient, fontSize = 14.sp)
                } else {
                    lmModels.forEach { slot ->
                        val active = slot.id == lmActiveId
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = active, onClick = { onSelectLocalModel(slot.id) })
                            Column(
                                modifier = Modifier.weight(1f).clickable { onSelectLocalModel(slot.id) }.padding(vertical = 4.dp)
                            ) {
                                Text(slot.name.ifBlank { "model.gguf" }, color = onGradient, fontSize = 14.sp)
                                Text(
                                    AttachmentLimits.formatBytes(LocalModelStore.modelSizeBytes(context, slot.id)) +
                                        (if (active) " · " + S.lmActiveTag else ""),
                                    color = onGradientMuted,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { onRequestRenameModel(slot) }) {
                                Icon(Icons.Default.Edit, contentDescription = S.lmRenameA11y, tint = onGradientMuted)
                            }
                            IconButton(onClick = { onRequestDeleteModel(slot) }) {
                                Icon(Icons.Default.Delete, contentDescription = S.lmDeleteA11y, tint = onGradientMuted)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (lmImporting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(S.lmImporting, color = onGradientMuted, fontSize = 13.sp)
                    }
                } else if (lmCanImportMore) {
                    GlassButton(
                        text = S.lmImportButton,
                        icon = Icons.Default.Add,
                        onClick = onImportModelClick
                    )
                } else {
                    Text(S.lmSlotsFullHint(LocalModelStore.MAX_MODELS), color = onGradientMuted, fontSize = 12.sp)
                }
                if (lmError.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(lmError, color = Color(0xFFFFC1C1), fontSize = 13.sp)
                }

                if (lmActiveId != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(S.lmMmprojTitle, color = onGradient, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    val lmMmprojFile = remember(lmRefresh, lmActiveId) { LocalModelStore.activeMmprojFile(context) }
                    Text(
                        lmMmprojFile?.let { "${it.length() / (1024 * 1024)} MB" } ?: S.lmMmprojMissing,
                        color = onGradientMuted, fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(
                            text = S.lmMmprojImport,
                            icon = Icons.Default.Add,
                            onClick = onImportMmprojClick
                        )
                        if (lmMmprojFile != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            GlassButton(text = S.lmMmprojRemove, onClick = onRemoveMmproj)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(S.lmToolsToggle, color = onGradient, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = localToolsEnabled,
                        onCheckedChange = { on ->
                            if (on) onRequestToolsOn()
                            else AppScope.io.launch { repo.setLocalToolsEnabled(false) }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(S.lmGpuToggle, color = onGradient, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = localGpuEnabled,
                        onCheckedChange = { on ->
                            if (on) onRequestGpuOn()
                            else AppScope.io.launch { repo.setLocalGpuEnabled(false) }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(S.lmBackgroundToggle, color = onGradient, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = localBackgroundReply,
                        onCheckedChange = { on ->
                            if (on) onRequestBackgroundOn()
                            else AppScope.io.launch { repo.setLocalBackgroundReplyEnabled(false) }
                        }
                    )
                }
            }
        }
    }
}
