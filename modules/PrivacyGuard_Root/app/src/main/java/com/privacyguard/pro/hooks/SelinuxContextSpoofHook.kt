package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.FakeDeviceCache
import com.privacyguard.pro.utils.InstanceTagger
import com.privacyguard.pro.utils.LogX
import com.privacyguard.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】SELinux 上下文伪造Hook（Root 专属）
 *
 * 功能：
 *  - 应用层 Hook android.os.SELinux.getSELinuxContext 返回伪造上下文
 *  - 应用层 Hook 读取 /proc/self/attr/current 文件返回伪造内容
 *  - 通过 Shizuku 执行 getenforce 查看当前 SELinux 状态（仅观察，不强制）
 *
 * 应用场景：
 *  - 部分 APP 通过读取 SELinux 上下文检测 Root/调试环境
 *  - 伪造为标准 untrusted_app 上下文（u:r:untrusted_app:s0:c512,c768）
 *
 * 硬性限制：
 *  - 必须先检查 ShizukuHelper.isShizukuAvailable() 才读取系统状态
 *  - 仅修改应用层 Java 调用的返回值，不修改内核 attr
 *  - Hook File/RandomAccessFile 构造函数对性能有轻微影响
 */
object SelinuxContextSpoofHook {

    /** 伪造的 SELinux 上下文（标准 untrusted_app） */
    private val FAKE_CONTEXT = FakeDeviceCache.fakeSelinuxContext

    /** /proc/self/attr/current 路径 */
    private const val ATTR_CURRENT_PATH = "/proc/self/attr/current"

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.selinuxContextSpoofEnabled) return
        LogX.i("【实验性】SELinux 上下文伪造启动（Root 专属）")

        hookSelinuxClass(lpparam)
        hookFileReadForAttr(lpparam)
        observeSelinuxStatus()
    }

    /**
     * Hook android.os.SELinux.getSELinuxContext
     * 这是 Java 层查询 SELinux 上下文的标准 API
     */
    private fun hookSelinuxClass(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val selinuxCls = XposedHelpers.findClassIfExists(
                "android.os.SELinux", lpparam.classLoader) ?: return

            // getSELinuxContext() 静态方法
            try {
                XposedHelpers.findAndHookMethod(selinuxCls, "getSELinuxContext",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = FAKE_CONTEXT
                            LogX.d("SELinux.getSELinuxContext -> 伪造为 untrusted_app")
                        }
                    })
                LogX.hookSuccess("SELinux", "getSELinuxContext")
            } catch (_: Exception) {}

            // getSELinuxContext(String path) 静态方法
            try {
                XposedHelpers.findAndHookMethod(selinuxCls, "getSELinuxContext",
                    String::class.java, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = FAKE_CONTEXT
                        }
                    })
                LogX.hookSuccess("SELinux", "getSELinuxContext(path)")
            } catch (_: Exception) {}

            // isSELinuxEnabled / isSELinuxEnforced 保留真实值（不修改，避免影响功能）
        } catch (e: Exception) {
            LogX.hookFailed("SELinux", "getSELinuxContext", e)
        }
    }

    /**
     * Hook 文件读取 /proc/self/attr/current
     *
     * APP 可能直接通过 FileInputStream / RandomAccessFile 读取该文件获取上下文
     * 这里 Hook FileInputStream 构造函数 + read 方法，对 /proc/self/attr/current 返回伪造内容
     */
    private fun hookFileReadForAttr(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook FileInputStream 构造函数：记录路径
        try {
            val fisCls = XposedHelpers.findClassIfExists(
                "java.io.FileInputStream", lpparam.classLoader) ?: return

            // FileInputStream(String path)
            try {
                XposedHelpers.findAndHookConstructor(fisCls, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val path = p.args[0] as? String ?: return
                                if (path == ATTR_CURRENT_PATH || path.contains("attr/current")) {
                                    // 标记该 FileInputStream 实例
                                    InstanceTagger.setTag(p.thisObject, "isAttrCurrent", true)
                                    LogX.d("检测到 APP 读取 /proc/self/attr/current")
                                }
                            } catch (_: Throwable) {}
                        }
                    })
                LogX.hookSuccess("FileInputStream", "<init>(String) attr-current")
            } catch (_: Throwable) {}

            // Hook read() / read(byte[]) 返回伪造内容
            val fakeBytes = FAKE_CONTEXT.toByteArray()

            try {
                XposedHelpers.findAndHookMethod(fisCls, "read",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val flag = InstanceTagger.getTag(p.thisObject, "isAttrCurrent") as? Boolean ?: return
                                if (flag) {
                                    p.result = if (fakeBytes.isNotEmpty()) fakeBytes[0].toInt() and 0xFF else -1
                                }
                            } catch (_: Throwable) {}
                        }
                    })
            } catch (_: Throwable) {}

            try {
                XposedHelpers.findAndHookMethod(fisCls, "read",
                    ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val flag = InstanceTagger.getTag(p.thisObject, "isAttrCurrent") as? Boolean ?: return
                                if (!flag) return
                                val buf = p.args[0] as? ByteArray ?: return
                                val off = p.args[1] as Int
                                val len = p.args[2] as Int
                                val n = minOf(fakeBytes.size, len)
                                System.arraycopy(fakeBytes, 0, buf, off, n)
                                p.result = n
                            } catch (_: Throwable) {}
                        }
                    })
                LogX.hookSuccess("FileInputStream", "read(buf) attr-current")
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            LogX.hookFailed("FileInputStream", "attr-current", e)
        }
    }

    /**
     * 通过 Shizuku 执行 getenforce 查看 SELinux 状态（仅观察）
     */
    private fun observeSelinuxStatus() {
        try {
            if (!ShizukuHelper.isShizukuAvailable()) {
                LogX.d("Shizuku不可用，跳过 SELinux 状态观察")
                return
            }
            val result = ShizukuHelper.execShell("getenforce")
            LogX.i("当前 SELinux 状态: $result")
        } catch (_: Throwable) {}
    }
}
