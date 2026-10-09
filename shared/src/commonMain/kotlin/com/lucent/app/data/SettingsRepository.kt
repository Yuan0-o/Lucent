package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import kotlinx.coroutines.flow.Flow

const val DEFAULT_ASSISTANT_STYLE = "lively and friendly, relaxed and natural."

interface SettingsRepository {

    data class DisplayPrefs(val themeMode: String, val palette: String, val font: String)

    data class StartupPrefs(
        val display: DisplayPrefs,
        val appLockEnabled: Boolean,
        val startupLoggingEnabled: Boolean,
        val systemIntegrationEnabled: Boolean,
        val appLanguage: String = "system",
        val assistantName: String = "Lucent",
        val backgroundAnimationEnabled: Boolean = true,
        val splashEnabled: Boolean = true,
        val splashStyle: String = SplashStyle.DEFAULT.key,
        val autoBackup: AutoBackup.State = AutoBackup.State.EMPTY,
        val dynamicColor: Boolean = false,
        val notesSort: String = "recent",
        val tasksSort: String = "recent",
        val notebooksSort: String = "recent",
        val sessionSnapshot: String = "",
        val assistantStyle: String = "",
        val baseUrl: String = "",
        val apiSpec: String = "openai",
        val apiKey: String = "",
        val model: String = "",
        val apiProfilesJson: String = "",
        val apiProfileSelected: Int = 0,
        val noteHistoryEnabled: Boolean = true,
        val taskHistoryEnabled: Boolean = true,
        val crashShieldEnabled: Boolean = false,
        val blackoutEnabled: Boolean = false,
        val pwSelfDestructEnabled: Boolean = false,
        val pwFirstRoundLimit: Int = PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT,
        val pwLaterRoundLimit: Int = PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT,
        val pwSelfDestructThreshold: Int = PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD,
        val passwordAttemptState: String = "",
        val appLockBiometricEnabled: Boolean = false,
        val appLockHelloEnabled: Boolean = false,
        val closeToTray: Boolean = true,
        val openLinksExternally: Boolean = false,
        val markdownEnabled: Boolean = false,
        val richTextEnabled: Boolean = false,
        val linksEnabled: Boolean = false,
        val typingHapticsEnabled: Boolean = true,
        val assistantConfirmToolsEnabled: Boolean = true,
        val localModelEnabled: Boolean = false,
        val localToolsEnabled: Boolean = false,
        val localGpuEnabled: Boolean = false,
        val localBackgroundReplyEnabled: Boolean = false,
        val agentMode: Boolean = true,
        val reasoning: String = ReasoningEffort.DEFAULT.key,
        val webSearchEngine: String = WebSearchEngine.DEFAULT.key,
        val smallModelModeEnabled: Boolean = false,
        val webSearchEnabled: Boolean = false,
        val memoryTier: String = MemoryTier.DEFAULT.key,
        val memoryTierLocal: String = MemoryTier.LOW.key,
        val embeddingProvider: String = "local",
        val cloudEnabled: Boolean = false,
        val cloudProvider: String = "Nutstore",
        val cloudUrl: String = "",
        val cloudUser: String = "",
        val cloudFolder: String = "Lucent",
        val cloudAutoBackup: Boolean = false,
        val terminalFontSize: Float? = null,
        val terminalKeyBarVisible: Boolean = true,
        val globalTextSelectionEnabled: Boolean = false,
        val cloudPasswordEnc: String = "",
        val updateChannel: String = "stable",
        val installedPreviewIdentity: String = "",
        val stagedUpdateIdentity: String = "",
        val autoUpdateEnabled: Boolean = false,
        val privilegedEnabled: Boolean = false,
        val pendingUpdateVersion: String = "",
        val stagedUpdateTag: String = "",
        val stagedUpdateFiles: String = ""
    )

    val baseUrl: Flow<String>
    val apiSpec: Flow<String>
    val model: Flow<String>
    val assistantName: Flow<String>
    val assistantStyle: Flow<String>

    val themeMode: Flow<String>
    val palette: Flow<String>
    val font: Flow<String>
    val dynamicColorEnabled: Flow<Boolean>

    suspend fun displayPrefsOnce(): DisplayPrefs
    suspend fun startupPrefsOnce(): StartupPrefs

    val lastScreen: Flow<String>
    suspend fun lastScreenOnce(): String
    suspend fun setLastScreen(value: String)

    val blackoutEnabled: Flow<Boolean>
    suspend fun blackoutEnabledOnce(): Boolean
    suspend fun setBlackoutEnabled(value: Boolean)
    suspend fun appLockWasOnBeforeBlackout(): Boolean

