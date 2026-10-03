package android.util

object Log {
    private fun line(level: String, tag: String, msg: String, tr: Throwable? = null): Int {
        System.err.println("[$level/$tag] $msg")
        tr?.printStackTrace()
        return 0
    }

    @JvmStatic fun d(tag: String, msg: String): Int = line("D", tag, msg)
    @JvmStatic fun i(tag: String, msg: String): Int = line("I", tag, msg)
    @JvmStatic fun w(tag: String, msg: String): Int = line("W", tag, msg)
    @JvmStatic fun w(tag: String, msg: String, tr: Throwable?): Int = line("W", tag, msg, tr)
    @JvmStatic fun e(tag: String, msg: String): Int = line("E", tag, msg)
    @JvmStatic fun e(tag: String, msg: String, tr: Throwable?): Int = line("E", tag, msg, tr)
}
