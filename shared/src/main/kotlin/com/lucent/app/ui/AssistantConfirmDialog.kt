package com.lucent.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.lucent.app.i18n.S

@Composable
fun AssistantConfirmationDialog() {
    val confirm = AssistantController.pendingConfirmation ?: return

    val draft = remember(confirm) {
        mutableStateMapOf<String, String>().apply {
            confirm.edits.forEach { put(it.key, it.value) }
        }
    }

    val ctx = LocalContext.current
    val draftSnapshot = draft.toMap()
    LaunchedEffect(confirm, draftSnapshot) {
        if (!com.lucent.app.data.AssistantDraftBridge.shouldMirror(confirm.toolName)) return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        runCatching {
            com.lucent.app.data.AssistantDraftBridge.mirror(
                ctx.applicationContext, confirm.toolName, draftSnapshot
            )
        }
    }

    val hasForm = confirm.edits.isNotEmpty()

    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text(confirm.actionTitle) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(confirm.details)
                if (hasForm) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(S.confirmReviewHint, fontSize = 12.sp)
                    confirm.edits.forEach { field ->
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = draft[field.key].orEmpty(),
                            onValueChange = { draft[field.key] = it },
                            label = { Text(field.label) },
                            singleLine = false,
                            maxLines = if (field.multiline) 8 else 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (field.multiline) Modifier.heightIn(min = 96.dp) else Modifier
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                TextButton(
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    onClick = {
                        AssistantController.resolveConfirmation(
                            approved = false,
                            edits = draft.toMap(),
                            refine = true
                        )
                    }
                ) { Text(S.confirmKeepRefining, maxLines = 1) }
                TextButton(
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    onClick = {
                        AssistantController.resolveConfirmation(
                            approved = true,
                            edits = draft.toMap()
                        )
                    }
                ) { Text(if (hasForm) S.confirmAddIt else S.actionConfirm, maxLines = 1) }
                TextButton(
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    onClick = { AssistantController.resolveConfirmation(approved = false) }
                ) { Text(S.actionCancel, maxLines = 1) }
            }
        },
        dismissButton = {}
    )
}
