package com.lucent.app.data

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONException

class BackupRoundTripTest {

    private class TestContext(private val dir: File) : Context() {
        override val filesDir: File get() = dir
    }

    private class TestHarnessHost(private val dir: File) : com.lucent.app.harness.HarnessHost {
        override val android: Boolean = false
        override fun defaultWorkspace(): File = File(dir, "Lucent")
        override fun filesDir(): File = dir
        override fun cacheDir(): File = File(dir, "cache")
    }

    private fun freshDir(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-backup-test-${System.nanoTime()}")
            .apply { mkdirs() }

    private suspend fun use(dir: File, block: suspend () -> Unit) {
        LocalSecrets.filesDirOverride = dir
        LocalSecrets.resetForTesting()
        DataKeys.resetCacheForTesting()
        try {
            block()
        } finally {
            LocalSecrets.filesDirOverride = null
            LocalSecrets.resetForTesting()
            DataKeys.resetCacheForTesting()
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
            ChatMessage(
                role = "assistant",
                content = "Osaka, obviously.",
                timestamp = 1700000004000,
                reasoningBlocks = """[{"type":"thinking","thinking":"keep it short","signature":"sig"}]""",
                reasoningText = "the person wants a city"
            )
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

            val dir2 = freshDir()
            use(dir2) {
                val context2 = TestContext(dir2)
                val db2 = AppDatabase.createForTesting(context2)
                val settings2 = SettingsRepository(context2)
                BackupImporter.import(context2, db2, settings2, manifest)

                val restoredNotes = db2.noteDao().getAllOnce()
                assertEquals(1, restoredNotes.size)
                assertEquals("Osaka trip", restoredNotes[0].title)
                assertEquals("Takoyaki and a very determined pigeon", restoredNotes[0].body)

                val restoredTasks = db2.taskDao().getAllOnce()
                assertEquals(1, restoredTasks.size)
                assertEquals("Book shinkansen", restoredTasks[0].title)
                assertEquals("window seat", restoredTasks[0].notes)

                val restoredChats = db2.chatDao().getAllOnce()
                assertEquals(2, restoredChats.size)
                assertEquals(
                    "the person wants a city",
                    restoredChats.first { it.role == "assistant" }.reasoningText
                )
                assertTrue(
                    restoredChats.first { it.role == "assistant" }
                        .reasoningBlocks.orEmpty().contains("keep it short")
                )
                assertEquals(1, db2.chatConversationDao().getAllOnce().size)

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

            val truncated = """{"version":1,"notes":["""
            assertFailsWith<JSONException> {
                BackupImporter.import(context, db, SettingsRepository(context), truncated)
            }

            assertEquals(1, db.noteDao().getAllOnce().size)
            assertEquals(1, db.taskDao().getAllOnce().size)
            assertEquals(2, db.chatDao().getAllOnce().size)
        }
    }

    @Test
    fun harnessHomeRoundTripsThroughBackupAndRestore() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val previousHost = com.lucent.app.harness.HarnessRuntime.host
            com.lucent.app.harness.HarnessRuntime.host = TestHarnessHost(dir)
            try {
                val db = AppDatabase.createForTesting(context)
                val settings = SettingsRepository(context)
                val harnessHome = com.lucent.app.harness.HarnessRuntime.home()
                File(harnessHome, "todos").mkdirs()
                File(harnessHome, "todos").resolve("today.json").writeText("""{"items":["buy milk"]}""")
                File(harnessHome, "memory").mkdirs()
                File(harnessHome, "memory").resolve("notes.md").writeText("the assistant remembers")
                File(harnessHome, "harness_audit.jsonl").writeText("""{"event":"started"}""" + "\n")

                assertTrue(harnessHome.resolve("todos/today.json").exists())

                val payload = ByteArrayOutputStream()
                BackupManager.exportEncrypted(
                    context, db, settings, payload, null,
                    BackupManager.BackupSelection(modules = BackupManager.DEFAULT_MODULES)
                )

                File(harnessHome, "todos").deleteRecursively()
                File(harnessHome, "memory").deleteRecursively()
                File(harnessHome, "harness_audit.jsonl").delete()
                assertTrue(!harnessHome.resolve("todos/today.json").exists())

                val dir2 = freshDir()
                use(dir2) {
                    val context2 = TestContext(dir2)
                    com.lucent.app.harness.HarnessRuntime.host = TestHarnessHost(dir2)
                    val db2 = AppDatabase.createForTesting(context2)
                    val settings2 = SettingsRepository(context2)
                    val bytes = payload.toByteArray()

                    val preview = BackupManager.inspect(context2, bytes, null)
                    assertEquals(3, preview.harnessFiles)
                    assertTrue(preview.harnessBytes > 0L)
                    assertTrue(BackupManager.BackupModule.HARNESS in preview.modules)

                    BackupManager.commit(
                        context2, db2, settings2, preview,
                        modules = setOf(BackupManager.BackupModule.HARNESS),
                        source = BackupManager.BackupSource { bytes.inputStream() }
                    )

                    val restoredHome = com.lucent.app.harness.HarnessRuntime.home()
                    assertEquals(
                        """{"items":["buy milk"]}""",
                        restoredHome.resolve("todos/today.json").readText()
                    )
                    assertEquals(
                        "the assistant remembers",
                        restoredHome.resolve("memory/notes.md").readText()
                    )
                    assertEquals(
                        """{"event":"started"}""" + "\n",
                        restoredHome.resolve("harness_audit.jsonl").readText()
                    )
                }
            } finally {
                com.lucent.app.harness.HarnessRuntime.host = previousHost
            }
        }
    }

