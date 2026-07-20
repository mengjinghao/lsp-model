package com.mjh.shizukufix.utils

import java.io.BufferedReader
import java.io.InputStreamReader

data class ExecResult(val exitCode: Int, val stdout: String, val stderr: String)

object ShizukuHelper {

    private const val SHIZUKU_MANAGER_PKG = "moe.shizuku.manager"
    private const val SHIZUKU_PRIVILEGED_PKG = "moe.shizuku.privileged.api"
    private const val RIKKA_SHIZUKU_PKG = "rikka.shizuku.manager"
    private val SHIZUKU_DATA_DIRS = arrayOf(
        "/data/data/$SHIZUKU_MANAGER_PKG",
        "/data/data/$SHIZUKU_PRIVILEGED_PKG",
        "/data/data/$RIKKA_SHIZUKU_PKG",
        "/data/user/0/$SHIZUKU_MANAGER_PKG",
        "/data/user/0/$SHIZUKU_PRIVILEGED_PKG",
        "/data/user/0/$RIKKA_SHIZUKU_PKG"
    )

    fun execShell(command: String): ExecResult {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = BufferedReader(InputStreamReader(proc.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(proc.errorStream)).readText()
            val exitCode = proc.waitFor()
            ExecResult(exitCode, stdout.trim(), stderr.trim())
        } catch (e: Throwable) {
            LogX.e("ShizukuHelper execShell failed: ${e.message}", e)
            ExecResult(-1, "", e.message ?: "unknown error")
        }
    }

    fun execShell(commands: List<String>): List<ExecResult> {
        return commands.map { execShell(it) }
    }

    fun findShizukuDataDir(): String? {
        for (dir in SHIZUKU_DATA_DIRS) {
            val result = execShell("test -d $dir && echo EXISTS")
            if (result.stdout.contains("EXISTS")) return dir
        }
        return null
    }

    fun getShizukuSharedPrefsPath(): String? {
        val dataDir = findShizukuDataDir() ?: return null
        return "$dataDir/shared_prefs"
    }

    fun findShizukuManagerPackage(): String? {
        for (pkg in arrayOf(SHIZUKU_MANAGER_PKG, RIKKA_SHIZUKU_PKG, SHIZUKU_PRIVILEGED_PKG)) {
            val result = execShell("pm list packages $pkg")
            if (result.exitCode == 0 && result.stdout.contains(pkg)) return pkg
        }
        return null
    }
}
