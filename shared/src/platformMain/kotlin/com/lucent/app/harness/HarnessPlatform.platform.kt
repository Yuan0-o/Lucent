package com.lucent.app.harness

import kotlin.jvm.Volatile

actual fun <T> withHarnessLock(lock: Any, block: () -> T): T = synchronized(lock, block)

actual fun harnessCurrentTimeMillis(): Long = System.currentTimeMillis()

actual typealias HarnessVolatile = Volatile

actual fun harnessUserHome(): String = System.getProperty("user.home")

actual fun harnessTempDir(): String = System.getProperty("java.io.tmpdir")

actual fun harnessBackgroundScope(): kotlinx.coroutines.CoroutineScope = com.lucent.app.AppScope.io

actual fun <T> harnessRunBlocking(block: suspend () -> T): T = kotlinx.coroutines.runBlocking { block() }
