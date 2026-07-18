package com.batteryopt.noroot.ui.screens

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
import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.ui.components.FeatureCard
import com.batteryopt.noroot.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: BatteryConfig, onConfigChange: (BatteryConfig) -> Unit) {
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
            "WakeLock 优化", "超长持有自动释放 + 拦截冗余 SDK 统计类",
            cfg.wakeLockEnabled,
            { cfg.wakeLockEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Alarm 闹钟优化", "高频精确闹钟降级为 setWindow，最小间隔放大",
            cfg.alarmEnabled,
            { cfg.alarmEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Sync 同步降频", "requestSync 节流，周期同步最小 30 分钟",
            cfg.syncEnabled,
            { cfg.syncEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "JobScheduler 限频", "Job 最小周期 15 分钟，追加 requireDeviceIdle 约束",
            cfg.jobEnabled,
            { cfg.jobEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Location 定位降频", "最小间隔 30s，后台高频 GPS 降级为 NETWORK",
            cfg.locationEnabled,
            { cfg.locationEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Animation 动画优化", "scale=0 关闭动画省 GPU（默认关闭，可能影响体验）",
            cfg.animationEnabled,
            { cfg.animationEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Sensor 传感器降频", ">50Hz 高频传感器降频至 5Hz",
            cfg.sensorEnabled,
            { cfg.sensorEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )

        Spacer(Modifier.height(20.dp))
        Text("实验性功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "蓝牙扫描降频", "Hook BluetoothLeScanner.startScan，最小间隔 60s",
            cfg.bluetoothScanThrottleEnabled,
            { cfg.bluetoothScanThrottleEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "后台相机阻断", "Hook Camera2/Camera.open，APP 在后台时阻止打开相机",
            cfg.cameraBackgroundBlockEnabled,
            { cfg.cameraBackgroundBlockEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "振动器限频", "Hook Vibrator.vibrate，最小触发间隔 1s",
            cfg.vibratorThrottleEnabled,
            { cfg.vibratorThrottleEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )

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

        Spacer(Modifier.height(40.dp))
    }
}
