package com.lucent.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import org.json.JSONObject
import java.io.OutputStream

object BackupManager {

    enum class BackupModule {
        NOTES,
        TASKS,
        CHATS,
        SETTINGS,
        API,
        LOCAL_ASSISTANT,
        LOCAL_MODEL_FILES,
        HARNESS
    }

    val DEFAULT_MODULES: Set<BackupModule> =
        BackupModule.entries.toSet() - BackupModule.LOCAL_MODEL_FILES

    fun interface BackupSource {
        fun open(): java.io.InputStream
    }

    fun fileSource(file: java.io.File): BackupSource = BackupSource { file.inputStream() }

    data class BackupSelection(
        val modules: Set<BackupModule> = DEFAULT_MODULES,
        val noteIds: Set<Long>? = null,
        val taskIds: Set<Long>? = null,
        val conversationIds: Set<Long>? = null,
        val apiProfileNames: Set<String>? = null
    ) {
        fun has(m: BackupModule) = m in modules
        fun wantsNote(id: Long) = noteIds?.contains(id) ?: true
        fun wantsTask(id: Long) = taskIds?.contains(id) ?: true
        fun wantsConversation(id: Long) = conversationIds?.contains(id) ?: true
        fun wantsApiProfileName(name: String) = apiProfileNames?.contains(name) ?: true
        val isEmpty: Boolean
            get() = modules.isEmpty() ||
                modules.all { m ->
                    when (m) {
                        BackupModule.NOTES -> noteIds?.isEmpty() == true
                        BackupModule.TASKS -> taskIds?.isEmpty() == true
                        BackupModule.CHATS -> conversationIds?.isEmpty() == true
                        BackupModule.API -> apiProfileNames?.isEmpty() == true
                        else -> false
                    }
                }
    }



    internal const val BACKUP_VERSION = 13


    suspend fun exportJsonFull(
        context: Context,
        db: AppDatabase,
        settings: SettingsRepository,
        selection: BackupSelection = BackupSelection()
    ): String {
        val modules = selection.modules
        val notes = if (BackupModule.NOTES in modules) {
            db.noteDao().getAllOnce().filter { selection.wantsNote(it.id) }
        } else emptyList()
        val tasks = if (BackupModule.TASKS in modules) {
            db.taskDao().getAllOnce().filter { selection.wantsTask(it.id) }
        } else emptyList()
        val keptNoteIds = notes.map { it.id }.toHashSet()
        val noteVersions = if (BackupModule.NOTES in modules) {
            db.noteVersionDao().getAllOnce().filter { it.noteId in keptNoteIds }
        } else emptyList()
        val keptTaskIds = tasks.map { it.id }.toHashSet()
        val taskVersions = if (BackupModule.TASKS in modules) {
            db.taskVersionDao().getAllOnce().filter { it.taskId in keptTaskIds }
        } else emptyList()
        val conversations =
            if (BackupModule.CHATS in modules) {
                db.chatConversationDao().getAllOnce().filter { selection.wantsConversation(it.id) }
            } else emptyList()
        val chats = if (BackupModule.CHATS in modules) {
            db.chatDao().getAll().first().filter { selection.wantsConversation(it.conversationId) }
        } else emptyList()
        val notebooks = if (BackupModule.NOTES in modules || BackupModule.TASKS in modules) {
            db.notebookDao().getAllOnce()
        } else emptyList()
        val notebookItems = if (notebooks.isNotEmpty()) {
            notebooks.flatMap { db.notebookDao().getItemsOnce(it.id) }
        } else emptyList()
        return BackupManifestBuilder.build(
            context, notes, tasks, noteVersions, taskVersions, chats, conversations, settings,
            notebooks = notebooks, notebookItems = notebookItems,
            inlineAttachments = true, modules = modules, apiProfileNames = selection.apiProfileNames
        ).toString(2)
    }

