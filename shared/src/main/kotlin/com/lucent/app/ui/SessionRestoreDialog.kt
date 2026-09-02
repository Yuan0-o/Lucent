package com.lucent.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lucent.app.AppScope
import com.lucent.app.Screen
import com.lucent.app.data.SessionRestore
import kotlinx.coroutines.launch

/**
 * The once-per-launch "go back to where you were?" prompt (round R1, task 5).
 *
 * ### Why it is asked rather than done
 *
 * Restoring silently would be the wrong default in both directions. Someone who was mid-sentence
 * when the app died wants to land straight back in it — but someone who force-stopped Lucent
 * deliberately, or who has simply moved on since, would find the app reopening an editor they
 * thought they had left, over the page they actually came for. A one-line question costs a tap and
 * cannot be wrong either way, which is what the task asked for in the first place.
 *
 * ### Why it lives beside the draft prompt rather than in the app shell
 *
 * [DraftRestoreDialog] already solves the identical structural problem — a question that must be
 * asked once per launch, by whichever screen happens to compose first, without either tab asking it
 * twice — and it solves it with a process-level "asked" flag set during composition. Reusing that
 * shape keeps both prompts behaving the same way and keeps this out of the two platform-specific
 * app shells, where it would have had to be written twice and kept in step by hand.
 *
 * The prompt is deliberately shown by *either* home tab, regardless of which one the snapshot
 * belongs to: the app can open on Tasks with a half-written note waiting, and a prompt that only
 * the right tab could show would never appear at all. Accepting routes to the owning tab through
 * the ordinary navigation bridge, and that tab picks the snapshot up when it composes.
 */
@Composable
fun SessionRestoreDialog() {
    val context = LocalContext.current
    val snapshot = SessionRestore.pending
    var visible by remember { mutableStateOf(!SessionRestore.asked && snapshot != null) }
    if (!visible || snapshot == null) return
    SessionRestore.asked = true

    /**
     * Say no. The in-memory flag alone is not enough: the stored record outlives the process, so
     * leaving it behind would put the same question up again on the next launch after the user has
     * already answered it. Cleared on the app-lifetime IO scope rather than a composition scope
     * because the composable disappears the moment `visible` goes false.
     */
    fun decline() {
        visible = false
        SessionRestore.decline()
        AppScope.io.launch { SessionRestore.clear(context) }
    }

    val title = snapshot.title.ifBlank { com.lucent.app.i18n.S.untitled }
    AlertDialog(
        onDismissRequest = {
            // Dismissing counts as declining. The alternative — re-asking on the next tab switch —
            // turns a safety net into nagging, which is the reasoning the draft prompt already
            // settled on. Nothing is destroyed either way: an auto-parked draft is still in the
            // draft area if `autoDraft` managed to run before the process died.
            decline()
        },
        title = { Text(com.lucent.app.i18n.S.sessionRestoreTitle) },
        text = {
            Text(
                if (snapshot.kind == SessionRestore.KIND_TASK) {
                    com.lucent.app.i18n.S.sessionRestoreTaskBody(title)
                } else {
                    com.lucent.app.i18n.S.sessionRestoreNoteBody(title)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                visible = false
                SessionRestore.beginRestore(snapshot)
                // Ask for the owning tab. Routed through AppNavigation rather than set directly so
                // it goes through the same unsaved-changes guard as any other tab switch — the user
                // may already have started typing something else in the seconds before answering.
                com.lucent.app.AppNavigation.requestScreen(
                    if (snapshot.kind == SessionRestore.KIND_TASK) Screen.Tasks else Screen.Notes
                )
            }) { Text(com.lucent.app.i18n.S.sessionRestoreConfirm) }
        },
        dismissButton = {
            TextButton(onClick = { decline() }) { Text(com.lucent.app.i18n.S.sessionRestoreDismiss) }
        }
    )
}
