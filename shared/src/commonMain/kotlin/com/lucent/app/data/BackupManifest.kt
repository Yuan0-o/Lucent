package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.first
import com.lucent.app.harness.HarnessConfig
import kotlinx.serialization.json.*

object BackupManifestBuilder {

    suspend fun build(
        context: PlatformContext,
        notes: List<Note>,
        tasks: List<Task>,
        noteVersions: List<NoteVersion>,
        taskVersions: List<TaskVersion>,
        chats: List<ChatMessage>,
        conversations: List<ChatConversation>,
        settings: SettingsRepository,
        notebooks: List<Notebook> = emptyList(),
        notebookItems: List<NotebookItem> = emptyList(),
        inlineAttachments: Boolean,
        modules: Set<BackupManager.BackupModule> = BackupManager.DEFAULT_MODULES,
        apiProfileNames: Set<String>? = null
    ): JsonObject {
        val notesArray = buildJsonArray {
            notes.forEach {
                val attachments = if (inlineAttachments) inlineAttachmentBytes(context, it.attachments) else it.attachments
                addJsonObject {
                    put("title", it.title)
                    put("body", it.body)
                    put("updatedAt", it.updatedAt)
                    put("tags", it.tags)
                    put("attachments", attachments)
                    put("archived", it.archived)
                    put("archivedAt", it.archivedAt)
                    put("pinned", it.pinned)
                    put("color", it.color)
                    put("isChecklist", it.isChecklist)
                    put("checklist", it.checklist)
                    put("trashedAt", it.trashedAt)
                    put("manualOrder", it.manualOrder)
                    put("isDraft", it.isDraft)
                    put("draftSavedAt", it.draftSavedAt)
                    put("hidden", it.hidden)
                    put("isDoodle", it.isDoodle)
                    put("bodySpans", it.bodySpans)
                    put("doodle", it.doodle)
                    put("formatOverride", it.formatOverride)
                }
            }
        }

        val tasksArray = buildJsonArray {
            tasks.forEach {
                val attachments = if (inlineAttachments) inlineAttachmentBytes(context, it.attachments) else it.attachments
                addJsonObject {
                    put("title", it.title)
                    put("isDone", it.isDone)
                    put("createdAt", it.createdAt)
                    put("attachments", attachments)
                    put("dueAt", it.dueAt)
                    put("notes", it.notes)
                    put("completedAt", it.completedAt)
                    put("priority", it.priority)
                    put("pinned", it.pinned)
                    put("subtasks", it.subtasks)
                    put("repeatRule", it.repeatRule)
                    put("reminderEnabled", it.reminderEnabled)
                    put("trashedAt", it.trashedAt)
                    put("manualOrder", it.manualOrder)
                    put("isDraft", it.isDraft)
                    put("draftSavedAt", it.draftSavedAt)
                    put("hidden", it.hidden)
                    put("notesSpans", it.notesSpans)
                    put("formatOverride", it.formatOverride)
                }
            }
        }

        val versionsArray = buildJsonArray {
            val noteById = notes.associateBy { it.id }
            noteVersions.forEach { version ->
                val owner = noteById[version.noteId] ?: return@forEach
                addJsonObject {
                    put("noteTitle", owner.title)
                    put("noteUpdatedAt", owner.updatedAt)
                    put("title", version.title)
                    put("body", version.body)
                    put("tags", version.tags)
                    put("isChecklist", version.isChecklist)
                    put("checklist", version.checklist)
                    put("savedAt", version.savedAt)
                }
            }
        }

        val taskVersionsArray = buildJsonArray {
            val taskById = tasks.associateBy { it.id }
            taskVersions.forEach { version ->
                val owner = taskById[version.taskId] ?: return@forEach
                addJsonObject {
                    put("taskTitle", owner.title)
                    put("taskCreatedAt", owner.createdAt)
                    put("title", version.title)
                    put("notes", version.notes)
                    put("subtasks", version.subtasks)
                    put("priority", version.priority)
                    put("dueAt", version.dueAt)
                    put("savedAt", version.savedAt)
                }
            }
        }

        val chatsArray = buildJsonArray {
            chats.forEach {
                addJsonObject {
                    put("role", it.role)
                    put("content", it.content)
                    put("timestamp", it.timestamp)
                    put("attachmentMime", it.attachmentMime)
                    put("attachmentData", it.attachmentData)
                    put("attachmentName", it.attachmentName)
                    put("attachmentList", it.attachmentList)
                    put("conversationId", it.conversationId)
                    put("tokens", it.tokens)
                    put("agentTrace", it.agentTrace)
                    put("reasoningBlocks", it.reasoningBlocks)
                    put("reasoningText", it.reasoningText)
                    put("quotedRole", it.quotedRole)
                    put("quotedText", it.quotedText)
                }
            }
        }

        val conversationsArray = buildJsonArray {
            conversations.forEach {
                addJsonObject {
                    put("id", it.id)
                    put("title", it.title)
                    put("createdAt", it.createdAt)
                    put("updatedAt", it.updatedAt)
                }
            }
        }

        val wantApi = BackupManager.BackupModule.API in modules
        val wantSettings = BackupManager.BackupModule.SETTINGS in modules
        val wantLocal = BackupManager.BackupModule.LOCAL_ASSISTANT in modules

        val settingsObj = buildJsonObject {
            if (wantApi) {
                val rawProfilesJson = settings.apiProfilesJson.first()
                val allProfiles = ApiProfiles.parse(rawProfilesJson)
                if (apiProfileNames == null || allProfiles.isEmpty()) {
                    put("baseUrl", settings.baseUrl.first())
                    put("apiSpec", settings.apiSpec.first())
                    put("apiKeyEncrypted", CryptoUtil.encrypt(settings.apiKey.first()))
                    put("model", settings.model.first())
                    put("apiProfiles", rawProfilesJson)
                    put("apiProfileSelected", settings.apiProfileSelected.first())
                } else {
                    val origSelected = settings.apiProfileSelected.first()
                    val keptIndexed = allProfiles.withIndex().filter { it.value.name in apiProfileNames }
                    val keptProfiles = keptIndexed.map { it.value }
                    val newSelected =
                        keptIndexed.indexOfFirst { it.index == origSelected }.let { if (it >= 0) it else 0 }
                    val mirror = keptProfiles.getOrNull(newSelected)
                    put("baseUrl", mirror?.baseUrl ?: "")
                    put("apiSpec", mirror?.spec ?: "openai")
                    put("apiKeyEncrypted", CryptoUtil.encrypt(mirror?.apiKey ?: ""))
                    put("model", mirror?.model ?: "")
                    put("apiProfiles", ApiProfiles.serializeForBackup(keptProfiles))
                    put("apiProfileSelected", newSelected)
                }
            }
            if (wantSettings) {
                put("themeMode", settings.themeMode.first())
                put("palette", settings.palette.first())
                put("dynamicColorEnabled", settings.dynamicColorEnabled.first())
                put("font", settings.font.first())
                put("fontLibrary", FontStore.exportManifestJson(context))
                put("assistantName", settings.assistantName.first())
                put("assistantStyle", settings.assistantStyle.first())
                put("memoryTier", settings.memoryTier.first())
                put("memoryTierLocal", settings.memoryTierLocal.first())
                put("agentMode", settings.agentMode.first())
                put("reasoning", settings.reasoning.first())
                put("webSearchEngine", settings.webSearchEngine.first())
                put("webSearchEnabled", settings.webSearchEnabled.first())
                put("typingHaptics", settings.typingHapticsEnabled.first())
                put("markdownEnabled", settings.markdownEnabled.first())
                put("linksEnabled", settings.linksEnabled.first())
                put("backgroundAnimationEnabled", settings.backgroundAnimationEnabled.first())
                put("splashEnabled", settings.splashEnabled.first())
                put("splashStyle", settings.splashStyle.first())
                put("appLanguage", settings.appLanguage.first())
                put("notesSort", settings.notesSort.first())
                put("tasksSort", settings.tasksSort.first())
                put("notebooksSort", settings.notebooksSort.first())
                put("notebookOpens", settings.notebookOpens.first())
                put("systemIntegrationEnabled", settings.systemIntegrationEnabled.first())
                put("startupLoggingEnabled", settings.startupLoggingEnabled.first())
                put("savedSearches", settings.savedSearches.first())
                
                val hcStr = settings.harnessConfig.first()
                val hc = HarnessConfig.parse(hcStr)
                val exportHc = if (hc.pluginBackupScope == "none") hc.copy(plugins = emptyList()) else hc
                put("harnessConfig", exportHc.toJson())

                put("customTemplates", settings.customTemplatesJson.first())
                put("templateDraft", settings.templateDraftJson.first())
                put("hiddenTemplates", settings.hiddenTemplatesJson.first())
                put("cloudEnabled", settings.cloudEnabled.first())
                put("cloudProvider", settings.cloudProvider.first())
                put("cloudUrl", settings.cloudUrl.first())
                put("cloudUser", settings.cloudUser.first())
                put("cloudPasswordEnc", settings.cloudPasswordEnc.first())
                put("terminalFontSize", settings.terminalFontSize.first()?.toDouble() ?: 14.0)
                put("terminalKeyBarVisible", settings.terminalKeyBarVisible.first())
                put("globalTextSelectionEnabled", settings.globalTextSelectionEnabled.first())
                put("cloudFolder", settings.cloudFolder.first())
                put("cloudAutoBackup", settings.cloudAutoBackup.first())
                put("noteHistoryEnabled", settings.noteHistoryEnabled.first())
                put("taskHistoryEnabled", settings.taskHistoryEnabled.first())
                put("pwFirstRoundLimit", settings.pwFirstRoundLimit.first())
                put("pwLaterRoundLimit", settings.pwLaterRoundLimit.first())
                put("pwSelfDestructEnabled", settings.pwSelfDestructEnabled.first())
                put("pwSelfDestructThreshold", settings.pwSelfDestructThreshold.first())
                put("richTextEnabled", settings.richTextEnabled.first())
                put("openLinksExternally", settings.openLinksExternally.first())
                put("assistantConfirmToolsEnabled", settings.assistantConfirmToolsEnabled.first())
                put("smallModelModeEnabled", settings.smallModelModeEnabled.first())
                put("crashShieldEnabled", settings.crashShieldEnabled.first())
                put("blackoutEnabled", settings.blackoutEnabled.first())
                put("updateChannel", settings.updateChannel.first())
                put("autoUpdateEnabled", settings.autoUpdateEnabled.first())
                put("privilegedEnabled", settings.privilegedEnabled.first())
            }

            if (wantLocal) {
                put("localModelEnabled", settings.localModelEnabled.first())
                put("localToolsEnabled", settings.localToolsEnabled.first())
                put("localGpuEnabled", settings.localGpuEnabled.first())
                put("localBackgroundReply", settings.localBackgroundReplyEnabled.first())
                put("localModelManifest", com.lucent.app.local.LocalModelStore.exportManifestJson(context))
            }
        }

        val notebooksArray = buildJsonArray {
            notebooks.forEach { nb ->
                addJsonObject {
                    put("title", nb.title)
                    put("createdAt", nb.createdAt)
                    put("updatedAt", nb.updatedAt)
                    put("color", nb.color)
                    put("manualOrder", nb.manualOrder)
                    put("pinned", nb.pinned)
                    if (nb.color.startsWith("photo:")) {
                        val photoBytes = AttachmentStore.readBytes(context, nb.color.removePrefix("photo:"), Long.MAX_VALUE)
                        if (photoBytes != null) put("coverData", Base64.Default.encode(photoBytes))
                    }
                }
            }
        }
        val notebookItemsArray = buildJsonArray {
            notebookItems.forEach { item ->
                addJsonObject {
                    put("notebookTitle", notebooks.firstOrNull { it.id == item.notebookId }?.title ?: "")
                    put("itemKind", item.itemKind)
                    put("itemTitle", when (item.itemKind) {
                        NotebookItem.KIND_NOTE -> notes.firstOrNull { it.id == item.itemId }?.title ?: ""
                        NotebookItem.KIND_TASK -> tasks.firstOrNull { it.id == item.itemId }?.title ?: ""
                        else -> ""
                    })
                    put("itemCreatedAt", when (item.itemKind) {
                        NotebookItem.KIND_NOTE -> notes.firstOrNull { it.id == item.itemId }?.updatedAt ?: -1L
                        NotebookItem.KIND_TASK -> tasks.firstOrNull { it.id == item.itemId }?.createdAt ?: -1L
                        else -> -1L
                    })
                }
            }
        }

        val root = buildJsonObject {
            put("version", BackupManager.BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("modules", buildJsonArray { modules.forEach { add(it.name) } })
            if (BackupManager.BackupModule.NOTES in modules) {
                put("notes", notesArray)
                put("noteVersions", versionsArray)
            }
            if (BackupManager.BackupModule.TASKS in modules) {
                put("tasks", tasksArray)
                put("taskVersions", taskVersionsArray)
            }
            if (BackupManager.BackupModule.CHATS in modules) {
                put("chats", chatsArray)
                put("conversations", conversationsArray)
            }
            if (notebooksArray.isNotEmpty()) {
                put("notebooks", notebooksArray)
                put("notebookItems", notebookItemsArray)
            }
            if (settingsObj.isNotEmpty()) {
                put("settings", settingsObj)
            }
        }
        return root
    }

    private fun inlineAttachmentBytes(context: PlatformContext, attachmentsJson: String): String {
        val list = Attachments.parse(attachmentsJson)
        if (list.isEmpty()) return attachmentsJson
        val inlined = list.mapNotNull { att ->
            if (!AttachmentStore.looksLikeId(att.data)) return@mapNotNull att
            val plain = AttachmentStore.readBytes(context, att.data, maxBytes = Long.MAX_VALUE)
                ?: return@mapNotNull null
            val encoded = try {
                Base64.Default.encode(plain)
            } catch (t: Throwable) {
                return@mapNotNull null
            }
            att.copy(data = encoded)
        }
        return Attachments.serialize(inlined)
    }
}
