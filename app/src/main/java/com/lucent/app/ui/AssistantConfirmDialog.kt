package com.lucent.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import com.lucent.app.i18n.S

/**
 * The assistant's confirmation for an action that would change the user's notes or tasks.
 *
 * ### Why this lives at the app level and not on the Assistant screen (task 3, earlier round)
 *
 * It used to be declared inside `AssistantScreen`, which meant it could only be *seen* from the
 * Assistant tab. But generation deliberately does not belong to that screen: [AssistantController]
 * runs a turn on a process-lifetime scope precisely so a reply survives the user wandering off to
 * check a note. Those two decisions were in direct conflict, and the failure was worse than a
 * missing dialog: a mutating tool coming up while the user was on Notes parked the generation
 * coroutine on a `CompletableDeferred` waiting for an answer from a dialog that no longer existed
 * anywhere in the composition. Nothing could complete it, and the turn hung forever. Hosting it in
 * `LucentApp` gives the modal exactly the same lifetime as the generation it belongs to.
 *
 * ### Edit first, decide second (B-group task 3)
 *
 * The order used to be: approve the action, watch it run, and only then get offered a button that
 * opened the created item so you could fix it. That is backwards. By the time the editor opened,
 * the note or task genuinely existed — in the list, in the history, with its reminder scheduled —
 * so "actually, no" meant deleting something rather than declining it, and a wrong due date had
 * already been committed before anyone could read it.
 *
 * Now the dialog IS the editor, and it runs before anything is written. Every argument worth
 * reviewing ([com.lucent.app.tools.AppTools.editableArguments]) is a filled-in field: a task's
 * title, its notes, its due date, its subtasks. Nothing reaches the database until the add button
 * is pressed, so cancelling costs exactly nothing and leaves exactly nothing behind.
 *
 * Actions with nothing meaningfully editable (deleting, pinning, completing) still show no fields,
 * because for those the decision genuinely is only yes or no.
 *
 * ### Three ways out, not two
 *
 * Add, cancel, and — new — "keep refining with the assistant". The third exists because the honest
 * answer to a proposal is often neither yes nor no but "close, but let me explain". Cancelling to
 * retype the whole request is a poor answer to that, and it was the only one available. Choosing it
 * runs nothing and hands the proposal (with whatever was typed into the fields) back to the
 * assistant, which then asks what to change and carries on from there.
 *
 * ### Only the buttons decide (accidental-dismiss fix)
 *
 * This dialog appears on the model's schedule, not the user's — it can pop up mid-scroll. With the
 * default behaviour a stray touch on the scrim counted as "no" and silently cancelled an action the
 * user had actually asked for. So dismissal is disabled entirely: [DialogProperties] turns off
 * outside-tap and back-press dismissal, and `onDismissRequest` is an inert no-op as belt-and-braces.
 * A decision is always a deliberate tap on a button the user has read.
 *
 * ### Draft area hook (group A)
 *
 * The edited-but-uncommitted values live in [draft] below and nowhere else. When group A's draft
 * area (草稿区) landed, that map became what gets persisted as the draft: it is mirrored where
 * [draft] is initialised and dropped in every branch that calls `resolveConfirmation`. See
 * com.lucent.app.data.AssistantDraftBridge — implemented during integration.
 */
@Composable
fun AssistantConfirmationDialog() {
    val confirm = AssistantController.pendingConfirmation ?: return

    // Keyed on the confirmation itself so a second action in the same turn starts from ITS proposed
    // values rather than inheriting whatever was left in the previous dialog. A snapshot map, so
    // typing in one field recomposes only what it must.
    //
    // ---- GROUP A DRAFT-AREA HOOK: this map is the uncommitted draft. ----
    val draft = remember(confirm) {
        mutableStateMapOf<String, String>().apply {
            confirm.edits.forEach { put(it.key, it.value) }
        }
    }

    // INTEGRATION (B task 3 x A task 10) — mirror the proposal into the draft area for as long as
    // this dialog is open, so a crash or force-quit mid-review lands in drafts instead of losing
    // the whole proposal.
    //
    // Debounced rather than written on every keystroke: this runs inside the editor, and a database
    // write per character would make typing in the body field stutter on a phone. 700ms after the
    // user stops typing is far inside the window that matters — the risk being covered is the app
    // dying, not the app dying between two consecutive letters.
    //
    // The snapshot is taken as an immutable map INSIDE the effect key so the effect actually
    // restarts when a field changes; keying on the snapshot map itself would compare identities and
    // never re-fire.
    val ctx = LocalContext.current
    val draftSnapshot = draft.toMap()
    LaunchedEffect(confirm, draftSnapshot) {
        if (!com.lucent.app.data.AssistantDraftBridge.shouldMirror(confirm.toolName)) return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        runCatching {
            com.lucent.app.data.AssistantDraftBridge.mirror(
                ctx.applicationContext, confirm.toolName, draftSnapshot
            )
        }
    }

    val hasForm = confirm.edits.isNotEmpty()

    AlertDialog(
        // Deliberately inert: scrim taps and back presses are disabled below, so this cannot fire —
        // and keeping it a no-op guarantees that even if it somehow did, an accidental touch could
        // never count as "cancel".
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text(confirm.actionTitle) },
        text = {
            // Capped and scrollable: a create_task with notes and a subtask list is a real form, and
            // on a phone in landscape it would otherwise push its own buttons off the screen.
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(confirm.details)
                if (hasForm) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(S.confirmReviewHint, fontSize = 12.sp)
                    confirm.edits.forEach { field ->
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = draft[field.key].orEmpty(),
                            onValueChange = { draft[field.key] = it },
                            label = { Text(field.label) },
                            // A body or a subtask list is a paragraph; a title is one line. Neither
                            // is ever clipped invisibly at the right edge, which is how someone ends
                            // up approving text they could not read.
                            singleLine = false,
                            maxLines = if (field.multiline) 8 else 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (field.multiline) Modifier.heightIn(min = 96.dp) else Modifier
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AssistantController.resolveConfirmation(
                    approved = true,
                    edits = draft.toMap()
                )
            }) { Text(if (hasForm) S.confirmAddIt else S.actionConfirm) }
        },
        dismissButton = {
            // Two choices share this slot: decline outright, or park it and keep talking. They are
            // stacked rather than laid out in a row because "keep refining with the assistant" is
            // too long to sit beside two other buttons on a phone without one of them wrapping to
            // an unreadable stub.
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(onClick = {
                    AssistantController.resolveConfirmation(
                        approved = false,
                        edits = draft.toMap(),
                        refine = true
                    )
                }) { Text(S.confirmKeepRefining) }
                TextButton(onClick = { AssistantController.resolveConfirmation(approved = false) }) {
                    Text(S.actionCancel)
                }
            }
        }
    )
}
