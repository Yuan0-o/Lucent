package com.lucent.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

internal object BackupManifestBuilder {

    suspend fun build(
        context: Context,
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
    ): JSONObject {
        val notesArray = JSONArray()
        notes.forEach {
            val attachments = if (inlineAttachments) inlineAttachmentBytes(context, it.attachments) else it.attachments
            notesArray.put(
                JSONObject()
                    .put("title", it.title)
                    .put("body", it.body)
                    .put("updatedAt", it.updatedAt)
                    .put("tags", it.tags)
                    .put("attachments", attachments)
                    .put("archived", it.archived)
                    .put("archivedAt", it.archivedAt ?: JSONObject.NULL)
                    .put("pinned", it.pinned)
                    .put("color", it.color)
                    .put("isChecklist", it.isChecklist)
                    .put("checklist", it.checklist)
                    .put("trashedAt", it.trashedAt ?: JSONObject.NULL)
                    .put("manualOrder", it.manualOrder)
                    .put("isDraft", it.isDraft)
                    .put("draftSavedAt", it.draftSavedAt ?: JSONObject.NULL)
                    .put("hidden", it.hidden)
                    .put("isDoodle", it.isDoodle)
                    .put("bodySpans", it.bodySpans)
                    .put("doodle", it.doodle)
                    .put("formatOverride", it.formatOverride ?: JSONObject.NULL)
            )
        }

        val tasksArray = JSONArray()
        tasks.forEach {
            val attachments = if (inlineAttachments) inlineAttachmentBytes(context, it.attachments) else it.attachments
            tasksArray.put(
                JSONObject()
                    .put("title", it.title)
                    .put("isDone", it.isDone)
                    .put("createdAt", it.createdAt)
                    .put("attachments", attachments)
                    .put("dueAt", it.dueAt ?: JSONObject.NULL)
                    .put("notes", it.notes)
                    .put("completedAt", it.completedAt ?: JSONObject.NULL)
                    .put("priority", it.priority)
                    .put("pinned", it.pinned)
                    .put("subtasks", it.subtasks)
                    .put("repeatRule", it.repeatRule)
                    .put("reminderEnabled", it.reminderEnabled)
                    .put("trashedAt", it.trashedAt ?: JSONObject.NULL)
                    .put("manualOrder", it.manualOrder)
                    .put("isDraft", it.isDraft)
                    .put("draftSavedAt", it.draftSavedAt ?: JSONObject.NULL)
                    .put("hidden", it.hidden)
                    .put("notesSpans", it.notesSpans)
                    .put("formatOverride", it.formatOverride ?: JSONObject.NULL)
            )
        }

        val versionsArray = JSONArray()
        val noteById = notes.associateBy { it.id }
        noteVersions.forEach { version ->
            val owner = noteById[version.noteId] ?: return@forEach
            versionsArray.put(
                JSONObject()
                    .put("noteTitle", owner.title)
                    .put("noteUpdatedAt", owner.updatedAt)
                    .put("title", version.title)
                    .put("body", version.body)
                    .put("tags", version.tags)
                    .put("isChecklist", version.isChecklist)
                    .put("checklist", version.checklist)
                    .put("savedAt", version.savedAt)
            )
        }

        val taskVersionsArray = JSONArray()
        val taskById = tasks.associateBy { it.id }
        taskVersions.forEach { version ->
            val owner = taskById[version.taskId] ?: return@forEach
            taskVersionsArray.put(
                JSONObject()
                    .put("taskTitle", owner.title)
                    .put("taskCreatedAt", owner.createdAt)
                    .put("title", version.title)
                    .put("notes", version.notes)
                    .put("subtasks", version.subtasks)
                    .put("priority", version.priority)
                    .put("dueAt", version.dueAt ?: JSONObject.NULL)
                    .put("savedAt", version.savedAt)
            )
        }

        val chatsArray = JSONArray()
        chats.forEach {
            chatsArray.put(
                JSONObject()
                    .put("role", it.role)
                    .put("content", it.content)
                    .put("timestamp", it.timestamp)
                    .put("attachmentMime", it.attachmentMime ?: JSONObject.NULL)
                    .put("attachmentData", it.attachmentData ?: JSONObject.NULL)
                    .put("attachmentName", it.attachmentName ?: JSONObject.NULL)
                    .put("attachmentList", it.attachmentList ?: JSONObject.NULL)
                    .put("conversationId", it.conversationId)
                    .put("tokens", it.tokens)
                    .put("agentTrace", it.agentTrace ?: JSONObject.NULL)
            )
        }

        val conversationsArray = JSONArray()
        conversations.forEach {
            conversationsArray.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("createdAt", it.createdAt)
                    .put("updatedAt", it.updatedAt)
            )
        }

