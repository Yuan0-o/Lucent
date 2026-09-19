package com.lucent.app.ui

import android.content.Context
import android.provider.OpenableColumns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lucent.app.AppNavigation
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Attachment
import com.lucent.app.data.Attachments
import com.lucent.app.data.AttachmentStore
import com.lucent.app.data.Note
import com.lucent.app.data.ShareIntegration
import com.lucent.app.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ShareIntake {

    var pending by mutableStateOf<ShareIntegration.Shared?>(null)
        private set

    fun offer(shared: ShareIntegration.Shared) { pending = shared }
    fun clear() { pending = null }

    suspend fun createNote(context: Context, shared: ShareIntegration.Shared): Long {
        val db = AppDatabase.getInstance(context.applicationContext)
        val attachment = shared.streamUri?.let { importStream(context, it, shared.mime) }
        val title = deriveTitle(shared, attachment)
        val body = shared.text.orEmpty()
        val attachmentsJson = attachment?.let { Attachments.serialize(listOf(it)) } ?: "[]"
        return db.noteDao().insert(Note(title = title, body = body, attachments = attachmentsJson))
    }

    suspend fun createTask(context: Context, shared: ShareIntegration.Shared): Long {
        val db = AppDatabase.getInstance(context.applicationContext)
        val attachment = shared.streamUri?.let { importStream(context, it, shared.mime) }
        val title = deriveTitle(shared, attachment)
        val remainder = shared.text?.substringAfter('\n', "")?.trim().orEmpty()
        val attachmentsJson = attachment?.let { Attachments.serialize(listOf(it)) } ?: "[]"
        return db.taskDao().insert(Task(title = title, notes = remainder, attachments = attachmentsJson))
    }

    private fun deriveTitle(shared: ShareIntegration.Shared, attachment: Attachment?): String {
        val firstLine = shared.text?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        return when {
            firstLine.isNotEmpty() -> firstLine.take(80)
            attachment != null -> attachment.name
            else -> com.lucent.app.i18n.S.sharedDefaultTitle
        }
    }

    private fun importStream(context: Context, uri: android.net.Uri, mime: String?): Attachment? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val id = AttachmentStore.importBytes(context, bytes) ?: return null
            val resolvedMime = mime ?: context.contentResolver.getType(uri) ?: "application/octet-stream"
            Attachment(mime = resolvedMime, data = id, name = queryName(context, uri) ?: "shared")
        } catch (_: Throwable) {
            null
        }
    }

    private fun queryName(context: Context, uri: android.net.Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }
}

@Composable
fun ShareIntakeDialog() {
    val shared = ShareIntake.pending ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { ShareIntake.clear() },
        title = { Text(com.lucent.app.i18n.S.shareDialogTitle) },
        text = {
            val preview = shared.text?.take(140)
            Text(
                when {
                    !preview.isNullOrBlank() && shared.streamUri != null -> com.lucent.app.i18n.S.shareSaveTextAndFile
                    !preview.isNullOrBlank() -> com.lucent.app.i18n.S.shareSaveTextAs(preview)
                    else -> com.lucent.app.i18n.S.shareSaveFile
                }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val payload = shared
                ShareIntake.clear()
                scope.launch {
                    val id = withContext(Dispatchers.IO) { ShareIntake.createNote(context, payload) }
                    AppNavigation.openNote(id)
                }
            }) { Text(com.lucent.app.i18n.S.newNote) }
        },
        dismissButton = {
            TextButton(onClick = {
                val payload = shared
                ShareIntake.clear()
                scope.launch {
                    val id = withContext(Dispatchers.IO) { ShareIntake.createTask(context, payload) }
                    AppNavigation.openTask(id)
                }
            }) { Text(com.lucent.app.i18n.S.newTask) }
        }
    )
}