    suspend fun exportEncrypted(
        context: Context,
        db: AppDatabase,
        settings: SettingsRepository,
        out: OutputStream,
        password: String?,
        selection: BackupSelection = BackupSelection()
    ) {
        val modules = selection.modules
        val exportJob = coroutineContext[Job]
        val cancelled: () -> Boolean = { exportJob?.isActive == false }
        val json = exportJsonFull(context, db, settings, selection)
        val jsonBytes = json.toByteArray(Charsets.UTF_8)

        val modelFiles: List<Pair<String, java.io.File>> =
            if (BackupModule.LOCAL_MODEL_FILES in modules) {
                com.lucent.app.local.LocalModelStore.slots(context).mapNotNull { slot ->
                    com.lucent.app.local.LocalModelStore.modelFileForSlot(context, slot)
                        ?.let { slot.fileName to it }
                }
            } else emptyList()

        val fontFiles: List<Pair<String, java.io.File>> =
            if (BackupModule.SETTINGS in modules) {
                FontStore.fonts(context).mapNotNull { slot ->
                    FontStore.fontFileForSlot(context, slot)
                        ?.let { (BackupFrames.FONT_BLOB_PREFIX + slot.fileName) to it }
                }
            } else emptyList()

        val harnessFiles: List<Pair<String, java.io.File>> =
            if (BackupModule.HARNESS in modules) {
                HarnessBackup.useHome { HarnessBackup.listFiles() }
            } else emptyList()

        val blobs = modelFiles + fontFiles + harnessFiles
        BackupCrypto.encryptingStream(out, password).use { cipherOut ->
            if (blobs.isEmpty()) {
                cipherOut.write(jsonBytes)
                return@use
            }
            cipherOut.write(byteArrayOf(BackupFrames.FRAME_MAGIC, BackupFrames.FRAME_VERSION))
            BackupFrames.writeInt(cipherOut, jsonBytes.size)
            cipherOut.write(jsonBytes)
            val buffer = ByteArray(1 shl 16)
            for ((name, file) in blobs) {
                BackupFrames.throwIfCancelled(cancelled)
                val nameBytes = name.toByteArray(Charsets.UTF_8)
                BackupFrames.writeInt(cipherOut, nameBytes.size)
                cipherOut.write(nameBytes)
                BackupFrames.writeLong(cipherOut, file.length())
                file.inputStream().use { input ->
                    while (true) {
                        BackupFrames.throwIfCancelled(cancelled)
                        val n = input.read(buffer)
                        if (n < 0) break
                        cipherOut.write(buffer, 0, n)
                    }
                }
            }
        }
        StartupLog.event(
            context,
            "Backup exported: modules=${modules.joinToString(",") { it.name }}" +
                (selection.noteIds?.let { "; notes=${it.size}" } ?: "") +
                (selection.taskIds?.let { "; tasks=${it.size}" } ?: "") +
                (selection.conversationIds?.let { "; chats=${it.size}" } ?: "") +
                (selection.apiProfileNames?.let { "; apiProfiles=${it.size}" } ?: "") +
                "; models=${modelFiles.size}; fonts=${fontFiles.size}; harness=${harnessFiles.size}" +
                "; password=${if (password.isNullOrEmpty()) "no" else "yes"}"
        )
    }






    data class BackupPreview(
        internal val manifestJson: String,
        val formatVersion: Int,
        val exportedAt: Long?,
        val encrypted: Boolean,
        val passwordProtected: Boolean,
        val notes: Int,
        val archivedNotes: Int,
        val trashedNotes: Int,
        val tasks: Int,
        val completedTasks: Int,
        val trashedTasks: Int,
        val noteVersions: Int,
        val conversations: Int,
        val chatMessages: Int,
        val attachments: Int,
        val hasSettings: Boolean,
        val modules: Set<BackupModule> = emptySet(),
        val modelFiles: Int = 0,
        val modelBytes: Long = 0L,
        val fontFiles: Int = 0,
        val fontBytes: Long = 0L,
        val harnessFiles: Int = 0,
        val harnessBytes: Long = 0L,
        internal val hasBlobs: Boolean = false,
        internal val password: String? = null,
        val conversationList: List<Pair<Long, String>> = emptyList(),
        val apiProfileNames: List<String> = emptyList()
    ) {
        val isEmpty: Boolean
            get() = notes == 0 && tasks == 0 && chatMessages == 0 && conversations == 0 &&
                !hasSettings && modelFiles == 0 && fontFiles == 0 && harnessFiles == 0
    }

