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
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
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

private val DEFAULT_TAGS: List<String>
    get() = com.lucent.app.data.NoteTags.DEFAULTS

@Composable
fun NotesScreen(active: Boolean = true) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val settingsRepo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val notes by remember { db.noteDao().getAll() }.collectAsState(initial = com.lucent.app.data.DataCache.notes)
    val archivedNotes by remember { db.noteDao().getArchived() }.collectAsState(initial = emptyList())
    val hiddenNotes by remember { db.noteDao().getHidden() }.collectAsState(initial = emptyList())
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    val sortKey by settingsRepo.notesSort
        .collectAsState(initial = com.lucent.app.data.SettingsCache.notesSort ?: "recent")
    val sortOption = NoteSort.fromKey(sortKey)

    val markdownEnabled by settingsRepo.markdownEnabled.collectAsState(initial = false)
    val linksEnabled by settingsRepo.linksEnabled.collectAsState(initial = true)
    val linksActive = linksEnabled

    val noteUsage by remember { com.lucent.app.data.UsageTracker.scores(context, com.lucent.app.data.UsageTracker.Kind.NOTE) }
        .collectAsState(initial = emptyMap())

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
    var isDoodleMode by remember { mutableStateOf(false) }
    var doodleData by remember { mutableStateOf("") }
    var checklistItems by remember { mutableStateOf<List<ChecklistItem>>(emptyList()) }
    var newChecklistItemText by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var dateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var noteToTogglePin by remember { mutableStateOf<Note?>(null) }
    var noteToToggleArchive by remember { mutableStateOf<Note?>(null) }
    var showArchive by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var showDrafts by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var showNotebooks by remember { mutableStateOf(false) }
    var showNotebookPicker by remember { mutableStateOf(false) }
    val draftCount by db.noteDao().getDrafts().collectAsState(initial = emptyList())
    DraftRestoreDialog(draftCount = draftCount.size, onOpenDrafts = { showDrafts = true })
    SessionRestoreDialog()
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }
    var historyForId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        settingsRepo.noteHistoryEnabled.collect { com.lucent.app.data.NoteHistory.enabled = it }
    }
    LaunchedEffect(Unit) {
        settingsRepo.taskHistoryEnabled.collect { com.lucent.app.data.TaskHistory.enabled = it }
    }
    val gridState = rememberLazyGridState()

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
    var templateAuthoring by remember { mutableStateOf(false) }
    var pendingTemplateDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingTemplateMenu by remember { mutableStateOf<TemplateMenu?>(null) }
    var pendingBuiltinHide by remember { mutableStateOf<NoteTemplate?>(null) }
    var showRestoreBuiltins by remember { mutableStateOf(false) }
    var editingTemplateId by remember { mutableStateOf<String?>(null) }
    val bodyUndo = remember(composing) { TextUndoStack(newBody) }
    val repo = remember { com.lucent.app.data.SettingsRepository(context) }
    val richTextEnabled by repo.richTextEnabled.collectAsState(initial = false)
    val customTemplatesJson by repo.customTemplatesJson.collectAsState(initial = "[]")
    val customTemplates = remember(customTemplatesJson) {
        com.lucent.app.data.CustomTemplates.parse(customTemplatesJson)
    }
    val templateDraftJson by repo.templateDraftJson.collectAsState(initial = "")
    val hiddenTemplatesJson by repo.hiddenTemplatesJson.collectAsState(initial = "")
    val hiddenBuiltins = remember(hiddenTemplatesJson) {
        hiddenTemplatesJson.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }
    var bodySpans by remember { mutableStateOf(emptyList<com.lucent.app.data.RichSpan>()) }
    var bodySelStart by remember(composing) { mutableStateOf(0) }
    var bodySelEnd by remember(composing) { mutableStateOf(0) }
    var pendingKinds by remember(composing) { mutableStateOf(emptySet<com.lucent.app.data.RichSpan.Kind>()) }
    var pendingHighlight by remember(composing) { mutableStateOf<Int?>(null) }
    var pendingExplicit by remember(composing) { mutableStateOf(false) }
    var pendingColor by remember(composing) { mutableStateOf<Int?>(null) }
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
    val reorderState = rememberReorderDragState()
    val reorderSlots = rememberGridSlots(gridState)
    var selectionMode by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    fun exitSelection() { selectionMode = false; selectedNoteIds = emptySet() }

    var returnToOnClose by remember { mutableStateOf<Screen?>(null) }

    LaunchedEffect(AppNavigation.pendingNoteId) {
        AppNavigation.consumeNoteId()?.let { id ->
            showSearch = false
            showArchive = false
            showTrash = false
            showNotebooks = false
            viewingId = id
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

    LaunchedEffect(AppNavigation.composeNoteRequested) {
        if (AppNavigation.consumeComposeNote()) {
            showSearch = false
            showArchive = false
            showTrash = false
            showNotebooks = false
            viewingId = null
            startCreate()
        }
    }

    fun applyTemplate(template: NoteTemplate) {
        val prefill = template.prefill()
        newTitle = prefill.title
        newBody = prefill.body
        selectedTags = prefill.tags
        isChecklistMode = prefill.isChecklist
        checklistItems = prefill.checklist
    }


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

    fun startTemplateAuthoring(fromDraft: com.lucent.app.data.CustomTemplates.Draft?) {
        editingTemplateId = null
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

    fun discardTemplateAuthoring() {
        templateAuthoring = false
        editingTemplateId = null
        resetComposer()
        scope.launch { repo.setTemplateDraftJson("") }
    }

    fun persistTemplateDraft() {
        val draft = currentTemplateDraft()
        if (com.lucent.app.data.CustomTemplates.draftEmpty(draft)) {
            scope.launch { repo.setTemplateDraftJson("") }
        } else {
            scope.launch { repo.setTemplateDraftJson(com.lucent.app.data.CustomTemplates.draftToJson(draft)) }
        }
    }

    fun startTemplateEdit(t: com.lucent.app.data.CustomTemplates.Template) {
        editingTemplateId = null
        resetComposer()
        newTitle = t.title
        newBody = t.body
        selectedTags = t.tags.toSet()
        pinned = t.pinned
        selectedColor = t.colorKey?.let { key ->
            NoteColor.entries.firstOrNull { it.key == key }
        } ?: NoteColor.DEFAULT
        isChecklistMode = t.isChecklist
        checklistItems = t.checklistTexts.map { Checklist.newItem(it) }
        editingTemplateId = t.id
        templateAuthoring = true
    }

    fun startTemplateEditBuiltIn(template: NoteTemplate) {
        val prefill = template.prefill()
        editingTemplateId = null
        resetComposer()
        newTitle = prefill.title
        newBody = prefill.body
        selectedTags = prefill.tags
        isChecklistMode = prefill.isChecklist
        checklistItems = prefill.checklist
        templateAuthoring = true
    }

    fun hideBuiltinTemplate(template: NoteTemplate) {
        val next = (hiddenBuiltins + template.name).joinToString(",")
        scope.launch { repo.setHiddenTemplatesJson(next) }
    }

    fun restoreBuiltinTemplates(names: List<String>?) {
        val next = (if (names == null) emptySet() else hiddenBuiltins - names.toSet()).joinToString(",")
        scope.launch { repo.setHiddenTemplatesJson(next) }
    }

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

    fun saveCustomTemplate() {
        if (newTitle.isBlank()) {
            LucentToast.show(templateToastContext(context), com.lucent.app.i18n.S.tplNeedsTitle)
            return
        }
        val t = com.lucent.app.data.CustomTemplates.Template(
            id = editingTemplateId ?: java.util.UUID.randomUUID().toString(),
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
        editingTemplateId = null
        resetComposer()
        scope.launch {
            repo.setCustomTemplatesJson(com.lucent.app.data.CustomTemplates.upsert(previous, t))
            repo.setTemplateDraftJson("")
        }
        LucentToast.show(templateToastContext(context), com.lucent.app.i18n.S.tplSavedToast)
    }

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

    fun closeDetail() {
        viewingId = null
        val back = returnToOnClose
        returnToOnClose = null
        if (back != null) AppNavigation.requestScreen(back)
    }

    fun openDetail(note: Note) {
        viewingId = note.id
        returnToOnClose = null
        AppScope.io.launch {
            com.lucent.app.data.UsageTracker.recordOpen(context, com.lucent.app.data.UsageTracker.Kind.NOTE, note.id)
        }
    }

    fun startEdit(note: Note) {
        editingId = note.id
        newTitle = note.title
        newBody = note.body
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

    var draftRowId by remember(composing, editingId) { mutableStateOf<Long?>(null) }
    fun saveNote(
        asDraft: Boolean = false,
        closeAfter: Boolean = !asDraft
    ) {
        val pendingItem = newChecklistItemText.trim()
        val composedChecklist = (
            if (isChecklistMode && pendingItem.isNotEmpty()) checklistItems + Checklist.newItem(pendingItem)
            else checklistItems
            ).filter { it.text.isNotBlank() }
        val prunedDoodle = com.lucent.app.ui.DoodlePages.pruneEmpty(doodleData)
        val hasDoodle = isDoodleMode && prunedDoodle.isNotBlank()
        if (newTitle.isBlank() && newBody.isBlank() && pendingAttachments.isEmpty() &&
            composedChecklist.isEmpty() && !hasDoodle
        ) {
            composing = false
            return
        }
        val title = newTitle
        val body = newBody
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

        val draftToClear = draftRowId
        draftRowId = null

        val appContext = context.applicationContext

        com.lucent.app.data.backgroundWrite(context, "note save") {
            val existing = if (id != null) db.noteDao().getByIdOnce(id) else null
            if (existing != null) {
                NoteHistory.recordIfChanged(
                    db = db,
                    existing = existing,
                    newTitle = title,
                    newBody = body,
                    newTags = tags,
                    newIsChecklist = isChecklistSnapshot,
                    newChecklist = checklistJson
                )
                val updated = existing.copy(
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
                        id = id ?: 0,
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
                isDoodleMode != original.isDoodle || doodleData != original.doodle ||
                (isChecklistMode && newChecklistItemText.isNotBlank())
        } else {
            newTitle.isNotBlank() || newBody.isNotBlank() || pendingAttachments.isNotEmpty() ||
                selectedTags.isNotEmpty() || pinned || selectedColor != NoteColor.DEFAULT ||
                checklistItems.isNotEmpty() || (isChecklistMode && newChecklistItemText.isNotBlank()) ||
                !com.lucent.app.ui.DoodlePages.isEmpty(doodleData)
        }
    }

    val noteContentDirty = composing && editingId == null && (
        newTitle.isNotBlank() || newBody.isNotBlank() ||
            selectedTags.isNotEmpty() || checklistItems.isNotEmpty() ||
            newChecklistItemText.isNotBlank()
        )

    fun discardComposer() {
        if (templateAuthoring) persistTemplateDraft()
        templateAuthoring = false
        composing = false
        resetComposer()
    }

    fun leaveComposer() {
        if (templateAuthoring) {
            discardComposer()
            return
        }
        if (noteDirty) showUnsavedDialog = true else discardComposer()
    }

    BackHandler(enabled = composing) { leaveComposer() }
    BackHandler(enabled = !composing && historyForId != null) { historyForId = null }
    BackHandler(enabled = !composing && historyForId == null && viewingId != null) { closeDetail() }
    BackHandler(enabled = !composing && historyForId == null && viewingId == null && showArchive) { showArchive = false }
    BackHandler(enabled = !composing && historyForId == null && viewingId == null && !showArchive && showTrash) { showTrash = false }
    BackHandler(enabled = !composing && historyForId == null && viewingId == null && !showArchive && !showTrash && showSearch) { showSearch = false }
    BackHandler(enabled = !composing && historyForId == null && viewingId == null && (showDrafts || showHidden)) {
        if (showDrafts) showDrafts = false else showHidden = false
    }
    BackHandler(enabled = !composing && historyForId == null && viewingId == null &&
        !showArchive && !showTrash && !showSearch && !showDrafts && !showHidden && showNotebooks) { showNotebooks = false }
    BackHandler(enabled = selectionMode && !composing && historyForId == null && viewingId == null && !showArchive && !showTrash && !showSearch && !showDrafts && !showHidden) { exitSelection() }

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
            showNotebooks = false
            actionsExpanded = false
            exitSelection()
        }
    }

    OnAppHidden { actionsExpanded = false }

    SideEffect {
        if (noteDirty && !templateAuthoring) {
            UnsavedChangesGuard.register(
                owner = "notes",
                onSave = { saveNote() },
                onDiscard = { discardComposer() },
                onAutoDraft = { saveNote(asDraft = true, closeAfter = false) }
            )
        } else {
            UnsavedChangesGuard.clear("notes")
        }
    }
    DisposableEffect(Unit) { onDispose { UnsavedChangesGuard.clear("notes") } }

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
        bodySpans = com.lucent.app.data.RichText.load(p.optString("bodySpans"), newBody)
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

    val templateMenu = pendingTemplateMenu
    if (templateMenu != null) {
        AlertDialog(
            onDismissRequest = { pendingTemplateMenu = null },
            title = {
                Text(
                    when (templateMenu) {
                        is TemplateMenu.BuiltIn -> templateMenu.template.label
                        is TemplateMenu.Custom -> templateMenu.template.name
                    }
                )
            },
            text = { Text(com.lucent.app.i18n.S.tplMenuHint) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        when (templateMenu) {
                            is TemplateMenu.BuiltIn -> startTemplateEditBuiltIn(templateMenu.template)
                            is TemplateMenu.Custom -> startTemplateEdit(templateMenu.template)
                        }
                        pendingTemplateMenu = null
                    }) { Text(com.lucent.app.i18n.S.actionEdit) }
                    TextButton(onClick = {
                        when (templateMenu) {
                            is TemplateMenu.BuiltIn -> pendingBuiltinHide = templateMenu.template
                            is TemplateMenu.Custom -> pendingTemplateDeleteId = templateMenu.template.id
                        }
                        pendingTemplateMenu = null
                    }) { Text(com.lucent.app.i18n.S.actionDelete) }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTemplateMenu = null }) {
                    Text(com.lucent.app.i18n.S.actionCancel)
                }
            }
        )
    }

    pendingBuiltinHide?.let { builtin ->
        AlertDialog(
            onDismissRequest = { pendingBuiltinHide = null },
            title = { Text(com.lucent.app.i18n.S.tplDeleteTitle) },
            text = { Text(com.lucent.app.i18n.S.tplDeleteBody(builtin.label)) },
            confirmButton = {
                TextButton(onClick = {
                    hideBuiltinTemplate(builtin)
                    pendingBuiltinHide = null
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBuiltinHide = null }) {
                    Text(com.lucent.app.i18n.S.actionCancel)
                }
            }
        )
    }

    if (showRestoreBuiltins) {
        val hiddenList = NoteTemplate.entries.filter { it.name in hiddenBuiltins }
        AlertDialog(
            onDismissRequest = { showRestoreBuiltins = false },
            title = { Text(com.lucent.app.i18n.S.tplRestoreBuiltins) },
            text = {
                Column {
                    hiddenList.forEach { t ->
                        TextButton(onClick = {
                            restoreBuiltinTemplates(listOf(t.name))
                            showRestoreBuiltins = false
                        }) { Text(t.label) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreBuiltinTemplates(null)
                    showRestoreBuiltins = false
                }) { Text(com.lucent.app.i18n.S.tplRestoreAll) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreBuiltins = false }) {
                    Text(com.lucent.app.i18n.S.actionCancel)
                }
            }
        )
    }

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

    val allTags = remember(notes, selectedTags) {
        (DEFAULT_TAGS +
            notes.flatMap { com.lucent.app.data.NoteTags.parse(it.tags) } +
            selectedTags.map { com.lucent.app.data.NoteTags.canonical(it) }).distinct()
    }

    val query = remember(searchText) { SearchQuery.parse(searchText) }
    val searchPool = remember(notes, archivedNotes, query) {
        if ("archived" in query.flags) notes + archivedNotes else notes
    }
    val filteredNotes = remember(searchPool, query, dateRange) {
        searchPool.filterBySearch(query).filter { note ->
            dateRange?.let { (start, end) -> withinLocalDayRange(note.updatedAt, start, end) } ?: true
        }
    }
    val sortedNotes = remember(filteredNotes, sortOption, query) {
        filteredNotes.sortedForDisplay(sortOption, query)
    }

    val reorderEnabled = true
    val placementSpec = rememberReorderPlacementSpec()
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
            isPinned = { it.pinned },
            orderWithinSections = sortOption == NoteSort.CUSTOM
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
    val inlineLocalIds = remember(sortedNotes) { sortedNotes.mapTo(HashSet()) { it.id } }
    val inlineGlobalNotes = remember(inlineGlobal.notes, inlineLocalIds) {
        inlineGlobal.notes.filterNot { it.id in inlineLocalIds }
    }
    val inlineGlobalTasks = inlineGlobal.tasks

    fun dropSelection(beforeId: Long?, afterId: Long?) {
        val moving = selectedNoteIds.toList().mapNotNull { id -> sortedNotes.firstOrNull { it.id == id } }
        if (moving.isEmpty()) return
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
                if (n.manualOrder != index) db.noteDao().setManualOrder(n.id, index)
            }
        }
        if (sortOption != NoteSort.CUSTOM) {
            scope.launch { settingsRepo.setNotesSort(NoteSort.CUSTOM.key) }
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

    if (showNotebookPicker) {
        AddToNotebookDialog(
            noteIds = selectedNoteIds,
            taskIds = emptySet(),
            onDismiss = { showNotebookPicker = false },
            onAdded = { showNotebookPicker = false; exitSelection() }
        )
    }

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
                    if (viewingId == target.id) closeDetail()
                }) { Text(if (willArchive) com.lucent.app.i18n.S.archive else com.lucent.app.i18n.S.actionRestore) }
            },
            dismissButton = { TextButton(onClick = { noteToToggleArchive = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

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
                    val pinnedNow = !target.pinned
                    noteToTogglePin = null
                    com.lucent.app.data.backgroundWrite(context, "note pin toggle") {
                        db.noteDao().setPinned(target.id, pinnedNow)
                    }
                }) { Text(if (willPin) com.lucent.app.i18n.S.actionPin else com.lucent.app.i18n.S.actionUnpin) }
            },
            dismissButton = { TextButton(onClick = { noteToTogglePin = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    val viewingNote = remember(notes, archivedNotes, hiddenNotes, viewingId) {
        (notes + archivedNotes + hiddenNotes).firstOrNull { it.id == viewingId && it.trashedAt == null }
    }

    when {
        composing -> {
            val jumpToBodyEnd = remember { editingId != null && newBody.length > BODY_JUMP_THRESHOLD }
            val bodyEndRequester = remember { BringIntoViewRequester() }
            if (jumpToBodyEnd) {
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    withFrameNanos { }
                    bodyEndRequester.bringIntoView()
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
                onUndo = { bodyUndo.undo()?.let { newBody = it } },
                onRedo = { bodyUndo.redo()?.let { newBody = it } },
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
                    Text(if (editingId != null) com.lucent.app.i18n.S.editNote else com.lucent.app.i18n.S.newNote, color = onGradient, fontSize = 20.sp)
                }

                Column(modifier = Modifier.fillMaxWidth().frostedGlass(tint = selectedColor.swatch).padding(16.dp)) {
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
                                NoteTemplate.entries
                                    .filter { it.name !in hiddenBuiltins }
                                    .forEach { template ->
                                        TemplateChipWithMenu(
                                            label = { Text(template.label) },
                                            icon = templateIcon(template),
                                            onClick = { applyTemplate(template) },
                                            onLongPress = {
                                                pendingTemplateMenu = TemplateMenu.BuiltIn(template)
                                            }
                                        )
                                    }
                                customTemplates.forEach { t ->
                                    TemplateChipWithMenu(
                                        label = {
                                            Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        icon = Icons.Default.Star,
                                        onClick = { applyCustomTemplate(t) },
                                        onLongPress = {
                                            pendingTemplateMenu = TemplateMenu.Custom(t)
                                        }
                                    )
                                }
                                if (hiddenBuiltins.isNotEmpty()) {
                                    FilterChip(
                                        selected = false,
                                        onClick = { showRestoreBuiltins = true },
                                        label = { Text(com.lucent.app.i18n.S.tplRestoreBuiltins) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Restore,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
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

                    val detailsIsAside = isChecklistMode || isDoodleMode
                    ExpandableGlassTextField(
                        value = newBody,
                        onValueChange = { newBody = it },
                        extraAction = { DictationButton(onText = { spoken ->
                            newBody = if (newBody.isBlank()) spoken
                            else newBody + (if (newBody.endsWith(" ") || newBody.endsWith("\n")) "" else " ") + spoken
                        }) },
                        spans = if (richTextEnabled) bodySpans else emptyList(),
                        onSelectionChange = { a, b -> bodySelStart = a; bodySelEnd = b },
                        highlightColors = if (richTextEnabled) RichHighlightColors else emptyList(),
                        textColors = if (richTextEnabled) richTextColors() else emptyList(),
                        placeholder = com.lucent.app.i18n.S.detailsPlaceholder,
                        expandedTitle = if (editingId != null) com.lucent.app.i18n.S.editNote else com.lucent.app.i18n.S.newNote,
                        collapsedMinHeight = if (detailsIsAside) 270.dp else 360.dp,
                        tools = bodyTools
                    )
                    if (!detailsIsAside && newBody.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
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
                            onEditText = { item, text ->
                                checklistItems = checklistItems.map { if (it.id == item.id) it.copy(text = text) else it }
                            },
                            addLabel = com.lucent.app.i18n.S.addItem,
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
                        ExpandableDoodleEditor(value = doodleData, onValueChange = { doodleData = it })
                    }
                    Spacer(modifier = Modifier.height(12.dp).bringIntoViewRequester(bodyEndRequester))
                    Spacer(modifier = Modifier.height(12.dp))
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
                                    label = { Text(com.lucent.app.data.NoteTags.label(tag)) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(45.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .background(onGradient.copy(alpha = 0.05f))
                                    .border(1.dp, onGradient.copy(alpha = 0.25f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = newCustomTag,
                                    onValueChange = { newCustomTag = it },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = onGradient, fontSize = 14.sp),
                                    singleLine = true,
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(onGradient),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (newCustomTag.isEmpty()) {
                                        Text(com.lucent.app.i18n.S.newTag, color = onGradientMuted, fontSize = 14.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
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
                            GlassButton(
                                text = com.lucent.app.i18n.S.saveToDraft,
                                onClick = { saveNote(asDraft = true) },
                                modifier = Modifier.weight(1f).height(COMPOSER_ACTION_HEIGHT)
                            )
                        }
                    }
                }
            }
            bodyTools()
        }
        }

        historyForId != null && viewingNote != null -> {
            NoteHistoryScreen(
                note = viewingNote,
                onBack = { historyForId = null },
                onRestored = { historyForId = null }
            )
        }

        viewingNote != null -> {
            val detailScroll = rememberScrollState()
            val note = viewingNote
            val attachments = remember(note.attachments) { Attachments.parse(note.attachments) }
            val checklistView = remember(note.checklist) { Checklist.parse(note.checklist) }
            val linkPool = remember(notes, archivedNotes) { notes + archivedNotes }
            val outgoing = remember(note, linkPool) { NoteLinks.outgoing(note, linkPool) }
            val backlinks = remember(note, linkPool) { NoteLinks.backlinks(note, linkPool) }
            val broken = remember(note, linkPool) { NoteLinks.brokenLinks(note, linkPool) }
            val brokenLower = remember(broken) { broken.map { it.lowercase() }.toSet() }
            val versionCount by db.noteVersionDao().getForNote(note.id).collectAsState(initial = emptyList())

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
                    Text(com.lucent.app.i18n.S.screenNote, color = onGradient, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))

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
                        if (!note.pinned) {
                            PinIconButton(
                                pinned = false,
                                onToggle = { noteToTogglePin = note }
                            )
                        }
                        IconButton(onClick = { startEdit(note) }) {
                            Icon(Icons.Default.Edit, contentDescription = com.lucent.app.i18n.S.actionEdit, tint = onGradient)
                        }
                        if (markdownEnabled && richTextEnabled) {
                            FormatOverrideButton(
                                current = note.formatOverride,
                                onSelect = { key ->
                                    AppScope.io.launch {
                                        db.noteDao().getByIdOnce(note.id)?.let { row ->
                                            db.noteDao().update(row.copy(formatOverride = key))
                                        }
                                    }
                                }
                            )
                        }
                        IconButton(onClick = { noteToToggleArchive = note }) {
                            Icon(
                                if (note.archived) Icons.Filled.Unarchive else Icons.Default.Archive,
                                contentDescription = if (note.archived) com.lucent.app.i18n.S.actionRestore else com.lucent.app.i18n.S.archive,
                                tint = onGradient
                            )
                        }

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
                            shareText(context, subject = note.title.ifBlank { "Note" }, text = shareTextForNote(note), chooserTitle = com.lucent.app.i18n.S.shareNoteChooser)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = com.lucent.app.i18n.S.actionShare, tint = onGradient)
                        }
                        IconButton(onClick = { noteToDelete = note }) {
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
                        .frostedGlass(tint = NoteColor.fromKey(note.color).swatch)
                        .padding(16.dp)
                ) {
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
                    if (note.isDoodle) {
                        Spacer(modifier = Modifier.height(12.dp))
                        DoodleView(value = note.doodle)
                    }
                    if (note.body.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val openLink: (String) -> Unit = { target ->
                            val hit = NoteLinks.resolve(target, linkPool)
                            if (hit != null) viewingId = hit.id else startCreate(prefillTitle = target)
                        }
                        val detailFormat = com.lucent.app.data.ContentFormats.resolve(
                            markdownEnabled = markdownEnabled,
                            richTextEnabled = richTextEnabled,
                            override = note.formatOverride,
                            body = note.body
                        )
                        when {
                            detailFormat == com.lucent.app.data.ContentFormat.MARKDOWN -> MarkdownText(
                                text = note.body,
                                brokenLinks = brokenLower,
                                onWikiLink = openLink,
                                linksEnabled = linksEnabled
                            )
                            linksEnabled -> LinkedPlainText(
                                text = note.body,
                                brokenLinks = brokenLower,
                                onWikiLink = openLink
                            )
                            else -> Text(note.body, color = onGradient)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        val statsLabel = remember(note.body) { com.lucent.app.data.NoteStats.paragraphCharLabel(note.body) }
                        Text(statsLabel, color = onGradientMuted, fontSize = 12.sp)
                    }

                    CardAttachments(
                        attachments, onGradient, onGradientMuted,
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
                    GlassCapsuleButton(
                        text = if (note.archived) com.lucent.app.i18n.S.actionRestore else com.lucent.app.i18n.S.archive,
                        icon = if (note.archived) Icons.Filled.Unarchive else Icons.Default.Archive,
                        onClick = { noteToToggleArchive = note },
                        modifier = Modifier.weight(1f)
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

        showArchive -> {
            ArchivedNotesScreen(
                onBack = { showArchive = false },
                onOpen = { note -> openDetail(note) },
                onDeleteRequest = { note -> noteToDelete = note }
            )
        }

        showTrash -> {
            TrashNotesScreen(onBack = { showTrash = false })
        }

        showHidden && HiddenArea.visible -> {
            HiddenNotesScreen(
                onBack = { showHidden = false },
                onOpen = { openDetail(it) }
            )
        }

        showDrafts -> {
            DraftNotesScreen(
                onBack = { showDrafts = false },
                onOpen = { showDrafts = false; startEdit(it) }
            )
        }

        showNotebooks -> {
            NotebooksScreen(
                onBack = { showNotebooks = false },
                onOpenNote = { note -> openDetail(note) },
                onOpenTask = { task -> AppNavigation.openTask(task.id, from = Screen.Notes) }
            )
        }

        showSearch -> {
            SearchScreen(
                onOpenNote = { note -> openDetail(note) },
                onOpenTask = { task -> AppNavigation.openTask(task.id, from = Screen.Notes) },
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
                            com.lucent.app.i18n.S.nSelected(selectedNoteIds.size),
                            color = onGradient,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
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
                        TextButton(onClick = {
                            val allIds = sortedNotes.map { it.id }.toSet()
                            selectedNoteIds = if (selectedNoteIds.containsAll(allIds)) emptySet() else allIds
                        }) {
                            Text(if (selectedNoteIds.containsAll(sortedNotes.map { it.id }.toSet()) && sortedNotes.isNotEmpty()) com.lucent.app.i18n.S.clearAllSelection else com.lucent.app.i18n.S.selectAll)
                        }
                        IconButton(
                            onClick = { if (selectedNoteIds.isNotEmpty()) showNotebookPicker = true },
                            enabled = selectedNoteIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Book, contentDescription = com.lucent.app.i18n.S.notebookA11yAddItems, tint = onGradient)
                        }
                        IconButton(
                            onClick = { if (selectedNoteIds.isNotEmpty()) showBatchDeleteConfirm = true },
                            enabled = selectedNoteIds.isNotEmpty()
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
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = com.lucent.app.i18n.S.a11ySearchNotes) },
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
                                options = NoteSort.entries.toList(),
                                label = { it.label },
                                onSelect = { option -> scope.launch { settingsRepo.setNotesSort(option.key) } },
                                tint = onGradientMuted,
                                activeTint = onGradient
                            )
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = com.lucent.app.i18n.S.a11yMoreOptions, tint = onGradientMuted)
                                }
                                DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
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
                            NewItemButton(contentDescription = com.lucent.app.i18n.S.newNote, onClick = { startCreate() })
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
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(notesGridColumns),
                    modifier = Modifier.fillMaxSize().hazeSource(state = LocalHazeState.current),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        val renderCard: @Composable (Note, Modifier) -> Unit = { note, itemModifier ->
                            NoteCard(
                                note = note,
                                reorderVisualModifier = itemModifier.reorderVisuals(note.id, reorderState, reorderSlots),
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
                    if (searchText.isNotBlank() && inlineGlobal.hasResults) {
                        item(key = "inline_global_divider", span = { GridItemSpan(maxLineSpan) }) {
                            GlobalSearchDivider()
                        }
                        if (inlineGlobalNotes.isNotEmpty()) {
                            item(key = "inline_global_notes_header", span = { GridItemSpan(maxLineSpan) }) {
                                GlobalNotesHeader(inlineGlobalNotes.size)
                            }
                            items(
                                inlineGlobalNotes,
                                key = { "global_note_${it.id}" },
                                span = { GridItemSpan(maxLineSpan) }
                            ) { note ->
                                NoteResultRow(note = note, onOpen = { openDetail(note) })
                            }
                        }
                        if (inlineGlobalTasks.isNotEmpty()) {
                            item(key = "inline_global_tasks_header", span = { GridItemSpan(maxLineSpan) }) {
                                GlobalTasksHeader(inlineGlobalTasks.size)
                            }
                            items(
                                inlineGlobalTasks,
                                key = { "global_task_${it.id}" },
                                span = { GridItemSpan(maxLineSpan) }
                            ) { task ->
                                TaskResultRow(task = task, onOpen = { AppNavigation.openTask(task.id, from = Screen.Notes) })
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

private fun shareTextForNote(note: Note): String {
    val body = if (note.isChecklist) Checklist.toMarkdown(note.checklist) else note.body
    return if (note.title.isBlank()) body else "${note.title}\n\n$body"
}

private fun copyTextForNote(note: Note): String =
    if (note.isChecklist) Checklist.toMarkdown(note.checklist).trim() else note.body.trim()

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    reorderEnabled: Boolean = false,
    reorderModifier: Modifier = Modifier,
    reorderVisualModifier: Modifier = Modifier,
    onToggleSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val selShape = RoundedCornerShape(20.dp)
    val formattedTimestamp = rememberFormattedTimestamp(note.updatedAt)
    Column(
        modifier = reorderVisualModifier
            .fillMaxWidth()
            .height(172.dp)
            .frostedGlass(tint = NoteColor.fromKey(note.color).swatch)
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
                    PinnedMarker(
                        onUnpin = onTogglePin,
                        modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                    )
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
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (selected) com.lucent.app.i18n.S.a11ySelected else com.lucent.app.i18n.S.a11yNotSelected,
                    tint = if (selected) onGradient else onGradientMuted,
                    modifier = Modifier.padding(start = 6.dp).size(20.dp)
                )
            } else {
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

@Composable
private fun TemplateChipWithMenu(
    label: @Composable () -> Unit,
    icon: ImageVector,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val currentLong by rememberUpdatedState(onLongPress)
    Box(
        modifier = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val lp = awaitLongPressOrCancellation(down.id)
                if (lp != null) {
                    lp.consume()
                    currentLong()
                }
            }
        }
    ) {
        FilterChip(
            selected = false,
            onClick = onClick,
            label = label,
            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
}

private sealed class TemplateMenu {
    data class BuiltIn(val template: NoteTemplate) : TemplateMenu()
    data class Custom(val template: com.lucent.app.data.CustomTemplates.Template) : TemplateMenu()
}

private fun templateIcon(template: NoteTemplate) = when (template.iconName) {
    com.lucent.app.data.TemplateIcon.JOURNAL -> Icons.Default.Book
    com.lucent.app.data.TemplateIcon.MEETING -> Icons.Default.Groups
    com.lucent.app.data.TemplateIcon.IDEA -> Icons.Default.Lightbulb
    com.lucent.app.data.TemplateIcon.CHECKLIST -> Icons.AutoMirrored.Filled.FormatListBulleted
}

private const val BODY_JUMP_THRESHOLD = 400
