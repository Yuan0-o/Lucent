package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Composable
internal fun AboutSettingsPage(
    repo: SettingsRepository,
    onRoute: (SettingsRoute) -> Unit,
    onOpenUrl: ((String) -> Unit)? = null
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    val autoUpdateOn by repo.autoUpdateEnabled.collectAsState(initial = SettingsCache.autoUpdateEnabled)
    var checkResult by remember { mutableStateOf<String?>(null) }

    val appVersion = "2.9.0"
    val buildNumber = "2.9.0.1"
    val appInfo = "Lucent"
    val developerName = "Yuan Yifan"
    val contactEmail = "yuan47578@gmail.com"
    val copyrightYears = "\u00A9 2026-2027 Jessica Martinez"

    BackHeader(S.settingsAboutTitle) { onRoute(SettingsRoute.Root) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(appInfo, color = onGradient, fontSize = 22.sp)
        Text("${S.aboutVersion} $appVersion (${S.aboutBuild} $buildNumber)", color = onGradientMuted, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Text("${S.aboutCopyright} $copyrightYears", color = onGradientMuted, fontSize = 13.sp)
        Text(S.aboutRights, color = onGradientMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Text("${S.aboutDeveloper}: $developerName", color = onGradient, fontSize = 15.sp)
        Text("${S.aboutContact}: $contactEmail", color = onGradientMuted, fontSize = 13.sp)
        TextButton(onClick = { onOpenUrl?.invoke("https://github.com/Yuan0-o/Lucent") }) {
            Text(S.aboutGithub, color = onGradient, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text(S.aboutLicense, color = onGradient, fontSize = 15.sp)
        Text(S.aboutLicenseDetail, color = onGradientMuted, fontSize = 12.sp)
        Text(S.aboutLicenseFull, color = onGradientMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.aboutAutoUpdate, color = onGradient, fontSize = 15.sp)
                Text(S.aboutAutoUpdateDesc, color = onGradientMuted, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = autoUpdateOn,
                onCheckedChange = { checked ->
                    scope.launch { repo.setAutoUpdateEnabled(checked) }
                    SettingsCache.autoUpdateEnabled = checked
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = {
            scope.launch {
                checkResult = "…"
                try {
                    val client = OkHttpClient()
                    val request = Request.Builder()
                        .url("https://api.github.com/repos/Yuan0-o/Lucent/releases/latest")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val tag = JSONObject(body).optString("tag_name", "")
                        checkResult = if (tag > "v$appVersion") "${S.aboutNewVersion} $tag" else "${S.aboutUpToDate} $tag"
                    } else {
                        checkResult = "${S.aboutCheckFailed} (HTTP ${response.code})"
                    }
                } catch (t: Throwable) {
                    checkResult = "${S.aboutCheckError} ${t.message}"
                }
            }
        }) {
            Text(S.aboutCheckUpdate, color = onGradient, fontSize = 13.sp)
        }
        checkResult?.let { msg ->
            Text(msg, color = onGradientMuted, fontSize = 12.sp, textAlign = TextAlign.Start)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "$appInfo v$appVersion (${S.aboutBuild} $buildNumber)",
            color = onGradientMuted.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}