    val crashShieldEnabled: Flow<Boolean>
    suspend fun crashShieldEnabledOnce(): Boolean
    suspend fun setCrashShieldEnabled(value: Boolean)

    val passwordAttemptState: Flow<String>
    suspend fun passwordAttemptStateOnce(): String
    suspend fun setPasswordAttemptState(json: String)

    val pwFirstRoundLimit: Flow<Int>
    val pwLaterRoundLimit: Flow<Int>
    suspend fun setPwFirstRoundLimit(value: Int)
    suspend fun setPwLaterRoundLimit(value: Int)

    val pwSelfDestructEnabled: Flow<Boolean>
    val pwSelfDestructThreshold: Flow<Int>
    suspend fun setPwSelfDestructEnabled(value: Boolean)
    suspend fun setPwSelfDestructThreshold(value: Int)

    val openLinksExternally: Flow<Boolean>
    suspend fun setOpenLinksExternally(value: Boolean)

    val appLanguage: Flow<String>
    suspend fun setAppLanguage(value: String)
    suspend fun appLanguageOnce(): String

    val localModelEnabled: Flow<Boolean>
    suspend fun setLocalModelEnabled(value: Boolean)

    val memoryTierLocal: Flow<String>
    suspend fun setMemoryTierLocal(value: String)

    val agentMode: Flow<Boolean>
    suspend fun setAgentMode(value: Boolean)

    val reasoning: Flow<String>
    suspend fun setReasoning(value: String)

    val webSearchEngine: Flow<String>
    suspend fun setWebSearchEngine(value: String)
    suspend fun webSearchEngineOnce(): String

    val localBackgroundReplyEnabled: Flow<Boolean>
    suspend fun setLocalBackgroundReplyEnabled(value: Boolean)
    suspend fun localBackgroundReplyEnabledOnce(): Boolean

    val smallModelModeEnabled: Flow<Boolean>
    suspend fun setSmallModelModeEnabled(value: Boolean)

    val localToolsEnabled: Flow<Boolean>
    suspend fun setLocalToolsEnabled(value: Boolean)

    val localGpuEnabled: Flow<Boolean>
    suspend fun setLocalGpuEnabled(value: Boolean)

    val apiKey: Flow<String>
    val apiProfilesJson: Flow<String>
    val apiProfileSelected: Flow<Int>
    val attachmentsMigrated: Flow<Boolean>
    val backupPassword: Flow<String>

    val noteHistoryEnabled: Flow<Boolean>
    val taskHistoryEnabled: Flow<Boolean>
    suspend fun setNoteHistoryEnabled(value: Boolean)
    suspend fun setTaskHistoryEnabled(value: Boolean)

    val autoBackup: Flow<AutoBackup.State>
    suspend fun autoBackupOnce(): AutoBackup.State
    suspend fun setAutoBackup(state: AutoBackup.State)

    val autoBackupPasswords: Flow<List<String>>
    suspend fun autoBackupPasswordsOnce(): List<String>
    suspend fun setAutoBackupPassword(index: Int, value: String)
    val autoBackupRecoveryQuestion: Flow<String>
    suspend fun autoBackupRecoveryQuestionOnce(): String
    val autoBackupRecoveryAnswer: Flow<String>
    suspend fun autoBackupRecoveryAnswerOnce(): String
    suspend fun setAutoBackupRecovery(question: String, answer: String)

    val notesSort: Flow<String>
    val tasksSort: Flow<String>
    val notebooksSort: Flow<String>
    suspend fun setNotesSort(value: String)
    suspend fun setTasksSort(value: String)
    suspend fun setNotebooksSort(value: String)

    val notebookOpens: Flow<String>
    suspend fun notebookOpensOnce(): String
    suspend fun setNotebookOpens(value: String)

    val memoryTier: Flow<String>
    suspend fun setMemoryTier(value: String)

    val webSearchEnabled: Flow<Boolean>
    suspend fun setWebSearchEnabled(value: Boolean)

    val typingHapticsEnabled: Flow<Boolean>
    suspend fun setTypingHapticsEnabled(value: Boolean)

    val assistantConfirmToolsEnabled: Flow<Boolean>
    suspend fun setAssistantConfirmTools(value: Boolean)

    val updateChannel: Flow<String>
    suspend fun setUpdateChannel(value: String)

    val installedPreviewIdentity: Flow<String>
    suspend fun setInstalledPreviewIdentity(value: String)
    suspend fun setStagedUpdateIdentity(value: String)

    val autoUpdateEnabled: Flow<Boolean>
    suspend fun setAutoUpdateEnabled(value: Boolean)

    val pendingUpdateVersion: Flow<String>
    suspend fun setPendingUpdateVersion(value: String)

