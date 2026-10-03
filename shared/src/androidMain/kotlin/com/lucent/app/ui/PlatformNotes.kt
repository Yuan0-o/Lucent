package com.lucent.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentStore
import com.lucent.app.platform.PlatformContext
import com.lucent.app.reminders.Notifications
import java.util.Calendar

actual typealias PlatformPickedFile = android.net.Uri

@Composable
actual fun rememberAttachmentFilePicker(onPicked: (List<PlatformPickedFile>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<android.net.Uri> ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    return { launcher.launch("*/*") }
}

internal actual fun attachmentSizeHint(context: PlatformContext, source: PlatformPickedFile): Long =
    AttachmentStore.sizeHint(context, source)

internal actual fun pickedFileToAttachment(context: PlatformContext, source: PlatformPickedFile): Attachment? =
    uriToAttachment(context, source)

internal actual fun templateToastContext(context: PlatformContext): PlatformContext = context

@Composable
actual fun OnAppHidden(action: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) action()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

actual fun shareText(context: PlatformContext, subject: String?, text: String, chooserTitle: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
}

internal actual val notesGridColumns: Int = 2

@Composable
actual fun rememberNotificationPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            LucentToast.show(context, com.lucent.app.i18n.S.notifPermissionRationale, longDuration = true)
        }
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
actual fun rememberDateTimePicker(minMillis: Long, initialMillis: Long, onChange: (Long) -> Unit): () -> Unit {
    val context = LocalContext.current
    return {
        val base = Calendar.getInstance().apply { timeInMillis = initialMillis }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val chosen = Calendar.getInstance().apply {
                    timeInMillis = base.timeInMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        chosen.set(Calendar.HOUR_OF_DAY, hour)
                        chosen.set(Calendar.MINUTE, minute)
                        chosen.set(Calendar.SECOND, 0)
                        chosen.set(Calendar.MILLISECOND, 0)
                        onChange(chosen.timeInMillis.coerceAtLeast(minMillis))
                    },
                    base.get(Calendar.HOUR_OF_DAY),
                    base.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(context)
                ).show()
            },
            base.get(Calendar.YEAR),
            base.get(Calendar.MONTH),
            base.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.minDate = minMillis }.show()
    }
}
