package com.lucent.app.data

import android.content.Context
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class SettingsRepositoryTest {

    private class TestContext(private val dir: File) : Context() {
        override val filesDir: File get() = dir
    }

    private fun freshDir(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-settings-test-${System.nanoTime()}")
            .apply { mkdirs() }

    private fun settingsFile(dir: File): File = File(dir, "lucent_settings.json")

    private fun readJson(dir: File): JSONObject = JSONObject(settingsFile(dir).readText())

    @Test
    fun plainKeysRoundTripThroughDisk() = runBlocking {
        val dir = freshDir()
        val repo = SettingsRepository(TestContext(dir))
        repo.setThemeMode("dark")
        repo.setPalette("OCEAN")
        repo.setFont("serif")
        repo.setAppLanguage("zh")
        repo.setMarkdownEnabled(true)
        repo.setMemoryTier(MemoryTier.LOW.key)

        val json = readJson(dir)
        assertEquals("dark", json.getString("theme_mode"))
        assertEquals("OCEAN", json.getString("palette"))
        assertEquals("serif", json.getString("font"))
        assertEquals("zh", json.getString("app_language"))
        assertEquals(true, json.getBoolean("markdown_enabled"))
        assertEquals(MemoryTier.LOW.key, json.getString("memory_tier"))

        val repo2 = SettingsRepository(TestContext(dir))
        assertEquals("dark", repo2.themeMode.first())
        assertEquals("OCEAN", repo2.palette.first())
        assertEquals("zh", repo2.appLanguage.first())
        assertEquals(MemoryTier.LOW.key, repo2.memoryTier.first())
    }

    @Test
    fun secretKeysAreEncryptedAtRest() = runBlocking {
        val dir = freshDir()
        val repo = SettingsRepository(TestContext(dir))
        repo.setApiKey("sk-super-secret-abc123")
        repo.setAssistantName("Jeeves")
        repo.setAssistantStyle("witty but brief")

        val fileText = settingsFile(dir).readText()
        assertFalse(fileText.contains("sk-super-secret-abc123"))
        assertFalse(fileText.contains("Jeeves"))
        assertFalse(fileText.contains("witty but brief"))
        val json = JSONObject(fileText)
        assertTrue(json.has("api_key_enc"))
        assertTrue(json.has("assistant_name_enc"))
        assertTrue(json.has("assistant_style_enc"))
        assertEquals("sk-super-secret-abc123", repo.apiKey.first())
        assertEquals("Jeeves", repo.assistantName.first())
        assertEquals("witty but brief", repo.assistantStyle.first())
    }

    @Test
    fun cloudSecretsAreEncryptedAtRest() = runBlocking {
        val dir = freshDir()
        val repo = SettingsRepository(TestContext(dir))
        repo.setCloudEnabled(true)
        repo.setCloudProvider("nutstore")
        repo.setCloudUrl("https://dav.example.com")
        repo.setCloudUser("user@example.com")
        repo.setCloudPasswordEnc(LocalSecrets.encrypt("hunter2-cloud-password"))

        val fileText = settingsFile(dir).readText()
        assertFalse(fileText.contains("hunter2-cloud-password"))
        assertTrue(fileText.contains("cloud_password_enc"))
        assertTrue(fileText.contains("https://dav.example.com"))
    }

    @Test
    fun corruptFileDegradesToDefaults() {
        val dir = freshDir()
        settingsFile(dir).writeText("{not valid json!!")
        val repo = SettingsRepository(TestContext(dir))
        assertEquals("system", runBlocking { repo.themeMode.first() })
        assertEquals("CYCLE", runBlocking { repo.palette.first() })
        assertEquals("Lucent", runBlocking { repo.assistantName.first() })
    }

    @Test
    fun localModelToggleParksAndRestoresTier() = runBlocking {
        val dir = freshDir()
        val repo = SettingsRepository(TestContext(dir))
        repo.setMemoryTier(MemoryTier.HIGH.key)
        repo.setWebSearchEnabled(true)

        repo.setLocalModelEnabled(true)
        assertEquals(MemoryTier.LOW.key, repo.memoryTier.first())
        assertEquals(false, repo.webSearchEnabled.first())

        repo.setLocalModelEnabled(false)
        assertEquals(MemoryTier.HIGH.key, repo.memoryTier.first())
        assertEquals(true, repo.webSearchEnabled.first())
    }
}
