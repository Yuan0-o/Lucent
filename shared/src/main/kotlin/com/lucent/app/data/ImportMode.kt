package com.lucent.app.data

enum class ImportMode {
    PARALLEL,

    OVERWRITE;

    companion object {
        val DEFAULT = PARALLEL
    }
}

enum class ImportAction {
    INSERT,

    REPLACE,

    SKIP
}

object ImportDecision {

    fun noteKey(title: String): String = title.trim().lowercase()

    fun taskKey(title: String, createdAt: Long): String = "${title.trim().lowercase()}\u0000$createdAt"

    fun forNote(
        mode: ImportMode,
        localUpdatedAt: Long?,
        backupUpdatedAt: Long,
        exactDuplicate: Boolean
    ): ImportAction {
        if (exactDuplicate) return ImportAction.SKIP
        if (localUpdatedAt == null) return ImportAction.INSERT
        return when (mode) {
            ImportMode.PARALLEL -> ImportAction.INSERT
            ImportMode.OVERWRITE ->
                if (backupUpdatedAt > localUpdatedAt) ImportAction.REPLACE else ImportAction.SKIP
        }
    }

    fun forTask(
        mode: ImportMode,
        matchedLocally: Boolean,
        exactDuplicate: Boolean
    ): ImportAction = when {
        exactDuplicate -> ImportAction.SKIP
        !matchedLocally -> ImportAction.INSERT
        mode == ImportMode.PARALLEL -> ImportAction.INSERT
        else -> ImportAction.REPLACE
    }
}
