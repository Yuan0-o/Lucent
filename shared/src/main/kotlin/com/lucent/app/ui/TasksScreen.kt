package com.lucent.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppNavigation
import com.lucent.app.AppScope
import com.lucent.app.Screen
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentLimits
import com.lucent.app.data.AttachmentStore
import com.lucent.app.data.Attachments
import com.lucent.app.data.Checklist
import com.lucent.app.data.ChecklistItem
import com.lucent.app.data.RepeatRule
import com.lucent.app.data.SearchQuery
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.Task
import com.lucent.app.data.TaskHistory
import com.lucent.app.data.TaskPriority
import com.lucent.app.data.TrashCleanup
import com.lucent.app.data.filterBySearch
import com.lucent.app.reminders.Notifications
import com.lucent.app.reminders.ReminderScheduler
import com.lucent.app.tools.TaskActions
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TasksScreen(active: Boolean = true) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val settingsRepo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    val activeTasks by remember { db.taskDao().getActive() }.collectAsState(initial = com.lucent.app.data.DataCache.activeTasks)
    val allTasks by remember { db.taskDao().getAll() }.collectAsState(initial = com.lucent.app.data.DataCache.activeTasks + com.lucent.app.data.DataCache.completedTasks)
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val hazeState = LocalHazeState.current

    val sortKey by settingsRepo.tasksSort
        .collectAsState(initial = com.lucent.app.data.SettingsCache.tasksSort ?: "recent")
    val sortOption = TaskSort.fromKey(sortKey)

    val taskUsage by remember { com.lucent.app.data.UsageTracker.scores(context, com.lucent.app.data.UsageTracker.Kind.TASK) }
        .collectAsState(initial = emptyMap())

    var composing by remember { mutableStateOf(false) }
    var showingHistory by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var showDrafts by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var showNotebooks by remember { mutableStateOf(false) }
    var showNotebookPicker by remember { mutableStateOf(false) }
    val draftCount by db.taskDao().getDrafts().collectAsState(initial = emptyList())
    DraftRestoreDialog(draftCount = draftCount.size, onOpenDrafts = { showDrafts = true })
    SessionRestoreDialog()
    var showOverflowMenu by remember { mutableStateOf(false) }
    var historyForTaskId by remember { mutableStateOf<Long?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }
    var viewingId by remember { mutableStateOf<Long?>(null) }

    var returnToOnClose by remember { mutableStateOf<Screen?>(null) }

    LaunchedEffect(AppNavigation.pendingTaskId) {
        AppNavigation.consumeTaskId()?.let { id ->
            showSearch = false
            showingHistory = false
            showTrash = false
            showNotebooks = false
            viewingId = id
            returnToOnClose = AppNavigation.consumeReturnScreen()
        }
    }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var newTitle by remember { mutableStateOf(TextFieldValue("")) }
    var newNotes by remember { mutableStateOf("") }
    var pendingAttachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var dueAt by remember { mutableStateOf<Long?>(null) }
    var priority by remember { mutableStateOf(TaskPriority.NONE) }
    var pinned by remember { mutableStateOf(false) }
    var subtasks by remember { mutableStateOf<List<ChecklistItem>>(emptyList()) }
    var newSubtaskText by remember { mutableStateOf("") }
    var subtasksEnabled by remember { mutableStateOf(false) }
    var repeatRule by remember { mutableStateOf(RepeatRule.NONE) }
    var reminderEnabled by remember { mutableStateOf(false) }
    var composerMinMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var searchText by remember { mutableStateOf("") }
    var dateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskToTogglePin by remember { mutableStateOf<Task?>(null) }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        settingsRepo.noteHistoryEnabled.collect { com.lucent.app.data.NoteHistory.enabled = it }
    }
    LaunchedEffect(Unit) {
        settingsRepo.taskHistoryEnabled.collect { com.lucent.app.data.TaskHistory.enabled = it }
    }
    val composerScroll = rememberScrollState()
    var lastScrollValue by remember { mutableStateOf(0) }
    val composerScrollingUp = composerScroll.value < lastScrollValue
    val composerScrollingDown = composerScroll.value > lastScrollValue
    LaunchedEffect(composerScroll.value) {
        kotlinx.coroutines.delay(700)
        lastScrollValue = composerScroll.value
    }
    var quickActionsOpen by remember(composing) { mutableStateOf(false) }
    var composerMoreOpen by rememberSaveable(composing) { mutableStateOf(false) }
    val bodyUndo = remember(composing) { TextUndoStack(newNotes) }
    val repo = remember { com.lucent.app.data.SettingsRepository(context) }
    val richTextEnabled by repo.richTextEnabled.collectAsState(initial = false)
    val markdownEnabled by repo.markdownEnabled.collectAsState(initial = false)
    var bodySpans by remember { mutableStateOf(emptyList<com.lucent.app.data.RichSpan>()) }
    var bodySelStart by remember(composing) { mutableStateOf(0) }
    var bodySelEnd by remember(composing) { mutableStateOf(0) }
    var pendingKinds by remember(composing) { mutableStateOf(emptySet<com.lucent.app.data.RichSpan.Kind>()) }
    var pendingHighlight by remember(composing) { mutableStateOf<Int?>(null) }
    var pendingExplicit by remember(composing) { mutableStateOf(false) }
    var pendingColor by remember(composing) { mutableStateOf<Int?>(null) }
    var spansText by remember(composing) { mutableStateOf(newNotes) }
    LaunchedEffect(newNotes) {
        if (spansText != newNotes) {
            bodySpans = com.lucent.app.data.RichText.applyEdit(
                bodySpans, spansText, newNotes, pendingKinds, pendingHighlight, pendingColor, pendingExplicit
            )
            spansText = newNotes
        }
    }
    LaunchedEffect(newNotes) { bodyUndo.record(newNotes) }
    val reorderState = rememberReorderDragState()
    val reorderSlots = rememberListSlots(listState)
    var selectionMode by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    fun exitSelection() { selectionMode = false; selectedTaskIds = emptySet() }
    var taskToComplete by remember { mutableStateOf<Task?>(null) }
    var taskToRestore by remember { mutableStateOf<Task?>(null) }

    var showUnsavedDialog by remember { mutableStateOf(false) }

    val ensureNotificationPermission = rememberNotificationPermissionRequester()

    fun resetComposer() {
        editingTask = null
        newTitle = TextFieldValue("")
        newNotes = ""
        bodySpans = emptyList()
        pendingAttachments = emptyList()
        dueAt = null
        priority = TaskPriority.NONE
        pinned = false
        subtasks = emptyList()
        newSubtaskText = ""
        subtasksEnabled = false
        repeatRule = RepeatRule.NONE
        reminderEnabled = false
        composerMinMillis = System.currentTimeMillis()
    }

    fun startCreate() {
        resetComposer()
        composing = true
    }

    LaunchedEffect(AppNavigation.composeTaskRequested) {
        if (AppNavigation.consumeComposeTask()) {
            showSearch = false
            showingHistory = false
            showTrash = false
            showNotebooks = false
            viewingId = null
            startCreate()
        }
    }

    fun closeDetail() {
        viewingId = null
        val back = returnToOnClose
        returnToOnClose = null
        if (back != null) AppNavigation.requestScreen(back)
    }

    fun openDetail(task: Task) {
        viewingId = task.id
        returnToOnClose = null
        AppScope.io.launch {
            com.lucent.app.data.UsageTracker.recordOpen(context, com.lucent.app.data.UsageTracker.Kind.TASK, task.id)
        }
    }

    fun startEdit(task: Task) {
        editingTask = task
        newTitle = TextFieldValue(task.title, selection = TextRange(task.title.length))
        newNotes = task.notes
        bodySpans = com.lucent.app.data.RichText.load(task.notesSpans, task.notes)
        pendingAttachments = Attachments.parse(task.attachments)
        dueAt = task.dueAt
        priority = TaskPriority.fromValue(task.priority)
        pinned = task.pinned
        subtasks = Checklist.parse(task.subtasks)
        newSubtaskText = ""
        subtasksEnabled = subtasks.isNotEmpty()
        repeatRule = RepeatRule.fromKey(task.repeatRule)
        reminderEnabled = task.reminderEnabled
        composerMinMillis = task.createdAt
        composing = true
    }

    var draftRowId by remember(composing, editingTask) { mutableStateOf<Long?>(null) }
    fun saveTask(
        asDraft: Boolean = false,
        closeAfter: Boolean = !asDraft
    ) {
        val pendingSubtask = newSubtaskText.trim()
        val composedSubtasks =
            (if (pendingSubtask.isNotEmpty()) subtasks + Checklist.newItem(pendingSubtask) else subtasks)
                .filter { it.text.isNotBlank() }
        val effectiveSubtasks = if (subtasksEnabled) composedSubtasks else emptyList()
        if (newTitle.text.isBlank() && newNotes.isBlank() && pendingAttachments.isEmpty() && effectiveSubtasks.isEmpty()) {
            composing = false
            return
        }
        val title = newTitle.text
        val notesText = newNotes
        val notesSpansJson = com.lucent.app.data.RichText.encode(
            com.lucent.app.data.RichText.reconcile(bodySpans, notesText.length)
        )
        val attachmentsJson = Attachments.serialize(pendingAttachments)
        val due = dueAt
        val prioritySnapshot = priority.value
        val pinnedSnapshot = pinned
        val subtasksJson = Checklist.serialize(effectiveSubtasks)
        val repeatSnapshot = repeatRule.key
        val reminderSnapshot = reminderEnabled
        val original = editingTask
        val createdAt = composerMinMillis
        if (asDraft) {
            AppScope.io.launch {
                val row = Task(
                    title = title,
                    createdAt = createdAt,
                    notes = notesText,
                    notesSpans = notesSpansJson,
                    attachments = attachmentsJson,
                    dueAt = due,
                    priority = prioritySnapshot,
                    pinned = pinnedSnapshot,
                    subtasks = subtasksJson,
                    repeatRule = repeatSnapshot,
                    reminderEnabled = reminderSnapshot,
                    isDraft = true,
                    draftSavedAt = System.currentTimeMillis()
                )
                val existing = draftRowId
                if (existing == null) draftRowId = db.taskDao().insert(row)
                else db.taskDao().update(row.copy(id = existing))
                withContext(Dispatchers.Main) {
                    LucentToast.show(context.applicationContext, com.lucent.app.i18n.S.draftSavedToast)
                }
            }
            if (closeAfter) {
                composing = false
                resetComposer()
            }
            return
        }

        val draftToClear = draftRowId
        draftRowId = null

        val appContext = context.applicationContext

        com.lucent.app.data.backgroundWrite(context, "task save") {
            val existing = original?.let { db.taskDao().getByIdOnce(it.id) }
            val saved: Task = if (existing != null) {
                val updated = existing.copy(
                    title = title,
                    notes = notesText,
                    notesSpans = notesSpansJson,
                    attachments = attachmentsJson,
                    dueAt = due,
                    priority = prioritySnapshot,
                    pinned = pinnedSnapshot,
                    subtasks = subtasksJson,
                    repeatRule = repeatSnapshot,
                    reminderEnabled = reminderSnapshot
                )
                TaskHistory.recordIfChanged(
                    db = db,
                    existing = existing,
                    newTitle = title,
                    newNotes = notesText,
                    newSubtasks = subtasksJson,
                    newPriority = prioritySnapshot,
                    newDueAt = due
                )
                db.taskDao().update(updated)
                updated
            } else {
                val toInsert = Task(
                    id = original?.id ?: 0,
                    title = title,
                    createdAt = createdAt,
                    notes = notesText,
                    notesSpans = notesSpansJson,
                    attachments = attachmentsJson,
                    dueAt = due,
                    priority = prioritySnapshot,
                    pinned = pinnedSnapshot,
                    subtasks = subtasksJson,
                    repeatRule = repeatSnapshot,
                    reminderEnabled = reminderSnapshot
                )
                val newId = db.taskDao().insert(toInsert)
                toInsert.copy(id = newId)
            }
            ReminderScheduler.sync(appContext, saved)

                if (draftToClear != null) {
                    db.taskDao().getByIdOnce(draftToClear)?.let { row ->
                        if (row.isDraft) db.taskDao().delete(row)
                    }
                }
            withContext(Dispatchers.Main) {
                LucentToast.show(appContext, com.lucent.app.i18n.S.taskSaved)
            }
        }
        composing = false
        resetComposer()
    }

    val taskDirty = composing && run {
        val original = editingTask
        if (original != null) {
            newTitle.text != original.title || newNotes != original.notes || dueAt != original.dueAt ||
                Attachments.serialize(pendingAttachments) != original.attachments ||
                priority.value != original.priority || pinned != original.pinned ||
                Checklist.serialize(subtasks) != original.subtasks ||
                newSubtaskText.isNotBlank() ||
                repeatRule.key != original.repeatRule || reminderEnabled != original.reminderEnabled
        } else {
            newTitle.text.isNotBlank() || newNotes.isNotBlank() || pendingAttachments.isNotEmpty() ||
                dueAt != null || priority != TaskPriority.NONE || pinned || subtasks.isNotEmpty() ||
                newSubtaskText.isNotBlank() ||
                repeatRule != RepeatRule.NONE || reminderEnabled
        }
    }

    fun discardComposer() {
        composing = false
        resetComposer()
    }

    fun leaveComposer() {
        if (taskDirty) showUnsavedDialog = true else discardComposer()
    }

    BackHandler(enabled = composing) { leaveComposer() }
    BackHandler(enabled = !composing && viewingId != null) { closeDetail() }
    BackHandler(enabled = !composing && viewingId == null && showingHistory) { showingHistory = false }
    BackHandler(enabled = !composing && viewingId == null && !showingHistory && showTrash) { showTrash = false }
    BackHandler(enabled = !composing && viewingId == null && !showingHistory && !showTrash && showSearch) { showSearch = false }
    BackHandler(enabled = !composing && viewingId == null && (showDrafts || showHidden)) {
        if (showDrafts) showDrafts = false else showHidden = false
    }
    BackHandler(enabled = !composing && viewingId == null && !showingHistory && !showTrash && !showSearch && !showDrafts && !showHidden && showNotebooks) { showNotebooks = false }
    BackHandler(enabled = selectionMode && !composing && viewingId == null && !showingHistory && !showTrash && !showSearch && !showDrafts && !showHidden) { exitSelection() }

    LaunchedEffect(active) {
        if (!active) {
            if (composing) discardComposer()
            viewingId = null
            returnToOnClose = null
            showingHistory = false
            showTrash = false
            showSearch = false
            showOverflowMenu = false
            showNotebooks = false
            actionsExpanded = false
            exitSelection()
        }
    }

    OnAppHidden { actionsExpanded = false }

    SideEffect {
        if (taskDirty) {
            UnsavedChangesGuard.register(
                owner = "tasks",
                onSave = { saveTask() },
                onDiscard = { discardComposer() },
                onAutoDraft = { saveTask(asDraft = true, closeAfter = false) }
            )
        } else {
            UnsavedChangesGuard.clear("tasks")
        }
    }
    DisposableEffect(Unit) { onDispose { UnsavedChangesGuard.clear("tasks") } }

    val sessionSnapshot = if (composing && taskDirty) {
        com.lucent.app.data.SessionRestore.Snapshot(
            kind = com.lucent.app.data.SessionRestore.KIND_TASK,
            itemId = editingTask?.id,
            title = newTitle.text,
            payload = com.lucent.app.data.SessionRestore.put(
                "title" to newTitle.text,
                "notes" to newNotes,
                "notesSpans" to com.lucent.app.data.RichText.encode(
                    com.lucent.app.data.RichText.reconcile(bodySpans, newNotes.length)
                ),
                "attachments" to Attachments.serialize(pendingAttachments),
                "dueAt" to dueAt,
                "priority" to priority.value,
                "pinned" to pinned,
                "subtasks" to Checklist.serialize(subtasks),
                "subtaskText" to newSubtaskText,
                "subtasksEnabled" to subtasksEnabled,
                "repeatRule" to repeatRule.key,
                "reminderEnabled" to reminderEnabled
            )
        )
    } else null
    LaunchedEffect(sessionSnapshot) {
        if (sessionSnapshot == null) {
            if (com.lucent.app.data.SessionRestore.pending == null) {
                com.lucent.app.data.SessionRestore.clear(context)
            }
        } else {
            kotlinx.coroutines.delay(900)
            com.lucent.app.data.SessionRestore.save(context, sessionSnapshot)
        }
    }

    LaunchedEffect(com.lucent.app.data.SessionRestore.restoring) {
        val snap = com.lucent.app.data.SessionRestore
            .consumeRestore(com.lucent.app.data.SessionRestore.KIND_TASK) ?: return@LaunchedEffect
        val p = com.lucent.app.data.SessionRestore.read(snap.payload)
        val id = snap.itemId
        resetComposer()
        editingTask = if (id != null) db.taskDao().getByIdOnce(id) else null
        newTitle = androidx.compose.ui.text.input.TextFieldValue(p.optString("title"))
        newNotes = p.optString("notes")
        pendingAttachments = Attachments.parse(p.optString("attachments", "[]"))
        dueAt = if (p.isNull("dueAt")) null else p.optLong("dueAt")
        priority = TaskPriority.fromValue(p.optInt("priority"))
        pinned = p.optBoolean("pinned")
        subtasks = Checklist.parse(p.optString("subtasks", "[]"))
        newSubtaskText = p.optString("subtaskText")
        subtasksEnabled = p.optBoolean("subtasksEnabled")
        repeatRule = RepeatRule.fromKey(p.optString("repeatRule"))
        reminderEnabled = p.optBoolean("reminderEnabled")
        bodySpans = com.lucent.app.data.RichText.load(p.optString("notesSpans"), newNotes)
        viewingId = null
        showingHistory = false
        historyForTaskId = null
        showTrash = false
        showSearch = false
        showDrafts = false
        showHidden = false
        composing = true
    }


    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(com.lucent.app.i18n.S.unsavedChangesTitle) },
            text = { Text(if (editingTask != null) com.lucent.app.i18n.S.unsavedTaskExistingBody else com.lucent.app.i18n.S.unsavedTaskNewBody) },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    saveTask()
                }) { Text(com.lucent.app.i18n.S.actionSave) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        discardComposer()
                    }) { Text(com.lucent.app.i18n.S.actionDiscard) }
                    TextButton(onClick = { showUnsavedDialog = false }) { Text(com.lucent.app.i18n.S.actionCancel) }
                }
            }
        )
    }

    val query = remember(searchText) { SearchQuery.parse(searchText) }
    val searchPool = remember(activeTasks, allTasks, query) {
        if ("done" in query.flags) allTasks.filter { it.trashedAt == null } else activeTasks
    }
    val filteredActive = remember(searchPool, query, dateRange) {
        searchPool.filterBySearch(query).filter { task ->
            dateRange?.let { (start, end) -> withinLocalDayRange(task.createdAt, start, end) } ?: true
        }
    }
    val sortedActive = remember(filteredActive, sortOption, query) {
        filteredActive.sortedForDisplay(sortOption, query)
    }

    val reorderEnabled = true
    val placementSpec = rememberReorderPlacementSpec()
    val browsing = searchText.isBlank() && dateRange == null
    val sectionNow = remember(sortedActive) { System.currentTimeMillis() }
    val sections = remember(sortedActive, taskUsage, browsing, sectionNow, sortOption) {
        if (!browsing) null else sectionHomeItems(
            items = sortedActive,
            now = sectionNow,
            maxRecent = 6,
            id = { it.id },
            timestamp = { it.createdAt },
            activityScore = { com.lucent.app.data.UsageTracker.score(taskUsage[it.id] ?: 0.0, it.createdAt, sectionNow) },
            isPinned = { it.pinned },
            orderWithinSections = sortOption == TaskSort.CUSTOM
        )
    }
    val sectionOfId = remember(sections) {
        val m = HashMap<Long, HomeSection>()
        sections?.nonEmpty()?.forEach { (section, list) ->
            list.forEach { m[it.id] = section }
        }
        m
    }

    val inlineGlobal = rememberInlineGlobalResults(searchText)
    val inlineLocalIds = remember(sortedActive) { sortedActive.mapTo(HashSet()) { it.id } }
    val inlineGlobalTasks = remember(inlineGlobal.tasks, inlineLocalIds) {
        inlineGlobal.tasks.filterNot { it.id in inlineLocalIds }
    }
    val inlineGlobalNotes = inlineGlobal.notes

    fun dropSelection(beforeId: Long?, afterId: Long?) {
        val moving = selectedTaskIds.toList().mapNotNull { id -> sortedActive.firstOrNull { it.id == id } }
        if (moving.isEmpty()) return
        val home = sectionOfId[moving.first().id]
        val sameSection = { id: Long? -> sections == null || id == null || sectionOfId[id] == home }
        val usableAfter = if (sameSection(afterId)) afterId else null
        val usableBefore = if (sameSection(beforeId)) beforeId else null
        if (usableAfter == null && usableBefore == null) return
        if (moving.any { sectionOfId[it.id] != home }) return
        val reordered = reorderedAround(sortedActive, moving, usableBefore, usableAfter) { it.id }
        if (reordered === sortedActive) return
        AppScope.io.launch {
            reordered.forEachIndexed { index, t ->
                if (t.manualOrder != index) db.taskDao().setManualOrder(t.id, index)
            }
        }
        if (sortOption != TaskSort.CUSTOM) {
            scope.launch { settingsRepo.setTasksSort(TaskSort.CUSTOM.key) }
        }
        exitSelection()
    }

    val launchFilePicker = rememberAttachmentFilePicker { picked ->
        scope.launch {
            val (accepted, lastMessage) = withContext(Dispatchers.IO) {
                var runningPending = pendingAttachments.toList()
                var message = ""
                for (source in picked) {
                    val hint = attachmentSizeHint(context, source)
                    if (hint > 0) {
                        val preCheck = AttachmentLimits.checkSingle(hint)
                        if (!preCheck.allowed) { message = preCheck.message; continue }
                    }
                    val newAtt = pickedFileToAttachment(context, source) ?: run { message = com.lucent.app.i18n.S.couldNotReadOneFile; null } ?: continue
                    val incoming = AttachmentLimits.sizeOf(context, newAtt)
                    val postCheck = AttachmentLimits.checkSingle(incoming)
                    if (postCheck.allowed) {
                        runningPending = Attachments.upsert(context, runningPending, newAtt)
                    } else {
                        if (AttachmentStore.looksLikeId(newAtt.data)) AttachmentStore.delete(context, newAtt.data)
                        message = postCheck.message
                    }
                }
                runningPending to message
            }
            pendingAttachments = accepted
            if (lastMessage.isNotBlank()) LucentToast.show(context, lastMessage, longDuration = true)
        }
    }

    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text(com.lucent.app.i18n.S.moveToTrashTitle) },
            text = {
                Text(com.lucent.app.i18n.S.moveTaskTrashBody(task.title.ifBlank { com.lucent.app.i18n.S.untitledTask }, TrashCleanup.RETENTION_DAYS))
            },
            confirmButton = {
                TextButton(onClick = {
                    val toTrash = task
                    taskToDelete = null
                    if (viewingId == toTrash.id) viewingId = null
                    AppScope.io.launch { TaskActions.trash(context, db, toTrash) }
                }) { Text(com.lucent.app.i18n.S.moveToTrash) }
            },
            dismissButton = { TextButton(onClick = { taskToDelete = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    taskToComplete?.let { task ->
        val repeats = RepeatRule.fromKey(task.repeatRule) != RepeatRule.NONE
        AlertDialog(
            onDismissRequest = { taskToComplete = null },
            title = { Text(com.lucent.app.i18n.S.completeTaskTitle) },
            text = {
                Text(com.lucent.app.i18n.S.completeTaskBody(task.title.ifBlank { com.lucent.app.i18n.S.untitledTask }))
            },
            confirmButton = {
                TextButton(onClick = {
                    val toComplete = task
                    taskToComplete = null
                    AppScope.io.launch { TaskActions.complete(context, db, toComplete) }
                }) { Text(com.lucent.app.i18n.S.actionConfirm) }
            },
            dismissButton = { TextButton(onClick = { taskToComplete = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    taskToRestore?.let { task ->
        val hasFutureDue = task.dueAt != null
        AlertDialog(
            onDismissRequest = { taskToRestore = null },
            title = { Text(com.lucent.app.i18n.S.markNotDoneTitle) },
            text = {
                Text(com.lucent.app.i18n.S.notDoneTaskBody(task.title.ifBlank { com.lucent.app.i18n.S.untitledTask }))
            },
            confirmButton = {
                TextButton(onClick = {
                    val toRestore = task
                    taskToRestore = null
                    AppScope.io.launch { TaskActions.restore(context, db, toRestore) }
                }) { Text(com.lucent.app.i18n.S.markNotDone) }
            },
            dismissButton = { TextButton(onClick = { taskToRestore = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    taskToTogglePin?.let { task ->
        val willPin = !task.pinned
        AlertDialog(
            onDismissRequest = { taskToTogglePin = null },
            title = { Text(if (willPin) com.lucent.app.i18n.S.pinTaskTitle else com.lucent.app.i18n.S.unpinTaskTitle) },
            text = {
                Text(
                    if (willPin) com.lucent.app.i18n.S.pinTaskBody(task.title.ifBlank { com.lucent.app.i18n.S.untitledTask })
                    else com.lucent.app.i18n.S.unpinTaskBody(task.title.ifBlank { com.lucent.app.i18n.S.untitledTask })
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = task
                    val pinnedNow = !target.pinned
                    taskToTogglePin = null
                    com.lucent.app.data.backgroundWrite(context, "task pin toggle") {
                        db.taskDao().setPinned(target.id, pinnedNow)
                    }
                }) { Text(if (willPin) com.lucent.app.i18n.S.actionPin else com.lucent.app.i18n.S.actionUnpin) }
            },
            dismissButton = { TextButton(onClick = { taskToTogglePin = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    if (showBatchDeleteConfirm) {
        val count = selectedTaskIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text(com.lucent.app.i18n.S.moveToTrashTitle) },
            text = {
                Text(
                    if (count == 1) com.lucent.app.i18n.S.moveOneTaskTrashBody(TrashCleanup.RETENTION_DAYS)
                    else com.lucent.app.i18n.S.moveNTasksTrashBody(count, TrashCleanup.RETENTION_DAYS)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedTaskIds
                    showBatchDeleteConfirm = false
                    exitSelection()
                    AppScope.io.launch {
                        ids.forEach { id ->
                            db.taskDao().getByIdOnce(id)?.let { TaskActions.trash(context, db, it) }
                        }
                    }
                }) { Text(com.lucent.app.i18n.S.moveToTrash) }
            },
            dismissButton = { TextButton(onClick = { showBatchDeleteConfirm = false }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    if (showNotebookPicker) {
        AddToNotebookDialog(
            noteIds = emptySet(),
            taskIds = selectedTaskIds,
            onDismiss = { showNotebookPicker = false },
            onAdded = { showNotebookPicker = false; exitSelection() }
        )
    }

    val viewingTask = remember(allTasks, viewingId) {
        allTasks.firstOrNull { it.id == viewingId && it.trashedAt == null }
    }

    when {
        composing -> {
            val jumpToLastSubtask = remember { editingTask != null && subtasks.isNotEmpty() }
            val subtaskEndRequester = remember { BringIntoViewRequester() }
            val jumpToDetailsEnd = remember {
                editingTask != null && subtasks.isEmpty() && newNotes.length > DETAILS_JUMP_THRESHOLD
            }
            val detailsEndRequester = remember { BringIntoViewRequester() }
            if (jumpToLastSubtask || jumpToDetailsEnd) {
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    withFrameNanos { }
                    if (jumpToLastSubtask) subtaskEndRequester.bringIntoView()
                    else detailsEndRequester.bringIntoView()
                }
            }
            val bodyTools: @Composable BoxScope.() -> Unit = {
            QuickActionFab(
                scrollingUp = composerScrollingUp,
                scrollingDown = composerScrollingDown,
                expanded = quickActionsOpen,
                canUndo = bodyUndo.canUndo,
                canRedo = bodyUndo.canRedo,
                onScrollTop = { scope.launch { composerScroll.animateScrollTo(0) } },
                onScrollBottom = { scope.launch { composerScroll.animateScrollTo(composerScroll.maxValue) } },
                onToggleExpanded = { quickActionsOpen = !quickActionsOpen },
                onUndo = { bodyUndo.undo()?.let { newNotes = it } },
                onRedo = { bodyUndo.redo()?.let { newNotes = it } },
                richTextEnabled = richTextEnabled,
                hasSelection = bodySelEnd > bodySelStart,
                onToggleStyle = { kind, color ->
                    if (bodySelEnd > bodySelStart) {
                        bodySpans = com.lucent.app.data.RichText.toggle(
                            bodySpans, bodySelStart, bodySelEnd, kind, color
                        )
                    } else if (kind == com.lucent.app.data.RichSpan.Kind.HIGHLIGHT) {
                        pendingExplicit = true
                        pendingHighlight = if (pendingHighlight == color) null else color
                    } else if (kind == com.lucent.app.data.RichSpan.Kind.COLOR) {
                        pendingExplicit = true
                        pendingColor = if (pendingColor == color) null else color
                    } else {
                        pendingExplicit = true
                        val opposite = when (kind) {
                            com.lucent.app.data.RichSpan.Kind.BOLD -> com.lucent.app.data.RichSpan.Kind.LIGHT
                            com.lucent.app.data.RichSpan.Kind.LIGHT -> com.lucent.app.data.RichSpan.Kind.BOLD
                            else -> null
                        }
                        pendingKinds = if (kind in pendingKinds) pendingKinds - kind
                        else (if (opposite != null) pendingKinds - opposite else pendingKinds) + kind
                    }
                },
                onClearStyle = {
                    if (bodySelEnd > bodySelStart) {
                        bodySpans = com.lucent.app.data.RichSpan.Kind.entries.fold(bodySpans) { acc, k ->
                            com.lucent.app.data.RichText.remove(acc, bodySelStart, bodySelEnd, k, null)
                        }
                    } else {
                        pendingExplicit = true
                        pendingKinds = emptySet()
                        pendingHighlight = null
                        pendingColor = null
                    }
                },
                activeKinds = if (bodySelEnd > bodySelStart)
                    com.lucent.app.data.RichText.kindsCovering(bodySpans, bodySelStart, bodySelEnd)
                else pendingKinds,
                activeHighlight = if (bodySelEnd > bodySelStart)
                    com.lucent.app.data.RichText.highlightCovering(bodySpans, bodySelStart, bodySelEnd)
                else pendingHighlight,
                activeColor = if (bodySelEnd > bodySelStart)
                    com.lucent.app.data.RichText.colorCovering(bodySpans, bodySelStart, bodySelEnd)
                else pendingColor,
                onNeedSelection = { LucentToast.show(context.applicationContext, com.lucent.app.i18n.S.richTextNeedSelection) },
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = LocalBottomBarInset.current + 14.dp)
            )
            }
            Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(composerScroll).imePadding().padding(bottom = LocalBottomBarInset.current)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { leaveComposer() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.lucent.app.i18n.S.actionBack, tint = onGradient)
                    }
                    Text(if (editingTask != null) com.lucent.app.i18n.S.editTask else com.lucent.app.i18n.S.newTask, color = onGradient, fontSize = 20.sp)
                }

                Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = null,
                            tint = if (pinned) onGradient else onGradientMuted
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(com.lucent.app.i18n.S.pinToTop, color = onGradient, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(checked = pinned, onCheckedChange = { pinned = it })
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text(com.lucent.app.i18n.S.fieldTitle) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ExpandableGlassTextField(
                        value = newNotes,
                        onValueChange = { newNotes = it },
                        extraAction = { DictationButton(onText = { spoken ->
                            newNotes = if (newNotes.isBlank()) spoken
                            else newNotes + (if (newNotes.endsWith(" ") || newNotes.endsWith("\n")) "" else " ") + spoken
                        }) },
                        spans = if (richTextEnabled) bodySpans else emptyList(),
                        onSelectionChange = { a, b -> bodySelStart = a; bodySelEnd = b },
                        highlightColors = if (richTextEnabled) RichHighlightColors else emptyList(),
                            textColors = if (richTextEnabled) richTextColors() else emptyList(),
                        placeholder = com.lucent.app.i18n.S.detailsPlaceholder,
                        expandedTitle = if (editingTask != null) com.lucent.app.i18n.S.editTask else com.lucent.app.i18n.S.newTask,
                        collapsedMinHeight = 360.dp,
                        tools = bodyTools
                    )
                    Spacer(modifier = Modifier.height(12.dp).bringIntoViewRequester(detailsEndRequester))
                    if (subtasksEnabled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        ChecklistEditorSection(
                            items = subtasks,
                            newItemText = newSubtaskText,
                            onNewItemTextChange = { newSubtaskText = it },
                            onAdd = {
                                subtasks = subtasks + Checklist.newItem(newSubtaskText)
                                newSubtaskText = ""
                            },
                            onToggle = { item -> subtasks = subtasks.map { if (it.id == item.id) it.copy(done = !it.done) else it } },
                            onRemove = { item -> subtasks = subtasks.filterNot { it.id == item.id } },
                            onEditText = { item, text -> subtasks = subtasks.map { if (it.id == item.id) it.copy(text = text) else it } },
                            addLabel = com.lucent.app.i18n.S.addSubtask,
                            addRowModifier = Modifier.bringIntoViewRequester(subtaskEndRequester),
                            onInsertAfter = { item ->
                                val at = subtasks.indexOfFirst { it.id == item.id }
                                subtasks = if (at < 0) subtasks + Checklist.newItem("")
                                else subtasks.toMutableList().also { it.add(at + 1, Checklist.newItem("")) }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    DueDateRow(
                        dueAt = dueAt,
                        minMillis = composerMinMillis,
                        onChange = { newDue ->
                            dueAt = newDue
                            if (newDue == null) {
                                repeatRule = RepeatRule.NONE
                                reminderEnabled = false
                            }
                        }
                    )
                    if (dueAt != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ReminderToggleRow(
                            enabled = reminderEnabled,
                            hasDueDate = true,
                            onToggle = { checked ->
                                reminderEnabled = checked
                                if (checked) ensureNotificationPermission()
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        RepeatRuleRow(selected = repeatRule, onSelect = { repeatRule = it })
                    }
                    AttachmentSection(
                        attachments = pendingAttachments,
                        onPick = { launchFilePicker() },
                        onRemove = { att ->
                            pendingAttachments = Attachments.removeByName(context, pendingAttachments, att.name)
                        },
                        onRename = { att, newName ->
                            pendingAttachments = pendingAttachments.map {
                                if (it.data == att.data) it.copy(name = newName) else it
                            }
                        },
                        onReorder = { from, to ->
                            pendingAttachments = pendingAttachments.toMutableList().also {
                                if (from in it.indices && to in it.indices) it.add(to, it.removeAt(from))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    MoreOptionsFold(
                        expanded = composerMoreOpen,
                        onToggle = { composerMoreOpen = !composerMoreOpen }
                    ) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = null,
                                tint = if (subtasksEnabled) onGradient else onGradientMuted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(com.lucent.app.i18n.S.labelSubtasks, color = onGradient, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Switch(checked = subtasksEnabled, onCheckedChange = { subtasksEnabled = it })
                        }

                        PriorityPickerRow(selected = priority, onSelect = { priority = it })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { saveTask() },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.weight(1f).height(COMPOSER_ACTION_HEIGHT)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                if (editingTask != null) " " + com.lucent.app.i18n.S.saveChanges else " " + com.lucent.app.i18n.S.addTaskBtn,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        GlassButton(
                            text = com.lucent.app.i18n.S.saveToDraft,
                            onClick = { saveTask(asDraft = true) },
                            modifier = Modifier.weight(1f).height(COMPOSER_ACTION_HEIGHT)
                        )
                    }
                }
            }
            bodyTools()
        }
        }

        historyForTaskId != null && viewingTask != null -> {
            TaskHistoryScreen(
                task = viewingTask,
                onBack = { historyForTaskId = null },
                onRestored = { historyForTaskId = null }
            )
        }

        viewingTask != null -> {
            val detailScroll = rememberScrollState()
            val task = viewingTask
            val attachments = remember(task.attachments) { Attachments.parse(task.attachments) }
            val subtaskItems = remember(task.subtasks) { Checklist.parse(task.subtasks) }
            val taskPriority = remember(task.priority) { TaskPriority.fromValue(task.priority) }
            val taskRepeat = remember(task.repeatRule) { RepeatRule.fromKey(task.repeatRule) }
            val overdue = isOverdue(task.dueAt, task.isDone)

            val swipeList = sortedActive
            val swipeOffset = remember { Animatable(0f) }
            var pageWidth by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { pageWidth = it.width.toFloat() }
                    .pointerInput(task.id, swipeList) {
                        var armed = false
                        var atEnd = false
                        val threshold = 72.dp.toPx()
                        val topExclusion = 88.dp.toPx()
                        val bottomExclusion = 88.dp.toPx()
                        detectHorizontalDragGestures(
                            onDragStart = { start ->
                                armed = start.y >= topExclusion && start.y <= size.height - bottomExclusion
                                atEnd = false
                            },
                            onDragEnd = {
                                if (armed) {
                                    val idx = swipeList.indexOfFirst { it.id == task.id }
                                    val travelled = swipeOffset.value
                                    val goNext = travelled <= -threshold && idx >= 0 && idx < swipeList.lastIndex
                                    val goPrev = travelled >= threshold && idx > 0
                                    val width = if (pageWidth > 0f) pageWidth else threshold * 6f
                                    scope.launch {
                                        when {
                                            goNext -> {
                                                swipeOffset.animateTo(-width, tween(SWIPE_EXIT_MS, easing = FastOutLinearInEasing))
                                                openDetail(swipeList[idx + 1])
                                                swipeOffset.snapTo(width)
                                                swipeOffset.animateTo(0f, tween(SWIPE_ENTER_MS, easing = LinearOutSlowInEasing))
                                            }
                                            goPrev -> {
                                                swipeOffset.animateTo(width, tween(SWIPE_EXIT_MS, easing = FastOutLinearInEasing))
                                                openDetail(swipeList[idx - 1])
                                                swipeOffset.snapTo(-width)
                                                swipeOffset.animateTo(0f, tween(SWIPE_ENTER_MS, easing = LinearOutSlowInEasing))
                                            }
                                            else -> swipeOffset.animateTo(
                                                0f,
                                                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                            )
                                        }
                                    }
                                }
                                armed = false
                            },
                            onDragCancel = {
                                armed = false
                                scope.launch { swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (armed) {
                                    val idx = swipeList.indexOfFirst { it.id == task.id }
                                    val blocked = idx < 0 ||
                                        (dragAmount < 0 && idx >= swipeList.lastIndex) ||
                                        (dragAmount > 0 && idx <= 0)
                                    atEnd = blocked
                                    change.consume()
                                    scope.launch {
                                        swipeOffset.snapTo(swipeOffset.value + if (blocked) dragAmount * SWIPE_RESIST else dragAmount)
                                    }
                                }
                            }
                        )
                    }
            ) {
              Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = swipeOffset.value
                        alpha = 1f - (kotlin.math.abs(swipeOffset.value) / (pageWidth.takeIf { it > 0f } ?: 1f))
                            .coerceIn(0f, 1f) * 0.35f
                    }
                    .padding(16.dp)
                    .verticalScroll(detailScroll)
                    .padding(bottom = LocalBottomBarInset.current)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { closeDetail() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.lucent.app.i18n.S.actionBack, tint = onGradient)
                    }
                    Text(com.lucent.app.i18n.S.screenTask, color = onGradient, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))

                    var actionsExpanded by remember(task.id) { mutableStateOf(false) }
                    val taskVersions by db.taskVersionDao().getForTask(task.id).collectAsState(initial = emptyList())
                    val taskVersionCount = taskVersions.size
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(
                            visible = actionsExpanded,
                            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
                        ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            copyToClipboard(context, copyTextForTask(task))
                            LucentToast.show(context.applicationContext, com.lucent.app.i18n.S.copiedAllToast)
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = com.lucent.app.i18n.S.copyAll, tint = onGradient)
                        }
                        if (!task.isDone && !task.pinned) {
                            PinIconButton(
                                pinned = false,
                                onToggle = { taskToTogglePin = task }
                            )
                        }
                        IconButton(onClick = {
                            if (task.isDone) taskToRestore = task else taskToComplete = task
                        }) {
                            Icon(
                                if (task.isDone) Icons.AutoMirrored.Filled.Undo else Icons.Default.CheckCircle,
                                contentDescription = if (task.isDone) com.lucent.app.i18n.S.markNotDone else com.lucent.app.i18n.S.markDone,
                                tint = onGradient
                            )
                        }
                        IconButton(onClick = { startEdit(task) }) {
                            Icon(Icons.Default.Edit, contentDescription = com.lucent.app.i18n.S.actionEdit, tint = onGradient)
                        }
                        if (markdownEnabled && richTextEnabled) {
                            FormatOverrideButton(
                                current = task.formatOverride,
                                onSelect = { key ->
                                    AppScope.io.launch {
                                        db.taskDao().getByIdOnce(task.id)?.let { row ->
                                            db.taskDao().update(row.copy(formatOverride = key))
                                        }
                                    }
                                }
                            )
                        }
                        IconButton(onClick = {
                            shareText(context, subject = task.title.ifBlank { "Task" }, text = shareTextForTask(task), chooserTitle = com.lucent.app.i18n.S.shareTaskChooser)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = com.lucent.app.i18n.S.actionShare, tint = onGradient)
                        }
                        if (taskVersionCount > 0) {
                            IconButton(onClick = { historyForTaskId = task.id }) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = com.lucent.app.i18n.S.a11yVersionHistory(taskVersionCount),
                                    tint = onGradient
                                )
                            }
                        }
                        IconButton(onClick = { taskToDelete = task }) {
                            Icon(Icons.Default.Delete, contentDescription = com.lucent.app.i18n.S.actionDelete, tint = onGradient)
                        }
                        }
                        }
                        IconButton(onClick = { actionsExpanded = !actionsExpanded }) {
                            Icon(
                                if (actionsExpanded) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                                contentDescription = if (actionsExpanded) com.lucent.app.i18n.S.a11yHideActions
                                                     else com.lucent.app.i18n.S.a11yShowMoreActions,
                                tint = onGradient
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlass()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Column {
                                Text(
                                    task.title.ifBlank { com.lucent.app.i18n.S.untitledTask },
                                    color = onGradient,
                                    fontSize = 22.sp,
                                    textDecoration = if (task.isDone) TextDecoration.LineThrough else null
                                )
                                if (taskPriority != TaskPriority.NONE) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    PriorityBadge(taskPriority)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(com.lucent.app.i18n.S.createdOn(formatTimestamp(task.createdAt)), color = onGradientMuted, fontSize = 12.sp)
                                task.dueAt?.let { due ->
                                    Text(
                                        if (overdue) friendlyDue(due) else com.lucent.app.i18n.S.dueWhen(friendlyDue(due)),
                                        color = if (overdue) OverdueColor else onGradientMuted,
                                        fontSize = 12.sp
                                    )
                                }
                                if (taskRepeat != RepeatRule.NONE) {
                                    Text(com.lucent.app.i18n.S.repeatsEvery(taskRepeat.uiLabel), color = onGradientMuted, fontSize = 12.sp)
                                }
                                if (task.reminderEnabled && task.dueAt != null && !task.isDone) {
                                    Text(com.lucent.app.i18n.S.reminderOn, color = onGradientMuted, fontSize = 12.sp)
                                }
                                task.completedAt?.let { done ->
                                    Text(com.lucent.app.i18n.S.completedOn(formatTimestamp(done)), color = onGradientMuted, fontSize = 12.sp)
                                }
                                if (task.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val detailFormat = com.lucent.app.data.ContentFormats.resolve(
                                        markdownEnabled = markdownEnabled,
                                        richTextEnabled = richTextEnabled,
                                        override = task.formatOverride,
                                        body = task.notes
                                    )
                                    if (detailFormat == com.lucent.app.data.ContentFormat.MARKDOWN) {
                                        MarkdownText(text = task.notes)
                                    } else {
                                        Text(task.notes, color = onGradient)
                                    }
                                }
                            }
                        }
                    }

                    if (subtaskItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ChecklistView(
                            items = subtaskItems,
                            header = com.lucent.app.i18n.S.labelSubtasks,
                            onToggle = if (task.isDone) {
                                null
                            } else {
                                { item, checked ->
                                    AppScope.io.launch {
                                        db.taskDao().update(
                                            task.copy(subtasks = Checklist.setDone(task.subtasks, item.id, checked))
                                        )
                                    }
                                }
                            }
                        )
                    }

                    CardAttachments(
                        attachments, onGradient, onGradientMuted,
                        onRename = { att, newName ->
                            AppScope.io.launch {
                                db.taskDao().update(
                                    task.copy(
                                        attachments = Attachments.serialize(
                                            attachments.map { if (it.data == att.data) it.copy(name = newName) else it }
                                        )
                                    )
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                if (task.isDone) {
                    GlassCapsuleButton(
                        text = com.lucent.app.i18n.S.markNotDone,
                        icon = Icons.AutoMirrored.Filled.Undo,
                        onClick = { taskToRestore = task },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    GlassCapsuleButton(
                        text = com.lucent.app.i18n.S.editTask,
                        icon = Icons.Default.Edit,
                        onClick = { startEdit(task) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
                ScrollEdgeJumpButtons(
                    canUp = detailScroll.value > 0,
                    canDown = detailScroll.value < detailScroll.maxValue,
                    tint = onGradient,
                    onUp = { scope.launch { detailScroll.animateScrollTo(0) } },
                    onDown = { scope.launch { detailScroll.animateScrollTo(detailScroll.maxValue) } },
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = LocalBottomBarInset.current + 14.dp)
                )
            }
        }

        showingHistory -> {
            CompletedTasksScreen(
                onBack = { showingHistory = false },
                onOpen = { openDetail(it) },
                onDeleteRequest = { taskToDelete = it }
            )
        }

        showTrash -> {
            TrashTasksScreen(onBack = { showTrash = false })
        }

        showHidden && HiddenArea.visible -> {
            HiddenTasksScreen(
                onBack = { showHidden = false },
                onOpen = { openDetail(it) }
            )
        }

        showDrafts -> {
            DraftTasksScreen(
                onBack = { showDrafts = false },
                onOpen = { showDrafts = false; startEdit(it) }
            )
        }

        showNotebooks -> {
            NotebooksScreen(
                onBack = { showNotebooks = false },
                onOpenTask = { task -> openDetail(task) },
                onOpenNote = { note -> AppNavigation.openNote(note.id, from = Screen.Tasks) }
            )
        }

        showSearch -> {
            SearchScreen(
                onOpenNote = { note -> AppNavigation.openNote(note.id, from = Screen.Tasks) },
                onOpenTask = { task -> openDetail(task) },
                onBack = { showSearch = false }
            )
        }

        else -> {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                if (selectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = com.lucent.app.i18n.S.a11yCancelSelection, tint = onGradient)
                        }
                        Text(
                            com.lucent.app.i18n.S.nSelected(selectedTaskIds.size),
                            color = onGradient,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (HiddenArea.visible) {
                            IconButton(onClick = {
                                val ids = selectedTaskIds
                                val target = showHidden
                                AppScope.io.launch {
                                    ids.forEach { id ->
                                        db.taskDao().getByIdOnce(id)?.let { row ->
                                            db.taskDao().update(row.copy(hidden = !target))
                                        }
                                    }
                                }
                                exitSelection()
                            }) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = if (showHidden) com.lucent.app.i18n.S.hiddenRemove
                                                         else com.lucent.app.i18n.S.hiddenAdd,
                                    tint = onGradient
                                )
                            }
                        }
                        TextButton(onClick = {
                            val allIds = sortedActive.map { it.id }.toSet()
                            selectedTaskIds = if (selectedTaskIds.containsAll(allIds)) emptySet() else allIds
                        }) {
                            Text(if (selectedTaskIds.containsAll(sortedActive.map { it.id }.toSet()) && sortedActive.isNotEmpty()) com.lucent.app.i18n.S.clearAllSelection else com.lucent.app.i18n.S.selectAll)
                        }
                        IconButton(
                            onClick = { if (selectedTaskIds.isNotEmpty()) showNotebookPicker = true },
                            enabled = selectedTaskIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Book, contentDescription = com.lucent.app.i18n.S.notebookA11yAddItems, tint = onGradient)
                        }
                        IconButton(
                            onClick = { if (selectedTaskIds.isNotEmpty()) showBatchDeleteConfirm = true },
                            enabled = selectedTaskIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = com.lucent.app.i18n.S.a11yDeleteSelected, tint = onGradient)
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
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = com.lucent.app.i18n.S.a11ySearchTasks) },
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
                                options = TaskSort.entries.toList(),
                                label = { it.label },
                                onSelect = { option -> scope.launch { settingsRepo.setTasksSort(option.key) } },
                                tint = onGradientMuted,
                                activeTint = onGradient
                            )
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = com.lucent.app.i18n.S.a11yMoreOptions, tint = onGradientMuted)
                                }
                                DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(com.lucent.app.i18n.S.screenCompletedTasks) },
                                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                        onClick = { showOverflowMenu = false; showingHistory = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(com.lucent.app.i18n.S.screenTrash) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                        onClick = { showOverflowMenu = false; showTrash = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(com.lucent.app.i18n.S.screenDrafts) },
                                        leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                                        onClick = { showOverflowMenu = false; showDrafts = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(com.lucent.app.i18n.S.screenNotebooks) },
                                        leadingIcon = { Icon(Icons.Default.Book, contentDescription = null) },
                                        onClick = { showOverflowMenu = false; showNotebooks = true }
                                    )
                                    if (HiddenArea.visible) {
                                        DropdownMenuItem(
                                            text = { Text(com.lucent.app.i18n.S.screenHidden) },
                                            leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                                            onClick = { showOverflowMenu = false; showHidden = true }
                                        )
                                    }
                                }
                            }
                        },
                        trailing = {
                            NewItemButton(contentDescription = com.lucent.app.i18n.S.newTask, onClick = { startCreate() })
                        }
                    )

                    dateRange?.let { (start, end) ->
                        Spacer(modifier = Modifier.height(8.dp))
                        DateFilterChip(startMillis = start, endMillis = end, onClear = { dateRange = null })
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxSize()) {
                ReorderDropSlot(state = reorderState, slots = reorderSlots, modifier = Modifier.fillMaxSize())
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = LocalBottomBarInset.current)
                ) {
                    if (sortedActive.isEmpty()) {
                        item(key = "empty_state") {
                            Box(modifier = Modifier.fillMaxWidth().fillParentMaxHeight()) {
                                EmptyState(
                                    isFiltered = searchText.isNotBlank() || dateRange != null,
                                    emptyMessage = com.lucent.app.i18n.S.emptyTasksHint,
                                    noMatchMessage = com.lucent.app.i18n.S.noTasksMatchSearch
                                )
                            }
                        }
                    } else {
                        val renderCard: @Composable (Task, Modifier) -> Unit = { task, itemModifier ->
                            TaskCard(
                                task = task,
                                reorderVisualModifier = itemModifier.reorderVisuals(task.id, reorderState, reorderSlots),
                                selectionMode = selectionMode,
                                selected = task.id in selectedTaskIds,
                                onOpen = { openDetail(task) },
                                onLongPress = { selectionMode = true; selectedTaskIds = setOf(task.id) },
                                reorderEnabled = reorderEnabled,
                                reorderModifier = Modifier.reorderableItem(
                                    id = task.id,
                                    enabled = reorderEnabled,
                                    listState = listState,
                                    state = reorderState,
                                    onLongPress = {
                                        selectionMode = true
                                        if (task.id !in selectedTaskIds) selectedTaskIds = selectedTaskIds + task.id
                                    },
                                    onDrop = { beforeId, afterId -> dropSelection(beforeId, afterId) }
                                ),
                                onToggleSelect = {
                                    selectedTaskIds = if (task.id in selectedTaskIds) selectedTaskIds - task.id else selectedTaskIds + task.id
                                },
                                onArmComplete = { taskToComplete = task },
                                onTogglePin = { taskToTogglePin = task },
                                onDelete = { taskToDelete = task }
                            )
                        }
                        if (sections != null) {
                            sections.nonEmpty().forEach { (section, list) ->
                                item(key = "header_${section.name}") { TaskSectionHeader(section.label) }
                                items(list, key = { it.id }) { task ->
                                    renderCard(task, Modifier.animateItem(placementSpec = placementSpec))
                                }
                            }
                        } else {
                            items(sortedActive, key = { it.id }) { task ->
                                renderCard(task, Modifier.animateItem(placementSpec = placementSpec))
                            }
                        }
                    }
                    if (searchText.isNotBlank() && inlineGlobal.hasResults) {
                        item(key = "inline_global_divider") {
                            GlobalSearchDivider()
                        }
                        if (inlineGlobalTasks.isNotEmpty()) {
                            item(key = "inline_global_tasks_header") {
                                GlobalTasksHeader(inlineGlobalTasks.size)
                            }
                            items(inlineGlobalTasks, key = { "global_task_${it.id}" }) { task ->
                                TaskResultRow(task = task, onOpen = { openDetail(task) })
                            }
                        }
                        if (inlineGlobalNotes.isNotEmpty()) {
                            item(key = "inline_global_notes_header") {
                                GlobalNotesHeader(inlineGlobalNotes.size)
                            }
                            items(inlineGlobalNotes, key = { "global_note_${it.id}" }) { note ->
                                NoteResultRow(note = note, onOpen = { AppNavigation.openNote(note.id, from = Screen.Tasks) })
                            }
                        }
                    }
                }
                ScrollEdgeJumpButtons(
                    canUp = listState.canScrollBackward,
                    canDown = listState.canScrollForward,
                    tint = onGradient,
                    onUp = { scope.launch { listState.animateScrollToItem(0) } },
                    onDown = { scope.launch { listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = LocalBottomBarInset.current + 14.dp)
                )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(
    task: Task,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    reorderEnabled: Boolean = false,
    reorderModifier: Modifier = Modifier,
    reorderVisualModifier: Modifier = Modifier,
    onToggleSelect: () -> Unit,
    onArmComplete: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val priority = remember(task.priority) { TaskPriority.fromValue(task.priority) }
    val progress = remember(task.subtasks) { Checklist.progress(task.subtasks) }
    val repeats = remember(task.repeatRule) { RepeatRule.fromKey(task.repeatRule) != RepeatRule.NONE }
    val createdLabel = rememberFormattedTimestamp(task.createdAt)
    val overdue = isOverdue(task.dueAt, task.isDone)
    val selShape = RoundedCornerShape(20.dp)

    Column(
        modifier = reorderVisualModifier
            .fillMaxWidth()
            .frostedGlass()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                onLongClick = if (reorderEnabled) null else { { if (!selectionMode) onLongPress() } }
            )
            .then(reorderModifier)
            .then(if (selected) Modifier.border(2.dp, onGradient, selShape) else Modifier)
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (selected) com.lucent.app.i18n.S.a11ySelected else com.lucent.app.i18n.S.a11yNotSelected,
                    tint = if (selected) onGradient else onGradientMuted,
                    modifier = Modifier.padding(horizontal = 12.dp).size(22.dp)
                )
            } else {
                Checkbox(
                    checked = task.isDone,
                    onCheckedChange = { checked ->
                        if (checked) onArmComplete()
                    }
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.pinned) {
                        PinnedMarker(
                            onUnpin = onTogglePin,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    PriorityDot(priority)
                    if (priority != TaskPriority.NONE) Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        task.title.ifBlank { com.lucent.app.i18n.S.untitledTask },
                        color = onGradient,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(createdLabel, color = onGradientMuted, fontSize = 12.sp)
                task.dueAt?.let { due ->
                    Text(
                        if (overdue) friendlyDue(due) else com.lucent.app.i18n.S.dueWhen(friendlyDue(due)),
                        color = if (overdue) OverdueColor else onGradientMuted,
                        fontSize = 12.sp
                    )
                }
                if (repeats || progress != null) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (repeats) {
                            Icon(
                                Icons.Default.Repeat,
                                contentDescription = com.lucent.app.i18n.S.a11yRepeats,
                                tint = onGradientMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            if (progress != null) Spacer(modifier = Modifier.width(8.dp))
                        }
                        progress?.let { (done, total) ->
                            Text(com.lucent.app.i18n.S.nSubtasks(done, total), color = onGradientMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (!selectionMode) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = com.lucent.app.i18n.S.actionDelete, tint = onGradient)
                }
            }
        }
    }
}

@Composable
private fun TaskSectionHeader(label: String) {
    val onGradientMuted = LocalOnGradientMuted.current
    Text(
        label.uppercase(),
        color = onGradientMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

private fun shareTextForTask(task: Task): String {
    val sb = StringBuilder()
    sb.append(task.title.ifBlank { com.lucent.app.i18n.S.untitledTask })
    task.dueAt?.let { sb.append("\nDue: ").append(formatTimestamp(it)) }
    TaskPriority.fromValue(task.priority).takeIf { it != TaskPriority.NONE }?.let {
        sb.append("\nPriority: ").append(it.label)
    }
    RepeatRule.fromKey(task.repeatRule).takeIf { it != RepeatRule.NONE }?.let {
        sb.append("\nRepeats: ").append(it.label)
    }
    if (task.notes.isNotBlank()) sb.append("\n\n").append(task.notes)
    val items = Checklist.parse(task.subtasks)
    if (items.isNotEmpty()) {
        sb.append("\n\nSubtasks:\n").append(Checklist.toMarkdown(task.subtasks))
    }
    return sb.toString().trim()
}

private fun copyTextForTask(task: Task): String {
    val sb = StringBuilder()
    if (task.notes.isNotBlank()) sb.append(task.notes.trim())
    val items = Checklist.parse(task.subtasks)
    if (items.isNotEmpty()) {
        if (sb.isNotEmpty()) sb.append("\n\n")
        sb.append(Checklist.toMarkdown(task.subtasks).trim())
    }
    return sb.toString().trim()
}

@Composable
private fun DueDateRow(dueAt: Long?, minMillis: Long, onChange: (Long?) -> Unit) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    val openPicker = rememberDateTimePicker(minMillis = minMillis, initialMillis = dueAt ?: minMillis) { chosen ->
        onChange(chosen)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clickable { openPicker() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = onGradientMuted)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (dueAt != null) com.lucent.app.i18n.S.dueWhen(formatTimestamp(dueAt)) else com.lucent.app.i18n.S.setADueDate,
            color = onGradient,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        if (dueAt != null) {
            IconButton(onClick = { onChange(null) }) {
                Icon(Icons.Default.Close, contentDescription = com.lucent.app.i18n.S.a11yClearDueDate, tint = onGradientMuted)
            }
        }
    }
}

private const val DETAILS_JUMP_THRESHOLD = 400
