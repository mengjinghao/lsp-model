package com.notifymaster.pro.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.ui.components.FeatureCard
import com.notifymaster.pro.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: NotifyConfig, onConfigChange: (NotifyConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("基础功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "通知过滤", "根据关键词命中拦截通知（含关键词的不显示）",
            cfg.notifyFilterEnabled,
            { val nc = cfg.copy(notifyFilterEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "防通知撤回", "拦截应用主动 cancel 通知（防撤回提示被清掉）",
            cfg.antiRecallNotifyEnabled,
            { val nc = cfg.copy(antiRecallNotifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "通知历史", "记录通知到内存历史列表（提供查询接口）",
            cfg.notifyHistoryEnabled,
            { val nc = cfg.copy(notifyHistoryEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "通知美化", "Hook Notification.Builder 修改通知样式（图标/颜色/标题）",
            cfg.notifyBeautifyEnabled,
            { val nc = cfg.copy(notifyBeautifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("应用层实验性", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "通知分组", "同类通知合并为组（setGroup + 汇总通知）",
            cfg.batchNotifyEnabled,
            { val nc = cfg.copy(batchNotifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "优先级覆盖", "强制将通知优先级提升到指定级别（IMPORTANT）",
            cfg.priorityOverrideEnabled,
            { val nc = cfg.copy(priorityOverrideEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "静默通知", "指定 APP 通知静默（不响铃不震动）",
            cfg.silentNotifyEnabled,
            { val nc = cfg.copy(silentNotifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("Root 专属（需 Shizuku）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "系统通知策略", "Shizuku 调用 dumpsys notification + settings put global 通知策略",
            cfg.systemNotifyHookEnabled,
            { val nc = cfg.copy(systemNotifyHookEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "全局绕过勿扰", "通过 Shizuku 关闭 Zen Mode，让所有通知无视 DND",
            cfg.globalPolicyBypassEnabled,
            { val nc = cfg.copy(globalPolicyBypassEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "NotificationListener 监听", "Hook 系统 NotificationListenerService 全局监听所有通知",
            cfg.notifyListenerHookEnabled,
            { val nc = cfg.copy(notifyListenerHookEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("Root 实验性", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "全局通知过滤", "跨 APP 通知过滤（Hook Listener + cmd notification cancel）",
            cfg.globalNotifyFilterEnabled,
            { val nc = cfg.copy(globalNotifyFilterEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Shizuku 通知桥接", "Shizuku 执行 cmd notification post/refresh（拦截后重新 post）",
            cfg.shizukuNotifyBridgeEnabled,
            { val nc = cfg.copy(shizukuNotifyBridgeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("Root v1.1.0 系统级（需 Shizuku）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "通知策略直接修改", "Shizuku 修改 /data/system/notification_policy.xml + cmd notification refresh",
            cfg.notificationPolicyEditEnabled,
            { val nc = cfg.copy(notificationPolicyEditEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "通知监听器注入", "Shizuku 修改 notification_listeners.xml + cmd notification set_listener 系统级注入",
            cfg.listenerInjectEnabled,
            { val nc = cfg.copy(listenerInjectEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        // 优先级覆盖滑块
        if (cfg.priorityOverrideEnabled) {
            Spacer(Modifier.height(16.dp))
            Text("优先级覆盖级别", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val levels = listOf("MIN", "LOW", "HIGH", "MAX")
            Text("当前: ${levels.getOrElse(cfg.priorityOverrideLevel) { "HIGH" }}", style = MaterialTheme.typography.bodySmall)
            val priorityState = remember(cfg) { mutableFloatStateOf(cfg.priorityOverrideLevel.toFloat()) }
            Slider(
                value = priorityState.floatValue,
                onValueChange = { priorityState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(priorityOverrideLevel = priorityState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 0f..3f, steps = 2
            )
        }

        // 美化参数
        if (cfg.notifyBeautifyEnabled) {
            Spacer(Modifier.height(16.dp))
            Text("美化参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("主色: ${Integer.toHexString(cfg.beautifyColor)}", style = MaterialTheme.typography.bodySmall)
            Text("标题前缀: \"${cfg.beautifyTitlePrefix}\"", style = MaterialTheme.typography.bodySmall)
            Text("覆盖图标: ${cfg.beautifyOverrideIcon}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
