package com.vipunlock.pro.hooks

import com.vipunlock.pro.models.VipConfig
import com.vipunlock.pro.utils.LogX
import com.vipunlock.pro.utils.ShizukuHelper
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Play Store 数据库直接修改 Hook（Root 专属）
 *
 * 通过 Shizuku 直接备份、修改、回传 Play Store 本地购买记录数据库。
 *
 * 操作流程：
 *  1. cp localappstate.db -> /data/local/tmp/ 备份
 *  2. sqlite3 修改数据库，标记目标 APP 为已购买
 *  3. cp 修改后的 DB 回原路径
 *  4. chmod 660 设置正确权限
 *  5. am force-stop + am start 重启 Play Store 使修改生效
 *
 * 硬性限制：
 *  - 需要 Shizuku 可用
 *  - 需要 root 级别权限操作 /data/data/com.android.vending/
 *  - 修改数据库有风险，建议先备份后操作
 */
object PlayStoreDbHook {

    private const val DB_PATH = "/data/data/com.android.vending/databases/localappstate.db"
    private const val BACKUP_PATH = "/data/local/tmp/localappstate.db"

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.playStoreDbModifyEnabled) return
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过 Play Store DB 修改")
            return
        }
        LogX.i("【Root】Play Store DB 修改启动")

        val pkg = lpparam.packageName

        try {
            // 1. 备份原始数据库
            val backupResult = ShizukuHelper.execShell("cp $DB_PATH $BACKUP_PATH 2>/dev/null && echo 'OK'")
            LogX.i("备份 Play Store DB: $backupResult")

            // 2. 通过 sqlite3 修改数据库，标记目标 APP 为已购买
            val modifyCmd = buildString {
                append("sqlite3 $BACKUP_PATH ")
                append("\"INSERT OR REPLACE INTO appstate (package_name, auto_acquire, desired_auto_acquire, desired_auto_acquire_time, is_purchased) ")
                append("VALUES ('$pkg', 1, 1, ${System.currentTimeMillis()}, 1);\" 2>/dev/null")
            }
            ShizukuHelper.execShell(modifyCmd)
            LogX.i("已修改数据库，标记 $pkg 为已购买")

            // 3. 回传修改后的数据库
            val copyResult = ShizukuHelper.execShell("cp $BACKUP_PATH $DB_PATH 2>/dev/null && echo 'OK'")
            LogX.i("回传数据库: $copyResult")

            // 4. 设置正确权限
            ShizukuHelper.execShell("chmod 660 $DB_PATH 2>/dev/null")
            ShizukuHelper.execShell("chown \$(stat -c '%u:%g' /data/data/com.android.vending) $DB_PATH 2>/dev/null")
            LogX.i("已设置数据库权限 660")

            // 5. 重启 Play Store 使修改生效
            ShizukuHelper.execShell("am force-stop com.android.vending 2>/dev/null")
            ShizukuHelper.execShell("am start -n com.android.vending/.AssetBrowserActivity 2>/dev/null")
            LogX.i("已重启 Play Store")

        } catch (e: Throwable) {
            LogX.w("Play Store DB 修改异常: ${e.message}")
        }
    }
}
