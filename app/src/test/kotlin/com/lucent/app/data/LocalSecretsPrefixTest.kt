package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalSecretsPrefixTest {

    @Test
    fun emptyValueStaysEmpty() {
        assertEquals("", LocalSecrets.encrypt(""))
        assertEquals("", LocalSecrets.decrypt(""))
    }

    @Test
    fun legacyPlaintextValueIsReturnedUntouched() {
        assertEquals("legacy-api-key", LocalSecrets.decrypt("legacy-api-key"))
        assertEquals("", LocalSecrets.decrypt(""))
    }

    @Test
    fun keystoreSchemeValueFailsClosedWithoutTheKeystore() {
        assertEquals("", LocalSecrets.decrypt("k1:AAAA"))
        assertEquals("", LocalSecrets.decrypt("k1:"))
    }

    @Test
    fun recognisablePrefixIsRequiredToEnterTheCryptoPath() {
        assertEquals("v1:AAAA", LocalSecrets.decrypt("v1:AAAA"))
    }
}
