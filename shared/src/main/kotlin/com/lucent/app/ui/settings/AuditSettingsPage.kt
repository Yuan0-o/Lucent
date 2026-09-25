package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.harness.AuditTrail
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute

private const val PAGE_SIZE = 40

@Composable
internal fun AuditSettingsPage(onRoute: (SettingsRoute) -> Unit) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var shown by remember { mutableStateOf(PAGE_SIZE) }
    val entries = remember(refresh) { AuditTrail.entries(context, shown) }

    BackHeader(onBack = { onRoute(SettingsRoute.Agent) })
    Column(modifier = Modifier.fillMaxWidth()) {
        Section(onGradient, onGradientMuted, S.agentAuditTitle, S.agentAuditSub) {
            if (entries.isEmpty()) {
                Text(S.agentAuditEmpty, color = onGradientMuted, fontSize = 12.sp)
            } else {
                entries.forEach { entry ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(AuditTrail.format(entry), color = onGradient, fontSize = 12.sp)
                        if (entry.arguments.isNotBlank()) {
                            Text(entry.arguments, color = onGradientMuted, fontSize = 10.sp, maxLines = 2)
                        }
                    }
                }
                if (entries.size >= shown) {
                    TextButton(onClick = { shown += PAGE_SIZE }) {
                        Text(S.actionShowMore, color = onGradient, fontSize = 13.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                TextButton(onClick = {
                    shown = PAGE_SIZE
                    refresh++
                }) {
                    Text(S.actionRefresh, color = onGradient, fontSize = 13.sp)
                }
                TextButton(onClick = {
                    AuditTrail.clear(context)
                    refresh++
                }) {
                    Text(S.actionClear, color = onGradient, fontSize = 13.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
