package com.lucent.app.ui

import android.util.Base64
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.desktop.platform.DesktopCloudFolders
import com.lucent.desktop.platform.DesktopFiles
import com.lucent.desktop.platform.FileFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AssistantScreen(active: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft = remember { AssistantChatDraft() }
    val onGradientMuted = LocalOnGradientMuted.current
    val inputFocus = remember { FocusRequester() }

    fun pickAttachment(imagesOnly: Boolean = false, startIn: File? = null) {
        val file = DesktopFiles.openFile(
            filter = if (imagesOnly) FileFilter.IMAGES else FileFilter.ANY,
            startIn = startIn
        ) ?: return
        scope.launch {
            val mime = mimeForFileName(file.name)
            if (mime.startsWith("image/")) {
                val prepared = fileToChatImage(context, file)
                if (prepared != null) {
                    val (outMime, base64, name) = prepared
                    draft.attachments = draft.attachments + AssistantAttachmentDraft(outMime, base64, name)
                }
            } else {
                val name = file.name
                var overCap = false
                val bytes: ByteArray? = try {
                    file.inputStream().use { stream ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        var tooBig = false
                        while (true) {
                            val r = stream.read(buf)
                            if (r == -1) break
                            total += r
                            if (total > MAX_CHAT_UPLOAD_BYTES) { tooBig = true; break }
                            out.write(buf, 0, r)
                        }
                        if (tooBig) { overCap = true; null } else out.toByteArray()
                    }
                } catch (t: Throwable) { null }

                if (bytes != null) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    draft.attachments = draft.attachments + AssistantAttachmentDraft(mime, base64, name)
                }
                val asText: String? = if (bytes != null && bytes.none { it.toInt() == 0 }) {
                    try { String(bytes, Charsets.UTF_8) } catch (t: Throwable) { null }
                } else null
                if (asText != null) {
                    draft.input = if (draft.input.isBlank()) {
                        "${com.lucent.app.i18n.S.inputAttachedFile(name)}\n$asText"
                    } else {
                        "${draft.input}\n\n${com.lucent.app.i18n.S.inputAttachedFile(name)}\n$asText"
                    }
                } else if (overCap) {
                    draft.input = if (draft.input.isBlank()) {
                        com.lucent.app.i18n.S.inputAttachedFileTooLarge(name)
                    } else {
                        "${draft.input}\n\n${com.lucent.app.i18n.S.inputAttachedFileTooLarge(name)}"
                    }
                }
            }
        }
    }

    fun saveText(suggestedName: String, text: String) {
        val file = DesktopFiles.saveFile(suggestedName = suggestedName) ?: return
        scope.launch(Dispatchers.IO) {
            try { file.writeBytes(text.toByteArray()) } catch (_: Throwable) {}
        }
    }

    fun saveFileBytes(suggestedName: String, bytes: ByteArray) {
        val file = DesktopFiles.saveFile(suggestedName = suggestedName) ?: return
        scope.launch(Dispatchers.IO) {
            try { file.writeBytes(bytes) } catch (_: Throwable) {}
        }
    }

    fun saveZip(suggestedName: String, entries: List<Pair<String, ByteArray>>) {
        val file = DesktopFiles.saveFile(suggestedName = suggestedName) ?: return
        scope.launch(Dispatchers.IO) {
            try {
                file.outputStream().use { out ->
                    java.util.zip.ZipOutputStream(out).use { zip ->
                        entries.forEach { (name, bytes) ->
                            zip.putNextEntry(java.util.zip.ZipEntry(name))
                            zip.write(bytes)
                            zip.closeEntry()
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    AssistantChatBody(
        active = active,
        draft = draft,
        exportLocale = java.util.Locale.getDefault(),
        attachMenuContent = { close ->
            DropdownMenuItem(
                text = { Text(com.lucent.app.i18n.S.attachFromFiles) },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = { close(); pickAttachment() }
            )
            DropdownMenuItem(
                text = { Text(com.lucent.app.i18n.S.attachFromGallery) },
                leadingIcon = {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = { close(); pickAttachment(imagesOnly = true) }
            )
            val cloudDir = remember { DesktopCloudFolders.firstAvailable() }
            DropdownMenuItem(
                text = { Text(com.lucent.app.i18n.S.attachFromCloud) },
                leadingIcon = {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                enabled = cloudDir != null,
                onClick = { close(); pickAttachment(startIn = cloudDir) }
            )
            if (cloudDir == null) {
                Text(
                    com.lucent.app.i18n.S.attachNoCloudFolder,
                    color = onGradientMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }
        },
        inputModifier = { submit, sending ->
            Modifier
                .heightIn(min = 56.dp, max = 140.dp)
                .focusRequester(inputFocus)
                .onPreviewKeyEvent { event ->
                    val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
                    when {
                        !isEnter || event.type != KeyEventType.KeyDown -> false
                        event.isShiftPressed -> false
                        sending -> true
                        else -> { submit(); true }
                    }
                }
        },
        inputSingleLine = false,
        inputMaxLines = 6,
        onSubmitted = { inputFocus.requestFocus() },
        onSaveText = { fileName, text -> saveText(fileName, text) },
        onSaveFile = { fileName, bytes -> saveFileBytes(fileName, bytes) },
        onSaveZip = { suggestedName, entries -> saveZip(suggestedName, entries) }
    )
}
