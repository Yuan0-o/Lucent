package com.lucent.app.harness

import com.lucent.app.harness.plugins.PluginSource
import com.lucent.app.harness.plugins.PluginSpec
import com.lucent.app.harness.terminal.PtyBackend


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
    fun defaultWorkspace(): String
    fun filesDir(): String
    fun cacheDir(): String
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
    fun pluginRoot(): String = systemHarnessFs().join(filesDir(), "plugins")
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

    @HarnessVolatile var android: Boolean = false
    @HarnessVolatile var host: HarnessHost? = null
    @HarnessVolatile var shell: HarnessShell? = null
    @HarnessVolatile var pluginHost: PluginHost? = null
    @HarnessVolatile var terminalBackend: PtyBackend? = null
    @HarnessVolatile var llm: SubAgentLlm? = null
    @HarnessVolatile var conversationId: Long = 0L
    @HarnessVolatile var noteSink: ((String) -> Unit)? = null

    fun note(text: String) {
        if (text.isBlank()) return
        try {
            noteSink?.invoke(text)
        } catch (_: Throwable) {
        }
    }

    @HarnessVolatile private var current: HarnessConfig = HarnessConfig.DEFAULT

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

    fun background(): kotlinx.coroutines.CoroutineScope = harnessBackgroundScope()

    fun workspace(): String {
        val configured = current.workspace.trim()
        val path = if (configured.isNotEmpty()) configured else defaultWorkspace()
        val fs = systemHarnessFs()
        if (!fs.exists(path)) fs.mkdirs(path)
        return path
    }

    fun subDir(name: String): String {
        val fs = systemHarnessFs()
        val path = home() + fs.separator + name
        if (!fs.exists(path)) fs.mkdirs(path)
        return path
    }

    fun downloadsDir(): String {
        val fs = systemHarnessFs()
        val path = workspace() + fs.separator + ".lucent" + fs.separator + "downloads"
        if (!fs.exists(path)) fs.mkdirs(path)
        return path
    }

    fun defaultWorkspace(): String {
        val h = host
        if (h != null) return h.defaultWorkspace()
        return harnessUserHome() + systemHarnessFs().separator + "Lucent"
    }

    fun filesDir(): String {
        val h = host
        if (h != null) return h.filesDir()
        return harnessUserHome() + systemHarnessFs().separator + ".lucent"
    }

    fun cacheDir(): String {
        val h = host
        if (h != null) return h.cacheDir()
        return harnessTempDir() + systemHarnessFs().separator + "lucent"
    }

    fun home(): String {
        val fs = systemHarnessFs()
        val path = filesDir() + fs.separator + "harness"
        if (!fs.exists(path)) fs.mkdirs(path)
        return path
    }
}
