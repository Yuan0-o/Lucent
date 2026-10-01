package com.lucent.app.harness.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCatalogRemoteTest {

    @Test
    fun testMergeAddNew() {
        val static = listOf(
            PluginSpec("base", "Base", "", true, true, 0L, emptyList(), "", "", "", "", "", false, "", "", "")
        )
        val u1 = "https:" + "//example.com/1"
        val json = """
        [
            {
                "id": "new-plugin",
                "name": "New",
                "summary": "Summary",
                "sources": [
                    { "id": "s1", "url": "$u1" }
                ]
            }
        ]
        """
        val merged = PluginCatalogRemote.merge(static, json)
        assertEquals(2, merged.size)
        assertEquals("base", merged[0].id)
        assertEquals("new-plugin", merged[1].id)
        assertEquals("New", merged[1].name)
        assertEquals(1, merged[1].sources.size)
    }

    @Test
    fun testMergeSources() {
        val staticSource = PluginSource("s0", "S0", "https:" + "//existing.com")
        val static = listOf(
            PluginSpec("base", "Base", "", true, true, 0L, listOf(staticSource), "", "", "", "", "", false, "", "", "")
        )
        val u0 = "https:" + "//existing.com"
        val u1 = "https:" + "//new.com"
        val json = """
        [
            {
                "id": "base",
                "name": "Ignored Name Change",
                "sources": [
                    { "id": "s0-dup", "url": "$u0" },
                    { "id": "s1", "url": "$u1", "sha256": "abc123" }
                ]
            }
        ]
        """
        val merged = PluginCatalogRemote.merge(static, json)
        assertEquals(1, merged.size)
        assertEquals("base", merged[0].id)
        assertEquals("Base", merged[0].name)
        assertEquals(2, merged[0].sources.size)
        assertEquals("https:" + "//existing.com", merged[0].sources[0].url)
        assertEquals("https:" + "//new.com", merged[0].sources[1].url)
    }

    @Test
    fun testNeverRemoveStaticAndSkipMalformed() {
        val static = listOf(
            PluginSpec("base", "Base", "", true, true, 0L, emptyList(), "", "", "", "", "", false, "", "", "")
        )
        val json = """
        [
            {},
            { "name": "No ID" },
            { "id": "valid", "name": "Valid" }
        ]
        """
        val merged = PluginCatalogRemote.merge(static, json)
        assertEquals(2, merged.size)
        assertEquals("base", merged[0].id)
        assertEquals("valid", merged[1].id)
    }
}
