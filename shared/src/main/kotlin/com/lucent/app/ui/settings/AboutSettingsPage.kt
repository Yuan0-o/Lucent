package com.lucent.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.LucentBuild
import com.lucent.app.data.AutoUpdate
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun AboutSettingsPage(
    repo: SettingsRepository,
    onRoute: (SettingsRoute) -> Unit,
    onOpenUrl: ((String) -> Unit)? = null,
    versionName: String = LucentBuild.VERSION,
    buildNumber: String = LucentBuild.BUILD_NUMBER
) {
    val scope = rememberCoroutineScope()
    val autoUpdateOn by repo.autoUpdateEnabled.collectAsState(initial = SettingsCache.autoUpdateEnabled)

    LaunchedEffect(AutoUpdate.message, AutoUpdate.offered) {
        if (AutoUpdate.message != null && AutoUpdate.offered == null) {
            delay(3000)
            AutoUpdate.report(null)
        }
    }

    BackHeader(onBack = { onRoute(SettingsRoute.Root) })
    AboutIdentityCard(versionName, buildNumber)
    Spacer(modifier = Modifier.height(12.dp))
    AboutUpdateCard(
        autoUpdateOn = autoUpdateOn,
        busy = AutoUpdate.phase != AutoUpdate.Phase.IDLE,
        status = AutoUpdate.message,
        offered = AutoUpdate.offered,
        onToggle = { checked ->
            SettingsCache.autoUpdateEnabled = checked
            scope.launch { repo.setAutoUpdateEnabled(checked) }
        },
        onCheck = { scope.launch { AutoUpdate.check(versionName, notifyWhenCurrent = true) } },
        onOpenUrl = onOpenUrl
    )
    Spacer(modifier = Modifier.height(12.dp))
    AboutFooter(
        versionName = versionName,
        buildNumber = buildNumber,
        onOpenUrl = onOpenUrl,
        onLicences = { onRoute(SettingsRoute.Licences) }
    )
}

@Composable
private fun AboutIdentityCard(versionName: String, buildNumber: String) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(LucentBuild.PRODUCT_NAME, color = onGradient, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(S.aboutTagline, color = onGradientMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text("${S.aboutVersion} v$versionName", color = onGradient, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text("${S.aboutBuild} $buildNumber", color = onGradientMuted, fontSize = 13.sp)
    }
}

@Composable
private fun AboutUpdateCard(
    autoUpdateOn: Boolean,
    busy: Boolean,
    status: String?,
    offered: com.lucent.app.data.ReleaseInfo?,
    onToggle: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onOpenUrl: ((String) -> Unit)?
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.aboutAutoUpdate, color = onGradient, fontSize = 15.sp)
                Text(S.aboutAutoUpdateDesc, color = onGradientMuted, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = autoUpdateOn, onCheckedChange = onToggle)
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onCheck, enabled = !busy) {
            Text(S.aboutCheckUpdate, color = onGradient, fontSize = 13.sp)
        }
        status?.let { line ->
            Text(line, color = onGradientMuted, fontSize = 12.sp)
        }
        offered?.let { info ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                S.updateAvailableBody(info.version),
                color = onGradientMuted,
                fontSize = 12.sp
            )
            if (onOpenUrl != null) {
                ReleaseNotesLink(url = info.releaseUrl, onOpenUrl = onOpenUrl, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AboutFooter(
    versionName: String,
    buildNumber: String,
    onOpenUrl: ((String) -> Unit)?,
    onLicences: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(LucentBuild.COPYRIGHT, color = onGradientMuted, fontSize = 11.sp)
        Text(S.aboutRights, color = onGradientMuted, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("${S.aboutDeveloper} ${LucentBuild.DEVELOPER}", color = onGradientMuted, fontSize = 11.sp)
        AboutLink("${S.aboutHomepage} github.com/Yuan0-o/Lucent", onGradient) {
            onOpenUrl?.invoke(LucentBuild.HOMEPAGE)
        }
        AboutLink("${S.aboutContact} ${LucentBuild.SUPPORT_EMAIL}", onGradient) {
            onOpenUrl?.invoke("mailto:${LucentBuild.SUPPORT_EMAIL}")
        }
        AboutLink("${S.aboutLicense}: ${S.aboutLicensesOpen}", onGradient) {
            onLicences()
        }
        AboutLink("${S.aboutPrivacy}: ${S.aboutPrivacyOpen}", onGradient) {
            onOpenUrl?.invoke(LucentBuild.privacyPage(com.lucent.app.i18n.currentLanguageKey()))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Lucent v$versionName (${S.aboutBuild} $buildNumber)",
            color = onGradientMuted.copy(alpha = 0.6f),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun AboutLink(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { onClick() }.padding(vertical = 2.dp)
    )
}
