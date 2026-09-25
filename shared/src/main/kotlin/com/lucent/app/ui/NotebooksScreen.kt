package com.lucent.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppNavigation
import com.lucent.app.AppScope
import com.lucent.app.BackClaim
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Note
import com.lucent.app.data.Notebook
import com.lucent.app.data.NotebookItem
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.Task
import com.lucent.app.data.pruneOrphans
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun NotebooksScreen(
    onBack: () -> Unit,
    onOpenNote: (Note) -> Unit,
    onOpenTask: (Task) -> Unit,
    showBack: Boolean = true,
    active: Boolean = true
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }
    val notebooks by db.notebookDao().getAll().collectAsState(initial = emptyList())
    val counts by db.notebookDao().itemCounts().collectAsState(initial = emptyList())
    val countById = remember(counts) { counts.associate { it.notebookId to it.count } }
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    val sortKey by settingsRepo.notebooksSort.collectAsState(initial = com.lucent.app.data.SettingsCache.notebooksSort ?: "recent")
    val sortOption = NotebookSort.fromKey(sortKey)

    var openNotebookId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var coverForNew by remember { mutableStateOf(NotebookColor.DEFAULT) }
    var nameForNew by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Notebook?>(null) }
    var recolouring by remember { mutableStateOf<Notebook?>(null) }
    var deleting by remember { mutableStateOf<Notebook?>(null) }
    var showTrash by remember { mutableStateOf(false) }

    var draggingNotebookId by remember { mutableStateOf<Long?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var batchDeleting by remember { mutableStateOf(false) }
    var itemTitles by remember { mutableStateOf(emptyMap<Long, List<String>>()) }
    var searchText by remember { mutableStateOf("") }
    var dateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { db.notebookDao().pruneOrphans(db.noteDao(), db.taskDao()) }
    }

    LaunchedEffect(notebooks.size) {
        itemTitles = runCatching {
            val noteTitles = db.noteDao().getAllOnce().associate { it.id to it.title }
            val taskTitles = db.taskDao().getAllOnce().associate { it.id to it.title }
            val grouped = HashMap<Long, MutableList<String>>()
            db.notebookDao().getAllItemsOnce().forEach { item ->
                val title = when (item.itemKind) {
                    NotebookItem.KIND_NOTE -> noteTitles[item.itemId]
                    NotebookItem.KIND_TASK -> taskTitles[item.itemId]
                    else -> null
                } ?: return@forEach
                grouped.getOrPut(item.notebookId) { mutableListOf() }.add(title)
            }
            grouped.mapValues { entry -> entry.value.toList() }
        }.getOrDefault(emptyMap())
    }

    val reorderState = rememberReorderDragState()
    val gridState = rememberRestoredGridState("NotebooksScreen#grid")
    val reorderSlots = rememberGridSlots(gridState)
    ReorderSettleEffect(reorderState, reorderSlots)
    val placementSpec = rememberReorderPlacementSpec()
    val reorderEnabled = true

    BackClaim(active && (selectionMode || (!showBack && (openNotebookId != null || showTrash))))
    BackHandler(enabled = active && (selectionMode || (!showBack && (openNotebookId != null || showTrash)))) {
        when {
            selectionMode -> {
                selectionMode = false
                selectedIds = emptySet()
            }
            showTrash -> showTrash = false
            else -> openNotebookId = null
        }
    }

    val open = openNotebookId
    if (open != null) {
        NotebookDetailScreen(
            notebookId = open,
            onBack = { openNotebookId = null },
            onOpenNote = onOpenNote,
            onOpenTask = onOpenTask
        )
        return
    }

    if (showTrash) {
        NotebookTrashScreen(onBack = { showTrash = false })
        return
    }

    deleting?.let { notebook ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(com.lucent.app.i18n.S.notebookDeleteTitle) },
            text = { Text(com.lucent.app.i18n.S.notebookTrashBody(notebook.title.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle })) },
            confirmButton = {
                TextButton(onClick = {
                    val target = notebook
                    deleting = null
                    AppScope.io.launch {
                        db.notebookDao().update(target.copy(trashedAt = System.currentTimeMillis()))
                        LucentToast.show(context, com.lucent.app.i18n.S.notebookDeletedToast)
                    }
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(com.lucent.app.i18n.S.actionCancel) }
            }
        )
    }

    if (batchDeleting) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { batchDeleting = false },
            title = { Text(com.lucent.app.i18n.S.notebookDeleteTitle) },
            text = { Text(com.lucent.app.i18n.S.notebookBatchTrashBody(count)) },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds
                    batchDeleting = false
                    selectionMode = false
                    selectedIds = emptySet()
                    AppScope.io.launch {
                        notebooks.filter { it.id in ids }.forEach { notebook ->
                            db.notebookDao().update(notebook.copy(trashedAt = System.currentTimeMillis()))
                        }
                    }
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = {
                TextButton(onClick = { batchDeleting = false }) { Text(com.lucent.app.i18n.S.actionCancel) }
            }
        )
    }

    if (creating) {
        NotebookEditorDialog(
            title = com.lucent.app.i18n.S.notebookNew,
            confirmLabel = com.lucent.app.i18n.S.notebookCreate,
            initialName = nameForNew,
            initialColor = coverForNew,
            onConfirm = { name, color ->
                nameForNew = ""
                coverForNew = NotebookColor.DEFAULT
                scope.launch {
                    db.notebookDao().insert(Notebook(title = name.trim(), color = color.key))
                }
            },
            onDismiss = { creating = false }
        )
    }

    renaming?.let { notebook ->
        NotebookEditorDialog(
            title = com.lucent.app.i18n.S.notebookRename,
            confirmLabel = com.lucent.app.i18n.S.notebookRenameAction,
            initialName = notebook.title,
            initialColor = NotebookColor.fromKey(notebook.color),
            showColorPicker = false,
            onConfirm = { name, _ ->
                scope.launch {
                    db.notebookDao().update(
                        notebook.copy(title = name.trim(), updatedAt = System.currentTimeMillis())
                    )
                    LucentToast.show(context, com.lucent.app.i18n.S.notebookRenamedToast)
                }
            },
            onDismiss = { renaming = null }
        )
    }

    recolouring?.let { notebook ->
        NotebookEditorDialog(
            title = com.lucent.app.i18n.S.notebookCoverTitle,
            confirmLabel = com.lucent.app.i18n.S.actionSave,
            initialName = notebook.title,
            initialColor = NotebookColor.fromKey(notebook.color),
            showNameField = false,
            onConfirm = { _, color ->
                scope.launch {
                    db.notebookDao().update(
                        notebook.copy(color = color.key, updatedAt = System.currentTimeMillis())
                    )
                }
            },
            onDismiss = { recolouring = null }
        )
    }

    val visible = remember(notebooks, searchText, dateRange, sortOption, itemTitles) {
        val trimmed = searchText.trim()
        val tokens = trimmed.split(" ").filter { it.isNotBlank() }
        val pinnedOnly = tokens.any { it.equals("is:pinned", ignoreCase = true) }
        val colourToken = tokens.firstOrNull { it.startsWith("color:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.lowercase()
        val words = tokens.filterNot { it.startsWith("is:", ignoreCase = true) || it.startsWith("color:", ignoreCase = true) }
        notebooks
            .filter { notebook ->
                val matchesPinned = !pinnedOnly || notebook.pinned
                val matchesColour = colourToken.isNullOrBlank() ||
                    NotebookColor.fromKey(notebook.color).key == colourToken ||
                    NotebookColor.fromKey(notebook.color).label.contains(colourToken, ignoreCase = true)
                val contained = itemTitles[notebook.id].orEmpty()
                val matchesWords = words.isEmpty() || words.all { word ->
                    notebook.title.contains(word, ignoreCase = true) ||
                        NotebookColor.fromKey(notebook.color).label.contains(word, ignoreCase = true) ||
                        contained.any { it.contains(word, ignoreCase = true) }
                }
                val range = dateRange
                val matchesDate = range == null ||
                    (notebook.updatedAt >= range.first && notebook.updatedAt <= range.second + DAY_MS)
                matchesPinned && matchesColour && matchesWords && matchesDate
            }
            .sortedForDisplay(sortOption)
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    fun drop(beforeId: Long?, afterId: Long?) {
        val movingId = draggingNotebookId ?: reorderState.draggingId
        draggingNotebookId = null
        val picked: List<Notebook> = if (selectionMode && selectedIds.isNotEmpty()) {
            visible.filter { it.id in selectedIds }
        } else {
            visible.filter { it.id == movingId }
        }
        if (picked.isEmpty()) { reorderState.cancel(); return }
        val reordered = reorderedAround(visible, picked, beforeId, afterId) { it.id }
        if (reordered === visible) { reorderState.cancel(); return }
        AppScope.io.launch {
            reordered.forEachIndexed { index, notebook ->
                if (notebook.manualOrder != index * 1000) {
                    db.notebookDao().update(notebook.copy(manualOrder = index * 1000))
                }
            }
        }
        if (sortOption != NotebookSort.CUSTOM) {
            scope.launch { settingsRepo.setNotebooksSort(NotebookSort.CUSTOM.key) }
        }
        exitSelection()
    }

    fun setPinned(targets: List<Notebook>, pinned: Boolean) {
        AppScope.io.launch {
            targets.forEach { notebook ->
                db.notebookDao().update(notebook.copy(pinned = pinned, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (showBack) {
            BackHeader(onBack = onBack)
        }

        if (selectionMode) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { exitSelection() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = com.lucent.app.i18n.S.a11yCancelSelection,
                        tint = onGradient
                    )
                }
                Text(
                    com.lucent.app.i18n.S.nSelected(selectedIds.size),
                    color = onGradient,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    val targets = visible.filter { it.id in selectedIds }
                    val anyUnpinned = targets.any { !it.pinned }
                    setPinned(targets, anyUnpinned)
                    exitSelection()
                }) {
                    Icon(
                        if (visible.filter { it.id in selectedIds }.all { it.pinned }) Icons.Default.PushPin
                        else Icons.Default.PushPin,
                        contentDescription = com.lucent.app.i18n.S.notebookPinA11y,
                        tint = onGradient
                    )
                }
                TextButton(onClick = {
                    val allIds = visible.map { it.id }.toSet()
                    selectedIds = if (selectedIds.containsAll(allIds)) emptySet() else allIds
                }) {
                    Text(
                        if (selectedIds.containsAll(visible.map { it.id }.toSet()) && visible.isNotEmpty())
                            com.lucent.app.i18n.S.clearAllSelection
                        else com.lucent.app.i18n.S.selectAll
                    )
                }
                IconButton(
                    onClick = { if (selectedIds.isNotEmpty()) batchDeleting = true },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = com.lucent.app.i18n.S.a11yDeleteSelected,
                        tint = onGradient
                    )
                }
            }
        } else {
            CollapsibleActionBar(
                expanded = actionsExpanded,
                onToggleExpanded = { actionsExpanded = !actionsExpanded },
                search = {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = com.lucent.app.i18n.S.a11ySearchNotebooks) },
                        trailingIcon = { SearchHelpButton() },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                actions = {
                    DateFilterIconButton(
                        active = dateRange != null,
                        onClick = {
                            showDateRangePicker(context, dateRange?.first, dateRange?.second) { start, end ->
                                dateRange = start to end
                            }
                        }
                    )
                    SortMenuButton(
                        current = sortOption,
                        options = NotebookSort.entries.toList(),
                        label = { it.label },
                        onSelect = { option -> scope.launch { settingsRepo.setNotebooksSort(option.key) } },
                        tint = onGradientMuted,
                        activeTint = onGradient
                    )
                    IconButton(onClick = { showTrash = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = com.lucent.app.i18n.S.screenTrash,
                            tint = onGradientMuted
                        )
                    }
                },
                trailing = {
                    NewItemButton(
                        contentDescription = com.lucent.app.i18n.S.notebookNew,
                        onClick = { creating = true }
                    )
                }
            )
        }

        dateRange?.let { (start, end) ->
            Spacer(modifier = Modifier.height(8.dp))
            DateFilterChip(startMillis = start, endMillis = end, onClear = { dateRange = null })
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (visible.isEmpty()) {
            EmptyState(
                isFiltered = searchText.isNotBlank() || dateRange != null,
                emptyMessage = com.lucent.app.i18n.S.notebookEmpty,
                noMatchMessage = com.lucent.app.i18n.S.notebookNoMatch
            )
            return@Column
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.fillMaxSize().hazeSource(state = LocalHazeState.current),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + 12.dp)
        ) {
            items(visible, key = { it.id }) { notebook ->
                NotebookShelfItem(
                    notebook = notebook,
                    count = countById[notebook.id] ?: 0,
                    selectionMode = selectionMode,
                    selected = notebook.id in selectedIds,
                    itemModifier = Modifier
                        .animateItem(placementSpec = placementSpec)
                        .reorderVisuals(notebook.id, reorderState, reorderSlots, shadow = false),
                    reorderModifier = Modifier.reorderableGridItem(
                        id = notebook.id,
                        enabled = reorderEnabled,
                        gridState = gridState,
                        state = reorderState,
                        onLongPress = {
                            draggingNotebookId = notebook.id
                            selectionMode = true
                            if (notebook.id !in selectedIds) selectedIds = selectedIds + notebook.id
                        },
                        onDrop = { beforeId, afterId -> drop(beforeId, afterId) }
                    ),
                    onOpen = {
                        if (selectionMode) {
                            selectedIds = if (notebook.id in selectedIds) selectedIds - notebook.id else selectedIds + notebook.id
                        } else {
                            openNotebookId = notebook.id
                        }
                    },
                    onToggleSelect = {
                        selectedIds = if (notebook.id in selectedIds) selectedIds - notebook.id else selectedIds + notebook.id
                    },
                    onTogglePin = { setPinned(listOf(notebook), !notebook.pinned) },
                    onRename = { renaming = notebook },
                    onRecolour = { recolouring = notebook },
                    onDelete = { deleting = notebook }
                )
            }
        }
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000

@Composable
private fun NotebookShelfItem(
    notebook: Notebook,
    count: Int,
    selectionMode: Boolean,
    selected: Boolean,
    itemModifier: Modifier,
    reorderModifier: Modifier,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onRecolour: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var menuOpen by remember { mutableStateOf(false) }
    val title = notebook.title.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle }

    Column(
        modifier = itemModifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Box(
                modifier = Modifier
                    .clickable {
                        Haptics.tick(context)
                        if (selectionMode) onToggleSelect() else onOpen()
                    }
                    .then(reorderModifier)
            ) {
                NotebookCover(
                    colorKey = notebook.color,
                    label = title,
                    modifier = Modifier.width(84.dp).height(112.dp)
                )
                if (notebook.pinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = com.lucent.app.i18n.S.notebookPinnedA11y,
                        tint = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(14.dp)
                    )
                }
                if (selectionMode && selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(onGradient)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = com.lucent.app.i18n.S.a11ySelected,
                            tint = onGradientMuted,
                            modifier = Modifier.align(Alignment.Center).size(14.dp)
                        )
                    }
                }
            }
            if (!selectionMode) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = com.lucent.app.i18n.S.a11yMoreOptions,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    if (notebook.pinned) com.lucent.app.i18n.S.actionUnpin
                                    else com.lucent.app.i18n.S.actionPin
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                            onClick = { menuOpen = false; onTogglePin() }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(com.lucent.app.i18n.S.actionSelect) },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            onClick = { menuOpen = false; onToggleSelect() }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(com.lucent.app.i18n.S.notebookRename) },
                            onClick = { menuOpen = false; onRename() }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(com.lucent.app.i18n.S.notebookCoverTitle) },
                            onClick = { menuOpen = false; onRecolour() }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(com.lucent.app.i18n.S.actionDelete) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            title,
            color = onGradient,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            com.lucent.app.i18n.S.notebookItemsCount(count),
            color = onGradientMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun NotebookEditorDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialColor: NotebookColor,
    showNameField: Boolean = true,
    showColorPicker: Boolean = true,
    onConfirm: (String, NotebookColor) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showNameField) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(com.lucent.app.i18n.S.notebookName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (showColorPicker) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        com.lucent.app.i18n.S.notebookCoverTitle,
                        color = LocalOnGradient.current,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NotebookCoverPicker(selected = color, onSelect = { color = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (showNameField && name.isBlank()) {
                    LucentToast.show(context, com.lucent.app.i18n.S.notebookNameRequired)
                } else {
                    onConfirm(name, color)
                    onDismiss()
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(com.lucent.app.i18n.S.actionCancel) }
        }
    )
}

