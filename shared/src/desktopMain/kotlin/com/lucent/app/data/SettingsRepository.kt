@file:JvmName("DesktopSettingsRepositoryKt")
package com.lucent.app.data
import okio.Path.Companion.toPath
import com.lucent.app.platform.getFilesDir
import com.lucent.app.platform.applicationContext
import com.lucent.app.platform.filesDir

import com.lucent.app.platform.PlatformContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File

class DesktopSettingsRepository(private val context: PlatformContext) : SettingsRepository {

    private object K {
        const val THEME_MODE = "theme_mode"
        const val PALETTE = "palette"
        const val FONT = "font"
        const val DYNAMIC_COLOR_ENABLED = "dynamic_color_enabled"
        const val BASE_URL_ENC = "base_url_enc"
        const val API_SPEC_ENC = "api_spec_enc"
        const val MODEL_ENC = "model_enc"
        const val ASSISTANT_NAME_ENC = "assistant_name_enc"
        const val ASSISTANT_STYLE_ENC = "assistant_style_enc"
        const val ATTACHMENTS_MIGRATED = "attachments_migrated_v1"
        const val API_KEY_ENC = "api_key_enc"
        const val API_PROFILES_ENC = "api_profiles_json_enc"
        const val BACKUP_PASSWORD_ENC = "backup_password_enc"
        const val AUTO_BACKUP_PASSWORDS_ENC = "auto_backup_passwords_enc"
        const val AUTO_BACKUP_RECOVERY_Q = "auto_backup_recovery_q"
        const val AUTO_BACKUP_RECOVERY_A_ENC = "auto_backup_recovery_a_enc"
        const val API_PROFILE_SELECTED = "api_profile_selected"
        const val NOTES_SORT = "notes_sort"
        const val AUTO_BACKUP = "auto_backup_state"
        const val NOTE_HISTORY_ENABLED = "note_history_enabled"
        const val TASK_HISTORY_ENABLED = "task_history_enabled"
        const val TASKS_SORT = "tasks_sort"
        const val NOTEBOOKS_SORT = "notebooks_sort"
        const val NOTEBOOK_OPENS_ENC = "notebook_opens_enc"
        const val MEMORY_TIER = "memory_tier"
        const val WEB_SEARCH_ENABLED = "web_search_enabled"
        const val ASSISTANT_CONFIRM_TOOLS = "assistant_confirm_tools"
        const val TYPING_HAPTICS = "typing_haptics"
        const val MODEL_RECENTS = "model_recents"
        const val SMALL_MODEL_MODE = "small_model_mode"

        const val LAST_SCREEN = "last_screen"
        const val SESSION_SNAPSHOT = "session_snapshot"
        const val BLACKOUT_ENABLED = "blackout_enabled"
        const val APP_LOCK_PREBLACKOUT = "app_lock_preblackout"
        const val SYSTEM_INTEGRATION_PREBLACKOUT = "system_integration_preblackout"
        const val CRASH_SHIELD_ENABLED = "crash_shield_enabled"
        const val PW_ATTEMPT_STATE = "pw_attempt_state"
        const val PW_FIRST_ROUND_LIMIT = "pw_first_round_limit"
        const val PW_LATER_ROUND_LIMIT = "pw_later_round_limit"
        const val PW_SELF_DESTRUCT_ENABLED = "pw_self_destruct_enabled"
        const val PW_SELF_DESTRUCT_THRESHOLD = "pw_self_destruct_threshold"
        const val OPEN_LINKS_EXTERNALLY = "open_links_externally"
        const val MARKDOWN_ENABLED = "markdown_enabled"
        const val RICH_TEXT_ENABLED = "rich_text_enabled"
        const val CLOSE_TO_TRAY = "close_to_tray"
        const val SAVED_SEARCHES = "saved_searches"
        const val CUSTOM_TEMPLATES = "custom_templates"
        const val TEMPLATE_DRAFT = "template_draft"
        const val HIDDEN_TEMPLATES = "hidden_templates"
        const val CLOUD_ENABLED = "cloud_enabled"
        const val CLOUD_PROVIDER = "cloud_provider"
        const val CLOUD_URL = "cloud_url"
        const val CLOUD_USER = "cloud_user"
        const val CLOUD_PASSWORD_ENC = "cloud_password_enc"
        const val EMBEDDING_PROVIDER = "embedding_provider"
        const val CLOUD_FOLDER = "cloud_folder"
        const val CLOUD_AUTO_BACKUP = "cloud_auto_backup"
        const val LINKS_ENABLED = "links_enabled"
        const val BACKGROUND_ANIMATION_ENABLED = "background_animation_enabled"
        const val SPLASH_ENABLED = "splash_enabled"
        const val SPLASH_STYLE = "splash_style"
        const val APP_LOCK_ENABLED = "app_lock_enabled"
        const val APP_LOCK_CREDENTIALS_ENC = "app_lock_credentials_enc"
        const val APP_LOCK_HELLO_ENABLED = "app_lock_hello_enabled"
        const val SYSTEM_INTEGRATION_ENABLED = "system_integration_enabled"
        const val STARTUP_LOGGING_ENABLED = "startup_logging_enabled"
        const val APP_LANGUAGE = "app_language"
        const val LOCAL_MODEL_ENABLED = "local_model_enabled"
        const val LOCAL_TOOLS_ENABLED = "local_tools_enabled"
        const val LOCAL_GPU_ENABLED = "local_gpu_enabled"
        const val LOCAL_BACKGROUND_REPLY = "local_background_reply"
        const val MEMORY_TIER_LOCAL = "memory_tier_local"
        const val AGENT_MODE = "cloud_agent_mode"
        const val REASONING_EFFORT = "reasoning_effort"
        const val WEB_SEARCH_ENGINE = "web_search_engine"
        const val MEMORY_TIER_PRELOCAL = "memory_tier_prelocal"
        const val WEB_SEARCH_PRELOCAL = "web_search_prelocal"
        const val UPDATE_CHANNEL_ENC = "update_channel_enc"
        const val INSTALLED_PREVIEW_IDENTITY = "installed_preview_identity"
        const val STAGED_UPDATE_IDENTITY = "staged_update_identity"
        const val AUTO_UPDATE_ENABLED = "auto_update_enabled"
        const val PENDING_UPDATE_VERSION = "pending_update_version"
        const val STAGED_UPDATE_TAG = "staged_update_tag"
        const val STAGED_UPDATE_FILES = "staged_update_files"
        const val PRIVILEGED_ENABLED = "privileged_enabled"
        const val HARNESS_CONFIG_ENC = "harness_config_enc"

