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
    val markdownEnabled by repo.markdownEnabled.collectAsState(initial = SettingsCache.markdownEnabled)
    val richTextEnabled by repo.richTextEnabled.collectAsState(initial = SettingsCache.richTextEnabled)
    val linksEnabled by repo.linksEnabled.collectAsState(initial = SettingsCache.linksEnabled)
    val blackoutOn by repo.blackoutEnabled.collectAsState(initial = SettingsCache.blackoutEnabled)
    val openLinksExternallyOn by repo.openLinksExternally.collectAsState(initial = SettingsCache.openLinksExternally)

    BackHeader(onBack = { onRoute(SettingsRoute.Root) })
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
