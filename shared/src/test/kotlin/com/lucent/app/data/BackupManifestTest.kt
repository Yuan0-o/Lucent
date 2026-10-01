package com.lucent.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BackupManifestTest {

    @Test
    fun testManifestSerialization() {
        val jsonString = """
            {
                "version": 1,
                "created": 123456789,
                "device": "Test Device",
                "harnessConfig": "{\"pluginBackupScope\":\"state\"}"
            }
        """
        val obj = JSONObject(jsonString)
        val harnessConfig = obj.optString("harnessConfig", "")
        assertNotNull(harnessConfig)
        assertEquals("{\"pluginBackupScope\":\"state\"}", harnessConfig)
        
        val parsedConfig = JSONObject(harnessConfig)
        assertEquals("state", parsedConfig.optString("pluginBackupScope"))
    }
}
