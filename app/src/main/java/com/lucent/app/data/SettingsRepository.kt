package com.lucent.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    val LEGACY_API_KEY = stringPreferencesKey("api_key")
    val LEGACY_API_PROFILES = stringPreferencesKey("api_profiles_json")

    val API_PROFILE_SELECTED = intPreferencesKey("api_profile_selected")

    val NOTES_SORT = stringPreferencesKey("notes_sort")
    val SESSION_SNAPSHOT = stringPreferencesKey("session_snapshot")
    val TASKS_SORT = stringPreferencesKey("tasks_sort")

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
}

const val DEFAULT_ASSISTANT_STYLE = "lively and friendly, relaxed and natural."

class SettingsRepository(private val context: Context) {

    private fun secret(
        prefs: androidx.datastore.preferences.core.Preferences,
        encrypted: androidx.datastore.preferences.core.Preferences.Key<String>,
        legacy: androidx.datastore.preferences.core.Preferences.Key<String>,
        default: String
    ): String {
        val stored = prefs[encrypted] ?: prefs[legacy] ?: return default
        return LocalSecrets.decrypt(stored).ifEmpty { default }
    }

    val baseUrl: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.BASE_URL_ENC, SettingsKeys.LEGACY_BASE_URL, "")
    }
    val apiSpec: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.API_SPEC_ENC, SettingsKeys.LEGACY_API_SPEC, "openai")
    }
    val model: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.MODEL_ENC, SettingsKeys.LEGACY_MODEL, "")
    }
    val assistantName: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.ASSISTANT_NAME_ENC, SettingsKeys.LEGACY_ASSISTANT_NAME, "Lucent")
    }
    val assistantStyle: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.ASSISTANT_STYLE_ENC, SettingsKeys.LEGACY_ASSISTANT_STYLE, "")
    }

    val themeMode: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.THEME_MODE] ?: "system" }
    val palette: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.PALETTE] ?: "CYCLE" }
    val font: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.FONT] ?: "system" }

    val dynamicColorEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.DYNAMIC_COLOR_ENABLED] ?: false }

    data class DisplayPrefs(val themeMode: String, val palette: String, val font: String)

    suspend fun displayPrefsOnce(): DisplayPrefs {
        val prefs = context.settingsDataStore.data.first()
        return DisplayPrefs(
            themeMode = prefs[SettingsKeys.THEME_MODE] ?: "system",
            palette = prefs[SettingsKeys.PALETTE] ?: "CYCLE",
            font = prefs[SettingsKeys.FONT] ?: "system"
        )
    }

    data class StartupPrefs(
        val display: DisplayPrefs,
        val appLockEnabled: Boolean,
        val startupLoggingEnabled: Boolean,
        val systemIntegrationEnabled: Boolean,
        val appLanguage: String = "system",
        val assistantName: String = "Lucent",
        val backgroundAnimationEnabled: Boolean = true,
        val dynamicColor: Boolean = false,
        val notesSort: String = "recent",
        val tasksSort: String = "recent",
        val sessionSnapshot: String = ""
    )

    suspend fun startupPrefsOnce(): StartupPrefs {
        val prefs = context.settingsDataStore.data.first()
        return StartupPrefs(
            display = DisplayPrefs(
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
            dynamicColor = prefs[SettingsKeys.DYNAMIC_COLOR_ENABLED] ?: false,
            notesSort = prefs[SettingsKeys.NOTES_SORT] ?: "recent",
            tasksSort = prefs[SettingsKeys.TASKS_SORT] ?: "recent",
            sessionSnapshot = prefs[SettingsKeys.SESSION_SNAPSHOT] ?: ""
        )
    }


    val lastScreen: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.LAST_SCREEN] ?: "" }
    suspend fun lastScreenOnce(): String = context.settingsDataStore.data.first()[SettingsKeys.LAST_SCREEN] ?: ""
    suspend fun setLastScreen(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.LAST_SCREEN] = value }
    }

    val blackoutEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.BLACKOUT_ENABLED] ?: false }
    suspend fun blackoutEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.BLACKOUT_ENABLED] ?: false

    suspend fun setBlackoutEnabled(value: Boolean) {
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
    }

    suspend fun appLockWasOnBeforeBlackout(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.APP_LOCK_PREBLACKOUT] ?: false

    val crashShieldEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.CRASH_SHIELD_ENABLED] ?: false }
    suspend fun crashShieldEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.CRASH_SHIELD_ENABLED] ?: false

    suspend fun setCrashShieldEnabled(value: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.CRASH_SHIELD_ENABLED] = value
            if (value) prefs[SettingsKeys.STARTUP_LOGGING_ENABLED] = true
        }
    }

    val passwordAttemptState: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.PW_ATTEMPT_STATE] ?: "" }
    suspend fun passwordAttemptStateOnce(): String =
        context.settingsDataStore.data.first()[SettingsKeys.PW_ATTEMPT_STATE] ?: ""
    suspend fun setPasswordAttemptState(json: String) {
        context.settingsDataStore.edit { it[SettingsKeys.PW_ATTEMPT_STATE] = json }
    }

    val pwFirstRoundLimit: Flow<Int> = context.settingsDataStore.data.map {
        it[SettingsKeys.PW_FIRST_ROUND_LIMIT] ?: PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT
    }
    val pwLaterRoundLimit: Flow<Int> = context.settingsDataStore.data.map {
        it[SettingsKeys.PW_LATER_ROUND_LIMIT] ?: PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT
    }
    suspend fun setPwFirstRoundLimit(value: Int) {
        context.settingsDataStore.edit {
            it[SettingsKeys.PW_FIRST_ROUND_LIMIT] = value.coerceIn(PasswordAttempts.ROUND_LIMIT_RANGE)
        }
    }
    suspend fun setPwLaterRoundLimit(value: Int) {
        context.settingsDataStore.edit {
            it[SettingsKeys.PW_LATER_ROUND_LIMIT] = value.coerceIn(PasswordAttempts.ROUND_LIMIT_RANGE)
        }
    }

    val pwSelfDestructEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.PW_SELF_DESTRUCT_ENABLED] ?: false }
    val pwSelfDestructThreshold: Flow<Int> = context.settingsDataStore.data.map {
        it[SettingsKeys.PW_SELF_DESTRUCT_THRESHOLD] ?: PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD
    }
    suspend fun setPwSelfDestructEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.PW_SELF_DESTRUCT_ENABLED] = value }
    }
    suspend fun setPwSelfDestructThreshold(value: Int) {
        context.settingsDataStore.edit {
            it[SettingsKeys.PW_SELF_DESTRUCT_THRESHOLD] = value.coerceIn(PasswordAttempts.SELF_DESTRUCT_RANGE)
        }
    }

    val openLinksExternally: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.OPEN_LINKS_EXTERNALLY] ?: false }
    suspend fun setOpenLinksExternally(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.OPEN_LINKS_EXTERNALLY] = value }
    }

    val appLanguage: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.APP_LANGUAGE] ?: "system" }
    suspend fun setAppLanguage(value: String) { context.settingsDataStore.edit { it[SettingsKeys.APP_LANGUAGE] = value } }
    suspend fun appLanguageOnce(): String =
        context.settingsDataStore.data.first()[SettingsKeys.APP_LANGUAGE] ?: "system"

    val localModelEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.LOCAL_MODEL_ENABLED] ?: false }

    suspend fun setLocalModelEnabled(value: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val wasEnabled = prefs[SettingsKeys.LOCAL_MODEL_ENABLED] ?: false
            prefs[SettingsKeys.LOCAL_MODEL_ENABLED] = value
            if (value) {
                if (!wasEnabled) {
                    prefs[SettingsKeys.MEMORY_TIER_PRELOCAL] =
                        prefs[SettingsKeys.MEMORY_TIER] ?: MemoryTier.DEFAULT.key
                    prefs[SettingsKeys.WEB_SEARCH_PRELOCAL] = prefs[SettingsKeys.WEB_SEARCH_ENABLED] ?: false
                }
                prefs[SettingsKeys.MEMORY_TIER] = MemoryTier.LOW.key
                prefs[SettingsKeys.WEB_SEARCH_ENABLED] = false
                prefs[SettingsKeys.LOCAL_TOOLS_ENABLED] = false
                prefs[SettingsKeys.LOCAL_GPU_ENABLED] = false
            } else {
                prefs[SettingsKeys.MEMORY_TIER] =
                    prefs[SettingsKeys.MEMORY_TIER_PRELOCAL] ?: MemoryTier.DEFAULT.key
                prefs[SettingsKeys.WEB_SEARCH_ENABLED] = prefs[SettingsKeys.WEB_SEARCH_PRELOCAL] ?: false
                prefs.remove(SettingsKeys.MEMORY_TIER_PRELOCAL)
                prefs.remove(SettingsKeys.WEB_SEARCH_PRELOCAL)
            }
        }
    }

    val localBackgroundReplyEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.LOCAL_BACKGROUND_REPLY] ?: false }
    suspend fun setLocalBackgroundReplyEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.LOCAL_BACKGROUND_REPLY] = value }
    }

    suspend fun localBackgroundReplyEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.LOCAL_BACKGROUND_REPLY] ?: false

    val smallModelModeEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.SMALL_MODEL_MODE] ?: false }
    suspend fun setSmallModelModeEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.SMALL_MODEL_MODE] = value }
    }

    val localToolsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.LOCAL_TOOLS_ENABLED] ?: false }
    suspend fun setLocalToolsEnabled(value: Boolean) { context.settingsDataStore.edit { it[SettingsKeys.LOCAL_TOOLS_ENABLED] = value } }

    val localGpuEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.LOCAL_GPU_ENABLED] ?: false }
    suspend fun setLocalGpuEnabled(value: Boolean) { context.settingsDataStore.edit { it[SettingsKeys.LOCAL_GPU_ENABLED] = value } }

    val apiKey: Flow<String> = context.settingsDataStore.data.map { prefs ->
        val stored = prefs[SettingsKeys.API_KEY_ENC] ?: prefs[SettingsKeys.LEGACY_API_KEY] ?: ""
        LocalSecrets.decrypt(stored)
    }

    val apiProfilesJson: Flow<String> = context.settingsDataStore.data.map { prefs ->
        val stored = prefs[SettingsKeys.API_PROFILES_ENC] ?: prefs[SettingsKeys.LEGACY_API_PROFILES] ?: ""
        LocalSecrets.decrypt(stored)
    }

    val apiProfileSelected: Flow<Int> = context.settingsDataStore.data.map { it[SettingsKeys.API_PROFILE_SELECTED] ?: 0 }

    val attachmentsMigrated: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.ATTACHMENTS_MIGRATED] ?: false }

    val backupPassword: Flow<String> = context.settingsDataStore.data.map { prefs ->
        LocalSecrets.decrypt(prefs[SettingsKeys.BACKUP_PASSWORD_ENC] ?: "")
    }

    val noteHistoryEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.NOTE_HISTORY_ENABLED] ?: true }
    val taskHistoryEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.TASK_HISTORY_ENABLED] ?: true }
    suspend fun setNoteHistoryEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.NOTE_HISTORY_ENABLED] = value }
    }
    suspend fun setTaskHistoryEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.TASK_HISTORY_ENABLED] = value }
    }

    val autoBackup: Flow<AutoBackup.State> = context.settingsDataStore.data
        .map { AutoBackup.State.fromJson(it[SettingsKeys.AUTO_BACKUP] ?: "") }
    suspend fun autoBackupOnce(): AutoBackup.State =
        AutoBackup.State.fromJson(context.settingsDataStore.data.first()[SettingsKeys.AUTO_BACKUP] ?: "")
    suspend fun setAutoBackup(state: AutoBackup.State) {
        context.settingsDataStore.edit { it[SettingsKeys.AUTO_BACKUP] = state.toJson() }
    }

    val notesSort: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.NOTES_SORT] ?: "recent" }
    val tasksSort: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.TASKS_SORT] ?: "recent" }

    val memoryTier: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.MEMORY_TIER] ?: MemoryTier.DEFAULT.key
    }

    val webSearchEnabled: Flow<Boolean> = context.settingsDataStore.data.map {
        it[SettingsKeys.WEB_SEARCH_ENABLED] ?: false
    }

    val typingHapticsEnabled: Flow<Boolean> = context.settingsDataStore.data.map {
        it[SettingsKeys.TYPING_HAPTICS] ?: true
    }

    val assistantConfirmToolsEnabled: Flow<Boolean> = context.settingsDataStore.data.map {
        it[SettingsKeys.ASSISTANT_CONFIRM_TOOLS] ?: true
    }

    suspend fun setAssistantConfirmTools(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.ASSISTANT_CONFIRM_TOOLS] = value }
    }

    val markdownEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.MARKDOWN_ENABLED] ?: false }
    val richTextEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.RICH_TEXT_ENABLED] ?: false }

    val savedSearches: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.SAVED_SEARCHES] ?: "" }
    suspend fun setSavedSearches(json: String) {
        context.settingsDataStore.edit { it[SettingsKeys.SAVED_SEARCHES] = json }
    }
    val customTemplatesJson: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CUSTOM_TEMPLATES] ?: "[]" }
    suspend fun setCustomTemplatesJson(json: String) {
        context.settingsDataStore.edit { it[SettingsKeys.CUSTOM_TEMPLATES] = json }
    }
    val templateDraftJson: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.TEMPLATE_DRAFT] ?: "" }
    suspend fun setTemplateDraftJson(json: String) {
        context.settingsDataStore.edit { it[SettingsKeys.TEMPLATE_DRAFT] = json }
    }
    val hiddenTemplatesJson: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.HIDDEN_TEMPLATES] ?: "" }
    suspend fun setHiddenTemplatesJson(json: String) {
        context.settingsDataStore.edit { it[SettingsKeys.HIDDEN_TEMPLATES] = json }
    }
    val cloudEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_ENABLED] ?: false }
    suspend fun setCloudEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_ENABLED] = value }
    }
    val cloudProvider: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_PROVIDER] ?: "Nutstore" }
    suspend fun setCloudProvider(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_PROVIDER] = value }
    }
    val embeddingProvider: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.EMBEDDING_PROVIDER] ?: "local"
    }
    suspend fun setEmbeddingProvider(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.EMBEDDING_PROVIDER] = value }
    }
    val cloudUrl: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_URL] ?: "" }
    suspend fun setCloudUrl(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_URL] = value }
    }
    val cloudUser: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_USER] ?: "" }
    suspend fun setCloudUser(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_USER] = value }
    }
    val cloudPasswordEnc: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_PASSWORD_ENC] ?: "" }
    suspend fun setCloudPasswordEnc(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_PASSWORD_ENC] = value }
    }
    val cloudFolder: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_FOLDER] ?: "Lucent" }
    suspend fun setCloudFolder(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_FOLDER] = value }
    }
    val cloudAutoBackup: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_AUTO_BACKUP] ?: false }
    suspend fun setCloudAutoBackup(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.CLOUD_AUTO_BACKUP] = value }
    }

    val linksEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.LINKS_ENABLED] ?: false }

    val backgroundAnimationEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] ?: true }
    suspend fun setBackgroundAnimationEnabled(value: Boolean) { context.settingsDataStore.edit { it[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] = value } }

    val appLockEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.APP_LOCK_ENABLED] ?: false }
    val appLockCredentials: Flow<String> = context.settingsDataStore.data.map { prefs ->
        LocalSecrets.decrypt(prefs[SettingsKeys.APP_LOCK_CREDENTIALS_ENC] ?: "")
    }

    suspend fun appLockEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.APP_LOCK_ENABLED] ?: false
    suspend fun appLockCredentialsOnce(): String =
        LocalSecrets.decrypt(context.settingsDataStore.data.first()[SettingsKeys.APP_LOCK_CREDENTIALS_ENC] ?: "")
    suspend fun startupLoggingEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.STARTUP_LOGGING_ENABLED] ?: false

    suspend fun setAppLock(enabled: Boolean, credentialsJson: String) {
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

    suspend fun setAppLockCredentials(credentialsJson: String) {
        context.settingsDataStore.edit { it[SettingsKeys.APP_LOCK_CREDENTIALS_ENC] = LocalSecrets.encrypt(credentialsJson) }
    }

    val appLockBiometricEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.APP_LOCK_BIOMETRIC_ENABLED] ?: false }
    suspend fun setAppLockBiometricEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.APP_LOCK_BIOMETRIC_ENABLED] = enabled }
    }

    val systemIntegrationEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.SYSTEM_INTEGRATION_ENABLED] ?: false }
    suspend fun systemIntegrationEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.SYSTEM_INTEGRATION_ENABLED] ?: false
    suspend fun setSystemIntegrationEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.SYSTEM_INTEGRATION_ENABLED] = value }
    }

    val startupLoggingEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.STARTUP_LOGGING_ENABLED] ?: false }
    suspend fun setStartupLoggingEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.STARTUP_LOGGING_ENABLED] = value }
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

    suspend fun setBaseUrl(value: String) = putSecret(SettingsKeys.BASE_URL_ENC, SettingsKeys.LEGACY_BASE_URL, value)
    suspend fun setApiSpec(value: String) = putSecret(SettingsKeys.API_SPEC_ENC, SettingsKeys.LEGACY_API_SPEC, value)
    suspend fun setModel(value: String) = putSecret(SettingsKeys.MODEL_ENC, SettingsKeys.LEGACY_MODEL, value)


    val modelRecents: Flow<List<String>> =
        context.settingsDataStore.data.map { ModelRecents.parse(it[SettingsKeys.MODEL_RECENTS]) }

    suspend fun setActiveModel(value: String) {
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
    }
    suspend fun setAssistantName(value: String) = putSecret(SettingsKeys.ASSISTANT_NAME_ENC, SettingsKeys.LEGACY_ASSISTANT_NAME, value)
    suspend fun setAssistantStyle(value: String) = putSecret(SettingsKeys.ASSISTANT_STYLE_ENC, SettingsKeys.LEGACY_ASSISTANT_STYLE, value)

    suspend fun setThemeMode(value: String) { context.settingsDataStore.edit { it[SettingsKeys.THEME_MODE] = value } }
    suspend fun setPalette(value: String) { context.settingsDataStore.edit { it[SettingsKeys.PALETTE] = value } }
    suspend fun setFont(value: String) { context.settingsDataStore.edit { it[SettingsKeys.FONT] = value } }
    suspend fun setDynamicColorEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.DYNAMIC_COLOR_ENABLED] = value }
    }
    suspend fun setAttachmentsMigrated(value: Boolean) { context.settingsDataStore.edit { it[SettingsKeys.ATTACHMENTS_MIGRATED] = value } }
    suspend fun setBackupPassword(value: String) {
        context.settingsDataStore.edit { prefs ->
            if (value.isEmpty()) prefs.remove(SettingsKeys.BACKUP_PASSWORD_ENC)
            else prefs[SettingsKeys.BACKUP_PASSWORD_ENC] = LocalSecrets.encrypt(value)
        }
    }

    suspend fun setNotesSort(value: String) { context.settingsDataStore.edit { it[SettingsKeys.NOTES_SORT] = value } }

    suspend fun setSessionSnapshot(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.SESSION_SNAPSHOT] = value }
    }

    suspend fun sessionSnapshotOnce(): String =
        context.settingsDataStore.data.first()[SettingsKeys.SESSION_SNAPSHOT] ?: ""
    suspend fun setTasksSort(value: String) { context.settingsDataStore.edit { it[SettingsKeys.TASKS_SORT] = value } }
    suspend fun setMarkdownEnabled(value: Boolean) {
        context.settingsDataStore.edit {
            it[SettingsKeys.MARKDOWN_ENABLED] = value
            if (value) it[SettingsKeys.RICH_TEXT_ENABLED] = false
        }
    }

    suspend fun setRichTextEnabled(value: Boolean) {
        context.settingsDataStore.edit {
            it[SettingsKeys.RICH_TEXT_ENABLED] = value
            if (value) it[SettingsKeys.MARKDOWN_ENABLED] = false
        }
    }
    suspend fun setLinksEnabled(value: Boolean) { context.settingsDataStore.edit { it[SettingsKeys.LINKS_ENABLED] = value } }

    suspend fun setMemoryTier(value: String) { context.settingsDataStore.edit { it[SettingsKeys.MEMORY_TIER] = value } }
    suspend fun setWebSearchEnabled(value: Boolean) { context.settingsDataStore.edit { it[SettingsKeys.WEB_SEARCH_ENABLED] = value } }
    suspend fun setTypingHapticsEnabled(value: Boolean) { context.settingsDataStore.edit { it[SettingsKeys.TYPING_HAPTICS] = value } }

    suspend fun setApiKey(value: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.API_KEY_ENC] = LocalSecrets.encrypt(value)
            prefs.remove(SettingsKeys.LEGACY_API_KEY)
        }
    }

    suspend fun saveApiProfiles(profiles: List<ApiProfile>, selected: Int) {
        val safe = profiles.take(ApiProfiles.MAX)
        val idx = if (safe.isEmpty()) 0 else selected.coerceIn(0, safe.size - 1)
        val active = safe.getOrNull(idx)
        val profilesJson = ApiProfiles.serialize(safe)
        val profilesEnc = LocalSecrets.encrypt(profilesJson)
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

    suspend fun clearAll() { context.settingsDataStore.edit { it.clear() } }
}
