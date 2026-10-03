package com.lucent.app.data

expect object LocalSecrets {
    fun encrypt(value: String): String
    fun decrypt(stored: String): String
}
