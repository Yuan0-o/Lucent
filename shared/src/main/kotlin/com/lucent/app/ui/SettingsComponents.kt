package com.lucent.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.BackupManager
import com.lucent.app.i18n.S

internal fun specLabel(spec: String): String = when (spec) {
    "anthropic" -> "Anthropic"
    "google" -> "Google"
    else -> "OpenAI"
}

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

@Composable
internal fun MemoryTierRow(
    selected: Boolean,
    title: String,
    detail: String? = null,
    onGradient: Color,
    onGradientMuted: Color,
    onClick: () -> Unit,
    dimmed: Boolean = false
) {
    val fade = if (dimmed) 0.38f else 1f
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp)) {
        RadioButton(selected = selected && !dimmed, onClick = onClick, modifier = Modifier.alpha(fade))
        Column(modifier = Modifier.padding(start = 4.dp, top = 4.dp).alpha(fade)) {
            Text(title, color = onGradient)
            if (!detail.isNullOrBlank()) Text(detail, color = onGradientMuted, fontSize = 12.sp)
        }
    }
}

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
                onCheckedChange = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, fontSize = 14.sp)
                if (subLabel != null) Text(subLabel, fontSize = 11.sp)
            }
        }
        if (onChooseItems != null && checked) {
            TextButton(onClick = onChooseItems) { Text(S.backupChooseItems, fontSize = 13.sp) }
        }
    }
}

internal enum class ExportItemKind { NOTES, TASKS, CHATS, API }

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

@Composable
internal fun ApiImportLimitDialog(
    incoming: List<String>,
    canAdd: Int,
    max: Int,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
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
        confirmButton = {
            TextButton(onClick = { onDone(if (canAdd <= 0) emptySet() else draft) }) {
                Text(if (canAdd <= 0) S.actionDone else S.actionRestore)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.actionCancel) } }
    )
}

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
