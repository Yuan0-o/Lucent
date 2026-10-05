package com.lucent.app.platform

actual object PlatformLog {
    @JvmStatic actual fun d(tag: String, msg: String): Int = android.util.Log.d(tag, msg)
    @JvmStatic actual fun i(tag: String, msg: String): Int = android.util.Log.i(tag, msg)
    @JvmStatic actual fun w(tag: String, msg: String): Int = android.util.Log.w(tag, msg)
    @JvmStatic actual fun w(tag: String, msg: String, tr: Throwable?): Int = android.util.Log.w(tag, msg, tr)
    @JvmStatic actual fun e(tag: String, msg: String): Int = android.util.Log.e(tag, msg)
    @JvmStatic actual fun e(tag: String, msg: String, tr: Throwable?): Int = android.util.Log.e(tag, msg, tr)
}