        const val TERMINAL_FONT_SIZE_ENC = "terminal_font_size_enc"
        const val TERMINAL_KEY_BAR_VISIBLE_ENC = "terminal_key_bar_visible_enc"
        const val GLOBAL_TEXT_SELECTION_ENABLED_ENC = "global_text_selection_enabled_enc"
    }

    private val file: File get() = java.io.File(context.applicationContext.getFilesDir().toString(), "lucent_settings.json")

    companion object {
        private val state = MutableStateFlow<Map<String, Any>>(emptyMap())
        private val writeMutex = Mutex()
        @Volatile private var loadedFrom: String? = null
    }

    init {
        ensureLoaded()
    }

    private fun ensureLoaded() {
        val path = file.toString()
        if (loadedFrom == path) return
        synchronized(DesktopSettingsRepository::class.java) {
            if (loadedFrom == path) return
            state.value = readFile()
            loadedFrom = path
        }
    }

    private fun readFile(): Map<String, Any> = try {
        if (!okio.FileSystem.SYSTEM.exists(file.toString().toPath())) emptyMap() else {
            val obj = JSONObject(file.readText())
            buildMap {
                obj.keys().forEach { k ->
                    when (val v = obj.get(k)) {
                        is Boolean, is Int, is String -> put(k, v)
                        is Number -> put(k, v.toInt())
                    }
                }
            }
        }
    } catch (t: Throwable) {
        emptyMap()
    }

    private fun writeFile(values: Map<String, Any>) {
        try {
            val obj = JSONObject()
            values.forEach { (k, v) -> obj.put(k, v) }
            file.parentFile?.mkdirs()
            val tmp = File(file.parent, file.name + ".tmp")
            tmp.writeText(obj.toString(2))
            if (!tmp.renameTo(file)) {
                try { okio.FileSystem.SYSTEM.delete(file.toString().toPath()) } catch (e: Exception) {}
                tmp.renameTo(file)
            }
        } catch (t: Throwable) {
            StartupLog.event(context, "settings: write failed: ${t.message}")
        }
    }

    private suspend fun edit(mutate: (MutableMap<String, Any>) -> Unit) {
        writeMutex.withLock {
            val next = state.value.toMutableMap()
            mutate(next)
            state.value = next
            writeFile(next)
        }
    }

    private fun str(prefs: Map<String, Any>, key: String): String? = prefs[key] as? String
    private fun bool(prefs: Map<String, Any>, key: String): Boolean? = prefs[key] as? Boolean
    private fun int(prefs: Map<String, Any>, key: String): Int? = (prefs[key] as? Number)?.toInt()

    private fun secret(prefs: Map<String, Any>, key: String, default: String): String {
        val stored = str(prefs, key) ?: return default
        return LocalSecrets.decrypt(stored).ifEmpty { default }
    }


    override val baseUrl: Flow<String> = state.map { secret(it, K.BASE_URL_ENC, "") }
    override val apiSpec: Flow<String> = state.map { secret(it, K.API_SPEC_ENC, "openai") }
    override val model: Flow<String> = state.map { secret(it, K.MODEL_ENC, "") }
    override val assistantName: Flow<String> = state.map { secret(it, K.ASSISTANT_NAME_ENC, "Lucent") }
    override val assistantStyle: Flow<String> = state.map { secret(it, K.ASSISTANT_STYLE_ENC, "") }


    override val themeMode: Flow<String> = state.map { str(it, K.THEME_MODE) ?: "system" }
    override val palette: Flow<String> = state.map { str(it, K.PALETTE) ?: "CYCLE" }
    override val font: Flow<String> = state.map { str(it, K.FONT) ?: "system" }

    override val dynamicColorEnabled: Flow<Boolean> =
        state.map { bool(it, K.DYNAMIC_COLOR_ENABLED) ?: false }

    override suspend fun displayPrefsOnce(): SettingsRepository.DisplayPrefs {
        val prefs = state.first()
        return SettingsRepository.DisplayPrefs(
            themeMode = str(prefs, K.THEME_MODE) ?: "system",
            palette = str(prefs, K.PALETTE) ?: "CYCLE",
            font = str(prefs, K.FONT) ?: "system"
        )
    }

