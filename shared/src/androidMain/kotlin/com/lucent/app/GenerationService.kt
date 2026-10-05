package com.lucent.app

import android.content.Intent
import androidx.core.content.ContextCompat
import com.lucent.app.platform.PlatformContext

actual fun startGenerationService(context: PlatformContext, assistantName: String) {
    val intent = Intent().apply {
        setClassName(context, "com.lucent.app.GenerationService")
        putExtra("assistant_name", assistantName)
    }
    ContextCompat.startForegroundService(context.applicationContext, intent)
}

actual fun stopGenerationService(context: PlatformContext) {
    val intent = Intent().apply {
        setClassName(context, "com.lucent.app.GenerationService")
    }
    context.applicationContext.stopService(intent)
}
