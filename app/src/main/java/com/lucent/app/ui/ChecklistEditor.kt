package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lucent.app.data.ChecklistItem

/**
 * A fully editable checklist: one row per item (checkbox, editable text, expand, remove) plus a
 * field at the bottom for adding another.
 *
 * Shared verbatim by checklist-mode *notes* and task *subtasks*. They store the same
 * [ChecklistItem] JSON (see [com.lucent.app.data.Checklist]), so the only thing that differs between
 * the two call sites is which column the result is written back to — which is exactly the kind of
 * duplication that starts identical and drifts, so it doesn't get to start.
 *
 * Each item's text is a live text field, not a static label (settings tasks B2/B3): an item that
 * cannot be reworded after it was added forces the delete-and-retype dance for every typo. Rows are
 * spaced apart (B5) so neighbouring items read as distinct entries, and every row carries the same
 * expand affordance the details box has (B8) — it opens a centred, half-screen editor for the item,
 * because a long step is exactly as unpleasant to edit through a one-line strip as a long body is.
 *
 * The add field commits on the keyboard's Done key as well as the "+" button, because typing five
 * list items and having to reach for a button between each one is the difference between a feature
 * people use and one they abandon. (And even skipping both no longer loses the text: the composers
 * fold a still-typed item in on save — see the save functions in NotesScreen/TasksScreen, task B1.)
 *
 * [addRowModifier] decorates the add row at the bottom — the Tasks composer hangs a
 * BringIntoViewRequester on it so opening a task that already has subtasks can jump straight to the
 * end of the list (task B7).
 */
@Composable
fun ChecklistEditorSection(
    items: List<ChecklistItem>,
    newItemText: String,
    onNewItemTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (ChecklistItem) -> Unit,
    onRemove: (ChecklistItem) -> Unit,
    onEditText: (ChecklistItem, String) -> Unit,
    addLabel: String,
    modifier: Modifier = Modifier,
    addRowModifier: Modifier = Modifier
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    // Which item is open in the centred half-screen editor (B8); null = none. Looked up by id on
    // every recomposition so the dialog always edits the live item, never a stale copy.
    var expandedItemId by remember { mutableStateOf<String?>(null) }
    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            // Breathing room between items (B5): without it, neighbouring one-line rows fuse into
            // a single block and it's genuinely hard to see where one step ends and the next starts.
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = item.done, onCheckedChange = { onToggle(item) })
                val itemColor = if (item.done) onGradientMuted else onGradient
                OutlinedTextField(
                    value = item.text,
                    onValueChange = { onEditText(item, it) },
                    singleLine = true,
                    placeholder = { Text(com.lucent.app.i18n.S.checklistEmptyItem, color = onGradientMuted) },
                    textStyle = LocalTextStyle.current.copy(
                        textDecoration = if (item.done) TextDecoration.LineThrough else null
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = itemColor,
                        unfocusedTextColor = itemColor,
                        cursorColor = onGradient,
                        focusedBorderColor = onGradient.copy(alpha = 0.5f),
                        unfocusedBorderColor = onGradient.copy(alpha = 0.25f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { expandedItemId = item.id },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.OpenInFull,
                        contentDescription = com.lucent.app.i18n.S.expandTextBox,
                        tint = onGradientMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = { onRemove(item) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = com.lucent.app.i18n.S.checklistRemoveA11y(item.text), tint = onGradientMuted)
                }
            }
        }
        if (items.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = addRowModifier) {
            OutlinedTextField(
                value = newItemText,
                onValueChange = onNewItemTextChange,
                label = { Text(addLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (newItemText.isNotBlank()) onAdd() }),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = { if (newItemText.isNotBlank()) onAdd() }) {
                Icon(Icons.Default.Add, contentDescription = addLabel, tint = onGradient)
            }
        }
    }
    // The half-screen item editor (B8). Rendered only while its item still exists; edits flow
    // through the same onEditText the inline field uses, so there is exactly one write path.
    expandedItemId?.let { id ->
        items.firstOrNull { it.id == id }?.let { item ->
            ChecklistItemEditorDialog(
                text = item.text,
                onTextChange = { onEditText(item, it) },
                onClose = { expandedItemId = null }
            )
        }
    }
}

