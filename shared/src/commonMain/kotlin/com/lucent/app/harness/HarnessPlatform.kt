package com.lucent.app.harness

import kotlinx.coroutines.CoroutineScope

expect fun <T> withHarnessLock(lock: Any, block: () -> T): T

expect fun harnessCurrentTimeMillis(): Long

expect var harnessIsAndroid: Boolean

expect fun harnessUserHome(): String

expect fun harnessTempDir(): String

expect fun <T> harnessRunBlocking(block: suspend () -> T): T

expect fun harnessIoScope(): CoroutineScope
