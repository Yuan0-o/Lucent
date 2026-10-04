package com.lucent.app.harness

actual fun <T> withHarnessLock(lock: Any, block: () -> T): T = synchronized(lock, block)

actual fun harnessCurrentTimeMillis(): Long = System.currentTimeMillis()