    @Test
    fun harnessEntriesStayOutOfTheRestoreWhenTheModuleIsAbsent() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val previousHost = com.lucent.app.harness.HarnessRuntime.host
            com.lucent.app.harness.HarnessRuntime.host = TestHarnessHost(dir)
            try {
                val db = AppDatabase.createForTesting(context)
                val settings = SettingsRepository(context)
                val harnessHome = com.lucent.app.harness.HarnessRuntime.home()
                File(harnessHome, "todos").mkdirs()
                File(harnessHome, "todos").resolve("today.json").writeText("original")

                val payload = ByteArrayOutputStream()
                BackupManager.exportEncrypted(
                    context, db, settings, payload, null,
                    BackupManager.BackupSelection(modules = BackupManager.DEFAULT_MODULES)
                )

                File(harnessHome, "todos/today.json").writeText("local edit")

                val dir2 = freshDir()
                use(dir2) {
                    val context2 = TestContext(dir2)
                    com.lucent.app.harness.HarnessRuntime.host = TestHarnessHost(dir2)
                    val db2 = AppDatabase.createForTesting(context2)
                    val settings2 = SettingsRepository(context2)
                    val bytes = payload.toByteArray()
                    val preview = BackupManager.inspect(context2, bytes, null)

                    BackupManager.commit(
                        context2, db2, settings2, preview,
                        modules = setOf(BackupManager.BackupModule.NOTES),
                        source = BackupManager.BackupSource { bytes.inputStream() }
                    )

                    val restoredHome = com.lucent.app.harness.HarnessRuntime.home()
                    assertTrue(!restoredHome.resolve("todos/today.json").exists())
                }
            } finally {
                com.lucent.app.harness.HarnessRuntime.host = previousHost
            }
        }
    }

    @Test
    fun backupManifestCoversEverySettingsKey() {
        val backable = setOf(
            "themeMode", "palette", "dynamicColorEnabled", "font", "fontLibrary",
            "assistantName", "assistantStyle",
            "memoryTier", "memoryTierLocal", "webSearchEnabled", "typingHaptics", "markdownEnabled",
            "agentMode", "reasoning", "webSearchEngine",
            "linksEnabled", "backgroundAnimationEnabled", "appLanguage", "notesSort",
            "tasksSort", "notebooksSort", "systemIntegrationEnabled", "startupLoggingEnabled",
            "savedSearches", "customTemplates", "templateDraft", "hiddenTemplates",
            "cloudEnabled", "cloudProvider", "cloudUrl", "cloudUser", "cloudPasswordEnc",
            "cloudFolder", "cloudAutoBackup",
            "noteHistoryEnabled", "taskHistoryEnabled",
            "pwFirstRoundLimit", "pwLaterRoundLimit",
            "pwSelfDestructEnabled", "pwSelfDestructThreshold",
            "richTextEnabled", "openLinksExternally",
            "assistantConfirmToolsEnabled", "smallModelModeEnabled",
            "crashShieldEnabled", "blackoutEnabled",
            "autoUpdateEnabled", "privilegedEnabled",
            "localModelEnabled", "localToolsEnabled", "localGpuEnabled",
            "localBackgroundReply", "localModelManifest",
            "baseUrl", "apiSpec", "apiKeyEncrypted", "model", "apiProfiles", "apiProfileSelected"
        )
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
