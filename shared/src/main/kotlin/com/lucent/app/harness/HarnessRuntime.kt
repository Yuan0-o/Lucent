package com.lucent.app.harness

import com.lucent.app.AppScope
import com.lucent.app.harness.plugins.PluginSource
import com.lucent.app.harness.plugins.PluginSpec
import java.io.File

data class ShellOutcome(
    val ok: Boolean,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val timedOut: Boolean = false
) {
    val text: String
        get() {
            val out = stdout.trimEnd()
            val err = stderr.trimEnd()
            return when {
                out.isEmpty() && err.isEmpty() -> ""
                err.isEmpty() -> out
                out.isEmpty() -> err
                else -> "$out\n$err"
            }
        }
}

interface HarnessShell {
    val id: String
    fun isReady(): Boolean
    fun describe(): String
    suspend fun run(
        command: String,
        workdir: File?,
        timeoutSeconds: Int,
        env: Map<String, String>
    ): ShellOutcome

    fun capabilityNames(): Set<String> = setOf("shell")
}

interface PluginHost {
    val id: String
    fun isReady(): Boolean
    fun describe(): String
    suspend fun available(): List<String>
    suspend fun install(
        plugin: PluginSpec,
        source: PluginSource,
        onProgress: (Float, String) -> Unit
    ): PluginOutcome

    suspend fun remove(plugin: PluginSpec): PluginOutcome
    suspend fun detect(plugin: PluginSpec): Boolean
    suspend fun runPluginCommand(plugin: PluginSpec, command: String, timeoutSeconds: Int): ShellOutcome
}

enum class PluginFailure { NONE, NO_SHELL, NO_SCRIPT, DOWNLOAD, VERIFY, STORAGE, INSTALL, DETECT, NO_PLATFORM }

data class PluginOutcome(
    val ok: Boolean,
    val message: String,
    val installedPath: String = "",
    val failure: PluginFailure = PluginFailure.NONE,
    val detail: String = ""
)

interface HarnessHost {
    val android: Boolean
    fun defaultWorkspace(): File
    fun filesDir(): File
    fun cacheDir(): File
    fun capabilities(): Set<String> = emptySet()
    fun openUrl(url: String): Boolean = false
    fun shareText(text: String, subject: String): Boolean = false
    fun shareFile(path: String, mime: String): Boolean = false
    fun notify(title: String, text: String): Boolean = false
    fun toast(text: String): Boolean = false
    fun vibrate(millis: Long): Boolean = false
    fun readClipboard(): String = ""
    fun writeClipboard(text: String): Boolean = false
    fun screenText(): String = ""
    suspend fun screenshot(): ByteArray? = null
    suspend fun tap(x: Int, y: Int): Boolean = false
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, millis: Int): Boolean = false
    suspend fun typeText(text: String): Boolean = false
    suspend fun pressKey(key: String): Boolean = false
    suspend fun deviceInfo(): String = ""
    suspend fun launchApp(packageName: String): Boolean = false
    suspend fun stopApp(packageName: String): Boolean = false
    suspend fun installedApps(query: String): String = ""
    suspend fun notifications(): String = ""
    suspend fun location(): String = ""
    suspend fun sensors(): String = ""
    fun setTorch(on: Boolean): Boolean = false
    fun exportFile(path: String): Boolean = false
    suspend fun askUser(question: String, options: List<String>): String = ""
    suspend fun importFile(hint: String): String = ""
    fun availableSqlite(): Boolean = false
    suspend fun sqliteQuery(dbPath: String, sql: String, limit: Int): String = ""
    suspend fun sqliteExec(dbPath: String, sql: String): String = ""
    suspend fun renderPdfPage(path: String, page: Int, width: Int): ByteArray? = null
    suspend fun readPdfText(path: String): String = ""
    suspend fun pdfMerge(inputs: List<String>, out: String): Boolean = false
    suspend fun pdfSplit(input: String, out: String, pages: String): Boolean = false
    fun workspaceCandidates(): List<String> = emptyList()
    fun pluginRoot(): File = File(filesDir(), "plugins")
    fun permissionNote(): String = ""
}

interface SubAgentLlm {
    suspend fun step(
        systemPrompt: String,
        userPrompt: String,
        transcript: List<String>,
        allowedTools: Set<String>,
        model: String
    ): SubAgentStep
}

