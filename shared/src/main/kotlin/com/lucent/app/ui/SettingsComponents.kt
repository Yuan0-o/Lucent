package com.lucent.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.BackupManager

/**
 * Small settings-screen building blocks shared verbatim by the Android and Windows settings pages
 * (P0-2: single source instead of one private copy per platform). Rows, headers, steppers,
 * module-selection rows and the export/import picker dialogs. Behaviour is unchanged; each block
 * was byte-identical in the two SettingsScreen files before this extraction.
 */
internal fun specLabel(spec: String): String = when (spec) {
    "anthropic" -> "Anthropic"
    "google" -> "Google"
    else -> "OpenAI"
}

/** A small rounded chip previewing a palette as a horizontal gradient of its colours. */
@Composable
internal fun PaletteSwatch(colors: List<Color>) {
    val preview = if (colors.size >= 2) colors else listOf(
        colors.firstOrNull() ?: Color.Gray,
        colors.firstOrNull() ?: Color.Gray
    )
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .width(44.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.horizontalGradient(preview))
    )
}


/**
 * A labelled -/+ stepper for a small bounded integer (C-group task 18's attempt limits).
 *
 * A stepper rather than a text field because every value these settings take is a single digit or
 * two, and a keyboard for that is more work than the setting is worth — and because a text field
 * would need its own validation for "", "-3" and "999999", all of which a stepper makes
 * unrepresentable. [range] is enforced here as well as in the repository: the UI should not offer a
 * value the data layer would silently clamp, or the number shown stops matching the number stored.
 */
@Composable
internal fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    onChange: (Int) -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = onGradient, fontSize = 14.sp, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onChange((value - step).coerceIn(range)) },
            enabled = value > range.first
        ) {
            Icon(Icons.Default.Remove, contentDescription = null, tint = onGradient)
        }
        Text(
            value.toString(),
            color = onGradient,
            fontSize = 15.sp,
            modifier = Modifier.widthIn(min = 32.dp),
            textAlign = TextAlign.Center
        )
        IconButton(
            onClick = { onChange((value + step).coerceIn(range)) },
            enabled = value < range.last
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = onGradientMuted)
        }
    }
}

@Composable
internal fun NavCard(title: String, subtitle: String, onClick: () -> Unit) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(title, color = onGradient, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, color = onGradientMuted, fontSize = 13.sp)
    }
}

@Composable
internal fun BackHeader(title: String, onBack: () -> Unit) {
    val onGradient = LocalOnGradient.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S.actionBack, tint = onGradient)
        }
        Text(title, color = onGradient, fontSize = 20.sp)
    }
    Spacer(modifier = Modifier.height(8.dp))
}

/** One selectable memory-tier row: a radio button, the tier's name, and its cost explanation (issue 9). */
@Composable
internal fun MemoryTierRow(
    selected: Boolean,
    title: String,
    detail: String,
    onGradient: Color,
    onGradientMuted: Color,
    onClick: () -> Unit,
    // [dimmed] fades the row to show it can't be chosen right now, WITHOUT making it inert: the
    // click still fires so the caller can say why (task 8). Disabling the controls outright would
    // have been less code and a worse answer — the user's question is "why is this grey?", and only
    // a control that still responds can answer it.
    dimmed: Boolean = false
) {
    val fade = if (dimmed) 0.38f else 1f
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp)) {
        // Kept ENABLED even when dimmed. A disabled RadioButton can swallow the touch before the
        // row's clickable ever sees it, which is exactly the dead control this is trying to avoid;
        // the fade carries the "unavailable" meaning and onClick carries the explanation.
        RadioButton(selected = selected && !dimmed, onClick = onClick, modifier = Modifier.alpha(fade))
        Column(modifier = Modifier.padding(start = 4.dp, top = 4.dp).alpha(fade)) {
            Text(title, color = onGradient)
            Text(detail, color = onGradientMuted, fontSize = 12.sp)
        }
    }
}

/**
 * One selectable section in the backup / restore dialogs (task 9).
 *
 * A plain labelled checkbox, with the whole row clickable rather than just the box. That is not
 * politeness — these rows sit in a scrolling dialog on a phone, a checkbox is below the size anyone
 * can hit reliably while a list is still settling, and a mis-tap here is the difference between
 * backing up your API keys and not.
 *
 * The caller owns the set and is handed a new one, so the same component drives both dialogs without
 * either sharing state with the other — an export selection must never leak into a restore, since
 * the same words mean opposite things in the two directions.
 */
