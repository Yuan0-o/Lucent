package com.lucent.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalWindowInfo
import com.lucent.app.data.Attachment
import com.lucent.desktop.platform.DesktopFiles
import com.lucent.desktop.platform.DesktopShare
import java.io.File

/**
 * Desktop seams for the shared [NotesScreen] (and, where noted, reused by [TasksScreen] once that
 * screen is unified too). See the Android `PlatformNotes.kt` for the full list of six seams this
 * pairs with.
 */

/** A picked attachment source on desktop: a plain [File] from the AWT file dialog. */
typealias PlatformPickedFile = File

/**
 * Desktop counterpart of Android's activity-result launcher: the native "open" dialog is synchronous,
 * so there is nothing to "remember" — the returned callback just opens it and, if the user picked at
 * least one file, calls [onPicked] immediately. Kept `@Composable` (rather than a plain function) so
 * the call shape matches the Android seam exactly and shared code doesn't need to care which platform
 * it's on.
 */
@Composable
fun rememberAttachmentFilePicker(onPicked: (List<PlatformPickedFile>) -> Unit): () -> Unit = {
    val files = DesktopFiles.openFiles()
    if (files.isNotEmpty()) onPicked(files)
}

/** A plain file's length needs no resolver round-trip the way a content Uri does. */
internal fun attachmentSizeHint(context: Context, source: PlatformPickedFile): Long = source.length()

/** Reads and imports [source] into an [Attachment], or null if it can't be read. */
internal fun pickedFileToAttachment(context: Context, source: PlatformPickedFile): Attachment? =
    fileToAttachment(context, source)

/**
 * The context a toast shown from [NotesScreen]'s "save as template" flow should use.
 *
 * That flow closes the template-authoring sheet in the same action that shows the toast, and on
 * desktop the sheet's own (dialog-scoped) context can already be on its way out by the time the
 * toast callback runs. [Context.applicationContext] outlives it; the screen's other toasts fire from
 * actions that don't tear anything down at the same moment, so they don't need this.
 */
internal fun templateToastContext(context: Context): Context = context.applicationContext

/**
 * Runs [action] when the app leaves the foreground. Desktop has no `ON_STOP`; the equivalent is the
 * window losing focus — reading [LocalWindowInfo]'s `isWindowFocused` here re-runs the effect when
 * focus flips, running [action] when focus is lost.
 */
@Composable
fun OnAppHidden(action: () -> Unit) {
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(isWindowFocused) {
        if (!isWindowFocused) action()
    }
}

/**
 * Desktop has no system share sheet; the local-first equivalent is to copy the text (with [subject]
 * as a leading line, when present) to the clipboard and confirm with a toast. Matches
 * [DesktopShare.shareText]'s own call shape exactly — this just gives shared code a bare top-level
 * `shareText(...)` that resolves to it, the same way the Android seam gives one that pops the share
 * sheet.
 */
fun shareText(context: Context, subject: String? = null, text: String) =
    DesktopShare.shareText(context, subject = subject, text = text)

/**
 * The notes screen's overflow menu has no "search everything" entry on desktop: the sidebar already
 * carries a dedicated Search destination, so repeating it here would be redundant. A deliberate
 * per-platform difference, not a missing feature — see the Android implementation.
 */
@Composable
fun NotesOverflowSearchItem(onClick: () -> Unit) {
    // Intentionally empty.
}

/**
 * Notes grid columns on the desktop's wide window. Four across reads as a proper board on a large
 * monitor rather than a phone list stretched sideways (Android stays at two).
 */
internal val notesGridColumns: Int = 4
