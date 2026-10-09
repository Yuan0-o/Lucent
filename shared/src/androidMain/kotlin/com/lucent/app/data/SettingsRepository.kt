@file:JvmName("AndroidSettingsRepositoryKt")
package com.lucent.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lucent.app.platform.PlatformContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "lucent_settings")

private object SettingsKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val PALETTE = stringPreferencesKey("palette")
    val FONT = stringPreferencesKey("font")
    val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")

    val BASE_URL_ENC = stringPreferencesKey("base_url_enc")
    val API_SPEC_ENC = stringPreferencesKey("api_spec_enc")
    val MODEL_ENC = stringPreferencesKey("model_enc")
    val ASSISTANT_NAME_ENC = stringPreferencesKey("assistant_name_enc")
    val ASSISTANT_STYLE_ENC = stringPreferencesKey("assistant_style_enc")

    val LEGACY_BASE_URL = stringPreferencesKey("base_url")
    val LEGACY_API_SPEC = stringPreferencesKey("api_spec")
    val LEGACY_MODEL = stringPreferencesKey("model")
    val LEGACY_ASSISTANT_NAME = stringPreferencesKey("assistant_name")
    val LEGACY_ASSISTANT_STYLE = stringPreferencesKey("assistant_style")

    val ATTACHMENTS_MIGRATED = booleanPreferencesKey("attachments_migrated_v1")

    val API_KEY_ENC = stringPreferencesKey("api_key_enc")
    val API_PROFILES_ENC = stringPreferencesKey("api_profiles_json_enc")

    val BACKUP_PASSWORD_ENC = stringPreferencesKey("backup_password_enc")
    val AUTO_BACKUP_PASSWORDS_ENC = stringPreferencesKey("auto_backup_passwords_enc")
    val AUTO_BACKUP_RECOVERY_Q = stringPreferencesKey("auto_backup_recovery_q")
    val AUTO_BACKUP_RECOVERY_A_ENC = stringPreferencesKey("auto_backup_recovery_a_enc")

    val LEGACY_API_KEY = stringPreferencesKey("api_key")
    val LEGACY_API_PROFILES = stringPreferencesKey("api_profiles_json")

    val API_PROFILE_SELECTED = intPreferencesKey("api_profile_selected")

    val NOTES_SORT = stringPreferencesKey("notes_sort")
    val SESSION_SNAPSHOT = stringPreferencesKey("session_snapshot")
    val TASKS_SORT = stringPreferencesKey("tasks_sort")
    val NOTEBOOKS_SORT = stringPreferencesKey("notebooks_sort")
    val NOTEBOOK_OPENS_ENC = stringPreferencesKey("notebook_opens_enc")
    val NOTEBOOK_OPENS_LEGACY = stringPreferencesKey("notebook_opens")

    val AUTO_BACKUP = stringPreferencesKey("auto_backup_state")

    val NOTE_HISTORY_ENABLED = booleanPreferencesKey("note_history_enabled")
    val TASK_HISTORY_ENABLED = booleanPreferencesKey("task_history_enabled")

    val MEMORY_TIER = stringPreferencesKey("memory_tier")
    val WEB_SEARCH_ENABLED = booleanPreferencesKey("web_search_enabled")
    val ASSISTANT_CONFIRM_TOOLS = booleanPreferencesKey("assistant_confirm_tools")
    val TYPING_HAPTICS = booleanPreferencesKey("typing_haptics")

    val MARKDOWN_ENABLED = booleanPreferencesKey("markdown_enabled")

    val RICH_TEXT_ENABLED = booleanPreferencesKey("rich_text_enabled")
    val SAVED_SEARCHES = stringPreferencesKey("saved_searches")
    val CUSTOM_TEMPLATES = stringPreferencesKey("custom_templates")
    val TEMPLATE_DRAFT = stringPreferencesKey("template_draft")
    val HIDDEN_TEMPLATES = stringPreferencesKey("hidden_templates")
    val CLOUD_ENABLED = booleanPreferencesKey("cloud_enabled")
    val CLOUD_PROVIDER = stringPreferencesKey("cloud_provider")
    val CLOUD_URL = stringPreferencesKey("cloud_url")
    val CLOUD_USER = stringPreferencesKey("cloud_user")
    val CLOUD_PASSWORD_ENC = stringPreferencesKey("cloud_password_enc")
    val EMBEDDING_PROVIDER = stringPreferencesKey("embedding_provider")
    val CLOUD_FOLDER = stringPreferencesKey("cloud_folder")
    val CLOUD_AUTO_BACKUP = booleanPreferencesKey("cloud_auto_backup")

    val LINKS_ENABLED = booleanPreferencesKey("links_enabled")

    val BACKGROUND_ANIMATION_ENABLED = booleanPreferencesKey("background_animation_enabled")
    val SPLASH_ENABLED = booleanPreferencesKey("splash_enabled")
    val SPLASH_STYLE = stringPreferencesKey("splash_style")

    val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    val APP_LOCK_CREDENTIALS_ENC = stringPreferencesKey("app_lock_credentials_enc")
    val APP_LOCK_BIOMETRIC_ENABLED = booleanPreferencesKey("app_lock_biometric_enabled")

    val SYSTEM_INTEGRATION_ENABLED = booleanPreferencesKey("system_integration_enabled")

    val STARTUP_LOGGING_ENABLED = booleanPreferencesKey("startup_logging_enabled")

    val APP_LANGUAGE = stringPreferencesKey("app_language")

    val LOCAL_MODEL_ENABLED = booleanPreferencesKey("local_model_enabled")

    val LOCAL_TOOLS_ENABLED = booleanPreferencesKey("local_tools_enabled")

    val LOCAL_GPU_ENABLED = booleanPreferencesKey("local_gpu_enabled")

    val LOCAL_BACKGROUND_REPLY = booleanPreferencesKey("local_background_reply")

    val MEMORY_TIER_LOCAL = stringPreferencesKey("memory_tier_local")

    val AGENT_MODE = booleanPreferencesKey("cloud_agent_mode")

    val REASONING_EFFORT = stringPreferencesKey("reasoning_effort")

    val WEB_SEARCH_ENGINE = stringPreferencesKey("web_search_engine")

    val MODEL_RECENTS = stringPreferencesKey("model_recents")

    val SMALL_MODEL_MODE = booleanPreferencesKey("small_model_mode")

    val LAST_SCREEN = stringPreferencesKey("last_screen")

    val BLACKOUT_ENABLED = booleanPreferencesKey("blackout_enabled")
    val APP_LOCK_PREBLACKOUT = booleanPreferencesKey("app_lock_preblackout")
    val SYSTEM_INTEGRATION_PREBLACKOUT = booleanPreferencesKey("system_integration_preblackout")

    val CRASH_SHIELD_ENABLED = booleanPreferencesKey("crash_shield_enabled")

    val PW_ATTEMPT_STATE = stringPreferencesKey("pw_attempt_state")
    val PW_FIRST_ROUND_LIMIT = intPreferencesKey("pw_first_round_limit")
    val PW_LATER_ROUND_LIMIT = intPreferencesKey("pw_later_round_limit")
    val PW_SELF_DESTRUCT_ENABLED = booleanPreferencesKey("pw_self_destruct_enabled")
    val PW_SELF_DESTRUCT_THRESHOLD = intPreferencesKey("pw_self_destruct_threshold")

    val OPEN_LINKS_EXTERNALLY = booleanPreferencesKey("open_links_externally")

    val MEMORY_TIER_PRELOCAL = stringPreferencesKey("memory_tier_prelocal")
    val WEB_SEARCH_PRELOCAL = booleanPreferencesKey("web_search_prelocal")
    val UPDATE_CHANNEL_ENC = stringPreferencesKey("update_channel_enc")
    val LEGACY_UPDATE_CHANNEL = stringPreferencesKey("update_channel")
    val INSTALLED_PREVIEW_IDENTITY = stringPreferencesKey("installed_preview_identity")
    val STAGED_UPDATE_IDENTITY = stringPreferencesKey("staged_update_identity")
    val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
    val PENDING_UPDATE_VERSION = stringPreferencesKey("pending_update_version")
    val STAGED_UPDATE_TAG = stringPreferencesKey("staged_update_tag")
    val STAGED_UPDATE_FILES = stringPreferencesKey("staged_update_files")

    val PRIVILEGED_ENABLED = booleanPreferencesKey("privileged_enabled")

    val HARNESS_CONFIG_ENC = stringPreferencesKey("harness_config_enc")

    val TERMINAL_FONT_SIZE_ENC = stringPreferencesKey("terminal_font_size_enc")
    val LEGACY_TERMINAL_FONT_SIZE = stringPreferencesKey("terminal_font_size")
    val TERMINAL_KEY_BAR_VISIBLE_ENC = stringPreferencesKey("terminal_key_bar_visible_enc")
    val LEGACY_TERMINAL_KEY_BAR_VISIBLE = stringPreferencesKey("terminal_key_bar_visible")

    val GLOBAL_TEXT_SELECTION_ENABLED_ENC = stringPreferencesKey("global_text_selection_enabled_enc")
    val LEGACY_GLOBAL_TEXT_SELECTION_ENABLED = stringPreferencesKey("global_text_selection_enabled")

}

