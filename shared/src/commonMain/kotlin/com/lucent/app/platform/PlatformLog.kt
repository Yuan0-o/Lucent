package com.lucent.app.platform

expect object PlatformLog {
    fun d(tag: String, msg: String): Int
    fun i(tag: String, msg: String): Int
    fun w(tag: String, msg: String): Int
    fun w(tag: String, msg: String, tr: Throwable?): Int
    fun e(tag: String, msg: String): Int
    fun e(tag: String, msg: String, tr: Throwable?): Int
}
