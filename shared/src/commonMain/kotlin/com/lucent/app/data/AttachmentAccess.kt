package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import java.io.File
import java.io.OutputStream

expect object AttachmentAccess {
    fun materialize(context: PlatformContext, att: Attachment): File?
    fun writeTo(context: PlatformContext, att: Attachment, out: OutputStream): Boolean
    fun clearPreviewCache(context: PlatformContext)
}
