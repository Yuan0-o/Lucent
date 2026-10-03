package com.lucent.app.ui

import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentLimits
import com.lucent.app.data.AttachmentStore
import com.lucent.app.platform.PlatformContext

actual fun platformFileToAttachment(context: PlatformContext, source: PlatformPickedFile): Attachment? {
    if (!source.exists() || !source.isFile) return null
    if (source.length() > AttachmentLimits.MAX_SINGLE_BYTES) return null
    val id = AttachmentStore.importFile(context, source) ?: return null
    return Attachment(
        mime = mimeForFileName(source.name),
        data = id,
        name = source.name.ifBlank { "file" }
    )
}
