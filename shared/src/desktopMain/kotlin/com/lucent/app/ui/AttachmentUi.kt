package com.lucent.app.ui

import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentLimits
import com.lucent.app.data.AttachmentStore
import com.lucent.app.platform.PlatformContext
import java.io.File

fun fileToAttachment(context: PlatformContext, file: File): Attachment? {
    if (!file.exists() || !file.isFile) return null
    if (file.length() > AttachmentLimits.MAX_SINGLE_BYTES) return null
    val id = AttachmentStore.importFile(context, file) ?: return null
    return Attachment(
        mime = mimeForFileName(file.name),
        data = id,
        name = file.name.ifBlank { "file" }
    )
}
