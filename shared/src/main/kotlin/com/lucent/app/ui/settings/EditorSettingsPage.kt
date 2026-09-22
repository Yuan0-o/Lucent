package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.DesktopIntegrationRows
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

@Composable
internal fun EditorSettingsPage(
    repo: SettingsRepository,
    onRequestOpenLinksWarning: () -> Unit,
    onRoute: (SettingsRoute) -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    var markdownEnabled by remember { mutableStateOf(SettingsCache.markdownEnabled) }
    LaunchedEffect(Unit) { repo.markdownEnabled.collect { markdownEnabled = it } }
    var richTextEnabled by remember { mutableStateOf(SettingsCache.richTextEnabled) }
    LaunchedEffect(Unit) { repo.richTextEnabled.collect { richTextEnabled = it } }
    var linksEnabled by remember { mutableStateOf(SettingsCache.linksEnabled) }
    LaunchedEffect(Unit) { repo.linksEnabled.collect { linksEnabled = it } }
    var blackoutOn by remember { mutableStateOf(SettingsCache.blackoutEnabled) }
    LaunchedEffect(Unit) { repo.blackoutEnabled.collect { blackoutOn = it } }
    var openLinksExternallyOn by remember { mutableStateOf(SettingsCache.openLinksExternally) }
    LaunchedEffect(Unit) { repo.openLinksExternally.collect { openLinksExternallyOn = it } }

    BackHeader(S.settingsEditorTitle) { onRoute(SettingsRoute.Root) }
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.markdownFormattingTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = markdownEnabled,
                onCheckedChange = { checked ->
                    scope.launch { repo.setMarkdownEnabled(checked) }
                    SettingsCache.markdownEnabled = checked
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.richTextTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = richTextEnabled,
                onCheckedChange = { checked ->
                    scope.launch { repo.setRichTextEnabled(checked) }
                    SettingsCache.richTextEnabled = checked
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        DesktopIntegrationRows(repo)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.linksTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = linksEnabled,
                onCheckedChange = { checked ->
                    scope.launch { repo.setLinksEnabled(checked) }
                    SettingsCache.linksEnabled = checked
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.openLinksTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = openLinksExternallyOn,
                enabled = !blackoutOn,
                onCheckedChange = { turnOn ->
                    if (turnOn) onRequestOpenLinksWarning()
                    else {
                        scope.launch { repo.setOpenLinksExternally(false) }
                        SettingsCache.openLinksExternally = false
                    }
                }
            )
        }
        if (blackoutOn) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(S.openLinksBlockedByBlackout, color = onGradientMuted, fontSize = 12.sp)
        }
    }
}
