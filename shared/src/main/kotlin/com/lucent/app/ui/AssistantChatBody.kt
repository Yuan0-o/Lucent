package com.lucent.app.ui

import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.ChatMessage
import com.lucent.app.data.ChatSearch
import com.lucent.app.data.MemoryTier
import com.lucent.app.data.ReplyFiles
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.TokenEstimator
import com.lucent.app.network.ApiSpec
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val MAX_CHAT_UPLOAD_BYTES = 1_000_000L

data class AssistantAttachmentDraft(val mime: String, val data: String, val name: String)

class AssistantChatDraft {
    var input by mutableStateOf("")
    var error by mutableStateOf("")
    var attachments by mutableStateOf<List<AssistantAttachmentDraft>>(emptyList())
}

private suspend fun LazyListState.scrollToLatest() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    scrollToItem(lastIndex)
    var passes = 0
    while (canScrollForward && passes < 4) {
        if (scrollBy(100_000f) == 0f) break
        passes++
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AssistantChatBody(
    active: Boolean,
    draft: AssistantChatDraft,
    exportLocale: java.util.Locale,
    attachMenuContent: @Composable (close: () -> Unit) -> Unit,
    inputModifier: (submit: () -> Unit, sending: Boolean) -> Modifier,
    inputSingleLine: Boolean,
    inputMaxLines: Int,
    onSubmitted: () -> Unit,
    onSaveText: (fileName: String, text: String) -> Unit,
    onSaveFile: (fileName: String, bytes: ByteArray) -> Unit,
    onSaveZip: (suggestedName: String, entries: List<Pair<String, ByteArray>>) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    LaunchedEffect(Unit) { AssistantController.ensureMessagesLoaded(context) }
    val messages = AssistantController.messages
    val chatAttachments = remember(messages) {
        messages.flatMap { m ->
            com.lucent.app.data.ChatAttachments.all(
                m.attachmentMime, m.attachmentData, m.attachmentName, m.attachmentList)
        }
    }
    val savedUrl by repo.baseUrl.collectAsState(initial = SettingsCache.baseUrl)
    val savedSpecStr by repo.apiSpec.collectAsState(initial = SettingsCache.apiSpec)
    val savedKey by repo.apiKey.collectAsState(initial = SettingsCache.apiKey)
    val savedModel by repo.model.collectAsState(initial = SettingsCache.model)
    val assistantNameOrNull by repo.assistantName.collectAsState(initial = SettingsCache.assistantName)
    val assistantName = assistantNameOrNull.orEmpty()
    val assistantStyle by repo.assistantStyle.collectAsState(initial = SettingsCache.assistantStyle)
    val memoryTierKey by repo.memoryTier.collectAsState(initial = SettingsCache.memoryTier)
    val webSearchEnabled by repo.webSearchEnabled.collectAsState(initial = SettingsCache.webSearchEnabled)
    val typingHapticsEnabled by repo.typingHapticsEnabled.collectAsState(initial = SettingsCache.typingHapticsEnabled)
    val confirmToolsEnabled by repo.assistantConfirmToolsEnabled.collectAsState(
        initial = SettingsCache.assistantConfirmToolsEnabled
    )
    val localModelEnabled by repo.localModelEnabled.collectAsState(initial = SettingsCache.localModelEnabled)
    val localToolsEnabled by repo.localToolsEnabled.collectAsState(initial = SettingsCache.localToolsEnabled)
    val localGpuEnabled by repo.localGpuEnabled.collectAsState(initial = SettingsCache.localGpuEnabled)
    val agentModeOn by repo.agentMode.collectAsState(initial = SettingsCache.agentMode)
    val reasoningKey by repo.reasoning.collectAsState(initial = SettingsCache.reasoning)
    val memoryTierLocalKey by repo.memoryTierLocal.collectAsState(initial = SettingsCache.memoryTierLocal)
    val modelRecents by repo.modelRecents.collectAsState(initial = emptyList())
    val smallModelMode by repo.smallModelModeEnabled.collectAsState(initial = SettingsCache.smallModelModeEnabled)
    val savedProfilesJson by repo.apiProfilesJson.collectAsState(initial = SettingsCache.apiProfilesJson)
    val savedSelectedIdx by repo.apiProfileSelected.collectAsState(initial = SettingsCache.apiProfileSelected)
    val activeApiProfile = remember(savedProfilesJson, savedSelectedIdx) {
        val parsed = com.lucent.app.data.ApiProfiles.parse(savedProfilesJson)
        parsed.getOrNull(savedSelectedIdx.coerceIn(0, (parsed.size - 1).coerceAtLeast(0)))
    }

    fun persistSelectedModels(picked: List<String>) {
        val parsed = com.lucent.app.data.ApiProfiles.parse(savedProfilesJson)
        if (parsed.isEmpty()) return
        val idx = savedSelectedIdx.coerceIn(0, parsed.size - 1)
        val updated = parsed.mapIndexed { index, profile ->
            if (index == idx) profile.copy(selectedModels = picked) else profile
        }
        scope.launch { repo.saveApiProfiles(updated, idx) }
    }

    var viewingAttachment by remember { mutableStateOf<com.lucent.app.data.Attachment?>(null) }

    var attachMenuOpen by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var selectingTextIn by remember { mutableStateOf<Long?>(null) }

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
    }
    var showClearConfirm by remember { mutableStateOf(false) }
    var conversationMenuOpen by remember { mutableStateOf(false) }
    var conversationSearch by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<com.lucent.app.data.ChatConversation?>(null) }
    var renameText by remember { mutableStateOf("") }
    var convoActions by remember { mutableStateOf<com.lucent.app.data.ChatConversation?>(null) }
    var convoToDelete by remember { mutableStateOf<com.lucent.app.data.ChatConversation?>(null) }
    var downloadDialogMsg by remember { mutableStateOf<ChatMessage?>(null) }

    var deepMatches by remember { mutableStateOf<List<ChatSearch.MessageMatch>>(emptyList()) }
    var pendingJump by remember { mutableStateOf<ChatSearch.MessageMatch?>(null) }
    var highlightMessageId by remember { mutableStateOf<Long?>(null) }
    var highlightStart by remember { mutableStateOf(0) }
    var highlightLen by remember { mutableStateOf(0) }
    var highlightPulse by remember { mutableStateOf(0) }

    LaunchedEffect(active) {
        if (!active) {
            conversationMenuOpen = false
            conversationSearch = ""
            renameTarget = null
            convoActions = null
            convoToDelete = null
            showClearConfirm = false
            selectionMode = false
            selectedIds.clear()
            selectingTextIn = null
        }
    }

    LaunchedEffect(conversationSearch, conversationMenuOpen) {
        val q = conversationSearch.trim()
        if (q.isEmpty()) {
            deepMatches = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(180)
        val titles = AssistantController.conversations.associate { it.id to it.title }
        val matches = withContext(Dispatchers.IO) {
            val messageDocs = try {
                AppDatabase.getInstance(context.applicationContext).chatDao().getAllOnce().map { m ->
                    ChatSearch.MessageDoc(
                        conversationId = m.conversationId,
                        conversationTitle = titles[m.conversationId] ?: "",
                        messageId = m.id,
                        content = m.content,
                        timestamp = m.timestamp
                    )
                }
            } catch (t: Throwable) {
                emptyList()
            }
            ChatSearch.messageMatches(q, messageDocs)
        }
        deepMatches = matches
    }

    val streamingText = AssistantController.streamingTextFor(AssistantController.currentConversationId)
    val sending = AssistantController.isGenerating(AssistantController.currentConversationId)
    val thinking = AssistantController.thinkingFor(AssistantController.currentConversationId)
    val loadingModel = AssistantController.loadingModelFor(AssistantController.currentConversationId)
    val liveTrace = AssistantController.agentTraceFor(AssistantController.currentConversationId)
    val pendingConfirmation = AssistantController.pendingConfirmation
    val networkError = AssistantController.networkErrorMessage
    val controllerError =
        if (AssistantController.errorConversationId == AssistantController.currentConversationId) {
            AssistantController.errorText
        } else ""
    val controllerErrorTrace =
        if (AssistantController.errorTraceConversationId == AssistantController.currentConversationId) {
            AssistantController.errorTrace
        } else null
    val shownError = if (controllerError.isNotBlank()) controllerError else draft.error

    LaunchedEffect(active) {
        if (!active) {
            draft.error = ""
            AssistantController.clearError()
            highlightMessageId = null
            conversationMenuOpen = false
            conversationSearch = ""
        }
    }

    DisposableEffect(Unit) {
        onDispose { AssistantController.clearError() }
    }

    val variantGroups = remember(messages) {
        messages.filter { it.role == "assistant" && it.replyToId != 0L }.groupBy { it.replyToId }
    }
    val filteredMessages = remember(messages, variantGroups, AssistantController.variantSelection.toMap()) {
        val emitted = HashSet<Long>()
        messages.mapNotNull { msg ->
            val group = if (msg.role == "assistant" && msg.replyToId != 0L) variantGroups[msg.replyToId] else null
            if (group == null || group.size <= 1) return@mapNotNull msg
            if (!emitted.add(msg.replyToId)) return@mapNotNull null
            val index = (AssistantController.variantSelection[msg.replyToId] ?: (group.size - 1))
                .coerceIn(0, group.size - 1)
            group[index]
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = AssistantController.messages.size.coerceAtLeast(0)
    )

    var autoScroll by remember { mutableStateOf(true) }

    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) autoScroll = false
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && !listState.canScrollForward) autoScroll = true
        }
    }
    LaunchedEffect(messages.size, streamingText, autoScroll, thinking, pendingConfirmation != null, liveTrace?.steps?.size) {
        if (autoScroll && pendingJump == null) listState.scrollToLatest()
    }
    LaunchedEffect(AssistantController.currentConversationId) {
        selectionMode = false
        selectedIds.clear()
        if (pendingJump == null) {
            autoScroll = true
            listState.scrollToLatest()
        }
    }
    LaunchedEffect(pendingJump, AssistantController.currentConversationId, messages) {
        val jump = pendingJump ?: return@LaunchedEffect
        if (AssistantController.currentConversationId != jump.conversationId) return@LaunchedEffect
        val idx = filteredMessages.indexOfFirst { it.id == jump.messageId }
        if (idx < 0) return@LaunchedEffect
        listState.animateScrollToItem(idx.coerceAtLeast(0))
        highlightMessageId = jump.messageId
        highlightStart = jump.matchStart
        highlightLen = jump.matchLength
        highlightPulse++
        pendingJump = null
    }
    LaunchedEffect(shownError) {
        if (shownError.isNotBlank()) {
            autoScroll = true
            listState.scrollToLatest()
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(com.lucent.app.i18n.S.deleteConversationTitle) },
            text = { Text(com.lucent.app.i18n.S.deleteConversationBodyAll) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    val id = AssistantController.currentConversationId
                    if (id != null) AssistantController.deleteConversation(context.applicationContext, id)
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    convoActions?.let { convo ->
        AlertDialog(
            onDismissRequest = { convoActions = null },
            title = { Text(convDisplayTitle(convo.title).ifBlank { com.lucent.app.i18n.S.conversationFallback }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                renameTarget = convo
                                renameText = convo.title
                                convoActions = null
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(com.lucent.app.i18n.S.actionRename)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                convoToDelete = convo
                                convoActions = null
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = OverdueColor)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(com.lucent.app.i18n.S.actionDelete, color = OverdueColor)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { convoActions = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    convoToDelete?.let { convo ->
        AlertDialog(
            onDismissRequest = { convoToDelete = null },
            title = { Text(com.lucent.app.i18n.S.deleteConversationTitle) },
            text = {
                Text(
                    com.lucent.app.i18n.S.deleteConversationBodyNamed(convDisplayTitle(convo.title).ifBlank { com.lucent.app.i18n.S.conversationFallback })
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = convo.id
                    convoToDelete = null
                    AssistantController.deleteConversation(context.applicationContext, id)
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = { TextButton(onClick = { convoToDelete = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(com.lucent.app.i18n.S.renameConversationTitle) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(com.lucent.app.i18n.S.labelName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    AssistantController.renameConversation(context.applicationContext, target.id, renameText)
                    renameTarget = null
                }) { Text(com.lucent.app.i18n.S.actionSave) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    viewingAttachment?.let { att ->
        AttachmentViewerDialog(
            attachments = chatAttachments,
            initialIndex = chatAttachments.indexOfFirst { it.data == att.data }.coerceAtLeast(0),
            onDismiss = { viewingAttachment = null }
        )
    }

    downloadDialogMsg?.let { msg ->
        DownloadFilesDialog(
            message = msg,
            assistantName = assistantName,
            onDismiss = { downloadDialogMsg = null },
            onSaveText = { fileName, text ->
                onSaveText(fileName, text)
                downloadDialogMsg = null
            },
            onSaveFile = { fileName, bytes ->
                onSaveFile(fileName, bytes)
                downloadDialogMsg = null
            },
            onSaveZip = { entries ->
                onSaveZip("lucent-reply.zip", entries)
                downloadDialogMsg = null
            }
        )
    }


    networkError?.let { message ->
        AlertDialog(
            onDismissRequest = { AssistantController.clearNetworkError() },
            title = { Text(com.lucent.app.i18n.S.connectionProblem) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { AssistantController.retryLast() }) { Text(com.lucent.app.i18n.S.actionRetry) }
            },
            dismissButton = {
                TextButton(onClick = { AssistantController.clearNetworkError() }) { Text(com.lucent.app.i18n.S.actionOk) }
            }
        )
    }


    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text(com.lucent.app.i18n.S.batchDeleteTitle) },
            text = { Text(com.lucent.app.i18n.S.batchDeleteBody(selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds.toSet()
                    showBatchDeleteConfirm = false
                    exitSelection()
                    AssistantController.deleteMessages(context.applicationContext, ids)
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text(com.lucent.app.i18n.S.actionCancel) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp).padding(bottom = LocalBottomBarInset.current)) {
        if (selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { exitSelection() }) {
                    Icon(Icons.Default.Close, contentDescription = com.lucent.app.i18n.S.actionCancel, tint = onGradient)
                }
                Text(
                    com.lucent.app.i18n.S.selectedCount(selectedIds.size),
                    color = onGradient,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    if (selectedIds.size == messages.size) selectedIds.clear()
                    else { selectedIds.clear(); messages.forEach { selectedIds.add(it.id) } }
                }) {
                    Icon(Icons.Default.SelectAll, contentDescription = com.lucent.app.i18n.S.selectAll, tint = onGradient)
                }
                if (selectedIds.size == 1) {
                    val only = messages.firstOrNull { it.id == selectedIds.first() }
                    if (only != null) {
                        IconButton(onClick = {
                            copyToClipboard(context, only.content)
                            exitSelection()
                        }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = com.lucent.app.i18n.S.msgCopyWhole,
                                tint = onGradient
                            )
                        }
                        IconButton(onClick = {
                            selectingTextIn = only.id
                            exitSelection()
                        }) {
                            Icon(
                                Icons.Default.TextFields,
                                contentDescription = com.lucent.app.i18n.S.msgSelectText,
                                tint = onGradient
                            )
                        }
                    }
                }
                IconButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        val chosen = messages.filter { it.id in selectedIds }
                        onSaveZip(
                            "lucent-selection.zip",
                            buildChatExportEntries(chosen, assistantName.ifBlank { "Lucent" }, exportLocale)
                        )
                    }
                ) {
                    Icon(Icons.Default.Archive, contentDescription = com.lucent.app.i18n.S.a11yExportChat, tint = onGradient)
                }
                IconButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = { showBatchDeleteConfirm = true }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = com.lucent.app.i18n.S.actionDelete, tint = OverdueColor)
                }
            }
        } else
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { conversationMenuOpen = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val current = AssistantController.conversations.firstOrNull { it.id == AssistantController.currentConversationId }
                    Icon(Icons.Default.Forum, contentDescription = null, tint = onGradient, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        current?.title?.let { convDisplayTitle(it) }?.ifBlank { com.lucent.app.i18n.S.conversationFallback } ?: com.lucent.app.i18n.S.newConversation,
                        color = onGradient,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = com.lucent.app.i18n.S.a11ySwitchConversation,
                        tint = onGradient,
                        modifier = Modifier.size(24.dp)
                    )
                }
                DropdownMenu(expanded = conversationMenuOpen, onDismissRequest = { conversationMenuOpen = false }) {
                    val convos = AssistantController.conversations
                    Column(modifier = Modifier.width(210.dp)) {
                        OutlinedTextField(
                            value = conversationSearch,
                            onValueChange = { conversationSearch = it },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                        HorizontalDivider()

                        val searching = conversationSearch.isNotBlank()
                        if (searching) {
                            if (deepMatches.isEmpty()) {
                                DropdownMenuItem(text = { Text(com.lucent.app.i18n.S.noMatches) }, onClick = { }, enabled = false)
                            } else {
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = 280.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    deepMatches.forEachIndexed { i, match ->
                                        key(match.messageId, match.matchStart, i) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        conversationMenuOpen = false
                                                        conversationSearch = ""
                                                        autoScroll = false
                                                        pendingJump = match
                                                        if (AssistantController.currentConversationId != match.conversationId) {
                                                            AssistantController.switchConversation(
                                                                context.applicationContext,
                                                                match.conversationId
                                                            )
                                                        }
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        convDisplayTitle(match.conversationTitle).ifBlank { com.lucent.app.i18n.S.conversationFallback },
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontSize = 11.sp,
                                                        color = onGradientMuted
                                                    )
                                                    val previewed = remember(match.snippet, match.hitInSnippetStart, match.hitInSnippetLength) {
                                                        buildAnnotatedString {
                                                            val s = match.hitInSnippetStart.coerceIn(0, match.snippet.length)
                                                            val e = (match.hitInSnippetStart + match.hitInSnippetLength).coerceIn(s, match.snippet.length)
                                                            append(match.snippet.substring(0, s))
                                                            withStyle(SpanStyle(color = onGradient, fontWeight = FontWeight.Bold)) {
                                                                append(match.snippet.substring(s, e))
                                                            }
                                                            append(match.snippet.substring(e))
                                                        }
                                                    }
                                                    Text(
                                                        previewed,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontSize = 13.sp,
                                                        color = onGradientMuted
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (convos.isEmpty()) {
                            DropdownMenuItem(text = { Text(com.lucent.app.i18n.S.noSavedConversations) }, onClick = { conversationMenuOpen = false }, enabled = false)
                        } else {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                convos.forEach { convo ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    conversationMenuOpen = false
                                                    AssistantController.switchConversation(context.applicationContext, convo.id)
                                                }
                                                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
                                        ) {
                                            Text(
                                                convDisplayTitle(convo.title).ifBlank { com.lucent.app.i18n.S.conversationFallback },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (convo.id == AssistantController.currentConversationId) onGradient else onGradientMuted
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                conversationMenuOpen = false
                                                convoActions = convo
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = com.lucent.app.i18n.S.a11yConversationOptions(convo.title.ifBlank { com.lucent.app.i18n.S.thisConversation }),
                                                tint = onGradientMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val toolkit = com.lucent.app.harness.HarnessRuntime.config()
                TodoChip(
                    conversationId = AssistantController.currentConversationId,
                    tint = onGradient,
                    mutedTint = onGradientMuted
                )
                GoalChip(
                    conversationId = AssistantController.currentConversationId,
                    tint = onGradient,
                    mutedTint = onGradientMuted
                )
                if (toolkit.enabled) {
                    JobsChip(tint = onGradient, mutedTint = onGradientMuted)
                }
                if (toolkit.enabled && toolkit.subAgents) {
                    SubAgentChip(tint = onGradient, mutedTint = onGradientMuted)
                }
                if (toolkit.enabled) {
                    RunningCounterChip(tint = onGradient, mutedTint = onGradientMuted)
                }
                GlassRoundButton(
                    icon = Icons.Default.Archive,
                    contentDescription = com.lucent.app.i18n.S.a11yExportChat,
                    onClick = {
                        onSaveZip(
                            "lucent-chat.zip",
                            buildChatExportEntries(messages, assistantName.ifBlank { "Lucent" }, exportLocale)
                        )
                    },
                    tint = onGradient,
                    enabled = messages.isNotEmpty(),
                    diameter = 40.dp
                )
                GlassRoundButton(
                    icon = Icons.Default.Delete,
                    contentDescription = com.lucent.app.i18n.S.deleteConversationTitle.removeSuffix("?").removeSuffix("？"),
                    onClick = { showClearConfirm = true },
                    tint = onGradient,
                    enabled = AssistantController.currentConversationId != null && messages.isNotEmpty(),
                    diameter = 40.dp
                )
                GlassRoundButton(
                    icon = Icons.Default.Add,
                    contentDescription = com.lucent.app.i18n.S.newConversation,
                    onClick = {
                        conversationMenuOpen = false
                        conversationSearch = ""
                        pendingJump = null
                        AssistantController.startNewConversation(context.applicationContext)
                    },
                    tint = onGradient,
                    diameter = 40.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val toolkit = com.lucent.app.harness.HarnessRuntime.config()
        SubAgentStrip(
            visible = toolkit.enabled && toolkit.subAgents,
            tint = onGradient,
            mutedTint = onGradientMuted,
            modifier = Modifier.padding(top = 8.dp)
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().hazeSource(state = LocalHazeState.current),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (messages.isEmpty() && assistantName.isNotBlank()) {
                    item(key = "greeting") {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.align(Alignment.CenterStart).frostedGlass().padding(12.dp)) {
                                Text(
                                    com.lucent.app.i18n.S.assistantGreeting(assistantName)
                                        .withLineStartPunctuationAllowed(),
                                    color = onGradient
                                )
                            }
                        }
                    }
                }
                items(filteredMessages, key = { it.id }) { msg ->
                    val isUser = msg.role == "user"
                    val isHighlighted = msg.id == highlightMessageId
                    val bounce = remember(msg.id) { Animatable(1f) }
                    LaunchedEffect(msg.id, isHighlighted, highlightPulse) {
                        if (isHighlighted) {
                            repeat(2) {
                                bounce.animateTo(1.06f, tween(110))
                                bounce.animateTo(1f, tween(140))
                            }
                        }
                    }
                    val isSelected = msg.id in selectedIds
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { on ->
                                    if (on) selectedIds.add(msg.id) else selectedIds.remove(msg.id)
                                }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier
                                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                                .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
                                .frostedGlass()
                                .then(
                                    if (isSelected) Modifier.background(Color.White.copy(alpha = 0.10f))
                                    else Modifier
                                )
                                .pointerInput(msg.id, selectionMode) {
                                    detectTapGestures(
                                        onLongPress = {
                                            if (!selectionMode) {
                                                Haptics.tick(context)
                                                selectionMode = true
                                                selectedIds.clear()
                                                selectedIds.add(msg.id)
                                                selectingTextIn = null
                                            }
                                        },
                                        onTap = {
                                            if (selectionMode) {
                                                if (isSelected) selectedIds.remove(msg.id) else selectedIds.add(msg.id)
                                            }
                                        }
                                    )
                                }
                                .padding(12.dp)
                        ) {
                            if (!isUser && assistantName.isNotBlank()) {
                                Text(
                                    assistantName,
                                    color = onGradientMuted,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                            val attachmentsAll = remember(msg.id, msg.attachmentData, msg.attachmentList) {
                                com.lucent.app.data.ChatAttachments.all(
                                    msg.attachmentMime, msg.attachmentData, msg.attachmentName, msg.attachmentList)
                            }
                            attachmentsAll.forEach { att ->
                                val fileName = att.name.ifBlank { "file" }
                                Row(
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.10f))
                                        .clickable {
                                            viewingAttachment = chatAttachments.firstOrNull { it.data == att.data }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = onGradient)
                                    Text(
                                        fileName,
                                        color = onGradient,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false).padding(start = 6.dp)
                                    )
                                }
                            }

                            if (!isUser) {
                                val savedTrace = remember(msg.id, msg.agentTrace) {
                                    AgentTraceCodec.decode(msg.agentTrace)
                                }
                                if (savedTrace != null) {
                                    AgentTracePanel(
                                        trace = savedTrace,
                                        tint = onGradient,
                                        mutedTint = onGradientMuted
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                val reasoning = msg.reasoningText.orEmpty()
                                if (reasoning.isNotBlank()) {
                                    AssistantReasoningBlock(
                                        reasoning = reasoning,
                                        tint = onGradient,
                                        mutedTint = onGradientMuted
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                            val plainContent = isUser ||
                                selectingTextIn == msg.id ||
                                (isHighlighted && highlightLen > 0)
                            if (!plainContent) {
                                MarkdownText(msg.content.withLineStartPunctuationAllowed())
                            } else {
                                val contentText = if (isHighlighted && highlightLen > 0) {
                                    buildAnnotatedString {
                                        val s = highlightStart.coerceIn(0, msg.content.length)
                                        val e = (highlightStart + highlightLen).coerceIn(s, msg.content.length)
                                        append(msg.content.substring(0, s).withLineStartPunctuationAllowed())
                                        withStyle(SpanStyle(background = Color(0x66FFC107), fontWeight = FontWeight.Bold)) {
                                            append(msg.content.substring(s, e).withLineStartPunctuationAllowed())
                                        }
                                        append(msg.content.substring(e).withLineStartPunctuationAllowed())
                                    }
                                } else {
                                    buildAnnotatedString { append(msg.content.withLineStartPunctuationAllowed()) }
                                }
                                if (selectingTextIn == msg.id) {
                                    SelectionContainer {
                                        Text(contentText, color = onGradient)
                                    }
                                } else {
                                    Text(contentText, color = onGradient)
                                }
                            }
                            if (!isUser) {
                                val siblings = variantGroups[msg.replyToId]
                                if (msg.replyToId != 0L && siblings != null && siblings.size > 1) {
                                    val current = siblings.indexOfFirst { it.id == msg.id }.coerceAtLeast(0)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { AssistantController.selectVariant(msg.replyToId, current - 1) },
                                            enabled = current > 0,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ChevronLeft, contentDescription = com.lucent.app.i18n.S.variantPrevious, tint = onGradientMuted, modifier = Modifier.size(18.dp))
                                        }
                                        Text(
                                            "${current + 1}/${siblings.size}",
                                            color = onGradientMuted,
                                            fontSize = 11.sp
                                        )
                                        IconButton(
                                            onClick = { AssistantController.selectVariant(msg.replyToId, current + 1) },
                                            enabled = current < siblings.size - 1,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ChevronRight, contentDescription = com.lucent.app.i18n.S.variantNext, tint = onGradientMuted, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { downloadDialogMsg = msg },
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = com.lucent.app.i18n.S.a11yDownloadReplyFiles, tint = onGradientMuted)
                                }
                                if (msg.tokens > 0) {
                                    Text(
                                        TokenEstimator.label(msg.tokens),
                                        color = onGradientMuted,
                                        fontSize = 11.sp,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                        }
                    }
                    if (isUser) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(
                                onClick = {
                                    val useLocal = localModelEnabled
                                    AssistantController.resend(
                                        appContext = context.applicationContext,
                                        message = msg,
                                        url = savedUrl,
                                        spec = when (savedSpecStr) {
                                            "anthropic" -> ApiSpec.ANTHROPIC
                                            "google" -> ApiSpec.GOOGLE
                                            else -> ApiSpec.OPENAI
                                        },
                                        key = savedKey, model = savedModel,
                                        name = assistantName.ifBlank { "Lucent" }, style = assistantStyle,
                                        memoryTier = MemoryTier.fromKey(if (useLocal) memoryTierLocalKey else memoryTierKey),
                                        webSearchEnabled = webSearchEnabled,
                                        typingHapticsEnabled = typingHapticsEnabled,
                                        useLocalModel = useLocal,
                                        useLocalTools = localToolsEnabled,
                                        useLocalGpu = localGpuEnabled,
                                        confirmTools = confirmToolsEnabled,
                                        smallModelMode = smallModelMode,
                                        agentMode = agentModeOn,
                                        localWebSearch = webSearchEnabled,
                                        reasoning = reasoningKey
                                    )
                                },
                                enabled = !sending,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = com.lucent.app.i18n.S.resendMessage, tint = onGradientMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                if (streamingText != null) {
                    item(key = "streaming") {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.align(Alignment.CenterStart).frostedGlass().padding(12.dp)) {
                                if (assistantName.isNotBlank()) {
                                    Text(
                                        assistantName,
                                        color = onGradientMuted,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    )
                                }
                                if (liveTrace != null) {
                                    AgentTracePanel(
                                        trace = liveTrace,
                                        tint = onGradient,
                                        mutedTint = onGradientMuted,
                                        initiallyExpanded = true
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                Text(streamingText.withLineStartPunctuationAllowed(), color = onGradient)
                            }
                        }
                    }
                }
                if (thinking && streamingText == null && pendingConfirmation == null) {
                    item(key = "thinking") {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.align(Alignment.CenterStart).frostedGlass().padding(12.dp)) {
                                if (liveTrace != null) {
                                    AgentTracePanel(
                                        trace = liveTrace,
                                        tint = onGradient,
                                        mutedTint = onGradientMuted,
                                        initiallyExpanded = true
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                ThinkingBubble(
                                    name = assistantName,
                                    tint = onGradient,
                                    mutedTint = onGradientMuted,
                                    loadingModel = loadingModel
                                )
                            }
                        }
                    }
                }
                if (shownError.isNotBlank()) {
                    item(key = "error") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (controllerErrorTrace != null) {
                                AgentTracePanel(
                                    trace = controllerErrorTrace,
                                    tint = onGradient,
                                    mutedTint = onGradientMuted,
                                    initiallyExpanded = true
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(
                                shownError,
                                color = if (onGradient == Color.White) Color(0xFFFFC1C1) else Color(0xFFC62828),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).longPressCopy(context, shownError)
                            )
                        }
                    }
                }
            }

            JumpToLatestButton(
                visible = !atBottom,
                tint = onGradient,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                onClick = {
                    autoScroll = true
                    scope.launch { listState.scrollToLatest() }
                }
            )
        }

        if (localModelEnabled && !localToolsEnabled) {
            Text(
                com.lucent.app.i18n.S.localToolsOffHint,
                color = onGradientMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
        if (draft.attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                draft.attachments.forEachIndexed { index, att ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = onGradientMuted, modifier = Modifier.size(14.dp))
                        Text(
                            att.name,
                            color = onGradientMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        IconButton(
                            onClick = { draft.attachments = draft.attachments.filterIndexed { i, _ -> i != index } },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = com.lucent.app.i18n.S.a11yRemoveAttachment,
                                tint = onGradientMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
        val submitMessage: () -> Unit = submit@{
            val text = draft.input.trim()
            val attachments = draft.attachments.map {
                com.lucent.app.data.Attachment(it.mime, it.data, it.name)
            }
            if (text.isBlank() && attachments.isEmpty()) return@submit
            val useLocal = localModelEnabled
            if (!useLocal && (savedUrl.isBlank() || savedModel.isBlank())) {
                draft.error = com.lucent.app.i18n.S.setupApiFirst
                return@submit
            }
            draft.input = ""
            draft.attachments = emptyList()
            draft.error = ""
            autoScroll = true
            val spec = when (savedSpecStr) {
                "anthropic" -> ApiSpec.ANTHROPIC
                "google" -> ApiSpec.GOOGLE
                else -> ApiSpec.OPENAI
            }
            AssistantController.send(
                appContext = context.applicationContext,
                text = text,
                attachmentMime = attachments.firstOrNull()?.mime,
                attachmentData = attachments.firstOrNull()?.data,
                attachmentName = attachments.firstOrNull()?.name,
                attachments = attachments,
                url = savedUrl, spec = spec, key = savedKey, model = savedModel,
                name = assistantName.ifBlank { "Lucent" }, style = assistantStyle,
                memoryTier = MemoryTier.fromKey(if (useLocal) memoryTierLocalKey else memoryTierKey),
                webSearchEnabled = webSearchEnabled,
                typingHapticsEnabled = typingHapticsEnabled,
                useLocalModel = useLocal,
                useLocalTools = localToolsEnabled,
                useLocalGpu = localGpuEnabled,
                confirmTools = confirmToolsEnabled,
                smallModelMode = smallModelMode,
                agentMode = agentModeOn,
                localWebSearch = webSearchEnabled,
                reasoning = reasoningKey
            )
            onSubmitted()
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                GlassRoundButton(
                    icon = Icons.Default.AttachFile,
                    contentDescription = com.lucent.app.i18n.S.a11yAttachFile,
                    onClick = { attachMenuOpen = true },
                    tint = onGradient
                )
                DropdownMenu(expanded = attachMenuOpen, onDismissRequest = { attachMenuOpen = false }) {
                    attachMenuContent { attachMenuOpen = false }
                }
            }
            OutlinedTextField(
                value = draft.input,
                onValueChange = { draft.input = it },
                trailingIcon = {
                    var inputExpanded by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(false)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QuickModelSwitcher(
                            currentModel = savedModel,
                            recents = modelRecents,
                            baseUrl = savedUrl,
                            spec = when (savedSpecStr) {
                                "anthropic" -> ApiSpec.ANTHROPIC
                                "google" -> ApiSpec.GOOGLE
                                else -> ApiSpec.OPENAI
                            },
                            apiKey = savedKey,
                            selectedModels = activeApiProfile?.selectedModels ?: emptyList(),
                            hasProfile = activeApiProfile != null,
                            localModelEnabled = localModelEnabled,
                            tint = onGradient,
                            mutedTint = onGradientMuted,
                            onPickCloudModel = { model -> scope.launch { repo.setActiveModel(model) } },
                            onSelectedModelsChange = { picked -> persistSelectedModels(picked) },
                            buttonSize = 34.dp,
                            plain = true,
                            reasoningProviderId = com.lucent.app.data.ApiProviders.forRequest(savedSpecStr, savedUrl),
                            reasoningCurrent = reasoningKey,
                            onPickReasoning = { picked ->
                                SettingsCache.reasoning = picked.key
                                scope.launch { repo.setReasoning(picked.key) }
                            }
                        )
                        DictationButton(
                            onText = { spoken ->
                                draft.input = if (draft.input.isBlank()) spoken
                                else draft.input + (if (draft.input.endsWith(" ")) "" else " ") + spoken
                            },
                            modifier = Modifier.size(34.dp)
                        )
                        IconButton(
                            onClick = { inputExpanded = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.OpenInFull,
                                contentDescription = com.lucent.app.i18n.S.expandTextBox,
                                tint = onGradientMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    if (inputExpanded) {
                        LucentExpandedInput(
                            value = draft.input,
                            onValueChange = { draft.input = it },
                            placeholder = com.lucent.app.i18n.S.messagePlaceholder,
                            title = com.lucent.app.i18n.S.messagePlaceholder,
                            onCollapse = { inputExpanded = false }
                        )
                    }
                },
                placeholder = { Text(com.lucent.app.i18n.S.messagePlaceholder, fontSize = 13.sp) },
                singleLine = inputSingleLine,
                maxLines = inputMaxLines,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                modifier = Modifier.weight(1f).then(inputModifier(submitMessage, sending))
            )
            if (sending) {
                GlassRoundButton(
                    icon = Icons.Default.Stop,
                    contentDescription = com.lucent.app.i18n.S.a11yStopGenerating,
                    onClick = { AssistantController.stopGeneration() },
                    tint = onGradient,
                    filled = true
                )
            } else {
                GlassRoundButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = com.lucent.app.i18n.S.a11ySend,
                    onClick = submitMessage,
                    tint = onGradient,
                    filled = true
                )
            }
        }
    }
}

@Composable
private fun RunningCounterChip(tint: Color, mutedTint: Color) {
    var jobs by remember { mutableStateOf(com.lucent.app.harness.HarnessJobs.runningCount()) }
    var agents by remember { mutableStateOf(com.lucent.app.harness.SubAgents.running()) }

    LaunchedEffect(Unit) {
        while (true) {
            jobs = com.lucent.app.harness.HarnessJobs.runningCount()
            agents = com.lucent.app.harness.SubAgents.running()
            kotlinx.coroutines.delay(1500)
        }
    }

    if (jobs == 0 && agents == 0) return
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            com.lucent.app.i18n.S.runningCounter(jobs, agents),
            color = tint,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun JumpToLatestButton(
    visible: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = com.lucent.app.i18n.S.a11yJumpToLatest, tint = tint)
        }
    }
}

@Composable
private fun ThinkingBubble(name: String, tint: Color, mutedTint: Color, loadingModel: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "thinking")
    Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (loadingModel) com.lucent.app.i18n.S.lmLoadingIndicator
            else com.lucent.app.i18n.S.thinkingIndicator(name),
            color = mutedTint,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        for (i in 0 until 3) {
            val dotAlpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = i * 200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Text(
                "•",
                color = tint,
                fontSize = 18.sp,
                modifier = Modifier.alpha(dotAlpha).padding(horizontal = 1.dp)
            )
        }
    }
    if (loadingModel) {
        Text(
            com.lucent.app.i18n.S.lmFirstLoadHint,
            color = mutedTint,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    }
}

@Composable
private fun DownloadFilesDialog(
    message: ChatMessage,
    assistantName: String,
    onDismiss: () -> Unit,
    onSaveText: (fileName: String, text: String) -> Unit,
    onSaveFile: (fileName: String, bytes: ByteArray) -> Unit,
    onSaveZip: (entries: List<Pair<String, ByteArray>>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasText = message.content.isNotBlank()
    val messageAtts = remember(message.id, message.attachmentData, message.attachmentList) {
        com.lucent.app.data.ChatAttachments.all(
            message.attachmentMime, message.attachmentData, message.attachmentName, message.attachmentList)
    }
    val hasAttachment = messageAtts.isNotEmpty()
    val selAtts = remember(message.id) {
        mutableStateMapOf<Int, Boolean>().apply { messageAtts.indices.forEach { put(it, true) } }
    }
    val textFileName = "lucent-reply.txt"

    val replyFiles = remember(message.id, message.content) { ReplyFiles.extract(message.content) }

    var selText by remember(message.id) { mutableStateOf(hasText) }
    val selRemote = remember(message.id) {
        mutableStateMapOf<String, Boolean>().apply { replyFiles.forEach { put(it.url, true) } }
    }
    var working by remember(message.id) { mutableStateOf(false) }
    var failure by remember(message.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.lucent.app.i18n.S.downloadFilesTitle) },
        text = {
            Column {
                Text(com.lucent.app.i18n.S.downloadChoose, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (hasText) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selText = !selText }
                    ) {
                        Checkbox(checked = selText, onCheckedChange = { selText = it })
                        Text(com.lucent.app.i18n.S.downloadReplyTxt)
                    }
                }
                if (hasAttachment) {
                    messageAtts.forEachIndexed { idx, att ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selAtts[idx] = !(selAtts[idx] ?: false) }
                        ) {
                            Checkbox(
                                checked = selAtts[idx] ?: false,
                                onCheckedChange = { selAtts[idx] = it }
                            )
                            Text(att.name.ifBlank { com.lucent.app.i18n.S.a11yAttachment }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                replyFiles.forEach { file ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            selRemote[file.url] = !(selRemote[file.url] ?: false)
                        }
                    ) {
                        Checkbox(
                            checked = selRemote[file.url] ?: false,
                            onCheckedChange = { selRemote[file.url] = it }
                        )
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!hasText && !hasAttachment && replyFiles.isEmpty()) {
                    Text(com.lucent.app.i18n.S.downloadNone, fontSize = 13.sp)
                }
                if (working) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(com.lucent.app.i18n.S.downloadFetching, fontSize = 12.sp)
                }
                if (failure.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(failure, fontSize = 12.sp, color = OverdueColor)
                }
                if (replyFiles.isNotEmpty() || hasAttachment) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(com.lucent.app.i18n.S.downloadSaveInto, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            enabled = !working,
                            onClick = {
                                working = true; failure = ""
                                scope.launch {
                                    val ok = saveReplyIntoItem(context, message, replyFiles, selRemote,
                                        localAtts = messageAtts.filterIndexed { i, _ -> selAtts[i] == true }, asTask = false)
                                    working = false
                                    if (ok) onDismiss() else failure = com.lucent.app.i18n.S.downloadFetchFailed
                                }
                            }
                        ) { Text(com.lucent.app.i18n.S.saveAsNote) }
                        TextButton(
                            enabled = !working,
                            onClick = {
                                working = true; failure = ""
                                scope.launch {
                                    val ok = saveReplyIntoItem(context, message, replyFiles, selRemote,
                                        localAtts = messageAtts.filterIndexed { i, _ -> selAtts[i] == true }, asTask = true)
                                    working = false
                                    if (ok) onDismiss() else failure = com.lucent.app.i18n.S.downloadFetchFailed
                                }
                            }
                        ) { Text(com.lucent.app.i18n.S.saveAsTask) }
                    }
                }
            }
        },
        confirmButton = {
            val anyRemote = replyFiles.any { selRemote[it.url] == true }
            TextButton(
                enabled = (selText || selAtts.values.any { it } || anyRemote) && !working,
                onClick = {
                    working = true; failure = ""
                    scope.launch {
                        val entries = mutableListOf<Pair<String, ByteArray>>()
                        if (selText && hasText) entries.add(textFileName to message.content.toByteArray())
                        messageAtts.forEachIndexed { idx, att ->
                            if (selAtts[idx] != true) return@forEachIndexed
                            val bytes = try { Base64.decode(att.data, Base64.DEFAULT) } catch (t: Throwable) { ByteArray(0) }
                            entries.add((att.name.ifBlank { "attachment" }) to bytes)
                        }
                        var missed = 0
                        for (file in replyFiles) {
                            if (selRemote[file.url] != true) continue
                            val bytes = withContext(Dispatchers.IO) { ReplyFiles.fetch(file) }
                            if (bytes == null) missed++ else entries.add(file.name to bytes)
                        }
                        working = false
                        if (entries.isEmpty()) {
                            failure = com.lucent.app.i18n.S.downloadFetchFailed
                            return@launch
                        }
                        if (missed > 0) failure = com.lucent.app.i18n.S.downloadFetchPartial(missed)
                        if (entries.size == 1) {
                            val (n, b) = entries.first()
                            if (n == textFileName) onSaveText(n, message.content) else onSaveFile(n, b)
                        } else {
                            onSaveZip(entries)
                        }
                    }
                }
            ) { Text(com.lucent.app.i18n.S.actionDownload) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(com.lucent.app.i18n.S.actionCancel) } }
    )
}

private suspend fun saveReplyIntoItem(
    context: android.content.Context,
    message: ChatMessage,
    replyFiles: List<ReplyFiles.ReplyFile>,
    selected: Map<String, Boolean>,
    localAtts: List<com.lucent.app.data.Attachment> = emptyList(),
    asTask: Boolean
): Boolean = withContext(Dispatchers.IO) {
    val db = AppDatabase.getInstance(context.applicationContext)
    val title = message.content.lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(60)
        ?.ifBlank { null }
        ?: com.lucent.app.i18n.S.conversationFallback
    val wanted = replyFiles.filter { selected[it.url] == true }
    if (wanted.isEmpty() && localAtts.isEmpty()) {
        return@withContext ReplyFiles.saveToNewItem(
            context.applicationContext, db, asTask, title, message.content,
            fileName = "", mime = "", bytes = ByteArray(0)
        )
    }
    var saved = 0
    for (att in localAtts) {
        val bytes = try { Base64.decode(att.data, Base64.DEFAULT) } catch (t: Throwable) { ByteArray(0) }
        val ok = ReplyFiles.saveToNewItem(
            context.applicationContext, db, asTask, title, message.content,
            att.name, att.mime, bytes
        )
        if (ok) saved++
    }
    for (file in wanted) {
        val bytes = ReplyFiles.fetch(file) ?: continue
        val mime = if (file.isImage) "image/*" else "application/octet-stream"
        val ok = ReplyFiles.saveToNewItem(
            context.applicationContext, db, asTask, title, message.content,
            file.name, mime, bytes
        )
        if (ok) saved++
    }
    saved > 0
}

private fun buildChatExportEntries(
    messages: List<ChatMessage>,
    assistantName: String,
    locale: java.util.Locale
): List<Pair<String, ByteArray>> {
    val entries = mutableListOf<Pair<String, ByteArray>>()
    val sb = StringBuilder()
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", locale)
    var attIndex = 1
    messages.forEach { m ->
        val who = if (m.role == "assistant") assistantName else com.lucent.app.i18n.S.exportYou
        val time = fmt.format(java.util.Date(m.timestamp))
        sb.append("[").append(time).append("] ").append(who).append(":\n")
        sb.append(m.content).append("\n")
        com.lucent.app.data.ChatAttachments.all(m.attachmentMime, m.attachmentData, m.attachmentName, m.attachmentList)
            .forEach { att ->
                val entryName = "%02d_%s".format(attIndex, sanitizeExportName(att.name.ifBlank { "attachment" }))
                val bytes = try { Base64.decode(att.data, Base64.DEFAULT) } catch (t: Throwable) { ByteArray(0) }
                entries.add(entryName to bytes)
                sb.append("[attachment: ").append(entryName).append("]\n")
                attIndex++
            }
        sb.append("\n")
    }
    entries.add(0, "chat.txt" to sb.toString().toByteArray())
    return entries
}

private fun sanitizeExportName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "file" }

private fun convDisplayTitle(title: String): String =
    if (title == "New conversation") com.lucent.app.i18n.S.newConversation else title

private const val ZWSP = '\u200B'

private const val CLINGY_CJK_PUNCT = "，。、；：！？）】》〉」』〕〗〙〛％…‥"

private fun isCjkContext(ch: Char): Boolean {
    val c = ch.code
    return (c in 0x4E00..0x9FFF) ||
        (c in 0x3400..0x4DBF) ||
        (c in 0x3040..0x30FF) ||
        (c in 0x3000..0x303F) ||
        (c in 0xFF00..0xFFEF) ||
        (c in 0xAC00..0xD7AF) ||
        (c in 0xF900..0xFAFF)
}

private fun String.withLineStartPunctuationAllowed(): String {
    if (isEmpty()) return this
    val sb = StringBuilder(length + 8)
    var prev = '\u0000'
    for (ch in this) {
        if (ch in CLINGY_CJK_PUNCT && isCjkContext(prev)) sb.append(ZWSP)
        sb.append(ch)
        prev = ch
    }
    return sb.toString()
}
