package com.lucent.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lucent.app.harness.HarnessAsk
import com.lucent.app.i18n.S
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object HarnessAskPrompt {

    suspend fun ask(question: String, options: List<String>): String =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                runCatching { HarnessAsk.respond("") }
            }
            HarnessAsk.request(question, options) { answer ->
                if (continuation.isActive) continuation.resume(answer)
            }
        }
}

@Composable
fun HarnessAskDialog() {
    val request by HarnessAsk.pending.collectAsState(initial = null)
    val pending = request ?: return
    var typed by remember(pending.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { HarnessAsk.respond("") },
        title = { Text(pending.question) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                pending.options.forEach { option ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { HarnessAsk.respond(option) }
                    ) {
                        Text(option)
                    }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(S.fieldAnswerOptional) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { HarnessAsk.respond(typed.trim()) },
                enabled = typed.isNotBlank()
            ) {
                Text(S.actionConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = { HarnessAsk.respond("") }) {
                Text(S.actionCancel)
            }
        }
    )
}
