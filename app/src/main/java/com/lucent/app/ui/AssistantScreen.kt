package com.lucent.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val CLOUD_STORAGE_PACKAGES = listOf(
    "com.google.android.apps.docs",
    "com.microsoft.skydrive",
    "com.dropbox.android",
    "com.box.android",
    "com.yandex.disk",
    "mega.privacy.android.app"
)

private fun newCameraCaptureUri(context: Context): Uri? = try {
    val dir = java.io.File(context.cacheDir, "camera-capture").apply { mkdirs() }
    val file = java.io.File(dir, "camera_${System.currentTimeMillis()}.jpg")
    androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
} catch (t: Throwable) {
    null
}

private fun queryFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = cursor.getString(idx)
        }
    }
    return name
}

@Composable
fun AssistantScreen(active: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft = remember { AssistantChatDraft() }

    var pendingSaveText by remember { mutableStateOf("") }
    var pendingFileSave by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    var pendingZipSave by remember { mutableStateOf<List<Pair<String, ByteArray>>?>(null) }

    suspend fun ingestAttachment(uri: Uri) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        if (mime.startsWith("image/")) {
            val prepared = uriToChatImage(context, uri)
            if (prepared != null) {
                val (outMime, base64, name) = prepared
                draft.attachments = draft.attachments + AssistantAttachmentDraft(outMime, base64, name)
            }
        } else {
            val name = queryFileName(context, uri) ?: "file"
            var overCap = false
            val bytes: ByteArray? = try {
                resolver.openInputStream(uri)?.use { stream ->
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

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        uris.forEach { uri -> scope.launch { ingestAttachment(uri) } }
    }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) scope.launch { ingestAttachment(uri) }
        }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        uris.forEach { uri -> scope.launch { ingestAttachment(uri) } }
    }

    val cloudStorageAvailable = remember(context) {
        CLOUD_STORAGE_PACKAGES.any { pkg ->
            try {
                context.packageManager.getApplicationInfo(pkg, 0)
                true
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                false
            }
        }
    }
    val cloudPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { uri -> scope.launch { ingestAttachment(uri) } }
    }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok: Boolean ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (ok && uri != null) scope.launch { ingestAttachment(uri) }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) {
            scope.launch { context.contentResolver.openOutputStream(uri)?.use { it.write(pendingSaveText.toByteArray()) } }
        }
    }

    val fileSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val payload = pendingFileSave
        if (uri != null && payload != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(payload.second) }
                } catch (_: Throwable) {
                }
            }
        }
        pendingFileSave = null
    }

    val zipSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val entries = pendingZipSave
        if (uri != null && entries != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
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
        pendingZipSave = null
    }

    AssistantChatBody(
        active = active,
        draft = draft,
        exportLocale = com.lucent.app.i18n.lucentLocale(),
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
                onClick = { close(); filePickerLauncher.launch("*/*") }
            )
            DropdownMenuItem(
                text = { Text(com.lucent.app.i18n.S.attachFromCamera) },
                leadingIcon = {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    close()
                    val uri = newCameraCaptureUri(context)
                    if (uri != null) {
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        draft.error = com.lucent.app.i18n.S.attachCameraFailed
                    }
                }
            )
            DropdownMenuItem(
                text = { Text(com.lucent.app.i18n.S.attachFromGallery) },
                leadingIcon = {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    close()
                    try {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } catch (_: android.content.ActivityNotFoundException) {
                        galleryLauncher.launch("image/*")
                    }
                }
            )
            DropdownMenuItem(
                text = { Text(com.lucent.app.i18n.S.attachFromCloud) },
                leadingIcon = {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                enabled = cloudStorageAvailable,
                onClick = { close(); cloudPickerLauncher.launch(arrayOf("*/*")) }
            )
        },
        inputModifier = { _, _ -> Modifier.height(56.dp) },
        inputSingleLine = true,
        inputMaxLines = 1,
        onSubmitted = {},
        onSaveText = { fileName, text ->
            pendingSaveText = text
            saveLauncher.launch(fileName)
        },
        onSaveFile = { fileName, bytes ->
            pendingFileSave = fileName to bytes
            fileSaveLauncher.launch(fileName)
        },
        onSaveZip = { suggestedName, entries ->
            pendingZipSave = entries
            zipSaveLauncher.launch(suggestedName)
        }
    )
}
