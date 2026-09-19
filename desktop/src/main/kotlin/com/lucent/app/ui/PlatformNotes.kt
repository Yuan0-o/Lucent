package com.lucent.app.ui

import android.content.Context
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


typealias PlatformPickedFile = File

@Composable
fun rememberAttachmentFilePicker(onPicked: (List<PlatformPickedFile>) -> Unit): () -> Unit = {
    val files = DesktopFiles.openFiles()
    if (files.isNotEmpty()) onPicked(files)
}

internal fun attachmentSizeHint(context: Context, source: PlatformPickedFile): Long = source.length()

internal fun pickedFileToAttachment(context: Context, source: PlatformPickedFile): Attachment? =
    fileToAttachment(context, source)

internal fun templateToastContext(context: Context): Context = context.applicationContext

@Composable
fun OnAppHidden(action: () -> Unit) {
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(isWindowFocused) {
        if (!isWindowFocused) action()
    }
}

fun shareText(context: Context, subject: String? = null, text: String, chooserTitle: String) =
    DesktopShare.shareText(context, subject = subject, text = text)

@Composable
fun OverflowMenuSearchItem(onClick: () -> Unit) {
}

internal val notesGridColumns: Int = 4

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
