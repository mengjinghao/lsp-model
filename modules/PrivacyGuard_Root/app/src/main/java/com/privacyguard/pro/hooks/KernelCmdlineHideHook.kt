package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.FakeDeviceCache
import com.privacyguard.pro.utils.InstanceTagger
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】内核 cmdline 隐藏Hook（Root 专属）
 *
 * 功能：
 *  - Hook 文件读取 /proc/cmdline 返回混淆内容
 *  - 干扰 APP 通过 cmdline 进行 Root/调试环境检测
 *
 * 应用场景：
 *  - 部分 APP 通过读取 /proc/cmdline 检测 Magisk、custom ROM、调试参数
 *  - 伪造为标准 Qualcomm 设备 cmdline
 *
 * 硬性限制：
 *  - 仅修改 Java 层读取 /proc/cmdline 的返回值
 *  - Native 层直接 open/read 系统调用的检测无法拦截
 *  - Hook File/RandomAccessFile 构造函数对性能有轻微影响
 */
object KernelCmdlineHideHook {

    /** 伪造的 /proc/cmdline 内容（标准 Qualcomm 设备） */
    private val FAKE_CMDLINE = FakeDeviceCache.fakeKernelCmdline

    /** /proc/cmdline 路径 */
    private const val CMDLINE_PATH = "/proc/cmdline"

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.kernelCmdlineHideEnabled) return
        LogX.i("【实验性】内核 cmdline 隐藏启动（Root 专属）")

        hookFileInputStreamForCmdline(lpparam)
        hookRandomAccessFileForCmdline(lpparam)
    }

    /**
     * Hook FileInputStream 读取 /proc/cmdline
     */
    private fun hookFileInputStreamForCmdline(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val fisCls = XposedHelpers.findClassIfExists(
                "java.io.FileInputStream", lpparam.classLoader) ?: return

            val fakeBytes = FAKE_CMDLINE.toByteArray()

            // FileInputStream(String path)
            try {
                XposedHelpers.findAndHookConstructor(fisCls, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val path = p.args[0] as? String ?: return
                                if (path == CMDLINE_PATH) {
                                    InstanceTagger.setTag(p.thisObject, "isCmdline", true)
                                    LogX.d("检测到 APP 读取 /proc/cmdline")
                                }
                            } catch (_: Throwable) {}
                        }
                    })
                LogX.hookSuccess("FileInputStream", "<init>(String) cmdline")
            } catch (_: Throwable) {}

            // FileInputStream(File file)
            try {
                val fileCls = XposedHelpers.findClassIfExists(
                    "java.io.File", lpparam.classLoader) ?: return
                XposedHelpers.findAndHookConstructor(fisCls, fileCls,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val file = p.args[0] ?: return
                                val path = XposedHelpers.callMethod(file, "getAbsolutePath") as? String ?: return
                                if (path == CMDLINE_PATH) {
                                    InstanceTagger.setTag(p.thisObject, "isCmdline", true)
                                    LogX.d("检测到 APP 通过 File 读取 /proc/cmdline")
                                }
                            } catch (_: Throwable) {}
                        }
                    })
                LogX.hookSuccess("FileInputStream", "<init>(File) cmdline")
            } catch (_: Throwable) {}

            // Hook read(byte[], int, int)
            try {
                XposedHelpers.findAndHookMethod(fisCls, "read",
                    ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val flag = InstanceTagger.getTag(p.thisObject, "isCmdline") as? Boolean ?: return
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
                LogX.hookSuccess("FileInputStream", "read(buf) cmdline")
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            LogX.hookFailed("FileInputStream", "cmdline", e)
        }
    }

    /**
     * Hook RandomAccessFile 读取 /proc/cmdline
     */
    private fun hookRandomAccessFileForCmdline(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val rafCls = XposedHelpers.findClassIfExists(
                "java.io.RandomAccessFile", lpparam.classLoader) ?: return

            val fakeBytes = FAKE_CMDLINE.toByteArray()

            // RandomAccessFile(String path, String mode)
            try {
                XposedHelpers.findAndHookConstructor(rafCls, String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val path = p.args[0] as? String ?: return
                                if (path == CMDLINE_PATH) {
                                    InstanceTagger.setTag(p.thisObject, "isCmdline", true)
                                    LogX.d("检测到 APP 通过 RandomAccessFile 读取 /proc/cmdline")
                                }
                            } catch (_: Throwable) {}
                        }
                    })
                LogX.hookSuccess("RandomAccessFile", "<init>(String, mode) cmdline")
            } catch (_: Throwable) {}

            // RandomAccessFile(File file, String mode)
            try {
                val fileCls = XposedHelpers.findClassIfExists(
                    "java.io.File", lpparam.classLoader) ?: return
                XposedHelpers.findAndHookConstructor(rafCls, fileCls, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val file = p.args[0] ?: return
                                val path = XposedHelpers.callMethod(file, "getAbsolutePath") as? String ?: return
                                if (path == CMDLINE_PATH) {
                                    InstanceTagger.setTag(p.thisObject, "isCmdline", true)
                                }
                            } catch (_: Throwable) {}
                        }
                    })
                LogX.hookSuccess("RandomAccessFile", "<init>(File, mode) cmdline")
            } catch (_: Throwable) {}

            // Hook readLine()
            try {
                XposedHelpers.findAndHookMethod(rafCls, "readLine",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val flag = InstanceTagger.getTag(p.thisObject, "isCmdline") as? Boolean ?: return
                                if (flag) {
                                    p.result = FAKE_CMDLINE
                                }
                            } catch (_: Throwable) {}
                        }
                    })
                LogX.hookSuccess("RandomAccessFile", "readLine cmdline")
            } catch (_: Throwable) {}

            // Hook read(byte[], int, int)
            try {
                XposedHelpers.findAndHookMethod(rafCls, "read",
                    ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val flag = InstanceTagger.getTag(p.thisObject, "isCmdline") as? Boolean ?: return
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
                LogX.hookSuccess("RandomAccessFile", "read(buf) cmdline")
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            LogX.hookFailed("RandomAccessFile", "cmdline", e)
        }
    }
}
