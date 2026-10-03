package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import com.lucent.app.data.Attachment
import com.lucent.app.platform.PlatformContext
import com.lucent.desktop.platform.DesktopFiles
import com.lucent.desktop.platform.DesktopShare
import com.lucent.desktop.platform.LucentDateTimePickerFlow

typealias PlatformPickedFile = java.io.File

@Composable
fun rememberAttachmentFilePicker(onPicked: (List<PlatformPickedFile>) -> Unit): () -> Unit = {
    val files = DesktopFiles.openFiles()
    if (files.isNotEmpty()) onPicked(files)
}

fun attachmentSizeHint(context: PlatformContext, source: PlatformPickedFile): Long = source.length()

fun pickedFileToAttachment(context: PlatformContext, source: PlatformPickedFile): Attachment? =
    fileToAttachment(context, source)

fun templateToastContext(context: PlatformContext): PlatformContext = context.applicationContext

@Composable
fun OnAppHidden(action: () -> Unit) {
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(isWindowFocused) {
        if (!isWindowFocused) action()
    }
}

fun shareText(context: PlatformContext, subject: String?, text: String, chooserTitle: String) =
    DesktopShare.shareText(context, subject = subject, text = text)

val notesGridColumns: Int = 4

@Composable
fun rememberNotificationPermissionRequester(): () -> Unit = {}

@Composable
fun rememberDateTimePicker(minMillis: Long, initialMillis: Long, onChange: (Long) -> Unit): () -> Unit {
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