    override suspend fun startupPrefsOnce(): SettingsRepository.StartupPrefs {
        val prefs = state.first()
        return SettingsRepository.StartupPrefs(
            display = SettingsRepository.DisplayPrefs(
                themeMode = str(prefs, K.THEME_MODE) ?: "system",
                palette = str(prefs, K.PALETTE) ?: "CYCLE",
                font = str(prefs, K.FONT) ?: "system"
            ),
            appLockEnabled = bool(prefs, K.APP_LOCK_ENABLED) ?: false,
            startupLoggingEnabled = bool(prefs, K.STARTUP_LOGGING_ENABLED) ?: false,
            systemIntegrationEnabled = bool(prefs, K.SYSTEM_INTEGRATION_ENABLED) ?: false,
            appLanguage = str(prefs, K.APP_LANGUAGE) ?: "system",
            assistantName = secret(prefs, K.ASSISTANT_NAME_ENC, "Lucent"),
            backgroundAnimationEnabled = bool(prefs, K.BACKGROUND_ANIMATION_ENABLED) ?: false,
            splashEnabled = bool(prefs, K.SPLASH_ENABLED) ?: true,
            splashStyle = str(prefs, K.SPLASH_STYLE) ?: SplashStyle.DEFAULT.key,
            autoBackup = AutoBackup.State.fromJson(str(prefs, K.AUTO_BACKUP) ?: ""),
            dynamicColor = bool(prefs, K.DYNAMIC_COLOR_ENABLED) ?: false,
            notesSort = str(prefs, K.NOTES_SORT) ?: "recent",
            tasksSort = str(prefs, K.TASKS_SORT) ?: "recent",
            notebooksSort = str(prefs, K.NOTEBOOKS_SORT) ?: "recent",
            sessionSnapshot = str(prefs, K.SESSION_SNAPSHOT) ?: "",
            assistantStyle = secret(prefs, K.ASSISTANT_STYLE_ENC, ""),
            baseUrl = secret(prefs, K.BASE_URL_ENC, ""),
            apiSpec = secret(prefs, K.API_SPEC_ENC, "openai"),
            apiKey = secret(prefs, K.API_KEY_ENC, ""),
            model = secret(prefs, K.MODEL_ENC, ""),
            apiProfilesJson = secret(prefs, K.API_PROFILES_ENC, ""),
            apiProfileSelected = int(prefs, K.API_PROFILE_SELECTED) ?: 0,
            noteHistoryEnabled = bool(prefs, K.NOTE_HISTORY_ENABLED) ?: true,
            taskHistoryEnabled = bool(prefs, K.TASK_HISTORY_ENABLED) ?: true,
            crashShieldEnabled = bool(prefs, K.CRASH_SHIELD_ENABLED) ?: false,
            blackoutEnabled = bool(prefs, K.BLACKOUT_ENABLED) ?: false,
            pwSelfDestructEnabled = bool(prefs, K.PW_SELF_DESTRUCT_ENABLED) ?: false,
            pwFirstRoundLimit = int(prefs, K.PW_FIRST_ROUND_LIMIT)
                ?: PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT,
            pwLaterRoundLimit = int(prefs, K.PW_LATER_ROUND_LIMIT)
                ?: PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT,
            pwSelfDestructThreshold = int(prefs, K.PW_SELF_DESTRUCT_THRESHOLD)
                ?: PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD,
            passwordAttemptState = str(prefs, K.PW_ATTEMPT_STATE) ?: "",
            appLockBiometricEnabled = false,
            appLockHelloEnabled = bool(prefs, K.APP_LOCK_HELLO_ENABLED) ?: false,
            closeToTray = bool(prefs, K.CLOSE_TO_TRAY) ?: true,
            openLinksExternally = bool(prefs, K.OPEN_LINKS_EXTERNALLY) ?: false,
            markdownEnabled = bool(prefs, K.MARKDOWN_ENABLED) ?: false,
            richTextEnabled = bool(prefs, K.RICH_TEXT_ENABLED) ?: false,
            linksEnabled = bool(prefs, K.LINKS_ENABLED) ?: false,
            typingHapticsEnabled = bool(prefs, K.TYPING_HAPTICS) ?: true,
            assistantConfirmToolsEnabled = bool(prefs, K.ASSISTANT_CONFIRM_TOOLS) ?: true,
            localModelEnabled = bool(prefs, K.LOCAL_MODEL_ENABLED) ?: false,
            localToolsEnabled = bool(prefs, K.LOCAL_TOOLS_ENABLED) ?: false,
            localGpuEnabled = bool(prefs, K.LOCAL_GPU_ENABLED) ?: false,
            localBackgroundReplyEnabled = bool(prefs, K.LOCAL_BACKGROUND_REPLY) ?: false,
            agentMode = bool(prefs, K.AGENT_MODE) ?: true,
            reasoning = str(prefs, K.REASONING_EFFORT) ?: ReasoningEffort.DEFAULT.key,
            webSearchEngine = str(prefs, K.WEB_SEARCH_ENGINE) ?: WebSearchEngine.DEFAULT.key,
            smallModelModeEnabled = bool(prefs, K.SMALL_MODEL_MODE) ?: false,
            webSearchEnabled = bool(prefs, K.WEB_SEARCH_ENABLED) ?: false,
            memoryTier = str(prefs, K.MEMORY_TIER) ?: MemoryTier.DEFAULT.key,
            memoryTierLocal = str(prefs, K.MEMORY_TIER_LOCAL) ?: MemoryTier.LOW.key,
            embeddingProvider = str(prefs, K.EMBEDDING_PROVIDER) ?: "local",
            cloudEnabled = bool(prefs, K.CLOUD_ENABLED) ?: false,
            cloudProvider = str(prefs, K.CLOUD_PROVIDER) ?: "Nutstore",
            cloudUrl = str(prefs, K.CLOUD_URL) ?: "",
            cloudUser = str(prefs, K.CLOUD_USER) ?: "",
            cloudFolder = str(prefs, K.CLOUD_FOLDER) ?: "Lucent",
            cloudAutoBackup = bool(prefs, K.CLOUD_AUTO_BACKUP) ?: false,
            terminalFontSize = secret(prefs, K.TERMINAL_FONT_SIZE_ENC, "").toFloatOrNull(),
            terminalKeyBarVisible = secret(prefs, K.TERMINAL_KEY_BAR_VISIBLE_ENC, "true").toBooleanStrictOrNull() ?: true,
            globalTextSelectionEnabled = secret(prefs, K.GLOBAL_TEXT_SELECTION_ENABLED_ENC, "false").toBooleanStrictOrNull() ?: false,
            cloudPasswordEnc = str(prefs, K.CLOUD_PASSWORD_ENC) ?: "",
            updateChannel = secret(prefs, K.UPDATE_CHANNEL_ENC, "stable"),
            installedPreviewIdentity = str(prefs, K.INSTALLED_PREVIEW_IDENTITY) ?: "",
            stagedUpdateIdentity = str(prefs, K.STAGED_UPDATE_IDENTITY) ?: "",
            autoUpdateEnabled = bool(prefs, K.AUTO_UPDATE_ENABLED) ?: false,
            privilegedEnabled = bool(prefs, K.PRIVILEGED_ENABLED) ?: false,
            pendingUpdateVersion = str(prefs, K.PENDING_UPDATE_VERSION) ?: "",
            stagedUpdateTag = str(prefs, K.STAGED_UPDATE_TAG) ?: "",
            stagedUpdateFiles = str(prefs, K.STAGED_UPDATE_FILES) ?: ""
        )
    }


    override val appLanguage: Flow<String> = state.map { str(it, K.APP_LANGUAGE) ?: "system" }
    override suspend fun setAppLanguage(value: String) {
        edit { it[K.APP_LANGUAGE] = value }
        SettingsCache.appLanguage = value
    }
    override suspend fun appLanguageOnce(): String = str(state.first(), K.APP_LANGUAGE) ?: "system"


    override val localModelEnabled: Flow<Boolean> = state.map { bool(it, K.LOCAL_MODEL_ENABLED) ?: false }

    override suspend fun setLocalModelEnabled(value: Boolean) {
        edit { it[K.LOCAL_MODEL_ENABLED] = value }
        SettingsCache.localModelEnabled = value
    }

    override val memoryTierLocal: Flow<String> = state.map { str(it, K.MEMORY_TIER_LOCAL) ?: MemoryTier.LOW.key }
    override suspend fun setMemoryTierLocal(value: String) {
        edit { it[K.MEMORY_TIER_LOCAL] = value }
        SettingsCache.memoryTierLocal = value
    }

    override val agentMode: Flow<Boolean> = state.map { bool(it, K.AGENT_MODE) ?: true }
    override suspend fun setAgentMode(value: Boolean) {
        edit { it[K.AGENT_MODE] = value }
        SettingsCache.agentMode = value
    }

    override val reasoning: Flow<String> = state.map {
        str(it, K.REASONING_EFFORT) ?: ReasoningEffort.DEFAULT.key
    }
    override suspend fun setReasoning(value: String) {
        edit { it[K.REASONING_EFFORT] = value }
        SettingsCache.reasoning = value
    }

