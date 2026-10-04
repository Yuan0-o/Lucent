package com.lucent.app.platform

object PlatformLog {
    @JvmStatic fun d(tag: String, msg: String): Int = android.util.Log.d(tag, msg)
    @JvmStatic fun i(tag: String, msg: String): Int = android.util.Log.i(tag, msg)
    @JvmStatic fun w(tag: String, msg: String): Int = android.util.Log.w(tag, msg)
    @JvmStatic fun w(tag: String, msg: String, tr: Throwable?): Int = android.util.Log.w(tag, msg, tr)
    @JvmStatic fun e(tag: String, msg: String): Int = android.util.Log.e(tag, msg)
    @JvmStatic fun e(tag: String, msg: String, tr: Throwable?): Int = android.util.Log.e(tag, msg, tr)
}