@Composable
fun NotebookTrashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val trashed by db.notebookDao().getTrashed().collectAsState(initial = emptyList())
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var confirmEmpty by remember { mutableStateOf(false) }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(com.lucent.app.i18n.S.emptyTrashTitle) },
            text = { Text(com.lucent.app.i18n.S.notebookEmptyTrashBody) },
            confirmButton = {
                TextButton(onClick = {
                    val rows = trashed.toList()
                    confirmEmpty = false
                    AppScope.io.launch {
                        rows.forEach { row ->
                            db.notebookDao().deleteItemsForNotebook(row.id)
                            db.notebookDao().deleteById(row.id)
                        }
                    }
                }) { Text(com.lucent.app.i18n.S.emptyTrash) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        BackHeader(onBack = onBack)
        Text(
            com.lucent.app.i18n.S.trashRetention(com.lucent.app.data.TrashCleanup.RETENTION_DAYS),
            color = onGradientMuted,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (trashed.isEmpty()) {
            EmptyState(
                isFiltered = false,
                emptyMessage = com.lucent.app.i18n.S.notebookTrashEmpty,
                noMatchMessage = com.lucent.app.i18n.S.notebookTrashEmpty
            )
            return@Column
        }

        if (trashed.isNotEmpty()) {
            TextButton(onClick = { confirmEmpty = true }) {
                Text(com.lucent.app.i18n.S.emptyTrash, color = onGradient, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        androidx.compose.foundation.lazy.LazyColumn(
            state = rememberRestoredListState("NotebookTrashScreen#1"),
            modifier = Modifier.hazeSource(state = LocalHazeState.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current)
        ) {
            listItems(trashed, key = { it.id }) { notebook ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlass()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Book, contentDescription = null, tint = onGradient, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            notebook.title.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle },
                            color = onGradient,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        notebook.trashedAt?.let { at ->
                            Text(formatTimestamp(at), color = onGradientMuted, fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = {
                        AppScope.io.launch {
                            db.notebookDao().update(
                                notebook.copy(trashedAt = null, updatedAt = System.currentTimeMillis())
                            )
                        }
                    }) { Text(com.lucent.app.i18n.S.actionRestore) }
                    IconButton(onClick = {
                        AppScope.io.launch {
                            db.notebookDao().deleteItemsForNotebook(notebook.id)
                            db.notebookDao().deleteById(notebook.id)
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = com.lucent.app.i18n.S.deleteForever, tint = onGradientMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun AddToNotebookDialog(
    noteIds: Set<Long>,
    taskIds: Set<Long>,
    onDismiss: () -> Unit,
    onAdded: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val notebooks by db.notebookDao().getAll().collectAsState(initial = emptyList())
    val onGradient = LocalOnGradient.current

    var creatingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf(NotebookColor.DEFAULT) }

    fun fileInto(notebookId: Long) {
        scope.launch {
            noteIds.forEach { id ->
                if (db.notebookDao().membershipExistsOnce(notebookId, NotebookItem.KIND_NOTE, id) == 0) {
                    db.notebookDao().insertItem(
                        NotebookItem(notebookId = notebookId, itemKind = NotebookItem.KIND_NOTE, itemId = id)
                    )
                }
            }
            taskIds.forEach { id ->
                if (db.notebookDao().membershipExistsOnce(notebookId, NotebookItem.KIND_TASK, id) == 0) {
                    db.notebookDao().insertItem(
                        NotebookItem(notebookId = notebookId, itemKind = NotebookItem.KIND_TASK, itemId = id)
                    )
                }
            }
            db.notebookDao().getByIdOnce(notebookId)?.let { row ->
                db.notebookDao().update(row.copy(updatedAt = System.currentTimeMillis()))
            }
            LucentToast.show(context, com.lucent.app.i18n.S.notebookAddedToast)
            onAdded()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.lucent.app.i18n.S.notebookPickTitle) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (notebooks.isEmpty() && !creatingNew) {
                    Text(com.lucent.app.i18n.S.notebookPickEmpty, color = onGradient.copy(alpha = 0.65f))
                }
                notebooks.forEach { notebook ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fileInto(notebook.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NotebookCover(
                            colorKey = notebook.color,
                            label = notebook.title,
                            modifier = Modifier.width(22.dp).height(30.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            notebook.title.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle },
                            color = onGradient,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (creatingNew) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(com.lucent.app.i18n.S.notebookName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    NotebookCoverPicker(selected = newColor, onSelect = { newColor = it })
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { creatingNew = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Book,
                            contentDescription = null,
                            tint = onGradient,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(com.lucent.app.i18n.S.notebookNewPrompt, color = onGradient, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (creatingNew) {
                TextButton(onClick = {
                    if (newName.isBlank()) {
                        LucentToast.show(context, com.lucent.app.i18n.S.notebookNameRequired)
                    } else {
                        val name = newName.trim()
                        val colorKey = newColor.key
                        scope.launch {
                            val id = db.notebookDao().insert(Notebook(title = name, color = colorKey))
                            noteIds.forEach { nid ->
                                db.notebookDao().insertItem(
                                    NotebookItem(notebookId = id, itemKind = NotebookItem.KIND_NOTE, itemId = nid)
                                )
                            }
                            taskIds.forEach { tid ->
                                db.notebookDao().insertItem(
                                    NotebookItem(notebookId = id, itemKind = NotebookItem.KIND_TASK, itemId = tid)
                                )
                            }
                            LucentToast.show(context, com.lucent.app.i18n.S.notebookAddedToast)
                            onAdded()
                        }
                    }
                }) { Text(com.lucent.app.i18n.S.notebookCreateAndAdd) }
            } else {
                TextButton(onClick = onDismiss) { Text(com.lucent.app.i18n.S.actionCancel) }
            }
        },
        dismissButton = {
            if (creatingNew) {
                TextButton(onClick = { creatingNew = false; newName = "" }) { Text(com.lucent.app.i18n.S.actionCancel) }
            }
        }
    )
}
