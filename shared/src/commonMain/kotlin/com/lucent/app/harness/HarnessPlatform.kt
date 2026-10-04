package com.lucent.app.harness

expect fun <T> withHarnessLock(lock: Any, block: () -> T): T

expect fun harnessCurrentTimeMillis(): Long

@Target(AnnotationTarget.FIELD)
expect annotation class HarnessVolatile()

expect fun harnessUserHome(): String

expect fun harnessTempDir(): String

expect fun harnessBackgroundScope(): kotlinx.coroutines.CoroutineScope

expect fun <T> harnessRunBlocking(block: suspend () -> T): T
