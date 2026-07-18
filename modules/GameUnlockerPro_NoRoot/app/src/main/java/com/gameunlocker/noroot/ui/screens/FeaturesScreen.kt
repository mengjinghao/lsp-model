package com.gameunlocker.noroot.ui.screens

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gameunlocker.noroot.model.GameConfig
import com.gameunlocker.noroot.ui.components.FeatureCard
import com.gameunlocker.noroot.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: GameConfig, onConfigChange: (GameConfig) -> Unit) {
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
            "机型伪装", "伪装 Build/SystemProperties 为旗舰机型，规避机型检测",
            cfg.deviceSpoofEnabled,
            { cfg.deviceSpoofEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "帧率解锁", "Hook Display/Surface/Unity/Unreal 强制目标帧率",
            cfg.frameRateUnlockEnabled,
            { cfg.frameRateUnlockEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "环境隐藏", "隐藏 Xposed/Shizuku/LSPatch/Magisk 等敏感环境",
            cfg.detectionHideEnabled,
            { cfg.detectionHideEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "进程优化", "提升渲染线程优先级 + Hook 热状态回调（仅应用层）",
            cfg.processOptimizeEnabled,
            { cfg.processOptimizeEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "分辨率伪装", "伪装 Display/DisplayMetrics 为 2K，强制加载高清材质",
            cfg.resolutionSpoofEnabled,
            { cfg.resolutionSpoofEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )

        Spacer(Modifier.height(20.dp))
        Text("实验性功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "触摸采样率提升", "Hook InputEventReceiver/InputQueue 提升事件线程优先级",
            cfg.touchSamplingBoostEnabled,
            { cfg.touchSamplingBoostEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "网络延迟优化", "Hook Socket 设置 TCP_NODELAY + 扩大接收缓冲区",
            cfg.networkLatencyOptEnabled,
            { cfg.networkLatencyOptEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "音频优先级提升", "Hook AudioTrack 设 PERFORMANCE_MODE_LOW_LATENCY + 线程优先级",
            cfg.audioPriorityBoostEnabled,
            { cfg.audioPriorityBoostEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "内存整理", "Hook MemoryInfo/TrimMemory 让游戏看到更充足内存 + GC 提示",
            cfg.memoryDefragEnabled,
            { cfg.memoryDefragEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        if (cfg.frameRateUnlockEnabled) {
            Text("目标帧率", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("当前: ${cfg.targetFps} fps", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = cfg.targetFps.toFloat(),
                onValueChange = { cfg.targetFps = it.toInt(); ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
                valueRange = 60f..160f,
                steps = 19  // 60..160 步长 5，共 21 档
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}
