package com.lucent.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfiles
import com.lucent.app.data.ModelSearch
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.network.ApiSpec
import com.lucent.app.network.LlmClient
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.specLabel
import kotlinx.coroutines.launch

/**
 * P1-3 — extracted from the `ApiPage` local composable that used to live inside `SettingsScreen`.
 * Diffing the two originals found them byte-identical (one trailing blank line aside), so this
 * page needed no seam of any kind.
 *
 * Every editable field here — profile list, the profile currently being edited, the fetched
 * model list, the fetch's own loading/error state — stays owned by [SettingsScreen] and is
 * threaded through as value + setter, because outer-scope functions unrelated to this page's own
 * composition (`saveActiveProfile`, `selectProfile`, `addProfile`, the delete-confirmation dialog,
 * and the two `LaunchedEffect`s that auto-hide a revealed key) read and write the very same state.
 * [keyVisible]/[onRevealKey] intentionally don't expose *why* the key hides itself again — that
 * timer is `SettingsScreen`'s own mechanism, not this page's concern, the same way font/model
 * import don't expose *how* a file gets picked.
 */
@Composable
internal fun ApiSettingsPage(
    repo: SettingsRepository,
    profiles: List<ApiProfile>,
    selectedProfileIdx: Int,
    editingProfileName: String,
    onEditingProfileNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    spec: String,
    onSpecChange: (String) -> Unit,
    key: String,
    onKeyChange: (String) -> Unit,
    keyVisible: Boolean,
    onRevealKey: () -> Unit,
    selectedModel: String,
    onSelectedModelChange: (String) -> Unit,
    models: List<String>,
    onModelsChange: (List<String>) -> Unit,
    loading: Boolean,
    onLoadingChange: (Boolean) -> Unit,
    errorText: String,
    onErrorTextChange: (String) -> Unit,
    onRequestDeleteProfile: (Int) -> Unit,
    onSelectProfile: (Int) -> Unit,
    onAddProfile: () -> Unit,
    onSaveProfile: () -> Unit,
    onRoute: (SettingsRoute) -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    val localModelEnabled by repo.localModelEnabled.collectAsState(initial = false)
    var menuExpanded by remember { mutableStateOf(false) }

    BackHeader(S.settingsApiTitle) { onRoute(SettingsRoute.Assistant) }

    // When local model mode is on, the cloud API is FROZEN — the assistant answers
    // on-device and never calls the API. Say so plainly at the top of the page, with a
    // one-tap way back to the Local model page to turn it off. That link matters more
    // than usual now that the rest of the page is hidden behind the freeze: it is the
    // only route out, so it has to be right here in the explanation.
    if (localModelEnabled) {
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(S.apiFrozenTitle, color = onGradient, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(S.apiFrozenBody, color = onGradientMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            // Glass, like every other page-level button in Settings. This was one of
            // the last Material 3 controls left on a page made entirely of glass.
            GlassButton(text = S.apiFrozenManage, onClick = { onRoute(SettingsRoute.LocalModel) })
        }
    }

    // Task 18: while the freeze is on, the blocks below are HIDDEN rather than shown
    // greyed. They were disabled before, which left a full API editor sitting under a
    // banner saying it would never be used — fields you could type in, a Save button you
    // could not press, a model list that would not load. Disabling communicates "not
    // now"; the honest message here is "not while this mode is on", and the way a screen
    // says that is by not offering the controls at all. Nothing is lost: every profile,
    // key and model stays saved, and flipping local mode off brings this page back
    // exactly as it was.
    if (!localModelEnabled) {

    // ---- API Selection: pick which saved profile is active ----
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.apiSelectionTitle, color = onGradient, modifier = Modifier.weight(1f))
            Text("${profiles.size}/${ApiProfiles.MAX}", color = onGradientMuted, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(S.apiSelectionDesc(ApiProfiles.MAX), color = onGradientMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        if (profiles.isEmpty()) {
            // Deleting the last API is allowed now (task 6), so this state is reachable
            // and has to be a place the user can stand: it names what happened and both
            // ways forward, rather than an empty card that looks like a rendering bug.
            Text(S.apiNoneTitle, color = onGradient, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(S.apiNoneBody, color = onGradientMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
        }
        profiles.forEachIndexed { idx, p ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = idx == selectedProfileIdx, onClick = { onSelectProfile(idx) })
                Column(modifier = Modifier.weight(1f).clickable { onSelectProfile(idx) }.padding(vertical = 4.dp)) {
                    Text(p.name.ifBlank { S.apiFallbackName(idx + 1) }, color = onGradient)
                    Text(
                        "${specLabel(p.spec)} · ${if (p.model.isBlank()) S.apiNoModel else p.model}",
                        color = onGradientMuted,
                        fontSize = 12.sp
                    )
                }
                // The delete icon is shown on every row, the only profile included, and
                // every delete goes through the confirmation dialog below first — there
                // is no quiet path that removes a saved key (task 6).
                IconButton(onClick = { onRequestDeleteProfile(idx) }) {
                    Icon(Icons.Default.Delete, contentDescription = S.apiDeleteA11y, tint = onGradientMuted)
                }
            }
        }
        if (profiles.size < ApiProfiles.MAX) {
            Spacer(modifier = Modifier.height(4.dp))
            GlassButton(text = S.apiAddButton, icon = Icons.Default.Add, onClick = onAddProfile)
        }
    }

    // Nothing selected means nothing to edit, so the editor block is hidden entirely
    // rather than bound to a phantom profile (task 6).
    if (profiles.isNotEmpty()) {

    Spacer(modifier = Modifier.height(12.dp))

    // ---- Editor for the selected profile ----
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.apiEditTitle, color = onGradient)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = editingProfileName,
            onValueChange = onEditingProfileNameChange,
            label = { Text(S.fieldName) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = key,
            onValueChange = onKeyChange,
            label = { Text(S.fieldApiKey) },
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onRevealKey) {
                    Icon(
                        if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = S.a11yToggleKeyVisibility
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(S.apiSpecTitle, color = onGradient)
        Row {
            RadioButton(selected = spec == "openai", onClick = { onSpecChange("openai") })
            Text(S.apiSpecOpenAi, color = onGradient, modifier = Modifier.padding(top = 14.dp))
        }
        Row {
            RadioButton(selected = spec == "anthropic", onClick = { onSpecChange("anthropic") })
            Text(S.apiSpecAnthropic, color = onGradient, modifier = Modifier.padding(top = 14.dp))
        }
        Row {
            RadioButton(selected = spec == "google", onClick = { onSpecChange("google") })
            Text(S.apiSpecGoogle, color = onGradient, modifier = Modifier.padding(top = 14.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(S.apiConnectionTitle, color = onGradient)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(S.fieldBaseUrl) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            when (spec) {
                "anthropic" -> S.apiUrlExampleAnthropic
                "google" -> S.apiUrlExampleGoogle
                else -> S.apiUrlExampleOpenAi
            },
            color = onGradientMuted
        )

        Spacer(modifier = Modifier.height(12.dp))
        // No `enabled = !localModelEnabled` guard any more: this whole block is hidden
        // while the API is frozen (task 18), so the only way to reach this button is
        // with local mode off.
        GlassButton(text = S.fetchModels, onClick = {
            if (url.trim().isEmpty()) {
                // No address yet: say so plainly instead of letting the HTTP client throw a
                // technical "malformed URL" style error the user can't act on.
                onErrorTextChange(S.apiUrlRequired)
            } else {
                onLoadingChange(true)
                onErrorTextChange("")
                scope.launch {
                    val apiSpecEnum = when (spec) {
                        "anthropic" -> ApiSpec.ANTHROPIC
                        "google" -> ApiSpec.GOOGLE
                        else -> ApiSpec.OPENAI
                    }
                    val result = LlmClient.fetchModels(url.trim(), apiSpecEnum, key.trim())
                    onLoadingChange(false)
                    result.onSuccess { onModelsChange(it) }
                        .onFailure { onErrorTextChange(S.errorWithDetail(it.javaClass.simpleName, it.message ?: S.noDetails)) }
                }
            }
        })

        if (loading) {
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator()
        }
        if (errorText.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorText, color = Color(0xFFFFC1C1))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(S.fieldModel, color = onGradient)
        Box {
            GlassButton(
                text = if (selectedModel.isBlank()) S.chooseModel else selectedModel,
                onClick = { menuExpanded = true },
                enabled = models.isNotEmpty()
            )
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                // R3 report: a manual FUZZY model search on top of the list, sharing the
                // same ranking rules as the chat model switcher (ModelSearch.rankModels):
                // typing part of a name — case, separators and even exact order optional —
                // reorders the list best-match first. The query resets with each open.
                var query by remember(menuExpanded) { mutableStateOf("") }
                Column(modifier = Modifier.width(280.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(S.actionSearch) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val shown = ModelSearch.rankModels(models, query)
                        shown.forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = {
                                onSelectedModelChange(m)
                                menuExpanded = false
                            })
                        }
                    }
                }
            }
        }
        if (models.isEmpty() && selectedModel.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(S.currentModelHint(selectedModel), color = onGradientMuted, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        // Saving writes the edits into the selected profile and activates it, so the
        // assistant uses it right away.
        GlassButton(text = S.saveApi, onClick = onSaveProfile)
    }
    } // profiles.isNotEmpty()
    } // !localModelEnabled
}
