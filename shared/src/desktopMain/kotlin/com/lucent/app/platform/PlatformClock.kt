package com.lucent.app.platform

actual fun platformElapsedRealtime(): Long = System.nanoTime() / 1_000_000L
