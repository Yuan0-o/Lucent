package com.lucent.app.data

import org.json.JSONArray

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

    companion object {
        fun parse(json: String?): List<Attachment> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    Attachment(
                        mime = o.optString("mime", "application/octet-stream"),
                        data = o.optString("data", ""),
                        name = o.optString("name", "file")
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun estimateDecodedBase64Size(base64: String): Long {
            if (base64.isEmpty()) return 0
            val padding = when {
                base64.endsWith("==") -> 2
                base64.endsWith("=") -> 1
                else -> 0
            }
            return (base64.length.toLong() * 3 / 4) - padding
        }
    }
}
