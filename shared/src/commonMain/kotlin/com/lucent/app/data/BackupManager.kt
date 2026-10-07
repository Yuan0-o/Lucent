package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.json.*
import okio.Path.Companion.toPath
import okio.buffer

object BackupManager {

    typealias BackupModule = com.lucent.app.data.BackupModule

    val DEFAULT_MODULES: Set<BackupModule> = com.lucent.app.data.DEFAULT_BACKUP_MODULES
    private val prettyJson = Json { prettyPrint = true }

    fun interface BackupSource {
        fun open(): okio.Source
    }

    fun fileSource(file: okio.Path): BackupSource = BackupSource { okio.FileSystem.SYSTEM.source(file) }

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
        context: PlatformContext,
        db: AppDatabase,
        settings: SettingsRepository,
        selection: BackupSelection = BackupSelection()
    ): String {
        val modules = selection.modules
        val notes = if (BackupModule.NOTES in modules) {
            db.noteDao.getAllOnce().filter { selection.wantsNote(it.id) }
        } else emptyList()
        val tasks = if (BackupModule.TASKS in modules) {
            db.taskDao.getAllOnce().filter { selection.wantsTask(it.id) }
        } else emptyList()
        val keptNoteIds = notes.map { it.id }.toHashSet()
        val noteVersions = if (BackupModule.NOTES in modules) {
            db.noteVersionDao.getAllOnce().filter { it.noteId in keptNoteIds }
        } else emptyList()
        val keptTaskIds = tasks.map { it.id }.toHashSet()
        val taskVersions = if (BackupModule.TASKS in modules) {
            db.taskVersionDao.getAllOnce().filter { it.taskId in keptTaskIds }
        } else emptyList()
        val conversations =
            if (BackupModule.CHATS in modules) {
                db.chatConversationDao.getAllOnce().filter { selection.wantsConversation(it.id) }
            } else emptyList()
        val chats = if (BackupModule.CHATS in modules) {
            db.chatDao.getAll().first().filter { selection.wantsConversation(it.conversationId) }
        } else emptyList()
        val notebooks = if (BackupModule.NOTES in modules || BackupModule.TASKS in modules) {
            db.notebookDao.getAllOnce()
        } else emptyList()
        val notebookItems = if (notebooks.isNotEmpty()) {
            notebooks.flatMap { db.notebookDao.getItemsOnce(it.id) }
        } else emptyList()
        val jsonObj = BackupManifestBuilder.build(
            context, notes, tasks, noteVersions, taskVersions, chats, conversations, settings,
            notebooks = notebooks, notebookItems = notebookItems,
            inlineAttachments = true, modules = modules, apiProfileNames = selection.apiProfileNames
        )
        return prettyJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), jsonObj)
    }

    suspend fun exportEncrypted(
        context: PlatformContext,
        db: AppDatabase,
        settings: SettingsRepository,
        out: okio.Sink,
        password: String?,
        selection: BackupSelection = BackupSelection()
    ) {
        val modules = selection.modules
        val exportJob = coroutineContext[Job]
        val cancelled: () -> Boolean = { exportJob?.isActive == false }
        val json = exportJsonFull(context, db, settings, selection)
        val jsonBytes = json.toByteArray(Charsets.UTF_8)

        val modelFiles: List<Pair<String, okio.Path>> =
            if (BackupModule.LOCAL_MODEL_FILES in modules) {
                com.lucent.app.local.LocalModelStore.slots(context).mapNotNull { slot ->
                    com.lucent.app.local.LocalModelStore.modelFileForSlot(context, slot)
                        ?.let { slot.fileName to it }
                }
            } else emptyList()

        val fontFiles: List<Pair<String, okio.Path>> =
            if (BackupModule.SETTINGS in modules) {
                FontStore.fonts(context).mapNotNull { slot ->
                    FontStore.fontFileForSlot(context, slot)
                        ?.let { (BackupFrames.FONT_BLOB_PREFIX + slot.fileName) to it }
                }
            } else emptyList()

        val harnessFiles: List<Pair<String, okio.Path>> =
            if (BackupModule.HARNESS in modules) {
                HarnessBackup.useHome { HarnessBackup.listFiles().map { it.first to it.second.toString().toPath() } }
            } else emptyList()

        val blobs = modelFiles + fontFiles + harnessFiles
        BackupCrypto.encryptingSink(out, password).buffer().use { cipherOut ->
            if (blobs.isEmpty()) {
                cipherOut.write(jsonBytes)
                cipherOut.flush()
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
                BackupFrames.writeLong(cipherOut, okio.FileSystem.SYSTEM.metadata(file).size ?: 0L)
                okio.FileSystem.SYSTEM.source(file).use { input ->
                    while (true) {
                        BackupFrames.throwIfCancelled(cancelled)
                        val n = input.read(buffer)
                        if (n < 0) break
                        cipherOut.write(buffer, 0, n)
                    }
                }
            }
            cipherOut.flush()
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

    suspend fun inspect(context: PlatformContext, source: BackupSource, password: String? = null): BackupPreview {
        val inspectJob = coroutineContext[Job]
        val cancelled: () -> Boolean = { inspectJob?.isActive == false }
        val needsPassword: Boolean
        val scan: BackupFrames.PayloadScan
        var plain: okio.Source? = null
        try {
            needsPassword = peekPasswordRequirement(source)?.needsPassword
                ?: throw IllegalArgumentException(com.lucent.app.i18n.S.notLcbBackup)
            plain = BackupFrames.openDecrypted(source, password)
            scan = try {
                BackupFrames.scanPayload(plain, cancelled)
            } catch (t: okio.IOException) {
                if (t is BackupCrypto.WrongPasswordException) throw t
                if (needsPassword) throw BackupCrypto.WrongPasswordException()
                throw okio.IOException("Backup file is damaged", t)
            }
        } finally {
            try { plain?.close() } catch (_: Throwable) {}
        }
        val manifestJson = scan.manifestJson

        val root = try {
            Json.parseToJsonElement(manifestJson).jsonObject
        } catch (t: Throwable) {
            throw IllegalArgumentException("That backup couldn't be read — the file may be damaged.")
        }

        val notesArr = root["notes"]?.jsonArray
        val tasksArr = root["tasks"]?.jsonArray

        var archived = 0
        var trashedNotes = 0
        var attachments = 0
        for (i in 0 until (notesArr?.size ?: 0)) {
            val o = notesArr!![i].jsonObject
            if (o["archived"]?.jsonPrimitive?.booleanOrNull == true) archived++
            if (o.containsKey("trashedAt") && o["trashedAt"] !is JsonNull) trashedNotes++
            attachments += Attachments.parse(o["attachments"]?.jsonPrimitive?.content ?: "[]").size
        }

        var completed = 0
        var trashedTasks = 0
        for (i in 0 until (tasksArr?.size ?: 0)) {
            val o = tasksArr!![i].jsonObject
            if (o["isDone"]?.jsonPrimitive?.booleanOrNull == true) completed++
            if (o.containsKey("trashedAt") && o["trashedAt"] !is JsonNull) trashedTasks++
            attachments += Attachments.parse(o["attachments"]?.jsonPrimitive?.content ?: "[]").size
        }


        val convList = root["conversations"]?.jsonArray?.let { arr ->
            (0 until arr.size).mapNotNull { i ->
                val o = arr[i].jsonObject
                val id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L
                if (id == 0L) null else id to (o["title"]?.jsonPrimitive?.content ?: "")
            }
        } ?: emptyList()
        val profileNames = root["settings"]?.jsonObject?.get("apiProfiles")?.jsonPrimitive?.content?.let { pj ->
            if (pj.isBlank()) emptyList() else ApiProfiles.parse(pj).map { it.name }
        } ?: emptyList()

        return BackupPreview(
            manifestJson = manifestJson,
            formatVersion = root["version"]?.jsonPrimitive?.intOrNull ?: 0,
            exportedAt = (root["exportedAt"]?.jsonPrimitive?.longOrNull ?: 0L).takeIf { it > 0 },
            encrypted = true,
            passwordProtected = needsPassword,
            notes = notesArr?.size ?: 0,
            archivedNotes = archived,
            trashedNotes = trashedNotes,
            tasks = tasksArr?.size ?: 0,
            completedTasks = completed,
            trashedTasks = trashedTasks,
            noteVersions = root["noteVersions"]?.jsonArray?.size ?: 0,
            conversations = root["conversations"]?.jsonArray?.size ?: 0,
            chatMessages = root["chats"]?.jsonArray?.size ?: 0,
            attachments = attachments,
            hasSettings = root["settings"]?.jsonObject != null,
            modules = root["modules"]?.jsonArray?.let { arr ->
                (0 until arr.size).mapNotNull { i ->
                    runCatching { BackupModule.valueOf(arr[i].jsonPrimitive.content) }.getOrNull()
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

    suspend fun inspect(context: PlatformContext, bytes: ByteArray, password: String? = null): BackupPreview =
        inspect(context, BackupSource { okio.Buffer().write(bytes) }, password)

    suspend fun commit(
        context: PlatformContext,
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
                var plain: okio.Source? = null
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
        context: PlatformContext,
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

private fun okio.Source.read(b: ByteArray, off: Int, len: Int): Int {
    val buf = okio.Buffer()
    val n = this.read(buf, len.toLong())
    if (n == -1L) return -1
    buf.read(b, off, n.toInt())
    return n.toInt()
}

private fun okio.Source.read(b: ByteArray): Int = read(b, 0, b.size)

private fun okio.Source.read(): Int {
    val buf = okio.Buffer()
    val n = this.read(buf, 1)
    if (n == -1L) return -1
    return buf.readByte().toInt() and 0xFF
}

private fun okio.Sink.write(b: Int) {
    val buf = okio.Buffer()
    buf.writeByte(b)
    this.write(buf, 1)
}

private fun okio.Sink.write(b: ByteArray, off: Int, len: Int) {
    val buf = okio.Buffer()
    buf.write(b, off, len)
    this.write(buf, len.toLong())
}

private fun okio.Sink.write(b: ByteArray) = write(b, 0, b.size)
