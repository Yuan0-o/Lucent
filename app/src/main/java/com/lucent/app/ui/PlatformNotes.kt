package com.lucent.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentStore

/**
 * Android seams for the shared [NotesScreen] (and, where noted, reused by [TasksScreen] once that
 * screen is unified too). Six small, named platform differences — file picking, backgrounding,
 * toast context, sharing, the overflow-menu search item, and grid density — each kept to exactly
 * the width it needs, per P1-1's "narrow and composable-shaped, not a wide PlatformApi" guidance.
 */

/** A picked attachment source on Android: a content [Uri] from the system file picker. */
typealias PlatformPickedFile = Uri

/**
 * Registers an Android activity-result launcher for picking multiple files, and returns a callback
 * that fires it. [onPicked] runs only when at least one file was actually chosen (a cancelled picker
 * calls back with an empty list, which this filters out — matching the original launcher's own
 * `if (uris.isNotEmpty())` guard).
 */
@Composable
fun rememberAttachmentFilePicker(onPicked: (List<PlatformPickedFile>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    return { launcher.launch("*/*") }
}

/** Best-effort size of a picked source without opening it, for the pre-write size check. */
internal fun attachmentSizeHint(context: Context, source: PlatformPickedFile): Long =
    AttachmentStore.sizeHint(context, source)

/** Reads and imports [source] into an [Attachment], or null if it can't be read. */
internal fun pickedFileToAttachment(context: Context, source: PlatformPickedFile): Attachment? =
    uriToAttachment(context, source)

/**
 * The context a toast shown from [NotesScreen]'s "save as template" flow should use. Plain
 * [context] on Android; see the desktop implementation for why desktop needs its application
 * context here specifically, while other toasts in the same screen don't.
 */
internal fun templateToastContext(context: Context): Context = context

/**
 * Runs [action] when the app leaves the foreground (screen off, home, another app) — used to
 * collapse the notes screen's action cluster so reopening finds it tucked away rather than as it
 * was left. `ON_STOP` fires when the activity is no longer visible, which covers all of those
 * without touching any tab-switching state.
 */
@Composable
fun OnAppHidden(action: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) action()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * Android counterpart to desktop's `DesktopShare.shareText` — pops the system share sheet via a
 * plain ACTION_SEND, matching that function's call shape so a screen can write
 * `shareText(context, subject = ..., text = ...)` once and have it work on both platforms. Entirely
 * local: no account, no Lucent server, no link that outlives the tap.
 *
 * [subject] currently always uses the notes-specific chooser label ([com.lucent.app.i18n.S.shareNoteChooser]);
 * if this is reused from a non-notes screen (e.g. once TasksScreen is unified) and a different
 * chooser title is wanted, that's the point to add a parameter rather than a second function.
 */
fun shareText(context: Context, subject: String? = null, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(sendIntent, com.lucent.app.i18n.S.shareNoteChooser))
}

/**
 * The notes screen's overflow-menu "search everything" entry. Present on Android because this menu
 * is the only route to global search here. Must be called from within a [androidx.compose.material3.DropdownMenu]'s
 * content.
 */
@Composable
fun NotesOverflowSearchItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(com.lucent.app.i18n.S.searchEverything) },
        leadingIcon = { Icon(Icons.Default.TravelExplore, contentDescription = null) },
        onClick = onClick
    )
}

/** Notes grid columns on a phone-width Android screen. */
internal val notesGridColumns: Int = 2
