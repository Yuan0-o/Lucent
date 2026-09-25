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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfiles
import com.lucent.app.data.ApiProviders
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.ModelSearch
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.network.ApiSpec
import com.lucent.app.network.LlmClient
import com.lucent.app.ui.ApiModelPickerDialog
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.specLabel
import kotlinx.coroutines.launch

private fun providerName(id: String): String = when (id) {
    ApiProviders.CHATGPT -> S.apiProviderChatGpt
    ApiProviders.GEMINI -> S.apiProviderGemini
    ApiProviders.CLAUDE -> S.apiProviderClaude
    ApiProviders.DEEPSEEK -> S.apiProviderDeepSeek
    ApiProviders.KIMI -> S.apiProviderKimi
    else -> S.apiProviderCustom
}

private fun profileProviderLabel(profile: ApiProfile): String =
    if (ApiProviders.isPreset(profile.provider)) providerName(profile.provider) else specLabel(profile.spec)

private fun modelChoices(fetched: List<String>, saved: List<String>): List<String> {
    val out = LinkedHashSet<String>()
    fetched.forEach { if (it.isNotBlank()) out.add(it.trim()) }
    saved.sorted().forEach { if (it.isNotBlank()) out.add(it.trim()) }
    return out.toList()
}

@Composable
internal fun CloudModelSettingsPage(
    repo: SettingsRepository,
    profiles: List<ApiProfile>,
    selectedProfileIdx: Int,
    editingProfileName: String,
    onEditingProfileNameChange: (String) -> Unit,
    provider: String,
    onProviderChange: (String) -> Unit,
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
    var menuExpanded by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickerOpen by remember { mutableStateOf(false) }
    val customProvider = provider == ApiProviders.CUSTOM
    val choices = modelChoices(fetchedModels, models)
    val savedProfile = profiles.getOrNull(selectedProfileIdx)
    val unsaved = savedProfile != null && (
        editingProfileName.trim().ifBlank { S.apiFallbackName(selectedProfileIdx + 1) } != savedProfile.name ||
            provider != savedProfile.provider ||
            spec != savedProfile.spec ||
            url.trim() != savedProfile.baseUrl ||
            key.trim() != savedProfile.apiKey ||
            selectedModel != savedProfile.model ||
            models.toSet() != savedProfile.selectedModels.toSet()
        )

    BackHeader(onBack = { onRoute(SettingsRoute.Assistant) })

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.apiSelectionTitle, color = onGradient, modifier = Modifier.weight(1f))
            Text("${profiles.size}/${ApiProfiles.MAX}", color = onGradientMuted, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (profiles.isEmpty()) {
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
                        "${profileProviderLabel(p)} · ${if (p.model.isBlank()) S.apiNoModel else p.model}",
                        color = onGradientMuted,
                        fontSize = 12.sp
                    )
                }
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

    Spacer(modifier = Modifier.height(12.dp))

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
        Text(S.apiProviderTitle, color = onGradient)
        ApiProviders.ALL.forEach { id ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onProviderChange(id) }
            ) {
                RadioButton(selected = provider == id, onClick = { onProviderChange(id) })
                Text(providerName(id), color = onGradient, modifier = Modifier.padding(top = 14.dp))
            }
        }

        if (customProvider) {
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
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(S.apiConnectionTitle, color = onGradient)
        if (customProvider) {
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
        }

        Spacer(modifier = Modifier.height(12.dp))
        GlassButton(text = S.fetchModels, onClick = {
            if (url.trim().isEmpty()) {
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
                    result.onSuccess { list ->
                        if (list.isEmpty()) {
                            onErrorTextChange(S.apiModelsEmpty)
                        } else {
                            fetchedModels = list
                            pickerOpen = true
                        }
                    }.onFailure {
                        val detail = (it.message ?: "").trim().ifBlank { S.noDetails }.take(180)
                        onErrorTextChange(S.apiModelsFetchFailed(detail))
                    }
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
        Spacer(modifier = Modifier.height(12.dp))
        GlassButton(text = S.saveApi, onClick = onSaveProfile)
        if (unsaved) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(S.apiUnsavedHint, color = onGradientMuted, fontSize = 12.sp)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    AssistantMemorySection(repo = repo, local = false)

    if (pickerOpen) {
        ApiModelPickerDialog(
            names = choices,
            selected = models.toSet(),
            onDone = { picked ->
                onModelsChange(choices.filter { it in picked })
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }
}
