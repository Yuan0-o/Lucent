package com.lucent.app.data

object SettingsCache {

    @Volatile
    var assistantName: String? = null
        private set

    @Volatile
    var notesSort: String? = null
        private set

    @Volatile
    var tasksSort: String? = null
        private set

    @Volatile
    var sessionSnapshot: String? = null
        private set

    @Volatile
    var appLockEnabled: Boolean = false
        private set

    @Volatile
    var startupLoggingEnabled: Boolean = false
        private set

    @Volatile
    var systemIntegrationEnabled: Boolean = false
        private set

    @Volatile
    var appLanguage: String = "system"
        private set

    @Volatile
    var themeMode: String = "system"
        private set

    @Volatile
    var palette: String = "CYCLE"
        private set

    @Volatile
    var font: String = "system"
        private set

    @Volatile
    var dynamicColor: Boolean = false
        private set

    @Volatile
    var backgroundAnimationEnabled: Boolean = true
        private set

    @Volatile
    var assistantStyle: String = ""
        private set

    @Volatile
    var baseUrl: String = ""
        private set

    @Volatile
    var apiSpec: String = "openai"
        private set

    @Volatile
    var apiKey: String = ""
        private set

    @Volatile
    var model: String = ""
        private set

    @Volatile
    var apiProfilesJson: String = ""
        private set

    @Volatile
    var apiProfileSelected: Int = 0
        private set

    @Volatile
    var noteHistoryEnabled: Boolean = true
        private set

    @Volatile
    var taskHistoryEnabled: Boolean = true
        private set

    @Volatile
    var crashShieldEnabled: Boolean = false
        private set

    @Volatile
    var blackoutEnabled: Boolean = false
        private set

    @Volatile
    var pwSelfDestructEnabled: Boolean = false
        private set

    @Volatile
    var pwFirstRoundLimit: Int = PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT
        private set

    @Volatile
    var pwLaterRoundLimit: Int = PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT
        private set

    @Volatile
    var pwSelfDestructThreshold: Int = PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD
        private set

    @Volatile
    var passwordAttemptState: String = ""
        private set

    @Volatile
    var appLockBiometricEnabled: Boolean = false
        private set

    @Volatile
    var appLockHelloEnabled: Boolean = false
        private set

    @Volatile
    var closeToTray: Boolean = true
        private set

    @Volatile
    var openLinksExternally: Boolean = false
        private set

    @Volatile
    var markdownEnabled: Boolean = false
        private set

    @Volatile
    var richTextEnabled: Boolean = false
        private set

    @Volatile
    var linksEnabled: Boolean = false
        private set

    @Volatile
    var typingHapticsEnabled: Boolean = true
        private set

    @Volatile
    var assistantConfirmToolsEnabled: Boolean = true
        private set

    @Volatile
    var localModelEnabled: Boolean = false
        private set

    @Volatile
    var localToolsEnabled: Boolean = false
        private set

    @Volatile
    var localGpuEnabled: Boolean = false
        private set

    @Volatile
    var localBackgroundReplyEnabled: Boolean = false
        private set

    @Volatile
    var smallModelModeEnabled: Boolean = false
        private set

    @Volatile
    var webSearchEnabled: Boolean = false
        private set

    @Volatile
    var memoryTier: String = MemoryTier.DEFAULT.key
        private set

    @Volatile
    var embeddingProvider: String = "local"
        private set

    @Volatile
    var cloudEnabled: Boolean = false
        private set

    @Volatile
    var cloudProvider: String = "Nutstore"
        private set

    @Volatile
    var cloudUrl: String = ""
        private set

    @Volatile
    var cloudUser: String = ""
        private set

    @Volatile
    var cloudFolder: String = "Lucent"
        private set

    @Volatile
    var cloudAutoBackup: Boolean = false
        private set

    @Volatile
    var cloudPasswordEnc: String = ""
        private set

    fun seed(prefs: SettingsRepository.StartupPrefs) {
        assistantName = prefs.assistantName
        notesSort = prefs.notesSort
        tasksSort = prefs.tasksSort
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
        smallModelModeEnabled = prefs.smallModelModeEnabled
        webSearchEnabled = prefs.webSearchEnabled
        memoryTier = prefs.memoryTier
        embeddingProvider = prefs.embeddingProvider
        cloudEnabled = prefs.cloudEnabled
        cloudProvider = prefs.cloudProvider
        cloudUrl = prefs.cloudUrl
        cloudUser = prefs.cloudUser
        cloudFolder = prefs.cloudFolder
        cloudAutoBackup = prefs.cloudAutoBackup
        cloudPasswordEnc = prefs.cloudPasswordEnc
    }
}
