package com.lucent.app.data

object SettingsCache {

    @Volatile
    var assistantName: String? = null

    @Volatile
    var notesSort: String? = null

    @Volatile
    var tasksSort: String? = null

    @Volatile
    var notebooksSort: String? = null

    @Volatile
    var sessionSnapshot: String? = null

    @Volatile
    var appLockEnabled: Boolean = false

    @Volatile
    var startupLoggingEnabled: Boolean = false

    @Volatile
    var systemIntegrationEnabled: Boolean = false

    @Volatile
    var appLanguage: String = "system"

    @Volatile
    var themeMode: String = "system"

    @Volatile
    var palette: String = "CYCLE"

    @Volatile
    var font: String = "system"

    @Volatile
    var dynamicColor: Boolean = false

    @Volatile
    var backgroundAnimationEnabled: Boolean = true

    @Volatile
    var splashEnabled: Boolean = true

    @Volatile
    var splashStyle: String = SplashStyle.DEFAULT.key

    @Volatile
    var autoBackup: AutoBackup.State = AutoBackup.State.EMPTY

    @Volatile
    var assistantStyle: String = ""

    @Volatile
    var baseUrl: String = ""

    @Volatile
    var apiSpec: String = "openai"

    @Volatile
    var apiKey: String = ""

    @Volatile
    var model: String = ""

    @Volatile
    var apiProfilesJson: String = ""

    @Volatile
    var apiProfileSelected: Int = 0

    @Volatile
    var noteHistoryEnabled: Boolean = true

    @Volatile
    var taskHistoryEnabled: Boolean = true

    @Volatile
    var crashShieldEnabled: Boolean = false

    @Volatile
    var blackoutEnabled: Boolean = false

    @Volatile
    var pwSelfDestructEnabled: Boolean = false

    @Volatile
    var pwFirstRoundLimit: Int = PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT

    @Volatile
    var pwLaterRoundLimit: Int = PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT

    @Volatile
    var pwSelfDestructThreshold: Int = PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD

    @Volatile
    var passwordAttemptState: String = ""

    @Volatile
    var appLockBiometricEnabled: Boolean = false

    @Volatile
    var appLockHelloEnabled: Boolean = false

    @Volatile
    var closeToTray: Boolean = true

    @Volatile
    var openLinksExternally: Boolean = false

    @Volatile
    var markdownEnabled: Boolean = false

    @Volatile
    var richTextEnabled: Boolean = false

    @Volatile
    var linksEnabled: Boolean = false

    @Volatile
    var typingHapticsEnabled: Boolean = true

    @Volatile
    var assistantConfirmToolsEnabled: Boolean = true

    @Volatile
    var localModelEnabled: Boolean = false

    @Volatile
    var localToolsEnabled: Boolean = false

    @Volatile
    var localGpuEnabled: Boolean = false

    @Volatile
    var localBackgroundReplyEnabled: Boolean = false

    @Volatile
    var agentMode: Boolean = true

    @Volatile
    var reasoning: String = com.lucent.app.data.ReasoningEffort.DEFAULT.key

    @Volatile
    var webSearchEngine: String = com.lucent.app.data.WebSearchEngine.DEFAULT.key

    @Volatile
    var smallModelModeEnabled: Boolean = false

    @Volatile
    var webSearchEnabled: Boolean = false

    @Volatile
    var memoryTier: String = MemoryTier.DEFAULT.key

    @Volatile
    var memoryTierLocal: String = MemoryTier.LOW.key

    @Volatile
    var embeddingProvider: String = "local"

    @Volatile
    var cloudEnabled: Boolean = false

    @Volatile
    var cloudProvider: String = "Nutstore"

    @Volatile
    var cloudUrl: String = ""

    @Volatile
    var cloudUser: String = ""

    @Volatile
    var cloudFolder: String = "Lucent"

    @Volatile
    var cloudAutoBackup: Boolean = false

    @Volatile
    var autoUpdateEnabled: Boolean = false

    @Volatile
    var privilegedEnabled: Boolean = false

    @Volatile
    var cloudPasswordEnc: String = ""

    fun seed(prefs: SettingsRepository.StartupPrefs) {
        assistantName = prefs.assistantName
        notesSort = prefs.notesSort
        tasksSort = prefs.tasksSort
        notebooksSort = prefs.notebooksSort
        sessionSnapshot = prefs.sessionSnapshot.ifBlank { null }
        appLockEnabled = prefs.appLockEnabled
        startupLoggingEnabled = prefs.startupLoggingEnabled
        systemIntegrationEnabled = prefs.systemIntegrationEnabled
        appLanguage = prefs.appLanguage
        themeMode = prefs.display.themeMode
        palette = prefs.display.palette
        font = prefs.display.font
        dynamicColor = prefs.dynamicColor
        backgroundAnimationEnabled = prefs.backgroundAnimationEnabled
        splashEnabled = prefs.splashEnabled
        splashStyle = prefs.splashStyle
        autoBackup = prefs.autoBackup
        assistantStyle = prefs.assistantStyle
        baseUrl = prefs.baseUrl
        apiSpec = prefs.apiSpec
        apiKey = prefs.apiKey
        model = prefs.model
        apiProfilesJson = prefs.apiProfilesJson
        apiProfileSelected = prefs.apiProfileSelected
        noteHistoryEnabled = prefs.noteHistoryEnabled
        taskHistoryEnabled = prefs.taskHistoryEnabled
        crashShieldEnabled = prefs.crashShieldEnabled
        blackoutEnabled = prefs.blackoutEnabled
        pwSelfDestructEnabled = prefs.pwSelfDestructEnabled
        pwFirstRoundLimit = prefs.pwFirstRoundLimit
        pwLaterRoundLimit = prefs.pwLaterRoundLimit
        pwSelfDestructThreshold = prefs.pwSelfDestructThreshold
        passwordAttemptState = prefs.passwordAttemptState
        appLockBiometricEnabled = prefs.appLockBiometricEnabled
        appLockHelloEnabled = prefs.appLockHelloEnabled
        closeToTray = prefs.closeToTray
        openLinksExternally = prefs.openLinksExternally
        markdownEnabled = prefs.markdownEnabled
        richTextEnabled = prefs.richTextEnabled
        linksEnabled = prefs.linksEnabled
        typingHapticsEnabled = prefs.typingHapticsEnabled
        assistantConfirmToolsEnabled = prefs.assistantConfirmToolsEnabled
        localModelEnabled = prefs.localModelEnabled
        localToolsEnabled = prefs.localToolsEnabled
        localGpuEnabled = prefs.localGpuEnabled
        localBackgroundReplyEnabled = prefs.localBackgroundReplyEnabled
        agentMode = prefs.agentMode
        reasoning = prefs.reasoning
        webSearchEngine = prefs.webSearchEngine
        smallModelModeEnabled = prefs.smallModelModeEnabled
        webSearchEnabled = prefs.webSearchEnabled
        memoryTier = prefs.memoryTier
        memoryTierLocal = prefs.memoryTierLocal
        embeddingProvider = prefs.embeddingProvider
        cloudEnabled = prefs.cloudEnabled
        cloudProvider = prefs.cloudProvider
        cloudUrl = prefs.cloudUrl
        cloudUser = prefs.cloudUser
        cloudFolder = prefs.cloudFolder
        cloudAutoBackup = prefs.cloudAutoBackup
        cloudPasswordEnc = prefs.cloudPasswordEnc
        autoUpdateEnabled = prefs.autoUpdateEnabled
        privilegedEnabled = prefs.privilegedEnabled
    }
}
