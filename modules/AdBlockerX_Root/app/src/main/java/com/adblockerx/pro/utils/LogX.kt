package com.adblockerx.pro.utils

import android.util.Log

/**
 * 统一日志工具
 * TAG: AdBlockerXPro，可用 adb logcat -s AdBlockerXPro:V 查看
 */
object LogX {
    private const val TAG = "AdBlockerXPro"
    var debugEnabled = true

    fun d(msg: String) { if (debugEnabled) Log.d(TAG, msg) }
    fun i(msg: String) { Log.i(TAG, msg) }
    fun w(msg: String) { Log.w(TAG, msg) }
    fun e(msg: String) { Log.e(TAG, msg) }
    fun e(msg: String, t: Throwable) { Log.e(TAG, msg, t) }
}