        val wantApi = BackupManager.BackupModule.API in modules
        val wantSettings = BackupManager.BackupModule.SETTINGS in modules
        val wantLocal = BackupManager.BackupModule.LOCAL_ASSISTANT in modules

        val settingsObj = JSONObject()
        if (wantApi) {
            val rawProfilesJson = settings.apiProfilesJson.first()
            val allProfiles = ApiProfiles.parse(rawProfilesJson)
            if (apiProfileNames == null || allProfiles.isEmpty()) {
                settingsObj
                    .put("baseUrl", settings.baseUrl.first())
                    .put("apiSpec", settings.apiSpec.first())
                    .put("apiKeyEncrypted", CryptoUtil.encrypt(settings.apiKey.first()))
                    .put("model", settings.model.first())
                    .put("apiProfiles", rawProfilesJson)
                    .put("apiProfileSelected", settings.apiProfileSelected.first())
            } else {
                val origSelected = settings.apiProfileSelected.first()
                val keptIndexed = allProfiles.withIndex().filter { it.value.name in apiProfileNames }
                val keptProfiles = keptIndexed.map { it.value }
                val newSelected =
                    keptIndexed.indexOfFirst { it.index == origSelected }.let { if (it >= 0) it else 0 }
                val mirror = keptProfiles.getOrNull(newSelected)
                settingsObj
                    .put("baseUrl", mirror?.baseUrl ?: "")
                    .put("apiSpec", mirror?.spec ?: "openai")
                    .put("apiKeyEncrypted", CryptoUtil.encrypt(mirror?.apiKey ?: ""))
                    .put("model", mirror?.model ?: "")
                    .put("apiProfiles", ApiProfiles.serializeForBackup(keptProfiles))
                    .put("apiProfileSelected", newSelected)
            }
        }
        if (wantSettings) settingsObj
            .put("themeMode", settings.themeMode.first())
            .put("palette", settings.palette.first())
            .put("dynamicColorEnabled", settings.dynamicColorEnabled.first())
            .put("font", settings.font.first())
            .put("fontLibrary", FontStore.exportManifestJson(context))
            .put("assistantName", settings.assistantName.first())
            .put("assistantStyle", settings.assistantStyle.first())
        if (wantSettings) settingsObj
            .put("memoryTier", settings.memoryTier.first())
            .put("webSearchEnabled", settings.webSearchEnabled.first())
            .put("typingHaptics", settings.typingHapticsEnabled.first())
            .put("markdownEnabled", settings.markdownEnabled.first())
            .put("linksEnabled", settings.linksEnabled.first())
            .put("backgroundAnimationEnabled", settings.backgroundAnimationEnabled.first())
            .put("splashEnabled", settings.splashEnabled.first())
            .put("splashStyle", settings.splashStyle.first())
            .put("appLanguage", settings.appLanguage.first())
            .put("notesSort", settings.notesSort.first())
            .put("tasksSort", settings.tasksSort.first())
            .put("systemIntegrationEnabled", settings.systemIntegrationEnabled.first())
            .put("startupLoggingEnabled", settings.startupLoggingEnabled.first())
            .put("savedSearches", settings.savedSearches.first())
            .put("customTemplates", settings.customTemplatesJson.first())
            .put("templateDraft", settings.templateDraftJson.first())
            .put("hiddenTemplates", settings.hiddenTemplatesJson.first())
            .put("cloudEnabled", settings.cloudEnabled.first())
            .put("cloudProvider", settings.cloudProvider.first())
            .put("cloudUrl", settings.cloudUrl.first())
            .put("cloudUser", settings.cloudUser.first())
            .put("cloudPasswordEnc", settings.cloudPasswordEnc.first())
            .put("cloudFolder", settings.cloudFolder.first())
            .put("cloudAutoBackup", settings.cloudAutoBackup.first())
            .put("noteHistoryEnabled", settings.noteHistoryEnabled.first())
            .put("taskHistoryEnabled", settings.taskHistoryEnabled.first())
            .put("pwFirstRoundLimit", settings.pwFirstRoundLimit.first())
            .put("pwLaterRoundLimit", settings.pwLaterRoundLimit.first())
            .put("pwSelfDestructEnabled", settings.pwSelfDestructEnabled.first())
            .put("pwSelfDestructThreshold", settings.pwSelfDestructThreshold.first())
            .put("richTextEnabled", settings.richTextEnabled.first())
            .put("openLinksExternally", settings.openLinksExternally.first())
            .put("assistantConfirmToolsEnabled", settings.assistantConfirmToolsEnabled.first())
            .put("smallModelModeEnabled", settings.smallModelModeEnabled.first())
            .put("crashShieldEnabled", settings.crashShieldEnabled.first())
            .put("blackoutEnabled", settings.blackoutEnabled.first())
            .put("autoUpdateEnabled", settings.autoUpdateEnabled.first())
            .put("privilegedEnabled", settings.privilegedEnabled.first())

