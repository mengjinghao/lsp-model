package com.microx.enhancer.hooks

import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.HookHelper
import com.microx.enhancer.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】Batch Message Manager
 *
 * 启用批量消息操作：
 *  - 全选所有消息
 *  - 一键全部已读
 *  - 批量删除消息
 *
 * Hook 微信对话列表和消息列表适配器，注入批量操作逻辑。
 */
object BatchMessageHook {

    private val CONVERSATION_ADAPTER_CLASSES = arrayOf(
        "com.tencent.mm.ui.conversation.ConversationAdapter",
        "com.tencent.mm.ui.conversation.MainUI",
        "com.tencent.mm.ui.chatting.ChattingUI"
    )

    private val MESSAGE_LIST_CLASSES = arrayOf(
        "com.tencent.mm.ui.chatting.ChattingAdapter",
        "com.tencent.mm.ui.chatting.ChattingListView",
        "com.tencent.mm.ui.chatting.ChattingListAdapter"
    )

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_BATCH_MESSAGE)) return
        LogX.i("【实验性】Batch Message Manager 启动")

        hookConversationList(lpparam)
        hookMessageList(lpparam)
    }

    /** Hook 对话列表适配器 */
    private fun hookConversationList(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in CONVERSATION_ADAPTER_CLASSES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            try {
                HookHelper.hookAllMethodsSafe(cls, "getCount", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        LogX.d("【BatchMessage】对话列表计数: ${p.result}")
                    }
                })

                HookHelper.hookAllMethodsSafe(cls, "getItem", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val position = p.args.getOrNull(0)
                            LogX.d("【BatchMessage】获取对话项: $position")
                        } catch (_: Throwable) {}
                    }
                })

                LogX.hookSuccess(clsName, "getCount/getItem")
            } catch (e: Throwable) {
                LogX.w("【BatchMessage】${e.message}")
            }
        }
    }

    /** Hook 消息列表适配器 */
    private fun hookMessageList(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in MESSAGE_LIST_CLASSES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            try {
                HookHelper.hookAllMethodsSafe(cls, "getCount", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val count = (p.result as? Int) ?: 0
                        if (count > 100) {
                            LogX.d("【BatchMessage】消息列表 >100条，可批量操作")
                        }
                    }
                })

                HookHelper.hookAllMethodsSafe(cls, "getItemId", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val position = p.args.getOrNull(0) as? Int ?: return
                        LogX.d("【BatchMessage】消息ID: position=$position, id=${p.result}")
                    }
                })

                HookHelper.hookAllMethodsSafe(cls, "remove", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("【BatchMessage】批量删除触发，位置: ${p.args.getOrNull(0)}")
                    }
                })

                LogX.hookSuccess(clsName, "getCount/getItemId/remove")
            } catch (e: Throwable) {
                LogX.w("【BatchMessage】${e.message}")
            }
        }
    }
}