    override val webSearchEngine: Flow<String> = state.map {
        str(it, K.WEB_SEARCH_ENGINE) ?: WebSearchEngine.DEFAULT.key
    }
    override suspend fun setWebSearchEngine(value: String) {
        val engine = WebSearchEngine.fromKey(value).key
        edit { it[K.WEB_SEARCH_ENGINE] = engine }
        SettingsCache.webSearchEngine = engine
    }

    override suspend fun webSearchEngineOnce(): String =
        str(state.first(), K.WEB_SEARCH_ENGINE) ?: WebSearchEngine.DEFAULT.key

    override val localBackgroundReplyEnabled: Flow<Boolean> = state.map { bool(it, K.LOCAL_BACKGROUND_REPLY) ?: false }
    override suspend fun setLocalBackgroundReplyEnabled(value: Boolean) {
        edit { it[K.LOCAL_BACKGROUND_REPLY] = value }
        SettingsCache.localBackgroundReplyEnabled = value
    }
    override suspend fun localBackgroundReplyEnabledOnce(): Boolean =
        bool(state.first(), K.LOCAL_BACKGROUND_REPLY) ?: false

    override val localToolsEnabled: Flow<Boolean> = state.map { bool(it, K.LOCAL_TOOLS_ENABLED) ?: false }
    override suspend fun setLocalToolsEnabled(value: Boolean) {
        edit { it[K.LOCAL_TOOLS_ENABLED] = value }
        SettingsCache.localToolsEnabled = value
    }

    override val smallModelModeEnabled: Flow<Boolean> = state.map { bool(it, K.SMALL_MODEL_MODE) ?: false }
    override suspend fun setSmallModelModeEnabled(value: Boolean) {
        edit { it[K.SMALL_MODEL_MODE] = value }
        SettingsCache.smallModelModeEnabled = value
    }

    override val localGpuEnabled: Flow<Boolean> = state.map { bool(it, K.LOCAL_GPU_ENABLED) ?: false }
    override suspend fun setLocalGpuEnabled(value: Boolean) {
        edit { it[K.LOCAL_GPU_ENABLED] = value }
        SettingsCache.localGpuEnabled = value
    }


    override val apiKey: Flow<String> = state.map { prefs ->
        val stored = str(prefs, K.API_KEY_ENC) ?: ""
        if (stored.isEmpty()) "" else LocalSecrets.decrypt(stored)
    }

    override val apiProfilesJson: Flow<String> = state.map { prefs ->
        val stored = str(prefs, K.API_PROFILES_ENC) ?: ""
        if (stored.isEmpty()) "" else LocalSecrets.decrypt(stored)
    }

    override val apiProfileSelected: Flow<Int> = state.map { int(it, K.API_PROFILE_SELECTED) ?: 0 }

    override val attachmentsMigrated: Flow<Boolean> = state.map { bool(it, K.ATTACHMENTS_MIGRATED) ?: false }
    override suspend fun setAttachmentsMigrated(value: Boolean) { edit { it[K.ATTACHMENTS_MIGRATED] = value } }

    override val backupPassword: Flow<String> = state.map { prefs ->
        val stored = str(prefs, K.BACKUP_PASSWORD_ENC) ?: ""
        if (stored.isEmpty()) "" else LocalSecrets.decrypt(stored)
    }

    override suspend fun setBackupPassword(value: String) {
        edit { prefs ->
            if (value.isEmpty()) prefs.remove(K.BACKUP_PASSWORD_ENC)
            else prefs[K.BACKUP_PASSWORD_ENC] = LocalSecrets.encrypt(value)
        }
    }


    override val notesSort: Flow<String> = state.map { str(it, K.NOTES_SORT) ?: "recent" }
    override val tasksSort: Flow<String> = state.map { str(it, K.TASKS_SORT) ?: "recent" }
    override val notebooksSort: Flow<String> = state.map { str(it, K.NOTEBOOKS_SORT) ?: "recent" }
    override suspend fun setNotesSort(value: String) {
        SettingsCache.notesSort = value
        edit { it[K.NOTES_SORT] = value }
    }
    override suspend fun setTasksSort(value: String) {
        SettingsCache.tasksSort = value
        edit { it[K.TASKS_SORT] = value }
    }

    override suspend fun setNotebooksSort(value: String) {
        SettingsCache.notebooksSort = value
        edit { it[K.NOTEBOOKS_SORT] = value }
    }

    override val notebookOpens: Flow<String> = state.map { secret(it, K.NOTEBOOK_OPENS_ENC, "{}") }
    override suspend fun notebookOpensOnce(): String = notebookOpens.first()
    override suspend fun setNotebookOpens(value: String) = putSecret(K.NOTEBOOK_OPENS_ENC, value)

    override val noteHistoryEnabled: Flow<Boolean> = state.map { bool(it, K.NOTE_HISTORY_ENABLED) ?: true }
    override val taskHistoryEnabled: Flow<Boolean> = state.map { bool(it, K.TASK_HISTORY_ENABLED) ?: true }
    override suspend fun setNoteHistoryEnabled(value: Boolean) {
        SettingsCache.noteHistoryEnabled = value
        edit { it[K.NOTE_HISTORY_ENABLED] = value }
    }
    override suspend fun setTaskHistoryEnabled(value: Boolean) {
        SettingsCache.taskHistoryEnabled = value
        edit { it[K.TASK_HISTORY_ENABLED] = value }
    }

    override val autoBackup: Flow<AutoBackup.State> =
        state.map { AutoBackup.State.fromJson(str(it, K.AUTO_BACKUP) ?: "") }
    override suspend fun autoBackupOnce(): AutoBackup.State =
        AutoBackup.State.fromJson(str(state.first(), K.AUTO_BACKUP) ?: "")
    override suspend fun setAutoBackup(state: AutoBackup.State) {
        SettingsCache.autoBackup = state
        edit { it[K.AUTO_BACKUP] = state.toJson() }
    }