        if (wantLocal) settingsObj
            .put("localModelEnabled", settings.localModelEnabled.first())
            .put("localToolsEnabled", settings.localToolsEnabled.first())
            .put("localGpuEnabled", settings.localGpuEnabled.first())
            .put("localBackgroundReply", settings.localBackgroundReplyEnabled.first())
            .put("localModelManifest", com.lucent.app.local.LocalModelStore.exportManifestJson(context))

        val notebooksArray = JSONArray()
        notebooks.forEach { nb ->
            notebooksArray.put(
                JSONObject()
                    .put("title", nb.title)
                    .put("createdAt", nb.createdAt)
                    .put("updatedAt", nb.updatedAt)
            )
        }
        val notebookItemsArray = JSONArray()
        notebookItems.forEach { item ->
            notebookItemsArray.put(
                JSONObject()
                    .put("notebookTitle", notebooks.firstOrNull { it.id == item.notebookId }?.title ?: "")
                    .put("itemKind", item.itemKind)
                    .put("itemTitle", when (item.itemKind) {
                        NotebookItem.KIND_NOTE -> notes.firstOrNull { it.id == item.itemId }?.title ?: ""
                        NotebookItem.KIND_TASK -> tasks.firstOrNull { it.id == item.itemId }?.title ?: ""
                        else -> ""
                    })
                    .put("itemCreatedAt", when (item.itemKind) {
                        NotebookItem.KIND_NOTE -> notes.firstOrNull { it.id == item.itemId }?.updatedAt ?: -1L
                        NotebookItem.KIND_TASK -> tasks.firstOrNull { it.id == item.itemId }?.createdAt ?: -1L
                        else -> -1L
                    })
            )
        }

        val root = JSONObject()
            .put("version", BackupManager.BACKUP_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("modules", JSONArray().apply { modules.forEach { put(it.name) } })
        if (BackupManager.BackupModule.NOTES in modules) root.put("notes", notesArray).put("noteVersions", versionsArray)
        if (BackupManager.BackupModule.TASKS in modules) root.put("tasks", tasksArray).put("taskVersions", taskVersionsArray)
        if (BackupManager.BackupModule.CHATS in modules) root.put("chats", chatsArray).put("conversations", conversationsArray)
        if (notebooksArray.length() > 0) {
            root.put("notebooks", notebooksArray).put("notebookItems", notebookItemsArray)
        }
        if (settingsObj.length() > 0) root.put("settings", settingsObj)
        return root
    }

    private fun inlineAttachmentBytes(context: Context, attachmentsJson: String): String {
        val list = Attachments.parse(attachmentsJson)
        if (list.isEmpty()) return attachmentsJson
        val inlined = list.mapNotNull { att ->
            if (!AttachmentStore.looksLikeId(att.data)) return@mapNotNull att
            val plain = AttachmentStore.readBytes(context, att.data, maxBytes = Long.MAX_VALUE)
                ?: return@mapNotNull null
            val encoded = try {
                Base64.encodeToString(plain, Base64.NO_WRAP)
            } catch (t: Throwable) {
                return@mapNotNull null
            }
            att.copy(data = encoded)
        }
        return Attachments.serialize(inlined)
    }
}
