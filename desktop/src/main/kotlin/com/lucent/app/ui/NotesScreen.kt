package com.lucent.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalWindowInfo
import com.lucent.desktop.platform.DesktopFiles
import com.lucent.desktop.platform.DesktopShare
import java.io.File
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
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.lucent.app.data.Note
import com.lucent.app.data.NoteHistory
import com.lucent.app.data.NoteLinks
import com.lucent.app.data.NoteTemplate
import com.lucent.app.data.SearchQuery
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.TrashCleanup
import com.lucent.app.data.filterBySearch
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Task 3.2 — the starter tags are canonical values now, not translated display strings. See
// [com.lucent.app.data.NoteTags] for why storing the translation was the bug.
private val DEFAULT_TAGS: List<String>
    get() = com.lucent.app.data.NoteTags.DEFAULTS

@Composable
fun NotesScreen(active: Boolean = true) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val settingsRepo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    // The list flows are remembered so their Room subscription is stable across recompositions.
    // Creating a fresh Flow on every recomposition (as this used to) could drop the invalidation
    // that a just-inserted note fires, which is why a new note sometimes didn't appear until the
    // user left the tab and came back. A single stable subscription reliably re-emits on insert, so
    // a new note now shows immediately (see feature: reactive UI for new notes).
    val notes by remember { db.noteDao().getAll() }.collectAsState(initial = com.lucent.app.data.DataCache.notes)
    // Archived notes are collected too so that a note opened from the archive screen can still be
    // resolved for its detail page (the `notes` list above excludes archived notes by design).
    val archivedNotes by remember { db.noteDao().getArchived() }.collectAsState(initial = emptyList())
    // Hidden (private-area) notes are collected too, for the same reason: the two lists above
    // exclude them at the SQL layer (hidden = 0), so without this third stream tapping a hidden
    // note could never resolve to a detail page (R3 report). The task side always worked because
    // its pool is unfiltered.
    val hiddenNotes by remember { db.noteDao().getHidden() }.collectAsState(initial = emptyList())
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    // Remembered sort choice, persisted across app restarts (see SettingsRepository.notesSort).
    //
    // Round R1, task 2: the initial value is the one read synchronously at startup, NOT the literal
    // "recent". With the literal, a user whose saved sort is Custom got a first frame ordered by
    // recency and a second frame ordered by their own arrangement — and `Modifier.animateItem`
    // dutifully animated the difference, replaying their reordering at them on every single launch.
    // Seeding from the startup read means frame one is already right. See data/SettingsCache.
    val sortKey by settingsRepo.notesSort
        .collectAsState(initial = com.lucent.app.data.SettingsCache.notesSort ?: "recent")
    val sortOption = NoteSort.fromKey(sortKey)

    // Whether Markdown formatting is on. Off by default (plain-text mode): the body renders
    // verbatim and the composer drops the "Markdown supported" hint. See SettingsRepository.
    val markdownEnabled by settingsRepo.markdownEnabled.collectAsState(initial = false)
    // Whether links are on. Independent of Markdown now (task 8): a link is a *navigation* feature,
    // not a formatting one, and the old `markdown && links` coupling meant anyone who preferred
    // plain text also silently lost their entire note graph — every [[link]] they had written became
    // dead text, and the Links switch in Settings sat greyed out with no explanation of why.
    // Plain-text mode now still resolves links; it just doesn't style anything else.
    val linksEnabled by settingsRepo.linksEnabled.collectAsState(initial = true)
    val linksActive = linksEnabled

    // Frequency scores for the "Recent" home section (id -> activity), see UsageTracker.
    val noteUsage by remember { com.lucent.app.data.UsageTracker.scores(context, com.lucent.app.data.UsageTracker.Kind.NOTE) }
        .collectAsState(initial = emptyMap())

    // View modes: the clean home grid, a read-only detail page, the create/edit composer, the
    // archive, the trash, and a note's revision history. Tapping a note opens its detail page; an
    // Edit button there opens the composer. New notes are created straight in the composer.
    var composing by remember { mutableStateOf(false) }
    var viewingId by remember { mutableStateOf<Long?>(null) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var newBody by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var newCustomTag by remember { mutableStateOf("") }
    var pendingAttachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var pinned by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(NoteColor.DEFAULT) }
    var isChecklistMode by remember { mutableStateOf(false) }
    // Task A22 — the third note kind. A second flag rather than an enum for the same reason the
    // column is (see Note.isDoodle): the two existing kinds keep working untouched.
    var isDoodleMode by remember { mutableStateOf(false) }
    var doodleData by remember { mutableStateOf("") }
    var checklistItems by remember { mutableStateOf<List<ChecklistItem>>(emptyList()) }
    var newChecklistItemText by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    // Optional date-range filter (start-of-day, end-of-day millis), set from the calendar button next
    // to the search box. Null means no date filter. A note carries only one timestamp (updatedAt), so
    // it doubles as the note's creation time for this filter. The end can't precede the start (the
    // picker enforces it).
    var dateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    // Pin/unpin now asks first (see the confirmation dialog below), so a stray tap on the pin icon
    // can't silently reorder the list. Holds the note whose pin state is pending confirmation.
    var noteToTogglePin by remember { mutableStateOf<Note?>(null) }
    // Archiving (or restoring) moves a note between the home grid and the archive — it vanishes from
    // wherever you were looking — so it now asks first (task 7). One piece of state covers both
    // directions because the note itself already knows which way it's going.
    var noteToToggleArchive by remember { mutableStateOf<Note?>(null) }
    var showArchive by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    // Task A10 — the draft area, reached from the same overflow menu as the trash.
    var showDrafts by remember { mutableStateOf(false) }
    // Task A21 — the hidden area. Only offered while HiddenArea.visible, which the Privacy switch
    // sets and which resets itself on every launch.
    var showHidden by remember { mutableStateOf(false) }
    // Task A10 — how many drafts are waiting, for the once-per-launch restore prompt below.
    val draftCount by db.noteDao().getDrafts().collectAsState(initial = emptyList())
    DraftRestoreDialog(draftCount = draftCount.size, onOpenDrafts = { showDrafts = true })
    // Round R1, task 5 — the "go back to where you were?" prompt. Shown by whichever home tab
    // composes first regardless of which one the snapshot belongs to; see SessionRestoreDialog.
    SessionRestoreDialog()
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    // Whether the header's secondary-action cluster (date filter, sort, overflow) is expanded.
    // Collapsed by default so the search box is full width; survives config changes (task 16).
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }
    // Which note's revision history is open, if any.
    var historyForId by remember { mutableStateOf<Long?>(null) }

    // Hoisted above the view-mode `when` so the home grid's scroll position survives opening a note
    // and coming back — the grid composable is disposed while the detail page is up, but this state
    // object lives on with the screen, so returning restores the exact scroll offset instead of
    // snapping to the top (see feature: preserve scroll position).
    // Task 4 — keep the history mirrors in step with the stored switches. Done from both list
    // screens because either one may be the tab the app opens on, and NoteHistory.recordIfChanged
    // has no Context of its own to read the setting with. Idempotent, so doing it twice is free.
    LaunchedEffect(Unit) {
        settingsRepo.noteHistoryEnabled.collect { com.lucent.app.data.NoteHistory.enabled = it }
    }
    LaunchedEffect(Unit) {
        settingsRepo.taskHistoryEnabled.collect { com.lucent.app.data.TaskHistory.enabled = it }
    }
    val gridState = rememberLazyGridState()

    // Long-press multi-select for batch actions (see feature: batch operations). When active, cards
    // show a checkbox and tapping toggles selection instead of opening.
    // ---- Task A7 state ----------------------------------------------------------------------
    // Hoisted so the floating control can drive it; the column below still owns the scrolling.
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
    // v2.7.2: the "More options" fold of the composer. rememberSaveable so rotation keeps it open
    // while a fresh composer always starts collapsed (same rule as the fold's doc comment).
    var composerMoreOpen by rememberSaveable(composing) { mutableStateOf(false) }
    // v2.7.2: authoring a USER template from the template strip, and the id of a custom template
    // whose chip was long-pressed (delete confirm). Neither survives leaving the screen.
    var templateAuthoring by remember { mutableStateOf(false) }
    var pendingTemplateDeleteId by remember { mutableStateOf<String?>(null) }
    // One undo stack per composer session, following the body/details field. Seeded from whatever
    // the field holds when the editor opens, so the first undo returns to the text as it was found
    // rather than to an empty string.
    val bodyUndo = remember(composing) { TextUndoStack(newBody) }
    // ---- INTEGRATION: C-group task 20, rich text ----
    // The spans for the field above, plus the current selection. Both are plain composer state, in
    // the same place and with the same lifetime as the text they describe — a span list that
    // outlived its body would style whatever text happened to be there next.
    // First-compile fix (CI 2026-07-26): the rich-text state below reads settings via `repo`,
    // which was never declared in this screen on either platform — the integration referenced a
    // name from another screen. Same construction the rest of the app uses.
    val repo = remember { com.lucent.app.data.SettingsRepository(context) }
    val richTextEnabled by repo.richTextEnabled.collectAsState(initial = false)
    // v2.7.2: user templates + interrupted-authoring draft (see data/CustomTemplates.kt).
    val customTemplatesJson by repo.customTemplatesJson.collectAsState(initial = "[]")
    val customTemplates = remember(customTemplatesJson) {
        com.lucent.app.data.CustomTemplates.parse(customTemplatesJson)
    }
    val templateDraftJson by repo.templateDraftJson.collectAsState(initial = "")
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
    var spansText by remember(composing) { mutableStateOf(newBody) }
    LaunchedEffect(newBody) {
        if (spansText != newBody) {
            bodySpans = com.lucent.app.data.RichText.applyEdit(
                bodySpans, spansText, newBody, pendingKinds, pendingHighlight, pendingColor, pendingExplicit
            )
            spansText = newBody
        }
    }
    LaunchedEffect(newBody) { bodyUndo.record(newBody) }
    // Task A16 — see ReorderDrag.kt; enabled only under the Custom sort.
    val reorderState = rememberReorderDragState()
    var selectionMode by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    // Task 1 — the card's new pin control reuses the screen's EXISTING `noteToTogglePin` state and
    // its confirmation dialog (both declared above, where the detail page's pin action already used
    // them). Adding a second copy is what broke the build: two `var noteToTogglePin` in one scope.
    fun exitSelection() { selectionMode = false; selectedNoteIds = emptySet() }

    // A note asked for from elsewhere — a unified-search result tapped while on another tab. Consumed
    // once, so returning to this tab later doesn't helpfully reopen a note the user closed long ago.
    // Where to go when the note currently open was reached from *another* tab (task 4).
    var returnToOnClose by remember { mutableStateOf<Screen?>(null) }

    LaunchedEffect(AppNavigation.pendingNoteId) {
        AppNavigation.consumeNoteId()?.let { id ->
            showSearch = false
            showArchive = false
            showTrash = false
            viewingId = id
            // Consumed together with the id, so closing this note returns to the tab the user was
            // actually on when they went looking for it.
            returnToOnClose = AppNavigation.consumeReturnScreen()
        }
    }

    var showUnsavedDialog by remember { mutableStateOf(false) }

    fun resetComposer() {
        editingId = null
        newTitle = ""
        newBody = ""
        bodySpans = emptyList()
        selectedTags = emptySet()
        newCustomTag = ""
        pendingAttachments = emptyList()
        pinned = false
        selectedColor = NoteColor.DEFAULT
        isChecklistMode = false
        isDoodleMode = false
        doodleData = ""
        checklistItems = emptyList()
        newChecklistItemText = ""
    }

    fun startCreate(prefillTitle: String = "") {
        resetComposer()
        newTitle = prefillTitle
        composing = true
    }

    // A home-screen widget "new note" tap routes here (task 9): open a fresh composer, exactly once.
    // Consumed so returning to the tab later doesn't reopen an empty composer.
    LaunchedEffect(AppNavigation.composeNoteRequested) {
        if (AppNavigation.consumeComposeNote()) {
            showSearch = false
            showArchive = false
            showTrash = false
            viewingId = null
            startCreate()
        }
    }

    /** Fill the composer from a one-tap template. Only ever offered on a *new*, still-empty note. */
    fun applyTemplate(template: NoteTemplate) {
        val prefill = template.prefill()
        newTitle = prefill.title
        newBody = prefill.body
        selectedTags = prefill.tags
        isChecklistMode = prefill.isChecklist
        checklistItems = prefill.checklist
    }

    // ================= v2.7.2: user-defined templates =================

    /** The composer's current fields as a template draft (mirrors [CustomTemplates.Draft]). */
    fun currentTemplateDraft(): com.lucent.app.data.CustomTemplates.Draft =
        com.lucent.app.data.CustomTemplates.Draft(
            title = newTitle,
            body = newBody,
            tags = selectedTags.toList(),
            colorKey = selectedColor.key,
            pinned = pinned,
            isChecklist = isChecklistMode,
            checklistTexts = checklistItems.map { it.text }
        )

    /** Restore the composer from a stored draft and enter authoring mode. */
    fun startTemplateAuthoring(fromDraft: com.lucent.app.data.CustomTemplates.Draft?) {
        resetComposer()
        if (fromDraft != null) {
            newTitle = fromDraft.title
            newBody = fromDraft.body
            selectedTags = fromDraft.tags.toSet()
            pinned = fromDraft.pinned
            selectedColor = fromDraft.colorKey?.let { key ->
                NoteColor.entries.firstOrNull { it.key == key }
            } ?: NoteColor.DEFAULT
            isChecklistMode = fromDraft.isChecklist
            checklistItems = fromDraft.checklistTexts.map { Checklist.newItem(it) }
        }
        templateAuthoring = true
    }

    /** Leave authoring on purpose: the unfinished fields are discarded and the stored draft cleared. */
    fun discardTemplateAuthoring() {
        templateAuthoring = false
        resetComposer()
        scope.launch { repo.setTemplateDraftJson("") }
    }

    /** Persist the interrupted authoring session (or clear it when nothing is left to keep). */
    fun persistTemplateDraft() {
        val draft = currentTemplateDraft()
        if (com.lucent.app.data.CustomTemplates.draftEmpty(draft)) {
            scope.launch { repo.setTemplateDraftJson("") }
        } else {
            scope.launch { repo.setTemplateDraftJson(com.lucent.app.data.CustomTemplates.draftToJson(draft)) }
        }
    }

    /** Apply one of the user's saved templates to the blank new-note composer. */
    fun applyCustomTemplate(t: com.lucent.app.data.CustomTemplates.Template) {
        newTitle = t.title
        newBody = t.body
        selectedTags = t.tags.toSet()
        pinned = t.pinned
        selectedColor = t.colorKey?.let { key ->
            NoteColor.entries.firstOrNull { it.key == key }
        } ?: NoteColor.DEFAULT
        isChecklistMode = t.isChecklist
        checklistItems = t.checklistTexts.map { Checklist.newItem(it) }
    }

    /** Save the authoring session as a new custom template (name = its title). */
    fun saveCustomTemplate() {
        if (newTitle.isBlank()) {
            LucentToast.show(context.applicationContext, com.lucent.app.i18n.S.tplNeedsTitle)
            return
        }
        val t = com.lucent.app.data.CustomTemplates.Template(
            id = java.util.UUID.randomUUID().toString(),
            name = newTitle.trim(),
            title = newTitle,
            body = newBody,
            tags = selectedTags.toList(),
            colorKey = selectedColor.key,
            pinned = pinned,
            isChecklist = isChecklistMode,
            checklistTexts = checklistItems.map { it.text }
        )
        val previous = customTemplatesJson
        templateAuthoring = false
        resetComposer()
        scope.launch {
            repo.setCustomTemplatesJson(com.lucent.app.data.CustomTemplates.upsert(previous, t))
            repo.setTemplateDraftJson("")
        }
        LucentToast.show(context.applicationContext, com.lucent.app.i18n.S.tplSavedToast)
    }

    // While authoring, every pause in typing keeps the draft store current, so an app kill cannot
    // eat an unfinished template any more than a deliberate exit can (the exit paths call
    // [persistTemplateDraft] directly — this effect is the crash belt).
    val authoringSignature = if (templateAuthoring && editingId == null) {
        listOf(
            newTitle, newBody, selectedTags.joinToString(","),
            pinned.toString(), selectedColor.key, isChecklistMode.toString(),
            checklistItems.joinToString("|") { it.text }
        ).joinToString("\u0001")
    } else null
    LaunchedEffect(authoringSignature) {
        if (authoringSignature != null) {
            kotlinx.coroutines.delay(400)
            val draft = currentTemplateDraft()
            if (com.lucent.app.data.CustomTemplates.draftEmpty(draft)) repo.setTemplateDraftJson("")
            else repo.setTemplateDraftJson(com.lucent.app.data.CustomTemplates.draftToJson(draft))
        }
    }

    /**
     * Close the detail page, returning to the tab this note was opened *from* if it came from
     * another one (task 4). For an ordinary in-tab open there is no origin and this is simply
     * "back to the grid", exactly as before.
     */
    fun closeDetail() {
        viewingId = null
        val back = returnToOnClose
        returnToOnClose = null
        if (back != null) AppNavigation.requestScreen(back)
    }

    fun openDetail(note: Note) {
        viewingId = note.id
        // An in-tab open is not a cross-tab trip; drop any stale return so it can't fire later.
        returnToOnClose = null
        // Feed the "Recent" section: opening a note counts toward how active it is.
        AppScope.io.launch {
            com.lucent.app.data.UsageTracker.recordOpen(context, com.lucent.app.data.UsageTracker.Kind.NOTE, note.id)
        }
    }

    fun startEdit(note: Note) {
        editingId = note.id
        newTitle = note.title
        newBody = note.body
        // INTEGRATION (C task 20): load the sidecar, clamped to the body it describes.
        bodySpans = com.lucent.app.data.RichText.load(note.bodySpans, note.body)
        selectedTags = com.lucent.app.data.NoteTags.parse(note.tags).toSet()
        newCustomTag = ""
        pendingAttachments = Attachments.parse(note.attachments)
        pinned = note.pinned
        selectedColor = NoteColor.fromKey(note.color)
        isChecklistMode = note.isChecklist
        isDoodleMode = note.isDoodle
        doodleData = note.doodle
        checklistItems = Checklist.parse(note.checklist)
        newChecklistItemText = ""
        composing = true
    }

    // Task A10: which drafts row this composer session owns, if it has parked anything yet.
    // Keyed on the session (composing/editingId) so a fresh composer never adopts the previous
    // one's row and starts silently overwriting a draft the user meant to keep.
    var draftRowId by remember(composing, editingId) { mutableStateOf<Long?>(null) }
    fun saveNote(
        // Task A10. `closeAfter` defaults to the opposite of `asDraft` because the two callers want
        // opposite things: the explicit "Save to drafts" button is the user finishing with this
        // screen, while the automatic park on backgrounding must leave the editor exactly as it
        // was — still open, still dirty, still able to save properly if they come straight back.
        asDraft: Boolean = false,
        closeAfter: Boolean = !asDraft
    ) {
        // A checklist item typed into the add field but never committed with "+" still counts
        // (settings tasks B1/B4, checklist-note side): it is folded in here, so typing the last
        // item and hitting Save directly never silently drops it. Only in checklist mode — that is
        // the only mode in which the add field is even on screen.
        val pendingItem = newChecklistItemText.trim()
        // Round R2, task 2 — a row with nothing written on it is not an item.
        //
        // "Add another one" is a single tap, and changing your mind afterwards is not: the blank
        // row stayed, saved, and came back every time the note was opened, so the only way to be
        // rid of it was to notice it and delete it by hand. Pruning on save is the honest reading —
        // an empty row is the *absence* of an item, not an item that happens to be empty — and it
        // is done unconditionally rather than only in checklist mode, because a list left behind by
        // switching the mode off should not smuggle blanks back in later.
        val composedChecklist = (
            if (isChecklistMode && pendingItem.isNotEmpty()) checklistItems + Checklist.newItem(pendingItem)
            else checklistItems
            ).filter { it.text.isNotBlank() }
        // Task 2 — a doodle counts as content.
        //
        // This guard decides "there is nothing here, close without saving", and it asked about the
        // title, the body, the attachments and the checklist — every kind of content the note can
        // hold EXCEPT the drawing. So a note whose only content was a drawing matched "empty" and
        // was discarded on save, silently, every time. Checklist-only notes were already covered by
        // `composedChecklist`, which is why only the doodle case failed.
        // Round R2, task 2 — the same rule for canvases, plus the multi-canvas fix.
        //
        // `Doodle.isEmpty` understands one bare stroke array, not the multi-canvas container, so it
        // answered "empty" for EVERY note with two or more canvases: a doodle-only note with two
        // canvases matched the "nothing here" guard below and was discarded on save, silently.
        // DoodlePages answers for the column, and prunes the canvases nobody drew on.
        val prunedDoodle = com.lucent.app.ui.DoodlePages.pruneEmpty(doodleData)
        val hasDoodle = isDoodleMode && prunedDoodle.isNotBlank()
        if (newTitle.isBlank() && newBody.isBlank() && pendingAttachments.isEmpty() &&
            composedChecklist.isEmpty() && !hasDoodle
        ) {
            composing = false
            return
        }
        // Snapshot every composer field the save needs *before* kicking off the background write.
        // This runs on AppScope's app-lifetime scope (see below), and resetComposer() runs
        // synchronously the moment this function returns — so reading these `var`s from inside the
        // launch block would race the reset and could persist blanks over a perfectly good note.
        val title = newTitle
        val body = newBody
        // The rich-text sidecar is snapshotted HERE, with everything else, and for the
        // reason stated above: it is a `var`, resetComposer() clears it synchronously as
        // soon as this function returns, and the write below runs on a background scope.
        // Reading it from inside that block persisted an empty sidecar over perfectly
        // good formatting - the one field the snapshot block had been missing.
        val bodySpansJson = com.lucent.app.data.RichText.encode(
            com.lucent.app.data.RichText.reconcile(bodySpans, body.length)
        )
        val tags = selectedTags.joinToString(",")
        val attachmentsJson = Attachments.serialize(pendingAttachments)
        val pinnedSnapshot = pinned
        val colorSnapshot = selectedColor.key
        val isChecklistSnapshot = isChecklistMode
        val isDoodleSnapshot = isDoodleMode
        val doodleSnapshot = prunedDoodle
        val checklistJson = Checklist.serialize(composedChecklist)
        val id = editingId
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
                val row = Note(
                    title = title,
                    body = body,
                    bodySpans = bodySpansJson,
                    tags = tags,
                    attachments = attachmentsJson,
                    pinned = pinnedSnapshot,
                    color = colorSnapshot,
                    isChecklist = isChecklistSnapshot,
                    checklist = checklistJson,
                    isDraft = true,
                    draftSavedAt = System.currentTimeMillis()
                )
                val existing = draftRowId
                if (existing == null) draftRowId = db.noteDao().insert(row)
                else db.noteDao().update(row.copy(id = existing))
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
        //
        // A draft is a parking space, not a copy. Once the same edit has been saved for real the
        // parked version is not merely redundant, it is misleading: the drafts list would keep
        // offering a stale half-finished version of a note that has since been finished, and
        // restoring it would quietly undo the save.
        //
        // Deleting the row this session created (rather than searching for "drafts that look like
        // this note") is what makes it exact. [draftRowId] is the id this editing session parked
        // under and nothing else can be pointed at by it, so there is no rule to get wrong about
        // which drafts belong to which item — and because the same id is reused for every "save to
        // draft" within a session, a session can never have produced more than one.
        val draftToClear = draftRowId
        draftRowId = null

        val appContext = context.applicationContext

        // Run on the app-lifetime scope, not the composable's scope: saving from the
        // unsaved-changes dialog switches screens in the same action, which would otherwise
        // dispose this screen and cancel the write before it commits.
        AppScope.io.launch {
            if (id != null) {
                // Read the existing row and copy onto it so fields the composer doesn't touch —
                // notably the archive state (archived/archivedAt) and trashedAt — are preserved.
                // Rebuilding a fresh Note() here would reset those to their defaults and silently
                // un-archive an archived note the moment it was edited.
                val existing = db.noteDao().getByIdOnce(id)
                if (existing != null) {
                    // Capture the outgoing text as a revision before it's overwritten. This is the
                    // only place an edit from the UI can happen, so it's the only place that has to
                    // remember — and NoteHistory itself decides whether the change is even worth
                    // recording, so an accidental no-op save doesn't evict real history.
                    NoteHistory.recordIfChanged(
                        db = db,
                        existing = existing,
                        newTitle = title,
                        newBody = body,
                        newTags = tags,
                        newIsChecklist = isChecklistSnapshot,
                        newChecklist = checklistJson
                    )
                }
                val updated = (existing ?: Note(id = id, title = title, body = body)).copy(
                    title = title,
                    body = body,
                    bodySpans = bodySpansJson,
                    updatedAt = System.currentTimeMillis(),
                    tags = tags,
                    attachments = attachmentsJson,
                    pinned = pinnedSnapshot,
                    color = colorSnapshot,
                    isChecklist = isChecklistSnapshot,
                    checklist = checklistJson,
                    isDoodle = isDoodleSnapshot,
                    doodle = doodleSnapshot
                )
                db.noteDao().update(updated)
            } else {
                db.noteDao().insert(
                    Note(
                        title = title,
                        body = body,
                        bodySpans = bodySpansJson,
                        tags = tags,
                        attachments = attachmentsJson,
                        pinned = pinnedSnapshot,
                        color = colorSnapshot,
                        isChecklist = isChecklistSnapshot,
                        checklist = checklistJson,
                        isDoodle = isDoodleSnapshot,
                        doodle = doodleSnapshot
                    )
                )
            }

                // Task 15 — drop this session's parked draft now that the real save has landed.
                if (draftToClear != null) {
                    db.noteDao().getByIdOnce(draftToClear)?.let { row ->
                        if (row.isDraft) db.noteDao().delete(row)
                    }
                }
            withContext(Dispatchers.Main) {
                LucentToast.show(appContext, com.lucent.app.i18n.S.noteSaved)
            }
        }
        composing = false
        resetComposer()
    }

    // True while the composer holds edits that haven't been saved yet, whether creating a new
    // note from scratch or editing an existing one.
    val editingNote = remember(notes, archivedNotes, editingId) {
        notes.firstOrNull { it.id == editingId } ?: archivedNotes.firstOrNull { it.id == editingId }
    }
    val noteDirty = composing && run {
        val original = editingNote
        if (original != null) {
            val originalTags = com.lucent.app.data.NoteTags.parse(original.tags).toSet()
            newTitle != original.title || newBody != original.body || selectedTags != originalTags ||
                Attachments.serialize(pendingAttachments) != original.attachments ||
                pinned != original.pinned || selectedColor.key != original.color ||
                isChecklistMode != original.isChecklist ||
                Checklist.serialize(checklistItems) != original.checklist ||
                // Round R2, task 2 — the drawing counts as an edit. It was missing from this
                // expression entirely, so a note whose ONLY change was a stroke on the canvas was
                // "not dirty": the unsaved-changes guard let it go without asking, and the crash
                // snapshot never recorded it. Both switch and strokes, because turning the canvas
                // off is as much a change as drawing on it.
                isDoodleMode != original.isDoodle || doodleData != original.doodle ||
                // Text sitting in the add field is work too (B4): losing it on exit because no "+"
                // was pressed is exactly the bug this flag exists to prevent.
                (isChecklistMode && newChecklistItemText.isNotBlank())
        } else {
            newTitle.isNotBlank() || newBody.isNotBlank() || pendingAttachments.isNotEmpty() ||
                selectedTags.isNotEmpty() || pinned || selectedColor != NoteColor.DEFAULT ||
                checklistItems.isNotEmpty() || (isChecklistMode && newChecklistItemText.isNotBlank()) ||
                // Round R2, task 2 — a brand-new note that is nothing but a drawing is still a note
                // with work in it; see the existing-note branch above.
                !com.lucent.app.ui.DoodlePages.isEmpty(doodleData)
        }
    }

    /**
     * Whether the composer holds any CONTENT a template would overwrite (task 12).
     *
     * This is a narrower question than [noteDirty], and conflating the two is what made pinning a
     * new note hide the template row. [noteDirty] answers "would leaving here lose something?" — and
     * for that, pinning and picking a colour absolutely count, because the user chose them and would
     * not expect them to evaporate. But the template row was gated on the same flag, and it is
     * answering a completely different question: "is there anything here a template would destroy?"
     *
     * [applyTemplate] writes exactly five things — title, body, tags, checklist mode, checklist items
     * — and touches neither the pin nor the colour. So a pinned, purple, otherwise-empty new note is
     * still a blank canvas as far as templates are concerned, and hiding them was protecting work
     * that did not exist. Worse, it was silent: the row simply disappeared, with the pin toggle as
     * the unexplained cause, which reads as a bug rather than a safeguard.
     *
     * The fields listed here are precisely the fields [applyTemplate] overwrites. If it ever learns
     * to set another one, that field belongs in this expression too.
     */
    val noteContentDirty = composing && editingId == null && (
        newTitle.isNotBlank() || newBody.isNotBlank() ||
            selectedTags.isNotEmpty() || checklistItems.isNotEmpty() ||
            newChecklistItemText.isNotBlank()
        )

    fun discardComposer() {
        // v2.7.2: leaving while authoring a user template parks the work (see the draft store), so
        // an accidental exit is an interruption, not a loss.
        if (templateAuthoring) persistTemplateDraft()
        templateAuthoring = false
        composing = false
        resetComposer()
    }

    // Leaving the composer (back arrow or system back) while dirty asks first instead of
    // silently discarding what was typed.
    fun leaveComposer() {
        // v2.7.2: in template-authoring mode the unsaved dialog would offer note actions that do
        // not apply; leaving simply parks the draft and closes, exactly as the draft copy says.
        if (templateAuthoring) {
            discardComposer()
            return
        }
        if (noteDirty) showUnsavedDialog = true else discardComposer()
    }

    // Back priority, most specific first: composer -> ask/close; history -> detail; detail -> the
    // list it came from; archive/trash -> home. Detail sits above archive/trash because a note
    // opened *from* either should return there, not skip straight to the home grid.
    BackHandler(enabled = composing) { leaveComposer() }
    BackHandler(enabled = !composing && historyForId != null) { historyForId = null }
    BackHandler(enabled = !composing && historyForId == null && viewingId != null) { closeDetail() }
    BackHandler(enabled = !composing && historyForId == null && viewingId == null && showArchive) { showArchive = false }
    BackHandler(enabled = !composing && historyForId == null && viewingId == null && !showArchive && showTrash) { showTrash = false }
    BackHandler(enabled = !composing && historyForId == null && viewingId == null && !showArchive && !showTrash && showSearch) { showSearch = false }
    // Task 3.4 — Drafts and the hidden area had no back handler at all, so a back press on either
    // fell straight through to the activity's "press again to exit". They are sub-pages of this tab
    // exactly like the archive and the trash beside them, and back means "up one level" on every
    // other page in the app.
    BackHandler(enabled = !composing && historyForId == null && viewingId == null && (showDrafts || showHidden)) {
        if (showDrafts) showDrafts = false else showHidden = false
    }
    // On the home grid, a back press first exits multi-select mode rather than leaving the screen.
    BackHandler(enabled = selectionMode && !composing && historyForId == null && viewingId == null && !showArchive && !showTrash && !showSearch && !showDrafts && !showHidden) { exitSelection() }

    // Leaving this tab folds it back to the notes grid (task 3). Same reasoning as the Tasks tab:
    // the detail page, composer, archive, trash, history and search are destinations, and returning
    // to a tab should return you to the tab, not to wherever you happened to stop. The
    // unsaved-changes guard has already run by this point, so nothing typed can be lost here.
    LaunchedEffect(active) {
        if (!active) {
            if (composing) discardComposer()
            viewingId = null
            returnToOnClose = null
            historyForId = null
            showArchive = false
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
    // On Android this collapsed the action cluster on ON_STOP (app no longer visible). The desktop
    // equivalent is the window losing focus — reading isWindowFocused here re-runs the effect when
    // focus flips, collapsing the cluster when focus is lost.
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(isWindowFocused) {
        if (!isWindowFocused) actionsExpanded = false
    }

    // Registers with the app-lifetime guard so switching bottom-nav tabs, or the system back
    // button closing the app, also asks before losing an in-progress note.
    SideEffect {
        if (noteDirty && !templateAuthoring) {
            UnsavedChangesGuard.register(
                owner = "notes",
                onSave = { saveNote() },
                onDiscard = { discardComposer() },
                // Task A10 — parks a copy without closing or committing anything.
                onAutoDraft = { saveNote(asDraft = true, closeAfter = false) }
            )
        } else {
            UnsavedChangesGuard.clear("notes")
        }
    }
    DisposableEffect(Unit) { onDispose { UnsavedChangesGuard.clear("notes") } }

    // ---- Round R1, task 5: the recovery snapshot -------------------------------------------
    //
    // Rebuilt on every recomposition and used as the KEY of the effect below, which is what makes
    // the debounce work: identical content means an identical Snapshot means the effect is not
    // restarted, so a pause in typing writes once rather than a write per keystroke. Snapshot's
    // timestamp is deliberately left at zero here and stamped at save time for the same reason.
    val sessionSnapshot = if (composing && noteDirty && !templateAuthoring) {
        com.lucent.app.data.SessionRestore.Snapshot(
            kind = com.lucent.app.data.SessionRestore.KIND_NOTE,
            itemId = editingId,
            title = newTitle,
            payload = com.lucent.app.data.SessionRestore.put(
                "title" to newTitle,
                "body" to newBody,
                "bodySpans" to com.lucent.app.data.RichText.encode(
                    com.lucent.app.data.RichText.reconcile(bodySpans, newBody.length)
                ),
                "tags" to selectedTags.joinToString(","),
                "attachments" to Attachments.serialize(pendingAttachments),
                "pinned" to pinned,
                "color" to selectedColor.key,
                "isChecklist" to isChecklistMode,
                "checklist" to Checklist.serialize(checklistItems),
                "checklistText" to newChecklistItemText,
                "isDoodle" to isDoodleMode,
                "doodle" to doodleData
            )
        )
    } else null
    LaunchedEffect(sessionSnapshot) {
        if (sessionSnapshot == null) {
            // Nothing open, so nothing to come back to — unless the PREVIOUS run left something
            // and the user has not been asked about it yet. Clearing then would destroy the answer
            // to a question still on screen, so it waits.
            if (com.lucent.app.data.SessionRestore.pending == null) {
                com.lucent.app.data.SessionRestore.clear(context)
            }
        } else {
            kotlinx.coroutines.delay(900)
            com.lucent.app.data.SessionRestore.save(context, sessionSnapshot)
        }
    }

    // The other half: the user said yes, so put the composer back the way they left it. Every field
    // the payload carries is restored, because a composer that comes back *almost* right is worse
    // than one that does not come back at all — it looks like it worked.
    LaunchedEffect(com.lucent.app.data.SessionRestore.restoring) {
        val snap = com.lucent.app.data.SessionRestore
            .consumeRestore(com.lucent.app.data.SessionRestore.KIND_NOTE) ?: return@LaunchedEffect
        val p = com.lucent.app.data.SessionRestore.read(snap.payload)
        resetComposer()
        editingId = snap.itemId
        newTitle = p.optString("title")
        newBody = p.optString("body")
        selectedTags = p.optString("tags").split(",").filter { it.isNotBlank() }.toSet()
        pendingAttachments = Attachments.parse(p.optString("attachments", "[]"))
        pinned = p.optBoolean("pinned")
        selectedColor = NoteColor.fromKey(p.optString("color"))
        isChecklistMode = p.optBoolean("isChecklist")
        checklistItems = Checklist.parse(p.optString("checklist", "[]"))
        newChecklistItemText = p.optString("checklistText")
        isDoodleMode = p.optBoolean("isDoodle")
        doodleData = p.optString("doodle")
        // Written straight in now that `bodySpans` is no longer keyed on `composing`;
        // see its declaration for the bug that used to force this into a second stage.
        bodySpans = com.lucent.app.data.RichText.load(p.optString("bodySpans"), newBody)
        // Land on the composer and nothing else: any sub-page that happened to be open would
        // otherwise sit on top of the very editor the user asked to be taken back to.
        viewingId = null
        historyForId = null
        showArchive = false
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
            text = { Text(if (editingId != null) com.lucent.app.i18n.S.unsavedNoteExistingBody else com.lucent.app.i18n.S.unsavedNoteNewBody) },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    saveNote()
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

    // v2.7.2: long-press a custom template chip -> confirm before it is removed.
    val pendingDelete = customTemplates.firstOrNull { it.id == pendingTemplateDeleteId }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingTemplateDeleteId = null },
            title = { Text(com.lucent.app.i18n.S.tplDeleteTitle) },
            text = { Text(com.lucent.app.i18n.S.tplDeleteBody(pendingDelete.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val id = pendingTemplateDeleteId
                    pendingTemplateDeleteId = null
                    if (id != null) {
                        scope.launch {
                            repo.setCustomTemplatesJson(
                                com.lucent.app.data.CustomTemplates.remove(customTemplatesJson, id)
                            )
                        }
                    }
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = {
                TextButton(onClick = { pendingTemplateDeleteId = null }) {
                    Text(com.lucent.app.i18n.S.actionCancel)
                }
            }
        )
    }

    // Keyed on selectedTags too: a tag the user just typed and added isn't in any saved note
    // yet, so without this it wouldn't render as a chip (or its selected highlight) until after
    // the note was saved and the list re-queried. Unioning it in makes it show immediately.
    val allTags = remember(notes, selectedTags) {
        (DEFAULT_TAGS +
            notes.flatMap { com.lucent.app.data.NoteTags.parse(it.tags) } +
            selectedTags.map { com.lucent.app.data.NoteTags.canonical(it) }).distinct()
    }

    // The typed text is parsed into a structured query (terms, phrases, tag:/is:/has:/link: filters)
    // rather than being string-matched directly — see data/SearchQuery.kt. The calendar date filter
    // then narrows by the note's timestamp. The two combine: a note must satisfy the query AND fall
    // on the picked day when one is set.
    val query = remember(searchText) { SearchQuery.parse(searchText) }
    // `is:archived` is the one filter that changes *which* notes are even in scope, since archived
    // notes are deliberately absent from the home grid. Honouring it here means the archive is
    // searchable from the home screen without first navigating into it.
    val searchPool = remember(notes, archivedNotes, query) {
        if ("archived" in query.flags) notes + archivedNotes else notes
    }
    val filteredNotes = remember(searchPool, query, dateRange) {
        searchPool.filterBySearch(query).filter { note ->
            dateRange?.let { (start, end) -> withinLocalDayRange(note.updatedAt, start, end) } ?: true
        }
    }
    // Pinned first, then relevance when the user is actually searching, then their chosen sort.
    val sortedNotes = remember(filteredNotes, sortOption, query) {
        filteredNotes.sortedForDisplay(sortOption, query)
    }

    // ---- Task A16, revised for task 8 --------------------------------------------------------
    //
    // The gesture used to be enabled ONLY under the Custom sort, on the reasoning that writing a
    // position the next recomposition is obliged to discard is worse than offering nothing. The
    // reasoning was sound and the outcome was still wrong: the app opens on "Recent", almost nobody
    // changes the sort, so for almost everybody long-press-and-drag simply did nothing — which is
    // exactly the "drag to reorder isn't implemented" report.
    //
    // The gesture is now always live, and a drop under any other sort does the one thing that makes
    // the result visible: it writes the new positions AND switches the list to Custom. Dragging a
    // card is an unambiguous statement that the user wants to decide the order themselves, so
    // adopting the sort that honours that is the least surprising reading of it — far less
    // surprising than a card that springs back with no explanation.
    val reorderEnabled = true
    // Round R1, task 2 — see rememberReorderPlacementSpec: no placement animation until the
    // screen has settled, so opening the app never replays a reordering the user already made.
    val placementSpec = rememberReorderPlacementSpec()
    // ---- Home sections, hoisted so the drop handler below can see them ----
    //
    // Reordering is confined to ONE section: a card dragged inside Recent stays in Recent, a pinned
    // card stays among the pinned. That is what the sections are for — they answer "why is this card
    // here?", and a drag that could move a card between them would be answering it differently every
    // time. Under the Custom sort the buckets keep the user's own sequence (see
    // [sectionHomeItems.orderWithinSections]), so a drop inside a section is visible and permanent.
    val browsing = searchText.isBlank() && dateRange == null
    val sectionNow = remember(sortedNotes) { System.currentTimeMillis() }
    val sections = remember(sortedNotes, noteUsage, browsing, sectionNow, sortOption) {
        if (!browsing) null else sectionHomeItems(
            items = sortedNotes,
            now = sectionNow,
            maxRecent = 6,
            id = { it.id },
            timestamp = { it.updatedAt },
            activityScore = { com.lucent.app.data.UsageTracker.score(noteUsage[it.id] ?: 0.0, it.updatedAt, sectionNow) },
            // Task A13 — pinned items lead the page instead of being scattered by date.
            isPinned = { it.pinned },
            orderWithinSections = sortOption == NoteSort.CUSTOM
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
        val moving = selectedNoteIds.toList().mapNotNull { id -> sortedNotes.firstOrNull { it.id == id } }
        if (moving.isEmpty()) return
        // ---- One section at a time (task 9) ----
        //
        // The gap is named by the cards on either side of it. A gap is usable only if the anchor we
        // would attach to belongs to the same section as what is being dragged: that keeps a card in
        // Recent inside Recent, and it also handles the ends of the list for free, since the top gap
        // has no card above it and the bottom gap none below.
        val home = sectionOfId[moving.first().id]
        val sameSection = { id: Long? -> sections == null || id == null || sectionOfId[id] == home }
        val usableAfter = if (sameSection(afterId)) afterId else null
        val usableBefore = if (sameSection(beforeId)) beforeId else null
        if (usableAfter == null && usableBefore == null) return
        if (moving.any { sectionOfId[it.id] != home }) return
        val reordered = reorderedAround(sortedNotes, moving, usableBefore, usableAfter) { it.id }
        if (reordered === sortedNotes) return
        AppScope.io.launch {
            reordered.forEachIndexed { index, n ->
                if (n.manualOrder != index) db.noteDao().update(n.copy(manualOrder = index))
            }
        }
        // Adopt the only sort under which the positions just written mean anything.
        if (sortOption != NoteSort.CUSTOM) {
            scope.launch { settingsRepo.setNotesSort(NoteSort.CUSTOM.key) }
        }
        exitSelection()
    }

    // Desktop counterpart of Android's GetMultipleContents launcher: the native file dialog is opened
    // synchronously on the AWT thread from the attach button's click handler, and this function then
    // imports the chosen java.io.Files off the main thread. The only limit is per file (600 MB) —
    // there's no cap on the combined volume — so each file is checked on its own. A file over the
    // ceiling is rejected before it's written where its size is known up front, cleaned up otherwise.
    fun importPickedFiles(files: List<File>) {
        if (files.isEmpty()) return
        scope.launch {
            val (accepted, lastMessage) = withContext(Dispatchers.IO) {
                var runningPending = pendingAttachments.toList()
                var message = ""
                for (file in files) {
                    val hint = file.length()
                    if (hint > 0) {
                        val preCheck = AttachmentLimits.checkSingle(hint)
                        if (!preCheck.allowed) { message = preCheck.message; continue }
                    }
                    val newAtt = fileToAttachment(context, file) ?: run { message = com.lucent.app.i18n.S.couldNotReadOneFile; null } ?: continue
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

    // Deleting is now a *move to Trash*, not an erasure — the row, its files, and its history stay
    // put for 30 days. The dialog says exactly that, because a confirmation that threatens
    // permanent loss when nothing of the sort is happening trains people to ignore confirmations.
    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text(com.lucent.app.i18n.S.moveToTrashTitle) },
            text = {
                Text(com.lucent.app.i18n.S.moveNoteTrashBody(note.title.ifBlank { com.lucent.app.i18n.S.untitledNote }, TrashCleanup.RETENTION_DAYS))
            },
            confirmButton = {
                TextButton(onClick = {
                    AppScope.io.launch {
                        db.noteDao().update(note.copy(trashedAt = System.currentTimeMillis()))
                    }
                    if (viewingId == note.id) viewingId = null
                    noteToDelete = null
                }) { Text(com.lucent.app.i18n.S.moveToTrash) }
            },
            dismissButton = { TextButton(onClick = { noteToDelete = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    // Batch move-to-trash for the multi-selected notes. Same soft-delete as the single-note path,
    // just applied to the whole selection at once.
    if (showBatchDeleteConfirm) {
        val count = selectedNoteIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text(com.lucent.app.i18n.S.moveToTrashTitle) },
            text = {
                Text(if (count == 1) com.lucent.app.i18n.S.moveOneNoteTrashBody(TrashCleanup.RETENTION_DAYS) else com.lucent.app.i18n.S.moveNNotesTrashBody(count, TrashCleanup.RETENTION_DAYS))
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedNoteIds
                    showBatchDeleteConfirm = false
                    exitSelection()
                    AppScope.io.launch {
                        val now = System.currentTimeMillis()
                        ids.forEach { id ->
                            db.noteDao().getByIdOnce(id)?.let { db.noteDao().update(it.copy(trashedAt = now)) }
                        }
                    }
                }) { Text(com.lucent.app.i18n.S.moveToTrash) }
            },
            dismissButton = { TextButton(onClick = { showBatchDeleteConfirm = false }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    // Confirm before archiving or restoring (task 7). Nothing is written until Confirm is pressed;
    // dismissing by any route — Cancel, tapping outside, back — leaves the note exactly as it was.
    noteToToggleArchive?.let { note ->
        val willArchive = !note.archived
        AlertDialog(
            onDismissRequest = { noteToToggleArchive = null },
            title = { Text(if (willArchive) com.lucent.app.i18n.S.archiveNoteTitle else com.lucent.app.i18n.S.restoreNoteTitle) },
            text = {
                Text(if (willArchive) com.lucent.app.i18n.S.archiveNoteBody(note.title.ifBlank { com.lucent.app.i18n.S.untitledNote }) else com.lucent.app.i18n.S.unarchiveNoteBody(note.title.ifBlank { com.lucent.app.i18n.S.untitledNote }))
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = note
                    noteToToggleArchive = null
                    val appContext = context.applicationContext
                    AppScope.io.launch {
                        db.noteDao().update(
                            if (target.archived) target.copy(archived = false, archivedAt = null)
                            else target.copy(archived = true, archivedAt = System.currentTimeMillis())
                        )
                        withContext(Dispatchers.Main) {
                            LucentToast.show(appContext, if (target.archived) com.lucent.app.i18n.S.noteRestoredToast else com.lucent.app.i18n.S.noteArchivedToast)
                        }
                    }
                    // The note is about to leave whichever list is showing, so the detail page can't
                    // keep standing on it.
                    if (viewingId == target.id) closeDetail()
                }) { Text(if (willArchive) com.lucent.app.i18n.S.archive else com.lucent.app.i18n.S.actionRestore) }
            },
            dismissButton = { TextButton(onClick = { noteToToggleArchive = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    // Confirm-before-pin: pinning (or unpinning) reorders the whole list, so it asks first rather
    // than acting on the tap. Cancelling leaves the note exactly as it was.
    noteToTogglePin?.let { note ->
        val willPin = !note.pinned
        AlertDialog(
            onDismissRequest = { noteToTogglePin = null },
            title = { Text(if (willPin) com.lucent.app.i18n.S.pinNoteTitle else com.lucent.app.i18n.S.unpinNoteTitle) },
            text = {
                Text(if (willPin) com.lucent.app.i18n.S.pinNoteBody(note.title.ifBlank { com.lucent.app.i18n.S.untitledNote }) else com.lucent.app.i18n.S.unpinNoteBody(note.title.ifBlank { com.lucent.app.i18n.S.untitledNote }))
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = note
                    noteToTogglePin = null
                    AppScope.io.launch { db.noteDao().update(target.copy(pinned = !target.pinned)) }
                }) { Text(if (willPin) com.lucent.app.i18n.S.actionPin else com.lucent.app.i18n.S.actionUnpin) }
            },
            dismissButton = { TextButton(onClick = { noteToTogglePin = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    // The note currently open in the detail page, resolved live so edits reflect at once. Trashed
    // notes are excluded, so a note trashed by *any* path — this page's button, the assistant, the
    // grid — makes the detail page fall away rather than sit there showing a deleted note.
    val viewingNote = remember(notes, archivedNotes, hiddenNotes, viewingId) {
        (notes + archivedNotes + hiddenNotes).firstOrNull { it.id == viewingId && it.trashedAt == null }
    }

    when {
        composing -> {
            // ---- Create / edit page ----
            /**
             * Task A12 — open an existing, long note straight at the *end* of its text.
             *
             * Editing a note you have been keeping for months means adding to the bottom of it, and
             * the composer opened at the top: every session started with a scroll. This is the same
             * device the task composer already uses for its subtask list, pointed at the bottom of
             * the text instead.
             *
             * `remember` with no keys is what makes it a one-shot: the flag is captured when the
             * composer opens and never recomputed, so typing (which changes newBody, and therefore
             * this condition) can't yank the page back down mid-edit. The threshold means a short
             * note — already fully visible — is left alone rather than scrolled for no reason.
             */
            val jumpToBodyEnd = remember { editingId != null && newBody.length > BODY_JUMP_THRESHOLD }
            val bodyEndRequester = remember { BringIntoViewRequester() }
            if (jumpToBodyEnd) {
                LaunchedEffect(Unit) {
                    // Two frames so the scrollable column has laid out before it is asked to move.
                    withFrameNanos { }
                    withFrameNanos { }
                    bodyEndRequester.bringIntoView()
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
                    Text(if (editingId != null) com.lucent.app.i18n.S.editNote else com.lucent.app.i18n.S.newNote, color = onGradient, fontSize = 20.sp)
                }

                Column(modifier = Modifier.fillMaxWidth().frostedGlass(tint = selectedColor.swatch).padding(16.dp)) {
                    // Templates are offered only on a brand-new note whose CONTENT is still empty.
                    // Showing them while editing an existing note — or after the user has started
                    // typing — would turn a one-tap convenience into a one-tap way to obliterate your
                    // own work. Pinning or colouring the note is not typing: see [noteContentDirty].
                    // v2.7.2: the strip also hosts the USER templates (chip after the four
                    // built-ins, long-press to delete) and the "+ New template" button that turns
                    // the composer into a template editor; authoring has its own banner and draft
                    // handling below.
                    if (templateAuthoring || (editingId == null && !noteContentDirty)) {
                        if (templateAuthoring) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = onGradientMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    com.lucent.app.i18n.S.tplAuthoringTitle,
                                    color = onGradient,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { discardTemplateAuthoring() }) {
                                    Text(com.lucent.app.i18n.S.tplDiscard, color = onGradientMuted)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        } else {
                            Text(com.lucent.app.i18n.S.startFromTemplate, color = onGradientMuted, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // An interrupted authoring session is offered first, so "continue
                                // where I left off" is the most reachable chip when it exists.
                                val pendingDraft = remember(templateDraftJson) {
                                    com.lucent.app.data.CustomTemplates.parseDraft(templateDraftJson)
                                }
                                if (!com.lucent.app.data.CustomTemplates.draftEmpty(pendingDraft)) {
                                    FilterChip(
                                        selected = true,
                                        onClick = { startTemplateAuthoring(pendingDraft) },
                                        label = { Text(com.lucent.app.i18n.S.tplContinueDraft) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                                NoteTemplate.entries.forEach { template ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { applyTemplate(template) },
                                        label = { Text(template.label) },
                                        leadingIcon = {
                                            Icon(
                                                templateIcon(template),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                                customTemplates.forEach { t ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                                            .background(onGradient.copy(alpha = 0.10f))
                                            .border(
                                                1.dp,
                                                onGradient.copy(alpha = 0.22f),
                                                androidx.compose.foundation.shape.RoundedCornerShape(50)
                                            )
                                            .combinedClickable(
                                                onClick = { applyCustomTemplate(t) },
                                                onLongClick = { pendingTemplateDeleteId = t.id }
                                            )
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = onGradientMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            t.name,
                                            color = onGradient,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 170.dp)
                                        )
                                    }
                                }
                                // The "+" that starts authoring a user template, after the built-ins.
                                FilterChip(
                                    selected = false,
                                    onClick = { startTemplateAuthoring(null) },
                                    label = { Text(com.lucent.app.i18n.S.tplCreateCustom) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text(com.lucent.app.i18n.S.fieldTitle) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Pin toggle: pinned notes float to the top of the home grid, ahead of sort.
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

                    // ---- The details box: ONE field, in every mode ----
                    //
                    // There used to be three copies of this call, one per branch, differing only in
                    // their height and their comment. Three copies of a field is three places for a
                    // fix to be applied twice and forgotten once - and with the kinds now free to
                    // combine, a branch-per-kind could not have expressed "checklist AND doodle"
                    // without a fourth copy. It is declared once and its height reacts instead.
                    //
                    // Task A20 / A22 reasoning, unchanged and now unconditional: a note kind that
                    // can hold *only* one form of content forces the user to open a second note to
                    // say what the first one is. Note.body is kept in every mode (deliberately, so
                    // switching modes back and forth loses nothing), so this is the same field and
                    // the same save path, with no migration and no new column.
                    val detailsIsAside = isChecklistMode || isDoodleMode
                    ExpandableGlassTextField(
                        value = newBody,
                        onValueChange = { newBody = it },
                        // PHASE 4: dictation — recognized speech appends to whatever is already
                        // written, joined with a single space, never replacing it.
                        extraAction = { DictationButton(onText = { spoken ->
                            newBody = if (newBody.isBlank()) spoken
                            else newBody + (if (newBody.endsWith(" ") || newBody.endsWith("\n")) "" else " ") + spoken
                        }) },
                        spans = if (richTextEnabled) bodySpans else emptyList(),
                        onSelectionChange = { a, b -> bodySelStart = a; bodySelEnd = b },
                        highlightColors = if (richTextEnabled) RichHighlightColors else emptyList(),
                        textColors = if (richTextEnabled) richTextColors() else emptyList(),
                        // Task 11. This used to swap in a longer hint when Markdown and/or links
                        // were on ("Details — supports Markdown and [[links]]"). Placeholder text
                        // is a single line that cannot wrap, and the field carries the dictation
                        // mic in its top-right corner, so the longer strings ran straight under
                        // the mic and were clipped mid-word. The plain label is used in every
                        // mode now: it matches the task composer, it never collides with the mic
                        // in any of the four languages, and what the field supports is documented
                        // where it can be read properly — Settings › Editor.
                        placeholder = com.lucent.app.i18n.S.detailsPlaceholder,
                        expandedTitle = if (editingId != null) com.lucent.app.i18n.S.editNote else com.lucent.app.i18n.S.newNote,
                        // Round R2, task 3: 1.5x again. Smaller when something else on the page is
                        // the content and this is an aside to it, so the items or the canvas are
                        // not pushed off screen by a box nobody has typed in yet. It still expands
                        // to full screen on demand either way.
                        collapsedMinHeight = if (detailsIsAside) 270.dp else 360.dp,
                        collapsedMaxHeight = if (detailsIsAside) 660.dp else 960.dp
                    )
                    if (!detailsIsAside && newBody.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        // Paragraphs + characters, not words: "word" is meaningless in scripts
                        // that don't separate words with spaces (Chinese, Japanese, Korean),
                        // where a whole note collapsed to "1 word". Both figures below count
                        // correctly in every language. See NoteStats.paragraphCharLabel.
                        val statsLabel = remember(newBody) { com.lucent.app.data.NoteStats.paragraphCharLabel(newBody) }
                        Text(
                            statsLabel,
                            color = onGradientMuted,
                            fontSize = 11.sp
                        )
                    }

                    if (isChecklistMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ChecklistEditorSection(
                            items = checklistItems,
                            newItemText = newChecklistItemText,
                            onNewItemTextChange = { newChecklistItemText = it },
                            onAdd = {
                                checklistItems = checklistItems + Checklist.newItem(newChecklistItemText)
                                newChecklistItemText = ""
                            },
                            onToggle = { item ->
                                checklistItems = checklistItems.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                            },
                            onRemove = { item -> checklistItems = checklistItems.filterNot { it.id == item.id } },
                            // Items are editable in place after being added (settings task B3).
                            onEditText = { item, text ->
                                checklistItems = checklistItems.map { if (it.id == item.id) it.copy(text = text) else it }
                            },
                            addLabel = com.lucent.app.i18n.S.addItem,
                            // Task A18, checklist notes — same rule as a task's subtasks, because
                            // the two share this editor and a change to one that fits the other
                            // should never be made to only one of them.
                            onInsertAfter = { item ->
                                val at = checklistItems.indexOfFirst { it.id == item.id }
                                checklistItems = if (at < 0) checklistItems + Checklist.newItem("")
                                else checklistItems.toMutableList().also { it.add(at + 1, Checklist.newItem("")) }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (isDoodleMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        // Task A22 — the whiteboard. The details box below is shared with every
                        // other mode now, so a sketch can still be captioned without this branch
                        // carrying a second copy of that field.
                        ExpandableDoodleEditor(value = doodleData, onValueChange = { doodleData = it })
                    }
                    // Task A12 anchor. Deliberately *here* — after the body/items, before the pin,
                    // tags and attachment rows. Scrolling to the true bottom of the form would put
                    // the attachment list on screen and the text the user came to edit off it.
                    Spacer(modifier = Modifier.height(12.dp).bringIntoViewRequester(bodyEndRequester))
                    Spacer(modifier = Modifier.height(12.dp))
                    AttachmentSection(
                        attachments = pendingAttachments,
                        onPick = { importPickedFiles(DesktopFiles.openFiles()) },
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
                    MoreOptionsFold(
                        expanded = composerMoreOpen,
                        onToggle = { composerMoreOpen = !composerMoreOpen }
                    ) {
                        // v2.7.2: the note-kind switches live one level down, here in the fold. Flicking
                        // one on makes its EDITOR appear on the main level, right under the details
                        // field — content in progress belongs to the page, not to a menu. The two
                        // kinds remain combinable (a sketch with a checklist of its own parts is a
                        // legitimate note); the old mutual exclusion is gone for good.
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = null,
                                tint = if (isChecklistMode) onGradient else onGradientMuted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(com.lucent.app.i18n.S.checklistNote, color = onGradient, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = isChecklistMode,
                                onCheckedChange = { isChecklistMode = it }
                            )
                        }


                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Brush,
                                contentDescription = null,
                                tint = if (isDoodleMode) onGradient else onGradientMuted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(com.lucent.app.i18n.S.doodleNote, color = onGradient, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = isDoodleMode,
                                onCheckedChange = { isDoodleMode = it }
                            )
                        }

                        Text(com.lucent.app.i18n.S.labelColour, color = onGradient, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        ColorPickerRow(selected = selectedColor, onSelect = { selectedColor = it })
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(com.lucent.app.i18n.S.labelTags, color = onGradient)
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allTags.forEach { tag ->
                                FilterChip(
                                    selected = selectedTags.contains(tag),
                                    onClick = {
                                        selectedTags = if (selectedTags.contains(tag)) selectedTags - tag else selectedTags + tag
                                    },
                                    // Canonical underneath, translated on screen.
                                    label = { Text(com.lucent.app.data.NoteTags.label(tag)) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newCustomTag,
                                onValueChange = { newCustomTag = it },
                                placeholder = { Text(com.lucent.app.i18n.S.newTag) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                // Canonicalised on the way in, so typing "Study" (or "学习") by hand
                                // selects the built-in chip instead of creating a duplicate beside it.
                                val tag = com.lucent.app.data.NoteTags.canonical(newCustomTag)
                                if (tag.isNotBlank()) {
                                    selectedTags = selectedTags + tag
                                    newCustomTag = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = com.lucent.app.i18n.S.a11yAddTag, tint = onGradient)
                            }
                        }
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
                            onClick = { if (templateAuthoring) saveCustomTemplate() else saveNote() },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.weight(1f).height(COMPOSER_ACTION_HEIGHT)
                        ) {
                            Icon(
                                if (templateAuthoring) Icons.Default.Star else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                when {
                                    templateAuthoring -> " " + com.lucent.app.i18n.S.tplSaveAsTemplate
                                    editingId != null -> " " + com.lucent.app.i18n.S.saveChanges
                                    else -> " " + com.lucent.app.i18n.S.addNoteBtn
                                },
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        if (!templateAuthoring) {
                            // Task A10 — "parallel to Save", as asked, and secondary on purpose: it
                            // is the same commitment in a lower gear ("keep this, I'm not done").
                            GlassButton(
                                text = com.lucent.app.i18n.S.saveToDraft,
                                onClick = { saveNote(asDraft = true) },
                                modifier = Modifier.weight(1f).height(COMPOSER_ACTION_HEIGHT)
                            )
                        }
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
                onUndo = { bodyUndo.undo()?.let { newBody = it } },
                onRedo = { bodyUndo.redo()?.let { newBody = it } },
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

        historyForId != null && viewingNote != null -> {
            // ---- Revision history for the note currently open ----
            NoteHistoryScreen(
                note = viewingNote,
                onBack = { historyForId = null },
                onRestored = { historyForId = null }
            )
        }

        viewingNote != null -> {
            // Hoisted so the jump-to-edge buttons below can drive it (task 8).
            val detailScroll = rememberScrollState()
            // ---- Read-only detail page ----
            val note = viewingNote
            val attachments = remember(note.attachments) { Attachments.parse(note.attachments) }
            val checklistView = remember(note.checklist) { Checklist.parse(note.checklist) }
            // The link graph is derived from the live note list, so it is always in step with the
            // text — see data/NoteLinks.kt for why there is no index to fall out of date.
            val linkPool = remember(notes, archivedNotes) { notes + archivedNotes }
            val outgoing = remember(note, linkPool) { NoteLinks.outgoing(note, linkPool) }
            val backlinks = remember(note, linkPool) { NoteLinks.backlinks(note, linkPool) }
            val broken = remember(note, linkPool) { NoteLinks.brokenLinks(note, linkPool) }
            val brokenLower = remember(broken) { broken.map { it.lowercase() }.toSet() }
            val versionCount by db.noteVersionDao().getForNote(note.id).collectAsState(initial = emptyList())

            // Swipe horizontally to move between notes in the current (sorted, filtered) order:
            // swipe left for the next note, right for the previous — the same list you were just
            // scrolling, so "next" means what it looks like it means. Does nothing at the ends, or
            // for a note not in the visible list (e.g. one opened from the archive).
            //
            // The gesture lives on this full-page Box, not on the inner scrolling Column, so it's
            // recognised anywhere on the page rather than only over the body text (task 7). Two strips
            // are deliberately excluded: the top band (the back / title / horizontally-scrollable
            // action header) and the bottom band. A drag is only armed when it *starts* inside the
            // central band; the inner Column still owns vertical scrolling, and the top action strip
            // still owns its own horizontal scroll (a child wins that gesture over this ancestor).
            // ---- Animated page-to-page swipe (task 7) ----
            //
            // The gesture already worked; what it lacked was any sense of movement. A drag past the
            // threshold simply swapped the content, so the next note appeared fully formed with no
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
            // [swipeOffset] is deliberately remembered WITHOUT a key on the note id: it has to
            // survive the content swap in the middle of the animation, because the exit and the entry
            // are two halves of one gesture, not two animations.
            val swipeList = sortedNotes
            val swipeOffset = remember { Animatable(0f) }
            var pageWidth by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { pageWidth = it.width.toFloat() }
                    .pointerInput(note.id, swipeList) {
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
                                    val idx = swipeList.indexOfFirst { it.id == note.id }
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
                                    val idx = swipeList.indexOfFirst { it.id == note.id }
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
                    Text(com.lucent.app.i18n.S.screenNote, color = onGradient, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))

                    // Action icons live in a right-aligned, horizontally-scrollable strip (task 17).
                    // Edit and Archive are now duplicated up here so they're reachable without scrolling
                    // to the bottom of a long note. The strip takes the remaining width and scrolls if
                    // every icon can't fit at once, so nothing is ever clipped on a narrow screen.
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
                    var actionsExpanded by remember(note.id) { mutableStateOf(false) }
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
                            copyToClipboard(context, copyTextForNote(note))
                            LucentToast.show(context.applicationContext, com.lucent.app.i18n.S.copiedAllToast)
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = com.lucent.app.i18n.S.copyAll, tint = onGradient)
                        }
                        // Pin/unpin sits at the very front of the strip (task 10) — it's the most-used
                        // quick action, so it's the first thing the thumb reaches. Confirmation is asked
                        // first (the dialog does the write).
                        PinIconButton(
                            pinned = note.pinned,
                            onToggle = { noteToTogglePin = note }
                        )
                        // Edit — opens the composer for this note (same action as the bottom button).
                        IconButton(onClick = { startEdit(note) }) {
                            Icon(Icons.Default.Edit, contentDescription = com.lucent.app.i18n.S.actionEdit, tint = onGradient)
                        }
                        // Archive / Restore — mirrors the bottom button: archives a live note, or
                        // restores one opened from the archive. Runs on the app-lifetime scope and
                        // then leaves the detail page, since the note moves between grid and archive.
                        // Archive / Restore — both ask first now (task 7); the dialog owns the write.
                        IconButton(onClick = { noteToToggleArchive = note }) {
                            Icon(
                                if (note.archived) Icons.Filled.Unarchive else Icons.Default.Archive,
                                contentDescription = if (note.archived) com.lucent.app.i18n.S.actionRestore else com.lucent.app.i18n.S.archive,
                                tint = onGradient
                            )
                        }

                        // History is only offered once there's something in it, so a note that's never
                        // been edited doesn't advertise an empty screen.
                        if (versionCount.isNotEmpty()) {
                            IconButton(onClick = { historyForId = note.id }) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = com.lucent.app.i18n.S.a11yVersionHistory(versionCount.size),
                                    tint = onGradient
                                )
                            }
                        }
                        IconButton(onClick = {
                            // Desktop has no system share sheet; the local-first equivalent is to copy
                            // the note's text (with its title as a subject line) to the clipboard and
                            // confirm with a toast — no account, no server, no link that outlives the tap.
                            DesktopShare.shareText(
                                context,
                                subject = note.title.ifBlank { "Note" },
                                text = shareTextForNote(note)
                            )
                        }) {
                            Icon(Icons.Default.Share, contentDescription = com.lucent.app.i18n.S.actionShare, tint = onGradient)
                        }
                        IconButton(onClick = { noteToDelete = note }) {
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

                // Long-press anywhere on this card to copy the note's content (task 1). It is on
                // the card rather than on the body Text so the target is the whole readable area,
                // including the padding — a long-press that lands a few pixels off the last line
                // should still work. The title/date block below is inside a SelectionContainer,
                // which consumes long-press for the platform's own text-selection toolbar, so a
                // press there still selects rather than copying; that is the correct split, since
                // someone pressing directly on the title is asking for the title.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlass(tint = NoteColor.fromKey(note.color).swatch)
                        .padding(16.dp)
                ) {
                    // Title/date/tags sit in a SelectionContainer so the reader gets the platform's
                    // native text-selection toolbar. The body is *outside* it, because a Markdown
                    // body carries tappable links and selection would swallow those taps — and the
                    // checklist is outside for the same reason its checkboxes need to stay live.
                    SelectionContainer {
                        Column {
                            Text(note.title.ifBlank { com.lucent.app.i18n.S.untitled }, color = onGradient, fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(formatTimestamp(note.updatedAt), color = onGradientMuted, fontSize = 12.sp)
                            if (note.tags.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(com.lucent.app.data.NoteTags.displayLine(note.tags), color = onGradientMuted, fontSize = 12.sp)
                            }
                        }
                    }

                    if (note.isChecklist) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ChecklistView(
                            items = checklistView,
                            header = com.lucent.app.i18n.S.historyItemsHeader,
                            onToggle = { item, checked ->
                                AppScope.io.launch {
                                    db.noteDao().update(
                                        note.copy(checklist = Checklist.setDone(note.checklist, item.id, checked))
                                    )
                                }
                            }
                        )
                    }
                    // Round R2, task 2 — the canvases come AFTER the items, so the read view lists
                    // a note's parts in the same order the composer edits them. A page that shows
                    // the same content in a different order to the screen that produced it makes
                    // the reader re-find their place every time they switch between the two.
                    if (note.isDoodle) {
                        Spacer(modifier = Modifier.height(12.dp))
                        DoodleView(value = note.doodle)
                    }
                    // Task A20: no longer an `else`. A checklist note's remarks and its items are
                    // both content and both belong on the page — the old branch showed the body
                    // *instead of* the list, so for a checklist note the remarks were invisible
                    // even once they existed. Rendering is otherwise untouched: Markdown, [[links]]
                    // and plain text all behave exactly as they do on a plain note.
                    if (note.body.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val openLink: (String) -> Unit = { target ->
                            val hit = NoteLinks.resolve(target, linkPool)
                            if (hit != null) viewingId = hit.id else startCreate(prefillTitle = target)
                        }
                        when {
                            markdownEnabled -> MarkdownText(
                                text = note.body,
                                brokenLinks = brokenLower,
                                onWikiLink = openLink,
                                linksEnabled = linksEnabled
                            )
                            // Links on, Markdown off (task 8): the body is shown exactly as typed —
                            // no headings, no bold, no code — but [[links]] and [text](url) are still
                            // live. This is the combination the old coupling made impossible.
                            linksEnabled -> LinkedPlainText(
                                text = note.body,
                                brokenLinks = brokenLower,
                                onWikiLink = openLink
                            )
                            // Both off: exactly what was typed, inert.
                            else -> Text(note.body, color = onGradient)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        val statsLabel = remember(note.body) { com.lucent.app.data.NoteStats.paragraphCharLabel(note.body) }
                        Text(statsLabel, color = onGradientMuted, fontSize = 12.sp)
                    }

                    // Attachments live only on the detail page, never on the home cards.
                    CardAttachments(
                        attachments, onGradient, onGradientMuted,
                        // Task A4: renaming touches only the display name in this row's JSON — the
                        // bytes stay where they are, keyed by the same store id. Written straight to
                        // the row because the detail page has no draft to fold it into.
                        onRename = { att, newName ->
                            AppScope.io.launch {
                                db.noteDao().update(
                                    note.copy(
                                        attachments = Attachments.serialize(
                                            attachments.map { if (it.data == att.data) it.copy(name = newName) else it }
                                        )
                                    )
                                )
                            }
                        }
                    )
                }

                // The link graph (outgoing/broken/backlinks) is part of the [[links]] feature, so it
                // only shows when links are actually active — off in plain-text mode, and off when
                // the Links toggle itself is off (task 3).
                if (linksActive) {
                    if (outgoing.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        NoteLinkChips(com.lucent.app.i18n.S.linksToHeader, outgoing) { target -> viewingId = target.id }
                    }
                    if (broken.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        BrokenLinkChips(broken) { target -> startCreate(prefillTitle = target) }
                    }
                    if (backlinks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        NoteLinkChips(com.lucent.app.i18n.S.linkedFromHeader, backlinks) { source -> viewingId = source.id }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassCapsuleButton(
                        text = com.lucent.app.i18n.S.editNote,
                        icon = Icons.Default.Edit,
                        onClick = { startEdit(note) },
                        modifier = Modifier.weight(1f)
                    )
                    // Archive sits right next to Edit in the same capsule style. For a note that's
                    // already archived (opened from the archive) this restores it instead. Both run
                    // on the app-lifetime scope and then leave the detail page, since the note is
                    // about to move between the home grid and the archive.
                    GlassCapsuleButton(
                        text = if (note.archived) com.lucent.app.i18n.S.actionRestore else com.lucent.app.i18n.S.archive,
                        icon = if (note.archived) Icons.Filled.Unarchive else Icons.Default.Archive,
                        // Confirmation first (task 7) — the dialog performs the write and leaves the page.
                        onClick = { noteToToggleArchive = note },
                        modifier = Modifier.weight(1f)
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

        showArchive -> {
            // ---- Archive screen ----
            // Shown in place of the home grid when "Archived notes" is picked from the overflow
            // menu. Opening a note from here sets viewingId, which the `viewingNote` branch above
            // handles (it looks in archivedNotes too), so tapping an archived note shows its detail
            // page; backing out returns here because showArchive is still true. Delete delegates to
            // the same move-to-trash dialog the home grid uses.
            ArchivedNotesScreen(
                onBack = { showArchive = false },
                onOpen = { note -> viewingId = note.id },
                onDeleteRequest = { note -> noteToDelete = note }
            )
        }

        showTrash -> {
            // ---- Trash screen ----
            TrashNotesScreen(onBack = { showTrash = false })
        }

        showHidden && HiddenArea.visible -> {
            // ---- Hidden area (task A21) ----
            HiddenNotesScreen(
                onBack = { showHidden = false },
                onOpen = { showHidden = false; openDetail(it) }
            )
        }

        showDrafts -> {
            // ---- Draft area (task A10) ----
            DraftNotesScreen(
                onBack = { showDrafts = false },
                // Opening a draft puts it straight back in the editor — the list exists to get you
                // back to unfinished work, not to display it. It stays flagged as a draft until the
                // user either saves it properly or moves it out explicitly.
                onOpen = { showDrafts = false; startEdit(it) }
            )
        }

        showSearch -> {
            // ---- Unified search ----
            // Notes *and* tasks in one pass, archived/completed/trashed included. A task result routes
            // through AppNavigation, so tapping it actually switches tabs and opens the task — a
            // result you can't open isn't a result, and a toast explaining where it lives instead
            // would just be the app describing the destination rather than going there.
            SearchScreen(
                onOpenNote = { note ->
                    showSearch = false
                    viewingId = note.id
                    returnToOnClose = null
                },
                // A task lives on the other tab: record that the jump started here and leave this
                // search open behind it, so closing the task returns to these results (task 4).
                onOpenTask = { task -> AppNavigation.openTask(task.id, from = Screen.Notes) },
                onBack = { showSearch = false }
            )
        }

        else -> {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                if (selectionMode) {
                    // A dedicated action bar takes over the top row while selecting: a live count,
                    // a way out, and batch delete for the current selection.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = com.lucent.app.i18n.S.a11yCancelSelection, tint = onGradient)
                        }
                        Text(
                            com.lucent.app.i18n.S.nSelected(selectedNoteIds.size),
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
                                val ids = selectedNoteIds
                                val target = showHidden
                                AppScope.io.launch {
                                    ids.forEach { id ->
                                        db.noteDao().getByIdOnce(id)?.let { row ->
                                            db.noteDao().update(row.copy(hidden = !target))
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
                        // Select-all / clear-all toggle for the notes currently in view.
                        TextButton(onClick = {
                            val allIds = sortedNotes.map { it.id }.toSet()
                            selectedNoteIds = if (selectedNoteIds.containsAll(allIds)) emptySet() else allIds
                        }) {
                            Text(if (selectedNoteIds.containsAll(sortedNotes.map { it.id }.toSet()) && sortedNotes.isNotEmpty()) com.lucent.app.i18n.S.clearAllSelection else com.lucent.app.i18n.S.selectAll)
                        }
                        IconButton(
                            onClick = { if (selectedNoteIds.isNotEmpty()) showBatchDeleteConfirm = true },
                            enabled = selectedNoteIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = com.lucent.app.i18n.S.a11yDeleteSelected, tint = onGradient)
                        }
                    }
                } else {
                    // Header with a collapsible action cluster (settings task 16), synthesized with the
                    // tasks/notes header changes: the "+" is always visible; the date-range filter, sort
                    // and overflow buttons hide behind the "<" chevron by default and slide out to the
                    // left when tapped while the search box smoothly resizes to make room. The search
                    // field carries no placeholder text at all (tasks items 6 & 15), so nothing can ever
                    // be clipped on a narrow screen — the leading icon says what the field is for.
                    CollapsibleActionBar(
                        expanded = actionsExpanded,
                        onToggleExpanded = { actionsExpanded = !actionsExpanded },
                        search = {
                            OutlinedTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = com.lucent.app.i18n.S.a11ySearchNotes) },
                                trailingIcon = { SearchHelpButton() },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        actions = {
                            // Tap to pick a start and end date; the grid then shows only notes in that range.
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
                                options = NoteSort.entries.toList(),
                                label = { it.label },
                                onSelect = { option -> scope.launch { settingsRepo.setNotesSort(option.key) } },
                                tint = onGradientMuted,
                                activeTint = onGradient
                            )
                            // Archive, Trash and Select live behind one overflow menu, so this bar
                            // doesn't grow an icon every time another action is added.
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = com.lucent.app.i18n.S.a11yMoreOptions, tint = onGradientMuted)
                                }
                                DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                                    // Global search is intentionally NOT here on the desktop: the
                                    // sidebar already has a dedicated Search destination, so repeating
                                    // it in this menu would be redundant. Only Archive and Trash remain.
                                    DropdownMenuItem(
                                        text = { Text(com.lucent.app.i18n.S.screenArchivedNotes) },
                                        leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                                        onClick = { showOverflowMenu = false; showArchive = true }
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
                            NewItemButton(contentDescription = com.lucent.app.i18n.S.newNote, onClick = { startCreate() })
                        }
                    )

                    // Only shown while a date filter is active, so it never adds clutter otherwise.
                    dateRange?.let { (start, end) ->
                        Spacer(modifier = Modifier.height(8.dp))
                        DateFilterChip(startMillis = start, endMillis = end, onClear = { dateRange = null })
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Notes render as a two-column card grid, and the grid now fills everything below
                // the header even when it has little or nothing to show (task 5).
                //
                // A scrollable container only hears drags inside its own bounds. Wrapping its height
                // meant the blank space under the last row — or the entire page, with no notes at
                // all — swallowed every gesture, including the one that raises the "Notes" title back
                // into view after it has been scrolled away. Filling the area fixes both: the empty
                // state now rides *inside* the grid as a full-width item rather than replacing it.
                // Wrapped in a Box so the scroll-edge jump buttons can overlay the grid (task E2).
                Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    // Four across on the desktop's wide window (Android stays at two). A large monitor
                    // has the room, and four cards a row reads as a proper board rather than a phone
                    // list stretched sideways.
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize().hazeSource(state = LocalHazeState.current),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    // Reserve the floating capsule's height so the last note clears the pill; the
                    // grid still extends under the capsule, which is what its blur samples.
                    contentPadding = PaddingValues(bottom = LocalBottomBarInset.current)
                ) {
                    if (sortedNotes.isEmpty()) {
                        item(key = "empty_state", span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                isFiltered = searchText.isNotBlank() || dateRange != null,
                                emptyMessage = com.lucent.app.i18n.S.emptyNotesHint,
                                noMatchMessage = com.lucent.app.i18n.S.noNotesMatchSearch
                            )
                        }
                    } else {
                        // The item modifier is handed in from inside `items { }`, where
                        // LazyGridItemScope.animateItem is in scope: that is what makes every card
                        // that has to shift slide and settle instead of teleporting.
                        val renderCard: @Composable (Note, Modifier) -> Unit = { note, itemModifier ->
                            NoteCard(
                                note = note,
                                reorderVisualModifier = itemModifier.reorderVisuals(note.id, reorderState),
                                selectionMode = selectionMode,
                                selected = note.id in selectedNoteIds,
                                onOpen = { openDetail(note) },
                                onLongPress = { selectionMode = true; selectedNoteIds = setOf(note.id) },
                                reorderEnabled = reorderEnabled,
                                reorderModifier = Modifier.reorderableGridItem(
                                    id = note.id,
                                    enabled = reorderEnabled,
                                    gridState = gridState,
                                    state = reorderState,
                                    onLongPress = {
                                        selectionMode = true
                                        if (note.id !in selectedNoteIds) selectedNoteIds = selectedNoteIds + note.id
                                    },
                                    onDrop = { beforeId, afterId -> dropSelection(beforeId, afterId) }
                                ),
                                onToggleSelect = {
                                    selectedNoteIds = if (note.id in selectedNoteIds) selectedNoteIds - note.id else selectedNoteIds + note.id
                                },
                                onTogglePin = { noteToTogglePin = note },
                                onDelete = { noteToDelete = note }
                            )
                        }
                        if (sections != null) {
                            sections.nonEmpty().forEach { (section, list) ->
                                item(key = "header_${section.name}", span = { GridItemSpan(maxLineSpan) }) {
                                    HomeSectionHeader(section.label)
                                }
                                items(list, key = { it.id }) { note ->
                                    renderCard(note, Modifier.animateItem(placementSpec = placementSpec))
                                }
                            }
                        } else {
                            items(sortedNotes, key = { it.id }) { note ->
                                renderCard(note, Modifier.animateItem(placementSpec = placementSpec))
                            }
                        }
                    }
                }
                ScrollEdgeJumpButtons(
                    canUp = gridState.canScrollBackward,
                    canDown = gridState.canScrollForward,
                    tint = onGradient,
                    onUp = { scope.launch { gridState.animateScrollToItem(0) } },
                    onDown = { scope.launch { gridState.animateScrollToItem((gridState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = LocalBottomBarInset.current + 14.dp)
                )
                }
            }
        }
    }
}

/** Plain-text rendering of a note for the OS share sheet. */
private fun shareTextForNote(note: Note): String {
    val body = if (note.isChecklist) Checklist.toMarkdown(note.checklist) else note.body
    return if (note.title.isBlank()) body else "${note.title}\n\n$body"
}

/**
 * What a long-press on the detail page copies: the note's CONTENT and nothing else.
 *
 * Deliberately not [shareTextForNote]. Sharing sends someone else a note, so it leads with the
 * title for context. Copying is almost always a step in moving the text somewhere you are already
 * working — a message, a document, another note — and there the title and the timestamp are
 * exactly the two things you would have to delete by hand afterwards. So they are not included.
 *
 * A checklist note copies as its items (one per line, ticked state preserved) because that IS its
 * content; a prose note copies as its body, verbatim, with no Markdown added or stripped.
 */
private fun copyTextForNote(note: Note): String =
    if (note.isChecklist) Checklist.toMarkdown(note.checklist).trim() else note.body.trim()

/**
 * A single note as a card in the two-per-row home grid.
 *
 * Shows the title, the date, and a short preview of the body — or, for a checklist note, its first
 * few items, because "three of five ticked" is the thing you actually want to know at a glance.
 * A pin marker sits beside the title when pinned, the card itself is tinted with the note's colour,
 * and a low-emphasis delete button in the corner routes through the same move-to-trash dialog as
 * everywhere else. The fixed height keeps the grid tidy however much text each note holds.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    // Task A16 — see the identical pair on TaskCard: exactly one long-press detector at a time.
    reorderEnabled: Boolean = false,
    reorderModifier: Modifier = Modifier,
    // Applied FIRST, so the drag carries the whole glass panel rather than only its contents, and so
    // the lifted card is drawn above its neighbours. See [reorderVisuals].
    reorderVisualModifier: Modifier = Modifier,
    onToggleSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val selShape = RoundedCornerShape(20.dp)
    // Cache the formatted timestamp keyed on the raw value (settings task 8). formatTimestamp
    // allocates a Date + formatter; recomputing it on every recomposition (a scroll can trigger
    // many) is pure waste when the underlying updatedAt hasn't changed. Keyed so an edit refreshes.
    val formattedTimestamp = remember(note.updatedAt) { formatTimestamp(note.updatedAt) }
    Column(
        modifier = reorderVisualModifier
            .fillMaxWidth()
            .height(172.dp)
            .frostedGlass(tint = NoteColor.fromKey(note.color).swatch)
            // A long press enters multi-select and grabs this card; once in select mode a tap toggles
            // this card instead of opening it. Outside select mode a tap opens as before.
            // Order matters: the drag detector must be the INNER pointer handler so it wins the
            // main pass and can consume the long press. Declared before `combinedClickable` it was
            // the outer one, and the click node got first refusal on every gesture.
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                onLongClick = if (reorderEnabled) null else { { if (!selectionMode) onLongPress() } }
            )
            .then(reorderModifier)
            .then(
                if (selected) Modifier.border(2.dp, onGradient, selShape) else Modifier
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(modifier = Modifier.weight(1f)) {
                if (note.pinned) {
                    PinnedMarker(modifier = Modifier.padding(top = 2.dp, end = 4.dp))
                }
                Text(
                    note.title.ifBlank { com.lucent.app.i18n.S.untitled },
                    color = onGradient,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selectionMode) {
                // Selection tick replaces the delete affordance while choosing.
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (selected) com.lucent.app.i18n.S.a11ySelected else com.lucent.app.i18n.S.a11yNotSelected,
                    tint = if (selected) onGradient else onGradientMuted,
                    modifier = Modifier.padding(start = 6.dp).size(20.dp)
                )
            } else {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = if (note.pinned) com.lucent.app.i18n.S.actionUnpin
                                         else com.lucent.app.i18n.S.actionPin,
                    tint = if (note.pinned) onGradient else onGradientMuted,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(18.dp)
                        .clickable { onTogglePin() }
                )
                Icon(
                    Icons.Default.Delete,
                    contentDescription = com.lucent.app.i18n.S.actionDelete,
                    tint = onGradientMuted,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(18.dp)
                        .clickable { onDelete() }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            formattedTimestamp,
            color = onGradientMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (note.isChecklist) {
            val items = remember(note.checklist) { Checklist.parse(note.checklist) }
            if (items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                ChecklistPreviewInline(items = items, modifier = Modifier.weight(1f), maxVisible = 3)
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        } else if (note.body.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                note.body,
                color = onGradientMuted,
                fontSize = 13.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (note.tags.isNotBlank()) {
            Text(
                com.lucent.app.data.NoteTags.displayLine(note.tags),
                color = onGradientMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A full-width section header ("Recent" / "Today" / "Older") between rows of the home grid. */
@Composable
private fun HomeSectionHeader(label: String) {
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

/** The glyph for a template chip. Kept here so `data/NoteTemplates.kt` stays free of Compose types. */
@Composable
private fun templateIcon(template: NoteTemplate) = when (template.iconName) {
    com.lucent.app.data.TemplateIcon.JOURNAL -> Icons.Default.Book
    com.lucent.app.data.TemplateIcon.MEETING -> Icons.Default.Groups
    com.lucent.app.data.TemplateIcon.IDEA -> Icons.Default.Lightbulb
    com.lucent.app.data.TemplateIcon.CHECKLIST -> Icons.AutoMirrored.Filled.FormatListBulleted
}

/**
 * Task A12. Below this many characters a note's body fits on screen without scrolling on any
 * reasonable device, so jumping to its end would move the page for no visible benefit — and moving
 * a page the user did not ask to move is its own small annoyance. Above it, the end is off-screen
 * and landing there is the whole point.
 */
private const val BODY_JUMP_THRESHOLD = 400
