package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.Attachment

@Composable
fun <T> ExportSelectionScreen(
    title: String,
    items: List<T>,
    id: (T) -> Long,
    label: (T) -> String,
    subtitle: (T) -> String,
    timestamp: (T) -> Long,
    searchText: (T) -> String,
    attachmentsOf: (T) -> List<Attachment>,
    doodlesOf: (T) -> List<com.lucent.app.data.DoodleExport.Canvas> = { emptyList() },
    onExport: (List<T>, com.lucent.app.data.ExportFormat, List<Attachment>, List<com.lucent.app.data.DoodleExport.Canvas>) -> Unit,
    onBack: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedAttachmentKeys by remember { mutableStateOf(setOf<String>()) }
    var selectedFormat by remember { mutableStateOf(com.lucent.app.data.ExportFormat.MARKDOWN) }
    var viewingAttachment by remember { mutableStateOf<Attachment?>(null) }

    fun attKey(itemId: Long, name: String) = com.lucent.app.data.DoodleExport.attachmentKey(itemId, name)
    fun canvasKey(itemId: Long, index: Int) = com.lucent.app.data.DoodleExport.canvasKey(itemId, index)

    fun keysUnder(item: T): List<String> =
        attachmentsOf(item).map { attKey(id(item), it.name) } +
            doodlesOf(item).map { canvasKey(id(item), it.index) }

    val ordered = remember(items) { items.sortedByDescending { timestamp(it) } }
    val visible = remember(ordered, query) {
        if (query.isBlank()) ordered
        else ordered.filter { searchText(it).contains(query.trim(), ignoreCase = true) }
    }
    val visibleIds = visible.map(id).toSet()
    val allVisibleSelected = visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)

    val selectableAttachmentKeys: Set<String> = ordered
        .filter { id(it) in selectedIds }
        .flatMap { item -> keysUnder(item) }
        .toSet()
    val allAttachmentsSelected =
        selectableAttachmentKeys.isNotEmpty() &&
            selectedAttachmentKeys.containsAll(selectableAttachmentKeys)

    fun deselectItem(itemId: Long) {
        selectedIds = selectedIds - itemId
        val prefix = "$itemId\u0000"
        selectedAttachmentKeys = selectedAttachmentKeys.filterNot { it.startsWith(prefix) }.toSet()
    }

    fun toggleItem(itemId: Long) {
        if (itemId in selectedIds) deselectItem(itemId) else selectedIds = selectedIds + itemId
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.lucent.app.i18n.S.actionBack, tint = onGradient)
            }
            Text(title, color = onGradient, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    val chosen = ordered.filter { id(it) in selectedIds }
                    val chosenAttachments = chosen.flatMap { item ->
                        attachmentsOf(item).filter { attKey(id(item), it.name) in selectedAttachmentKeys }
                    }
                    val chosenCanvases = chosen.flatMap { item ->
                        doodlesOf(item).filter { canvasKey(id(item), it.index) in selectedAttachmentKeys }
                    }
                    if (chosen.isNotEmpty()) onExport(chosen, selectedFormat, chosenAttachments, chosenCanvases)
                },
                enabled = selectedIds.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(if (selectedIds.isEmpty()) com.lucent.app.i18n.S.actionExport else com.lucent.app.i18n.S.exportNSelected(selectedIds.size))
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    if (allVisibleSelected) visibleIds.forEach { deselectItem(it) }
                    else selectedIds = selectedIds + visibleIds
                }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = allVisibleSelected, onCheckedChange = {
                if (allVisibleSelected) visibleIds.forEach { deselectItem(it) }
                else selectedIds = selectedIds + visibleIds
            })
            Text(
                if (query.isBlank()) com.lucent.app.i18n.S.selectAll else com.lucent.app.i18n.S.selectAllMatching,
                color = onGradient,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            Text(com.lucent.app.i18n.S.nSelected(selectedIds.size), color = onGradientMuted, fontSize = 13.sp)
        }

        if (selectableAttachmentKeys.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        selectedAttachmentKeys =
                            if (allAttachmentsSelected) selectedAttachmentKeys - selectableAttachmentKeys
                            else selectedAttachmentKeys + selectableAttachmentKeys
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = allAttachmentsSelected, onCheckedChange = {
                    selectedAttachmentKeys =
                        if (allAttachmentsSelected) selectedAttachmentKeys - selectableAttachmentKeys
                        else selectedAttachmentKeys + selectableAttachmentKeys
                })
                Text(
                    com.lucent.app.i18n.S.selectAllAttachments,
                    color = onGradient,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                Text(
                    com.lucent.app.i18n.S.nAttachmentsSelected(
                        selectedAttachmentKeys.count { it in selectableAttachmentKeys }
                    ),
                    color = onGradientMuted,
                    fontSize = 13.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current)
        ) {
            items(visible, key = { id(it) }) { item ->
                val checked = id(item) in selectedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { toggleItem(id(item)) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { toggleItem(id(item)) })
                    Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(label(item).ifBlank { com.lucent.app.i18n.S.untitled }, color = onGradient, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = subtitle(item)
                        if (sub.isNotBlank()) {
                            Text(sub, color = onGradientMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (checked) {
                    val atts = attachmentsOf(item)
                    val canvases = doodlesOf(item)
                    if (atts.isNotEmpty() || canvases.isNotEmpty()) {
                        val here = keysUnder(item)
                        val allHere = here.isNotEmpty() && selectedAttachmentKeys.containsAll(here)
                        fun toggleHere() {
                            selectedAttachmentKeys =
                                if (allHere) selectedAttachmentKeys - here.toSet()
                                else selectedAttachmentKeys + here
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { toggleHere() }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = allHere, onCheckedChange = { toggleHere() })
                            Text(
                                com.lucent.app.i18n.S.exportSelectAllHere,
                                color = onGradient,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f).padding(start = 4.dp)
                            )
                            Text(
                                com.lucent.app.i18n.S.nAttachmentsSelected(
                                    here.count { it in selectedAttachmentKeys }
                                ),
                                color = onGradientMuted,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            com.lucent.app.i18n.S.exportAttachmentsHint,
                            color = onGradientMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 44.dp, bottom = 2.dp)
                        )
                        if (canvases.isNotEmpty()) {
                            Text(
                                com.lucent.app.i18n.S.exportDoodleHint,
                                color = onGradientMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 44.dp, bottom = 2.dp)
                            )
                            canvases.forEach { canvas ->
                                val key = canvasKey(id(item), canvas.index)
                                val canvasChecked = key in selectedAttachmentKeys
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 44.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (canvasChecked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (canvasChecked) onGradient else onGradientMuted,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                selectedAttachmentKeys =
                                                    if (canvasChecked) selectedAttachmentKeys - key
                                                    else selectedAttachmentKeys + key
                                            }
                                    )
                                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(
                                            com.lucent.app.i18n.S.exportDoodleCanvas(canvas.index + 1),
                                            color = onGradient,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            canvas.fileName,
                                            color = onGradientMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        atts.forEach { att ->
                            val key = attKey(id(item), att.name)
                            val attChecked = key in selectedAttachmentKeys
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 44.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (attChecked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (attChecked) onGradient else onGradientMuted,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            selectedAttachmentKeys =
                                                if (attChecked) selectedAttachmentKeys - key
                                                else selectedAttachmentKeys + key
                                        }
                                )
                                Text(
                                    att.name,
                                    color = onGradient,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp)
                                        .clickable { viewingAttachment = att }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedFormat == com.lucent.app.data.ExportFormat.PDF) {
            rememberExportPdfFontHint()?.let { hint ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(hint, color = onGradientMuted, fontSize = 12.sp)
            }
        }

        Text(com.lucent.app.i18n.S.labelFormat, color = onGradientMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.lucent.app.data.ExportFormat.entries.forEach { fmt ->
                FilterChip(
                    selected = selectedFormat == fmt,
                    onClick = { selectedFormat = fmt },
                    label = { Text(fmt.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(LocalBottomBarInset.current))
    }

    viewingAttachment?.let { att ->
        AttachmentViewerDialog(att = att, onDismiss = { viewingAttachment = null })
    }
}
