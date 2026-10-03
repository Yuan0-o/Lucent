package com.lucent.app.ui

import com.lucent.app.platform.PlatformContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import com.lucent.app.data.Attachment
import com.lucent.desktop.platform.DesktopFiles
import com.lucent.desktop.platform.DesktopShare
import com.lucent.desktop.platform.LucentDateTimePickerFlow
import java.io.File


actual typealias PlatformPickedFile = File

@Composable
actual fun rememberAttachmentFilePicker(onPicked: (List<PlatformPickedFile>) -> Unit): () -> Unit = {
    val files = DesktopFiles.openFiles()
    if (files.isNotEmpty()) onPicked(files)
}

internal actual fun attachmentSizeHint(context: PlatformContext, source: PlatformPickedFile): Long = source.length()

internal actual fun pickedFileToAttachment(context: PlatformContext, source: PlatformPickedFile): Attachment? =
    fileToAttachment(context, source)

internal actual fun templateToastContext(context: PlatformContext): PlatformContext = context.applicationContext

@Composable
actual fun OnAppHidden(action: () -> Unit) {
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(isWindowFocused) {
        if (!isWindowFocused) action()
    }
}

actual fun shareText(context: PlatformContext, subject: String? = null, text: String, chooserTitle: String) =
    DesktopShare.shareText(context, subject = subject, text = text)

internal actual val notesGridColumns: Int = 4

@Composable
actual fun rememberNotificationPermissionRequester(): () -> Unit = {}

@Composable
actual fun rememberDateTimePicker(minMillis: Long, initialMillis: Long, onChange: (Long) -> Unit): () -> Unit {
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        LucentDateTimePickerFlow(
            initialMillis = initialMillis,
            minMillis = minMillis,
            is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
            onDismiss = { showPicker = false },
            onConfirm = { millis -> showPicker = false; onChange(millis) }
        )
    }
    return { showPicker = true }
}
