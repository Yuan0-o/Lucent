package com.lucent.app.platform

actual object PlatformLog {
    private fun line(level: String, tag: String, msg: String, tr: Throwable? = null): Int {
        System.err.println("[$level/$tag] $msg")
        tr?.printStackTrace()
        return 0
    }

    @JvmStatic actual fun d(tag: String, msg: String): Int = line("D", tag, msg)
    @JvmStatic actual fun i(tag: String, msg: String): Int = line("I", tag, msg)
    @JvmStatic actual fun w(tag: String, msg: String): Int = line("W", tag, msg)
    @JvmStatic actual fun w(tag: String, msg: String, tr: Throwable?): Int = line("W", tag, msg, tr)
    @JvmStatic actual fun e(tag: String, msg: String): Int = line("E", tag, msg)
    @JvmStatic actual fun e(tag: String, msg: String, tr: Throwable?): Int = line("E", tag, msg, tr)
}
