package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * P0-3: LocalSecrets prefix handling on a plain JVM.
 *
 * The full keystore path needs a device, but the *prefix discipline* is pure logic and can be
 * pinned here: an empty value stays empty, a value without a recognised prefix is legacy plaintext
 * and is returned untouched (that is what silently upgrades an existing install), and a `k1:` value
 * whose keystore is unavailable fails closed to "" — never leaks the stored bytes, never guesses.
 * The `p1:` branch needs android.util.Base64 (CryptoUtil), so it is covered by the desktop twin's
 * suite instead.
 */
class LocalSecretsPrefixTest {

    @Test
    fun emptyValueStaysEmpty() {
        assertEquals("", LocalSecrets.encrypt(""))
        assertEquals("", LocalSecrets.decrypt(""))
    }

    @Test
    fun legacyPlaintextValueIsReturnedUntouched() {
        // A value written before LocalSecrets existed has no prefix; decrypt must hand it back
        // verbatim so the caller re-seals it on the next save.
        assertEquals("legacy-api-key", LocalSecrets.decrypt("legacy-api-key"))
        assertEquals("", LocalSecrets.decrypt(""))
    }

    @Test
    fun keystoreSchemeValueFailsClosedWithoutTheKeystore() {
        // On a plain JVM there is no AndroidKeyStore, so secretKey() is unavailable: a k1: value
        // must decrypt to "" (the caller treats that as "no key set"), never to the stored bytes.
        assertEquals("", LocalSecrets.decrypt("k1:AAAA"))
        assertEquals("", LocalSecrets.decrypt("k1:"))
    }

    @Test
    fun recognisablePrefixIsRequiredToEnterTheCryptoPath() {
        // Anything without k1:/p1: is not crypto — even a string that looks Base64-ish stays as-is.
        assertEquals("v1:AAAA", LocalSecrets.decrypt("v1:AAAA"))
    }
}