class AndroidSettingsRepository(private val context: Context) : SettingsRepository {

    private fun secret(
        prefs: androidx.datastore.preferences.core.Preferences,
        encrypted: androidx.datastore.preferences.core.Preferences.Key<String>,
        legacy: androidx.datastore.preferences.core.Preferences.Key<String>,
        default: String
    ): String {
        val stored = prefs[encrypted] ?: prefs[legacy] ?: return default
        return LocalSecrets.decrypt(stored).ifEmpty { default }
    }

    override val baseUrl: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.BASE_URL_ENC, SettingsKeys.LEGACY_BASE_URL, "")
    }
    override val apiSpec: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.API_SPEC_ENC, SettingsKeys.LEGACY_API_SPEC, "openai")
    }
    override val model: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.MODEL_ENC, SettingsKeys.LEGACY_MODEL, "")
    }
    override val assistantName: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.ASSISTANT_NAME_ENC, SettingsKeys.LEGACY_ASSISTANT_NAME, "Lucent")
    }
    override val assistantStyle: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.ASSISTANT_STYLE_ENC, SettingsKeys.LEGACY_ASSISTANT_STYLE, "")
    }

    override val themeMode: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.THEME_MODE] ?: "system" }
    override val palette: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.PALETTE] ?: "CYCLE" }
    override val font: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.FONT] ?: "system" }

    override val dynamicColorEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.DYNAMIC_COLOR_ENABLED] ?: false }

    override suspend fun displayPrefsOnce(): SettingsRepository.DisplayPrefs {
        val prefs = context.settingsDataStore.data.first()
        return SettingsRepository.DisplayPrefs(
            themeMode = prefs[SettingsKeys.THEME_MODE] ?: "system",
            palette = prefs[SettingsKeys.PALETTE] ?: "CYCLE",
            font = prefs[SettingsKeys.FONT] ?: "system"
        )
    }

    override suspend fun startupPrefsOnce(): SettingsRepository.StartupPrefs {
        val prefs = context.settingsDataStore.data.first()
        return SettingsRepository.StartupPrefs(
            display = SettingsRepository.DisplayPrefs(
                themeMode = prefs[SettingsKeys.THEME_MODE] ?: "system",
                palette = prefs[SettingsKeys.PALETTE] ?: "CYCLE",
                font = prefs[SettingsKeys.FONT] ?: "system"
            ),
            appLockEnabled = prefs[SettingsKeys.APP_LOCK_ENABLED] ?: false,
            startupLoggingEnabled = prefs[SettingsKeys.STARTUP_LOGGING_ENABLED] ?: false,
            systemIntegrationEnabled = prefs[SettingsKeys.SYSTEM_INTEGRATION_ENABLED] ?: false,
            appLanguage = prefs[SettingsKeys.APP_LANGUAGE] ?: "system",
            assistantName = secret(prefs, SettingsKeys.ASSISTANT_NAME_ENC, SettingsKeys.LEGACY_ASSISTANT_NAME, "Lucent"),
            backgroundAnimationEnabled = prefs[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] ?: true,
            splashEnabled = prefs[SettingsKeys.SPLASH_ENABLED] ?: true,
            splashStyle = prefs[SettingsKeys.SPLASH_STYLE] ?: SplashStyle.DEFAULT.key,
            autoBackup = AutoBackup.State.fromJson(prefs[SettingsKeys.AUTO_BACKUP] ?: ""),
            dynamicColor = prefs[SettingsKeys.DYNAMIC_COLOR_ENABLED] ?: false,
            notesSort = prefs[SettingsKeys.NOTES_SORT] ?: "recent",
            tasksSort = prefs[SettingsKeys.TASKS_SORT] ?: "recent",
            notebooksSort = prefs[SettingsKeys.NOTEBOOKS_SORT] ?: "recent",
            sessionSnapshot = prefs[SettingsKeys.SESSION_SNAPSHOT] ?: "",
            assistantStyle = secret(prefs, SettingsKeys.ASSISTANT_STYLE_ENC, SettingsKeys.LEGACY_ASSISTANT_STYLE, ""),
            baseUrl = secret(prefs, SettingsKeys.BASE_URL_ENC, SettingsKeys.LEGACY_BASE_URL, ""),
            apiSpec = secret(prefs, SettingsKeys.API_SPEC_ENC, SettingsKeys.LEGACY_API_SPEC, "openai"),
            apiKey = LocalSecrets.decrypt(prefs[SettingsKeys.API_KEY_ENC] ?: prefs[SettingsKeys.LEGACY_API_KEY] ?: ""),
            model = secret(prefs, SettingsKeys.MODEL_ENC, SettingsKeys.LEGACY_MODEL, ""),
            apiProfilesJson = LocalSecrets.decrypt(
                prefs[SettingsKeys.API_PROFILES_ENC] ?: prefs[SettingsKeys.LEGACY_API_PROFILES] ?: ""
            ),
            apiProfileSelected = prefs[SettingsKeys.API_PROFILE_SELECTED] ?: 0,
            noteHistoryEnabled = prefs[SettingsKeys.NOTE_HISTORY_ENABLED] ?: true,
            taskHistoryEnabled = prefs[SettingsKeys.TASK_HISTORY_ENABLED] ?: true,
            crashShieldEnabled = prefs[SettingsKeys.CRASH_SHIELD_ENABLED] ?: false,
            blackoutEnabled = prefs[SettingsKeys.BLACKOUT_ENABLED] ?: false,
            pwSelfDestructEnabled = prefs[SettingsKeys.PW_SELF_DESTRUCT_ENABLED] ?: false,
            pwFirstRoundLimit = prefs[SettingsKeys.PW_FIRST_ROUND_LIMIT]
                ?: PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT,
            pwLaterRoundLimit = prefs[SettingsKeys.PW_LATER_ROUND_LIMIT]
                ?: PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT,
            pwSelfDestructThreshold = prefs[SettingsKeys.PW_SELF_DESTRUCT_THRESHOLD]
                ?: PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD,
            passwordAttemptState = prefs[SettingsKeys.PW_ATTEMPT_STATE] ?: "",
            appLockBiometricEnabled = prefs[SettingsKeys.APP_LOCK_BIOMETRIC_ENABLED] ?: false,
            appLockHelloEnabled = false,
            closeToTray = true,
            openLinksExternally = prefs[SettingsKeys.OPEN_LINKS_EXTERNALLY] ?: false,
            markdownEnabled = prefs[SettingsKeys.MARKDOWN_ENABLED] ?: false,
            richTextEnabled = prefs[SettingsKeys.RICH_TEXT_ENABLED] ?: false,
            linksEnabled = prefs[SettingsKeys.LINKS_ENABLED] ?: false,
            typingHapticsEnabled = prefs[SettingsKeys.TYPING_HAPTICS] ?: true,
            assistantConfirmToolsEnabled = prefs[SettingsKeys.ASSISTANT_CONFIRM_TOOLS] ?: true,
            localModelEnabled = prefs[SettingsKeys.LOCAL_MODEL_ENABLED] ?: false,
            localToolsEnabled = prefs[SettingsKeys.LOCAL_TOOLS_ENABLED] ?: false,
            localGpuEnabled = prefs[SettingsKeys.LOCAL_GPU_ENABLED] ?: false,
            localBackgroundReplyEnabled = prefs[SettingsKeys.LOCAL_BACKGROUND_REPLY] ?: false,
            agentMode = prefs[SettingsKeys.AGENT_MODE] ?: true,
            reasoning = prefs[SettingsKeys.REASONING_EFFORT] ?: ReasoningEffort.DEFAULT.key,
            webSearchEngine = prefs[SettingsKeys.WEB_SEARCH_ENGINE] ?: WebSearchEngine.DEFAULT.key,
            smallModelModeEnabled = prefs[SettingsKeys.SMALL_MODEL_MODE] ?: false,
            webSearchEnabled = prefs[SettingsKeys.WEB_SEARCH_ENABLED] ?: false,
            memoryTier = prefs[SettingsKeys.MEMORY_TIER] ?: MemoryTier.DEFAULT.key,
            memoryTierLocal = prefs[SettingsKeys.MEMORY_TIER_LOCAL] ?: MemoryTier.LOW.key,
            embeddingProvider = prefs[SettingsKeys.EMBEDDING_PROVIDER] ?: "local",
            cloudEnabled = prefs[SettingsKeys.CLOUD_ENABLED] ?: false,
            cloudProvider = prefs[SettingsKeys.CLOUD_PROVIDER] ?: "Nutstore",
            cloudUrl = prefs[SettingsKeys.CLOUD_URL] ?: "",
            cloudUser = prefs[SettingsKeys.CLOUD_USER] ?: "",
            cloudFolder = prefs[SettingsKeys.CLOUD_FOLDER] ?: "Lucent",
            cloudAutoBackup = prefs[SettingsKeys.CLOUD_AUTO_BACKUP] ?: false,
            terminalFontSize = secret(prefs, SettingsKeys.TERMINAL_FONT_SIZE_ENC, SettingsKeys.LEGACY_TERMINAL_FONT_SIZE, "").toFloatOrNull(),
            terminalKeyBarVisible = secret(prefs, SettingsKeys.TERMINAL_KEY_BAR_VISIBLE_ENC, SettingsKeys.LEGACY_TERMINAL_KEY_BAR_VISIBLE, "true").toBooleanStrictOrNull() ?: true,
            globalTextSelectionEnabled = secret(prefs, SettingsKeys.GLOBAL_TEXT_SELECTION_ENABLED_ENC, SettingsKeys.LEGACY_GLOBAL_TEXT_SELECTION_ENABLED, "false").toBooleanStrictOrNull() ?: false,
            cloudPasswordEnc = prefs[SettingsKeys.CLOUD_PASSWORD_ENC] ?: "",
            updateChannel = secret(prefs, SettingsKeys.UPDATE_CHANNEL_ENC, SettingsKeys.LEGACY_UPDATE_CHANNEL, "stable"),
            installedPreviewIdentity = prefs[SettingsKeys.INSTALLED_PREVIEW_IDENTITY] ?: "",
            stagedUpdateIdentity = prefs[SettingsKeys.STAGED_UPDATE_IDENTITY] ?: "",
            autoUpdateEnabled = prefs[SettingsKeys.AUTO_UPDATE_ENABLED] ?: false,
            privilegedEnabled = prefs[SettingsKeys.PRIVILEGED_ENABLED] ?: false,
            pendingUpdateVersion = prefs[SettingsKeys.PENDING_UPDATE_VERSION] ?: "",
            stagedUpdateTag = prefs[SettingsKeys.STAGED_UPDATE_TAG] ?: "",
            stagedUpdateFiles = prefs[SettingsKeys.STAGED_UPDATE_FILES] ?: ""
        )
    }


    override val lastScreen: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.LAST_SCREEN] ?: "" }
    override suspend fun lastScreenOnce(): String = context.settingsDataStore.data.first()[SettingsKeys.LAST_SCREEN] ?: ""
    override suspend fun setLastScreen(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.LAST_SCREEN] = value }
    }

    override val blackoutEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.BLACKOUT_ENABLED] ?: false }
    override suspend fun blackoutEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.BLACKOUT_ENABLED] ?: false

    override suspend fun setBlackoutEnabled(value: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val wasEnabled = prefs[SettingsKeys.BLACKOUT_ENABLED] ?: false
            prefs[SettingsKeys.BLACKOUT_ENABLED] = value
            if (value) {
                if (!wasEnabled) {
                    prefs[SettingsKeys.APP_LOCK_PREBLACKOUT] = prefs[SettingsKeys.APP_LOCK_ENABLED] ?: false
                    prefs[SettingsKeys.SYSTEM_INTEGRATION_PREBLACKOUT] =
                        prefs[SettingsKeys.SYSTEM_INTEGRATION_ENABLED] ?: false
                }
                prefs[SettingsKeys.SYSTEM_INTEGRATION_ENABLED] = false
            } else {
                prefs[SettingsKeys.SYSTEM_INTEGRATION_ENABLED] =
                    prefs[SettingsKeys.SYSTEM_INTEGRATION_PREBLACKOUT] ?: false
                prefs.remove(SettingsKeys.APP_LOCK_PREBLACKOUT)
                prefs.remove(SettingsKeys.SYSTEM_INTEGRATION_PREBLACKOUT)
            }
        }
        SettingsCache.blackoutEnabled = value
    }

    override suspend fun appLockWasOnBeforeBlackout(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.APP_LOCK_PREBLACKOUT] ?: false

    override val crashShieldEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.CRASH_SHIELD_ENABLED] ?: false }
    override suspend fun crashShieldEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.CRASH_SHIELD_ENABLED] ?: false

    override suspend fun setCrashShieldEnabled(value: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.CRASH_SHIELD_ENABLED] = value
            if (value) prefs[SettingsKeys.STARTUP_LOGGING_ENABLED] = true
        }
        SettingsCache.crashShieldEnabled = value
    }

    override val passwordAttemptState: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.PW_ATTEMPT_STATE] ?: "" }
    override suspend fun passwordAttemptStateOnce(): String =
        context.settingsDataStore.data.first()[SettingsKeys.PW_ATTEMPT_STATE] ?: ""
    override suspend fun setPasswordAttemptState(json: String) {
        SettingsCache.passwordAttemptState = json
        context.settingsDataStore.edit { it[SettingsKeys.PW_ATTEMPT_STATE] = json }
    }

    override val pwFirstRoundLimit: Flow<Int> = context.settingsDataStore.data.map {
        it[SettingsKeys.PW_FIRST_ROUND_LIMIT] ?: PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT
    }
    override val pwLaterRoundLimit: Flow<Int> = context.settingsDataStore.data.map {
        it[SettingsKeys.PW_LATER_ROUND_LIMIT] ?: PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT
    }
    override suspend fun setPwFirstRoundLimit(value: Int) {
        context.settingsDataStore.edit {
            it[SettingsKeys.PW_FIRST_ROUND_LIMIT] = value.coerceIn(PasswordAttempts.ROUND_LIMIT_RANGE)
        }
        SettingsCache.pwFirstRoundLimit = value.coerceIn(PasswordAttempts.ROUND_LIMIT_RANGE)
    }
    override suspend fun setPwLaterRoundLimit(value: Int) {
        context.settingsDataStore.edit {
            it[SettingsKeys.PW_LATER_ROUND_LIMIT] = value.coerceIn(PasswordAttempts.ROUND_LIMIT_RANGE)
        }
        SettingsCache.pwLaterRoundLimit = value.coerceIn(PasswordAttempts.ROUND_LIMIT_RANGE)
    }

    override val pwSelfDestructEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.PW_SELF_DESTRUCT_ENABLED] ?: false }
    override val pwSelfDestructThreshold: Flow<Int> = context.settingsDataStore.data.map {
        it[SettingsKeys.PW_SELF_DESTRUCT_THRESHOLD] ?: PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD
    }
    override suspend fun setPwSelfDestructEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.PW_SELF_DESTRUCT_ENABLED] = value }
        SettingsCache.pwSelfDestructEnabled = value
    }
    override suspend fun setPwSelfDestructThreshold(value: Int) {
        context.settingsDataStore.edit {
            it[SettingsKeys.PW_SELF_DESTRUCT_THRESHOLD] = value.coerceIn(PasswordAttempts.SELF_DESTRUCT_RANGE)
        }
        SettingsCache.pwSelfDestructThreshold = value.coerceIn(PasswordAttempts.SELF_DESTRUCT_RANGE)
    }

    override val openLinksExternally: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.OPEN_LINKS_EXTERNALLY] ?: false }
    override suspend fun setOpenLinksExternally(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.OPEN_LINKS_EXTERNALLY] = value }
        SettingsCache.openLinksExternally = value
    }

    override val appLanguage: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.APP_LANGUAGE] ?: "system" }
    override suspend fun setAppLanguage(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.APP_LANGUAGE] = value }
        SettingsCache.appLanguage = value
    }
    override suspend fun appLanguageOnce(): String =
        context.settingsDataStore.data.first()[SettingsKeys.APP_LANGUAGE] ?: "system"

    override val localModelEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.LOCAL_MODEL_ENABLED] ?: false }

    override suspend fun setLocalModelEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.LOCAL_MODEL_ENABLED] = value }
        SettingsCache.localModelEnabled = value
    }

    override val memoryTierLocal: Flow<String> =
        context.settingsDataStore.data.map { it[SettingsKeys.MEMORY_TIER_LOCAL] ?: MemoryTier.LOW.key }
    override suspend fun setMemoryTierLocal(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.MEMORY_TIER_LOCAL] = value }
        SettingsCache.memoryTierLocal = value
    }

    override val agentMode: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.AGENT_MODE] ?: true }
    override suspend fun setAgentMode(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.AGENT_MODE] = value }
        SettingsCache.agentMode = value
    }

    override val reasoning: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.REASONING_EFFORT] ?: ReasoningEffort.DEFAULT.key
    }
    override suspend fun setReasoning(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.REASONING_EFFORT] = value }
        SettingsCache.reasoning = value
    }

    override val webSearchEngine: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.WEB_SEARCH_ENGINE] ?: WebSearchEngine.DEFAULT.key
    }
    override suspend fun setWebSearchEngine(value: String) {
        val engine = WebSearchEngine.fromKey(value).key
        context.settingsDataStore.edit { it[SettingsKeys.WEB_SEARCH_ENGINE] = engine }
        SettingsCache.webSearchEngine = engine
    }

    override suspend fun webSearchEngineOnce(): String =
        context.settingsDataStore.data.first()[SettingsKeys.WEB_SEARCH_ENGINE]
            ?: WebSearchEngine.DEFAULT.key

    override val localBackgroundReplyEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.LOCAL_BACKGROUND_REPLY] ?: false }
    override suspend fun setLocalBackgroundReplyEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.LOCAL_BACKGROUND_REPLY] = value }
        SettingsCache.localBackgroundReplyEnabled = value
    }

    override suspend fun localBackgroundReplyEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.LOCAL_BACKGROUND_REPLY] ?: false

    override val smallModelModeEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.SMALL_MODEL_MODE] ?: false }
    override suspend fun setSmallModelModeEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.SMALL_MODEL_MODE] = value }
        SettingsCache.smallModelModeEnabled = value
    }

    override val localToolsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.LOCAL_TOOLS_ENABLED] ?: false }
    override suspend fun setLocalToolsEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.LOCAL_TOOLS_ENABLED] = value }
        SettingsCache.localToolsEnabled = value
    }

    override val localGpuEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.LOCAL_GPU_ENABLED] ?: false }
    override suspend fun setLocalGpuEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.LOCAL_GPU_ENABLED] = value }
        SettingsCache.localGpuEnabled = value
    }

    override val apiKey: Flow<String> = context.settingsDataStore.data.map { prefs ->
        val stored = prefs[SettingsKeys.API_KEY_ENC] ?: prefs[SettingsKeys.LEGACY_API_KEY] ?: ""
        LocalSecrets.decrypt(stored)
    }

    override val apiProfilesJson: Flow<String> = context.settingsDataStore.data.map { prefs ->
        val stored = prefs[SettingsKeys.API_PROFILES_ENC] ?: prefs[SettingsKeys.LEGACY_API_PROFILES] ?: ""
        LocalSecrets.decrypt(stored)
    }

    override val apiProfileSelected: Flow<Int> = context.settingsDataStore.data.map { it[SettingsKeys.API_PROFILE_SELECTED] ?: 0 }

    override val attachmentsMigrated: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.ATTACHMENTS_MIGRATED] ?: false }

    override val backupPassword: Flow<String> = context.settingsDataStore.data.map { prefs ->
        LocalSecrets.decrypt(prefs[SettingsKeys.BACKUP_PASSWORD_ENC] ?: "")
    }

    override val noteHistoryEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.NOTE_HISTORY_ENABLED] ?: true }
    override val taskHistoryEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.TASK_HISTORY_ENABLED] ?: true }
    override suspend fun setNoteHistoryEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.NOTE_HISTORY_ENABLED] = value }
        SettingsCache.noteHistoryEnabled = value
    }
    override suspend fun setTaskHistoryEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.TASK_HISTORY_ENABLED] = value }
        SettingsCache.taskHistoryEnabled = value
    }

    override val autoBackup: Flow<AutoBackup.State> = context.settingsDataStore.data
        .map { AutoBackup.State.fromJson(it[SettingsKeys.AUTO_BACKUP] ?: "") }
    override suspend fun autoBackupOnce(): AutoBackup.State =
        AutoBackup.State.fromJson(context.settingsDataStore.data.first()[SettingsKeys.AUTO_BACKUP] ?: "")
    override suspend fun setAutoBackup(state: AutoBackup.State) {
        SettingsCache.autoBackup = state
        context.settingsDataStore.edit { it[SettingsKeys.AUTO_BACKUP] = state.toJson() }
    }

    override val autoBackupPasswords: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        val rawJson = prefs[SettingsKeys.AUTO_BACKUP_PASSWORDS_ENC] ?: ""
        if (rawJson.isBlank()) emptyList()
        else {
            try {
                val encList = kotlinx.serialization.json.Json.decodeFromString<List<String>>(rawJson)
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
        context.settingsDataStore.edit { prefs ->
            val rawJson = prefs[SettingsKeys.AUTO_BACKUP_PASSWORDS_ENC] ?: ""
            val current = try {
                if (rawJson.isBlank()) mutableListOf()
                else kotlinx.serialization.json.Json.decodeFromString<List<String>>(rawJson)
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
                prefs.remove(SettingsKeys.AUTO_BACKUP_PASSWORDS_ENC)
            } else {
                val encList = updated.map { LocalSecrets.encrypt(it) }
                prefs[SettingsKeys.AUTO_BACKUP_PASSWORDS_ENC] = kotlinx.serialization.json.Json.encodeToString(encList)
            }
        }
    }

    override val autoBackupRecoveryQuestion: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.AUTO_BACKUP_RECOVERY_Q] ?: ""
    }

    override suspend fun autoBackupRecoveryQuestionOnce(): String = autoBackupRecoveryQuestion.first()

    override val autoBackupRecoveryAnswer: Flow<String> = context.settingsDataStore.data.map { prefs ->
        val enc = prefs[SettingsKeys.AUTO_BACKUP_RECOVERY_A_ENC] ?: ""
        if (enc.isEmpty()) "" else LocalSecrets.decrypt(enc)
    }

    override suspend fun autoBackupRecoveryAnswerOnce(): String = autoBackupRecoveryAnswer.first()

    override suspend fun setAutoBackupRecovery(question: String, answer: String) {
        context.settingsDataStore.edit { prefs ->
            if (question.isBlank()) {
                prefs.remove(SettingsKeys.AUTO_BACKUP_RECOVERY_Q)
                prefs.remove(SettingsKeys.AUTO_BACKUP_RECOVERY_A_ENC)
            } else {
                prefs[SettingsKeys.AUTO_BACKUP_RECOVERY_Q] = question
                prefs[SettingsKeys.AUTO_BACKUP_RECOVERY_A_ENC] = LocalSecrets.encrypt(answer)
            }
        }
    }

    override val notesSort: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.NOTES_SORT] ?: "recent" }
    override val tasksSort: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.TASKS_SORT] ?: "recent" }
    override val notebooksSort: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.NOTEBOOKS_SORT] ?: "recent" }
    override suspend fun setNotesSort(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.NOTES_SORT] = value }
        SettingsCache.notesSort = value
    }
    override suspend fun setTasksSort(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.TASKS_SORT] = value }
        SettingsCache.tasksSort = value
    }
    override suspend fun setNotebooksSort(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.NOTEBOOKS_SORT] = value }
        SettingsCache.notebooksSort = value
    }

    override val notebookOpens: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.NOTEBOOK_OPENS_ENC]?.let { LocalSecrets.decrypt(it) } ?: "{}"
    }
    override suspend fun notebookOpensOnce(): String = notebookOpens.first()
    override suspend fun setNotebookOpens(value: String) {
        putSecret(SettingsKeys.NOTEBOOK_OPENS_ENC, SettingsKeys.NOTEBOOK_OPENS_LEGACY, value)
    }

    override val memoryTier: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.MEMORY_TIER] ?: MemoryTier.DEFAULT.key
    }
    override suspend fun setMemoryTier(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.MEMORY_TIER] = value }
        SettingsCache.memoryTier = value
    }

    override val webSearchEnabled: Flow<Boolean> = context.settingsDataStore.data.map {
        it[SettingsKeys.WEB_SEARCH_ENABLED] ?: false
    }
    override suspend fun setWebSearchEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.WEB_SEARCH_ENABLED] = value }
        SettingsCache.webSearchEnabled = value
    }

    override val typingHapticsEnabled: Flow<Boolean> = context.settingsDataStore.data.map {
        it[SettingsKeys.TYPING_HAPTICS] ?: true
    }
    override suspend fun setTypingHapticsEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.TYPING_HAPTICS] = value }
        SettingsCache.typingHapticsEnabled = value
    }

    override val assistantConfirmToolsEnabled: Flow<Boolean> = context.settingsDataStore.data.map {
        it[SettingsKeys.ASSISTANT_CONFIRM_TOOLS] ?: true
    }

    override suspend fun setAssistantConfirmTools(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.ASSISTANT_CONFIRM_TOOLS] = value }
        SettingsCache.assistantConfirmToolsEnabled = value
    }

    override val updateChannel: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.UPDATE_CHANNEL_ENC, SettingsKeys.LEGACY_UPDATE_CHANNEL, "stable")
    }
    override suspend fun setUpdateChannel(value: String) {
        SettingsCache.updateChannel = value
        putSecret(SettingsKeys.UPDATE_CHANNEL_ENC, SettingsKeys.LEGACY_UPDATE_CHANNEL, value)
    }

    override val installedPreviewIdentity: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.INSTALLED_PREVIEW_IDENTITY] ?: "" }
    override suspend fun setInstalledPreviewIdentity(value: String) {
        SettingsCache.installedPreviewIdentity = value
        context.settingsDataStore.edit { it[SettingsKeys.INSTALLED_PREVIEW_IDENTITY] = value }
    }

    override suspend fun setStagedUpdateIdentity(value: String) {
        SettingsCache.stagedUpdateIdentity = value
        context.settingsDataStore.edit { it[SettingsKeys.STAGED_UPDATE_IDENTITY] = value }
    }

    override val autoUpdateEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.AUTO_UPDATE_ENABLED] ?: false }
    override suspend fun setAutoUpdateEnabled(value: Boolean) {
        SettingsCache.autoUpdateEnabled = value
        context.settingsDataStore.edit { it[SettingsKeys.AUTO_UPDATE_ENABLED] = value }
    }

    override val pendingUpdateVersion: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.PENDING_UPDATE_VERSION] ?: ""
    }
    override suspend fun setPendingUpdateVersion(value: String) {
        context.settingsDataStore.edit {
            if (value.isBlank()) it.remove(SettingsKeys.PENDING_UPDATE_VERSION)
            else it[SettingsKeys.PENDING_UPDATE_VERSION] = value
        }
    }

    override suspend fun setStagedUpdate(tag: String, files: List<String>) {
        context.settingsDataStore.edit {
            if (tag.isBlank()) {
                it.remove(SettingsKeys.STAGED_UPDATE_TAG)
                it.remove(SettingsKeys.STAGED_UPDATE_FILES)
            } else {
                it[SettingsKeys.STAGED_UPDATE_TAG] = tag
                it[SettingsKeys.STAGED_UPDATE_FILES] = files.joinToString(",")
            }
        }
    }

    override val privilegedEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.PRIVILEGED_ENABLED] ?: false }
    override suspend fun setPrivilegedEnabled(value: Boolean) {
        SettingsCache.privilegedEnabled = value
        context.settingsDataStore.edit { it[SettingsKeys.PRIVILEGED_ENABLED] = value }
    }

    override val markdownEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.MARKDOWN_ENABLED] ?: false }
    override val richTextEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.RICH_TEXT_ENABLED] ?: false }
    override suspend fun setMarkdownEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.MARKDOWN_ENABLED] = value }
        SettingsCache.markdownEnabled = value
    }
    override suspend fun setRichTextEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.RICH_TEXT_ENABLED] = value }
        SettingsCache.richTextEnabled = value
    }

    override val savedSearches: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.SAVED_SEARCHES] ?: "" }
    override suspend fun setSavedSearches(json: String) {
        context.settingsDataStore.edit { it[SettingsKeys.SAVED_SEARCHES] = json }
    }

    override val harnessConfig: Flow<String> = context.settingsDataStore.data.map {
        val stored = it[SettingsKeys.HARNESS_CONFIG_ENC] ?: return@map ""
        LocalSecrets.decrypt(stored)
    }

    override suspend fun setHarnessConfig(json: String) {
        SettingsCache.harnessConfigJson = json
        val sealed = LocalSecrets.encrypt(json)
        context.settingsDataStore.edit { it[SettingsKeys.HARNESS_CONFIG_ENC] = sealed }
    }

    override suspend fun harnessConfigOnce(): String =
        LocalSecrets.decrypt(context.settingsDataStore.data.first()[SettingsKeys.HARNESS_CONFIG_ENC] ?: "")

    override val customTemplatesJson: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CUSTOM_TEMPLATES] ?: "[]" }
    override suspend fun setCustomTemplatesJson(json: String) {
        context.settingsDataStore.edit { it[SettingsKeys.CUSTOM_TEMPLATES] = json }
    }
    override val templateDraftJson: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.TEMPLATE_DRAFT] ?: "" }
    override suspend fun setTemplateDraftJson(json: String) {
        context.settingsDataStore.edit { it[SettingsKeys.TEMPLATE_DRAFT] = json }
    }
    override val hiddenTemplatesJson: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.HIDDEN_TEMPLATES] ?: "" }
    override suspend fun setHiddenTemplatesJson(json: String) {
        context.settingsDataStore.edit { it[SettingsKeys.HIDDEN_TEMPLATES] = json }
    }
    override val cloudEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_ENABLED] ?: false }
    override suspend fun setCloudEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_ENABLED] = value }
        SettingsCache.cloudEnabled = value
    }
    override val cloudProvider: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_PROVIDER] ?: "Nutstore" }
    override suspend fun setCloudProvider(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_PROVIDER] = value }
        SettingsCache.cloudProvider = value
    }
    override val embeddingProvider: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.EMBEDDING_PROVIDER] ?: "local"
    }
    override suspend fun setEmbeddingProvider(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.EMBEDDING_PROVIDER] = value }
        SettingsCache.embeddingProvider = value
    }
    override val cloudUrl: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_URL] ?: "" }
    override suspend fun setCloudUrl(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_URL] = value }
        SettingsCache.cloudUrl = value
    }
    override val cloudUser: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_USER] ?: "" }
    override suspend fun setCloudUser(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_USER] = value }
        SettingsCache.cloudUser = value
    }
    override val cloudPasswordEnc: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_PASSWORD_ENC] ?: "" }
    override suspend fun setCloudPasswordEnc(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_PASSWORD_ENC] = value }
        SettingsCache.cloudPasswordEnc = value
    }
    override val cloudFolder: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_FOLDER] ?: "Lucent" }
    override suspend fun setCloudFolder(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_FOLDER] = value }
        SettingsCache.cloudFolder = value
    }
    override val cloudAutoBackup: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_AUTO_BACKUP] ?: false }
    override suspend fun setCloudAutoBackup(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_AUTO_BACKUP] = value }
        SettingsCache.cloudAutoBackup = value
    }

    override val linksEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.LINKS_ENABLED] ?: false }
    override suspend fun setLinksEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.LINKS_ENABLED] = value }
        SettingsCache.linksEnabled = value
    }

    override val backgroundAnimationEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] ?: true }
    override suspend fun setBackgroundAnimationEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] = value }
        SettingsCache.backgroundAnimationEnabled = value
    }

    override val splashEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.SPLASH_ENABLED] ?: true }
    override suspend fun setSplashEnabled(value: Boolean) {
        SettingsCache.splashEnabled = value
        context.settingsDataStore.edit { it[SettingsKeys.SPLASH_ENABLED] = value }
    }

    override val splashStyle: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.SPLASH_STYLE] ?: SplashStyle.DEFAULT.key
    }
    override suspend fun setSplashStyle(value: String) {
        SettingsCache.splashStyle = SplashStyle.fromKey(value).key
        context.settingsDataStore.edit { it[SettingsKeys.SPLASH_STYLE] = SplashStyle.fromKey(value).key }
    }

    override val appLockEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.APP_LOCK_ENABLED] ?: false }
    override val appLockCredentials: Flow<String> = context.settingsDataStore.data.map { prefs ->
        LocalSecrets.decrypt(prefs[SettingsKeys.APP_LOCK_CREDENTIALS_ENC] ?: "")
    }

    override suspend fun appLockEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.APP_LOCK_ENABLED] ?: false
    override suspend fun appLockCredentialsOnce(): String =
        LocalSecrets.decrypt(context.settingsDataStore.data.first()[SettingsKeys.APP_LOCK_CREDENTIALS_ENC] ?: "")
    override suspend fun startupLoggingEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.STARTUP_LOGGING_ENABLED] ?: false

    override suspend fun setAppLock(enabled: Boolean, credentialsJson: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.APP_LOCK_ENABLED] = enabled
            if (enabled && credentialsJson.isNotEmpty()) {
                prefs[SettingsKeys.APP_LOCK_CREDENTIALS_ENC] = LocalSecrets.encrypt(credentialsJson)
            } else if (!enabled) {
                prefs.remove(SettingsKeys.APP_LOCK_CREDENTIALS_ENC)
                prefs.remove(SettingsKeys.APP_LOCK_BIOMETRIC_ENABLED)
            }
        }
    }

    override suspend fun setAppLockCredentials(credentialsJson: String) {
        context.settingsDataStore.edit { it[SettingsKeys.APP_LOCK_CREDENTIALS_ENC] = LocalSecrets.encrypt(credentialsJson) }
    }

    override val appLockBiometricEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.APP_LOCK_BIOMETRIC_ENABLED] ?: false }
    override suspend fun setAppLockBiometricEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.APP_LOCK_BIOMETRIC_ENABLED] = enabled }
    }

    override val appLockHelloEnabled: Flow<Boolean> =
        kotlinx.coroutines.flow.flowOf(false)
    override suspend fun setAppLockHelloEnabled(value: Boolean) { }

    override val closeToTray: Flow<Boolean> =
        kotlinx.coroutines.flow.flowOf(true)
    override suspend fun setCloseToTray(value: Boolean) { }

    override val systemIntegrationEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.SYSTEM_INTEGRATION_ENABLED] ?: false }
    override suspend fun systemIntegrationEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.SYSTEM_INTEGRATION_ENABLED] ?: false
    override suspend fun setSystemIntegrationEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.SYSTEM_INTEGRATION_ENABLED] = value }
        SettingsCache.systemIntegrationEnabled = value
    }

    override val startupLoggingEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.STARTUP_LOGGING_ENABLED] ?: false }
    override suspend fun setStartupLoggingEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.STARTUP_LOGGING_ENABLED] = value }
        SettingsCache.startupLoggingEnabled = value
    }

    private suspend fun putSecret(
        encrypted: androidx.datastore.preferences.core.Preferences.Key<String>,
        legacy: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: String
    ) {
        val sealed = LocalSecrets.encrypt(value)
        context.settingsDataStore.edit { prefs ->
            prefs[encrypted] = sealed
            prefs.remove(legacy)
        }
    }

    override suspend fun setBaseUrl(value: String) {
        SettingsCache.baseUrl = value
        putSecret(SettingsKeys.BASE_URL_ENC, SettingsKeys.LEGACY_BASE_URL, value)
    }
    override suspend fun setApiSpec(value: String) {
        SettingsCache.apiSpec = value
        putSecret(SettingsKeys.API_SPEC_ENC, SettingsKeys.LEGACY_API_SPEC, value)
    }
    override suspend fun setModel(value: String) {
        SettingsCache.model = value
        putSecret(SettingsKeys.MODEL_ENC, SettingsKeys.LEGACY_MODEL, value)
    }


    override val modelRecents: Flow<List<String>> =
        context.settingsDataStore.data.map { ModelRecents.parse(it[SettingsKeys.MODEL_RECENTS]) }

    override suspend fun setActiveModel(value: String) {
        val model = value.trim()
        if (model.isBlank()) return
        val prefs = context.settingsDataStore.data.first()
        val profilesJson = LocalSecrets.decrypt(
            prefs[SettingsKeys.API_PROFILES_ENC] ?: prefs[SettingsKeys.LEGACY_API_PROFILES] ?: ""
        )
        val profiles = ApiProfiles.parse(profilesJson)
        val selected = (prefs[SettingsKeys.API_PROFILE_SELECTED] ?: 0)
        val profilesEnc = if (profiles.isEmpty()) null else {
            val idx = selected.coerceIn(0, profiles.size - 1)
            LocalSecrets.encrypt(
                ApiProfiles.serialize(profiles.mapIndexed { i, p -> if (i == idx) p.copy(model = model) else p })
            )
        }
        val modelEnc = LocalSecrets.encrypt(model)
        val recents = ModelRecents.add(prefs[SettingsKeys.MODEL_RECENTS], model)

        context.settingsDataStore.edit { p ->
            p[SettingsKeys.MODEL_ENC] = modelEnc
            p.remove(SettingsKeys.LEGACY_MODEL)
            if (profilesEnc != null) {
                p[SettingsKeys.API_PROFILES_ENC] = profilesEnc
                p.remove(SettingsKeys.LEGACY_API_PROFILES)
            }
            p[SettingsKeys.MODEL_RECENTS] = recents
        }
        SettingsCache.model = model
    }

    override val terminalFontSize: Flow<Float?> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.TERMINAL_FONT_SIZE_ENC, SettingsKeys.LEGACY_TERMINAL_FONT_SIZE, "").toFloatOrNull()
    }
    override val terminalKeyBarVisible: Flow<Boolean> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.TERMINAL_KEY_BAR_VISIBLE_ENC, SettingsKeys.LEGACY_TERMINAL_KEY_BAR_VISIBLE, "true").toBooleanStrictOrNull() ?: true
    }
    override val globalTextSelectionEnabled: Flow<Boolean> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.GLOBAL_TEXT_SELECTION_ENABLED_ENC, SettingsKeys.LEGACY_GLOBAL_TEXT_SELECTION_ENABLED, "false").toBooleanStrictOrNull() ?: false
    }

    override suspend fun setTerminalFontSize(value: Float) {
        SettingsCache.terminalFontSize = value
        putSecret(SettingsKeys.TERMINAL_FONT_SIZE_ENC, SettingsKeys.LEGACY_TERMINAL_FONT_SIZE, value.toString())
    }
    override suspend fun setTerminalKeyBarVisible(value: Boolean) {
        SettingsCache.terminalKeyBarVisible = value
        putSecret(SettingsKeys.TERMINAL_KEY_BAR_VISIBLE_ENC, SettingsKeys.LEGACY_TERMINAL_KEY_BAR_VISIBLE, value.toString())
    }
    override suspend fun setGlobalTextSelectionEnabled(value: Boolean) {
        SettingsCache.globalTextSelectionEnabled = value
        putSecret(SettingsKeys.GLOBAL_TEXT_SELECTION_ENABLED_ENC, SettingsKeys.LEGACY_GLOBAL_TEXT_SELECTION_ENABLED, value.toString())
    }

    override suspend fun setAssistantName(value: String) {
        SettingsCache.assistantName = value
        putSecret(SettingsKeys.ASSISTANT_NAME_ENC, SettingsKeys.LEGACY_ASSISTANT_NAME, value)
    }
    override suspend fun setAssistantStyle(value: String) {
        SettingsCache.assistantStyle = value
        putSecret(SettingsKeys.ASSISTANT_STYLE_ENC, SettingsKeys.LEGACY_ASSISTANT_STYLE, value)
    }

    override suspend fun setThemeMode(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.THEME_MODE] = value }
        SettingsCache.themeMode = value
    }
    override suspend fun setPalette(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.PALETTE] = value }
        SettingsCache.palette = value
    }
    override suspend fun setFont(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.FONT] = value }
        SettingsCache.font = value
    }
    override suspend fun setDynamicColorEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.DYNAMIC_COLOR_ENABLED] = value }
        SettingsCache.dynamicColor = value
    }
    override suspend fun setAttachmentsMigrated(value: Boolean) { context.settingsDataStore.edit { it[SettingsKeys.ATTACHMENTS_MIGRATED] = value } }
    override suspend fun setBackupPassword(value: String) {
        context.settingsDataStore.edit { prefs ->
            if (value.isEmpty()) prefs.remove(SettingsKeys.BACKUP_PASSWORD_ENC)
            else prefs[SettingsKeys.BACKUP_PASSWORD_ENC] = LocalSecrets.encrypt(value)
        }
    }

    override suspend fun setSessionSnapshot(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.SESSION_SNAPSHOT] = value }
    }

    override suspend fun sessionSnapshotOnce(): String =
        context.settingsDataStore.data.first()[SettingsKeys.SESSION_SNAPSHOT] ?: ""

    override suspend fun setApiKey(value: String) {
        SettingsCache.apiKey = value
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.API_KEY_ENC] = LocalSecrets.encrypt(value)
            prefs.remove(SettingsKeys.LEGACY_API_KEY)
        }
    }

    override suspend fun saveApiProfiles(profiles: List<ApiProfile>, selected: Int) {
        val safe = profiles.take(ApiProfiles.MAX)
        val idx = if (safe.isEmpty()) 0 else selected.coerceIn(0, safe.size - 1)
        val active = safe.getOrNull(idx)
        val profilesJson = ApiProfiles.serialize(safe)
        val profilesEnc = LocalSecrets.encrypt(profilesJson)
        SettingsCache.apiProfilesJson = profilesJson
        SettingsCache.apiProfileSelected = idx
        SettingsCache.baseUrl = active?.baseUrl ?: ""
        SettingsCache.apiSpec = active?.spec ?: "openai"
        SettingsCache.model = active?.model ?: ""
        active?.apiKey?.let { SettingsCache.apiKey = it }
        val activeKeyEnc = active?.let { LocalSecrets.encrypt(it.apiKey) }
        val activeBaseUrlEnc = LocalSecrets.encrypt(active?.baseUrl ?: "")
        val activeSpecEnc = LocalSecrets.encrypt(active?.spec ?: "openai")
        val activeModelEnc = LocalSecrets.encrypt(active?.model ?: "")

        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.API_PROFILES_ENC] = profilesEnc
            prefs[SettingsKeys.API_PROFILE_SELECTED] = idx
            prefs.remove(SettingsKeys.LEGACY_API_PROFILES)
            if (active != null) {
                prefs[SettingsKeys.BASE_URL_ENC] = activeBaseUrlEnc
                prefs[SettingsKeys.API_SPEC_ENC] = activeSpecEnc
                prefs[SettingsKeys.MODEL_ENC] = activeModelEnc
                if (activeKeyEnc != null) prefs[SettingsKeys.API_KEY_ENC] = activeKeyEnc
                prefs.remove(SettingsKeys.LEGACY_API_KEY)
                prefs.remove(SettingsKeys.LEGACY_BASE_URL)
                prefs.remove(SettingsKeys.LEGACY_API_SPEC)
                prefs.remove(SettingsKeys.LEGACY_MODEL)
            } else {
                prefs.remove(SettingsKeys.BASE_URL_ENC)
                prefs.remove(SettingsKeys.MODEL_ENC)
                prefs.remove(SettingsKeys.API_KEY_ENC)
                prefs.remove(SettingsKeys.LEGACY_API_KEY)
                prefs.remove(SettingsKeys.LEGACY_BASE_URL)
                prefs.remove(SettingsKeys.LEGACY_MODEL)
            }
        }
    }

    override suspend fun clearAll() { context.settingsDataStore.edit { it.clear() } }
}

actual fun createSettingsRepository(context: PlatformContext): SettingsRepository =
    AndroidSettingsRepository(context)
