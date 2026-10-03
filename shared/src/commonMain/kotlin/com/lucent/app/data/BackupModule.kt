package com.lucent.app.data

enum class BackupModule {
    NOTES,
    TASKS,
    CHATS,
    SETTINGS,
    API,
    LOCAL_ASSISTANT,
    LOCAL_MODEL_FILES,
    HARNESS
}

val DEFAULT_BACKUP_MODULES: Set<BackupModule> =
    BackupModule.entries.toSet() - BackupModule.LOCAL_MODEL_FILES