data class SubAgentStep(
    val text: String,
    val toolCalls: List<SubAgentCall> = emptyList(),
    val done: Boolean = true,
    val error: String = ""
)

data class SubAgentCall(val name: String, val argumentsJson: String)

object HarnessRuntime {

    const val CAP_SHELL = "shell"
    const val CAP_TERMUX = "termux"
    const val CAP_PRIVILEGED = "privileged"
    const val CAP_PLUGINS = "plugins"
    const val CAP_DEVICE = "device"
    const val CAP_PARENT = "parent"

    @Volatile var android: Boolean = false
    @Volatile var host: HarnessHost? = null
    @Volatile var shell: HarnessShell? = null
    @Volatile var pluginHost: PluginHost? = null
    @Volatile var llm: SubAgentLlm? = null
    @Volatile var conversationId: Long = 0L
    @Volatile var noteSink: ((String) -> Unit)? = null

    fun note(text: String) {
        if (text.isBlank()) return
        try {
            noteSink?.invoke(text)
        } catch (_: Throwable) {
        }
    }

    @Volatile private var current: HarnessConfig = HarnessConfig.DEFAULT

    private val listeners = mutableListOf<(HarnessConfig) -> Unit>()
    private val lock = Any()

    fun install(config: HarnessConfig) {
        current = config
    }

    fun config(): HarnessConfig = current

    fun update(config: HarnessConfig) {
        current = config
        val snapshot = synchronized(lock) { listeners.toList() }
        snapshot.forEach { it(config) }
    }

    fun observe(listener: (HarnessConfig) -> Unit) {
        synchronized(lock) { listeners.add(listener) }
    }

    fun capabilities(): Set<String> {
        val out = mutableSetOf<String>()
        val h = host
        if (h != null) out.addAll(h.capabilities())
        if (android) out.add(CAP_PARENT)
        val sh = shell
        if (sh != null && sh.isReady()) {
            out.add(CAP_SHELL)
            out.addAll(sh.capabilityNames())
        }
        val ph = pluginHost
        if (ph != null && ph.isReady()) {
            out.add(CAP_PLUGINS)
            out.addAll(current.installedPlugins())
        }
        return out
    }

    const val SHELL_SWITCHED_OFF = "The shell is switched off in Settings"

    fun shellReady(): Boolean = shell?.isReady() == true && current.shellEnabled

    fun runShell(
        command: String,
        workdir: File?,
        timeoutSeconds: Int,
        env: Map<String, String> = emptyMap()
    ): ShellOutcome {
        val sh = shell ?: return ShellOutcome(false, "", "No shell backend is available", -1, false)
        if (!sh.isReady()) return ShellOutcome(false, "", sh.describe(), -1, false)
        if (!current.shellEnabled) return ShellOutcome(false, "", SHELL_SWITCHED_OFF, -1, false)
        return kotlinx.coroutines.runBlocking { sh.run(command, workdir, timeoutSeconds, env) }
    }

    suspend fun runShellAsync(
        command: String,
        workdir: File?,
        timeoutSeconds: Int,
        env: Map<String, String> = emptyMap()
    ): ShellOutcome {
        val sh = shell ?: return ShellOutcome(false, "", "No shell backend is available", -1, false)
        if (!sh.isReady()) return ShellOutcome(false, "", sh.describe(), -1, false)
        if (!current.shellEnabled) return ShellOutcome(false, "", SHELL_SWITCHED_OFF, -1, false)
        return sh.run(command, workdir, timeoutSeconds, env)
    }

    fun downloadsDir(): File {
        val dir = File(workspace(), ".lucent/downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun workspace(): File {
        val configured = current.workspace.trim()
        val base = if (configured.isNotEmpty()) File(configured) else defaultWorkspace()
        if (!base.exists()) base.mkdirs()
        return base
    }

    fun defaultWorkspace(): File = host?.defaultWorkspace() ?: File(System.getProperty("user.home"), "Lucent")

    fun filesDir(): File = host?.filesDir() ?: File(System.getProperty("user.home"), ".lucent")

    fun cacheDir(): File = host?.cacheDir() ?: File(System.getProperty("java.io.tmpdir"), "lucent")

    fun home(): File = File(filesDir(), "harness").apply { mkdirs() }

    fun subDir(name: String): File = File(home(), name).apply { mkdirs() }

    fun background(): kotlinx.coroutines.CoroutineScope = AppScope.io
}