/**
 * A centred, half-screen editor for one checklist item (settings task B8) — the little sibling of
 * the details box's near-full-screen editor (see ExpandableGlassTextField). Half height rather than
 * full, because a checklist item is a line or three, not a document: the smaller panel keeps the
 * sense of editing *one entry of a list* while still giving long text room to wrap.
 *
 * Built only from cross-platform Dialog APIs so the Android and desktop copies of this file stay
 * byte-identical: the platform's own dim provides the scrim, the panel gets the same opaque
 * luminance-picked backing the big editor uses, and a tap on the surrounding area (or the collapse
 * button) closes it. Edits apply live through [onTextChange]; there is nothing to "confirm",
 * exactly like the inline field and the big editor.
 */
@Composable
private fun ChecklistItemEditorDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val noRipple = remember { MutableInteractionSource() }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Full-screen transparent catcher so a tap anywhere around the centred panel dismisses,
        // on both platforms, without relying on platform-specific outside-click plumbing.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = noRipple, indication = null) { onClose() },
            contentAlignment = Alignment.Center
        ) {
            // Same opaque, luminance-picked backing as the big expanded editor: a dark panel under
            // light text, a light panel under dark text, so the item sits on a solid surface
            // instead of fighting the animated background for legibility.
            val panelSurface = if (onGradient.luminance() > 0.5f) {
                Color(0xFF20202B).copy(alpha = 0.95f)
            } else {
                Color(0xFFF4F4F8).copy(alpha = 0.95f)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.5f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(panelSurface)
                    .frostedGlass()
                    // Swallow taps so pressing inside the panel never dismisses.
                    .clickable(interactionSource = noRipple, indication = null) {}
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(com.lucent.app.i18n.S.checklistEditItem, color = onGradient, fontSize = 18.sp)
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.CloseFullscreen,
                            contentDescription = com.lucent.app.i18n.S.collapseTextBox,
                            tint = onGradientMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text(com.lucent.app.i18n.S.checklistEmptyItem, color = onGradientMuted) },
                    textStyle = LocalTextStyle.current.copy(color = onGradient),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = onGradient,
                        unfocusedTextColor = onGradient,
                        cursorColor = onGradient,
                        focusedBorderColor = onGradient.copy(alpha = 0.5f),
                        unfocusedBorderColor = onGradient.copy(alpha = 0.3f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

/**
 * A read-only checklist that ticks off in place — for a detail page, where the list is content
 * rather than something being composed.
 *
 * [onToggle] being null makes every checkbox genuinely non-interactive rather than merely
 * greyed-out, which is how a *completed* task's subtasks are shown: a finished task is locked, and a
 * checkbox that looks pressable but silently does nothing is the worst of both worlds.
 */
@Composable
fun ChecklistView(
    items: List<ChecklistItem>,
    onToggle: ((ChecklistItem, Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    header: String? = null
) {
    if (items.isEmpty()) return
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val done = items.count { it.done }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "${header ?: com.lucent.app.i18n.S.checklist} · $done/${items.size}",
            color = onGradientMuted,
            fontSize = 12.sp
        )
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.done,
                    onCheckedChange = onToggle?.let { toggle -> { checked -> toggle(item, checked) } }
                )
                Text(
                    item.text.ifBlank { com.lucent.app.i18n.S.checklistEmptyItem },
                    color = if (item.done) onGradientMuted else onGradient,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * A compact preview for a card: the first few items with shrunken checkboxes, then a "+N more"
 * line. Lets a checklist note's *shape* be visible from the grid without opening it, which is the
 * only reason to render a checklist on a card at all — the point is "three of five ticked", not the
 * items themselves.
 */
@Composable
fun ChecklistPreviewInline(
    items: List<ChecklistItem>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 3
) {
    val onGradientMuted = LocalOnGradientMuted.current
    Column(modifier = modifier) {
        items.take(maxVisible).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.done,
                    onCheckedChange = null,
                    modifier = Modifier.scale(0.7f)
                )
                Text(
                    item.text.ifBlank { com.lucent.app.i18n.S.checklistEmptyItem },
                    color = onGradientMuted,
                    fontSize = 13.sp,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (items.size > maxVisible) {
            Text(com.lucent.app.i18n.S.checklistMore(items.size - maxVisible), color = onGradientMuted, fontSize = 12.sp)
        }
    }
}
