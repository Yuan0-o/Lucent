package com.lucent.app.harness

import com.lucent.app.harness.plugins.PluginSource
import com.lucent.app.harness.plugins.PluginSpec
import com.lucent.app.harness.terminal.PtyBackend
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope

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
        workdir: String?,
        timeoutSeconds: Int,
        env: Map<String, String>,
        onOutput: ((String) -> Unit)? = null
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
        onProgress: (Float, String) -> Unit,
        onOutput: ((String) -> Unit)? = null
    ): PluginOutcome

    suspend fun remove(plugin: PluginSpec, onOutput: ((String) -> Unit)? = null): PluginOutcome
    suspend fun detect(plugin: PluginSpec): Boolean
    suspend fun runPluginCommand(plugin: PluginSpec, command: String, timeoutSeconds: Int): ShellOutcome
}

enum class PluginFailure { NONE, NO_SHELL, NO_SCRIPT, DOWNLOAD, VERIFY, STORAGE, INSTALL, DETECT, NO_PLATFORM, CANCELLED, REMOVE }

data class PluginOutcome(
    val ok: Boolean,
    val message: String,
    val installedPath: String = "",
    val failure: PluginFailure = PluginFailure.NONE,
    val detail: String = ""
)

interface HarnessHost {
    val android: Boolean
    fun defaultWorkspacePath(): String
    fun filesDirPath(): String
    fun cacheDirPath(): String
    suspend fun probeShell(): ShellOutcome = ShellOutcome(true, "", "", 0)
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
    fun pluginRootPath(): String = systemHarnessFs().join(filesDirPath(), "plugins")
    fun permissionNote(): String = ""
    fun builtinRuntimeState(): String = "unavailable"
    suspend fun extractBuiltinRuntime(onProgress: (String) -> Unit): ShellOutcome =
        ShellOutcome(false, "", "built-in runtime is unavailable on this platform", -1)
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
    const val CAP_BUILTIN_RUNTIME = "builtin_runtime"
    const val CAP_PRIVILEGED = "privileged"
    const val CAP_PLUGINS = "plugins"
    const val CAP_DEVICE = "device"
    const val CAP_PARENT = "parent"

    @Volatile var android: Boolean = false
    @Volatile var host: HarnessHost? = null
    @Volatile var shell: HarnessShell? = null
    @Volatile var pluginHost: PluginHost? = null
    @Volatile var terminalBackend: PtyBackend? = null
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
        val snapshot = withHarnessLock(lock) { listeners.toList() }
        snapshot.forEach { it(config) }
    }

    fun observe(listener: (HarnessConfig) -> Unit) {
        withHarnessLock(lock) { listeners.add(listener) }
    }

    fun unobserve(listener: (HarnessConfig) -> Unit) {
        withHarnessLock(lock) { listeners.remove(listener) }
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

    const val ENV_BUILTIN_ONLY = "LUCENT_BUILTIN_ONLY"

    fun builtinOnlyEnv(): Map<String, String> = mapOf(ENV_BUILTIN_ONLY to "1")

    fun shellReady(): Boolean = shell?.isReady() == true && current.shellEnabled

    fun runShell(
        command: String,
        workdir: String?,
        timeoutSeconds: Int,
        env: Map<String, String> = emptyMap()
    ): ShellOutcome {
        val sh = shell ?: return ShellOutcome(false, "", "No shell backend is available", -1, false)
        if (!sh.isReady()) return ShellOutcome(false, "", sh.describe(), -1, false)
        if (!current.shellEnabled) return ShellOutcome(false, "", SHELL_SWITCHED_OFF, -1, false)
        return harnessRunBlocking { sh.run(command, workdir, timeoutSeconds, env) }
    }

    suspend fun runShellAsync(
        command: String,
        workdir: String?,
        timeoutSeconds: Int,
        env: Map<String, String> = emptyMap(),
        onOutput: ((String) -> Unit)? = null
    ): ShellOutcome {
        val sh = shell ?: return ShellOutcome(false, "", "No shell backend is available", -1, false)
        if (!sh.isReady()) return ShellOutcome(false, "", sh.describe(), -1, false)
        if (!current.shellEnabled) return ShellOutcome(false, "", SHELL_SWITCHED_OFF, -1, false)
        return sh.run(command, workdir, timeoutSeconds, env, onOutput)
    }

    fun downloadsDirPath(): String {
        val fs = systemHarnessFs()
        val path = fs.join(fs.join(workspacePath(), ".lucent"), "downloads")
        fs.mkdirs(path)
        return path
    }

    fun workspacePath(): String {
        val configured = current.workspace.trim()
        val path = if (configured.isNotEmpty()) configured else defaultWorkspacePath()
        systemHarnessFs().mkdirs(path)
        return path
    }

    fun defaultWorkspacePath(): String {
        val h = host
        if (h != null) return h.defaultWorkspacePath()
        return systemHarnessFs().join(harnessUserHome(), "Lucent")
    }

    fun filesDirPath(): String {
        val h = host
        if (h != null) return h.filesDirPath()
        return systemHarnessFs().join(harnessUserHome(), ".lucent")
    }

    fun cacheDirPath(): String {
        val h = host
        if (h != null) return h.cacheDirPath()
        return systemHarnessFs().join(harnessTempDir(), "lucent")
    }

    fun homePath(): String {
        val fs = systemHarnessFs()
        val path = fs.join(filesDirPath(), "harness")
        fs.mkdirs(path)
        return path
    }

    fun subDirPath(name: String): String {
        val fs = systemHarnessFs()
        val path = fs.join(homePath(), name)
        fs.mkdirs(path)
        return path
    }

    fun background(): CoroutineScope = harnessIoScope()
}