@Composable
internal fun BackupModuleRow(
    label: String,
    module: BackupManager.BackupModule,
    selected: Set<BackupManager.BackupModule>,
    subLabel: String? = null,
    onChooseItems: (() -> Unit)? = null,
    onChange: (Set<BackupManager.BackupModule>) -> Unit
) {
    val checked = module in selected
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clickable { onChange(if (checked) selected - module else selected + module) }
        ) {
            Checkbox(
                checked = checked,
                // Null, not a duplicate handler: the row above already owns the toggle, and letting
                // the box handle its own tap as well is how a fast double-tap cancels itself.
                onCheckedChange = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, fontSize = 14.sp)
                // Only shown for a partial selection, and it is the reason the second level is
                // safe to offer at all: a backup missing most of your notes must say so on the
                // screen where you press Export, not only inside a sub-menu you may never reopen.
                if (subLabel != null) Text(subLabel, fontSize = 11.sp)
            }
        }
        // The drill-in is a separate hit target from the tick, because they do opposite things:
        // one decides whether this section travels at all, the other decides what is in it.
        // Offered only while the module is actually ticked — choosing which notes to include in a
        // section you have just excluded is a menu that cannot mean anything.
        if (onChooseItems != null && checked) {
            TextButton(onClick = onChooseItems) { Text(S.backupChooseItems, fontSize = 13.sp) }
        }
    }
}

/** Which list the second-level backup picker is showing. */
internal enum class ExportItemKind { NOTES, TASKS, CHATS, API }

/**
 * The second-level picker: every note (or task), each with a tick, plus all/none shortcuts.
 *
 * Deliberately a flat list of titles and nothing else. This is a dialog for answering "is this one
 * in or out", and previews, dates or tags would make each row taller without making that question
 * easier — on a list of two hundred notes, height is the scarce resource. Titles are shown exactly
 * as stored, with the same "(untitled)" fallback the rest of the app uses, so an item is never
 * represented by a blank row that cannot be identified or reasoned about.
 */
@Composable
internal fun ExportItemPickerDialog(
    title: String,
    items: List<Pair<Long, String>>,
    selected: Set<Long>,
    onDone: (Set<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(items) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row {
                    TextButton(onClick = { draft = items.map { it.first }.toSet() }) {
                        Text(S.selectAll, fontSize = 13.sp)
                    }
                    TextButton(onClick = { draft = emptySet() }) {
                        Text(S.clearAllSelection, fontSize = 13.sp)
                    }
                }
                Text(S.backupNOfM(draft.size, items.size), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (items.isEmpty()) {
                    Text(S.backupNothingToPick, fontSize = 13.sp)
                } else {
                    // Lazy, not a scrolled Column: a database with a few hundred notes would
                    // otherwise compose every row up front to open a dialog.
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(items, key = { it.first }) { (id, label) ->
                            val checked = id in draft
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { draft = if (checked) draft - id else draft + id }
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(draft) }) { Text(S.actionDone) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.actionCancel) } }
    )
}

/**
 * The import-time cap resolver: when the API profiles a restore would add don't all fit under
 * [ApiProfiles.MAX], this asks which of the newcomers to keep rather than silently dropping the
 * overflow. [incoming] is only the profiles that would actually take a new slot — a name already
 * saved on the device is kept by the merge and never appears here. [canAdd] is how many slots are
 * free; when it is zero there is nothing to choose and the dialog just explains why, returning an
 * empty set so the rest of the restore still proceeds.
 *
 * Selection is held by NAME, the same handle the export and preview pickers use. The tick count can
 * never exceed [canAdd]: at the cap an unticked row simply can't be ticked until another is freed,
 * so the caller always receives a set that fits.
 */
@Composable
internal fun ApiImportLimitDialog(
    incoming: List<String>,
    canAdd: Int,
    max: Int,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    // Pre-fill up to the cap in file order, so tapping straight through still imports a full,
    // valid batch rather than nothing.
    var draft by remember(incoming, canAdd) {
        mutableStateOf(incoming.take(canAdd.coerceAtLeast(0)).toSet())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.backupPickApiTitle) },
        text = {
            Column {
                if (canAdd <= 0) {
                    Text(S.backupImportApiFull(max), fontSize = 13.sp)
                } else {
                    Text(S.backupImportApiLimit(canAdd, max), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(S.backupNOfM(draft.size, incoming.size), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(incoming, key = { it }) { name ->
                            val checked = name in draft
                            // At the cap an unticked row is inert until a slot is freed; ticking it is
                            // ignored so the selection can never exceed what will fit.
                            val blocked = !checked && draft.size >= canAdd
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = checked || !blocked) {
                                        draft = if (checked) draft - name else draft + name
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(checked = checked, enabled = checked || !blocked, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    name.ifBlank { S.backupModApi },
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        // With no room, the only honest action is to acknowledge and import none.
        confirmButton = {
            TextButton(onClick = { onDone(if (canAdd <= 0) emptySet() else draft) }) {
                Text(if (canAdd <= 0) S.actionDone else S.actionRestore)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.actionCancel) } }
    )
}

/**
 * One line of the "here's what's in this backup" list.
 *
 * A zero renders nothing at all. A restore preview listing "0 chat messages, 0 attachments" is
 * technically complete and practically noise — the point of the screen is to let someone see, at a
 * glance, what is about to arrive, and padding it with everything that *isn't* there makes that
 * harder, not easier.
 */
@Composable
internal fun BackupContentLine(label: String, count: Int, details: List<String>) {
    if (count <= 0) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$count", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(52.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp)
            if (details.isNotEmpty()) {
                Text(details.joinToString(", "), fontSize = 12.sp)
            }
        }
    }
}
