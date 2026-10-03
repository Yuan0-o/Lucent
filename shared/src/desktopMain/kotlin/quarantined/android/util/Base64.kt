package android.util

object Base64 {
    const val DEFAULT = 0
    const val NO_WRAP = 2

    fun encodeToString(input: ByteArray, flags: Int): String =
        java.util.Base64.getEncoder().encodeToString(input)

    fun decode(str: String, flags: Int): ByteArray {
        val cleaned = str.trim()
        return try {
            java.util.Base64.getDecoder().decode(cleaned)
        } catch (t: IllegalArgumentException) {
            java.util.Base64.getMimeDecoder().decode(cleaned)
        }
    }
}
