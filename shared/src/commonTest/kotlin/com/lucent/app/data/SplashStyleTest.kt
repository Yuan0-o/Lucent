package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplashStyleTest {

    @Test
    fun avatarKeyResolvesToAvatar() {
        assertEquals(SplashStyle.AVATAR, SplashStyle.fromKey("avatar"))
    }

    @Test
    fun knownKeysResolve() {
        assertEquals(SplashStyle.CAT, SplashStyle.fromKey("cat"))
        assertEquals(SplashStyle.PEN, SplashStyle.fromKey("pen"))
    }

    @Test
    fun unknownKeyFallsBackToDefault() {
        assertEquals(SplashStyle.DEFAULT, SplashStyle.fromKey("nope"))
        assertEquals(SplashStyle.DEFAULT, SplashStyle.fromKey(null))
    }

    @Test
    fun keysAreUnique() {
        val keys = SplashStyle.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.contains("avatar"))
    }
}
