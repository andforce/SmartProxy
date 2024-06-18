package me.smartproxy.ui.utils

import android.util.Log

object Logger {
    @JvmStatic
    fun i(tag: String?, msg: String): Int {
        return Log.i(tag, msg)
    }

    @JvmStatic
    fun v(tag: String?, msg: String): Int {
        return Log.v(tag, msg)
    }

    @JvmStatic
    fun w(tag: String?, msg: String): Int {
        return Log.w(tag, msg)
    }

    @JvmStatic
    fun d(tag: String?, msg: String): Int {
        return Log.d(tag, msg)
    }

    @JvmStatic
    fun e(tag: String?, msg: String?, tr: Throwable?): Int {
        return Log.e(tag, msg, tr)
    }

    @JvmStatic
    fun e(tag: String?, msg: String): Int {
        return Log.e(tag, msg)
    }
}