    override val autoBackupPasswords: Flow<List<String>> = state.map { prefs ->
        val rawJson = str(prefs, K.AUTO_BACKUP_PASSWORDS_ENC) ?: ""
        if (rawJson.isBlank()) emptyList()
        else {
            try {
                val encList = Json.decodeFromString<List<String>>(rawJson)
                encList.mapNotNull { enc ->
                    if (enc.isEmpty()) null
                    else {
                        val dec = LocalSecrets.decrypt(enc)
                        if (dec.isEmpty()) null else dec
                    }
                }.take(3)
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    override suspend fun autoBackupPasswordsOnce(): List<String> = autoBackupPasswords.first()

    override suspend fun setAutoBackupPassword(index: Int, value: String) {
        if (index !in 0..2) return
        edit { prefs ->
            val rawJson = str(prefs, K.AUTO_BACKUP_PASSWORDS_ENC) ?: ""
            val current = try {
                if (rawJson.isBlank()) mutableListOf()
                else Json.decodeFromString<List<String>>(rawJson)
                    .map { LocalSecrets.decrypt(it) }
                    .filter { it.isNotEmpty() }
                    .toMutableList()
            } catch (_: Throwable) {
                mutableListOf()
            }
            if (value.isEmpty()) {
                if (index in current.indices) {
                    current.removeAt(index)
                }
            } else {
                if (index in current.indices) {
                    current[index] = value
                } else if (current.size < 3) {
                    current.add(value)
                }
            }
            val updated = current.take(3)
            if (updated.isEmpty()) {
                prefs.remove(K.AUTO_BACKUP_PASSWORDS_ENC)
            } else {
                val encList = updated.map { LocalSecrets.encrypt(it) }
                prefs[K.AUTO_BACKUP_PASSWORDS_ENC] = Json.encodeToString<List<String>>(encList)
            }
        }
    }

    override val autoBackupRecoveryQuestion: Flow<String> = state.map {
        str(it, K.AUTO_BACKUP_RECOVERY_Q) ?: ""
    }

    override suspend fun autoBackupRecoveryQuestionOnce(): String = autoBackupRecoveryQuestion.first()

    override val autoBackupRecoveryAnswer: Flow<String> = state.map { prefs ->
        val enc = str(prefs, K.AUTO_BACKUP_RECOVERY_A_ENC) ?: ""
        if (enc.isEmpty()) "" else LocalSecrets.decrypt(enc)
    }

    override suspend fun autoBackupRecoveryAnswerOnce(): String = autoBackupRecoveryAnswer.first()

    override suspend fun setAutoBackupRecovery(question: String, answer: String) {
        edit { prefs ->
            if (question.isBlank()) {
                prefs.remove(K.AUTO_BACKUP_RECOVERY_Q)
                prefs.remove(K.AUTO_BACKUP_RECOVERY_A_ENC)
            } else {
                prefs[K.AUTO_BACKUP_RECOVERY_Q] = question
                prefs[K.AUTO_BACKUP_RECOVERY_A_ENC] = LocalSecrets.encrypt(answer)
            }
        }
    }

    override val memoryTier: Flow<String> = state.map { str(it, K.MEMORY_TIER) ?: MemoryTier.DEFAULT.key }
    override suspend fun setMemoryTier(value: String) {
        edit { it[K.MEMORY_TIER] = value }
        SettingsCache.memoryTier = value
    }

    override val webSearchEnabled: Flow<Boolean> = state.map { bool(it, K.WEB_SEARCH_ENABLED) ?: false }
    override suspend fun setWebSearchEnabled(value: Boolean) {
        edit { it[K.WEB_SEARCH_ENABLED] = value }
        SettingsCache.webSearchEnabled = value
    }

    override val assistantConfirmToolsEnabled: Flow<Boolean> = state.map { bool(it, K.ASSISTANT_CONFIRM_TOOLS) ?: true }
    override suspend fun setAssistantConfirmTools(value: Boolean) {
        edit { it[K.ASSISTANT_CONFIRM_TOOLS] = value }
        SettingsCache.assistantConfirmToolsEnabled = value
    }

    override val typingHapticsEnabled: Flow<Boolean> = state.map { bool(it, K.TYPING_HAPTICS) ?: true }
    override suspend fun setTypingHapticsEnabled(value: Boolean) {
        SettingsCache.typingHapticsEnabled = value
        edit { it[K.TYPING_HAPTICS] = value }
    }



    override val lastScreen: Flow<String> = state.map { str(it, K.LAST_SCREEN) ?: "" }
    override suspend fun lastScreenOnce(): String = str(state.first(), K.LAST_SCREEN) ?: ""
    override suspend fun setLastScreen(value: String) { edit { it[K.LAST_SCREEN] = value } }

    override suspend fun setSessionSnapshot(value: String) { edit { it[K.SESSION_SNAPSHOT] = value } }
    override suspend fun sessionSnapshotOnce(): String = str(state.first(), K.SESSION_SNAPSHOT) ?: ""

    override val blackoutEnabled: Flow<Boolean> = state.map { bool(it, K.BLACKOUT_ENABLED) ?: false }
    override suspend fun blackoutEnabledOnce(): Boolean = bool(state.first(), K.BLACKOUT_ENABLED) ?: false

    override suspend fun setBlackoutEnabled(value: Boolean) {
        edit { prefs ->
            val wasEnabled = prefs[K.BLACKOUT_ENABLED] as? Boolean ?: false
            prefs[K.BLACKOUT_ENABLED] = value
            if (value) {
                if (!wasEnabled) {
                    prefs[K.APP_LOCK_PREBLACKOUT] = prefs[K.APP_LOCK_ENABLED] as? Boolean ?: false
                    prefs[K.SYSTEM_INTEGRATION_PREBLACKOUT] =
                        prefs[K.SYSTEM_INTEGRATION_ENABLED] as? Boolean ?: false
                }
                prefs[K.SYSTEM_INTEGRATION_ENABLED] = false
            } else {
                prefs[K.SYSTEM_INTEGRATION_ENABLED] =
                    prefs[K.SYSTEM_INTEGRATION_PREBLACKOUT] as? Boolean ?: false
                prefs.remove(K.APP_LOCK_PREBLACKOUT)
                prefs.remove(K.SYSTEM_INTEGRATION_PREBLACKOUT)
            }
        }
        SettingsCache.blackoutEnabled = value
    }

    override suspend fun appLockWasOnBeforeBlackout(): Boolean =
        bool(state.first(), K.APP_LOCK_PREBLACKOUT) ?: false

    override val crashShieldEnabled: Flow<Boolean> = state.map { bool(it, K.CRASH_SHIELD_ENABLED) ?: false }
    override suspend fun crashShieldEnabledOnce(): Boolean = bool(state.first(), K.CRASH_SHIELD_ENABLED) ?: false
    override suspend fun setCrashShieldEnabled(value: Boolean) {
        edit {
            it[K.CRASH_SHIELD_ENABLED] = value
            if (value) it[K.STARTUP_LOGGING_ENABLED] = true
        }
        SettingsCache.crashShieldEnabled = value
        if (value) SettingsCache.startupLoggingEnabled = true
    }

    override val passwordAttemptState: Flow<String> = state.map { str(it, K.PW_ATTEMPT_STATE) ?: "" }
    override suspend fun passwordAttemptStateOnce(): String = str(state.first(), K.PW_ATTEMPT_STATE) ?: ""
    override suspend fun setPasswordAttemptState(json: String) {
        SettingsCache.passwordAttemptState = json
        edit { it[K.PW_ATTEMPT_STATE] = json }
    }

    override val pwFirstRoundLimit: Flow<Int> =
        state.map { int(it, K.PW_FIRST_ROUND_LIMIT) ?: PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT }
    override val pwLaterRoundLimit: Flow<Int> =
        state.map { int(it, K.PW_LATER_ROUND_LIMIT) ?: PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT }
    override suspend fun setPwFirstRoundLimit(value: Int) {
        val safe = value.coerceIn(PasswordAttempts.ROUND_LIMIT_RANGE)
        SettingsCache.pwFirstRoundLimit = safe
        edit { it[K.PW_FIRST_ROUND_LIMIT] = safe }
    }
    override suspend fun setPwLaterRoundLimit(value: Int) {
        val safe = value.coerceIn(PasswordAttempts.ROUND_LIMIT_RANGE)
        SettingsCache.pwLaterRoundLimit = safe
        edit { it[K.PW_LATER_ROUND_LIMIT] = safe }
    }

    override val pwSelfDestructEnabled: Flow<Boolean> = state.map { bool(it, K.PW_SELF_DESTRUCT_ENABLED) ?: false }
    override val pwSelfDestructThreshold: Flow<Int> =
        state.map { int(it, K.PW_SELF_DESTRUCT_THRESHOLD) ?: PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD }
    override suspend fun setPwSelfDestructEnabled(value: Boolean) {
        SettingsCache.pwSelfDestructEnabled = value
        edit { it[K.PW_SELF_DESTRUCT_ENABLED] = value }
    }
    override suspend fun setPwSelfDestructThreshold(value: Int) {
        val safe = value.coerceIn(PasswordAttempts.SELF_DESTRUCT_RANGE)
        SettingsCache.pwSelfDestructThreshold = safe
        edit { it[K.PW_SELF_DESTRUCT_THRESHOLD] = safe }
    }

    override val openLinksExternally: Flow<Boolean> = state.map { bool(it, K.OPEN_LINKS_EXTERNALLY) ?: false }
    override suspend fun setOpenLinksExternally(value: Boolean) {
        SettingsCache.openLinksExternally = value
        edit { it[K.OPEN_LINKS_EXTERNALLY] = value }
    }


    override val updateChannel: Flow<String> = state.map {
        secret(it, K.UPDATE_CHANNEL_ENC, "stable")
    }
    override suspend fun setUpdateChannel(value: String) {
        SettingsCache.updateChannel = value
        putSecret(K.UPDATE_CHANNEL_ENC, value)
    }

    override val installedPreviewIdentity: Flow<String> = state.map { str(it, K.INSTALLED_PREVIEW_IDENTITY) ?: "" }
    override suspend fun setInstalledPreviewIdentity(value: String) {
        SettingsCache.installedPreviewIdentity = value
        edit { it[K.INSTALLED_PREVIEW_IDENTITY] = value }
    }

    override suspend fun setStagedUpdateIdentity(value: String) {
        SettingsCache.stagedUpdateIdentity = value
        edit { it[K.STAGED_UPDATE_IDENTITY] = value }
    }

    override val autoUpdateEnabled: Flow<Boolean> = state.map { bool(it, K.AUTO_UPDATE_ENABLED) ?: false }
    override suspend fun setAutoUpdateEnabled(value: Boolean) {
        SettingsCache.autoUpdateEnabled = value
        edit { it[K.AUTO_UPDATE_ENABLED] = value }
    }

    override suspend fun setStagedUpdate(tag: String, files: List<String>) {
        edit { prefs ->
            if (tag.isBlank()) {
                prefs.remove(K.STAGED_UPDATE_TAG)
                prefs.remove(K.STAGED_UPDATE_FILES)
            } else {
                prefs[K.STAGED_UPDATE_TAG] = tag
                prefs[K.STAGED_UPDATE_FILES] = files.joinToString(",")
            }
        }
    }

    override val pendingUpdateVersion: Flow<String> = state.map { str(it, K.PENDING_UPDATE_VERSION) ?: "" }
    override suspend fun setPendingUpdateVersion(value: String) {
        edit { prefs ->
            if (value.isBlank()) prefs.remove(K.PENDING_UPDATE_VERSION)
            else prefs[K.PENDING_UPDATE_VERSION] = value
        }
    }

    override val privilegedEnabled: Flow<Boolean> = state.map { bool(it, K.PRIVILEGED_ENABLED) ?: false }
    override suspend fun setPrivilegedEnabled(value: Boolean) {
        SettingsCache.privilegedEnabled = value
        edit { it[K.PRIVILEGED_ENABLED] = value }
    }

    override val markdownEnabled: Flow<Boolean> = state.map { bool(it, K.MARKDOWN_ENABLED) ?: false }
    override val richTextEnabled: Flow<Boolean> = state.map { bool(it, K.RICH_TEXT_ENABLED) ?: false }
    override suspend fun setMarkdownEnabled(value: Boolean) {
        edit { it[K.MARKDOWN_ENABLED] = value }
        SettingsCache.markdownEnabled = value
    }
    override suspend fun setRichTextEnabled(value: Boolean) {
        edit { it[K.RICH_TEXT_ENABLED] = value }
        SettingsCache.richTextEnabled = value
    }

    override val closeToTray: Flow<Boolean> = state.map { bool(it, K.CLOSE_TO_TRAY) ?: true }
    override suspend fun setCloseToTray(value: Boolean) {
        SettingsCache.closeToTray = value
        edit { it[K.CLOSE_TO_TRAY] = value }
    }
    override val savedSearches: Flow<String> = state.map { str(it, K.SAVED_SEARCHES) ?: "" }
    override suspend fun setSavedSearches(json: String) { edit { it[K.SAVED_SEARCHES] = json } }

    override val harnessConfig: Flow<String> = state.map { secret(it, K.HARNESS_CONFIG_ENC, "") }

    override suspend fun setHarnessConfig(json: String) {
        SettingsCache.harnessConfigJson = json
        val sealed = LocalSecrets.encrypt(json)
        edit { it[K.HARNESS_CONFIG_ENC] = sealed }
    }

    override suspend fun harnessConfigOnce(): String = secret(state.first(), K.HARNESS_CONFIG_ENC, "")
    override val customTemplatesJson: Flow<String> = state.map { str(it, K.CUSTOM_TEMPLATES) ?: "[]" }
    override suspend fun setCustomTemplatesJson(json: String) { edit { it[K.CUSTOM_TEMPLATES] = json } }
    override val templateDraftJson: Flow<String> = state.map { str(it, K.TEMPLATE_DRAFT) ?: "" }
    override suspend fun setTemplateDraftJson(json: String) { edit { it[K.TEMPLATE_DRAFT] = json } }
    override val hiddenTemplatesJson: Flow<String> = state.map { str(it, K.HIDDEN_TEMPLATES) ?: "" }
    override suspend fun setHiddenTemplatesJson(json: String) { edit { it[K.HIDDEN_TEMPLATES] = json } }
    override val cloudEnabled: Flow<Boolean> = state.map { bool(it, K.CLOUD_ENABLED) ?: false }
    override suspend fun setCloudEnabled(value: Boolean) {
        edit { it[K.CLOUD_ENABLED] = value }
        SettingsCache.cloudEnabled = value
    }
    override val cloudProvider: Flow<String> = state.map { str(it, K.CLOUD_PROVIDER) ?: "Nutstore" }
    override suspend fun setCloudProvider(value: String) {
        edit { it[K.CLOUD_PROVIDER] = value }
        SettingsCache.cloudProvider = value
    }
    override val cloudUrl: Flow<String> = state.map { str(it, K.CLOUD_URL) ?: "" }
    override suspend fun setCloudUrl(value: String) {
        edit { it[K.CLOUD_URL] = value }
        SettingsCache.cloudUrl = value
    }
    override val cloudUser: Flow<String> = state.map { str(it, K.CLOUD_USER) ?: "" }
    override suspend fun setCloudUser(value: String) {
        edit { it[K.CLOUD_USER] = value }
        SettingsCache.cloudUser = value
    }
    override val cloudPasswordEnc: Flow<String> = state.map { str(it, K.CLOUD_PASSWORD_ENC) ?: "" }
    override suspend fun setCloudPasswordEnc(value: String) {
        edit { it[K.CLOUD_PASSWORD_ENC] = value }
        SettingsCache.cloudPasswordEnc = value
    }
    override val embeddingProvider: Flow<String> = state.map { str(it, K.EMBEDDING_PROVIDER) ?: "local" }
    override suspend fun setEmbeddingProvider(value: String) {
        edit { it[K.EMBEDDING_PROVIDER] = value }
        SettingsCache.embeddingProvider = value
    }
    override val cloudFolder: Flow<String> = state.map { str(it, K.CLOUD_FOLDER) ?: "Lucent" }
    override suspend fun setCloudFolder(value: String) {
        edit { it[K.CLOUD_FOLDER] = value }
        SettingsCache.cloudFolder = value
    }
    override val cloudAutoBackup: Flow<Boolean> = state.map { bool(it, K.CLOUD_AUTO_BACKUP) ?: false }
    override suspend fun setCloudAutoBackup(value: Boolean) {
        edit { it[K.CLOUD_AUTO_BACKUP] = value }
        SettingsCache.cloudAutoBackup = value
    }

    override val linksEnabled: Flow<Boolean> = state.map { bool(it, K.LINKS_ENABLED) ?: false }
    override suspend fun setLinksEnabled(value: Boolean) {
        edit { it[K.LINKS_ENABLED] = value }
        SettingsCache.linksEnabled = value
    }

    override val backgroundAnimationEnabled: Flow<Boolean> =
        state.map { bool(it, K.BACKGROUND_ANIMATION_ENABLED) ?: false }
    override suspend fun setBackgroundAnimationEnabled(value: Boolean) {
        edit { it[K.BACKGROUND_ANIMATION_ENABLED] = value }
        SettingsCache.backgroundAnimationEnabled = value
    }

    override val splashEnabled: Flow<Boolean> = state.map { bool(it, K.SPLASH_ENABLED) ?: true }
    override suspend fun setSplashEnabled(value: Boolean) {
        SettingsCache.splashEnabled = value
        edit { it[K.SPLASH_ENABLED] = value }
    }

    override val splashStyle: Flow<String> = state.map { str(it, K.SPLASH_STYLE) ?: SplashStyle.DEFAULT.key }
    override suspend fun setSplashStyle(value: String) {
        SettingsCache.splashStyle = SplashStyle.fromKey(value).key
        edit { it[K.SPLASH_STYLE] = SplashStyle.fromKey(value).key }
    }


    override val appLockEnabled: Flow<Boolean> = state.map { bool(it, K.APP_LOCK_ENABLED) ?: false }

    override val appLockCredentials: Flow<String> = state.map { prefs ->
        val stored = str(prefs, K.APP_LOCK_CREDENTIALS_ENC) ?: ""
        if (stored.isEmpty()) "" else LocalSecrets.decrypt(stored)
    }

    override suspend fun setAppLock(enabled: Boolean, credentialsJson: String) {
        edit { prefs ->
            prefs[K.APP_LOCK_ENABLED] = enabled
            if (credentialsJson.isEmpty()) prefs.remove(K.APP_LOCK_CREDENTIALS_ENC)
            else prefs[K.APP_LOCK_CREDENTIALS_ENC] = LocalSecrets.encrypt(credentialsJson)
            if (!enabled) prefs.remove(K.APP_LOCK_HELLO_ENABLED)
        }
    }

    override suspend fun setAppLockCredentials(credentialsJson: String) {
        edit { prefs ->
            if (credentialsJson.isEmpty()) prefs.remove(K.APP_LOCK_CREDENTIALS_ENC)
            else prefs[K.APP_LOCK_CREDENTIALS_ENC] = LocalSecrets.encrypt(credentialsJson)
        }
    }

    override val appLockBiometricEnabled: Flow<Boolean> =
        kotlinx.coroutines.flow.flowOf(false)
    override suspend fun setAppLockBiometricEnabled(enabled: Boolean) { }

    override val appLockHelloEnabled: Flow<Boolean> = state.map { bool(it, K.APP_LOCK_HELLO_ENABLED) ?: false }

    override suspend fun setAppLockHelloEnabled(value: Boolean) {
        edit { it[K.APP_LOCK_HELLO_ENABLED] = value }
        SettingsCache.appLockHelloEnabled = value
    }

    override suspend fun appLockEnabledOnce(): Boolean =
        bool(state.first(), K.APP_LOCK_ENABLED) ?: false
    override suspend fun appLockCredentialsOnce(): String {
        val stored = str(state.first(), K.APP_LOCK_CREDENTIALS_ENC) ?: ""
        return if (stored.isEmpty()) "" else LocalSecrets.decrypt(stored)
    }
    override suspend fun startupLoggingEnabledOnce(): Boolean =
        bool(state.first(), K.STARTUP_LOGGING_ENABLED) ?: false
    override suspend fun systemIntegrationEnabledOnce(): Boolean =
        bool(state.first(), K.SYSTEM_INTEGRATION_ENABLED) ?: false

    override val systemIntegrationEnabled: Flow<Boolean> = state.map { bool(it, K.SYSTEM_INTEGRATION_ENABLED) ?: false }
    override suspend fun setSystemIntegrationEnabled(value: Boolean) {
        SettingsCache.systemIntegrationEnabled = value
        edit { it[K.SYSTEM_INTEGRATION_ENABLED] = value }
    }

    override val startupLoggingEnabled: Flow<Boolean> = state.map { bool(it, K.STARTUP_LOGGING_ENABLED) ?: false }
    override suspend fun setStartupLoggingEnabled(value: Boolean) {
        SettingsCache.startupLoggingEnabled = value
        edit { it[K.STARTUP_LOGGING_ENABLED] = value }
    }


    private suspend fun putSecret(key: String, value: String) {
        val sealed = LocalSecrets.encrypt(value)
        edit { it[key] = sealed }
    }

    override suspend fun setBaseUrl(value: String) {
        SettingsCache.baseUrl = value
        putSecret(K.BASE_URL_ENC, value)
    }
    override suspend fun setApiSpec(value: String) {
        SettingsCache.apiSpec = value
        putSecret(K.API_SPEC_ENC, value)
    }
    override suspend fun setModel(value: String) {
        SettingsCache.model = value
        putSecret(K.MODEL_ENC, value)
    }


    override val modelRecents: Flow<List<String>> = state.map { ModelRecents.parse(str(it, K.MODEL_RECENTS)) }

    override suspend fun setActiveModel(value: String) {
        val model = value.trim()
        if (model.isBlank()) return
        val prefs = state.first()
        val profiles = ApiProfiles.parse(secret(prefs, K.API_PROFILES_ENC, ""))
        val selected = int(prefs, K.API_PROFILE_SELECTED) ?: 0
        val profilesJson = if (profiles.isEmpty()) null else {
            val idx = selected.coerceIn(0, profiles.size - 1)
            ApiProfiles.serialize(profiles.mapIndexed { i, p -> if (i == idx) p.copy(model = model) else p })
        }
        val recents = ModelRecents.add(str(prefs, K.MODEL_RECENTS), model)
        edit {
            it[K.MODEL_ENC] = LocalSecrets.encrypt(model)
            if (profilesJson != null) it[K.API_PROFILES_ENC] = LocalSecrets.encrypt(profilesJson)
            it[K.MODEL_RECENTS] = recents
        }
    }

    override val terminalFontSize: Flow<Float?> = state.map {
        secret(it, K.TERMINAL_FONT_SIZE_ENC, "").toFloatOrNull()
    }
    override val terminalKeyBarVisible: Flow<Boolean> = state.map {
        secret(it, K.TERMINAL_KEY_BAR_VISIBLE_ENC, "true").toBooleanStrictOrNull() ?: true
    }
    override val globalTextSelectionEnabled: Flow<Boolean> = state.map {
        secret(it, K.GLOBAL_TEXT_SELECTION_ENABLED_ENC, "false").toBooleanStrictOrNull() ?: false
    }

    override suspend fun setTerminalFontSize(value: Float) {
        SettingsCache.terminalFontSize = value
        putSecret(K.TERMINAL_FONT_SIZE_ENC, value.toString())
    }
    override suspend fun setTerminalKeyBarVisible(value: Boolean) {
        SettingsCache.terminalKeyBarVisible = value
        putSecret(K.TERMINAL_KEY_BAR_VISIBLE_ENC, value.toString())
    }
    override suspend fun setGlobalTextSelectionEnabled(value: Boolean) {
        SettingsCache.globalTextSelectionEnabled = value
        putSecret(K.GLOBAL_TEXT_SELECTION_ENABLED_ENC, value.toString())
    }

    override suspend fun setAssistantName(value: String) {
        SettingsCache.assistantName = value
        putSecret(K.ASSISTANT_NAME_ENC, value)
    }
    override suspend fun setAssistantStyle(value: String) {
        SettingsCache.assistantStyle = value
        putSecret(K.ASSISTANT_STYLE_ENC, value)
    }

    override suspend fun setThemeMode(value: String) {
        edit { it[K.THEME_MODE] = value }
        SettingsCache.themeMode = value
    }
    override suspend fun setPalette(value: String) {
        edit { it[K.PALETTE] = value }
        SettingsCache.palette = value
    }
    override suspend fun setFont(value: String) {
        edit { it[K.FONT] = value }
        SettingsCache.font = value
    }
    override suspend fun setDynamicColorEnabled(value: Boolean) {
        edit { it[K.DYNAMIC_COLOR_ENABLED] = value }
        SettingsCache.dynamicColor = value
    }

    override suspend fun setApiKey(value: String) {
        SettingsCache.apiKey = value
        edit { it[K.API_KEY_ENC] = LocalSecrets.encrypt(value) }
    }

    override suspend fun saveApiProfiles(profiles: List<ApiProfile>, selected: Int) {
        val safe = profiles.take(ApiProfiles.MAX)
        val idx = if (safe.isEmpty()) 0 else selected.coerceIn(0, safe.size - 1)
        val active = safe.getOrNull(idx)
        SettingsCache.apiProfilesJson = ApiProfiles.serialize(safe)
        SettingsCache.apiProfileSelected = idx
        SettingsCache.baseUrl = active?.baseUrl ?: ""
        SettingsCache.apiSpec = active?.spec ?: "openai"
        SettingsCache.model = active?.model ?: ""
        active?.apiKey?.let { SettingsCache.apiKey = it }
        val profilesEnc = LocalSecrets.encrypt(ApiProfiles.serialize(safe))
        val activeKeyEnc = active?.let { LocalSecrets.encrypt(it.apiKey) }
        val activeBaseUrlEnc = LocalSecrets.encrypt(active?.baseUrl ?: "")
        val activeSpecEnc = LocalSecrets.encrypt(active?.spec ?: "openai")
        val activeModelEnc = LocalSecrets.encrypt(active?.model ?: "")

        edit { prefs ->
            prefs[K.API_PROFILES_ENC] = profilesEnc
            prefs[K.API_PROFILE_SELECTED] = idx
            if (active != null) {
                prefs[K.BASE_URL_ENC] = activeBaseUrlEnc
                prefs[K.API_SPEC_ENC] = activeSpecEnc
                prefs[K.MODEL_ENC] = activeModelEnc
                if (activeKeyEnc != null) prefs[K.API_KEY_ENC] = activeKeyEnc
            } else {
                prefs.remove(K.BASE_URL_ENC)
                prefs.remove(K.MODEL_ENC)
                prefs.remove(K.API_KEY_ENC)
            }
        }
    }

    override suspend fun clearAll() { edit { it.clear() } }
}

actual fun createSettingsRepository(context: PlatformContext): SettingsRepository =
    DesktopSettingsRepository(context)
