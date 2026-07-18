package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import com.adblockerx.pro.utils.ShizukuHelper

/**
 * 系统级 hosts 文件操作 Hook（Root 版独有）
 *
 * 通过 Shizuku 执行系统命令：
 *  - 优先写入 /data/adb/modules/adblockerx/system/etc/hosts（Magisk 模块路径风格，需 Magisk）
 *  - 回退写入 /data/local/tmp/adblockerx_hosts.txt，再 mount --bind 到 /system/etc/hosts
 *  - 提供恢复原 hosts 的方法（删除文件 + 重新 mount）
 *
 * 注意事项：
 *  - /system 分区只读，不能直接 echo 写入
 *  - 需 Root 或 Shizuku adb 级授权
 *  - 在 system_server 进程不做任何 Hook，仅由本类在 APP 进程中通过 Shizuku 操作
 *
 * 风险声明：
 *  - 修改系统 hosts 会影响整机所有 APP 的 DNS 解析
 *  - 操作失败可能导致 DNS 异常，已内置恢复机制
 */
object SystemHostsHook {

    /** Magisk 模块路径风格（推荐，无侵入） */
    private const val MAGISK_HOSTS_DIR = "/data/adb/modules/adblockerx/system/etc"
    private const val MAGISK_HOSTS_PATH = "$MAGISK_HOSTS_DIR/hosts"

    /** 回退路径（/data 分区可写） */
    private const val TMP_HOSTS_PATH = "/data/local/tmp/adblockerx_hosts.txt"

    /** 备份原 hosts 路径 */
    private const val BACKUP_HOSTS_PATH = "/data/local/tmp/adblockerx_hosts.bak"

    private var isApplied = false

    fun apply(lpparam: de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.systemHostsEnabled) {
            LogX.d("SystemHosts 未启用，跳过")
            return
        }
        if (isApplied) return
        isApplied = true

        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，无法修改系统 hosts")
            return
        }

        LogX.i("SystemHosts 启动：通过 Shizuku 写入系统级广告拦截 hosts")

        // 1. 备份原 hosts
        backupOriginalHosts()

        // 2. 生成并写入新 hosts
        val blockedHosts = HostsFilterHook.currentBlockedHosts()
        if (blockedHosts.isEmpty()) {
            LogX.w("黑名单为空，跳过 hosts 写入")
            return
        }
        writeHostsFile(blockedHosts)

        // 3. 尝试 bind mount
        tryMountBind()
    }

    /** 备份原始 /system/etc/hosts */
    private fun backupOriginalHosts() {
        try {
            ShizukuHelper.execShell("cp /system/etc/hosts $BACKUP_HOSTS_PATH 2>/dev/null || cp /etc/hosts $BACKUP_HOSTS_PATH 2>/dev/null")
            LogX.d("原 hosts 已备份到 $BACKUP_HOSTS_PATH")
        } catch (e: Throwable) {
            LogX.w("备份 hosts 失败: ${e.message}")
        }
    }

    /**
     * 写入新 hosts 文件
     * 优先 Magisk 模块路径，回退 /data/local/tmp
     */
    private fun writeHostsFile(blockedHosts: List<String>) {
        val sb = StringBuilder()
        sb.append("# AdBlockerX Pro - generated hosts file\n")
        sb.append("# 127.0.0.1 localhost\n")
        sb.append("127.0.0.1 localhost\n")
        sb.append("::1 localhost\n\n")
        sb.append("# ===== AdBlockerX 拦截列表 =====\n")
        for (host in blockedHosts) {
            val h = host.trim()
            if (h.isBlank() || h.startsWith("#")) continue
            sb.append("127.0.0.1 $h\n")
        }

        val tmpContent = sb.toString()
        // 写入 /data/local/tmp/（/data 分区普通权限即可）
        val escaped = tmpContent.replace("'", "'\\''")
        ShizukuHelper.execShell("echo '$escaped' > $TMP_HOSTS_PATH")
        ShizukuHelper.execShell("chmod 644 $TMP_HOSTS_PATH")
        LogX.i("已写入临时 hosts: $TMP_HOSTS_PATH（${blockedHosts.size} 条）")

        // 尝试写入 Magisk 模块路径
        ShizukuHelper.execShell("mkdir -p $MAGISK_HOSTS_DIR")
        ShizukuHelper.execShell("cp $TMP_HOSTS_PATH $MAGISK_HOSTS_PATH")
        LogX.d("已尝试写入 Magisk 模块路径: $MAGISK_HOSTS_PATH")
    }

    /**
     * 尝试 bind mount 到 /system/etc/hosts
     * 需 Root（Shizuku adb 模式可能无 mount 权限）
     */
    private fun tryMountBind() {
        try {
            // mount --bind 需要真 Root，Shizuku adb 模式可能失败
            val result = ShizukuHelper.execShell("mount --bind $TMP_HOSTS_PATH /system/etc/hosts 2>&1")
            if (result != null && (result.contains("denied") || result.contains("Permission denied"))) {
                LogX.w("mount --bind 被拒绝（可能仅为 Shizuku adb 模式）。建议使用 Magisk 模块方式或 Root 授权")
            } else {
                LogX.i("mount --bind 成功")
            }
        } catch (e: Throwable) {
            LogX.w("mount --bind 失败: ${e.message}（不影响 Magisk 模块路径生效）")
        }
    }

    /**
     * 恢复原 hosts
     * 用户在 UI 中点击"恢复系统 hosts"调用
     */
    fun restoreOriginalHosts(): Boolean {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，无法恢复 hosts")
            return false
        }
        try {
            // 1. 卸载 bind mount
            ShizukuHelper.execShell("umount /system/etc/hosts 2>/dev/null")
            // 2. 删除 Magisk 模块
            ShizukuHelper.execShell("rm -rf /data/adb/modules/adblockerx 2>/dev/null")
            // 3. 删除临时文件
            ShizukuHelper.execShell("rm -f $TMP_HOSTS_PATH 2>/dev/null")
            // 4. 从备份恢复（如有）
            ShizukuHelper.execShell("cp $BACKUP_HOSTS_PATH /system/etc/hosts 2>/dev/null || true")
            LogX.i("系统 hosts 已恢复")
            isApplied = false
            return true
        } catch (e: Throwable) {
            LogX.e("恢复 hosts 异常", e)
            return false
        }
    }

    /** 释放资源 */
    fun release() {
        isApplied = false
    }
}
