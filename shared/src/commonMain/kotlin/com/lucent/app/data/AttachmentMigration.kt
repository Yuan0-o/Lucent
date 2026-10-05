package com.lucent.app.data
import com.lucent.app.platform.applicationContext

import com.lucent.app.data.createSettingsRepository

import com.lucent.app.data.createAppDatabase

import com.lucent.app.platform.PlatformContext
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.first

object AttachmentMigration {

    suspend fun runIfNeeded(context: PlatformContext) {
        val appContext = context.applicationContext
        val settings = createSettingsRepository(appContext)
        if (settings.attachmentsMigrated.first()) {
            pruneOrphans(appContext)
            return
        }

        val db = createAppDatabase(appContext)
        val notes = db.noteDao.getAllOnce()
        val tasks = db.taskDao.getAllOnce()

        var anyRemaining = false
        notes.forEach { note ->
            val (newJson, remaining) = migrateAttachmentsJson(appContext, note.attachments)
            if (newJson != note.attachments) {
                db.noteDao.update(note.copy(attachments = newJson))
            }
            if (remaining) anyRemaining = true
        }
        tasks.forEach { task ->
            val (newJson, remaining) = migrateAttachmentsJson(appContext, task.attachments)
            if (newJson != task.attachments) {
                db.taskDao.update(task.copy(attachments = newJson))
            }
            if (remaining) anyRemaining = true
        }

        pruneOrphans(appContext)

        if (!anyRemaining) settings.setAttachmentsMigrated(true)
    }

    private fun migrateAttachmentsJson(
        context: PlatformContext,
        attachmentsJson: String
    ): Pair<String, Boolean> {
        val list = Attachments.parse(attachmentsJson)
        if (list.isEmpty()) return attachmentsJson to false

        var changed = false
        var remaining = false
        val migrated = list.map { att ->
            if (AttachmentStore.looksLikeId(att.data)) return@map att
            val bytes = try {
                Base64.Mime.decode(att.data)
            } catch (t: Throwable) {
                remaining = true
                return@map att
            }
            val id = AttachmentStore.importBytes(context, bytes)
            if (id == null) {
                remaining = true
                att
            } else {
                changed = true
                att.copy(data = id)
            }
        }
        return (if (changed) Attachments.serialize(migrated) else attachmentsJson) to remaining
    }

    suspend fun encryptExistingAttachments(context: PlatformContext) {
        val appContext = context.applicationContext
        val db = createAppDatabase(appContext)
        val referenced = buildSet {
            db.noteDao.getAllOnce().forEach { addAll(Attachments.idsFromJson(it.attachments)) }
            db.taskDao.getAllOnce().forEach { addAll(Attachments.idsFromJson(it.attachments)) }
            db.notebookDao.getAllIncludingTrashedOnce().mapNotNull { notebook -> notebook.color.takeIf { it.startsWith("photo:") } }
                .forEach { add(it.removePrefix("photo:")) }
        }
        referenced.forEach { id ->
            try {
                AttachmentStore.encryptExistingFile(appContext, id)
            } catch (t: Throwable) {
            }
        }
    }

    suspend fun pruneOrphans(context: PlatformContext) {
        val db = createAppDatabase(context)
        val notes = db.noteDao.getAllOnce()
        val tasks = db.taskDao.getAllOnce()
        val referenced = buildSet {
            notes.forEach { addAll(Attachments.idsFromJson(it.attachments)) }
            tasks.forEach { addAll(Attachments.idsFromJson(it.attachments)) }
            db.notebookDao.getAllIncludingTrashedOnce().mapNotNull { notebook -> notebook.color.takeIf { it.startsWith("photo:") } }
                .forEach { add(it.removePrefix("photo:")) }
        }
        AttachmentStore.pruneOrphans(context, referenced)
    }
}
