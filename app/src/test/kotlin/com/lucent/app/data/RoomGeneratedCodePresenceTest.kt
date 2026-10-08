package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertNotNull

class RoomGeneratedCodePresenceTest {
    @Test
    fun generatedDatabaseImplementationExists() {
        val cls = Class.forName("com.lucent.app.data.AndroidAppDatabase_Impl", false, javaClass.classLoader)
        assertNotNull(cls)
    }
}
