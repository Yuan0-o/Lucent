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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

@Composable
internal fun AboutSettingsPage(
    repo: SettingsRepository,
    onRoute: (SettingsRoute) -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val autoUpdateOn by repo.autoUpdateEnabled.collectAsState(initial = SettingsCache.autoUpdateEnabled)
    var checkResult by remember { mutableStateOf<String?>(null) }

    val appVersion = "2.9.0"
    val buildNumber = com.lucent.app.data.BuildConfig.VERSION_CODE.toString()

    BackHeader("About") { onRoute(SettingsRoute.Root) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Lucent", color = onGradient, fontSize = 22.sp)
        Text("v$appVersion (build $buildNumber)", color = onGradientMuted, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Text("Copyright \u00A9 2026-2027 Jessica Martinez", color = onGradientMuted, fontSize = 13.sp)
        Text("All rights reserved.", color = onGradientMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Text("Developer: Yuan Yifan", color = onGradient, fontSize = 15.sp)
        Text("Contact: yuan47578@gmail.com", color = onGradientMuted, fontSize = 13.sp)
        TextButton(onClick = {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Yuan0-o/Lucent"))
            context.startActivity(intent)
        }) {
            Text("GitHub: Yuan0-o/Lucent", color = onGradient, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text("Open Source Licenses", color = onGradient, fontSize = 15.sp)
        Text("This software uses code from Kotlin, Compose Multiplatform, SQLCipher, llama.cpp, and others under Apache-2.0, MIT, BSD and similar permissive licenses.", color = onGradientMuted, fontSize = 12.sp)
        Text("See THIRD-PARTY-NOTICES.md for full details.", color = onGradientMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-update", color = onGradient, fontSize = 15.sp)
                Text("Fetch & install latest release on app start", color = onGradientMuted, fontSize = 12.sp)
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
                checkResult = "Checking..."
                try {
                    val client = okhttp3.OkHttpClient()
                    val request = okhttp3.Request.Builder()
                        .url("https://api.github.com/repos/Yuan0-o/Lucent/releases/latest")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { client.newCall(request).execute() }
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val tag = org.json.JSONObject(body).optString("tag_name", "")
                        checkResult = if (tag > "v$appVersion") "New version available: $tag" else "You have the latest version ($tag)"
                    } else {
                        checkResult = "Failed to check updates (HTTP ${response.code})"
                    }
                } catch (t: Throwable) {
                    checkResult = "Error: ${t.message}"
                }
            }
        }) {
            Text("Check for updates", color = onGradient, fontSize = 13.sp)
        }
        checkResult?.let { msg ->
            Text(msg, color = onGradientMuted, fontSize = 12.sp, textAlign = TextAlign.Start)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Lucent v$appVersion (Build $buildNumber)",
            color = onGradientMuted.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}