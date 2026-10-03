package com.lucent.app.ui

import android.net.Uri
import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentStore
import com.lucent.app.platform.PlatformContext

fun uriToAttachment(context: PlatformContext, uri: Uri): Attachment? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val name = AttachmentStore.queryDisplayName(context, uri) ?: "file"
    val id = AttachmentStore.importUri(context, uri) ?: return null
    return Attachment(mime = mime, data = id, name = name)
}
