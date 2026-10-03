package com.lucent.app.platform

actual fun platformElapsedRealtime(): Long = android.os.SystemClock.elapsedRealtime()
