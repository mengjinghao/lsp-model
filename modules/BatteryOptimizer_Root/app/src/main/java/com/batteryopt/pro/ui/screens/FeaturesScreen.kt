package com.batteryopt.pro.ui.screens

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
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.ui.components.FeatureCard
import com.batteryopt.pro.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: BatteryConfig, onConfigChange: (BatteryConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("基础功能（应用层）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard("WakeLock 优化", "超长持有自动释放 + 拦截冗余 SDK 统计类",
            cfg.wakeLockEnabled,
            { cfg.wakeLockEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) })
        Spacer(Modifier.height(8.dp))

        FeatureCard("Alarm 闹钟优化", "高频精确闹钟降级为 setWindow",
            cfg.alarmEnabled,
            { cfg.alarmEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) })
        Spacer(Modifier.height(8.dp))

        FeatureCard("Sync 同步降频", "requestSync 节流，周期同步最小 30 分钟",
            cfg.syncEnabled,
            { cfg.syncEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) })
        Spacer(Modifier.height(8.dp))

        FeatureCard("JobScheduler 限频", "Job 最小周期 15 分钟 + requireDeviceIdle",
            cfg.jobEnabled,
            { cfg.jobEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) })
        Spacer(Modifier.height(8.dp))

        FeatureCard("Location 定位降频", "最小间隔 30s，后台高频 GPS 降级 NETWORK",
            cfg.locationEnabled,
            { cfg.locationEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) })
        Spacer(Modifier.height(8.dp))

        FeatureCard("Animation 动画优化", "scale=0 关闭动画省 GPU（默认关闭）",
            cfg.animationEnabled,
            { cfg.animationEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) })
        Spacer(Modifier.height(8.dp))

        FeatureCard("Sensor 传感器降频", ">50Hz 高频传感器降频至 5Hz",
            cfg.sensorEnabled,
            { cfg.sensorEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) })

        Spacer(Modifier.height(20.dp))
        Text("实验性功能（应用层）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard("蓝牙扫描降频", "Hook BluetoothLeScanner.startScan，最小 60s",
            cfg.bluetoothScanThrottleEnabled,
            { cfg.bluetoothScanThrottleEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("后台相机阻断", "Hook Camera2/Camera.open，APP 在后台时阻止",
            cfg.cameraBackgroundBlockEnabled,
            { cfg.cameraBackgroundBlockEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("振动器限频", "Hook Vibrator.vibrate，最小触发间隔 1s",
            cfg.vibratorThrottleEnabled,
            { cfg.vibratorThrottleEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true)

        Spacer(Modifier.height(20.dp))
        Text("系统级功能（需 Shizuku）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(8.dp))

        FeatureCard("系统 Doze 强制", "dumpsys deviceidle force-idle deep，屏幕关闭后延迟触发",
            cfg.dozeEnabled,
            { cfg.dozeEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            systemLevel = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("后台 APP 冻结", "am force-stop 黑名单，屏幕关闭 N 分钟后批量冻结",
            cfg.freezeEnabled,
            { cfg.freezeEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            systemLevel = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("CPU 调度策略", "echo governor > /sys/.../scaling_governor，屏幕关闭切 powersave",
            cfg.cpuGovernorEnabled,
            { cfg.cpuGovernorEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            systemLevel = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("孤儿 WakeLock 清理", "dumpsys power 文本分析，识别孤儿 wake lock",
            cfg.greenifyEnabled,
            { cfg.greenifyEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            systemLevel = true)

        Spacer(Modifier.height(20.dp))
        Text("实验性功能（系统级）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(8.dp))

        FeatureCard("低电量模式自动切换", "settings put global low_power，电量低于阈值自动开启",
            cfg.lowPowerModeAutoEnabled,
            { cfg.lowPowerModeAutoEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true, systemLevel = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("电量统计重置", "dumpsys batterystats --reset，充电 N 秒后重置",
            cfg.batteryStatsResetEnabled,
            { cfg.batteryStatsResetEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true, systemLevel = true)

        Spacer(Modifier.height(20.dp))
        Text("参数调整", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Text("WakeLock 最大持有: ${cfg.wakeLockMaxHoldSec}s", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = cfg.wakeLockMaxHoldSec.toFloat(),
            onValueChange = { cfg.wakeLockMaxHoldSec = it.toInt(); ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            valueRange = 10f..300f
        )

        Text("Alarm 最小间隔: ${cfg.alarmMinIntervalMin} 分钟", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = cfg.alarmMinIntervalMin.toFloat(),
            onValueChange = { cfg.alarmMinIntervalMin = it.toInt(); ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            valueRange = 1f..60f
        )

        Text("Doze 进入延迟: ${cfg.dozeDelaySec}s", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = cfg.dozeDelaySec.toFloat(),
            onValueChange = { cfg.dozeDelaySec = it.toInt(); ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            valueRange = 0f..600f
        )

        Text("后台冻结延迟: ${cfg.freezeDelayMin} 分钟", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = cfg.freezeDelayMin.toFloat(),
            onValueChange = { cfg.freezeDelayMin = it.toInt(); ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            valueRange = 1f..60f
        )

        Text("Greenify 清理周期: ${cfg.greenifyIntervalSec}s", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = cfg.greenifyIntervalSec.toFloat(),
            onValueChange = { cfg.greenifyIntervalSec = it.toInt(); ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            valueRange = 60f..3600f
        )

        Spacer(Modifier.height(40.dp))
    }
}