    suspend fun setStagedUpdate(tag: String, files: List<String>)

    val privilegedEnabled: Flow<Boolean>
    suspend fun setPrivilegedEnabled(value: Boolean)

    val markdownEnabled: Flow<Boolean>
    val richTextEnabled: Flow<Boolean>
    suspend fun setMarkdownEnabled(value: Boolean)
    suspend fun setRichTextEnabled(value: Boolean)

    val savedSearches: Flow<String>
    suspend fun setSavedSearches(json: String)

    val harnessConfig: Flow<String>
    suspend fun setHarnessConfig(json: String)
    suspend fun harnessConfigOnce(): String

    val customTemplatesJson: Flow<String>
    suspend fun setCustomTemplatesJson(json: String)
    val templateDraftJson: Flow<String>
    suspend fun setTemplateDraftJson(json: String)
    val hiddenTemplatesJson: Flow<String>
    suspend fun setHiddenTemplatesJson(json: String)

    val cloudEnabled: Flow<Boolean>
    suspend fun setCloudEnabled(value: Boolean)
    val cloudProvider: Flow<String>
    suspend fun setCloudProvider(value: String)
    val embeddingProvider: Flow<String>
    suspend fun setEmbeddingProvider(value: String)
    val cloudUrl: Flow<String>
    suspend fun setCloudUrl(value: String)
    val cloudUser: Flow<String>
    suspend fun setCloudUser(value: String)
    val cloudPasswordEnc: Flow<String>
    suspend fun setCloudPasswordEnc(value: String)
    val cloudFolder: Flow<String>
    suspend fun setCloudFolder(value: String)
    val cloudAutoBackup: Flow<Boolean>
    suspend fun setCloudAutoBackup(value: Boolean)

    val linksEnabled: Flow<Boolean>
    suspend fun setLinksEnabled(value: Boolean)

    val backgroundAnimationEnabled: Flow<Boolean>
    suspend fun setBackgroundAnimationEnabled(value: Boolean)

    val splashEnabled: Flow<Boolean>
    suspend fun setSplashEnabled(value: Boolean)

    val splashStyle: Flow<String>
    suspend fun setSplashStyle(value: String)

    val appLockEnabled: Flow<Boolean>
    val appLockCredentials: Flow<String>
    suspend fun setAppLock(enabled: Boolean, credentialsJson: String)
    suspend fun setAppLockCredentials(credentialsJson: String)

    val appLockBiometricEnabled: Flow<Boolean>
    suspend fun setAppLockBiometricEnabled(enabled: Boolean)

    val appLockHelloEnabled: Flow<Boolean>
    suspend fun setAppLockHelloEnabled(value: Boolean)

    val closeToTray: Flow<Boolean>
    suspend fun setCloseToTray(value: Boolean)

    suspend fun appLockEnabledOnce(): Boolean
    suspend fun appLockCredentialsOnce(): String
    suspend fun startupLoggingEnabledOnce(): Boolean
    suspend fun systemIntegrationEnabledOnce(): Boolean

    val systemIntegrationEnabled: Flow<Boolean>
    suspend fun setSystemIntegrationEnabled(value: Boolean)

    val startupLoggingEnabled: Flow<Boolean>
    suspend fun setStartupLoggingEnabled(value: Boolean)

    suspend fun setBaseUrl(value: String)
    suspend fun setApiSpec(value: String)
    suspend fun setModel(value: String)

    val modelRecents: Flow<List<String>>
    suspend fun setActiveModel(value: String)

    val terminalFontSize: Flow<Float?>
    val terminalKeyBarVisible: Flow<Boolean>
    val globalTextSelectionEnabled: Flow<Boolean>
    suspend fun setTerminalFontSize(value: Float)
    suspend fun setTerminalKeyBarVisible(value: Boolean)
    suspend fun setGlobalTextSelectionEnabled(value: Boolean)

    suspend fun setAssistantName(value: String)
    suspend fun setAssistantStyle(value: String)

    suspend fun setThemeMode(value: String)
    suspend fun setPalette(value: String)
    suspend fun setFont(value: String)
    suspend fun setDynamicColorEnabled(value: Boolean)

    suspend fun setAttachmentsMigrated(value: Boolean)
    suspend fun setBackupPassword(value: String)

    suspend fun setSessionSnapshot(value: String)
    suspend fun sessionSnapshotOnce(): String

    suspend fun setApiKey(value: String)
    suspend fun saveApiProfiles(profiles: List<ApiProfile>, selected: Int)

    suspend fun clearAll()
}

expect fun createSettingsRepository(context: PlatformContext): SettingsRepository
