package com.lucent.app.harness

import com.lucent.app.data.AppDatabase
import com.lucent.app.network.ToolExecResult
import com.lucent.app.platform.PlatformContext
import kotlinx.serialization.json.JsonObject

data class HarnessCtx(
    val context: PlatformContext,
    val db: AppDatabase?,
    val config: HarnessConfig,
    val capabilities: Set<String>,
    val android: Boolean,
    val workspacePath: String,
    val subAgentId: String? = null
) {
    val workspace: okio.Path get() = workspacePath.toPath()

    fun cap(name: String): Boolean = capabilities.contains(name)

    fun shellReady(): Boolean = cap(HarnessRuntime.CAP_SHELL)

    fun limit(text: String): String {
        val cap = config.maxOutputChars.coerceIn(2000, 200000)
        return if (text.length <= cap) text else text.take(cap) + "\n… output truncated at $cap characters."
    }
}

interface HarnessGroupTools {
    val group: HarnessGroup
    val tools: List<HarnessTool>
    suspend fun execute(ctx: HarnessCtx, name: String, args: JsonObject): ToolExecResult?
    fun canHandle(name: String): Boolean = tools.any { it.name == name }
}
