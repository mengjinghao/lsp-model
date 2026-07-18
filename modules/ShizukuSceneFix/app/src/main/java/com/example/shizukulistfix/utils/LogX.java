package com.example.shizukulistfix.utils;

import android.util.Log;

public class LogX {

    public static final String TAG = "ShizukuListFix";
    private static boolean sDebug = true;

    private LogX() {
    }

    public static void setDebug(boolean debug) {
        sDebug = debug;
    }

    public static boolean isDebug() {
        return sDebug;
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
    }

    public static void d(String msg) {
        if (sDebug) {
            Log.d(TAG, msg);
        }
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
    }

    public static void d(String format, Object... args) {
        if (sDebug) {
            Log.d(TAG, String.format(format, args));
        }
    }

    public static void i(String format, Object... args) {
        Log.i(TAG, String.format(format, args));
    }

    public static void e(String format, Object... args) {
        Log.e(TAG, String.format(format, args));
    }
}
