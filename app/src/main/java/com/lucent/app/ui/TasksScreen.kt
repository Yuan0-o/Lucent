package com.lucent.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import java.util.Calendar
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

    // The home page shows only pending, non-trashed tasks. As soon as a task is marked complete
    // (via the checkbox dialog or the assistant tool) it disappears from this flow and reappears on
    // the history page — the split is a plain SQL WHERE clause, not an in-memory filter, so moving
    // between the two is instantaneous.
    // The list flows are remembered so their Room subscription is stable across recompositions — a
    // fresh Flow per recomposition could miss the invalidation a just-inserted task fires, so a new
    // task now appears immediately instead of only after leaving and returning to the tab.
    val activeTasks by remember { db.taskDao().getActive() }.collectAsState(initial = com.lucent.app.data.DataCache.activeTasks)
    // All tasks (active + completed) are read too so search can honour is:done and the detail page can
    // resolve a completed task opened from history.
    val allTasks by remember { db.taskDao().getAll() }.collectAsState(initial = com.lucent.app.data.DataCache.activeTasks + com.lucent.app.data.DataCache.completedTasks)
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val hazeState = LocalHazeState.current

    // Remembered sort choice, persisted across app restarts (see SettingsRepository.tasksSort).
    // Round R1, task 2 — seeded from the synchronous startup read; see the Notes twin of this
    // comment and data/SettingsCache for what reading it late actually looked like.
    val sortKey by settingsRepo.tasksSort
        .collectAsState(initial = com.lucent.app.data.SettingsCache.tasksSort ?: "recent")
    val sortOption = TaskSort.fromKey(sortKey)

    // Frequency scores for the "Recent" home section (id -> activity), see UsageTracker.
    val taskUsage by remember { com.lucent.app.data.UsageTracker.scores(context, com.lucent.app.data.UsageTracker.Kind.TASK) }
        .collectAsState(initial = emptyMap())

    // View modes: home list (active tasks), completed history, trash, read-only detail, composer.
    var composing by remember { mutableStateOf(false) }
    var showingHistory by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    // Task A10 — the draft area, reached from the same overflow menu as the trash.
    var showDrafts by remember { mutableStateOf(false) }
    // Task A21 — the hidden area. Only offered while HiddenArea.visible, which the Privacy switch
    // sets and which resets itself on every launch.
    var showHidden by remember { mutableStateOf(false) }
    // Task A10 — how many drafts are waiting, for the once-per-launch restore prompt below.
    val draftCount by db.taskDao().getDrafts().collectAsState(initial = emptyList())
    DraftRestoreDialog(draftCount = draftCount.size, onOpenDrafts = { showDrafts = true })
    // Round R1, task 5 — the "go back to where you were?" prompt. Shown by whichever home tab
    // composes first regardless of which one the snapshot belongs to; see SessionRestoreDialog.
    SessionRestoreDialog()
    var showOverflowMenu by remember { mutableStateOf(false) }
    // Task A19: which task's revision history is open, if any.
    var historyForTaskId by remember { mutableStateOf<Long?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    // Whether the header's secondary-action cluster (date filter, sort, overflow) is expanded.
    // Collapsed by default; survives config changes (task 16).
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }
    var viewingId by remember { mutableStateOf<Long?>(null) }

    // Where to go when the task currently open was reached from *another* tab (task 4). Null for a
    // task opened from this tab's own list, which is the ordinary case and needs no return trip.
    var returnToOnClose by remember { mutableStateOf<Screen?>(null) }

    // A task asked for from elsewhere — a unified-search result tapped while on the Notes tab.
    // Consumed once; see AppNavigation. The origin tab is consumed in the same breath and held until
    // this task is closed, so closing it lands the user back where they started their search rather
    // than stranding them on whichever tab happened to own the result.
    LaunchedEffect(AppNavigation.pendingTaskId) {
        AppNavigation.consumeTaskId()?.let { id ->
            showSearch = false
            showingHistory = false
            showTrash = false
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
    // Whether the subtasks section is switched on (task 11). Like a note's "Checklist" toggle, it
    // lives between the title and details and only reveals the subtask editor when on — a task that
    // doesn't need a checklist isn't cluttered with one. It defaults on when editing a task that
    // already has subtasks so they stay visible.
    var subtasksEnabled by remember { mutableStateOf(false) }
    var repeatRule by remember { mutableStateOf(RepeatRule.NONE) }
    var reminderEnabled by remember { mutableStateOf(false) }
    // Floor for the due-date picker: this task's real creation time when editing, or the instant
    // the composer was opened when creating (which becomes the task's createdAt).
    var composerMinMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var searchText by remember { mutableStateOf("") }
    // Optional date-range filter (start-of-day, end-of-day millis). Null means no filter; the end
    // can't precede the start (the picker enforces it).
    var dateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    // Pin/unpin now confirms first (dialog below) so a stray tap can't silently reorder the list.
    var taskToTogglePin by remember { mutableStateOf<Task?>(null) }

    // Hoisted above the view-mode `when` so the home list keeps its scroll position across opening a
    // task and coming back (see feature: preserve scroll position).
    val listState = rememberLazyListState()

    // Long-press multi-select for batch delete (see feature: batch operations).
    // ---- Task A7 state ----------------------------------------------------------------------
    // Hoisted so the floating control can drive it; the column below still owns the scrolling.
    // Task 4 — keep the history mirrors in step with the stored switches. Done from both list
    // screens because either one may be the tab the app opens on, and NoteHistory.recordIfChanged
    // has no Context of its own to read the setting with. Idempotent, so doing it twice is free.
    LaunchedEffect(Unit) {
        settingsRepo.noteHistoryEnabled.collect { com.lucent.app.data.NoteHistory.enabled = it }
    }
    LaunchedEffect(Unit) {
        settingsRepo.taskHistoryEnabled.collect { com.lucent.app.data.TaskHistory.enabled = it }
    }
    val composerScroll = rememberScrollState()
    // Which way the finger last moved the page. Derived from the scroll value rather than from the
    // gesture, so it is correct however the page moved — flung, dragged, or brought into view by a
    // focused text field.
    var lastScrollValue by remember { mutableStateOf(0) }
    val composerScrollingUp = composerScroll.value < lastScrollValue
    val composerScrollingDown = composerScroll.value > lastScrollValue
    LaunchedEffect(composerScroll.value) {
        kotlinx.coroutines.delay(700)
        lastScrollValue = composerScroll.value
    }
    // Task 5. Keyed on [composing], so the ring is collapsed every time an editor is opened. It used
    // to be remembered for the lifetime of the whole screen: expand it once, save, open anything
    // else, and the ring was still fanned out over a page the user had not asked it to cover.
    var quickActionsOpen by remember(composing) { mutableStateOf(false) }
    // v2.7.2: the "More options" fold of the composer (see MoreOptionsFold).
    var composerMoreOpen by rememberSaveable(composing) { mutableStateOf(false) }
    // One undo stack per composer session, following the body/details field. Seeded from whatever
    // the field holds when the editor opens, so the first undo returns to the text as it was found
    // rather than to an empty string.
    val bodyUndo = remember(composing) { TextUndoStack(newNotes) }
    // ---- INTEGRATION: C-group task 20, rich text ----
    // The spans for the field above, plus the current selection. Both are plain composer state, in
    // the same place and with the same lifetime as the text they describe — a span list that
    // outlived its body would style whatever text happened to be there next.
    // First-compile fix (CI 2026-07-26): the rich-text state below reads settings via `repo`,
    // which was never declared in this screen on either platform — the integration referenced a
    // name from another screen. Same construction the rest of the app uses.
    val repo = remember { com.lucent.app.data.SettingsRepository(context) }
    val richTextEnabled by repo.richTextEnabled.collectAsState(initial = false)
    // Deliberately NOT keyed on `composing`, unlike every other piece of composer state around it.
    //
    // It used to be `remember(composing) { ... }`, and that was quietly destructive. `startEdit`
    // loads the saved sidecar into this and THEN sets `composing = true`; a keyed remember discards
    // its state object on the recomposition that flip causes, so the freshly-loaded spans were
    // replaced by an empty list every single time an existing item was opened for editing.
    //
    // That was not merely a display bug. The save path writes
    // `RichText.encode(reconcile(bodySpans, ...))` unconditionally, so opening a formatted item and
    // saving it - changing nothing else, not even touching the text - ERASED its formatting in the
    // database. Silently, permanently, and on the most ordinary action there is.
    //
    // Dropping the key costs nothing, because the reset it provided was already being done
    // explicitly: every path that opens a *fresh* composer goes through `resetComposer()`, which
    // clears this, and the one path that must NOT be reset is precisely the one the key was
    // breaking. The remaining `remember(composing)` state below is left alone - none of it is
    // populated before the flip, and `bodyUndo`/`spansText` actually depend on being rebuilt from
    // the text that is in place by then.
    var bodySpans by remember { mutableStateOf(emptyList<com.lucent.app.data.RichSpan>()) }
    var bodySelStart by remember(composing) { mutableStateOf(0) }
    var bodySelEnd by remember(composing) { mutableStateOf(0) }
    // Spans follow the text. Any writer that does NOT go through the field — undo, the assistant, a
    // version restore — lands here as a length change, and reconcile() drops whatever no longer
    // sits on real characters. See data/RichText.kt.
    // ---- Task 1: styles armed BEFORE typing ----
    //
    // Formatting used to require text that already existed: select it, then style it. Every editor
    // anyone actually uses also works the other way round — press bold, then type — and these two
    // hold what has been armed. They are cleared with the composer session, like the spans they feed.
    var pendingKinds by remember(composing) { mutableStateOf(emptySet<com.lucent.app.data.RichSpan.Kind>()) }
    var pendingHighlight by remember(composing) { mutableStateOf<Int?>(null) }
    // Task 5 — set the moment the toolbar is used with no selection. From then on what was armed is
    // the whole truth about the next characters, which is what makes "clear formatting" actually
    // clear: without it the boundary-extension rule re-inherited the style from the character in
    // front of the caret and the button looked broken. See RichText.applyEdit.
    var pendingExplicit by remember(composing) { mutableStateOf(false) }
    var pendingColor by remember(composing) { mutableStateOf<Int?>(null) }
    // The text the span list currently describes. Kept beside the spans so an edit can be DIFFED
    // rather than merely clamped: reconcile() only ever chopped spans back to the new length, which
    // silently mis-styled every edit that inserted or deleted anywhere but the very end.
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
    // Task A16 — reordering is only meaningful under the Custom sort; see ReorderDrag.kt.
    val reorderState = rememberReorderDragState()
    var selectionMode by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    fun exitSelection() { selectionMode = false; selectedTaskIds = emptySet() }
    // Confirmation gate for "mark this task as complete". The checkbox is NEVER flipped
    // optimistically; only after the user taps Confirm here is isDone = true written to the
    // database. Cancelling (tapping outside, tapping Cancel, or pressing back) leaves the task
    // exactly as it was.
    var taskToComplete by remember { mutableStateOf<Task?>(null) }
    // The mirror of the above for the *reverse* action (task 7): sending a finished task back to the
    // active list moves it between two pages and re-arms its reminder, so it asks first too. The
    // rule is symmetric on purpose — a confirmation that only guards one direction teaches people
    // that the other direction is safe to tap by accident.
    var taskToRestore by remember { mutableStateOf<Task?>(null) }

    var showUnsavedDialog by remember { mutableStateOf(false) }

    // The notification permission is requested the first time the user actually switches a reminder
    // on — never at launch. If they decline, the preference is still stored: the reminder simply
    // won't alert until the permission is granted, which is recoverable, whereas silently discarding
    // their setting because of one dialog answer is not.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            LucentToast.show(
                context,
                com.lucent.app.i18n.S.notifPermissionRationale,
                longDuration = true
            )
        }
    }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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

    // A home-screen widget "new task" tap routes here (task 9): open a fresh composer, exactly once.
    LaunchedEffect(AppNavigation.composeTaskRequested) {
        if (AppNavigation.consumeComposeTask()) {
            showSearch = false
            showingHistory = false
            showTrash = false
            viewingId = null
            startCreate()
        }
    }

    /**
     * Close the detail page, returning to the tab the task was opened *from* when it came from
     * another one (task 4). An ordinary in-tab open has no origin, so this is just "go back to the
     * list" — the behaviour it always had.
     */
    fun closeDetail() {
        viewingId = null
        val back = returnToOnClose
        returnToOnClose = null
        if (back != null) AppNavigation.requestScreen(back)
    }

    fun openDetail(task: Task) {
        viewingId = task.id
        // Opening a task from this tab's own list is not a cross-tab trip, so any pending return
        // is stale and is dropped rather than firing later from an unrelated close.
        returnToOnClose = null
        // Feed the "Recent" section: opening a task counts toward how active it is.
        AppScope.io.launch {
            com.lucent.app.data.UsageTracker.recordOpen(context, com.lucent.app.data.UsageTracker.Kind.TASK, task.id)
        }
    }

    fun startEdit(task: Task) {
        editingTask = task
        newTitle = TextFieldValue(task.title, selection = TextRange(task.title.length))
        newNotes = task.notes
        // INTEGRATION (C task 20): load the sidecar, clamped to the text it describes.
        bodySpans = com.lucent.app.data.RichText.load(task.notesSpans, task.notes)
        pendingAttachments = Attachments.parse(task.attachments)
        dueAt = task.dueAt
        priority = TaskPriority.fromValue(task.priority)
        pinned = task.pinned
        subtasks = Checklist.parse(task.subtasks)
        newSubtaskText = ""
        // Show the subtasks section pre-opened if this task already has any, so editing doesn't hide
        // them behind an off switch.
        subtasksEnabled = subtasks.isNotEmpty()
        repeatRule = RepeatRule.fromKey(task.repeatRule)
        reminderEnabled = task.reminderEnabled
        composerMinMillis = task.createdAt
        composing = true
    }

    // Task A10: which drafts row this composer session owns, if it has parked anything yet.
    // Keyed on the session (composing/editingId) so a fresh composer never adopts the previous
    // one's row and starts silently overwriting a draft the user meant to keep.
    var draftRowId by remember(composing, editingTask) { mutableStateOf<Long?>(null) }
    fun saveTask(
        // Task A10. `closeAfter` defaults to the opposite of `asDraft` because the two callers want
        // opposite things: the explicit "Save to drafts" button is the user finishing with this
        // screen, while the automatic park on backgrounding must leave the editor exactly as it
        // was — still open, still dirty, still able to save properly if they come straight back.
        asDraft: Boolean = false,
        closeAfter: Boolean = !asDraft
    ) {
        // A subtask typed into the add field but never committed with "+" still counts (settings
        // tasks B1/B4): it is folded in here, so typing the last item and hitting Save directly
        // never silently drops it.
        val pendingSubtask = newSubtaskText.trim()
        // Round R2, task 2 — a subtask with nothing written on it is not a subtask. Same rule and
        // same reasoning as the note checklist; see NotesScreen.saveNote.
        val composedSubtasks =
            (if (pendingSubtask.isNotEmpty()) subtasks + Checklist.newItem(pendingSubtask) else subtasks)
                .filter { it.text.isNotBlank() }
        // With the subtasks switch off, the task has no subtasks even if the editor still holds some
        // from before it was toggled off — so an off switch means "no checklist" both for the
        // "nothing to save" check and for what actually gets written.
        val effectiveSubtasks = if (subtasksEnabled) composedSubtasks else emptyList()
        if (newTitle.text.isBlank() && newNotes.isBlank() && pendingAttachments.isEmpty() && effectiveSubtasks.isEmpty()) {
            composing = false
            return
        }
        // Snapshot every composer field before the background write — same reasoning as
        // NotesScreen.saveNote: resetComposer() runs the instant this function returns, so reading
        // these vars from inside the coroutine would race it.
        val title = newTitle.text
        val notesText = newNotes
        // The rich-text sidecar is snapshotted HERE, with everything else, and for the
        // reason stated above: it is a `var`, resetComposer() clears it synchronously as
        // soon as this function returns, and the write below runs on a background scope.
        // Reading it from inside that block persisted an empty sidecar over perfectly
        // good formatting - the one field the snapshot block had been missing.
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
        // ---- Task A10: park this edit in the draft area instead of committing it ----------
        //
        // Deliberately an early return over the *already snapshotted* fields rather than a branch
        // threaded through the write below. Two reasons, and the second is the important one:
        //
        //  1. It reuses the snapshot block above verbatim, so the draft can never disagree with
        //     what a real save would have written — the drift NoteHistory's comments warn about.
        //  2. It cannot touch the live row. Everything after this point is the committing path;
        //     by returning here, "save a draft" is structurally incapable of overwriting the note
        //     or task being edited, which is the one failure that would be worse than losing the
        //     edit in the first place.
        //
        // [draftRowId] keys the session, not the item: the first draft inserts, every later one
        // updates the same row. Without it, a phone that backgrounds the app five times would
        // leave five copies of the same half-finished note in the drafts list.
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

        // ---- Task 15: a committed edit takes its own draft with it ---------------------------
        // See the note editor's twin for the reasoning; the rule is identical.
        val draftToClear = draftRowId
        draftRowId = null

        val appContext = context.applicationContext

        // App-lifetime scope so saving-then-navigating (e.g. the unsaved-changes dialog) can't
        // cancel the write before it commits.
        AppScope.io.launch {
            val saved: Task = if (original != null) {
                val updated = original.copy(
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
                // Task A19: snapshot the old content *before* it is overwritten, exactly as the
                // note editor does. recordIfChanged is a no-op when nothing textual actually
                // changed, so re-saving without editing cannot evict a real revision.
                TaskHistory.recordIfChanged(
                    db = db,
                    existing = original,
                    newTitle = title,
                    newNotes = notesText,
                    newSubtasks = subtasksJson,
                    newPriority = prioritySnapshot,
                    newDueAt = due
                )
                db.taskDao().update(updated)
                updated
            } else {
                // createdAt is pinned to the same instant the due-date picker was bounded by, so
                // the stored creation time can never end up after a due date it constrained.
                val toInsert = Task(
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
            // One call, whatever changed: sync() decides for itself whether this task should now
            // have an alarm, so there's no "did I need to cancel that" branch to get wrong.
            ReminderScheduler.sync(appContext, saved)

                // Task 15 — drop this session's parked draft now that the real save has landed.
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

    // True while the composer holds edits that haven't been saved yet.
    val taskDirty = composing && run {
        val original = editingTask
        if (original != null) {
            newTitle.text != original.title || newNotes != original.notes || dueAt != original.dueAt ||
                Attachments.serialize(pendingAttachments) != original.attachments ||
                priority.value != original.priority || pinned != original.pinned ||
                Checklist.serialize(subtasks) != original.subtasks ||
                // Text sitting in the add field is work too (B4): losing it on exit because no "+"
                // was pressed is exactly the bug this flag exists to prevent.
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

    // Back priority, most specific first: composer -> ask, then close; detail -> back to whichever
    // list we came from; history/trash -> back to the active-tasks home.
    BackHandler(enabled = composing) { leaveComposer() }
    BackHandler(enabled = !composing && viewingId != null) { closeDetail() }
    BackHandler(enabled = !composing && viewingId == null && showingHistory) { showingHistory = false }
    BackHandler(enabled = !composing && viewingId == null && !showingHistory && showTrash) { showTrash = false }
    BackHandler(enabled = !composing && viewingId == null && !showingHistory && !showTrash && showSearch) { showSearch = false }
    // Task 3.4 — see the notes screen twin: Drafts and the hidden area are sub-pages, not the root.
    BackHandler(enabled = !composing && viewingId == null && (showDrafts || showHidden)) {
        if (showDrafts) showDrafts = false else showHidden = false
    }
    // On the home list, back first exits multi-select rather than leaving the screen.
    BackHandler(enabled = selectionMode && !composing && viewingId == null && !showingHistory && !showTrash && !showSearch && !showDrafts && !showHidden) { exitSelection() }

    // Leaving this tab folds it back to the task list (task 3). Sub-pages — a task's detail page,
    // the composer, history, trash, search — are places you *went*, not places you live, and coming
    // back to the Tasks tab three screens deep in something you opened ten minutes ago is
    // disorienting: the tab looks like it has forgotten what it is. Note this can't lose work: an
    // unsaved composer routes through UnsavedChangesGuard *before* the tab switch is allowed to
    // happen, so by the time this runs the edit has already been saved, discarded, or cancelled
    // (and if cancelled, the switch never occurred and this never runs).
    LaunchedEffect(active) {
        if (!active) {
            if (composing) discardComposer()
            viewingId = null
            returnToOnClose = null
            showingHistory = false
            showTrash = false
            showSearch = false
            showOverflowMenu = false
            // Collapse the header's action cluster too (task): leaving the tab and coming back should
            // find it tucked away again, not still expanded.
            actionsExpanded = false
            exitSelection()
        }
    }

    // Collapse the action cluster when the app leaves the foreground (screen off, home, another
    // app), so reopening finds it tucked away rather than as it was left (task). ON_STOP fires when
    // the activity is no longer visible, which covers all of those without touching the tab state.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) actionsExpanded = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SideEffect {
        if (taskDirty) {
            UnsavedChangesGuard.register(
                owner = "tasks",
                onSave = { saveTask() },
                onDiscard = { discardComposer() },
                // Task A10 — parks a copy without closing or committing anything.
                onAutoDraft = { saveTask(asDraft = true, closeAfter = false) }
            )
        } else {
            UnsavedChangesGuard.clear("tasks")
        }
    }
    DisposableEffect(Unit) { onDispose { UnsavedChangesGuard.clear("tasks") } }

    // ---- Round R1, task 5: the recovery snapshot ----
    // See the Notes twin of this block for why the Snapshot doubles as the effect key.
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
        // An edit of an existing task needs the row itself back, not just its id: the dirty check
        // and the save path both diff against it. A row that has since been deleted restores as a
        // new task carrying the same text, which is the only honest thing left to do with it.
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
        // Written straight in now that `bodySpans` is no longer keyed on `composing`;
        // see its declaration for the bug that used to force this into a second stage.
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

    // The active list already excludes completed and trashed tasks, so the structured query plus the
    // creation-date filter give "search my open tasks" with real operators — priority:high,
    // due:today, is:overdue, has:reminder — rather than only a title substring.
    val query = remember(searchText) { SearchQuery.parse(searchText) }
    // `is:done` is the one filter that changes which tasks are in scope, since completed tasks live
    // on the history page. Honouring it here makes the history searchable without navigating to it.
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

    // ---- Task A16: what a drop actually does -------------------------------------------------
    //
    // Reordering is offered only under the Custom sort. Under any other one the list's order is a
    // computed result, and honouring a drag would mean writing a position the next recomposition
    // is obliged to throw away — a control that appears to work and silently doesn't.
    // Task 8 — always live now, and a drop under any other sort adopts Custom so the positions
    // just written are actually the ones displayed. See the note-list twin for the full reasoning.
    val reorderEnabled = true
    // Round R1, task 2 - see rememberReorderPlacementSpec: no placement animation until the screen
    // has settled, so opening the app never replays a reordering the user already made.
    val placementSpec = rememberReorderPlacementSpec()
    // ---- Home sections, hoisted so the drop handler below can see them ----
    //
    // Reordering is confined to ONE section: a card dragged inside Recent stays in Recent, a pinned
    // card stays among the pinned. That is what the sections are for — they answer "why is this card
    // here?", and a drag that could move a card between them would be answering it differently every
    // time. Under the Custom sort the buckets keep the user's own sequence (see
    // [sectionHomeItems.orderWithinSections]), so a drop inside a section is visible and permanent.
    val browsing = searchText.isBlank() && dateRange == null
    val sectionNow = remember(sortedActive) { System.currentTimeMillis() }
    val sections = remember(sortedActive, taskUsage, browsing, sectionNow, sortOption) {
        if (!browsing) null else sectionHomeItems(
            items = sortedActive,
            now = sectionNow,
            maxRecent = 6,
            id = { it.id },
                    // A task's own time for the Today bucket is its creation time.
            timestamp = { it.createdAt },
            activityScore = { com.lucent.app.data.UsageTracker.score(taskUsage[it.id] ?: 0.0, it.createdAt, sectionNow) },
            // Task A13 — pinned items lead the page instead of being scattered by date.
            isPinned = { it.pinned },
            orderWithinSections = sortOption == TaskSort.CUSTOM
        )
    }
    // Which section a given id sits in, or null when the page is not sectioned at all.
    val sectionOfId = remember(sections) {
        val m = HashMap<Long, HomeSection>()
        sections?.let { s ->
            s.pinned.forEach { m[it.id] = HomeSection.PINNED }
            s.recent.forEach { m[it.id] = HomeSection.RECENT }
            s.today.forEach { m[it.id] = HomeSection.TODAY }
            s.older.forEach { m[it.id] = HomeSection.OLDER }
        }
        m
    }

    fun dropSelection(beforeId: Long?, afterId: Long?) {
        // Selection order, not list order: `Set.plus` returns a LinkedHashSet, so this is the
        // sequence the user ticked them in — the only arrival order they could have predicted.
        val moving = selectedTaskIds.toList().mapNotNull { id -> sortedActive.firstOrNull { it.id == id } }
        if (moving.isEmpty()) return
        // One section at a time — see the notes screen twin for the full reasoning. The gap is named
        // by the cards on either side of it, so the ends of the list need no special case: the top
        // gap simply has no card above it and the bottom gap none below.
        val home = sectionOfId[moving.first().id]
        val sameSection = { id: Long? -> sections == null || id == null || sectionOfId[id] == home }
        val usableAfter = if (sameSection(afterId)) afterId else null
        val usableBefore = if (sameSection(beforeId)) beforeId else null
        if (usableAfter == null && usableBefore == null) return
        if (moving.any { sectionOfId[it.id] != home }) return
        val reordered = reorderedAround(sortedActive, moving, usableBefore, usableAfter) { it.id }
        if (reordered === sortedActive) return
        // Renumber the whole visible sequence rather than patching two rows: a position only means
        // anything relative to its neighbours, so a move is a renumbering by definition. Rows whose
        // number is already right are skipped, so a drag near the top doesn't rewrite the tail.
        AppScope.io.launch {
            reordered.forEachIndexed { index, t ->
                if (t.manualOrder != index) db.taskDao().update(t.copy(manualOrder = index))
            }
        }
        if (sortOption != TaskSort.CUSTOM) {
            scope.launch { settingsRepo.setTasksSort(TaskSort.CUSTOM.key) }
        }
        exitSelection()
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                // Import the whole selection on IO. The only limit now is per file (600 MB) — no cap
                // on combined volume — so each file is checked on its own and rejected individually.
                val (accepted, lastMessage) = withContext(Dispatchers.IO) {
                    var runningPending = pendingAttachments.toList()
                    var message = ""
                    for (uri in uris) {
                        val hint = AttachmentStore.sizeHint(context, uri)
                        if (hint > 0) {
                            val preCheck = AttachmentLimits.checkSingle(hint)
                            if (!preCheck.allowed) { message = preCheck.message; continue }
                        }
                        val newAtt = uriToAttachment(context, uri) ?: run { message = com.lucent.app.i18n.S.couldNotReadOneFile; null } ?: continue
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

    // The single gate through which "check this checkbox" becomes an actual isDone = true write. If
    // it's dismissed for any reason, nothing runs and the task stays exactly as it was.
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
                    // The shared action: marks done, cancels the reminder, and — when the task
                    // repeats — spawns the next occurrence and arms its reminder. Exactly what the
                    // assistant's complete_task does, because it is literally the same function.
                    AppScope.io.launch { TaskActions.complete(context, db, toComplete) }
                }) { Text(com.lucent.app.i18n.S.actionConfirm) }
            },
            dismissButton = { TextButton(onClick = { taskToComplete = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    // Confirm before sending a completed task back to the active list (task 7).
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

    // Confirm-before-pin: pinning (or unpinning) reorders the list, so it asks first.
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
                    taskToTogglePin = null
                    AppScope.io.launch { db.taskDao().update(target.copy(pinned = !target.pinned)) }
                }) { Text(if (willPin) com.lucent.app.i18n.S.actionPin else com.lucent.app.i18n.S.actionUnpin) }
            },
            dismissButton = { TextButton(onClick = { taskToTogglePin = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    // Batch move-to-trash for the multi-selected tasks.
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

    // Resolved against all tasks (not just active) so opening a completed task from the history page
    // works too — but trashed tasks are excluded, so a task trashed by any path makes the detail
    // page fall away rather than keep showing it.
    val viewingTask = remember(allTasks, viewingId) {
        allTasks.firstOrNull { it.id == viewingId && it.trashedAt == null }
    }

    when {
        composing -> {
            // ---- Create / edit page ----
            // Opening the editor of a task that already has subtasks jumps straight to the bottom
            // of its subtask list — the add field under the last item — because that is where
            // editing a checklist almost always continues (settings task B7). Decided once, when
            // this page enters composition (startEdit has already populated the state by then), so
            // later add/remove edits never re-trigger the jump.
            val jumpToLastSubtask = remember { editingTask != null && subtasks.isNotEmpty() }
            val subtaskEndRequester = remember { BringIntoViewRequester() }
            /**
             * Task A12, the task side. A task with no subtasks got no jump at all, so editing one
             * with a long Details body opened at the top exactly as notes did. Same one-shot
             * `remember { }` capture, same threshold, and deliberately *second* in priority: when
             * there are subtasks the add-subtask row is the better landing spot and keeps the
             * behaviour B7 introduced.
             */
            val jumpToDetailsEnd = remember {
                editingTask != null && subtasks.isEmpty() && newNotes.length > DETAILS_JUMP_THRESHOLD
            }
            val detailsEndRequester = remember { BringIntoViewRequester() }
            if (jumpToLastSubtask || jumpToDetailsEnd) {
                LaunchedEffect(Unit) {
                    // Two frames so the scrollable column has laid out before it is asked to move.
                    withFrameNanos { }
                    withFrameNanos { }
                    if (jumpToLastSubtask) subtaskEndRequester.bringIntoView()
                    else detailsEndRequester.bringIntoView()
                }
            }
            // Task A15. Without this the composer's scroll area keeps the full screen height while the
            // keyboard covers its lower half, so the field being typed into can sit *under* the
            // keyboard — and a subtask that grows a line as you type walks further under it with
            // every wrap, forcing a manual scroll mid-sentence. imePadding() shrinks the scrollable
            // area to the space that is actually visible, which is also what lets Compose's built-in
            // "keep the focused field in view" do its job: it can only scroll to something that has
            // somewhere to be scrolled to.
            //
            // On desktop WindowInsets.ime is empty, so this is a no-op there and the two files stay
            // in step.
            Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(composerScroll).imePadding().padding(bottom = LocalBottomBarInset.current)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { leaveComposer() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.lucent.app.i18n.S.actionBack, tint = onGradient)
                    }
                    Text(if (editingTask != null) com.lucent.app.i18n.S.editTask else com.lucent.app.i18n.S.newTask, color = onGradient, fontSize = 20.sp)
                }

                Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text(com.lucent.app.i18n.S.fieldTitle) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Pin-to-top, exactly like a note's pin switch (task 11) — so the two composers
                    // line up rather than burying the task's pin far down among the scheduling
                    // controls.
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
                    // Details field with the same expand toggle the Notes screen uses, so the two
                    // editors are identical in size and behaviour. "Details" rather than "Notes": on a
                    // task, a field literally labelled "Notes" read as redundant ("this note says note").
                    ExpandableGlassTextField(
                        value = newNotes,
                        onValueChange = { newNotes = it },
                        // PHASE 4: dictation — recognized speech appends to whatever is already
                        // written, joined with a single space, never replacing it.
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
                        // Round R1, task 1: doubled - see ExpandableGlassTextField.
                        collapsedMinHeight = 360.dp,
                        collapsedMaxHeight = 960.dp
                    )
                    // Task A12 anchor: the bottom of the Details box, above priority / due date /
                    // attachments. Scrolling to the true bottom of the form would land on the
                    // attachment rows rather than the text being edited.
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
                            // Items are editable in place after being added (settings task B2).
                            onEditText = { item, text -> subtasks = subtasks.map { if (it.id == item.id) it.copy(text = text) else it } },
                            addLabel = com.lucent.app.i18n.S.addSubtask,
                            addRowModifier = Modifier.bringIntoViewRequester(subtaskEndRequester),
                            // Task A18: a blank step opens directly under the one you were on,
                            // instead of every new step landing at the very bottom.
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
                            // Repeat and the reminder both only mean anything while there's a due date
                            // to act on, so clearing the due date clears them too. A task carrying a
                            // repeat rule or a reminder it can never fire isn't a setting, it's a lie.
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
                        onPick = { filePicker.launch("*/*") },
                        onRemove = { att ->
                            pendingAttachments = Attachments.removeByName(context, pendingAttachments, att.name)
                        },
                        // Task A4. Only the display name changes — the row still points at the same
                        // bytes, so nothing is re-encrypted, re-imported, or copied. Uniqueness is
                        // enforced in the dialog because Attachments.upsert/removeByName match on it.
                        onRename = { att, newName ->
                            pendingAttachments = pendingAttachments.map {
                                if (it.data == att.data) it.copy(name = newName) else it
                            }
                        },
                        // Task A11. Attachments are stored as an ordered JSON array, so "sorted by
                        // when it was added" is simply the order they were appended in, and a custom
                        // order needs no extra column — it IS the array, saved with everything else.
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
                        // v2.7.2: the task-configuration controls live one level down. Subtasks are
                        // switched on here; their editor appears on the main level under the details
                        // field. Priority is a decision you make once per task, not per keystroke, so
                        // it waits here rather than taking a row from the page the whole task lives on.
                        Spacer(modifier = Modifier.height(6.dp))
                        // v2.7.2: the subtask switch lives in the fold; the editor appears on the
                        // main level under the details field when switched on. The list is kept in
                        // composer state regardless of the switch, so toggling off and back on never
                        // discards it.
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
                        // v2.7.2: priority waits in the fold with the rest of the configuration —
                        // decide it once per task, and keep the page for the task itself.

                        PriorityPickerRow(selected = priority, onSelect = { priority = it })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    // Task 3. The two actions are now laid out as equal halves of one row: both get
                    // weight(1f) and the same explicit height, so they are identical in size no
                    // matter how long the label is in the current language. Before, the primary was
                    // a Button sized by its content padding and the secondary a `compact` glass pill
                    // sized by its own, smaller, padding — two independent size profiles that only
                    // ever matched by accident, and did not.
                    //
                    // Equal size is not equal weight: the primary stays a filled button and the
                    // secondary stays quiet glass, so the hierarchy the two actions need survives
                    // the geometry the task asks for.
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
                        // Task A10 — "parallel to Save", as asked, and secondary on purpose: it is
                        // the same commitment in a lower gear ("keep this, I'm not done").
                        GlassButton(
                            text = com.lucent.app.i18n.S.saveToDraft,
                            onClick = { saveTask(asDraft = true) },
                            modifier = Modifier.weight(1f).height(COMPOSER_ACTION_HEIGHT)
                        )
                    }
                }
            }
            // ---- Task A7: the floating scroll / quick-edit control ---------------------------
            //
            // Placed in a Box *over* the scrolling column rather than inside it, because a control
            // that scrolls away with the content is exactly the control you cannot reach when you
            // need it — the point of it is to get you somewhere the current scroll position isn't.
            //
            // "Fast scroll, not jump" (the task is explicit) is why these animate: animateScrollTo
            // travels the distance so the reader keeps their bearings, where scrollTo would
            // teleport them somewhere unfamiliar.
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
                // With a selection the style is applied to it, exactly as before. WITHOUT one the
                // same tap arms the style for whatever is typed next, which is what makes the
                // toolbar usable on an empty note — previously it just said "select something first".
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
                        // Light and bold are one axis: arming one disarms the other, the same rule
                        // RichText.toggle() enforces on a selection.
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
                // Task 1. The bottom offset was a hard-coded 96dp, but the floating nav capsule is
                // taller than that (its own height plus the gap above it plus the system navigation
                // inset), so the control sat on top of the capsule and stole its taps. The measured
                // inset is already published as [LocalBottomBarInset] — the same value every list
                // screen reserves — so use it and lift the ring clear of the capsule on every device
                // rather than on the one this number was eyeballed against.
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = LocalBottomBarInset.current + 14.dp)
            )
        }
        }

        historyForTaskId != null && viewingTask != null -> {
            // Task A19 — same route shape the notes screen uses for its history.
            TaskHistoryScreen(
                task = viewingTask,
                onBack = { historyForTaskId = null },
                onRestored = { historyForTaskId = null }
            )
        }

        viewingTask != null -> {
            // Hoisted so the jump-to-edge buttons below can drive it (task 8).
            val detailScroll = rememberScrollState()
            // ---- Read-only detail page ----
            val task = viewingTask
            val attachments = remember(task.attachments) { Attachments.parse(task.attachments) }
            val subtaskItems = remember(task.subtasks) { Checklist.parse(task.subtasks) }
            val taskPriority = remember(task.priority) { TaskPriority.fromValue(task.priority) }
            val taskRepeat = remember(task.repeatRule) { RepeatRule.fromKey(task.repeatRule) }
            val overdue = isOverdue(task.dueAt, task.isDone)

            // Swipe horizontally to move between tasks in the current (sorted, filtered) order:
            // swipe left for the next, right for the previous. Does nothing at the ends or for a task
            // not in the visible active list (e.g. one opened from history).
            //
            // The gesture lives on this full-page Box, not the inner scrolling Column, so it works
            // anywhere on the page rather than only over the body (task 7). The top band (back / title
            // / action header) and the bottom band are excluded: a drag is only armed when it *starts*
            // in the central band. The inner Column keeps owning vertical scrolling.
            // ---- Animated page-to-page swipe (task 7) ----
            //
            // The gesture already worked; what it lacked was any sense of movement. A drag past the
            // threshold simply swapped the content, so the next task appeared fully formed with no
            // indication that anything had travelled — indistinguishable, from the user's side, from
            // the page having been redrawn for some unrelated reason. Direction is the whole meaning
            // of a horizontal swipe, and the old version showed none of it.
            //
            // So the page now follows the finger and completes the journey on release:
            //
            //  - **While dragging**, the content tracks the finger 1:1 and fades slightly as it goes,
            //    so the gesture is reversible — let go early and it springs back, which is the only
            //    way a user can discover the threshold without being punished for finding it.
            //  - **At the ends of the list** the drag is damped to a third (RESIST) instead of being
            //    ignored. A dead gesture reads as a broken one; a stiff one reads as a wall, which is
            //    what it actually is.
            //  - **On release past the threshold** the outgoing page finishes its exit in the
            //    direction of travel, then the incoming page enters from the opposite edge. Exit is
            //    faster than entry (accelerating out, decelerating in) — the standard asymmetry that
            //    makes the pair read as one continuous movement rather than two separate slides.
            //
            // [swipeOffset] is deliberately remembered WITHOUT a key on the task id: it has to
            // survive the content swap in the middle of the animation, because the exit and the entry
            // are two halves of one gesture, not two animations.
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
                                            // Not far enough, or nothing to move to: spring home.
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
                                    // Damp the drag when there is nothing in that direction to reach,
                                    // so the end of the list feels like a wall rather than a fault.
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
                        // A gentle fade tied to how far the page has travelled. It never reaches
                        // fully transparent during a drag — a page you can still see is a page you
                        // can still change your mind about.
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

                    // The action strip (task 6). A task's detail page used to offer three actions
                    // where a note's offered five, so the two pages behaved differently for no reason
                    // anyone could see: the two things you most want to do to a task — edit it, or
                    // tick it off — were the two that weren't here. Both are now in the strip, which
                    // scrolls horizontally exactly like the note page's so nothing is clipped on a
                    // narrow screen.
                    // ---- Task A6: the action strip folds away, and copy is explicit ----
                    //
                    // Two changes that belong together. Long-pressing this page used to copy the
                    // *entire* item — useful once, useless whenever what you wanted was one line,
                    // and silent either way, so there was no way to tell it had happened. A long
                    // press now starts an ordinary text selection, and "copy the whole thing"
                    // becomes a button that says so and confirms itself.
                    //
                    // The strip follows the home page's collapse control: six icons across the top
                    // of a page whose job is *reading* are six things competing with the text.
                    // Collapsed by default for the same reason the home bar is — the actions are
                    // one tap away, and the item is what you came for.
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
                        // Pinning only affects the active list, so it's hidden for a completed task —
                        // which lives in history, where pinning would mean nothing.
                        if (!task.isDone) {
                            PinIconButton(
                                pinned = task.pinned,
                                onToggle = { taskToTogglePin = task }
                            )
                        }
                        // The completion mark: tick a pending task off, or send a finished one back.
                        // It replaces the checkbox that used to sit inside the card below (see
                        // there), and like the checkbox it never writes anything itself — both
                        // directions go through their confirmation dialog first.
                        IconButton(onClick = {
                            if (task.isDone) taskToRestore = task else taskToComplete = task
                        }) {
                            Icon(
                                if (task.isDone) Icons.AutoMirrored.Filled.Undo else Icons.Default.CheckCircle,
                                contentDescription = if (task.isDone) com.lucent.app.i18n.S.markNotDone else com.lucent.app.i18n.S.markDone,
                                tint = onGradient
                            )
                        }
                        // Edit — the same action as the button at the bottom of the page, brought up
                        // here so it's reachable without scrolling past a long set of details.
                        IconButton(onClick = { startEdit(task) }) {
                            Icon(Icons.Default.Edit, contentDescription = com.lucent.app.i18n.S.actionEdit, tint = onGradient)
                        }
                        IconButton(onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, task.title.ifBlank { "Task" })
                                putExtra(Intent.EXTRA_TEXT, shareTextForTask(task))
                            }
                            context.startActivity(Intent.createChooser(sendIntent, com.lucent.app.i18n.S.shareTaskChooser))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = com.lucent.app.i18n.S.actionShare, tint = onGradient)
                        }
                        // Task A19 — offered only once there is something in it, so a task that has
                        // never been edited doesn't advertise an empty screen.
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
                        // The fold toggle: same chevron, same direction as the home bar's, so the
                        // control means one thing everywhere in the app.
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

                // Long-press anywhere on this card to copy the task's content (task 1). Same
                // reasoning as the note detail page: the whole card is the target so a press that
                // lands in the padding still counts, and the SelectionContainer inside still wins
                // long-press over the title/date block for native text selection.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlass()
                        .padding(16.dp)
                ) {
                    // No checkbox here any more (task 6). A tick-box on the detail page was a second
                    // control for something the header now owns, and — because the page is *read*
                    // rather than scanned — it was the one most likely to be hit by accident while
                    // scrolling. The box belongs on the home list, where ticking things off is the
                    // whole activity; here, completion is the header's deliberate, confirmed action.
                    //
                    // Title/dates/notes sit in a SelectionContainer for the native selection toolbar.
                    // The subtask checklist and the attachments stay outside it so their taps still
                    // reach them.
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
                                    Text(task.notes, color = onGradient)
                                }
                            }
                        }
                    }

                    if (subtaskItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ChecklistView(
                            items = subtaskItems,
                            header = com.lucent.app.i18n.S.labelSubtasks,
                            // A completed task is locked along with its subtasks, so the checkboxes
                            // become genuinely non-interactive rather than merely greyed out.
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
                        // Task A4: renaming touches only the display name in this row's JSON — the
                        // bytes stay where they are, keyed by the same store id. Written straight to
                        // the row because the detail page has no draft to fold it into.
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
                // Full-width bottom action (task 17): the Edit button used to size to its content
                // (~half the screen). Stretching it to the full width makes it a clear primary action
                // and matches the note editor's bottom row. The completed-task "undo" control uses the
                // same width so the slot looks identical in both states.
                if (task.isDone) {
                    GlassCapsuleButton(
                        text = com.lucent.app.i18n.S.markNotDone,
                        icon = Icons.AutoMirrored.Filled.Undo,
                        // Asks first (task 7) rather than firing on the tap.
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
                // Task 8 — the same jump-to-edge control the home lists carry. A detail page can be
                // far longer than the editor that produced it (body, checklist, attachments, links,
                // backlinks, version count), and it was the one long scrolling surface in the app
                // with no way to get to the other end of it without dragging.
                //
                // Placed as the Box's second child, after the scrolling Column, so it floats over the
                // page instead of scrolling away with it — the same arrangement the home grid uses.
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
            // ---- Hidden area (task A21) ----
            HiddenTasksScreen(
                onBack = { showHidden = false },
                onOpen = { showHidden = false; openDetail(it) }
            )
        }

        showDrafts -> {
            // ---- Draft area (task A10) ----
            DraftTasksScreen(
                onBack = { showDrafts = false },
                // Opening a draft puts it straight back in the editor — the list exists to get you
                // back to unfinished work, not to display it. It stays flagged as a draft until the
                // user either saves it properly or moves it out explicitly.
                onOpen = { showDrafts = false; startEdit(it) }
            )
        }

        showSearch -> {
            // The same unified search the Notes tab hosts — one screen, reachable from either side,
            // because "where did I put that" is not a question that knows which tab it belongs to.
            SearchScreen(
                // A note lives on the other tab, so this is a cross-tab jump: it records that it
                // started here (task 4) and deliberately leaves this search *open* behind it, so
                // closing the note comes back to these results rather than to a blank list.
                onOpenNote = { note -> AppNavigation.openNote(note.id, from = Screen.Tasks) },
                onOpenTask = { task ->
                    showSearch = false
                    viewingId = task.id
                    returnToOnClose = null
                },
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
                        // Task A21 — hide (or un-hide) everything ticked. Offered whenever the
                        // hidden area is open; while it is closed the action would move items
                        // somewhere the user currently has no way to reach, which is a trap
                        // rather than a feature.
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
                            onClick = { if (selectedTaskIds.isNotEmpty()) showBatchDeleteConfirm = true },
                            enabled = selectedTaskIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = com.lucent.app.i18n.S.a11yDeleteSelected, tint = onGradient)
                        }
                    }
                } else {
                    // Header with a collapsible action cluster (settings task 16), synthesized with the
                    // tasks/notes header changes: the "+" stays put; the date-range filter, sort and
                    // overflow buttons tuck behind the "<" chevron and slide out to the left on tap while
                    // the search box smoothly resizes. No placeholder text in the field (tasks items
                    // 6 & 15) so nothing can be clipped; the leading icon conveys its purpose.
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
                            // Completed history, Trash and Select live behind one overflow menu, so the
                            // bar doesn't grow an icon each time another action appears.
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = com.lucent.app.i18n.S.a11yMoreOptions, tint = onGradientMuted)
                                }
                                DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                                    // "Select tasks" is no longer here — selection is entered by
                                    // long-pressing a task (see onLongPress below). The overflow menu
                                    // now holds only the three navigation destinations.
                                    DropdownMenuItem(
                                        text = { Text(com.lucent.app.i18n.S.searchEverything) },
                                        leadingIcon = { Icon(Icons.Default.TravelExplore, contentDescription = null) },
                                        onClick = { showOverflowMenu = false; showSearch = true }
                                    )
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
                                    // Task A21: absent, not disabled. A greyed-out "Hidden" entry
                                    // would announce that a hidden area exists to anyone holding
                                    // the phone, which is the one thing it must not do.
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

                // One scrollable region, always, filling everything below the header (task 5).
                //
                // It used to be either an EmptyState *or* a wrap-height LazyColumn, and both leaked
                // the same bug: vertical drags are only picked up inside the scrollable container's
                // bounds, so with no tasks (no container at all) or a short list (a container only as
                // tall as its cards) the rest of the page was dead to touch. That matters more than
                // it sounds, because this page's scrolling is what raises and lowers the "Tasks"
                // title in the top bar — so anyone who scrolled the header away and then cleared or
                // filtered the list was left with a hidden title and no gesture anywhere on screen
                // that could bring it back.
                //
                // fillMaxSize() makes the list own the whole area, and the empty state rides inside
                // it as a full-height item, so a drag started anywhere below the header is heard —
                // and the header can always be pulled back down, whether there are zero tasks or two
                // hundred.
                // Wrapped in a Box so the scroll-edge jump buttons can overlay the list (task E2).
                Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    // Reserve the floating capsule's height at the bottom so the last card can
                    // scroll clear of the pill instead of ending up trapped behind it — the list
                    // itself still extends under the capsule (that's what its blur samples).
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
                                reorderVisualModifier = itemModifier.reorderVisuals(task.id, reorderState),
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
                                        // Same entry as before: the press selects. Adding to an
                                        // existing selection rather than replacing it is what lets
                                        // several ticked cards travel together.
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

