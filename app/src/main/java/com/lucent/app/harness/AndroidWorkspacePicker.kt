package com.lucent.app.harness

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

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

    fun pathFromTree(uri: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
        if (id.isBlank()) return ""
        val volume = id.substringBefore(":")
        val relative = id.substringAfter(":", "")
        val base = when {
            volume.isBlank() || volume.equals("primary", ignoreCase = true) ->
                Environment.getExternalStorageDirectory()
            else -> File("/storage", volume)
        }
        val target = if (relative.isBlank()) base else File(base, relative)
        return target.path
    }
}
