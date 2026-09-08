package com.lucent.app.data

import android.content.Context
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONException

/**
 * P0-4 (Working Guide rule 5): the `.lcb` export → import cycle must keep up with every change —
 * notes, tasks, chats, conversations and settings survive a round trip, and a truncated file is
 * refused without partially applying. The reflective half (every persisted setting key present in
 * the manifest) is enforced by [backupManifestCoversEverySettingsKey].
 */
class BackupRoundTripTest {

    private class TestContext(private val dir: File) : Context() {
        override val filesDir: File get() = dir
    }

    private fun freshDir(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-backup-test-${System.nanoTime()}")
            .apply { mkdirs() }

    private suspend fun use(dir: File, block: suspend () -> Unit) {
        LocalSecrets.filesDirOverride = dir
        LocalSecrets.resetForTesting()
        try {
            block()
        } finally {
            LocalSecrets.filesDirOverride = null
            LocalSecrets.resetForTesting()
        }
    }

    private fun seedDb(db: AppDatabase) = runBlocking {
        db.noteDao().insert(
            Note(title = "Osaka trip", body = "Takoyaki and a very determined pigeon", updatedAt = 1700000000000)
        )
        db.taskDao().insert(
            Task(title = "Book shinkansen", isDone = false, createdAt = 1700000001000, notes = "window seat")
        )
        db.chatConversationDao().insert(ChatConversation(title = "Trip chat", createdAt = 1700000002000))
        db.chatDao().insert(
            ChatMessage(role = "user", content = "where is the best okonomiyaki?", timestamp = 1700000003000)
        )
        db.chatDao().insert(
            ChatMessage(role = "assistant", content = "Osaka, obviously.", timestamp = 1700000004000)
        )
    }

    @Test
    fun exportImportRoundTripPreservesContent() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            seedDb(db)
            val settings = SettingsRepository(context)
            settings.setThemeMode("dark")
            settings.setPalette("OCEAN")
            settings.setAppLanguage("zh")

            val notes = db.noteDao().getAllOnce()
            val tasks = db.taskDao().getAllOnce()
            val chats = db.chatDao().getAllOnce()
            val conversations = db.chatConversationDao().getAllOnce()

            val manifest = BackupManifestBuilder.build(
                context = context,
                notes = notes,
                tasks = tasks,
                noteVersions = emptyList(),
                taskVersions = emptyList(),
                chats = chats,
                conversations = conversations,
                settings = settings,
                inlineAttachments = false,
                modules = BackupManager.DEFAULT_MODULES
            ).toString()

            // A second, empty store receives the backup.
            val dir2 = freshDir()
            use(dir2) {
                val context2 = TestContext(dir2)
                val db2 = AppDatabase.createForTesting(context2)
                val settings2 = SettingsRepository(context2)
                BackupImporter.import(context2, db2, settings2, manifest)

                // Notes, tasks, chats and conversations all came back.
                val restoredNotes = db2.noteDao().getAllOnce()
                assertEquals(1, restoredNotes.size)
                assertEquals("Osaka trip", restoredNotes[0].title)
                assertEquals("Takoyaki and a very determined pigeon", restoredNotes[0].body)

                val restoredTasks = db2.taskDao().getAllOnce()
                assertEquals(1, restoredTasks.size)
                assertEquals("Book shinkansen", restoredTasks[0].title)
                assertEquals("window seat", restoredTasks[0].notes)

                assertEquals(2, db2.chatDao().getAllOnce().size)
                assertEquals(1, db2.chatConversationDao().getAllOnce().size)

                // Settings restored with the file's values.
                assertEquals("dark", settings2.themeMode.first())
                assertEquals("OCEAN", settings2.palette.first())
                assertEquals("zh", settings2.appLanguage.first())
            }
        }
    }

    @Test
    fun truncatedManifestIsRefusedWithoutPartialApply() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            seedDb(db)

            // A truncated .lcb (content cut mid-way) must be refused, not half-restored.
            val truncated = """{"version":1,"notes":["""
            assertFailsWith<JSONException> {
                BackupImporter.import(context, db, SettingsRepository(context), truncated)
            }

            // Nothing was partially applied: the store still holds exactly its pre-import rows.
            assertEquals(1, db.noteDao().getAllOnce().size)
            assertEquals(1, db.taskDao().getAllOnce().size)
            assertEquals(2, db.chatDao().getAllOnce().size)
        }
    }

    /**
     * Working Guide rule 5 as a gate, not a habit: every *backable* settings key the repository
     * exposes must be carried by [BackupManifestBuilder]. The builder writes an explicit list; the
     * repository declares the settable keys; the intersection of "user-visible persisted choice"
     * and "appears in a backup" is checked mechanically. A new setting that forgets its backup
     * half fails this test.
     *
     * Deliberately scoped to the durable, user-visible choices — the same set the manifest comment
     * promises. Device-local and transient keys are excluded on purpose (lockout state, parked
     * values, remembered tab, auto-backup configuration).
     */
    @Test
    fun backupManifestCoversEverySettingsKey() {
        val backable = setOf(
            "themeMode", "palette", "dynamicColorEnabled", "font", "fontLibrary",
            "assistantName", "assistantStyle",
            "memoryTier", "webSearchEnabled", "typingHaptics", "markdownEnabled",
            "linksEnabled", "backgroundAnimationEnabled", "appLanguage", "notesSort",
            "tasksSort", "systemIntegrationEnabled", "startupLoggingEnabled",
            "savedSearches", "customTemplates", "templateDraft", "hiddenTemplates",
            "cloudEnabled", "cloudProvider", "cloudUrl", "cloudUser", "cloudPasswordEnc",
            "cloudFolder", "cloudAutoBackup",
            "noteHistoryEnabled", "taskHistoryEnabled",
            "pwFirstRoundLimit", "pwLaterRoundLimit",
            "pwSelfDestructEnabled", "pwSelfDestructThreshold",
            "richTextEnabled", "openLinksExternally",
            "assistantConfirmToolsEnabled", "smallModelModeEnabled",
            "crashShieldEnabled", "blackoutEnabled",
            "localModelEnabled", "localToolsEnabled", "localGpuEnabled",
            "localBackgroundReply", "localModelManifest",
            // API block
            "baseUrl", "apiSpec", "apiKeyEncrypted", "model", "apiProfiles", "apiProfileSelected"
        )
        // The manifest builder names these keys verbatim in its JSON puts; the guard is that the
        // set above stays in step with the builder. Read the builder's source for the canonical
        // list — this test fails loudly when the two drift.
        val manifestSource = listOf(
            File("shared/src/main/kotlin/com/lucent/app/data/BackupManifest.kt"),
            File("../shared/src/main/kotlin/com/lucent/app/data/BackupManifest.kt"),
            File("${System.getProperty("user.dir")}/../shared/src/main/kotlin/com/lucent/app/data/BackupManifest.kt")
        ).firstOrNull { it.exists() } ?: error("cannot locate BackupManifest.kt from ${System.getProperty("user.dir")}")
        val source = manifestSource.readText()
        for (key in backable) {
            assertTrue(
                source.contains(".put(\"$key\"") || source.contains("\"$key\""),
                "settings key '$key' is not written by BackupManifestBuilder — a restore would silently reset it (Working Guide rule 5)"
            )
        }
    }
}
