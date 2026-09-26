package com.lucent.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class DirectoryEntry(val name: String, val path: String)

internal object DirectoryBrowse {

    fun normalize(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return trimmed
        val cut = trimmed.trimEnd('/', '\\')
        return cut.ifEmpty { trimmed.substring(0, 1) }
    }

    fun parent(path: String): String? {
        val clean = normalize(path)
        if (clean.isEmpty()) return null
        val file = File(clean)
        val up = file.parentFile ?: return null
        if (up.path == file.path) return null
        return up.path
    }

    fun isRoot(path: String): Boolean = parent(path) == null

    fun child(path: String, name: String): String = File(normalize(path), name).path

    fun hidden(name: String): Boolean = name.startsWith(".")

    fun list(path: String, showHidden: Boolean): List<DirectoryEntry>? {
        val dir = File(normalize(path))
        val children = dir.listFiles() ?: return null
        return children
            .asSequence()
            .filter { it.isDirectory && (showHidden || !hidden(it.name)) }
            .map { DirectoryEntry(it.name, it.path) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .toList()
    }

    fun create(parent: String, name: String): String? {
        if (name.isBlank()) return null
        val target = File(normalize(parent), name.trim())
        if (target.exists()) return target.path
        return if (target.mkdirs()) target.path else null
    }

    fun roots(): List<DirectoryEntry> = File.listRoots()
        .orEmpty()
        .map { DirectoryEntry(it.path, it.path) }

    fun home(): String = runCatching { HarnessRuntime.defaultWorkspace().path }.getOrDefault("")

    fun startingPoint(wanted: String): String {
        val clean = normalize(wanted)
        if (clean.isNotEmpty() && File(clean).isDirectory) return clean
        val home = normalize(home())
        if (home.isNotEmpty() && File(home).isDirectory) return home
        val readableRoot = roots().firstOrNull { File(it.path).isDirectory }
        return readableRoot?.path ?: home.ifEmpty { File.separator }
    }
}

@Composable
internal fun DirectoryPickerDialog(
    initialPath: String,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    var current by remember { mutableStateOf(DirectoryBrowse.startingPoint(initialPath)) }
    var showHidden by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var listing by remember { mutableStateOf<List<DirectoryEntry>?>(emptyList()) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(current, showHidden) {
        listing = withContext(Dispatchers.IO) { DirectoryBrowse.list(current, showHidden) }
    }

    fun goTo(path: String) {
        current = DirectoryBrowse.normalize(path)
        editing = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(S.agentWorkspacePickTitle, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(6.dp))
                PathRow(
                    path = current,
                    editing = editing,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onToggleEditing = {
                        draft = current
                        editing = !editing
                    },
                    onSubmit = { goTo(draft) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ShortcutRow(current = current, onGo = { goTo(it) })
                Spacer(modifier = Modifier.height(6.dp))
                DirectoryList(path = current, listing = listing, onGo = { goTo(it) })
                Spacer(modifier = Modifier.height(10.dp))
                PickerActions(
                    showHidden = showHidden,
                    onToggleHidden = { showHidden = !showHidden },
                    onNewFolder = { creating = true },
                    onDismiss = onDismiss,
                    onOpen = { onOpen(current) }
                )
            }
        }
    }

    if (creating) {
        NewFolderDialog(
            parent = current,
            onDismiss = { creating = false },
            onCreate = { name ->
                DirectoryBrowse.create(current, name)?.let { goTo(it) }
                creating = false
            }
        )
    }
}

@Composable
private fun PickerActions(
    showHidden: Boolean,
    onToggleHidden: () -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onNewFolder) {
                Text(S.agentWorkspaceNewFolder, fontSize = 13.sp)
            }
            TextButton(onClick = onToggleHidden) {
                Text(
                    S.agentWorkspaceShowHidden,
                    fontSize = 13.sp,
                    color = if (showHidden) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) { Text(S.actionCancel, fontSize = 13.sp) }
            TextButton(onClick = onOpen) { Text(S.actionOpen, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun NewFolderDialog(parent: String, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.agentWorkspaceNewFolderTitle) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(S.agentWorkspaceLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name) }
            ) { Text(S.actionCreate) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.actionCancel) } }
    )
    if (parent.isBlank()) return
}

@Composable
private fun PathRow(
    path: String,
    editing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onToggleEditing: () -> Unit,
    onSubmit: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSubmit) { Text(S.actionOpen, fontSize = 13.sp) }
        } else {
            Text(
                path,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleEditing) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = S.agentWorkspaceEditPath,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ShortcutRow(current: String, onGo: (String) -> Unit) {
    val home = remember { DirectoryBrowse.home() }
    val roots = remember { DirectoryBrowse.roots() }
    val shortcuts = remember(home, roots) {
        buildList {
            if (home.isNotBlank()) add(DirectoryEntry(S.agentWorkspaceHome, home))
            roots.forEach { add(it) }
        }.distinctBy { DirectoryBrowse.normalize(it.path) }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        shortcuts.forEach { shortcut ->
            val active = DirectoryBrowse.normalize(shortcut.path) == DirectoryBrowse.normalize(current)
            TextButton(onClick = { onGo(shortcut.path) }) {
                Text(
                    shortcut.name,
                    fontSize = 12.sp,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DirectoryList(
    path: String,
    listing: List<DirectoryEntry>?,
    onGo: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (listing == null) {
            Text(
                S.agentWorkspaceUnreadable,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            val up = DirectoryBrowse.parent(path)
            if (up != null) {
                item {
                    DirectoryRow(name = "..", detail = S.agentWorkspaceUp, onClick = { onGo(up) })
                }
            }
            val entries = listing.orEmpty()
            items(entries, key = { it.path }) { entry ->
                DirectoryRow(name = entry.name, detail = null, onClick = { onGo(entry.path) })
            }
            if (listing != null && entries.isEmpty()) {
                item {
                    Text(
                        S.agentWorkspaceEmpty,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectoryRow(name: String, detail: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("\u203A", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
