package com.lucent.app.harness

import android.content.Context
import android.os.Build
import android.os.Environment

object AndroidWorkspacePicker {

    private const val TERMUX_PACKAGE = "com.termux"

    fun termuxInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0) }.isSuccess

    fun storageGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            true
        }
}
