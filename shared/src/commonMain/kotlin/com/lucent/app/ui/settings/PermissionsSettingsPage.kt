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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.harness.Approval
import com.lucent.app.harness.HarnessConfig
import com.lucent.app.harness.HarnessPermission
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute

@Composable
internal fun PermissionsSettingsPage(onRoute: (SettingsRoute) -> Unit) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var config by remember { mutableStateOf(HarnessRuntime.config()) }

    fun update(next: HarnessConfig) {
        config = next
        HarnessRuntime.update(next)
    }

    BackHeader(onBack = { onRoute(SettingsRoute.Agent) })
    Column(modifier = Modifier.fillMaxWidth()) {
        Section(onGradient, onGradientMuted, S.agentPermissionsTitle, S.agentPermissionsSub) {
            HarnessPermission.entries.forEach { permission ->
                val current = config.approvalFor(permission)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(permission.title, color = onGradient, fontSize = 14.sp)
                        Text(permission.detail, color = onGradientMuted, fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        val next = when (current) {
                            Approval.ALLOW -> Approval.CONFIRM
                            Approval.CONFIRM -> Approval.DENY
                            Approval.DENY -> Approval.ALLOW
                        }
                        update(config.withApproval(permission, next))
                    }) {
                        Text(current.title, color = onGradient, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
