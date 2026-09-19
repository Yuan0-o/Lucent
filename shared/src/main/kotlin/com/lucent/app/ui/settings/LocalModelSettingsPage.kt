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

/**
 * P1-3 — extracted from the `LocalModelPage` local composable that used to live inside
 * `SettingsScreen`. Diffing the two originals found three differences, none of which needed a new
 * seam:
 * - The unsupported-hardware message: desktop names AVX2-less CPUs specifically via
 *   `LocalLlm.unsupportedBecauseCpuLacksAvx2()`. That function is already a seam (Android's
 *   implementation is a hardcoded `false`, since AVX2 is an x86 concept), so the desktop
 *   expression works unchanged on Android — it just never takes the AVX2 branch there.
 * - Model import and mmproj import each trigger through an opaque callback
 *   ([onImportModelClick]/[onImportMmprojClick]), same reasoning as Language's font import: the
 *   picker (`ActivityResultContracts` vs a native file dialog) stays in each platform's own
 *   `SettingsScreen.kt`.
 *
 * Everything else that reads as local state here — the model list, rename/delete targets, the
 * four "turning this on has a consequence" confirmation flags — stays owned by `SettingsScreen`
 * for the same reason as Language's font state: outer-scope dialogs and functions
 * (`RenameLocalModelDialog`, `DeleteLocalModelDialog`, the four `Confirm*Dialog`s,
 * `startLocalImport`, `selectLocalModel`, `removeMmproj`) read and write it too, so this page only
 * ever receives read values and one-way callbacks, never owns the state itself.
 */
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
        // The .so wasn't packaged for this ABI (or failed to load). Everything below
        // would be a dead end, so say why once, plainly, instead of offering buttons
        // that can only disappoint.
        //
        // W-1: an AVX2-less CPU gets the sentence that names the processor, not the
        // packaging — different problem, different (non-)remedy.
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(
                if (LocalLlm.unsupportedBecauseCpuLacksAvx2()) S.localModelNeedsAvx2 else S.lmUnsupportedAbiNote,
                color = onGradientMuted, fontSize = 13.sp
            )
        }
    } else {
        // =========================================================================
        // The master switch — and, until it is on, nothing else (task 15)
        // =========================================================================
        //
        // The page used to open with the importer: pick a multi-gigabyte file first,
        // then decide whether you wanted the feature at all. That is backwards. Importing
        // is the expensive, irreversible-feeling step, and asking for it before the user
        // has said yes to anything makes the whole page read as a commitment. Worse, the
        // enable switch sat *below* the importer and was disabled until a model existed,
        // so the one control that explains what the page is for was the last thing you
        // reached and the only one you couldn't touch.
        //
        // So the order is now the order of the decision: do you want the assistant to run
        // on this device — yes — now here is what that involves. Everything below the
        // switch is hidden while it is off, which also settles task 1 more firmly than
        // greying would: a control that isn't there cannot be operated by accident, and
        // the page stops presenting four questions when only the first one is live.
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(S.lmUseLocalToggle, color = onGradient, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Switchable ON with no model imported, which the old build forbade.
                // It has to be: the importer only appears once this is on, so gating it
                // on a model that can only be imported afterwards was a deadlock. If it
                // is on with no model the page says so (lmNeedModelNotice below) and the
                // assistant answers with a clear "no model" error rather than silently
                // falling back to the cloud API.
                Switch(
                    checked = localModelEnabled,
                    onCheckedChange = { on ->
                        if (on) onRequestUseLocalOn()
                        else AppScope.io.launch { repo.setLocalModelEnabled(false) }
                    }
                )
            }
            // The "local mode is text only" paragraph that used to sit here has been
            // removed: on-device multimodal is supported now (see the mmproj projector
            // setting below), so the note described a limitation that no longer exists.
            // A stale warning is worse than no warning — it talks a user out of a feature
            // that works.
            if (!localModelEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(S.lmEnableToConfigureNote, color = onGradientMuted, fontSize = 12.sp)
            }
        }

        if (localModelEnabled) {
            Spacer(modifier = Modifier.height(12.dp))

            // ---------------------------------------------------------------
            // Models: import, choose the active one, rename, delete
            // ---------------------------------------------------------------
            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.lmModelsTitle, color = onGradient, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("${lmModels.size}/${LocalModelStore.MAX_MODELS}", color = onGradientMuted, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (lmModels.isEmpty()) {
                    // Local mode is on with nothing to run: the single most confusing
                    // state this feature can be in, so it is named rather than implied.
                    Text(S.lmNeedModelNotice, color = onGradient, fontSize = 14.sp)
                } else {
                    // One row per imported model: a radio picks the ACTIVE model (only it
                    // is ever loaded), its name and size are shown, and each has rename +
                    // delete. Switching the radio releases the loaded model right away.
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

                // ---- PHASE 4: multimodal projector (mmproj) for the active model ----
                // Two files make a vision-capable local model: the model .gguf above and a
                // projector .gguf from the SAME model family (a Qwen projector cannot serve
                // a Gemma model). The status line says which state the active slot is in.
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

            // ---- Opt-in: let the on-device model act on notes/tasks ----
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

            // ---- Opt-in: run the model on the GPU instead of the CPU ----
            // No "(experimental)" on this sub-option (task 7): repeating the word on every
            // knob starts to read as noise rather than as a warning.
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
                            // Turning OFF also only records: an in-flight reply (if any)
                            // finishes on the GPU and the next one loads on the CPU.
                            else AppScope.io.launch { repo.setLocalGpuEnabled(false) }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- Opt-in: keep generating after the app leaves the foreground ----
            // Hidden with the rest of this section when local mode is off, because it
            // describes something only the local model does (task 2).
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
