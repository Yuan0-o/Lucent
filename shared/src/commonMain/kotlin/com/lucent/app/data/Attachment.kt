package com.lucent.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Attachment(
    val mime: String = "application/octet-stream",
    val data: String = "",
    val name: String = "file"
) {
    val isImage: Boolean get() = mime.startsWith("image/")

    val isVideo: Boolean get() = mime.startsWith("video/")

    val isAudio: Boolean get() = mime.startsWith("audio/")

    val isPdf: Boolean get() = mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)

    val isInlineViewable: Boolean get() = isImage || isVideo || isAudio

    companion object {
        private val jsonParser = Json { ignoreUnknownKeys = true }

        fun parse(json: String?): List<Attachment> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                jsonParser.decodeFromString(json)
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
