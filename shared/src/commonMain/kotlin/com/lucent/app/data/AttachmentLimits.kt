package com.lucent.app.data

import com.lucent.app.platform.PlatformContext

object AttachmentLimits {

    const val MAX_SINGLE_BYTES: Long = 600L * 1024 * 1024

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        if (mb >= 1024) return String.format("%.1f GB", mb / 1024)
        return if (mb >= 10) "${mb.toInt()} MB"
        else String.format("%.1f MB", mb)
    }

    fun sizeOf(context: PlatformContext, att: Attachment): Long {
        return if (AttachmentStore.looksLikeId(att.data)) {
            AttachmentStore.sizeOf(context, att.data)
        } else {
            Attachment.estimateDecodedBase64Size(att.data)
        }
    }

    fun sizeOfList(context: PlatformContext, list: List<Attachment>): Long =
        list.sumOf { sizeOf(context, it) }

    fun totalStored(context: PlatformContext, notes: List<Note>, tasks: List<Task>): Long {
        var total = AttachmentStore.totalBytes(context)
        val addLegacy: (String) -> Unit = { json ->
            Attachment.parse(json).forEach { att ->
                if (!AttachmentStore.looksLikeId(att.data)) {
                    total += Attachment.estimateDecodedBase64Size(att.data)
                }
            }
        }
        notes.forEach { addLegacy(it.attachments) }
        tasks.forEach { addLegacy(it.attachments) }
        return total
    }

    data class Check(val allowed: Boolean, val message: String)

    fun checkSingle(incoming: Long): Check {
        return if (incoming <= MAX_SINGLE_BYTES) {
            Check(true, "")
        } else {
            Check(
                false,
                com.lucent.app.i18n.S.attachmentTooLarge(formatBytes(incoming), formatBytes(MAX_SINGLE_BYTES))
            )
        }
    }
}
