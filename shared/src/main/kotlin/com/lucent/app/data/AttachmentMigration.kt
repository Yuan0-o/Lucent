package com.lucent.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.first

object AttachmentMigration {

    suspend fun runIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val settings = SettingsRepository(appContext)
        if (settings.attachmentsMigrated.first()) {
            pruneOrphans(appContext)
            return
        }

        val db = AppDatabase.getInstance(appContext)
        val notes = db.noteDao().getAllOnce()
        val tasks = db.taskDao().getAllOnce()

        var anyRemaining = false
        notes.forEach { note ->
            val (newJson, remaining) = migrateAttachmentsJson(appContext, note.attachments)
            if (newJson != note.attachments) {
                db.noteDao().update(note.copy(attachments = newJson))
            }
            if (remaining) anyRemaining = true
        }
        tasks.forEach { task ->
            val (newJson, remaining) = migrateAttachmentsJson(appContext, task.attachments)
            if (newJson != task.attachments) {
                db.taskDao().update(task.copy(attachments = newJson))
            }
            if (remaining) anyRemaining = true
        }

        pruneOrphans(appContext)

        if (!anyRemaining) settings.setAttachmentsMigrated(true)
    }

    private fun migrateAttachmentsJson(
        context: Context,
        attachmentsJson: String
    ): Pair<String, Boolean> {
        val list = Attachments.parse(attachmentsJson)
        if (list.isEmpty()) return attachmentsJson to false

        var changed = false
        var remaining = false
        val migrated = list.map { att ->
            if (AttachmentStore.looksLikeId(att.data)) return@map att
            val bytes = try {
                Base64.decode(att.data, Base64.DEFAULT)
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

    suspend fun encryptExistingAttachments(context: Context) {
        val appContext = context.applicationContext
        val db = AppDatabase.getInstance(appContext)
        val referenced = buildSet {
            db.noteDao().getAllOnce().forEach { addAll(Attachments.idsFromJson(it.attachments)) }
            db.taskDao().getAllOnce().forEach { addAll(Attachments.idsFromJson(it.attachments)) }
        }
        referenced.forEach { id ->
            try {
                AttachmentStore.encryptExistingFile(appContext, id)
            } catch (t: Throwable) {
            }
        }
    }

    suspend fun pruneOrphans(context: Context) {
        val db = AppDatabase.getInstance(context)
        val notes = db.noteDao().getAllOnce()
        val tasks = db.taskDao().getAllOnce()
        val referenced = buildSet {
            notes.forEach { addAll(Attachments.idsFromJson(it.attachments)) }
            tasks.forEach { addAll(Attachments.idsFromJson(it.attachments)) }
        }
        AttachmentStore.pruneOrphans(context, referenced)
    }
}
