package com.lucent.app.ui
import com.lucent.app.platform.applicationContext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import com.lucent.app.data.Attachment
import com.lucent.app.platform.PlatformContext
import com.lucent.desktop.platform.DesktopFiles
import com.lucent.desktop.platform.DesktopShare
import com.lucent.desktop.platform.LucentDateTimePickerFlow


private fun is24HourFormat(): Boolean = try {
    val df = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, java.util.Locale.getDefault())
    val pattern = (df as? java.text.SimpleDateFormat)?.toPattern().orEmpty()
    !pattern.contains('a', ignoreCase = true)
} catch (t: Throwable) {
    true
}

@Composable
actual fun rememberAttachmentFilePicker(onPicked: (List<Any>) -> Unit): () -> Unit = {
    val files = DesktopFiles.openFiles()
    if (files.isNotEmpty()) onPicked(files)
}

actual fun attachmentSizeHint(context: PlatformContext, source: Any): Long = (source as java.io.File).length()

actual fun pickedFileToAttachment(context: PlatformContext, source: Any): Attachment? =
    fileToAttachment(context, source as java.io.File)

actual fun templateToastContext(context: PlatformContext): PlatformContext = context.applicationContext

@Composable
actual fun OnAppHidden(action: () -> Unit) {
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(isWindowFocused) {
        if (!isWindowFocused) action()
    }
}

actual fun shareText(context: PlatformContext, subject: String?, text: String, chooserTitle: String) =
    DesktopShare.shareText(context, subject = subject, text = text)

actual val notesGridColumns: Int = 4

@Composable
actual fun rememberNotificationPermissionRequester(): () -> Unit = {}

@Composable
actual fun rememberDateTimePicker(minMillis: Long, initialMillis: Long, onChange: (Long) -> Unit): () -> Unit {
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        LucentDateTimePickerFlow(
            initialMillis = initialMillis,
            minMillis = minMillis,
            is24Hour = is24HourFormat(),
            onDismiss = { showPicker = false },
            onConfirm = { millis -> showPicker = false; onChange(millis) }
        )
    }
    return { showPicker = true }
}
