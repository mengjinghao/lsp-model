package com.notifymaster.pro.models

/**
 * 通知管理配置（Root 版）
 *
 * 包含 NoRoot 版全部字段，并额外增加系统级 Hook 配置：
 *  - SystemNotifyHook: Shizuku 调用 dumpsys notification + settings put global 通知策略
 *  - NotifyListenerServiceHook: Hook 系统 NotificationListenerService 全局监听所有通知
 *  - GlobalNotifyFilterHook: 全局通知过滤（跨 APP）（实验性）
 *  - ShizukuNotifyBridgeHook: Shizuku 执行 cmd notification post/refresh（实验性）
 *
 * 硬性限制：
 *  - 系统级 Hook 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - settings put 修改非持久化，重启后消失（仅当前会话）
 *  - Hook system_server 需 LSPosed Root 模式（LSPatch 不可用）
 */
data class NotifyConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,

    // ===== 基础功能（同 NoRoot 版） =====
    var notifyFilterEnabled: Boolean = false,
    var antiRecallNotifyEnabled: Boolean = false,
    var notifyHistoryEnabled: Boolean = false,
    var notifyBeautifyEnabled: Boolean = false,

    // ===== 应用层实验性（同 NoRoot 版） =====
    var batchNotifyEnabled: Boolean = false,
    var priorityOverrideEnabled: Boolean = false,
    var silentNotifyEnabled: Boolean = false,

    // ===== Root 专属：系统通知策略（Shizuku dumpsys + settings put） =====
    var systemNotifyHookEnabled: Boolean = false,
    var globalPolicyBypassEnabled: Boolean = false,        // 全局绕过勿扰
    var globalImportanceFloor: Int = 2,                    // 全局最低优先级（0..5）

    // ===== Root 专属：系统级监听（Hook NotificationListenerService） =====
    var notifyListenerHookEnabled: Boolean = false,
    var captureAllNotifications: Boolean = false,           // 捕获所有通知（含其他 APP）

    // ===== Root 实验性：全局通知过滤（跨 APP） =====
    var globalNotifyFilterEnabled: Boolean = false,
    var globalFilterKeywords: MutableList<String> = mutableListOf("广告", "推广", "营销"),

    // ===== Root 实验性：Shizuku 通知桥接（cmd notification post/refresh） =====
    var shizukuNotifyBridgeEnabled: Boolean = false,
    var bridgePostOnIntercept: Boolean = false,             // 拦截后是否通过 Shizuku 重新 post
    /** 系统通知策略直接修改（Shizuku 修改 notification_policy.xml） */
    var notificationPolicyEditEnabled: Boolean = false,
    /** 系统级通知监听器注入（Shizuku 修改 notification_listeners.xml + cmd set_listener） */
    var listenerInjectEnabled: Boolean = false,

    // ===== 参数（同 NoRoot 版） =====
    var filterKeywords: MutableList<String> = mutableListOf(
        "广告", "推广", "推广链接", "营销", "限时抢购"
    ),
    var targetApps: MutableList<String> = mutableListOf(
        "com.tencent.mm", "com.tencent.mobileqq", "com.eg.android.AlipayGphone",
        "com.taobao.taobao", "com.jingdong.app.mall", "com.xunmeng.pinduoduo",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker", "com.netease.cloudmusic",
        "com.tencent.wmusic", "com.sina.weibo", "com.zhihu.android",
        "com.baidu.searchbox", "com.ss.android.article.news"
    ),

    // ===== 美化参数 =====
    var beautifyColor: Int = 0xFF00695C.toInt(),
    var beautifyTitlePrefix: String = "",
    var beautifyOverrideIcon: Boolean = false,

    // ===== 优先级覆盖参数 =====
    var priorityOverrideLevel: Int = 2,

    // ===== 静默通知参数 =====
    var silentTargetApps: MutableList<String> = mutableListOf(),

    // ===== 通知分组参数 =====
    var batchGroupKey: String = "notifymaster_group",
    var batchMaxCount: Int = 5,

    var lastModified: Long = 0L
)
