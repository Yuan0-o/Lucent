package com.lucent.app.harness

import com.lucent.app.AppScope
import kotlinx.coroutines.CoroutineScope

actual fun <T> withHarnessLock(lock: Any, block: () -> T): T = synchronized(lock, block)

actual fun harnessCurrentTimeMillis(): Long = System.currentTimeMillis()

actual var harnessIsAndroid: Boolean
    get() = HarnessRuntime.android
    set(value) {
        HarnessRuntime.android = value
    }

actual fun harnessUserHome(): String = System.getProperty("user.home")

actual fun harnessTempDir(): String = System.getProperty("java.io.tmpdir")

actual fun <T> harnessRunBlocking(block: suspend () -> T): T = kotlinx.coroutines.runBlocking { block() }

actual fun harnessIoScope(): CoroutineScope = AppScope.io