/**
 * One active task as a card.
 *
 * Checkbox, priority dot, title, created/due times, and a compact meta line for repeat and subtask
 * progress. Attachments and the full checklist are one tap away on the detail page — a card that
 * tries to show everything shows nothing.
 *
 * The checkbox is deliberately never flipped in state here: its handler only *arms* the confirmation
 * dialog, and the database write happens after the user confirms. That's the difference between
 * "tap to complete" and "tap to complete by accident".
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(
    task: Task,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    // Task A16. When reordering is live the long press belongs to [reorderModifier] instead — it
    // starts the same selection *and* keeps following the finger. Two detectors both claiming the
    // long press on one card is the classic way to end up with neither working, so exactly one is
    // installed at a time.
    reorderEnabled: Boolean = false,
    reorderModifier: Modifier = Modifier,
    // Applied FIRST — see the notes card twin and [reorderVisuals].
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
    // Cache the "created" label keyed on the raw value (task 8): same rationale as NoteCard — avoid
    // re-formatting a Date on every recomposition during a scroll when the value hasn't changed.
    val createdLabel = rememberFormattedTimestamp(task.createdAt)
    val overdue = isOverdue(task.dueAt, task.isDone)
    val selShape = RoundedCornerShape(20.dp)

    Column(
        modifier = reorderVisualModifier
            .fillMaxWidth()
            .frostedGlass()
            // Long press enters multi-select and grabs this card; in select mode a tap toggles it.
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
                // While selecting, a tick stands in for the checkbox so a tap can't both select and
                // arm completion.
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
                        // The home list never contains completed tasks, so unchecking is unreachable.
                        if (checked) onArmComplete()
                    }
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.pinned) {
                        PinnedMarker(modifier = Modifier.padding(end = 4.dp))
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
                PinIconButton(pinned = task.pinned, onToggle = onTogglePin)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = com.lucent.app.i18n.S.actionDelete, tint = onGradient)
                }
            }
        }
    }
}

/** A section header ("Recent" / "Today" / "Older") between task rows on the home list. */
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

