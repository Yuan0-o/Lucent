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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lucent.app.data.ChecklistItem

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
    addRowModifier: Modifier = Modifier,
    onInsertAfter: ((ChecklistItem) -> Unit)? = null
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var expandedItemId by remember { mutableStateOf<String?>(null) }
    var expandingNewItem by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = item.done,
                    onCheckedChange = { onToggle(item) },
                    modifier = Modifier.padding(top = CHECKBOX_FIRST_LINE_OFFSET)
                )
                val itemColor = if (item.done) onGradientMuted else onGradient
                OutlinedTextField(
                    value = item.text,
                    onValueChange = { onEditText(item, it) },
                    singleLine = false,
                    maxLines = 6,
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
                    modifier = Modifier
                        .weight(1f)
                        .commitOnEnter(enabled = onInsertAfter != null) { onInsertAfter?.invoke(item) }
                )
                if (onInsertAfter != null) {
                    IconButton(
                        onClick = { onInsertAfter(item) },
                        modifier = Modifier.size(32.dp).padding(top = ICON_FIRST_LINE_OFFSET)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = com.lucent.app.i18n.S.checklistInsertBelow,
                            tint = onGradientMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { expandedItemId = item.id },
                    modifier = Modifier.size(32.dp).padding(top = ICON_FIRST_LINE_OFFSET)
                ) {
                    Icon(
                        Icons.Default.OpenInFull,
                        contentDescription = com.lucent.app.i18n.S.expandTextBox,
                        tint = onGradientMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { onRemove(item) },
                    modifier = Modifier.size(36.dp).padding(top = ICON_FIRST_LINE_OFFSET)
                ) {
                    Icon(Icons.Default.Close, contentDescription = com.lucent.app.i18n.S.checklistRemoveA11y(item.text), tint = onGradientMuted)
                }
            }
        }
        if (items.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top, modifier = addRowModifier) {
            OutlinedTextField(
                value = newItemText,
                onValueChange = onNewItemTextChange,
                label = { Text(addLabel) },
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (newItemText.isNotBlank()) onAdd() }),
                modifier = Modifier
                    .weight(1f)
                    .commitOnEnter(enabled = newItemText.isNotBlank()) { onAdd() }
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = { expandingNewItem = true },
                modifier = Modifier.size(32.dp).padding(top = ADD_ROW_ICON_OFFSET)
            ) {
                Icon(
                    Icons.Default.OpenInFull,
                    contentDescription = com.lucent.app.i18n.S.expandTextBox,
                    tint = onGradientMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = { if (newItemText.isNotBlank()) onAdd() },
                modifier = Modifier.padding(top = ADD_ROW_ICON_OFFSET)
            ) {
                Icon(Icons.Default.Add, contentDescription = addLabel, tint = onGradient)
            }
        }
    }
    expandedItemId?.let { id ->
        items.firstOrNull { it.id == id }?.let { item ->
            ChecklistItemEditorDialog(
                text = item.text,
                onTextChange = { onEditText(item, it) },
                onClose = { expandedItemId = null }
            )
        }
    }
    if (expandingNewItem) {
        ChecklistItemEditorDialog(
            text = newItemText,
            onTextChange = onNewItemTextChange,
            onClose = { expandingNewItem = false }
        )
    }
}

private val CHECKBOX_FIRST_LINE_OFFSET = 4.dp
private val ICON_FIRST_LINE_OFFSET = 10.dp

private val ADD_ROW_ICON_OFFSET = 18.dp

private fun Modifier.commitOnEnter(enabled: Boolean, onCommit: () -> Unit): Modifier =
    this.onPreviewKeyEvent { event ->
        val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
        if (event.type == KeyEventType.KeyDown && isEnter && !event.isShiftPressed) {
            if (enabled) onCommit()
            true
        } else {
            false
        }
    }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = noRipple, indication = null) { onClose() },
            contentAlignment = Alignment.Center
        ) {
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Checkbox(
                    checked = item.done,
                    onCheckedChange = onToggle?.let { toggle -> { checked -> toggle(item, checked) } }
                )
                Text(
                    item.text.ifBlank { com.lucent.app.i18n.S.checklistEmptyItem },
                    color = if (item.done) onGradientMuted else onGradient,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f).padding(top = VIEW_FIRST_LINE_OFFSET)
                )
            }
        }
    }
}

private val VIEW_FIRST_LINE_OFFSET = 12.dp

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