    suspend fun inspect(context: Context, source: BackupSource, password: String? = null): BackupPreview {
        val inspectJob = coroutineContext[Job]
        val cancelled: () -> Boolean = { inspectJob?.isActive == false }
        val needsPassword: Boolean
        val scan: BackupFrames.PayloadScan
        var plain: java.io.InputStream? = null
        try {
            needsPassword = peekPasswordRequirement(source)?.needsPassword
                ?: throw IllegalArgumentException(com.lucent.app.i18n.S.notLcbBackup)
            plain = BackupFrames.openDecrypted(source, password)
            scan = try {
                BackupFrames.scanPayload(plain, cancelled)
            } catch (t: java.io.IOException) {
                if (t is BackupCrypto.WrongPasswordException) throw t
                if (needsPassword) throw BackupCrypto.WrongPasswordException()
                throw java.io.IOException("Backup file is damaged", t)
            }
        } finally {
            try { plain?.close() } catch (_: Throwable) {}
        }
        val manifestJson = scan.manifestJson

        val root = try {
            JSONObject(manifestJson)
        } catch (t: Throwable) {
            throw IllegalArgumentException("That backup couldn't be read — the file may be damaged.")
        }

        val notesArr = root.optJSONArray("notes")
        val tasksArr = root.optJSONArray("tasks")

        var archived = 0
        var trashedNotes = 0
        var attachments = 0
        for (i in 0 until (notesArr?.length() ?: 0)) {
            val o = notesArr!!.getJSONObject(i)
            if (o.optBoolean("archived", false)) archived++
            if (!o.isNull("trashedAt")) trashedNotes++
            attachments += Attachments.parse(o.optString("attachments", "[]")).size
        }

        var completed = 0
        var trashedTasks = 0
        for (i in 0 until (tasksArr?.length() ?: 0)) {
            val o = tasksArr!!.getJSONObject(i)
            if (o.optBoolean("isDone", false)) completed++
            if (!o.isNull("trashedAt")) trashedTasks++
            attachments += Attachments.parse(o.optString("attachments", "[]")).size
        }


        val convList = root.optJSONArray("conversations")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optLong("id", 0L)
                if (id == 0L) null else id to o.optString("title", "")
            }
        } ?: emptyList()
        val profileNames = root.optJSONObject("settings")?.optString("apiProfiles")?.let { pj ->
            if (pj.isBlank()) emptyList() else ApiProfiles.parse(pj).map { it.name }
        } ?: emptyList()

        return BackupPreview(
            manifestJson = manifestJson,
            formatVersion = root.optInt("version", 0),
            exportedAt = root.optLong("exportedAt", 0L).takeIf { it > 0 },
            encrypted = true,
            passwordProtected = needsPassword,
            notes = notesArr?.length() ?: 0,
            archivedNotes = archived,
            trashedNotes = trashedNotes,
            tasks = tasksArr?.length() ?: 0,
            completedTasks = completed,
            trashedTasks = trashedTasks,
            noteVersions = root.optJSONArray("noteVersions")?.length() ?: 0,
            conversations = root.optJSONArray("conversations")?.length() ?: 0,
            chatMessages = root.optJSONArray("chats")?.length() ?: 0,
            attachments = attachments,
            hasSettings = root.optJSONObject("settings") != null,
            modules = root.optJSONArray("modules")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { BackupModule.valueOf(arr.optString(i)) }.getOrNull()
                }.toSet()
            } ?: emptySet(),
            modelFiles = scan.modelCount,
            modelBytes = scan.modelBytes,
            fontFiles = scan.fontCount,
            fontBytes = scan.fontBytes,
            harnessFiles = scan.harnessCount,
            harnessBytes = scan.harnessBytes,
            hasBlobs = scan.framed,
            password = password,
            conversationList = convList,
            apiProfileNames = profileNames
        )
    }

    suspend fun inspect(context: Context, bytes: ByteArray, password: String? = null): BackupPreview =
        inspect(context, BackupSource { bytes.inputStream() }, password)

    suspend fun commit(
        context: Context,
        db: AppDatabase,
        settings: SettingsRepository,
        preview: BackupPreview,
        modules: Set<BackupModule> = BackupModule.entries.toSet(),
        conversationIds: Set<Long>? = null,
        apiProfileNames: Set<String>? = null,
        source: BackupSource? = null
    ): String {
        val commitJob = coroutineContext[Job]
        val cancelled: () -> Boolean = { commitJob?.isActive == false }
        var restoredModels = 0
        var restoredFonts = 0
        var restoredHarness = 0
        var harnessProblems: List<String> = emptyList()
        if (preview.hasBlobs && source != null) {
            val wantModels = BackupModule.LOCAL_MODEL_FILES in modules
            val wantFonts = BackupModule.SETTINGS in modules
            val wantHarness = BackupModule.HARNESS in modules
            if (wantModels || wantFonts) {
                val scratch = ByteArray(1 shl 16)
                var plain: java.io.InputStream? = null
                try {
                    plain = BackupFrames.openDecrypted(source, preview.password)
                    BackupFrames.scanPayload(plain, cancelled) { name, dataLen, data ->
                        val (m, f) = BackupFrames.restoreOneBlob(
                            context, name, dataLen, data, wantModels, wantFonts, scratch, cancelled
                        )
                        restoredModels += m
                        restoredFonts += f
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                } finally {
                    try { plain?.close() } catch (_: Throwable) {}
                }
            }
            if (wantHarness) {
                val harness = BackupImporter.restoreHarness(context, source, preview.password, cancelled)
                restoredHarness = harness.restored
                harnessProblems = harness.problems
            }
        }
        BackupFrames.throwIfCancelled(cancelled)
        val summary = withContext(NonCancellable) {
            importJson(
                context, db, settings, preview.manifestJson, modules, conversationIds, apiProfileNames
            )
        }
        StartupLog.event(
            context,
            "Backup restored: modules=${modules.joinToString(",") { it.name }}" +
                (conversationIds?.let { "; chats=${it.size}" } ?: "") +
                (apiProfileNames?.let { "; apiProfiles=${it.size}" } ?: "") +
                "; models=$restoredModels; fonts=$restoredFonts; harness=$restoredHarness" +
                (if (harnessProblems.isEmpty()) "" else "; harnessProblems=${harnessProblems.joinToString("|")}")
        )
        var report = summary
        if (restoredModels > 0) report += com.lucent.app.i18n.S.backupModelFilesRestored(restoredModels)
        if (restoredFonts > 0) report += com.lucent.app.i18n.S.backupFontsRestored(restoredFonts)
        if (restoredHarness > 0) report += com.lucent.app.i18n.S.backupHarnessFilesRestored(restoredHarness)
        if (harnessProblems.isNotEmpty()) {
            report += com.lucent.app.i18n.S.backupHarnessFilesFailed(harnessProblems.size)
        }
        return report
    }

    fun peekPasswordRequirement(bytes: ByteArray): BackupCrypto.Header? = BackupCrypto.readHeader(bytes)

    fun peekPasswordRequirement(source: BackupSource): BackupCrypto.Header? = try {
        source.open().use { input ->
            val head = ByteArray(64)
            var read = 0
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n < 0) break
                read += n
            }
            if (read == 0) null else BackupCrypto.readHeader(head.copyOf(read))
        }
    } catch (_: Throwable) {
        null
    }

    suspend fun importJson(
        context: Context,
        db: AppDatabase,
        settings: SettingsRepository,
        json: String,
        modules: Set<BackupModule> = BackupModule.entries.toSet(),
        conversationIds: Set<Long>? = null,
        apiProfileNames: Set<String>? = null,
        mode: ImportMode = ImportMode.DEFAULT
    ): String = BackupImporter.import(
        context, db, settings, json, modules, conversationIds, apiProfileNames, mode
    )

}
