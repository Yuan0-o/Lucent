package com.lucent.app.harness

expect fun <T> withHarnessLock(lock: Any, block: () -> T): T

expect fun harnessCurrentTimeMillis(): Long

expect var harnessIsAndroid: Boolean
