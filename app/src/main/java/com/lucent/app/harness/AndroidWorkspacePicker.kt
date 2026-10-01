package com.lucent.app.harness

import android.content.Context
import android.os.Build
import android.os.Environment

object AndroidWorkspacePicker {


    fun storageGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            true
        }
}