/** Plain-text rendering of a task for the OS share sheet. */
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

/**
 * What a long-press on the detail page copies: the task's CONTENT and nothing else.
 *
 * Deliberately not [shareTextForTask]. Sharing describes a task to someone else, so it leads with
 * the title and carries the due date, priority and repeat rule as context. Copying is a step in
 * moving text into something you are already writing, and there the title, the created/due dates
 * and the badges are precisely what you would have to strip out by hand at the other end.
 *
 * So this is the description text, plus the subtask list when there is one — the two parts of a
 * task that are actually *written* rather than *set*. A task with neither copies as an empty
 * string, and [longPressCopy] no-ops on blank text rather than showing a "copied" toast for
 * nothing.
 */
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

/**
 * Sets (or clears) a due date and time, floored so it can never land before [minMillis] — a task's
 * due date can't predate its own creation. The floor is applied twice: `datePicker.minDate` blocks
 * picking an earlier calendar day outright, and `coerceAtLeast` catches the one case that slips
 * through it (same day, earlier time of day) once a time is chosen.
 */
@Composable
private fun DueDateRow(dueAt: Long?, minMillis: Long, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    fun openPicker() {
        val base = Calendar.getInstance().apply { timeInMillis = dueAt ?: minMillis }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val chosen = Calendar.getInstance().apply {
                    timeInMillis = base.timeInMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        chosen.set(Calendar.HOUR_OF_DAY, hour)
                        chosen.set(Calendar.MINUTE, minute)
                        chosen.set(Calendar.SECOND, 0)
                        chosen.set(Calendar.MILLISECOND, 0)
                        onChange(chosen.timeInMillis.coerceAtLeast(minMillis))
                    },
                    base.get(Calendar.HOUR_OF_DAY),
                    base.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(context)
                ).show()
            },
            base.get(Calendar.YEAR),
            base.get(Calendar.MONTH),
            base.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.minDate = minMillis }.show()
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

/** Task A12 — see NotesScreen's BODY_JUMP_THRESHOLD; the two composers use the same rule. */
private const val DETAILS_JUMP_THRESHOLD = 400
