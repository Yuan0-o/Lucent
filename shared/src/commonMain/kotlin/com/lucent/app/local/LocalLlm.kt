package com.lucent.app.local

import com.lucent.app.platform.PlatformContext

expect object LocalLlm {
    val N_CTX: Int
    val MAX_NEW_TOKENS: Int
    val HISTORY_TURNS: Int
    fun isSupported(): Boolean
    fun supportsVision(): Boolean
    fun setGpuEnabled(enabled: Boolean)
    fun isLoaded(): Boolean
    fun isLoading(): Boolean
    fun isGenerating(): Boolean
    suspend fun ensureLoaded(context: PlatformContext): Boolean
    suspend fun generate(
        messages: List<Pair<String, String>>,
        images: List<ByteArray> = emptyList(),
        onDelta: (String) -> Unit
    ): Int
    fun stop()
    fun shutdown()
}
