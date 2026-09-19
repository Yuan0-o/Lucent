package com.lucent.app.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

object ShareIntegration {

    private const val ALIAS_CLASS = "com.lucent.app.ShareTarget"

    private fun aliasComponent(context: Context) = ComponentName(context.packageName, ALIAS_CLASS)

    fun setEnabled(context: Context, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        try {
            context.applicationContext.packageManager.setComponentEnabledSetting(
                aliasComponent(context),
                state,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Throwable) {
        }
    }

    data class Shared(val text: String?, val streamUri: Uri?, val mime: String?)

    fun parse(intent: Intent?): Shared? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (text.isNullOrBlank() && stream == null) return null
        return Shared(text = text?.takeIf { it.isNotBlank() }, streamUri = stream, mime = intent.type)
    }
}
