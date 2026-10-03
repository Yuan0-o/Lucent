package com.lucent.app.data

import com.lucent.app.PlatformFile
import com.lucent.app.PlatformOutputStream
import com.lucent.app.platform.PlatformContext

expect object AttachmentAccess {
    fun materialize(context: PlatformContext, att: Attachment): PlatformFile?
    fun writeTo(context: PlatformContext, att: Attachment, out: PlatformOutputStream): Boolean
    fun clearPreviewCache(context: PlatformContext)
}
