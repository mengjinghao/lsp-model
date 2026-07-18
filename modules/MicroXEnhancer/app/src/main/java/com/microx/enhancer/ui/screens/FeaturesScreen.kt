package com.microx.enhancer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.microx.enhancer.models.MicroXConfig
import com.microx.enhancer.ui.components.FeatureCard
import com.microx.enhancer.ui.components.SectionHeader
import com.microx.enhancer.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: MicroXConfig, onConfigChange: (MicroXConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        SectionHeader("通用功能（微信 + QQ）")

        FeatureCard(
            "广告净化", "拦截开屏/朋友圈/公众号/小程序/聊天页广告",
            cfg.adBlockEnabled,
            { cfg.adBlockEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "消息防撤回", "保留被撤回的消息，提示撤回方/撤回时间",
            cfg.antiRecallEnabled,
            { cfg.antiRecallEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "界面美化", "隐藏小红点 / 去除底部Tab / 自定义气泡（默认关闭，影响体验）",
            cfg.uiModEnabled,
            { cfg.uiModEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "隐私增强", "隐藏正在输入/已读状态，防读取回执",
            cfg.privacyEnabled,
            { cfg.privacyEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "自动回复", "按预设关键词自动回复消息（5分钟防刷屏）",
            cfg.autoReplyEnabled,
            { cfg.autoReplyEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )

        Spacer(Modifier.height(16.dp))
        SectionHeader("微信专属")

        FeatureCard(
            "朋友圈保护", "防删朋友圈 + 朋友圈精简，过滤营销动态",
            cfg.momentProtectEnabled,
            { cfg.momentProtectEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "批量管理", "一键清理缓存 / 导出好友 / 僵尸粉检测 / 群聊管理",
            cfg.batchManageEnabled,
            { cfg.batchManageEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )

        Spacer(Modifier.height(16.dp))
        SectionHeader("QQ专属")

        // QQ 专属功能（暂时无独立开关，由通用功能覆盖）
        FeatureCard(
            "QQ广告净化（通用）", "由上方「广告净化」开关统一控制",
            cfg.adBlockEnabled,
            { cfg.adBlockEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )

        Spacer(Modifier.height(16.dp))
        SectionHeader("实验性功能")

        FeatureCard(
            "语音消息导出", "Hook 语音播放，自动保存 amr 文件到 /sdcard/MicroXEnhancer/voice/",
            cfg.voiceMessageExportEnabled,
            { cfg.voiceMessageExportEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "消息搜索增强", "放宽微信搜索时间范围限制，搜索全部历史消息",
            cfg.messageSearchEnhanceEnabled,
            { cfg.messageSearchEnhanceEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "自定义主题", "Hook 资源加载，替换微信/QQ主色调为青色",
            cfg.customThemeEnabled,
            { cfg.customThemeEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )

        Spacer(Modifier.height(16.dp))
        SectionHeader("适配辅助")

        FeatureCard(
            "绕过 Xposed 检测", "Hook 安全检测方法，避免被微信/QQ识别为风险环境",
            cfg.bypassDetectionEnabled,
            { cfg.bypassDetectionEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )

        Spacer(Modifier.height(40.dp))
    }
}
