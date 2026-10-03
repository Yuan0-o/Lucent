package com.lucent.app.data

data class Attachment(
    val mime: String,
    val data: String,
    val name: String
) {
    val isImage: Boolean get() = mime.startsWith("image/")

    val isVideo: Boolean get() = mime.startsWith("video/")

    val isAudio: Boolean get() = mime.startsWith("audio/")

    val isPdf: Boolean get() = mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)

    val isInlineViewable: Boolean get() = isImage || isVideo || isAudio
}
