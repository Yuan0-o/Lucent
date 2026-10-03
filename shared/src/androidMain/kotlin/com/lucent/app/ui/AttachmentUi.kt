package com.lucent.app.ui

import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentStore
import com.lucent.app.platform.PlatformContext

actual fun platformFileToAttachment(context: PlatformContext, source: PlatformPickedFile): Attachment? {
    val resolver = context.contentResolver
    val mime = resolver.getType(source) ?: "application/octet-stream"
    val name = AttachmentStore.queryDisplayName(context, source) ?: "file"
    val id = AttachmentStore.importUri(context, source) ?: return null
    return Attachment(mime = mime, data = id, name = name)
}
